package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

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

enum class BleedExecState {
    IDLE,
    RUNNING,
    STOPPING,
    STOPPED_CONFIRMED,
    STOP_UNCONFIRMED,
    ERROR
}

data class BleedState(
    val selectedWheel: WheelLocation? = null,
    val terminalLog: List<TerminalEntry> = emptyList(),
    val activeOperation: String? = null,
    val timeRemainingSec: Int = 0,
    val isRunningSequence: Boolean = false,
    val executionState: BleedExecState = BleedExecState.IDLE
)

class BleedViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {
    private val _state = MutableStateFlow(BleedState())
    val state: StateFlow<BleedState> = _state

    private var activeJob: Job? = null
    private val stopMutex = Mutex()

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

    /**
     * Single Coordinated Emergency Stop Execution.
     * Guaranteed to run only once per stop request via Mutex.
     */
    suspend fun executeCoordinatedStop(wheel: WheelLocation?) {
        if (stopMutex.isLocked) return
        stopMutex.withLock {
            _state.update {
                it.copy(
                    activeOperation = "ARRESTO IN CORSO...",
                    executionState = BleedExecState.STOPPING
                )
            }

            withContext(NonCancellable) {
                addSystemLog("🛑 EXECUTING COORDINATED EMERGENCY SHUTDOWN...")

                var allConfirmed = true

                // 1. Send Pump OFF
                addSystemLog("Shutdown Step 1/4: Shutting down ABS Pump (30 02 07 00)...")
                addSentLog("30 02 07 00")
                val pumpOk = bluetoothManager.sendCommandConfirmed("30 02 07 00")
                if (!pumpOk) allConfirmed = false
                delay(200)

                // 2. Send DUMP and ISO OFF
                if (wheel != null) {
                    addSystemLog("Shutdown Step 2/4: Closing DUMP valve (${wheel.dumpOffCommand})...")
                    addSentLog(wheel.dumpOffCommand)
                    val dumpOk = bluetoothManager.sendCommandConfirmed(wheel.dumpOffCommand)
                    if (!dumpOk) allConfirmed = false
                    delay(200)

                    addSystemLog("Shutdown Step 3/4: Closing ISO valve (${wheel.isoOffCommand})...")
                    addSentLog(wheel.isoOffCommand)
                    val isoOk = bluetoothManager.sendCommandConfirmed(wheel.isoOffCommand)
                    if (!isoOk) allConfirmed = false
                    delay(200)
                } else {
                    val closeCmds = listOf("30 11 07 00", "30 21 07 00", "30 31 07 00", "30 41 07 00")
                    for (cmd in closeCmds) {
                        addSentLog(cmd)
                        val ok = bluetoothManager.sendCommandConfirmed(cmd)
                        if (!ok) allConfirmed = false
                        delay(100)
                    }
                }

                // 3. Confirm Pump OFF
                addSystemLog("Shutdown Step 4/4: Confirming Pump OFF (30 02 07 00)...")
                addSentLog("30 02 07 00")
                val doubleCheckOk = bluetoothManager.sendCommandConfirmed("30 02 07 00")
                if (!doubleCheckOk) allConfirmed = false

                val isConnected = bluetoothManager.connectionState.value == BluetoothManager.ConnectionState.CONNECTED

                if (isConnected && allConfirmed) {
                    _state.update {
                        it.copy(
                            activeOperation = null,
                            timeRemainingSec = 0,
                            isRunningSequence = false,
                            executionState = BleedExecState.STOPPED_CONFIRMED
                        )
                    }
                    addSystemLog("✅ EMERGENCY STOP COMPLETE: Actuator shutdown CONFIRMED by ECU.")
                } else {
                    _state.update {
                        it.copy(
                            activeOperation = null,
                            timeRemainingSec = 0,
                            isRunningSequence = false,
                            executionState = if (isConnected) BleedExecState.STOP_UNCONFIRMED else BleedExecState.ERROR
                        )
                    }
                    if (!isConnected) {
                        addSystemLog("⚠️ EMERGENCY STOP INCOMPLETE: Transport DISCONNECTED during shutdown procedure.")
                    } else {
                        addSystemLog("⚠️ EMERGENCY STOP UNCONFIRMED: ECU did not return positive acknowledgment for all OFF commands.")
                    }
                }
            }
        }
    }

    fun emergencyStop() {
        val currentWheel = _state.value.selectedWheel
        _state.update {
            it.copy(
                activeOperation = "ARRESTO IN CORSO...",
                executionState = BleedExecState.STOPPING
            )
        }

        activeJob?.cancel()
        activeJob = null

        viewModelScope.launch {
            executeCoordinatedStop(currentWheel)
        }
    }

