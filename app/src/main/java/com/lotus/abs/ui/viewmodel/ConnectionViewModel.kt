package com.lotus.abs.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.usb.UsbDeviceItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class TransportType { BLUETOOTH, USB }

data class ConnectionScreenState(
    val connectionStatus: BluetoothManager.ConnectionState = BluetoothManager.ConnectionState.DISCONNECTED,
    val selectedTransport: TransportType = TransportType.BLUETOOTH,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val usbDevices: List<UsbDeviceItem> = emptyList(),
    val isScanning: Boolean = false,
    val batteryVoltage: Float = 0f,
    val selectedDevice: BluetoothDevice? = null,
    val selectedUsbDevice: UsbDeviceItem? = null,
    val ecuInfo: String = "Not Connected",
    val isInitializing: Boolean = false
)

class ConnectionViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionScreenState())
    val state: StateFlow<ConnectionScreenState> = _state

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
                                ecuInfo = if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB)
                                    "Connected to KKL 409.1 / EBC430"
                                else "Connected to ELM327",
                                isInitializing = false
                            )
                        )
                        if (bluetoothManager.activeTransport == BluetoothManager.Transport.BLUETOOTH) {
                            initializeELM327()
                        }
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

    fun loadPairedDevices() {
        if (_state.value.selectedTransport == TransportType.USB) {
            val usbDevices = bluetoothManager.usbManager.getConnectedUsbDevices()
            _state.tryEmit(_state.value.copy(usbDevices = usbDevices))
        } else {
            val devices = bluetoothManager.getPairedDevices()
            _state.tryEmit(_state.value.copy(pairedDevices = devices))
        }
    }
    
    fun refreshDeviceList() {
        loadPairedDevices()
    }

    fun startScan() {
        if (_state.value.selectedTransport == TransportType.USB) {
            refreshDeviceList()
        } else {
            bluetoothManager.startScan()
        }
    }

    fun stopScan() {
        if (_state.value.selectedTransport != TransportType.USB) {
            bluetoothManager.stopScan()
        }
    }
    
    fun setTransport(transport: TransportType) {
        _state.value = _state.value.copy(selectedTransport = transport)
        refreshDeviceList()
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            bluetoothManager.stopScan()
            _state.emit(_state.value.copy(selectedDevice = device, isInitializing = true))
            bluetoothManager.connectToDevice(device)
        }
    }
    
    fun connectUsb(device: UsbDeviceItem) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(selectedUsbDevice = device, isInitializing = true))
            bluetoothManager.connectUsbDevice(device)
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

}
