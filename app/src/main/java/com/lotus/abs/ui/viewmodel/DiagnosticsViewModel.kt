package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.data.entity.DTCRecord
import com.lotus.abs.data.database.LotusAbsDatabase
import com.lotus.abs.obd2.OBD2Commands
import com.lotus.abs.obd2.OBD2Parser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class DiagnosticsState(
    val dtcList: List<DTCRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdate: Long = 0
)

class DiagnosticsViewModel(
    private val bluetoothManager: BluetoothManager,
    private val database: LotusAbsDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state

    private val dtcDao = database.dtcDao()

    init {
        loadDTCsFromDatabase()
        observeBluetoothDTC()
    }

    private fun loadDTCsFromDatabase() {
        viewModelScope.launch {
            dtcDao.getAllDTCs().collect { dtcList ->
                _state.emit(_state.value.copy(dtcList = dtcList))
            }
        }
    }

    private fun observeBluetoothDTC() {
        viewModelScope.launch {
            bluetoothManager.receivedData.collect { data ->
                if (data.contains("19 02") || data.contains("19") || data.contains("CLEARED")) {
                    parseDTCResponse(data)
                }
            }
        }
    }

    private fun parseDTCResponse(response: String) {
        viewModelScope.launch {
            try {
                val dtcCodes = OBD2Parser.parseDTC(response)
                if (response.contains("CLEARED")) {
                    dtcDao.clearAll()
                } else {
                    dtcCodes.forEach { code ->
                        val dtcRecord = DTCRecord(
                            code = code,
                            description = decodeDTC(code),
                            status = "Active"
                        )
                        dtcDao.insert(dtcRecord)
                    }
                }
                _state.emit(
                    _state.value.copy(
                        isLoading = false,
                        lastUpdate = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e.message, isLoading = false))
            }
        }
    }

    fun readDTC() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true))
            bluetoothManager.sendCommand(OBD2Commands.READ_DTC)
        }
    }

    fun clearDTC() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true))
            bluetoothManager.sendCommand(OBD2Commands.CLEAR_DTC)
        }
    }

    fun deleteDTC(dtcRecord: DTCRecord) {
        viewModelScope.launch {
            dtcDao.delete(dtcRecord)
        }
    }

    private fun decodeDTC(code: String): String {
        return when (code) {
            "P0100" -> "Mass Air Flow Circuit"
            "P0101" -> "Mass Air Flow Range/Performance"
            "P0102" -> "Mass Air Flow Low Input"
            "P0103" -> "Mass Air Flow High Input"
            "C0XXX" -> "ABS System Fault"
            "B0XXX" -> "Body/Chassis Fault"
            "U0XXX" -> "Network/Communication Fault"
            else -> "Unknown DTC: $code"
        }
    }
}
