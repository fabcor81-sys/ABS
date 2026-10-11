package com.lotus.abs.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val connectionStatus: BluetoothManager.ConnectionState = BluetoothManager.ConnectionState.DISCONNECTED,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
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
        observeScanning()
        loadPairedDevices()
        startScan()
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

    private fun observeScanning() {
        viewModelScope.launch {
            bluetoothManager.scannedDevices.collect { devices ->
                _state.emit(_state.value.copy(scannedDevices = devices))
            }
        }
        viewModelScope.launch {
            bluetoothManager.isScanning.collect { isScanning ->
                _state.emit(_state.value.copy(isScanning = isScanning))
            }
        }
    }

    private fun loadPairedDevices() {
        val devices = bluetoothManager.getPairedDevices()
        _state.tryEmit(_state.value.copy(pairedDevices = devices))
    }

    fun startScan() {
        bluetoothManager.startScan()
    }

    fun stopScan() {
        bluetoothManager.stopScan()
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            bluetoothManager.stopScan()
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
                delay(300)
                bluetoothManager.sendCommand("AT E0")     // Echo off
                delay(100)
                bluetoothManager.sendCommand("AT L0")     // Line feeds off
                delay(100)
                bluetoothManager.sendCommand("AT SP 6")   // Set protocol KWP2000
                delay(100)
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
        startScan()
    }
}
