package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.util.FileLogger
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
        observeBluetoothConnection()
        observeBluetoothData()
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connectionState ->
                if (connectionState != BluetoothManager.ConnectionState.CONNECTED) {
                    if (_state.value.isPolling) {
                        stopPolling()
                        FileLogger.i("LiveDataViewModel", "Live Data polling auto-stopped due to connection loss.")
                    }
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

                if (responseBuffer.contains("\n") || responseBuffer.endsWith(">")) {
                    val lines = responseBuffer.split("\n").map { it.trim().replace(" ", "") }
                    for (line in lines) {
                        if (line.contains("BLOCKED_NOT_IN_WHITELIST")) {
                            FileLogger.w("LiveDataViewModel", "Command blocked by USB Whitelist: $line")
                            continue
                        }

                        if (line.contains("5A80")) {
                            _state.update { it.copy(ecuId = extractValue(line, "5A80")) }
                        } else if (line.contains("5A91")) {
                            _state.update { it.copy(designCode = extractValue(line, "5A91")) }
                        } else if (line.contains("5A92")) {
                            _state.update { it.copy(hwVersion = extractValue(line, "5A92")) }
                        } else if (line.contains("5A94")) {
                            _state.update { it.copy(swVersion = extractValue(line, "5A94")) }
                        } else if (line.contains("6130")) {
                            _state.update { it.copy(batteryVoltage = extractVoltage(line, "6130")) }
                        } else if (line.contains("6131")) {
                            _state.update { it.copy(speedFL = extractSpeed(line, "6131")) }
                        } else if (line.contains("6132")) {
                            _state.update { it.copy(speedFR = extractSpeed(line, "6132")) }
                        } else if (line.contains("6133")) {
                            _state.update { it.copy(speedRL = extractSpeed(line, "6133")) }
                        } else if (line.contains("6134")) {
                            _state.update { it.copy(speedRR = extractSpeed(line, "6134")) }
                        } else if (line.startsWith("7F")) {
                            // Negative Response Code (NRC) from KWP2000
                            FileLogger.w("LiveDataViewModel", "ECU returned NRC: $line")
                        }
                    }
                    responseBuffer = ""
                }
            }
        }
    }

    private fun extractValue(rawLine: String, prefix: String): String {
        val payload = rawLine.substringAfter(prefix, "")
        if (payload.isEmpty()) return "Non supportato"
        return payload
    }

    private fun extractVoltage(rawLine: String, prefix: String = "6130"): String {
        val payload = rawLine.substringAfter(prefix, "")
        if (payload.isEmpty() || payload.length < 2) return "Non supportato"
        return try {
            val valByte = payload.substring(0, 2).toInt(16)
            val volts = valByte * 0.1f
            String.format(java.util.Locale.US, "%.1f V", volts)
        } catch (_: Exception) {
            "Non supportato"
        }
    }

    private fun extractSpeed(rawLine: String, prefix: String): String {
        val payload = rawLine.substringAfter(prefix, "")
        if (payload.isEmpty() || payload.length < 2) return "Non supportato"
        return try {
            val valByte = payload.substring(0, 2).toInt(16)
            "$valByte km/h"
        } catch (_: Exception) {
            "Non supportato"
        }
    }

    fun togglePolling() {
        if (_state.value.isPolling) {
            stopPolling()
        } else {
            if (bluetoothManager.connectionState.value == BluetoothManager.ConnectionState.CONNECTED) {
                _state.update { it.copy(isPolling = true) }
                startPolling()
            } else {
                FileLogger.w("LiveDataViewModel", "Cannot start Live Data polling: device not connected.")
            }
        }
    }

    fun stopPolling() {
        if (_state.value.isPolling) {
            _state.update { it.copy(isPolling = false) }
            FileLogger.i("LiveDataViewModel", "Live Data polling stopped.")
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            FileLogger.i("LiveDataViewModel", "Live Data polling started.")
            val commands = listOf(
                "1A 80", "1A 91", "1A 92", "1A 94",
                "21 30", "21 31", "21 32", "21 33", "21 34"
            )
            while (_state.value.isPolling && bluetoothManager.connectionState.value == BluetoothManager.ConnectionState.CONNECTED) {
                for (cmd in commands) {
                    if (!_state.value.isPolling || bluetoothManager.connectionState.value != BluetoothManager.ConnectionState.CONNECTED) break
                    bluetoothManager.sendCommand(cmd)
                    delay(350)
                }
                delay(800)
            }
            if (_state.value.isPolling) {
                stopPolling()
            }
        }
    }
}
