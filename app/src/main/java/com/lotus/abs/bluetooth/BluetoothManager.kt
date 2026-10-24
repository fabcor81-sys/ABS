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
import com.lotus.abs.usb.Kwp2000KLineTransport
import com.lotus.abs.usb.UsbDeviceItem
import com.lotus.abs.usb.UsbKLineManager
import com.lotus.abs.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BluetoothManager(private val context: Context) {

    enum class Transport {
        BLUETOOTH, USB
    }
    
    var activeTransport = Transport.BLUETOOTH
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Classic Bluetooth
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // BLE
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var bleConnecting = false

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Stream received data
    private val _receivedData = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val receivedData: SharedFlow<String> = _receivedData.asSharedFlow()

    // Scanning state
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null

    private var deviceType: DeviceType = DeviceType.UNKNOWN
    private var listeningActive = false
    
    val usbManager = UsbKLineManager(context)
    var kwpTransport: Kwp2000KLineTransport? = null

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
        activeTransport = Transport.BLUETOOTH
        stopScan()
        withContext(Dispatchers.IO) {
            try {
                _connectionState.emit(ConnectionState.CONNECTING)

                val isPairedInSettings = getPairedDevices().any { it.address == device.address }
                if (isPairedInSettings) {
                    Log.d("BluetoothManager", "Device is paired, connecting via Classic SPP")
                    try {
                        connectClassic(device)
                    } catch (e: Exception) {
                        Log.w("BluetoothManager", "Classic failed for paired device, trying BLE fallback: ${e.message}")
                        connectBLE(device)
                    }
                } else {
                    Log.d("BluetoothManager", "Device is un-paired, connecting via BLE")
                    try {
                        connectBLE(device)
                    } catch (e: Exception) {
                        Log.w("BluetoothManager", "BLE failed, trying Classic fallback: ${e.message}")
                        connectClassic(device)
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Connection failed: ${e.message}")
                _connectionState.emit(ConnectionState.ERROR)
                disconnect()
            }
        }
    }

    suspend fun connectUsbDevice(usbDeviceItem: UsbDeviceItem) {
        activeTransport = Transport.USB
        _connectionState.emit(ConnectionState.CONNECTING)
        
        withContext(Dispatchers.IO) {
            try {
                val driver = usbManager.findUsbDriverForAddress(usbDeviceItem.address)
                if (driver == null) {
                    Log.e("BluetoothManager", "USB Driver not found")
                    _connectionState.emit(ConnectionState.ERROR)
                    return@withContext
                }
                
                val port = driver.ports.firstOrNull()
                if (port == null) {
                    Log.e("BluetoothManager", "USB Port not found")
                    _connectionState.emit(ConnectionState.ERROR)
                    return@withContext
                }
                
                val permissionGranted = if (usbManager.hasPermission(driver)) true else
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        usbManager.requestPermission(driver) { granted ->
                            if (continuation.isActive) continuation.resume(granted)
                        }
                        continuation.invokeOnCancellation { usbManager.unregisterReceiver() }
                    }
                if (!permissionGranted) {
                    Log.e("BluetoothManager", "USB permission denied")
                    _connectionState.emit(ConnectionState.ERROR)
                    return@withContext
                }

                val usbConnection = usbManager.usbManager?.openDevice(driver.device)
                if (usbConnection == null) {
                    Log.e("BluetoothManager", "Cannot open USB device. Permissions?")
                    _connectionState.emit(ConnectionState.ERROR)
                    return@withContext
                }
                
                port.open(usbConnection)
                
                kwpTransport = Kwp2000KLineTransport(port)
                kwpTransport?.openPort(10400)
                
                val initSuccess = kwpTransport?.performFastInit { logMsg ->
                    Log.d("BluetoothManager", logMsg)
                    CoroutineScope(Dispatchers.IO).launch {
                        _receivedData.emit("[USB] $logMsg\n")
                    }
                }
                
                if (initSuccess == true) {
                    _connectionState.emit(ConnectionState.CONNECTED)
                    Log.d("BluetoothManager", "USB KWP2000 Connected")
                } else {
                    port.close()
                    _connectionState.emit(ConnectionState.ERROR)
                }
            } catch (e: Exception) {
                kwpTransport?.close()
                kwpTransport = null
                Log.e("BluetoothManager", "USB connection failed: ${e.message}")
                _connectionState.emit(ConnectionState.ERROR)
            }
        }
    }

    private suspend fun connectClassic(device: BluetoothDevice) {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "SecurityException cancelling discovery: ${e.message}")
        }

        var socket: BluetoothSocket? = null
        try {
            val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket?.connect()
        } catch (e: Exception) {
            Log.w("BluetoothManager", "Standard RFCOMM failed, trying fallback channel 1: ${e.message}")
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                socket = method.invoke(device, 1) as BluetoothSocket?
                socket?.connect()
            } catch (fallbackException: Exception) {
                Log.e("BluetoothManager", "Fallback RFCOMM failed: ${fallbackException.message}")
                throw fallbackException
            }
        }

        bluetoothSocket = socket
        inputStream = socket?.inputStream
        outputStream = socket?.outputStream

        deviceType = DeviceType.CLASSIC
        _connectionState.emit(ConnectionState.CONNECTED)
        Log.d("BluetoothManager", "Connected to ${device.name} (Classic SPP)")

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

                    val success = setupBLECharacteristics(gatt)
                    if (success) {
                        deviceType = DeviceType.BLE
                        Log.d("BluetoothManager", "BLE Characteristics configured: write=${writeCharacteristic?.uuid}, notify=${notifyCharacteristic?.uuid}")
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

            override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                Log.d("BluetoothManager", "onDescriptorWrite status=$status for ${descriptor?.characteristic?.uuid}")
                bleConnecting = false
                listeningActive = true
                try {
                    _connectionState.tryEmit(ConnectionState.CONNECTED)
                } catch (e: Exception) {
                    Log.e("BluetoothManager", "Error emitting connected: ${e.message}")
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

    private fun setupBLECharacteristics(gatt: BluetoothGatt): Boolean {
        writeCharacteristic = null
        notifyCharacteristic = null
        val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Generic Fallback: Search across all services
        for (service in gatt.services) {
            // Ignore generic access / attribute profiles
            if (service.uuid.toString().startsWith("0000180")) continue

            var foundWrite: BluetoothGattCharacteristic? = null
            var foundNotify: BluetoothGattCharacteristic? = null

            for (chara in service.characteristics) {
                val props = chara.properties
                val isWritable = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                val isNotifiable = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                val hasCCCD = chara.getDescriptor(cccdUUID) != null

                if (isWritable && foundWrite == null) {
                    foundWrite = chara
                }
                // Only consider it a notify characteristic if it actually has the CCCD descriptor
                if (isNotifiable && hasCCCD && foundNotify == null) {
                    foundNotify = chara
                }
            }

            if (foundWrite != null && foundNotify != null) {
                writeCharacteristic = foundWrite
                notifyCharacteristic = foundNotify
                enableBLEDataNotifications(gatt, foundNotify)
                Log.d("BluetoothManager", "Matched BLE characteristics in service ${service.uuid}: write ${foundWrite.uuid}, notify ${foundNotify.uuid}")
                return true
            }
        }

        // If we found a write char but no separate notify char with a CCCD, maybe it's the same characteristic
        if (writeCharacteristic != null && notifyCharacteristic == null) {
            val hasCCCD = writeCharacteristic!!.getDescriptor(cccdUUID) != null
            if (hasCCCD) {
                notifyCharacteristic = writeCharacteristic
                enableBLEDataNotifications(gatt, writeCharacteristic!!)
                Log.d("BluetoothManager", "Matched single BLE characteristic for write/notify: ${writeCharacteristic?.uuid}")
                return true
            }
        }

        return false
    }

    private fun enableBLEDataNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            val success = gatt.setCharacteristicNotification(characteristic, true)
            Log.d("BluetoothManager", "setCharacteristicNotification success=$success for ${characteristic.uuid}")

            val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor = characteristic.getDescriptor(cccdUUID)
            if (descriptor != null) {
                val supportsIndication = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                val descriptorValue = if (supportsIndication) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, descriptorValue)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = descriptorValue
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                Log.d("BluetoothManager", "Wrote CCCD descriptor for ${characteristic.uuid}")
            } else {
                bleConnecting = false
                listeningActive = true
                try {
                    _connectionState.tryEmit(ConnectionState.CONNECTED)
                } catch (e: Exception) {
                    Log.e("BluetoothManager", "Error emitting connected: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error enabling BLE notifications: ${e.message}")
            bleConnecting = false
        }
    }

    private fun handleReceivedBytes(value: ByteArray) {
        val receivedString = String(value, Charsets.US_ASCII)
        _receivedData.tryEmit(receivedString)
        FileLogger.logTerminal("<", receivedString.trim())
    }

    suspend fun sendCommand(command: String) {
        withContext(Dispatchers.IO) {
            try {
                if (activeTransport == Transport.USB) {
                    val result = kwpTransport?.sendDiagnosticReadCommand(command) { logMsg ->
                        Log.d("BluetoothManager", logMsg)
                        CoroutineScope(Dispatchers.IO).launch {
                            _receivedData.emit("[USB] $logMsg\n")
                        }
                    }
                    if (result != null && result.isSuccess) {
                        _receivedData.emit(result.payloadHex + "\n")
                    } else {
                        _receivedData.emit("[USB] ${result?.statusText ?: "NOT_CONNECTED"}\n")
                    }
                    return@withContext
                }

                when (deviceType) {
                    DeviceType.CLASSIC -> sendCommandClassic(command)
                    DeviceType.BLE -> sendCommandBLE(command)
                    else -> {
                        FileLogger.w("BluetoothManager", "Unknown device type for sending, attempting Classic SPP then BLE")
                        try {
                            sendCommandClassic(command)
                        } catch (e: Exception) {
                            sendCommandBLE(command)
                        }
                    }
                }
            } catch (e: Exception) {
                FileLogger.e("BluetoothManager", "Send failed: ${e.message}", e)
                _connectionState.emit(ConnectionState.ERROR)
            }
        }
    }

    private suspend fun sendCommandClassic(command: String) {
        val formattedCommand = if (command.endsWith("\r")) command else command + "\r"
        outputStream?.write(formattedCommand.toByteArray(Charsets.US_ASCII))
        outputStream?.flush()
        FileLogger.logTerminal(">", command.trim())
    }

    private suspend fun sendCommandBLE(command: String) {
        writeCharacteristic?.let { characteristic ->
            val formattedCommand = if (command.endsWith("\r")) command else command + "\r"
            val bytes = formattedCommand.toByteArray(Charsets.US_ASCII)

            val supportsWriteWithoutResponse = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            val writeType = if (supportsWriteWithoutResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.writeType = writeType

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result = bluetoothGatt?.writeCharacteristic(
                    characteristic,
                    bytes,
                    writeType
                )
                FileLogger.d("BluetoothManager", "writeCharacteristic (type $writeType) result code=$result")
                if (result != 0) {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    result = bluetoothGatt?.writeCharacteristic(
                        characteristic,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    FileLogger.d("BluetoothManager", "Retry writeCharacteristic (DEFAULT) result code=$result")
                }
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                @Suppress("DEPRECATION")
                val success = bluetoothGatt?.writeCharacteristic(characteristic)
                FileLogger.d("BluetoothManager", "writeCharacteristic success=$success")
            }
            FileLogger.logTerminal(">", command.trim())
        } ?: FileLogger.e("BluetoothManager", "Cannot send BLE command: writeCharacteristic is null")
    }

    private suspend fun startListeningClassic() {
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (listeningActive && bluetoothSocket?.isConnected == true) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val receivedString = String(buffer, 0, bytes, Charsets.US_ASCII)
                        _receivedData.emit(receivedString)
                        FileLogger.logTerminal("<", receivedString.trim())
                    } else if (bytes == -1) {
                        FileLogger.w("BluetoothManager", "InputStream reached EOF (-1)")
                        break
                    }
                } catch (e: IOException) {
                    Log.e("BluetoothManager", "Listen error: ${e.message}")
                    break
                }
            }
            listeningActive = false
        }
    }

    suspend fun disconnect() {
        stopScan()
        withContext(Dispatchers.IO) {
            try {
                listeningActive = false
                
                if (activeTransport == Transport.USB) {
                    kwpTransport?.close()
                    kwpTransport = null
                } else {
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
                        else -> {
                            bluetoothGatt?.close()
                            bluetoothGatt = null
                            bluetoothSocket?.close()
                        }
                    }
                }

                deviceType = DeviceType.UNKNOWN
                _connectionState.emit(ConnectionState.DISCONNECTED)
                Log.d("BluetoothManager", "Disconnected")
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Disconnect error: ${e.message}")
            }
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
