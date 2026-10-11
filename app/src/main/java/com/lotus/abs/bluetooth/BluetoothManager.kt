package com.lotus.abs.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
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

    private var deviceType: DeviceType = DeviceType.UNKNOWN
    private var listeningActive = false

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    enum class DeviceType {
        CLASSIC, BLE, UNKNOWN
    }

    suspend fun connectToDevice(device: BluetoothDevice) {
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
                        try {
                            _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                        } catch (e: Exception) {
                            Log.e("BluetoothManager", "Error emitting disconnected: ${e.message}")
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BluetoothManager", "Services discovered")

                    val service = gatt?.getService(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
                    writeCharacteristic = service?.getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))

                    if (writeCharacteristic != null) {
                        Log.d("BluetoothManager", "Write characteristic found")
                        try {
                            _connectionState.tryEmit(ConnectionState.CONNECTED)
                        } catch (e: Exception) {
                            Log.e("BluetoothManager", "Error emitting connected: ${e.message}")
                        }
                        listeningActive = true
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                value: ByteArray?
            ) {
                if (value != null) {
                    val receivedString = String(value)
                    try {
                        _receivedData.tryEmit(receivedString)
                    } catch (e: Exception) {
                        Log.e("BluetoothManager", "Error emitting data: ${e.message}")
                    }
                    Log.d("BluetoothManager", "BLE Received: $receivedString")
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
            characteristic.value = (command + "\r").toByteArray()
            bluetoothGatt?.writeCharacteristic(characteristic)
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
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                when (device.type) {
                    BluetoothDevice.TYPE_LE -> DeviceType.BLE
                    BluetoothDevice.TYPE_CLASSIC -> DeviceType.CLASSIC
                    else -> DeviceType.UNKNOWN
                }
            }
            else -> DeviceType.UNKNOWN
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
}
