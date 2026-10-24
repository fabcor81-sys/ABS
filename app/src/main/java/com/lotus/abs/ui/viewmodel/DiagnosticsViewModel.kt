package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.data.database.LotusAbsDatabase
import com.lotus.abs.data.entity.DTCRecord
import com.lotus.abs.obd2.OBD2Parser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TerminalEntryType {
    SENT, RECEIVED, SYSTEM, ERROR
}

data class TerminalEntry(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val text: String,
    val type: TerminalEntryType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
)

data class DiagnosticsState(
    val terminalLog: List<TerminalEntry> = emptyList(),
    val customCommand: String = "",
    val dtcList: List<DTCRecord> = emptyList(),
    val isSending: Boolean = false,
    val connectionState: BluetoothManager.ConnectionState = BluetoothManager.ConnectionState.DISCONNECTED
)

class DiagnosticsViewModel(
    private val bluetoothManager: BluetoothManager,
    private val database: LotusAbsDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state

    private val dtcDao = database.dtcDao()

    init {
        observeBluetoothConnection()
        observeBluetoothData()
        loadDTCsFromDatabase()
        addSystemLog("Terminal initialized. Ready for Lotus ABS ECU commands.")
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connectionState ->
                _state.emit(_state.value.copy(connectionState = connectionState))
                when (connectionState) {
                    BluetoothManager.ConnectionState.CONNECTED -> {
                        addSystemLog("Device connected. Terminal active.")
                    }
                    BluetoothManager.ConnectionState.DISCONNECTED -> {
                        addSystemLog("Device disconnected.")
                    }
                    BluetoothManager.ConnectionState.ERROR -> {
                        addErrorLog("Bluetooth connection error.")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeBluetoothData() {
        viewModelScope.launch {
            var responseBuffer = ""

            bluetoothManager.receivedData.collect { data ->
                val cleaned = data.replace("\r", "")
                responseBuffer += cleaned

                while (responseBuffer.contains("\n")) {
                    val index = responseBuffer.indexOf("\n")
                    val line = responseBuffer.substring(0, index).trim()
                    if (line.isNotEmpty()) {
                        addReceivedLog(line)
                        parseDTCResponse(line)
                    }
                    responseBuffer = responseBuffer.substring(index + 1)
                }

                if (responseBuffer.trim() == ">" || responseBuffer.endsWith(">")) {
                    val trimmed = responseBuffer.trim()
                    if (trimmed.isNotEmpty() && trimmed != ">") {
                        addReceivedLog(trimmed)
                        parseDTCResponse(trimmed)
                    } else if (trimmed == ">") {
                        addReceivedLog(trimmed)
                    }
                    responseBuffer = ""
                }
            }
        }
    }

    private fun loadDTCsFromDatabase() {
        viewModelScope.launch {
            dtcDao.getAllDTCs().collect { dtcList ->
                _state.emit(_state.value.copy(dtcList = dtcList))
            }
        }
    }

    fun updateCustomCommand(command: String) {
        _state.value = _state.value.copy(customCommand = command)
    }

    fun sendCustomCommand() {
        val command = _state.value.customCommand.trim()
        if (command.isNotEmpty()) {
            sendCommand(command)
            updateCustomCommand("")
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            if (_state.value.connectionState != BluetoothManager.ConnectionState.CONNECTED) {
                addErrorLog("Not connected to ELM327/Bluetooth device. Connect on Home tab first.")
            }
            addSentLog(command)
            _state.emit(_state.value.copy(isSending = true))
            try {
                bluetoothManager.sendCommand(command)
            } catch (e: Exception) {
                addErrorLog("Failed to send command '$command': ${e.message}")
            } finally {
                _state.emit(_state.value.copy(isSending = false))
            }
        }
    }

    fun clearTerminal() {
        _state.value = _state.value.copy(terminalLog = emptyList())
        addSystemLog("Terminal log cleared.")
    }

    fun clearAllDTCs() {
        viewModelScope.launch {
            sendCommand("14 FF 00")
            dtcDao.clearAll()
            addSystemLog("Cancellazione codici guasto (DTC) eseguita.")
        }
    }

    fun deleteDTC(dtcRecord: DTCRecord) {
        viewModelScope.launch {
            dtcDao.delete(dtcRecord)
        }
    }

    private fun addSentLog(text: String) {
        val entry = TerminalEntry(text = "> $text", type = TerminalEntryType.SENT)
        appendLog(entry)
    }

    private fun addReceivedLog(text: String) {
        val entry = TerminalEntry(text = text, type = TerminalEntryType.RECEIVED)
        appendLog(entry)
    }

    private fun addSystemLog(text: String) {
        val entry = TerminalEntry(text = "[SYS] $text", type = TerminalEntryType.SYSTEM)
        appendLog(entry)
    }

    private fun addErrorLog(text: String) {
        val entry = TerminalEntry(text = "[ERR] $text", type = TerminalEntryType.ERROR)
        appendLog(entry)
    }

    private fun appendLog(entry: TerminalEntry) {
        val updatedList = _state.value.terminalLog + entry
        val trimmedList = if (updatedList.size > 200) updatedList.takeLast(200) else updatedList
        _state.value = _state.value.copy(terminalLog = trimmedList)
    }

    private fun parseDTCResponse(response: String) {
        viewModelScope.launch {
            try {
                val dtcCodes = OBD2Parser.parseDTC(response)
                if (response.contains("CLEARED") || response.contains("44 FF")) {
                    dtcDao.clearAll()
                    addSystemLog("Database DTC azzerato.")
                } else if (dtcCodes.isNotEmpty()) {
                    dtcCodes.forEach { rawCode ->
                        val normalizedCode = normalizeDTC(rawCode)
                        val dtcRecord = DTCRecord(
                            code = normalizedCode,
                            description = decodeDTC(normalizedCode),
                            status = "Attivo"
                        )
                        dtcDao.insert(dtcRecord)
                    }
                    addSystemLog("Rilevati e decodificati ${dtcCodes.size} codice/i di errore Lotus ABS.")
                }
            } catch (e: Exception) {
                // Ignore parse errors for non-DTC raw responses
            }
        }
    }

    private fun normalizeDTC(code: String): String {
        val clean = code.uppercase().trim()
        // Convert P-prefix to C-prefix for ABS specific chassis DTCs
        return when {
            clean == "P0035" -> "C0035"
            clean == "P0040" -> "C0040"
            clean == "P0045" -> "C0045"
            clean == "P0050" -> "C0050"
            clean == "P0060" -> "C0060"
            clean == "P0065" -> "C0065"
            clean == "P0070" -> "C0070"
            clean == "P0075" -> "C0075"
            clean == "P0080" -> "C0080"
            clean == "P0085" -> "C0085"
            clean == "P0090" -> "C0090"
            clean == "P0095" -> "C0095"
            clean == "P0110" -> "C0110"
            clean == "P0121" -> "C0121"
            clean == "P0161" -> "C0161"
            clean == "P0232" -> "C0232"
            clean == "P0245" -> "C0245"
            clean == "P0550" -> "C0550"
            clean == "P0800" -> "C0800"
            else -> clean
        }
    }

    fun decodeDTC(code: String): String {
        val cleanCode = code.uppercase().trim().replace(" ", "").replace("-", "")
        return when {
            cleanCode.contains("C0035") || cleanCode.contains("0035") ->
                "Sensore Velocità Ruota Anteriore Sinistra (LF): cortocircuito, circuito aperto o assenza segnale"
            cleanCode.contains("C0040") || cleanCode.contains("0040") ->
                "Sensore Velocità Ruota Anteriore Destra (RF): cortocircuito, circuito aperto o assenza segnale"
            cleanCode.contains("C0045") || cleanCode.contains("0045") ->
                "Sensore Velocità Ruota Posteriore Sinistra (LR): cortocircuito, circuito aperto o assenza segnale"
            cleanCode.contains("C0050") || cleanCode.contains("0050") ->
                "Sensore Velocità Ruota Posteriore Destra (RR): cortocircuito, circuito aperto o assenza segnale"
            cleanCode.contains("C0060") || cleanCode.contains("0060") ->
                "Elettrovalvola Scarico (DUMP) Anteriore Sinistra (LF): guasto circuito elettrico"
            cleanCode.contains("C0065") || cleanCode.contains("0065") ->
                "Elettrovalvola Ingresso (ISO) Anteriore Sinistra (LF): guasto circuito elettrico"
            cleanCode.contains("C0070") || cleanCode.contains("0070") ->
                "Elettrovalvola Scarico (DUMP) Anteriore Destra (RF): guasto circuito elettrico"
            cleanCode.contains("C0075") || cleanCode.contains("0075") ->
                "Elettrovalvola Ingresso (ISO) Anteriore Destra (RF): guasto circuito elettrico"
            cleanCode.contains("C0080") || cleanCode.contains("0080") ->
                "Elettrovalvola Scarico (DUMP) Posteriore Sinistra (LR): guasto circuito elettrico"
            cleanCode.contains("C0085") || cleanCode.contains("0085") ->
                "Elettrovalvola Ingresso (ISO) Posteriore Sinistra (LR): guasto circuito elettrico"
            cleanCode.contains("C0090") || cleanCode.contains("0090") ->
                "Elettrovalvola Scarico (DUMP) Posteriore Destra (RR): guasto circuito elettrico"
            cleanCode.contains("C0095") || cleanCode.contains("0095") ->
                "Elettrovalvola Ingresso (ISO) Posteriore Destra (RR): guasto circuito elettrico"
            cleanCode.contains("C0110") || cleanCode.contains("0110") ->
                "Pompa di Ritorno ABS: pompa bloccata, circuito aperto o in cortocircuito"
            cleanCode.contains("C0121") || cleanCode.contains("0121") ->
                "Relè Principale Elettrovalvole ABS: guasto circuito di comando"
            cleanCode.contains("C0161") || cleanCode.contains("0161") ->
                "Interruttore Luci Freno (Pedale Stop): guasto circuito o segnale incongruente"
            cleanCode.contains("C0232") || cleanCode.contains("0232") ->
                "Spia Impianto Frenante / Spia ABS: tensione anomala o circuito aperto"
            cleanCode.contains("C0245") || cleanCode.contains("0245") ->
                "Segnale Velocità Ruota Irregolare: errore di frequenza o dente mancante"
            cleanCode.contains("C0550") || cleanCode.contains("0550") ->
                "Centralina Elettronica ECU ABS: errore interno memoria o guasto hardware"
            cleanCode.contains("C0800") || cleanCode.contains("0800") ->
                "Tensione Alimentazione Centralina ABS: tensione alta o bassa (fuori tolleranza)"
            cleanCode.contains("P0100") -> "Debimetro / Misuratore Flusso Aria (MAF): anomalia circuito"
            cleanCode.contains("P0110") -> "Sensore Temperatura Aria Aspirata (IAT): anomalia circuito"
            cleanCode.contains("P0120") -> "Sensore Posizione Farfalla (TPS): anomalia circuito"
            cleanCode.contains("P0300") -> "Mancate Accensioni Cilindri Multiple / Casuali"
            cleanCode.contains("U0121") -> "Comunicazione Persa con la Centralina ABS / Controllo Freni"
            else -> "Codice Errore Diagnostico Lotus ABS ($code)"
        }
    }
}
