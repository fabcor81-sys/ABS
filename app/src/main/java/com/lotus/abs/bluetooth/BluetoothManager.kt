package com.lotus.abs.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
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
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _receivedData = MutableStateFlow<String>("")
    val receivedData: StateFlow<String> = _receivedData

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    suspend fun connectToDevice(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            try {
                _connectionState.emit(ConnectionState.CONNECTING)

                bluetoothSocket = device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                _connectionState.emit(ConnectionState.CONNECTED)
                Log.d("BluetoothManager", "Connected to ${device.name}")

                startListening()
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Connection failed: ${e.message}")
                _connectionState.emit(ConnectionState.ERROR)
                disconnect()
            }
        }
    }

    suspend fun sendCommand(command: String) {
        withContext(Dispatchers.IO) {
            try {
                outputStream?.write((command + "\r").toByteArray())
                outputStream?.flush()
                Log.d("BluetoothManager", "Sent: $command")
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Send failed: ${e.message}")
                _connectionState.emit(ConnectionState.ERROR)
            }
        }
    }

    private suspend fun startListening() {
        withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(1024)
                var bytes: Int

                while (bluetoothSocket?.isConnected == true) {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val receivedString = String(buffer, 0, bytes)
                        _receivedData.emit(receivedString)
                        Log.d("BluetoothManager", "Received: $receivedString")
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Listen error: ${e.message}")
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                bluetoothSocket?.close()
                _connectionState.emit(ConnectionState.DISCONNECTED)
                Log.d("BluetoothManager", "Disconnected")
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Disconnect error: ${e.message}")
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
}
