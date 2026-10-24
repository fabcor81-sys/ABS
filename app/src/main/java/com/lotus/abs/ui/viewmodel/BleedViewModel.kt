package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WheelLocation(
    val id: String,
    val title: String,
    val shortName: String,
    val dumpOnCommand: String,
    val dumpOffCommand: String,
    val isoOnCommand: String,
    val isoOffCommand: String,
) {
    FRONT_LEFT(
        id = "front_left",
        title = "Front Left Wheel",
        shortName = "LF",
        dumpOnCommand = "30 11 07 01",
        dumpOffCommand = "30 11 07 00",
        isoOnCommand = "30 12 07 01",
        isoOffCommand = "30 12 07 00"
    ),
    FRONT_RIGHT(
        id = "front_right",
        title = "Front Right Wheel",
        shortName = "RF",
        dumpOnCommand = "30 21 07 01",
        dumpOffCommand = "30 21 07 00",
        isoOnCommand = "30 22 07 01",
        isoOffCommand = "30 22 07 00"
    ),
    REAR_LEFT(
        id = "rear_left",
        title = "Rear Left Wheel",
        shortName = "LR",
        dumpOnCommand = "30 31 07 01",
        dumpOffCommand = "30 31 07 00",
        isoOnCommand = "30 32 07 01",
        isoOffCommand = "30 32 07 00"
    ),
    REAR_RIGHT(
        id = "rear_right",
        title = "Rear Right Wheel",
        shortName = "RR",
        dumpOnCommand = "30 41 07 01",
        dumpOffCommand = "30 41 07 00",
        isoOnCommand = "30 42 07 01",
        isoOffCommand = "30 42 07 00"
    );

    companion object {
        fun fromId(id: String?): WheelLocation? {
            return entries.find { (it.id == id) || it.shortName.equals(id, ignoreCase = true) }
        }
    }
}

data class BleedState(
    val selectedWheel: WheelLocation? = null,
    val terminalLog: List<TerminalEntry> = emptyList(),
    val activeOperation: String? = null,
    val timeRemainingSec: Int = 0,
    val isRunningSequence: Boolean = false
)

class BleedViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {
    private val _state = MutableStateFlow(BleedState())
    val state: StateFlow<BleedState> = _state

    private var activeJob: Job? = null

