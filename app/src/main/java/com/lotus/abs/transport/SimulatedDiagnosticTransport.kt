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

    override suspend fun executeCommand(
        commandHex: String,
        category: CommandCategory,
        timeoutMs: Long
    ): DiagnosticCommandResult {
        val cleanCmd = commandHex.replace(" ", "").uppercase(Locale.US)

        if (!mockConnected || mockResponseMode == ResponseMode.DISCONNECTED) {
            return DiagnosticCommandResult(
                status = CommandStatus.DISCONNECTED,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = "Transport disconnected"
            )
        }

        if (category == CommandCategory.ACTUATOR_CONTROL && !allowActuatorControl) {
            return DiagnosticCommandResult(
                status = CommandStatus.BLOCKED_SAFETY_GUARD,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = "Actuator control blocked by real hardware safety guard"
            )
        }

        executedCommandsList.add(Pair(cleanCmd, category))

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
                    statusText = "SUCCESS: Positive KWP2000 response $respPayload"
                )
            }
            ResponseMode.TRANSMITTED_NO_ACK -> {
                DiagnosticCommandResult(
                    status = CommandStatus.TRANSMITTED,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "TRANSMITTED: Command sent to stream, but no ECU ACK received"
                )
            }
            ResponseMode.NEGATIVE_RESPONSE -> {
                DiagnosticCommandResult(
                    status = CommandStatus.NEGATIVE_RESPONSE,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    responseHex = "7F${cleanCmd.take(2)}12",
                    statusText = "NEGATIVE_RESPONSE: NRC 12 (SubFunctionNotSupported)",
                    nrcCode = "12"
                )
            }
            ResponseMode.TIMEOUT -> {
                DiagnosticCommandResult(
                    status = CommandStatus.TIMEOUT,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "TIMEOUT: No ECU response within ${timeoutMs}ms"
                )
            }
            ResponseMode.CORRUPT_CHECKSUM -> {
                DiagnosticCommandResult(
                    status = CommandStatus.COMMUNICATION_ERROR,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "CHECKSUM_ERROR: Corrupt frame received"
                )
            }
            ResponseMode.COMMUNICATION_ERROR -> {
                DiagnosticCommandResult(
                    status = CommandStatus.COMMUNICATION_ERROR,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "COMMUNICATION_ERROR: I/O or framing error"
                )
            }
            ResponseMode.DISCONNECTED -> {
                mockConnected = false
                DiagnosticCommandResult(
                    status = CommandStatus.DISCONNECTED,
                    isSuccess = false,
                    requestHex = cleanCmd,
                    statusText = "DISCONNECTED: Hardware cable unattached"
                )
            }
        }
    }

    override suspend fun disconnect() {
        mockConnected = false
    }
}
