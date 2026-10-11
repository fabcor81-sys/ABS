package com.lotus.abs.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class HomeState(
    val connectionStatus: BluetoothManager.ConnectionState = BluetoothManager.ConnectionState.DISCONNECTED,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val batteryVoltage: Float = 0f,
    val selectedDevice: BluetoothDevice? = null,
    val ecuInfo: String = "Not Connected",
    val isInitializing: Boolean = false
)

class HomeViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        observeConnectionState()
        loadPairedDevices()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connectionState ->
                _state.emit(_state.value.copy(connectionStatus = connectionState))

                when (connectionState) {
                    BluetoothManager.ConnectionState.CONNECTED -> {
                        _state.emit(
                            _state.value.copy(
                                ecuInfo = "Connected to ELM327",
                                isInitializing = false
                            )
                        )
                        initializeELM327()
                    }
                    BluetoothManager.ConnectionState.DISCONNECTED -> {
                        _state.emit(_state.value.copy(ecuInfo = "Not Connected"))
                    }
                    BluetoothManager.ConnectionState.CONNECTING -> {
                        _state.emit(_state.value.copy(isInitializing = true))
                    }
                    BluetoothManager.ConnectionState.ERROR -> {
                        _state.emit(_state.value.copy(ecuInfo = "Connection Error", isInitializing = false))
                    }
                }
            }
        }
    }

    private fun loadPairedDevices() {
        val devices = bluetoothManager.getPairedDevices()
        _state.tryEmit(_state.value.copy(pairedDevices = devices))
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(selectedDevice = device, isInitializing = true))
            bluetoothManager.connectToDevice(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bluetoothManager.disconnect()
            _state.emit(
                _state.value.copy(
                    selectedDevice = null,
                    ecuInfo = "Not Connected",
                    batteryVoltage = 0f
                )
            )
        }
    }

    private fun initializeELM327() {
        viewModelScope.launch {
            try {
                bluetoothManager.sendCommand("AT Z")      // Reset
                bluetoothManager.sendCommand("AT E0")     // Echo off
                bluetoothManager.sendCommand("AT L0")     // Line feeds off
                bluetoothManager.sendCommand("AT SP 6")   // Set protocol KWP2000

                // Request battery voltage
                bluetoothManager.sendCommand("ATRV")
            } catch (e: Exception) {
                _state.emit(_state.value.copy(ecuInfo = "Initialization Error: ${e.message}"))
            }
        }
    }

    fun updateBatteryVoltage(voltage: Float) {
        _state.tryEmit(_state.value.copy(batteryVoltage = voltage))
    }

    fun refreshDeviceList() {
        loadPairedDevices()
    }
}