    fun runWheelBleedMacro(wheel: WheelLocation) {
        if (_state.value.isRunningSequence || _state.value.executionState == BleedExecState.STOPPING) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Macro Spurgo (${wheel.shortName})",
                    timeRemainingSec = 13,
                    isRunningSequence = true,
                    executionState = BleedExecState.RUNNING
                )
            }

            var completedNormally = false
            try {
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
                        isRunningSequence = false,
                        executionState = BleedExecState.IDLE
                    )
                }
                completedNormally = true
            } catch (e: CancellationException) {
                addSystemLog("Bleed macro cancelled.")
                throw e
            } finally {
                if (!completedNormally) {
                    executeCoordinatedStop(wheel)
                }
            }
        }
    }

    fun runWheelBleedMacroSlow(wheel: WheelLocation) {
        if (_state.value.isRunningSequence || _state.value.executionState == BleedExecState.STOPPING) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Bleed Slow (${wheel.shortName})",
                    timeRemainingSec = 22,
                    isRunningSequence = true,
                    executionState = BleedExecState.RUNNING
                )
            }

            var completedNormally = false
            try {
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
                        isRunningSequence = false,
                        executionState = BleedExecState.IDLE
                    )
                }
                completedNormally = true
            } catch (e: CancellationException) {
                addSystemLog("Slow bleed macro cancelled.")
                throw e
            } finally {
                if (!completedNormally) {
                    executeCoordinatedStop(wheel)
                }
            }
        }
    }

    fun runWheelBleedPulseTestMacro(wheel: WheelLocation) {
        if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB) {
            addSystemLog("USB is read-only: actuator commands are disabled.")
            return
        }
        if (_state.value.isRunningSequence || _state.value.executionState == BleedExecState.STOPPING) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Bleeding Test (${wheel.shortName})",
                    timeRemainingSec = 9,
                    isRunningSequence = true,
                    executionState = BleedExecState.RUNNING
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
                        isRunningSequence = false,
                        executionState = BleedExecState.IDLE
                    )
                }
                completedNormally = true
            } catch (e: CancellationException) {
                addSystemLog("Pulse test macro cancelled.")
                throw e
            } finally {
                if (!completedNormally) {
                    executeCoordinatedStop(wheel)
                }
            }
        }
    }

    fun runWheelBleedPulseTestMacroSlow(wheel: WheelLocation) {
        if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB) {
            addSystemLog("USB is read-only: actuator commands are disabled.")
            return
        }
        if (_state.value.isRunningSequence || _state.value.executionState == BleedExecState.STOPPING) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeOperation = "Bleeding Test Slow (${wheel.shortName})",
                    timeRemainingSec = 18,
                    isRunningSequence = true,
                    executionState = BleedExecState.RUNNING
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
                        isRunningSequence = false,
                        executionState = BleedExecState.IDLE
                    )
                }
                completedNormally = true
            } catch (e: CancellationException) {
                addSystemLog("Slow pulse test macro cancelled.")
                throw e
            } finally {
                if (!completedNormally) {
                    executeCoordinatedStop(wheel)
                }
            }
        }
    }

    fun runBleedSequence(name: String, onCommand: String, offCommand: String, durationSec: Int) {
        if (bluetoothManager.activeTransport == BluetoothManager.Transport.USB) {
            addSystemLog("USB is read-only: actuator commands are disabled.")
            return
        }
        if (_state.value.isRunningSequence || _state.value.executionState == BleedExecState.STOPPING) return

        activeJob = viewModelScope.launch {
            var completedNormally = false
            try {
                _state.update {
                    it.copy(
                        activeOperation = name,
                        timeRemainingSec = durationSec,
                        isRunningSequence = true,
                        executionState = BleedExecState.RUNNING
                    )
                }
                addSystemLog("Starting $name sequence...")

                addSentLog(onCommand)
                bluetoothManager.sendCommand(onCommand)

                for (i in durationSec downTo 1) {
                    _state.update { it.copy(timeRemainingSec = i) }
                    delay(1000)
                }
                completedNormally = true
            } catch (e: CancellationException) {
                addSystemLog("$name sequence cancelled.")
                throw e
            } finally {
                if (!completedNormally) {
                    executeCoordinatedStop(_state.value.selectedWheel)
                } else {
                    withContext(NonCancellable) {
                        addSystemLog("Stopping $name components...")
                        addSentLog(offCommand)
                        bluetoothManager.sendCommand(offCommand)
                        delay(200)
                        addSentLog("30 02 07 00")
                        bluetoothManager.sendCommand("30 02 07 00")
                        delay(200)
                        addSentLog("30 03 07 00")
                        bluetoothManager.sendCommand("30 03 07 00")
                        addSystemLog("Finished $name sequence.")
                        _state.update {
                            it.copy(
                                activeOperation = null,
                                timeRemainingSec = 0,
                                isRunningSequence = false,
                                executionState = BleedExecState.IDLE
                            )
                        }
                    }
                }
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
