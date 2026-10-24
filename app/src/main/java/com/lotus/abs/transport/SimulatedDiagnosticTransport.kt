package com.lotus.abs.transport

import java.util.Locale

class SimulatedDiagnosticTransport(
    override val transportName: String = "Simulated KWP2000 Transport",
    var mockConnected: Boolean = true,
    var mockResponseMode: ResponseMode = ResponseMode.ACKNOWLEDGED,
    var allowActuatorControl: Boolean = true
) : DiagnosticTransport {

    enum class ResponseMode {
        ACKNOWLEDGED,
        TRANSMITTED_NO_ACK,
        NEGATIVE_RESPONSE,
        TIMEOUT,
        CORRUPT_CHECKSUM,
        COMMUNICATION_ERROR,
        DISCONNECTED
    }

    override val isConnected: Boolean
        get() = mockConnected

    private val executedCommandsList = mutableListOf<Pair<String, CommandCategory>>()
    val executedCommands: List<Pair<String, CommandCategory>> get() = executedCommandsList

    fun clearExecutedCommands() {
        executedCommandsList.clear()
    }

    override fun canExecute(category: CommandCategory): Boolean {
        if (!mockConnected) return false
        return when (category) {
            CommandCategory.READ_ONLY -> true
            CommandCategory.ACTUATOR_CONTROL -> allowActuatorControl
        }
    }

    override suspend fun executeCommand(
        commandHex: String,
        category: CommandCategory,
        timeoutMs: Long
    ): DiagnosticCommandResult {
        val cleanCmd = commandHex.replace(" ", "").uppercase(Locale.US)
        val t0Nanos = System.nanoTime()

        if (!mockConnected || mockResponseMode == ResponseMode.DISCONNECTED) {
            val t1Nanos = System.nanoTime()
            return DiagnosticCommandResult(
                status = CommandStatus.DISCONNECTED,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = "Transport disconnected",
                txStartNanos = t0Nanos,
                rxCompleteNanos = t1Nanos,
                totalRoundTripMs = (t1Nanos - t0Nanos) / 1_000_000.0
            )
        }

        if (category == CommandCategory.ACTUATOR_CONTROL && !allowActuatorControl) {
            val t1Nanos = System.nanoTime()
            return DiagnosticCommandResult(
                status = CommandStatus.BLOCKED_SAFETY_GUARD,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = "Actuator control blocked by real hardware safety guard",
                txStartNanos = t0Nanos,
                rxCompleteNanos = t1Nanos,
                totalRoundTripMs = (t1Nanos - t0Nanos) / 1_000_000.0
            )
        }

        executedCommandsList.add(Pair(cleanCmd, category))
        val t1Nanos = System.nanoTime()
        val durationMs = (t1Nanos - t0Nanos) / 1_000_000.0

        return when (mockResponseMode) {
            ResponseMode.ACKNOWLEDGED -> {
                val serviceByte = if (cleanCmd.length >= 2) cleanCmd.substring(0, 2) else "21"
                val positiveService = try {
                    String.format("%02X", (serviceByte.toInt(16) + 0x40) and 0xFF)
                } catch (_: Exception) {
                    "61"
                }
                val respPayload = positiveService + if (cleanCmd.length > 2) cleanCmd.substring(2) else ""
                DiagnosticCommandResult(
                    status = CommandStatus.ACKNOWLEDGED,
                    isSuccess = true,
                    requestHex = cleanCmd,
                    responseHex = respPayload,
                    statusText = "SUCCESS: Positive KWP2000 response $respPayload",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
            ResponseMode.TRANSMITTED_NO_ACK -> {
                DiagnosticCommandResult(
                    status = CommandStatus.TRANSMITTED,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "TRANSMITTED: Command sent to stream, but no ECU ACK received",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
            ResponseMode.NEGATIVE_RESPONSE -> {
                DiagnosticCommandResult(
                    status = CommandStatus.NEGATIVE_RESPONSE,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    responseHex = "7F${cleanCmd.take(2)}12",
                    statusText = "NEGATIVE_RESPONSE: NRC 12 (SubFunctionNotSupported)",
                    nrcCode = "12",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
            ResponseMode.TIMEOUT -> {
                DiagnosticCommandResult(
                    status = CommandStatus.TIMEOUT,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "TIMEOUT: No ECU response within ${timeoutMs}ms",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
            ResponseMode.CORRUPT_CHECKSUM -> {
                DiagnosticCommandResult(
                    status = CommandStatus.COMMUNICATION_ERROR,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "CHECKSUM_ERROR: Corrupt frame received",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
            ResponseMode.COMMUNICATION_ERROR -> {
                DiagnosticCommandResult(
                    status = CommandStatus.COMMUNICATION_ERROR,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "COMMUNICATION_ERROR: I/O or framing error",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
            ResponseMode.DISCONNECTED -> {
                mockConnected = false
                DiagnosticCommandResult(
                    status = CommandStatus.DISCONNECTED,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "DISCONNECTED: Hardware cable unattached",
                    txStartNanos = t0Nanos,
                    rxCompleteNanos = t1Nanos,
                    totalRoundTripMs = durationMs
                )
            }
        }
    }

    override suspend fun disconnect() {
        mockConnected = false
    }
}
