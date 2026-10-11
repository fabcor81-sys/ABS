package com.lotus.abs.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.data.entity.SensorData
import com.lotus.abs.data.database.LotusAbsDatabase
import com.lotus.abs.obd2.OBD2Commands
import com.lotus.abs.obd2.OBD2Parser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class DashboardState(
    val rpm: Int = 0,
    val speed: Int = 0,
    val temperature: Float = 0f,
    val pressure: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null
)

class DashboardViewModel(
    private val bluetoothManager: BluetoothManager,
    private val database: LotusAbsDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    private val sensorDao = database.sensorDao()

    init {
        observeBluetoothData()
    }

    private fun observeBluetoothData() {
        viewModelScope.launch {
            bluetoothManager.receivedData.collect { data ->
                if (data.isNotEmpty()) {
                    parseAndUpdateData(data)
                }
            }
        }
    }

    private fun parseAndUpdateData(response: String) {
        val currentState = _state.value
        var updatedRPM = currentState.rpm
        var updatedSpeed = currentState.speed
        var updatedTemp = currentState.temperature
        var updatedPressure = currentState.pressure

        when {
            response.contains("010C") -> updatedRPM = OBD2Parser.parseRPM(response)
            response.contains("010D") -> updatedSpeed = OBD2Parser.parseSpeed(response)
            response.contains("0105") -> updatedTemp = OBD2Parser.parseTemperature(response)
            response.contains("010A") -> updatedPressure = OBD2Parser.parsePressure(response)
        }

        val newState = currentState.copy(
            rpm = updatedRPM,
            speed = updatedSpeed,
            temperature = updatedTemp,
            pressure = updatedPressure
        )

        _state.tryEmit(newState)

        // Save to database
        viewModelScope.launch {
            sensorDao.insert(
                SensorData(
                    rpm = updatedRPM,
                    speed = updatedSpeed,
                    temperature = updatedTemp,
                    pressure = updatedPressure
                )
            )
        }
    }

    fun requestRPM() {
        viewModelScope.launch {
            bluetoothManager.sendCommand(OBD2Commands.ENGINE_RPM)
        }
    }

    fun requestSpeed() {
        viewModelScope.launch {
            bluetoothManager.sendCommand(OBD2Commands.VEHICLE_SPEED)
        }
    }

    fun requestTemperature() {
        viewModelScope.launch {
            bluetoothManager.sendCommand(OBD2Commands.COOLANT_TEMP)
        }
    }

    fun requestPressure() {
        viewModelScope.launch {
            bluetoothManager.sendCommand(OBD2Commands.FUEL_PRESSURE)
        }
    }

    fun startDataCollection() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true))
            try {
                bluetoothManager.sendCommand(OBD2Commands.ENGINE_RPM)
                bluetoothManager.sendCommand(OBD2Commands.VEHICLE_SPEED)
                bluetoothManager.sendCommand(OBD2Commands.COOLANT_TEMP)
                bluetoothManager.sendCommand(OBD2Commands.FUEL_PRESSURE)
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e.message))
            }
        }
    }
}
