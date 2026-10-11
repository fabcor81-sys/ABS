package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleedState(
    val terminalLog: List<TerminalEntry> = emptyList(),
    val activeOperation: String? = null,
    val timeRemainingSec: Int = 0
)

class BleedViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {
    private val _state = MutableStateFlow(BleedState())
    val state: StateFlow<BleedState> = _state

    init {
        observeBluetoothData()
    }

    private fun observeBluetoothData() {
        viewModelScope.launch {
            var responseBuffer = ""
            bluetoothManager.receivedData.collect { data ->
                val cleaned = data.replace("\r", "")
                responseBuffer += cleaned

                if (responseBuffer.contains("\n") || responseBuffer.endsWith(">")) {
                    val lines = responseBuffer.split("\n")
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            addReceivedLog(trimmed)
                        }
                    }
                    responseBuffer = ""
                }
            }
        }
    }

    fun runBleedSequence(name: String, onCommand: String, offCommand: String, durationSec: Int) {
        if (_state.value.activeOperation != null) return

        viewModelScope.launch {
            _state.update { it.copy(activeOperation = name, timeRemainingSec = durationSec) }
            addSystemLog("Starting $name sequence...")

            addSentLog(onCommand)
            bluetoothManager.sendCommand(onCommand)

            for (i in durationSec downTo 1) {
                _state.update { it.copy(timeRemainingSec = i) }
                delay(1000)
            }

            addSentLog(offCommand)
            bluetoothManager.sendCommand(offCommand)

            addSystemLog("Finished $name sequence.")
            _state.update { it.copy(activeOperation = null, timeRemainingSec = 0) }
        }
    }
    
    fun sendCommand(command: String) {
        viewModelScope.launch {
            addSentLog(command)
            bluetoothManager.sendCommand(command)
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminalLog = emptyList()) }
    }

    private fun addSentLog(text: String) {
        appendLog(TerminalEntry(text = "> $text", type = TerminalEntryType.SENT))
    }

    private fun addReceivedLog(text: String) {
        appendLog(TerminalEntry(text = text, type = TerminalEntryType.RECEIVED))
    }

    private fun addSystemLog(text: String) {
        appendLog(TerminalEntry(text = "[SYS] $text", type = TerminalEntryType.SYSTEM))
    }

    private fun appendLog(entry: TerminalEntry) {
        _state.update {
            val updated = it.terminalLog + entry
            val trimmed = if (updated.size > 200) updated.takeLast(200) else updated
            it.copy(terminalLog = trimmed)
        }
    }
}