    init {
        observeBluetoothData()
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
                    }
                    responseBuffer = responseBuffer.substring(index + 1)
                }

                if (responseBuffer.trim() == ">" || responseBuffer.endsWith(">")) {
                    val trimmed = responseBuffer.trim()
                    if (trimmed.isNotEmpty()) {
                        addReceivedLog(trimmed)
                    }
                    responseBuffer = ""
                }
            }
        }
    }

    fun selectWheel(wheel: WheelLocation?) {
        _state.update { it.copy(selectedWheel = wheel) }
    }

    /**
     * Reliable Fast ELM327 Initialization (~2.5s total)
     */
    private suspend fun executeSafeFastElm327Init() {
        addSystemLog("Init 1/8: Resetting ELM327 (ATZ)")
        addSentLog("ATZ")
        bluetoothManager.sendCommand("ATZ")
        delay(1000)

        addSystemLog("Init 2/8: Echo OFF (AT E0)")
        addSentLog("AT E0")
        bluetoothManager.sendCommand("AT E0")
        delay(200)

        addSystemLog("Init 3/8: Linefeeds OFF (AT L0)")
        addSentLog("AT L0")
        bluetoothManager.sendCommand("AT L0")
        delay(200)

        addSystemLog("Init 4/8: ECU Timeout 200ms (AT ST 32)")
        addSentLog("AT ST 32")
        bluetoothManager.sendCommand("AT ST 32")
        delay(200)

        addSystemLog("Init 5/8: Setting Header (AT SH 812BF1)")
        addSentLog("AT SH 812BF1")
        bluetoothManager.sendCommand("AT SH 812BF1")
        delay(200)

        addSystemLog("Init 6/8: Setting Wakeup (AT WM 812BF13E)")
        addSentLog("AT WM 812BF13E")
        bluetoothManager.sendCommand("AT WM 812BF13E")
        delay(200)

        addSystemLog("Init 7/8: Setting Protocol KWP (AT SP 5)")
        addSentLog("AT SP 5")
        bluetoothManager.sendCommand("AT SP 5")
        delay(200)

        addSystemLog("Init 8/8: DTC retained (automatic clearing disabled)")
        delay(500)
    }

    /**
     * Original Relaxed/Slow ELM327 Initialization (~11s total with 3s delays)
     */
    private suspend fun executeSlowElm327Init() {
        addSystemLog("Slow Init 1/5: Resetting ELM327 (ATZ)")
        addSentLog("ATZ")
        bluetoothManager.sendCommand("ATZ")
        delay(3000)

        addSystemLog("Slow Init 2/5: Setting Header (AT SH 812BF1)")
        addSentLog("AT SH 812BF1")
        bluetoothManager.sendCommand("AT SH 812BF1")
        delay(3000)

        addSystemLog("Slow Init 3/5: Setting Wakeup (AT WM 812BF13E)")
        addSentLog("AT WM 812BF13E")
        bluetoothManager.sendCommand("AT WM 812BF13E")
        delay(3000)

        addSystemLog("Slow Init 4/5: Setting Protocol KWP (AT SP 5)")
        addSentLog("AT SP 5")
        bluetoothManager.sendCommand("AT SP 5")
        delay(100)

        addSystemLog("Slow Init 5/5: DTC retained (automatic clearing disabled)")
        delay(2000)
    }

    fun runWheelBleedMacro(wheel: WheelLocation) {
        if (_state.value.isRunningSequence) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Macro Spurgo (${wheel.shortName})",
                    timeRemainingSec = 13,
                    isRunningSequence = true
                )
            }

            addSystemLog("--- Starting Fast Bleed Sequence for ${wheel.title} (${wheel.shortName}) ---")

            executeSafeFastElm327Init()

            // Open DUMP Valve (5s)
            addSystemLog("Opening DUMP valve (${wheel.dumpOnCommand})")
            addSentLog(wheel.dumpOnCommand)
            bluetoothManager.sendCommand(wheel.dumpOnCommand)

            for (i in 5 downTo 1) {
                _state.update { it.copy(timeRemainingSec = 5 + i) }
                delay(1000)
            }

            // Close DUMP Valve
            addSystemLog("Closing DUMP valve (${wheel.dumpOffCommand})")
            addSentLog(wheel.dumpOffCommand)
            bluetoothManager.sendCommand(wheel.dumpOffCommand)
            delay(300)

            // Start ABS Pump (5s)
            addSystemLog("Starting ABS Pump (30 02 07 01)")
            addSentLog("30 02 07 01")
            bluetoothManager.sendCommand("30 02 07 01")

            for (i in 5 downTo 1) {
                _state.update { it.copy(timeRemainingSec = i) }
                delay(1000)
            }

            // Stop ABS Pump safely with 300ms confirmation gap
            addSystemLog("Stopping ABS Pump (30 02 07 00)")
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")
            delay(300)
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")

            addSystemLog("--- Completed Full Bleed Macro for ${wheel.shortName} ---")
            _state.update {
                it.copy(
                    activeOperation = null,
                    timeRemainingSec = 0,
                    isRunningSequence = false
                )
            }
        }
    }

    fun runWheelBleedMacroSlow(wheel: WheelLocation) {
        if (_state.value.isRunningSequence) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Bleed Slow (${wheel.shortName})",
                    timeRemainingSec = 22,
                    isRunningSequence = true
                )
            }

            addSystemLog("--- Starting Slow Bleed Sequence for ${wheel.title} (${wheel.shortName}) ---")

            executeSlowElm327Init()

            // Open DUMP Valve (5s)
            addSystemLog("Opening DUMP valve (${wheel.dumpOnCommand})")
            addSentLog(wheel.dumpOnCommand)
            bluetoothManager.sendCommand(wheel.dumpOnCommand)

            for (i in 5 downTo 1) {
                _state.update { it.copy(timeRemainingSec = 5 + i) }
                delay(1000)
            }

            // Close DUMP Valve
            addSystemLog("Closing DUMP valve (${wheel.dumpOffCommand})")
            addSentLog(wheel.dumpOffCommand)
            bluetoothManager.sendCommand(wheel.dumpOffCommand)
            delay(500)

            // Start ABS Pump (5s)
            addSystemLog("Starting ABS Pump (30 02 07 01)")
            addSentLog("30 02 07 01")
            bluetoothManager.sendCommand("30 02 07 01")

            for (i in 5 downTo 1) {
                _state.update { it.copy(timeRemainingSec = i) }
                delay(1000)
            }

            // Stop ABS Pump safely with 300ms confirmation gap
            addSystemLog("Stopping ABS Pump (30 02 07 00)")
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")
            delay(300)
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")

            addSystemLog("--- Completed Full Bleed Macro (Slow) for ${wheel.shortName} ---")
            _state.update {
                it.copy(
                    activeOperation = null,
                    timeRemainingSec = 0,
                    isRunningSequence = false
                )
            }
        }
    }

    fun runWheelBleedPulseTestMacro(wheel: WheelLocation) {
        if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB) {
            addSystemLog("USB is read-only: actuator commands are disabled.")
            return
        }
        if (_state.value.isRunningSequence) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Bleeding Test (${wheel.shortName})",
                    timeRemainingSec = 9,
                    isRunningSequence = true
                )
            }

            var completedNormally = false
            try {
            addSystemLog("--- Starting Fast Hydraulic Pulse Test Macro for ${wheel.title} (${wheel.shortName}) ---")

            executeSafeFastElm327Init()

            // 1. Send DUMP ON
            addSystemLog("Step 1/5: Opening DUMP valve (${wheel.dumpOnCommand})")
            addSentLog(wheel.dumpOnCommand)
            bluetoothManager.sendCommand(wheel.dumpOnCommand)

            // 2. Wait 1000 ms
            addSystemLog("Step 2/5: Accumulating fluid (1000ms)...")
            delay(1000)

            // 3. Send ABS Pump ON
            addSystemLog("Step 3/5: Starting ABS Pump ON (30 02 07 01)")
            addSentLog("30 02 07 01")
            bluetoothManager.sendCommand("30 02 07 01")

            // 4. Wait 1500 ms
            addSystemLog("Step 4/5: Forced recirculation (1500ms)...")
            delay(1500)

            // 5. 6 Pulsation cycles on ISO valve (300ms ON / 300ms OFF)
            addSystemLog("Step 5/5: Executing 6 hydraulic pulsation cycles on ISO valve (300ms ON / 300ms OFF)...")
            for (cycle in 1..6) {
                addSystemLog("Pulse $cycle/6: ISO ON (${wheel.isoOnCommand})")
                addSentLog(wheel.isoOnCommand)
                bluetoothManager.sendCommand(wheel.isoOnCommand)
                delay(300)

                addSystemLog("Pulse $cycle/6: ISO OFF (${wheel.isoOffCommand})")
                addSentLog(wheel.isoOffCommand)
                bluetoothManager.sendCommand(wheel.isoOffCommand)
                delay(300)
            }

            // Turn off all actuators safely with 300ms gap between shutdown commands
            addSystemLog("Turning off all actuators safely (Pump OFF -> DUMP OFF -> ISO OFF)...")

            addSystemLog("Shutting down ABS Pump (30 02 07 00)...")
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")
            delay(300)

            addSystemLog("Closing DUMP valve (${wheel.dumpOffCommand})...")
            addSentLog(wheel.dumpOffCommand)
            bluetoothManager.sendCommand(wheel.dumpOffCommand)
            delay(300)

            addSystemLog("Closing ISO valve (${wheel.isoOffCommand})...")
            addSentLog(wheel.isoOffCommand)
            bluetoothManager.sendCommand(wheel.isoOffCommand)
            delay(300)

            // Safety confirmation
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")

            addSystemLog("--- Completed Hydraulic Pulse Test Macro for ${wheel.shortName} ---")
            _state.update {
                it.copy(
                    activeOperation = null,
                    timeRemainingSec = 0,
                    isRunningSequence = false
                )
            }
                completedNormally = true
            } finally {
                if (!completedNormally) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        addSystemLog("Sequence interrupted: attempting actuator OFF commands; verify ECU responses before use.")
                        bluetoothManager.sendCommand("30 02 07 00")
                        bluetoothManager.sendCommand(wheel.dumpOffCommand)
                        bluetoothManager.sendCommand(wheel.isoOffCommand)
                        _state.update { it.copy(activeOperation = null, timeRemainingSec = 0, isRunningSequence = false) }
                    }
                }
            }
        }
    }

    fun runWheelBleedPulseTestMacroSlow(wheel: WheelLocation) {
        if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB) {
            addSystemLog("USB is read-only: actuator commands are disabled.")
            return
        }
        if (_state.value.isRunningSequence) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Bleeding Test Slow (${wheel.shortName})",
                    timeRemainingSec = 18,
                    isRunningSequence = true
                )
            }

            var completedNormally = false
            try {
            addSystemLog("--- Starting Slow Hydraulic Pulse Test Macro for ${wheel.title} (${wheel.shortName}) ---")

            executeSlowElm327Init()

            // 1. Send DUMP ON
            addSystemLog("Step 1/5: Opening DUMP valve (${wheel.dumpOnCommand})")
            addSentLog(wheel.dumpOnCommand)
            bluetoothManager.sendCommand(wheel.dumpOnCommand)

            // 2. Wait 1000 ms
            addSystemLog("Step 2/5: Accumulating fluid (1000ms)...")
            delay(1000)

            // 3. Send ABS Pump ON
            addSystemLog("Step 3/5: Starting ABS Pump ON (30 02 07 01)")
            addSentLog("30 02 07 01")
            bluetoothManager.sendCommand("30 02 07 01")

            // 4. Wait 1500 ms
            addSystemLog("Step 4/5: Forced recirculation (1500ms)...")
            delay(1500)

            // 5. 6 Pulsation cycles on ISO valve (300ms ON / 300ms OFF)
            addSystemLog("Step 5/5: Executing 6 hydraulic pulsation cycles on ISO valve (300ms ON / 300ms OFF)...")
            for (cycle in 1..6) {
                addSystemLog("Pulse $cycle/6: ISO ON (${wheel.isoOnCommand})")
                addSentLog(wheel.isoOnCommand)
                bluetoothManager.sendCommand(wheel.isoOnCommand)
                delay(300)

                addSystemLog("Pulse $cycle/6: ISO OFF (${wheel.isoOffCommand})")
                addSentLog(wheel.isoOffCommand)
                bluetoothManager.sendCommand(wheel.isoOffCommand)
                delay(300)
            }

            // Turn off all actuators safely with 300ms gap between shutdown commands
            addSystemLog("Turning off all actuators safely (Pump OFF -> DUMP OFF -> ISO OFF)...")

            addSystemLog("Shutting down ABS Pump (30 02 07 00)...")
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")
            delay(300)

            addSystemLog("Closing DUMP valve (${wheel.dumpOffCommand})...")
            addSentLog(wheel.dumpOffCommand)
            bluetoothManager.sendCommand(wheel.dumpOffCommand)
            delay(300)

            addSystemLog("Closing ISO valve (${wheel.isoOffCommand})...")
            addSentLog(wheel.isoOffCommand)
            bluetoothManager.sendCommand(wheel.isoOffCommand)
            delay(300)

            // Safety confirmation
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")

            addSystemLog("--- Completed Hydraulic Pulse Test Macro (Slow) for ${wheel.shortName} ---")
            _state.update {
                it.copy(
                    activeOperation = null,
                    timeRemainingSec = 0,
                    isRunningSequence = false
                )
            }
                completedNormally = true
            } finally {
                if (!completedNormally) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        addSystemLog("Sequence interrupted: attempting actuator OFF commands; verify ECU responses before use.")
                        bluetoothManager.sendCommand("30 02 07 00")
                        bluetoothManager.sendCommand(wheel.dumpOffCommand)
                        bluetoothManager.sendCommand(wheel.isoOffCommand)
                        _state.update { it.copy(activeOperation = null, timeRemainingSec = 0, isRunningSequence = false) }
                    }
                }
            }
        }
    }

    fun runBleedSequence(name: String, onCommand: String, offCommand: String, durationSec: Int) {
        if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB) {
            addSystemLog("USB is read-only: actuator commands are disabled.")
            return
        }
        if (_state.value.isRunningSequence) return

        activeJob = viewModelScope.launch {
            try {
                _state.update { it.copy(activeOperation = name, timeRemainingSec = durationSec, isRunningSequence = true) }
                addSystemLog("Starting $name sequence...")

                addSentLog(onCommand)
                bluetoothManager.sendCommand(onCommand)

                for (i in durationSec downTo 1) {
                    _state.update { it.copy(timeRemainingSec = i) }
                    delay(1000)
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    addSystemLog("Stopping all components...")
                    
                    // Spegniamo la valvola/comando specifico usato
                    addSentLog(offCommand)
                    bluetoothManager.sendCommand(offCommand)
                    
                    // Piccolo buffer per l'ELM327
                    delay(200)
                    
                    // Spegnimento forzato Pompa
                    addSentLog("30 02 07 00")
                    bluetoothManager.sendCommand("30 02 07 00")
                    
                    // Piccolo buffer
                    delay(200)
                    
                    // Spegnimento forzato Relè Valvole
                    addSentLog("30 03 07 00")
                    bluetoothManager.sendCommand("30 03 07 00")
                    
                    addSystemLog("Finished $name sequence.")
                    _state.update { it.copy(activeOperation = null, timeRemainingSec = 0, isRunningSequence = false) }
                }
            }
        }
    }

    fun emergencyStop() {
        activeJob?.cancel()
        activeJob = null

        viewModelScope.launch {
            addSystemLog("🛑 EMERGENCY STOP EXECUTED!")

            // Immediately send Pump OFF
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")
            delay(200)

            // Immediately close all DUMP valves for safety
            val currentWheel = _state.value.selectedWheel
            if (currentWheel != null) {
                addSentLog(currentWheel.dumpOffCommand)
                bluetoothManager.sendCommand(currentWheel.dumpOffCommand)
                delay(200)
                addSentLog(currentWheel.isoOffCommand)
                bluetoothManager.sendCommand(currentWheel.isoOffCommand)
                delay(200)
            } else {
                val allCloseCommands = listOf("30 11 07 00", "30 21 07 00", "30 31 07 00", "30 41 07 00")
                for (cmd in allCloseCommands) {
                    addSentLog(cmd)
                    bluetoothManager.sendCommand(cmd)
                    delay(100)
                }
            }

            // Safety double check pump OFF
            addSentLog("30 02 07 00")
            bluetoothManager.sendCommand("30 02 07 00")

            addSystemLog("OFF commands sent; ECU actuator state is NOT independently confirmed.")
            _state.update {
                it.copy(
                    activeOperation = null,
                    timeRemainingSec = 0,
                    isRunningSequence = false
                )
            }
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            addSentLog(command)
            bluetoothManager.sendCommand(command)
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminalLog = emptyList()) }
    }

    private fun addSentLog(text: String) {
        appendLog(TerminalEntry(text = "> $text", type = TerminalEntryType.SENT))
    }

    private fun addReceivedLog(text: String) {
        appendLog(TerminalEntry(text = text, type = TerminalEntryType.RECEIVED))
    }

    private fun addSystemLog(text: String) {
        appendLog(TerminalEntry(text = "[SYS] $text", type = TerminalEntryType.SYSTEM))
    }

    private fun appendLog(entry: TerminalEntry) {
        _state.update {
            val updated = it.terminalLog + entry
            val trimmed = if (updated.size > 200) updated.takeLast(200) else updated
            it.copy(terminalLog = trimmed)
        }
    }
}
