package com.lotus.abs.usb

import android.os.SystemClock
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import com.lotus.abs.transport.DiagnosticCommandResult
import com.lotus.abs.transport.DiagnosticTransport
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale

enum class KwpState {
    DISCONNECTED,
    PORT_OPEN,
    INITIALIZING,
    SESSION_ACTIVE,
    ERROR
}

data class Kwp2000Frame(
    val formatByte: Byte,
    val targetAddress: Byte,
    val sourceAddress: Byte,
    val payload: ByteArray,
    val checksum: Byte
) {
    val rawBytes: ByteArray get() {
        val bytes = ByteArray(4 + payload.size)
        bytes[0] = formatByte
        bytes[1] = targetAddress
        bytes[2] = sourceAddress
        System.arraycopy(payload, 0, bytes, 3, payload.size)
        bytes[bytes.size - 1] = checksum
        return bytes
    }

    val hexString: String get() = rawBytes.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Kwp2000Frame
        if (formatByte != other.formatByte) return false
        if (targetAddress != other.targetAddress) return false
        if (sourceAddress != other.sourceAddress) return false
        if (!payload.contentEquals(other.payload)) return false
        if (checksum != other.checksum) return false
        return true
    }

    override fun hashCode(): Int {
        var result = formatByte.toInt()
        result = 31 * result + targetAddress.toInt()
        result = 31 * result + sourceAddress.toInt()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + checksum.toInt()
        return result
    }

    companion object {
        fun calculateChecksum(bytes: ByteArray, length: Int = bytes.size): Byte {
            var sum = 0
            for (i in 0 until length) {
                sum += (bytes[i].toInt() and 0xFF)
            }
            return (sum and 0xFF).toByte()
        }

        fun buildFrame(
            payload: ByteArray,
            targetAddress: Byte = 0x2B.toByte(),
            sourceAddress: Byte = 0xF1.toByte()
        ): Kwp2000Frame {
            val length = payload.size
            require(length in 1..63) { "Dimensione payload KWP2000 non supportata: $length" }

            val formatByte = (0x80 or length).toByte()

            val tempBuffer = ByteArray(3 + length)
            tempBuffer[0] = formatByte
            tempBuffer[1] = targetAddress
            tempBuffer[2] = sourceAddress
            System.arraycopy(payload, 0, tempBuffer, 3, length)

            val checksum = calculateChecksum(tempBuffer)

            return Kwp2000Frame(
                formatByte = formatByte,
                targetAddress = targetAddress,
                sourceAddress = sourceAddress,
                payload = payload,
                checksum = checksum
            )
        }

        fun parseFrame(bytes: ByteArray): Kwp2000Frame? {
            if (bytes.size < 5) return null
            val fmt = bytes[0]
            val tgt = bytes[1]
            val src = bytes[2]
            val length = (fmt.toInt() and 0x3F)

            if (length == 0 || bytes.size < 4 + length) return null

            val payload = ByteArray(length)
            System.arraycopy(bytes, 3, payload, 0, length)

            val expectedChecksum = calculateChecksum(bytes, 3 + length)
            val actualChecksum = bytes[3 + length]

            if (expectedChecksum != actualChecksum) {
                try {
                    Log.w("KWP_DEBUG", "Checksum non valido: calcolato ${expectedChecksum.toHexString()}, ricevuto ${actualChecksum.toHexString()}")
                } catch (_: Exception) {}
                return null
            }

            return Kwp2000Frame(fmt, tgt, src, payload, actualChecksum)
        }
    }
}

fun ByteArray.toHexString(): String {
    return joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
}

fun Byte.toHexString(): String {
    return String.format("%02X", this.toInt() and 0xFF)
}

fun String.hexToByteArray(): ByteArray {
    val clean = this.replace(" ", "").replace("\r", "").replace("\n", "").uppercase(Locale.US)
    if (clean.isEmpty() || clean.length % 2 != 0) return byteArrayOf()
    val bytes = ByteArray(clean.length / 2)
    for (i in bytes.indices) {
        val index = i * 2
        bytes[i] = clean.substring(index, index + 2).toInt(16).toByte()
    }
    return bytes
}

