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
    val isInitializing: Boolean = false,
    val initializationError: Boolean = false
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
                                    "USB KKL 409.1: sessione collegata (identificazione ECU non verificata)"
                                else "ELM327 collegato (identificazione ECU non verificata)",
                                isInitializing = bluetoothManager.activeTransport == BluetoothManager.Transport.BLUETOOTH,
                                initializationError = false
                            )
                        )
                        if (bluetoothManager.activeTransport == BluetoothManager.Transport.BLUETOOTH) {
                            initializeELM327()
                        }
                    }
                    BluetoothManager.ConnectionState.DISCONNECTED -> {
                        _state.emit(_state.value.copy(ecuInfo = "Not Connected", isInitializing = false, initializationError = false))
                    }
                    BluetoothManager.ConnectionState.CONNECTING -> {
                        _state.emit(_state.value.copy(isInitializing = true, initializationError = false))
                    }
                    BluetoothManager.ConnectionState.ERROR -> {
                        _state.emit(_state.value.copy(ecuInfo = "Connection Error", isInitializing = false, initializationError = true))
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
        if (_state.value.selectedTransport == TransportType.BLUETOOTH) {
            bluetoothManager.startScan()
        }
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
        if (transport == TransportType.USB) bluetoothManager.stopScan()
        _state.value = _state.value.copy(selectedTransport = transport, selectedDevice = null, selectedUsbDevice = null)
        refreshDeviceList()
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            bluetoothManager.stopScan()
            _state.emit(_state.value.copy(selectedDevice = device, selectedUsbDevice = null, isInitializing = true))
            bluetoothManager.connectToDevice(device)
        }
    }
    
    fun connectUsb(device: UsbDeviceItem) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(selectedUsbDevice = device, selectedDevice = null, isInitializing = true))
            bluetoothManager.connectUsbDevice(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bluetoothManager.disconnect()
            _state.emit(
                _state.value.copy(
                    selectedDevice = null,
                    selectedUsbDevice = null,
                    ecuInfo = "Not Connected",
                    batteryVoltage = 0f
                )
            )
        }
    }

    private fun initializeELM327() {
        viewModelScope.launch {
            try {
                bluetoothManager.sendCommand("AT Z")          // Reset
                delay(1000)
                bluetoothManager.sendCommand("AT E0")         // Echo off
                delay(200)
                bluetoothManager.sendCommand("AT L0")         // Line feeds off
                delay(200)
                bluetoothManager.sendCommand("AT ST 32")       // ECU timeout
                delay(200)
                bluetoothManager.sendCommand("AT SH 812BF1")   // Set Header for EBC430
                delay(200)
                bluetoothManager.sendCommand("AT WM 812BF13E") // Set Wakeup message
                delay(200)
                bluetoothManager.sendCommand("AT SP 5")       // ISO 14230-4 KWP2000 Fast Init
                delay(200)
                bluetoothManager.sendCommand("ATRV")
                _state.emit(_state.value.copy(isInitializing = false, initializationError = false))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(ecuInfo = "Initialization Error: ${e.message}", isInitializing = false, initializationError = true))
            }
        }
    }

    fun updateBatteryVoltage(voltage: Float) {
        _state.tryEmit(_state.value.copy(batteryVoltage = voltage))
    }

}
