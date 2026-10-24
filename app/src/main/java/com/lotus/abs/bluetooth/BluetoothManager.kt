package com.lotus.abs.bluetooth

import android.annotation.SuppressLint
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import com.lotus.abs.transport.DiagnosticCommandResult
import com.lotus.abs.transport.DiagnosticTransport
import com.lotus.abs.usb.Kwp2000KLineTransport
import com.lotus.abs.usb.UsbDeviceItem
import com.lotus.abs.usb.UsbKLineManager
import com.lotus.abs.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

open class BluetoothManager(private val context: Context? = null) : DiagnosticTransport {

    enum class Transport {
        BLUETOOTH, USB
    }

    var activeTransport = Transport.BLUETOOTH
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (_: Throwable) {
            null
        }
    }

    // Transport Mutex to serialize diagnostic requests and prevent concurrent access
    val transportMutex = Mutex()

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

    override val transportName: String
        get() = if (activeTransport == Transport.USB) "USB KKL 409.1" else "Bluetooth ELM327"

    override val isConnected: Boolean
        get() = (_connectionState.value == ConnectionState.CONNECTED)

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

    private val scanHandler by lazy { Handler(Looper.getMainLooper()) }
    private var scanCallback: ScanCallback? = null

    private var deviceType: DeviceType = DeviceType.UNKNOWN
    private var listeningActive = false

    val usbManager = UsbKLineManager(context)
    var kwpTransport: Kwp2000KLineTransport? = null
    private var usbDetachReceiver: BroadcastReceiver? = null
    private var currentUsbAddress: String? = null

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    enum class DeviceType {
        CLASSIC, BLE, UNKNOWN
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerUsbDetachReceiver(connectedAddress: String? = null) {
        if (usbDetachReceiver != null) return
        currentUsbAddress = connectedAddress
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        usbDetachReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent?.action) {
                    val detachedDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val devAddr = if (detachedDevice != null) {
                        "USB:${String.format("%04X", detachedDevice.vendorId)}:${String.format("%04X", detachedDevice.productId)}:${detachedDevice.deviceId}"
                    } else null

                    val matches = (currentUsbAddress == null || devAddr == null || devAddr == currentUsbAddress)

                    Log.d("BluetoothManager", "USB Device detached event: ${detachedDevice?.deviceName}, matches=$matches")
                    if (activeTransport == Transport.USB && matches) {
                        CoroutineScope(Dispatchers.IO).launch {
                            handleUsbDetached()
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(usbDetachReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context?.registerReceiver(usbDetachReceiver, filter)
        }
        Log.d("BluetoothManager", "USB Detach receiver registered")
    }

    fun unregisterUsbDetachReceiver() {
        try {
            usbDetachReceiver?.let {
                context?.unregisterReceiver(it)
                usbDetachReceiver = null
                currentUsbAddress = null
            }
        } catch (_: Exception) {}
    }

    suspend fun handleUsbDetached() {
        FileLogger.w("BluetoothManager", "USB device detached physically. Cleaning transport.")
        withContext(Dispatchers.IO) {
            kwpTransport?.close()
            kwpTransport = null
            _connectionState.emit(ConnectionState.DISCONNECTED)
            _receivedData.emit("[USB] USB DISCONNECTED: Cavo o adattatore rimosso.\n")
        }
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
                    registerUsbDetachReceiver(usbDeviceItem.address)
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

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleReceivedBytes(value)
            }

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

        for (service in gatt.services) {
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

    override suspend fun executeCommand(
        commandHex: String,
        category: CommandCategory,
        timeoutMs: Long
    ): DiagnosticCommandResult {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            return DiagnosticCommandResult(
                status = CommandStatus.DISCONNECTED,
                isSuccess = false,
                requestHex = commandHex,
                statusText = "Transport DISCONNECTED"
            )
        }

        return transportMutex.withLock {
            withContext(Dispatchers.IO) {
                if (activeTransport == Transport.USB) {
                    return@withContext kwpTransport?.executeCommand(commandHex, category, timeoutMs)
                        ?: DiagnosticCommandResult(
                            status = CommandStatus.DISCONNECTED,
                            isSuccess = false,
                            requestHex = commandHex,
                            statusText = "USB Transport is null"
                        )
                } else {
                    return@withContext executeBluetoothCommandStructured(commandHex, category, timeoutMs)
                }
            }
        }
    }

    private suspend fun executeBluetoothCommandStructured(
        commandHex: String,
        category: CommandCategory,
        timeoutMs: Long
    ): DiagnosticCommandResult {
        val cleanCmd = commandHex.replace(" ", "").uppercase(Locale.US)
        val serviceByteHex = if (cleanCmd.length >= 2) cleanCmd.substring(0, 2) else ""
        val expectedPosServiceHex = try {
            String.format("%02X", (serviceByteHex.toInt(16) + 0x40) and 0xFF)
        } catch (_: Exception) { "" }

        var responsePayload = ""
        var commandTransmitted = false

        val job = CoroutineScope(Dispatchers.IO).launch {
            receivedData.collect { data ->
                responsePayload += data.replace("\r", "")
            }
        }

        try {
            when (deviceType) {
                DeviceType.CLASSIC -> sendCommandClassic(commandHex)
                DeviceType.BLE -> sendCommandBLE(commandHex)
                else -> sendCommandClassic(commandHex)
            }
            commandTransmitted = true
        } catch (e: Exception) {
            job.cancel()
            return DiagnosticCommandResult(
                status = CommandStatus.COMMUNICATION_ERROR,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = "Write error: ${e.message}"
            )
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (responsePayload.contains("\n") || responsePayload.contains(">")) {
                val lines = responsePayload.split("\n", "\r", ">").map { it.trim().replace(" ", "").uppercase(Locale.US) }
                for (line in lines) {
                    if (expectedPosServiceHex.isNotEmpty() && line.contains(expectedPosServiceHex)) {
                        job.cancel()
                        return DiagnosticCommandResult(
                            status = CommandStatus.ACKNOWLEDGED,
                            isSuccess = true,
                            requestHex = cleanCmd,
                            responseHex = line,
                            statusText = "ACKNOWLEDGED: ECU returned positive response $line"
                        )
                    }
                    if (line.startsWith("7F") || line.contains("7F${serviceByteHex}")) {
                        job.cancel()
                        val nrc = if (line.length >= 6) line.substring(4, 6) else null
                        return DiagnosticCommandResult(
                            status = CommandStatus.NEGATIVE_RESPONSE,
                            isSuccess = false,
                            requestHex = cleanCmd,
                            responseHex = line,
                            statusText = "NEGATIVE_RESPONSE: ECU returned NRC $line",
                            nrcCode = nrc
                        )
                    }
                    if (line.contains("NODATA") || line.contains("CANERROR") || line.contains("BUSERROR")) {
                        job.cancel()
                        return DiagnosticCommandResult(
                            status = CommandStatus.COMMUNICATION_ERROR,
                            isSuccess = false,
                            requestHex = cleanCmd,
                            statusText = "ELM327 error: $line"
                        )
                    }
                }
            }
            delay(50)
        }

        job.cancel()

        if (commandTransmitted) {
            return DiagnosticCommandResult(
                status = CommandStatus.TRANSMITTED,
                isSuccess = false,
                requestHex = cleanCmd,
                statusText = "TRANSMITTED: Command written over Bluetooth, but no positive ECU ACK received"
            )
        }

        return DiagnosticCommandResult(
            status = CommandStatus.TIMEOUT,
            isSuccess = false,
            requestHex = cleanCmd,
            statusText = "TIMEOUT: No response within ${timeoutMs}ms"
        )
    }

    suspend fun sendCommand(command: String) {
        executeCommand(command, CommandCategory.READ_ONLY)
    }

    suspend fun sendCommandConfirmed(
        command: String,
        category: CommandCategory = CommandCategory.ACTUATOR_CONTROL,
        timeoutMs: Long = 1500
    ): DiagnosticCommandResult {
        return executeCommand(command, category, timeoutMs)
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

    override suspend fun disconnect() {
        stopScan()
        unregisterUsbDetachReceiver()
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
