package com.lotus.abs.transport

enum class CommandCategory {
    READ_ONLY,
    ACTUATOR_CONTROL
}

enum class CommandStatus {
    NOT_TRANSMITTED,
    TRANSMITTED,
    ACKNOWLEDGED,
    NEGATIVE_RESPONSE,
    TIMEOUT,
    COMMUNICATION_ERROR,
    DISCONNECTED,
    BLOCKED_SAFETY_GUARD
}

data class DiagnosticCommandResult(
    val status: CommandStatus,
    val isSuccess: Boolean, // True ONLY if status == ACKNOWLEDGED
    val requestHex: String,
    val responseHex: String = "",
    val statusText: String = "",
    val nrcCode: String? = null
)

interface DiagnosticTransport {
    val transportName: String
    val isConnected: Boolean

    suspend fun executeCommand(
        commandHex: String,
        category: CommandCategory,
        timeoutMs: Long = 1500
    ): DiagnosticCommandResult

    suspend fun disconnect()
}
