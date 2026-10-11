package com.lotus.abs.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Classic Bluetooth
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // BLE
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var bleConnecting = false

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _receivedData = MutableStateFlow<String>("")
    val receivedData: StateFlow<String> = _receivedData

    // Scanning state
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null

    private var deviceType: DeviceType = DeviceType.UNKNOWN
    private var listeningActive = false

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    enum class DeviceType {
        CLASSIC, BLE, UNKNOWN
    }

    fun startScan(scanDurationMs: Long = 10000) {
        if (_isScanning.value) return

        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) {
                Log.e("BluetoothManager", "BluetoothLeScanner unavailable or Bluetooth is off")
                return
            }

            _scannedDevices.value = emptyList()
            _isScanning.value = true

            val deviceMap = mutableMapOf<String, BluetoothDevice>()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let { device ->
                        try {
                            deviceMap[device.address] = device
                            _scannedDevices.value = deviceMap.values.toList()
                        } catch (e: SecurityException) {
                            Log.e("BluetoothManager", "SecurityException reading device info: ${e.message}")
                        }
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { result ->
                        result.device?.let { device ->
                            try {
                                deviceMap[device.address] = device
                            } catch (e: SecurityException) {
                                Log.e("BluetoothManager", "SecurityException reading device info: ${e.message}")
                            }
                        }
                    }
                    _scannedDevices.value = deviceMap.values.toList()
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BluetoothManager", "BLE Scan failed with code: $errorCode")
                    _isScanning.value = false
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(null, settings, scanCallback)
            Log.d("BluetoothManager", "BLE scan started")

            scanHandler.postDelayed({
                stopScan()
            }, scanDurationMs)

        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "SecurityException during startScan: ${e.message}")
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Exception during startScan: ${e.message}")
            _isScanning.value = false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        try {
            scanCallback?.let {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            }
            scanCallback = null
            Log.d("BluetoothManager", "BLE scan stopped")
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "SecurityException during stopScan: ${e.message}")
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Exception during stopScan: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        withContext(Dispatchers.IO) {
            try {
                _connectionState.emit(ConnectionState.CONNECTING)

                deviceType = detectDeviceType(device)
                Log.d("BluetoothManager", "Device type detected: $deviceType (${device.name})")

                when (deviceType) {
                    DeviceType.CLASSIC -> connectClassic(device)
                    DeviceType.BLE -> connectBLE(device)
                    DeviceType.UNKNOWN -> {
                        Log.w("BluetoothManager", "Unknown device type, trying Classic first")
                        try {
                            connectClassic(device)
                            deviceType = DeviceType.CLASSIC
                        } catch (e: Exception) {
                            Log.d("BluetoothManager", "Classic failed, trying BLE")
                            deviceType = DeviceType.BLE
                            connectBLE(device)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Connection failed: ${e.message}")
                _connectionState.emit(ConnectionState.ERROR)
                disconnect()
            }
        }
    }

    private suspend fun connectClassic(device: BluetoothDevice) {
        bluetoothSocket = device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        )
        bluetoothSocket?.connect()

        inputStream = bluetoothSocket?.inputStream
        outputStream = bluetoothSocket?.outputStream

        _connectionState.emit(ConnectionState.CONNECTED)
        Log.d("BluetoothManager", "Connected to ${device.name} (Classic)")

        listeningActive = true
        startListeningClassic()
    }

    private suspend fun connectBLE(device: BluetoothDevice) {
        bleConnecting = true

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BluetoothManager", "BLE Connected")
                        bluetoothGatt = gatt
                        gatt?.discoverServices()
                    }
                    android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("BluetoothManager", "BLE Disconnected")
                        bluetoothGatt = null
                        bleConnecting = false
                        try {
                            _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                        } catch (e: Exception) {
                            Log.e("BluetoothManager", "Error emitting disconnected: ${e.message}")
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    Log.d("BluetoothManager", "Services discovered")

                    val chara = findBLECharacteristic(gatt)
                    if (chara != null) {
                        writeCharacteristic = chara
                        enableBLEDataNotifications(gatt, chara)

                        Log.d("BluetoothManager", "BLE Characteristic found and configured: ${chara.uuid}")
                        bleConnecting = false
                        listeningActive = true
                        try {
                            _connectionState.tryEmit(ConnectionState.CONNECTED)
                        } catch (e: Exception) {
                            Log.e("BluetoothManager", "Error emitting connected: ${e.message}")
                        }
                    } else {
                        Log.e("BluetoothManager", "No suitable BLE characteristic found")
                        bleConnecting = false
                        try {
                            _connectionState.tryEmit(ConnectionState.ERROR)
                        } catch (e: Exception) {
                            Log.e("BluetoothManager", "Error emitting error: ${e.message}")
                        }
                    }
                }
            }

            // Android 13+ (API 33+)
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleReceivedBytes(value)
            }

            // Pre-Android 13 (API < 33)
            @Deprecated("Deprecated in Java", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic?.value
                if (value != null) {
                    handleReceivedBytes(value)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        var timeout = 0
        while (bleConnecting && timeout < 50) {
            Thread.sleep(100)
            timeout++
        }
    }

    private fun findBLECharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        // Known ELM327 BLE service & characteristic UUID pairs
        val knownUUIDPairs = listOf(
            Pair("0000ffe0-0000-1000-8000-00805f9b34fb", "0000ffe1-0000-1000-8000-00805f9b34fb"),
            Pair("0000fff0-0000-1000-8000-00805f9b34fb", "0000fff1-0000-1000-8000-00805f9b34fb"),
            Pair("000018f0-0000-1000-8000-00805f9b34fb", "00002af0-0000-1000-8000-00805f9b34fb")
        )

        for ((serviceUUID, charUUID) in knownUUIDPairs) {
            val service = gatt.getService(UUID.fromString(serviceUUID))
            val chara = service?.getCharacteristic(UUID.fromString(charUUID))
            if (chara != null) return chara
        }

        // Fallback: iterate all services to find a characteristic supporting write & notify
        for (service in gatt.services) {
            for (chara in service.characteristics) {
                val props = chara.properties
                val isWritable = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                val isNotifiable = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                if (isWritable && isNotifiable) {
                    return chara
                }
            }
        }
        return null
    }

    private fun enableBLEDataNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true)
            val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor = characteristic.getDescriptor(cccdUUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error enabling BLE notifications: ${e.message}")
        }
    }

    private fun handleReceivedBytes(value: ByteArray) {
        val receivedString = String(value)
        try {
            _receivedData.tryEmit(receivedString)
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error emitting data: ${e.message}")
        }
        Log.d("BluetoothManager", "BLE Received: $receivedString")
    }

    suspend fun sendCommand(command: String) {
        withContext(Dispatchers.IO) {
            try {
                when (deviceType) {
                    DeviceType.CLASSIC -> sendCommandClassic(command)
                    DeviceType.BLE -> sendCommandBLE(command)
                    else -> {
                        Log.e("BluetoothManager", "Unknown device type for sending")
                        _connectionState.emit(ConnectionState.ERROR)
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Send failed: ${e.message}")
                _connectionState.emit(ConnectionState.ERROR)
            }
        }
    }

    private suspend fun sendCommandClassic(command: String) {
        outputStream?.write((command + "\r").toByteArray())
        outputStream?.flush()
        Log.d("BluetoothManager", "Sent (Classic): $command")
    }

    private suspend fun sendCommandBLE(command: String) {
        writeCharacteristic?.let { characteristic ->
            val bytes = (command + "\r").toByteArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    characteristic,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(characteristic)
            }
            Log.d("BluetoothManager", "Sent (BLE): $command")
        }
    }

    private suspend fun startListeningClassic() {
        withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(1024)
                var bytes: Int

                while (listeningActive && bluetoothSocket?.isConnected == true) {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val receivedString = String(buffer, 0, bytes)
                        _receivedData.emit(receivedString)
                        Log.d("BluetoothManager", "Received (Classic): $receivedString")
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Listen error: ${e.message}")
                listeningActive = false
            }
        }
    }

    suspend fun disconnect() {
        stopScan()
        withContext(Dispatchers.IO) {
            try {
                listeningActive = false

                when (deviceType) {
                    DeviceType.CLASSIC -> {
                        inputStream?.close()
                        outputStream?.close()
                        bluetoothSocket?.close()
                    }
                    DeviceType.BLE -> {
                        bluetoothGatt?.close()
                        bluetoothGatt = null
                    }
                    else -> {}
                }

                _connectionState.emit(ConnectionState.DISCONNECTED)
                Log.d("BluetoothManager", "Disconnected")
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Disconnect error: ${e.message}")
            }
        }
    }

    private fun detectDeviceType(device: BluetoothDevice): DeviceType {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    when (device.type) {
                        BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
                        else -> DeviceType.UNKNOWN
                    }
                }
                else -> DeviceType.UNKNOWN
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "SecurityException detecting device type: ${e.message}")
            DeviceType.UNKNOWN
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "SecurityException getting paired devices: ${e.message}")
            emptyList()
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled ?: false
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "SecurityException checking bluetooth state: ${e.message}")
            false
        }
    }
}
