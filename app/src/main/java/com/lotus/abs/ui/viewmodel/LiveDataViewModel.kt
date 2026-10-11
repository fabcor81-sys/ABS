package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveDataState(
    val ecuId: String = "--",
    val designCode: String = "--",
    val hwVersion: String = "--",
    val swVersion: String = "--",
    val batteryVoltage: String = "--",
    val speedFL: String = "--",
    val speedFR: String = "--",
    val speedRL: String = "--",
    val speedRR: String = "--",
    val isPolling: Boolean = false
)

class LiveDataViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {
    private val _state = MutableStateFlow(LiveDataState())
    val state: StateFlow<LiveDataState> = _state

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
                    val lines = responseBuffer.split("\n").map { it.trim().replace(" ", "") }
                    for (line in lines) {
                        if (line.contains("5A80")) _state.update { it.copy(ecuId = line.substringAfter("5A80")) }
                        else if (line.contains("5A91")) _state.update { it.copy(designCode = line.substringAfter("5A91")) }
                        else if (line.contains("5A92")) _state.update { it.copy(hwVersion = line.substringAfter("5A92")) }
                        else if (line.contains("5A94")) _state.update { it.copy(swVersion = line.substringAfter("5A94")) }
                        else if (line.contains("6130")) _state.update { it.copy(batteryVoltage = line.substringAfter("6130")) }
                        else if (line.contains("6131")) _state.update { it.copy(speedFL = line.substringAfter("6131")) }
                        else if (line.contains("6132")) _state.update { it.copy(speedFR = line.substringAfter("6132")) }
                        else if (line.contains("6133")) _state.update { it.copy(speedRL = line.substringAfter("6133")) }
                        else if (line.contains("6134")) _state.update { it.copy(speedRR = line.substringAfter("6134")) }
                    }
                    responseBuffer = ""
                }
            }
        }
    }

    fun togglePolling() {
        val currentlyPolling = _state.value.isPolling
        _state.update { it.copy(isPolling = !currentlyPolling) }
        if (!currentlyPolling) {
            startPolling()
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            val commands = listOf(
                "1A 80", "1A 91", "1A 92", "1A 94",
                "21 30", "21 31", "21 32", "21 33", "21 34"
            )
            while (_state.value.isPolling) {
                for (cmd in commands) {
                    if (!_state.value.isPolling) break
                    bluetoothManager.sendCommand(cmd)
                    delay(300) 
                }
                delay(1000) 
            }
        }
    }
}