data class KwpExchangeResult(
    val isSuccess: Boolean,
    val statusText: String,
    val sentFrameHex: String,
    val echoReceivedHex: String,
    val echoFilteredHex: String,
    val isEchoMatched: Boolean,
    val rawRxHex: String,
    val payloadHex: String,
    val t0Nanos: Long,
    val t1Nanos: Long,
    val tEchoFirstNanos: Long,
    val tEchoCompleteNanos: Long,
    val tEcuFirstByteNanos: Long,
    val tEcuCompleteNanos: Long,
    val t3Nanos: Long,
    val writeDurationMs: Double,
    val echoDurationMs: Double,
    val ecuTimeToFirstByteMs: Double,
    val ecuFrameDurationMs: Double,
    val totalRoundTripMs: Double,
    val kwpFrame: Kwp2000Frame? = null
)

class Kwp2000KLineTransport(
    private val port: UsbSerialPort,
    val targetAddress: Byte = 0x2B.toByte(), // ECU Lotus EBC430 Target Address
    val sourceAddress: Byte = 0xF1.toByte(), // Tester Source Address
    var isEchoFilterEnabled: Boolean = true,
    var isRealHardwareActuatorExecutionEnabled: Boolean = false // Safety Guard for real USB hardware
) : DiagnosticTransport {

    override val transportName: String = "VAG-COM KKL 409.1 USB FTDI"

    override val isConnected: Boolean
        get() = (state == KwpState.SESSION_ACTIVE || state == KwpState.PORT_OPEN)

    companion object {
        private const val TAG = "KWP2000_KLINE"
        const val DEFAULT_READ_TIMEOUT_MS = 1500

        // Whitelist di sicurezza esplicita dei soli comandi di lettura diagnostica approvati per la EBC430 su USB KKL
        val ALLOWED_READ_COMMANDS_HEX = setOf(
            "1A80", "1A91", "1A92", "1A94",
            "2101", "2102",
            "2130", "2131", "2132", "2133", "2134"
        )

        // Comandi attuatori definiti nelle macro dell'applicazione
        val ALLOWED_ACTUATOR_COMMANDS_HEX = setOf(
            "30020701", "30020700", // ABS Pump ON/OFF
            "30110701", "30110700", // DUMP LF
            "30120701", "30120700", // ISO LF
            "30210701", "30210700", // DUMP RF
            "30220701", "30220700", // ISO RF
            "30310701", "30310700", // DUMP LR
            "30320701", "30320700", // ISO LR
            "30410701", "30410700", // DUMP RR
            "30420701", "30420700", // ISO RR
            "30030701", "30030700"  // Valve Relay
        )
    }

    private val streamDecoder by lazy {
        Kwp2000StreamDecoder(
            expectedTargetAddress = sourceAddress,
            expectedSourceAddress = targetAddress,
            isEchoFilterEnabled = isEchoFilterEnabled
        )
    }

    var state: KwpState = KwpState.DISCONNECTED
        private set

    fun openPort(baudRate: Int = 10400) {
        try {
            port.setParameters(
                baudRate,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            state = KwpState.PORT_OPEN
            Log.d(TAG, "Porta seriale FTDI aperta a $baudRate baud 8N1")
        } catch (e: Exception) {
            state = KwpState.ERROR
            throw IOException("Impossibile impostare i parametri della porta seriale: ${e.message}", e)
        }
    }

    override fun canExecute(category: CommandCategory): Boolean {
        if (!isConnected) return false
        return when (category) {
            CommandCategory.READ_ONLY -> true
            CommandCategory.ACTUATOR_CONTROL -> isRealHardwareActuatorExecutionEnabled
        }
    }

    override suspend fun executeCommand(
        commandHex: String,
        category: CommandCategory,
        timeoutMs: Long
    ): DiagnosticCommandResult {
        val cleanCmd = commandHex.replace(" ", "").uppercase(Locale.US)

        if (!canExecute(category)) {
            val msg = if (!isConnected) {
                "USB KKL Transport disconnected"
            } else if (category == CommandCategory.ACTUATOR_CONTROL && !isRealHardwareActuatorExecutionEnabled) {
                "BLOCKED_SAFETY_GUARD: Real USB actuator execution is disabled in this phase."
            } else {
                "BLOCKED_NOT_IN_WHITELIST: Command '$cleanCmd' not in allowed set."
            }
            Log.w(TAG, msg)
            return DiagnosticCommandResult(
                status = if (!isConnected) CommandStatus.DISCONNECTED else CommandStatus.BLOCKED_SAFETY_GUARD,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = msg
            )
        }

        if (category == CommandCategory.ACTUATOR_CONTROL) {
            if (!ALLOWED_ACTUATOR_COMMANDS_HEX.contains(cleanCmd)) {
                val msg = "BLOCKED_NOT_IN_WHITELIST: Actuator command '$cleanCmd' not in allowed actuator set."
                Log.w(TAG, msg)
                return DiagnosticCommandResult(
                    status = CommandStatus.BLOCKED_SAFETY_GUARD,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = msg
                )
            }
        } else {
            if (!ALLOWED_READ_COMMANDS_HEX.contains(cleanCmd)) {
                val msg = "BLOCKED_NOT_IN_WHITELIST: Diagnostic read command '$cleanCmd' not in allowed read whitelist."
                Log.w(TAG, msg)
                return DiagnosticCommandResult(
                    status = CommandStatus.BLOCKED_SAFETY_GUARD,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = msg
                )
            }
        }

        val kwpResult = sendDiagnosticReadCommand(cleanCmd, timeoutMs.toInt())
        return mapKwpResultToDiagnosticCommandResult(cleanCmd, kwpResult)
    }

    private fun mapKwpResultToDiagnosticCommandResult(
        cleanCmd: String,
        kwpResult: KwpExchangeResult
    ): DiagnosticCommandResult {
        val baseTiming = DiagnosticCommandResult(
            status = CommandStatus.COMMUNICATION_ERROR,
            isSuccess = false,
            requestHex = cleanCmd,
            txStartNanos = kwpResult.t0Nanos,
            rxCompleteNanos = kwpResult.t3Nanos,
            totalRoundTripMs = kwpResult.totalRoundTripMs
        )

        if (kwpResult.isSuccess && kwpResult.kwpFrame != null) {
            return baseTiming.copy(
                status = CommandStatus.ACKNOWLEDGED,
                isSuccess = true,
                responseHex = kwpResult.payloadHex,
                statusText = "ACKNOWLEDGED: ${kwpResult.statusText}"
            )
        }

        if (kwpResult.statusText.contains("NEGATIVE_RESPONSE") || kwpResult.payloadHex.startsWith("7F")) {
            val nrc = if ((kwpResult.kwpFrame?.payload?.size ?: 0) >= 3) {
                String.format("%02X", kwpResult.kwpFrame!!.payload[2].toInt() and 0xFF)
            } else null
            return baseTiming.copy(
                status = CommandStatus.NEGATIVE_RESPONSE,
                responseHex = kwpResult.payloadHex,
                statusText = kwpResult.statusText,
                nrcCode = nrc
            )
        }

        if (kwpResult.statusText.contains("INCOMPLETE_RESPONSE") || kwpResult.statusText.contains("TIMEOUT")) {
            return baseTiming.copy(
                status = CommandStatus.TIMEOUT,
                responseHex = kwpResult.payloadHex,
                statusText = kwpResult.statusText
            )
        }

        if (kwpResult.statusText.contains("BLOCKED")) {
            return baseTiming.copy(
                status = CommandStatus.BLOCKED_SAFETY_GUARD,
                statusText = kwpResult.statusText
            )
        }

        return baseTiming.copy(
            status = CommandStatus.COMMUNICATION_ERROR,
            responseHex = kwpResult.payloadHex,
            statusText = kwpResult.statusText
        )
    }

    fun performFastInit(
        logCallback: ((String) -> Unit)? = null
    ): Boolean {
        if (state != KwpState.PORT_OPEN && state != KwpState.SESSION_ACTIVE) {
            logCallback?.invoke("[FAST INIT] Errore: stato porta non valido ($state)")
            return false
        }

        state = KwpState.INITIALIZING
        logCallback?.invoke("[FAST INIT] Avvio sequenza Fast Init KWP2000 per ECU Lotus EBC430...")

        try {
            Thread.sleep(320)

            val t0Nanos = SystemClock.elapsedRealtimeNanos()
            var tBreakLowNanos = 0L
            var tBreakHighNanos = 0L

            try {
                port.setBreak(true)
                Thread.sleep(25)
                tBreakLowNanos = SystemClock.elapsedRealtimeNanos()
            } finally {
                try {
                    port.setBreak(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore disattivazione BREAK: ${e.message}")
                }
                Thread.sleep(25)
                tBreakHighNanos = SystemClock.elapsedRealtimeNanos()
            }

            val lowDurationMs = (tBreakLowNanos - t0Nanos) / 1_000_000.0
            val highDurationMs = (tBreakHighNanos - tBreakLowNanos) / 1_000_000.0
            logCallback?.invoke(
                String.format(
                    Locale.US,
                    "[FAST INIT] Impulsi SOFTWARE misurati: LOW = %.2f ms, HIGH = %.2f ms",
                    lowDurationMs, highDurationMs
                )
            )

            val startCommFrame = Kwp2000Frame.buildFrame(
                payload = byteArrayOf(0x81.toByte()),
                targetAddress = targetAddress,
                sourceAddress = sourceAddress
            )

            logCallback?.invoke("[FAST INIT] TX StartCommunication: ${startCommFrame.hexString}")

            val result = sendFrameAndReceive(startCommFrame, timeoutMs = 2000)

            logCallback?.invoke("[FAST INIT] RX Echo filtrato: '${result.echoFilteredHex}' (Echo Coerente: ${result.isEchoMatched})")
            logCallback?.invoke("[FAST INIT] RX Risposta ECU: '${result.rawRxHex}' (TTFB ECU: ${String.format(Locale.US, "%.2f", result.ecuTimeToFirstByteMs)} ms)")

            if (result.isSuccess && result.kwpFrame != null) {
                val respPayload = result.kwpFrame.payload
                if (respPayload.isNotEmpty() && (respPayload[0].toInt() and 0xFF) == 0xC1) {
                    state = KwpState.SESSION_ACTIVE
                    logCallback?.invoke("[FAST INIT] Sessione KWP2000 ATTIVA! Risposta StartCommunication Positiva (0xC1)")
                    return true
                }
            }

            state = KwpState.ERROR
            logCallback?.invoke("[FAST INIT] FAILED: Risposta a StartCommunication non valida o assente. Status: ${result.statusText}")
            return false

        } catch (e: Exception) {
            state = KwpState.ERROR
            logCallback?.invoke("[FAST INIT] ERRORE durante Fast Init: ${e.message}")
            return false
        }
    }

    fun sendDiagnosticReadCommand(
        commandCmdHex: String,
        timeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        logCallback: ((String) -> Unit)? = null
    ): KwpExchangeResult {
        val cleanCmd = commandCmdHex.replace(" ", "").uppercase(Locale.US)

        val payload = cleanCmd.hexToByteArray()

        val kwpFrame = Kwp2000Frame.buildFrame(
            payload = payload,
            targetAddress = targetAddress,
            sourceAddress = sourceAddress
        )

        return sendFrameAndReceive(kwpFrame, timeoutMs)
    }

    fun sendFrameAndReceive(
        frame: Kwp2000Frame,
        timeoutMs: Int
    ): KwpExchangeResult {
        val txBytes = frame.rawBytes
        val reqServiceId = if (frame.payload.isNotEmpty()) frame.payload[0].toInt() and 0xFF else 0

        val t0Nanos = SystemClock.elapsedRealtimeNanos()

        try {
            port.write(txBytes, timeoutMs)
        } catch (e: Exception) {
            val t1 = SystemClock.elapsedRealtimeNanos()
            return KwpExchangeResult(
                isSuccess = false,
                statusText = "WRITE_ERROR: ${e.message}",
                sentFrameHex = txBytes.toHexString(),
                echoReceivedHex = "", echoFilteredHex = "", isEchoMatched = false,
                rawRxHex = "", payloadHex = "",
                t0Nanos = t0Nanos, t1Nanos = t1, tEchoFirstNanos = t1, tEchoCompleteNanos = t1,
                tEcuFirstByteNanos = t1, tEcuCompleteNanos = t1, t3Nanos = t1,
                writeDurationMs = (t1 - t0Nanos) / 1_000_000.0, echoDurationMs = 0.0,
                ecuTimeToFirstByteMs = 0.0, ecuFrameDurationMs = 0.0,
                totalRoundTripMs = (t1 - t0Nanos) / 1_000_000.0
            )
        }

        val t1Nanos = SystemClock.elapsedRealtimeNanos()

        val rxStreamBuffer = ByteArrayOutputStream()
        val tempReadBuffer = ByteArray(256)

        var tEchoFirstNanos = 0L
        var tEchoCompleteNanos = 0L
        var tEcuFirstByteNanos = 0L
        var tEcuCompleteNanos = 0L

        val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
        val expectedEchoSize = if (isEchoFilterEnabled) txBytes.size else 0

        var decodeResult: KwpDecodeResult? = null

        while (SystemClock.elapsedRealtime() < deadlineMs) {
            try {
                val readNow = port.read(tempReadBuffer, 100)
                val nowNanos = SystemClock.elapsedRealtimeNanos()

                if (readNow > 0) {
                    if (tEchoFirstNanos == 0L) {
                        tEchoFirstNanos = nowNanos
                    }

                    rxStreamBuffer.write(tempReadBuffer, 0, readNow)
                    val currentStream = rxStreamBuffer.toByteArray()

                    if (expectedEchoSize > 0 && currentStream.size >= expectedEchoSize && tEchoCompleteNanos == 0L) {
                        tEchoCompleteNanos = nowNanos
                    }

                    if (currentStream.size > expectedEchoSize && tEcuFirstByteNanos == 0L) {
                        tEcuFirstByteNanos = nowNanos
                    }

                    streamDecoder.isEchoFilterEnabled = isEchoFilterEnabled
                    val currentDecode = streamDecoder.decodeStream(currentStream, txBytes, reqServiceId)

                    if (currentDecode.status != KwpDecodeStatus.INCOMPLETE_RESPONSE && currentDecode.status != KwpDecodeStatus.NO_DATA) {
                        decodeResult = currentDecode
                        tEcuCompleteNanos = nowNanos
                        break
                    }
                }
            } catch (_: Exception) {
                break
            }
        }

        val t3Nanos = SystemClock.elapsedRealtimeNanos()

        if (tEchoFirstNanos == 0L) tEchoFirstNanos = t3Nanos
        if (tEchoCompleteNanos == 0L) tEchoCompleteNanos = tEchoFirstNanos
        if (tEcuFirstByteNanos == 0L) tEcuFirstByteNanos = t3Nanos
        if (tEcuCompleteNanos == 0L) tEcuCompleteNanos = t3Nanos

        val fullStream = rxStreamBuffer.toByteArray()

        if (decodeResult == null) {
            streamDecoder.isEchoFilterEnabled = isEchoFilterEnabled
            decodeResult = streamDecoder.decodeStream(fullStream, txBytes, reqServiceId)
        }

        val writeDurationMs = (t1Nanos - t0Nanos) / 1_000_000.0
        val echoDurationMs = if (tEchoCompleteNanos > tEchoFirstNanos) (tEchoCompleteNanos - tEchoFirstNanos) / 1_000_000.0 else 0.0
        val ecuTtfbMs = if (tEcuFirstByteNanos > t1Nanos) (tEcuFirstByteNanos - t1Nanos) / 1_000_000.0 else 0.0
        val ecuFrameDurationMs = if (tEcuCompleteNanos > tEcuFirstByteNanos) (tEcuCompleteNanos - tEcuFirstByteNanos) / 1_000_000.0 else 0.0
        val totalRttMs = (t3Nanos - t0Nanos) / 1_000_000.0

        return KwpExchangeResult(
            isSuccess = decodeResult.isSuccess,
            statusText = decodeResult.statusText,
            sentFrameHex = txBytes.toHexString(),
            echoReceivedHex = decodeResult.echoBytes.toHexString(),
            echoFilteredHex = if (decodeResult.isEchoMatched) decodeResult.echoBytes.toHexString() else "MISMATCH/ABSENT",
            isEchoMatched = decodeResult.isEchoMatched,
            rawRxHex = decodeResult.ecuBytes.toHexString(),
            payloadHex = decodeResult.parsedFrame?.payload?.toHexString() ?: "",
            t0Nanos = t0Nanos,
            t1Nanos = t1Nanos,
            tEchoFirstNanos = tEchoFirstNanos,
            tEchoCompleteNanos = tEchoCompleteNanos,
            tEcuFirstByteNanos = tEcuFirstByteNanos,
            tEcuCompleteNanos = tEcuCompleteNanos,
            t3Nanos = t3Nanos,
            writeDurationMs = writeDurationMs,
            echoDurationMs = echoDurationMs,
            ecuTimeToFirstByteMs = ecuTtfbMs,
            ecuFrameDurationMs = ecuFrameDurationMs,
            totalRoundTripMs = totalRttMs,
            kwpFrame = decodeResult.parsedFrame
        )
    }

    override suspend fun disconnect() {
        close()
    }

    fun close() {
        state = KwpState.DISCONNECTED
        try {
            port.close()
        } catch (_: Exception) {}
    }
}
