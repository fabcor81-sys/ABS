package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import kotlinx.coroutines.flow.update
import com.lotus.abs.data.database.LotusAbsDatabase
import com.lotus.abs.data.entity.DTCRecord
import com.lotus.abs.obd2.OBD2Parser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TerminalEntryType {
    SENT, RECEIVED, SYSTEM, ERROR
}

data class TerminalEntry(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val text: String,
    val type: TerminalEntryType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
)

data class DiagnosticsState(
    val terminalLog: List<TerminalEntry> = emptyList(),
    val customCommand: String = "",
    val dtcList: List<DTCRecord> = emptyList(),
    val isSending: Boolean = false,
    val lastActionMessage: String? = null,
    val lastActionSuccess: Boolean = false,
    val canClearDtc: Boolean = false,
    val connectionState: BluetoothManager.ConnectionState = BluetoothManager.ConnectionState.DISCONNECTED
)

class DiagnosticsViewModel(
    private val bluetoothManager: BluetoothManager,
    private val database: LotusAbsDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state

    private val dtcDao = database.dtcDao()

    init {
        observeBluetoothConnection()
        observeBluetoothData()
        loadDTCsFromDatabase()
        addSystemLog("Terminal initialized. Ready for Lotus ABS ECU commands.")
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connectionState ->
                _state.emit(_state.value.copy(
                    connectionState = connectionState,
                    canClearDtc = connectionState == BluetoothManager.ConnectionState.CONNECTED &&
                        bluetoothManager.activeTransport != BluetoothManager.Transport.USB
                ))
                when (connectionState) {
                    BluetoothManager.ConnectionState.CONNECTED -> {
                        addSystemLog("Device connected. Terminal active.")
                    }
                    BluetoothManager.ConnectionState.DISCONNECTED -> {
                        addSystemLog("Device disconnected.")
                    }
                    BluetoothManager.ConnectionState.ERROR -> {
                        addErrorLog("Bluetooth connection error.")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeBluetoothData() {
        viewModelScope.launch {
            var responseBuffer = ""

            bluetoothManager.receivedData.collect { data ->
                val cleaned = data.replace("\r", "")
                responseBuffer += cleaned

                while (responseBuffer.contains("\n")) {
                    val index = responseBuffer.indexOf("\n")
                    val line = responseBuffer.substring(0, index).trim()
                    if (line.isNotEmpty()) {
                        addReceivedLog(line)
                        parseDTCResponse(line)
                    }
                    responseBuffer = responseBuffer.substring(index + 1)
                }

                if (responseBuffer.trim() == ">" || responseBuffer.endsWith(">")) {
                    val trimmed = responseBuffer.trim()
                    if (trimmed.isNotEmpty() && trimmed != ">") {
                        addReceivedLog(trimmed)
                        parseDTCResponse(trimmed)
                    } else if (trimmed == ">") {
                        addReceivedLog(trimmed)
                    }
                    responseBuffer = ""
                }
            }
        }
    }

    private fun loadDTCsFromDatabase() {
        viewModelScope.launch {
            dtcDao.getAllDTCs().collect { dtcList ->
                _state.emit(_state.value.copy(dtcList = dtcList))
            }
        }
    }

    fun updateCustomCommand(command: String) {
        _state.value = _state.value.copy(customCommand = command)
    }

    fun sendCustomCommand() {
        val command = _state.value.customCommand.trim()
        if (command.isNotEmpty()) {
            sendCommand(command)
            updateCustomCommand("")
        }
    }

    // Raw terminal commands are intentionally read-only. Clearing DTCs has a
    // separate confirmation flow and actuator commands are only exposed by the
    // guarded Bleed screen.
    fun sendCommand(command: String) {
        val normalized = command.replace(" ", "").uppercase(Locale.US)
        if (!listOf("18", "19", "1A", "21", "22").any { normalized.startsWith(it) }) {
            val message = "Command not allowed in the read-only terminal."
            addErrorLog(message)
            _state.update { it.copy(lastActionMessage = message, lastActionSuccess = false) }
            return
        }
        if (_state.value.isSending) return
        viewModelScope.launch {
            if (_state.value.connectionState != BluetoothManager.ConnectionState.CONNECTED) {
                val message = "Device disconnected: connect the adapter first."
                addErrorLog(message)
                _state.update { it.copy(lastActionMessage = message, lastActionSuccess = false) }
                return@launch
            }

            _state.update { it.copy(isSending = true, lastActionMessage = null) }
            addSentLog(command)
            try {
                val result = bluetoothManager.executeCommand(command, CommandCategory.READ_ONLY)
                val success = result.status == CommandStatus.ACKNOWLEDGED && result.isSuccess
                val message = if (success) {
                    "Diagnostic command acknowledged by the ECU."
                } else {
                    "Response not confirmed: ${result.status} (${result.statusText})."
                }
                _state.update { it.copy(lastActionMessage = message, lastActionSuccess = success) }
                if (!success) addErrorLog(message) else addSystemLog(message)
            } catch (e: Exception) {
                val message = "Command failed: ${e.message}"
                addErrorLog(message)
                _state.update { it.copy(lastActionMessage = message, lastActionSuccess = false) }
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminalLog = emptyList()) }
        addSystemLog("Terminal log cleared.")
    }

    fun clearAllDTCs() {
        if (_state.value.isSending) return
        viewModelScope.launch {
            if (_state.value.connectionState != BluetoothManager.ConnectionState.CONNECTED || !_state.value.canClearDtc) {
                val message = "DTC clearing unavailable: no connection or the USB transport is read-only."
                addErrorLog(message)
                _state.update { it.copy(lastActionMessage = message, lastActionSuccess = false) }
                return@launch
            }
            _state.update { it.copy(isSending = true, lastActionMessage = null) }
            try {
                addSentLog("14 FF 00")
                val result = bluetoothManager.executeCommand("14 FF 00", CommandCategory.READ_ONLY)
                val confirmed = result.status == CommandStatus.ACKNOWLEDGED && result.isSuccess
                if (confirmed) {
                    dtcDao.clearAll()
                    addSystemLog("DTC clearing acknowledged by the ECU. Local DTC history cleared.")
                    _state.update { it.copy(lastActionMessage = "DTC clearing acknowledged by the ECU.", lastActionSuccess = true) }
                } else {
                    val message = "DTCs NOT cleared: ECU acknowledgement missing (${result.status}). Local history preserved."
                    addErrorLog(message)
                    _state.update { it.copy(lastActionMessage = message, lastActionSuccess = false) }
                }
            } catch (e: Exception) {
                val message = "DTC clearing error: ${e.message}. Local history preserved."
                addErrorLog(message)
                _state.update { it.copy(lastActionMessage = message, lastActionSuccess = false) }
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    fun deleteDTC(dtcRecord: DTCRecord) {
        viewModelScope.launch {
            dtcDao.delete(dtcRecord)
        }
    }

    private fun addSentLog(text: String) {
        val entry = TerminalEntry(text = "> $text", type = TerminalEntryType.SENT)
        appendLog(entry)
    }

    private fun addReceivedLog(text: String) {
        val entry = TerminalEntry(text = text, type = TerminalEntryType.RECEIVED)
        appendLog(entry)
    }

    private fun addSystemLog(text: String) {
        val entry = TerminalEntry(text = "[SYS] $text", type = TerminalEntryType.SYSTEM)
        appendLog(entry)
    }

    private fun addErrorLog(text: String) {
        val entry = TerminalEntry(text = "[ERR] $text", type = TerminalEntryType.ERROR)
        appendLog(entry)
    }

    private fun appendLog(entry: TerminalEntry) {
        val updatedList = _state.value.terminalLog + entry
        val trimmedList = if (updatedList.size > 200) updatedList.takeLast(200) else updatedList
        _state.value = _state.value.copy(terminalLog = trimmedList)
    }

    private fun parseDTCResponse(response: String) {
        viewModelScope.launch {
            try {
                val dtcCodes = OBD2Parser.parseDTC(response)
                if (dtcCodes.isNotEmpty()) {
                    dtcCodes.forEach { rawCode ->
                        val normalizedCode = normalizeDTC(rawCode)
                        val dtcRecord = DTCRecord(
                            code = normalizedCode,
                            description = decodeDTC(normalizedCode),
                            status = "Active"
                        )
                        dtcDao.insert(dtcRecord)
                    }
                    addSystemLog("Detected and decoded ${dtcCodes.size} Lotus ABS DTC(s).")
                }
            } catch (e: Exception) {
                // Ignore parse errors for non-DTC raw responses
            }
        }
    }

    private fun normalizeDTC(code: String): String {
        val clean = code.uppercase().trim()
        // Convert P-prefix to C-prefix for ABS specific chassis DTCs
        return when {
            clean == "P0035" -> "C0035"
            clean == "P0040" -> "C0040"
            clean == "P0045" -> "C0045"
            clean == "P0050" -> "C0050"
            clean == "P0060" -> "C0060"
            clean == "P0065" -> "C0065"
            clean == "P0070" -> "C0070"
            clean == "P0075" -> "C0075"
            clean == "P0080" -> "C0080"
            clean == "P0085" -> "C0085"
            clean == "P0090" -> "C0090"
            clean == "P0095" -> "C0095"
            clean == "P0110" -> "C0110"
            clean == "P0121" -> "C0121"
            clean == "P0161" -> "C0161"
            clean == "P0232" -> "C0232"
            clean == "P0245" -> "C0245"
            clean == "P0550" -> "C0550"
            clean == "P0800" -> "C0800"
            else -> clean
        }
    }

    fun decodeDTC(code: String): String {
        val cleanCode = code.uppercase().trim().replace(" ", "").replace("-", "")
        return when {
            cleanCode.contains("C0035") || cleanCode.contains("0035") ->
                "Front left wheel speed sensor (LF): short circuit, open circuit, or missing signal"
            cleanCode.contains("C0040") || cleanCode.contains("0040") ->
                "Front right wheel speed sensor (RF): short circuit, open circuit, or missing signal"
            cleanCode.contains("C0045") || cleanCode.contains("0045") ->
                "Rear left wheel speed sensor (LR): short circuit, open circuit, or missing signal"
            cleanCode.contains("C0050") || cleanCode.contains("0050") ->
                "Rear right wheel speed sensor (RR): short circuit, open circuit, or missing signal"
            cleanCode.contains("C0060") || cleanCode.contains("0060") ->
                "Front left outlet (DUMP) solenoid (LF): electrical circuit fault"
            cleanCode.contains("C0065") || cleanCode.contains("0065") ->
                "Front left inlet (ISO) solenoid (LF): electrical circuit fault"
            cleanCode.contains("C0070") || cleanCode.contains("0070") ->
                "Front right outlet (DUMP) solenoid (RF): electrical circuit fault"
            cleanCode.contains("C0075") || cleanCode.contains("0075") ->
                "Front right inlet (ISO) solenoid (RF): electrical circuit fault"
            cleanCode.contains("C0080") || cleanCode.contains("0080") ->
                "Rear left outlet (DUMP) solenoid (LR): electrical circuit fault"
            cleanCode.contains("C0085") || cleanCode.contains("0085") ->
                "Rear left inlet (ISO) solenoid (LR): electrical circuit fault"
            cleanCode.contains("C0090") || cleanCode.contains("0090") ->
                "Rear right outlet (DUMP) solenoid (RR): electrical circuit fault"
            cleanCode.contains("C0095") || cleanCode.contains("0095") ->
                "Rear right inlet (ISO) solenoid (RR): electrical circuit fault"
            cleanCode.contains("C0110") || cleanCode.contains("0110") ->
                "ABS return pump: stalled pump, open circuit, or short circuit"
            cleanCode.contains("C0121") || cleanCode.contains("0121") ->
                "ABS solenoid main relay: control circuit fault"
            cleanCode.contains("C0161") || cleanCode.contains("0161") ->
                "Brake light switch: circuit fault or implausible signal"
            cleanCode.contains("C0232") || cleanCode.contains("0232") ->
                "Brake warning lamp / ABS lamp: abnormal voltage or open circuit"
            cleanCode.contains("C0245") || cleanCode.contains("0245") ->
                "Erratic wheel speed signal: frequency error or missing tooth"
            cleanCode.contains("C0550") || cleanCode.contains("0550") ->
                "ABS ECU: internal memory error or hardware failure"
            cleanCode.contains("C0800") || cleanCode.contains("0800") ->
                "ABS ECU supply voltage: too high or too low (out of range)"
            cleanCode.contains("P0100") -> "Mass air flow sensor (MAF): circuit malfunction"
            cleanCode.contains("P0110") -> "Intake air temperature sensor (IAT): circuit malfunction"
            cleanCode.contains("P0120") -> "Throttle position sensor (TPS): circuit malfunction"
            cleanCode.contains("P0300") -> "Random / multiple cylinder misfires detected"
            cleanCode.contains("U0121") -> "Lost communication with ABS / brake control module"
            else -> "Lotus ABS diagnostic trouble code ($code)"
        }
    }
}
