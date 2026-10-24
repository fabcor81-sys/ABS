package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
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
                _state.emit(_state.value.copy(connectionState = connectionState))
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

    fun sendCommand(command: String) {
        viewModelScope.launch {
            if (_state.value.connectionState != BluetoothManager.ConnectionState.CONNECTED) {
                addErrorLog("Not connected to ELM327/Bluetooth device. Connect on Home tab first.")
            }
            addSentLog(command)
            _state.emit(_state.value.copy(isSending = true))
            try {
                bluetoothManager.sendCommand(command)
            } catch (e: Exception) {
                addErrorLog("Failed to send command '$command': ${e.message}")
            } finally {
                _state.emit(_state.value.copy(isSending = false))
            }
        }
    }

    fun clearTerminal() {
        _state.value = _state.value.copy(terminalLog = emptyList())
        addSystemLog("Terminal log cleared.")
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
                if (response.contains("CLEARED")) {
                    dtcDao.clearAll()
                    addSystemLog("DTC database cleared.")
                } else if (dtcCodes.isNotEmpty()) {
                    dtcCodes.forEach { code ->
                        val dtcRecord = DTCRecord(
                            code = code,
                            description = decodeDTC(code),
                            status = "Active"
                        )
                        dtcDao.insert(dtcRecord)
                    }
                    addSystemLog("Parsed ${dtcCodes.size} DTC code(s).")
                }
            } catch (e: Exception) {
                // Ignore parse errors for non-DTC raw responses
            }
        }
    }

    fun deleteDTC(dtcRecord: DTCRecord) {
        viewModelScope.launch {
            dtcDao.delete(dtcRecord)
        }
    }

    private fun decodeDTC(code: String): String {
        return when (code) {
            "C0035" -> "Front Left Wheel Speed Sensor Circuit"
            "C0040" -> "Front Right Wheel Speed Sensor Circuit"
            "C0045" -> "Rear Left Wheel Speed Sensor Circuit"
            "C0050" -> "Rear Right Wheel Speed Sensor Circuit"
            "C0110" -> "ABS Pump Motor Circuit Malfunction"
            "C0121" -> "ABS Valve Relay Circuit Malfunction"
            "C0161" -> "ABS Brake Switch Circuit"
            "C0245" -> "Wheel Speed Sensor Frequency Error"
            "C0550" -> "ECU Malfunction / Internal Error"
            "P0100" -> "Mass Air Flow Circuit"
            else -> "Lotus ABS Fault Code: $code"
        }
    }
}
