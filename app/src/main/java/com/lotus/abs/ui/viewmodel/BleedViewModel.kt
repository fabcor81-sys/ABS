package com.lotus.abs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import com.lotus.abs.transport.DiagnosticCommandResult
import com.lotus.abs.transport.DiagnosticTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
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
    COMPLETED,
    COMPLETED_UNVERIFIED,
    STOPPING,
    STOPPED_CONFIRMED,
    STOP_UNCONFIRMED,
    ERROR
}

data class BleedState(
    val selectedWheel: WheelLocation? = null,
    val setupWheel: WheelLocation? = null,
    val slowMode: Boolean = false,
    val macroPhase: String = "In attesa",
    val pulseCycle: Int = 0,
    val hasUnconfirmedCommands: Boolean = false,
    val terminalLog: List<TerminalEntry> = emptyList(),
    val activeOperation: String? = null,
    val timeRemainingSec: Int = 0,
    val isRunningSequence: Boolean = false,
    val executionState: BleedExecState = BleedExecState.IDLE
)

class BleedViewModel(
    val bluetoothManager: BluetoothManager,
    private val customTransport: DiagnosticTransport? = null
) : ViewModel() {
    private val _state = MutableStateFlow(BleedState())
    val state: StateFlow<BleedState> = _state

    private var activeJob: Job? = null
    private val isStopInProgress = AtomicBoolean(false)

    // Experimental per-wheel recovery interval. The Lotus service notes describe
    // brief pump overrun, but do not specify a diagnostic bleed duration.
    private val postPulsePumpRecoverySeconds = 5

    val activeTransport: DiagnosticTransport
        get() = customTransport ?: bluetoothManager

    init {
        observeBluetoothData()
        observeConnectionLoss()
    }

    private fun observeConnectionLoss() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connState ->
                if (connState != BluetoothManager.ConnectionState.CONNECTED && _state.value.isRunningSequence) {
                    addSystemLog("⚠️ Connection lost during active sequence!")
                    activeJob?.cancel()
                    activeJob = null
                    _state.update {
                        it.copy(
                            activeOperation = null,
                            timeRemainingSec = 0,
                            isRunningSequence = false,
                            executionState = BleedExecState.ERROR
                        )
                    }
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
        if (_state.value.isRunningSequence || _state.value.executionState == BleedExecState.STOPPING) return
        _state.update { it.copy(selectedWheel = wheel, setupWheel = wheel ?: it.setupWheel) }
    }

    fun selectSetupWheel(wheel: WheelLocation) {
        if (_state.value.isRunningSequence) return
        _state.update { it.copy(setupWheel = wheel) }
    }

    fun setSlowMode(slow: Boolean) {
        if (_state.value.isRunningSequence) return
        _state.update { it.copy(slowMode = slow) }
    }

    // Dismiss an unverified result only after the operator has inspected
    // the hardware and consciously accepted the need for further checking.
    fun acknowledgeOutcome() {
        if (_state.value.isRunningSequence) return
        if (_state.value.executionState !in setOf(
                BleedExecState.ERROR,
                BleedExecState.STOP_UNCONFIRMED,
                BleedExecState.COMPLETED_UNVERIFIED
            )) return
        addSystemLog("Operator acknowledged unverified ABS result; physical safety is NOT verified by the app.")
        _state.update {
            it.copy(executionState = BleedExecState.IDLE, macroPhase = "In attesa di verifica")
        }
    }

    private fun updateMacroPhase(phase: String, cycle: Int = 0) {
        _state.update { it.copy(macroPhase = phase, pulseCycle = cycle) }
    }

    private suspend fun executeSafeFastElm327Init() {
        addSystemLog("Init 1/8: Resetting ELM327 (ATZ)")
        addSentLog("ATZ")
        activeTransport.executeCommand("ATZ", CommandCategory.READ_ONLY)
        delay(1000)

        addSystemLog("Init 2/8: Echo OFF (AT E0)")
        addSentLog("AT E0")
        activeTransport.executeCommand("AT E0", CommandCategory.READ_ONLY)
        delay(200)

        addSystemLog("Init 3/8: Linefeeds OFF (AT L0)")
        addSentLog("AT L0")
        activeTransport.executeCommand("AT L0", CommandCategory.READ_ONLY)
        delay(200)

        addSystemLog("Init 4/8: ECU Timeout 200ms (AT ST 32)")
        addSentLog("AT ST 32")
        activeTransport.executeCommand("AT ST 32", CommandCategory.READ_ONLY)
        delay(200)

        addSystemLog("Init 5/8: Setting Header (AT SH 812BF1)")
        addSentLog("AT SH 812BF1")
        activeTransport.executeCommand("AT SH 812BF1", CommandCategory.READ_ONLY)
        delay(200)

        addSystemLog("Init 6/8: Setting Wakeup (AT WM 812BF13E)")
        addSentLog("AT WM 812BF13E")
        activeTransport.executeCommand("AT WM 812BF13E", CommandCategory.READ_ONLY)
        delay(200)

        addSystemLog("Init 7/8: Setting Protocol KWP (AT SP 5)")
        addSentLog("AT SP 5")
        activeTransport.executeCommand("AT SP 5", CommandCategory.READ_ONLY)
        delay(200)

        addSystemLog("Init 8/8: DTC retained (automatic clearing disabled)")
        delay(500)
    }

    private suspend fun executeSlowElm327Init() {
        addSystemLog("Slow Init 1/5: Resetting ELM327 (ATZ)")
        addSentLog("ATZ")
        activeTransport.executeCommand("ATZ", CommandCategory.READ_ONLY)
        delay(3000)

        addSystemLog("Slow Init 2/5: Setting Header (AT SH 812BF1)")
        addSentLog("AT SH 812BF1")
        activeTransport.executeCommand("AT SH 812BF1", CommandCategory.READ_ONLY)
        delay(3000)

        addSystemLog("Slow Init 3/5: Setting Wakeup (AT WM 812BF13E)")
        addSentLog("AT WM 812BF13E")
        activeTransport.executeCommand("AT WM 812BF13E", CommandCategory.READ_ONLY)
        delay(3000)

        addSystemLog("Slow Init 4/5: Setting Protocol KWP (AT SP 5)")
        addSentLog("AT SP 5")
        activeTransport.executeCommand("AT SP 5", CommandCategory.READ_ONLY)
        delay(100)

        addSystemLog("Slow Init 5/5: DTC retained (automatic clearing disabled)")
        delay(2000)
    }

    private suspend fun executeMacroStep(
        commandHex: String,
        category: CommandCategory,
        wheel: WheelLocation,
        requireAcknowledgement: Boolean = false
    ): Boolean {
        if (checkStopRequested() || !activeTransport.isConnected) {
            return false
        }

        addSentLog(commandHex)
        val t0 = System.nanoTime()
        val result = activeTransport.executeCommand(commandHex, category)
        val t1 = System.nanoTime()
        val durationMs = (t1 - t0) / 1_000_000.0

        addSystemLog(
            String.format(
                Locale.US,
                "[DIAG TIMING] Cmd '%s' -> %s (RTT: %.1fms)",
                commandHex, result.status, durationMs
            )
        )

        if (result.status == CommandStatus.TRANSMITTED) {
            _state.update { it.copy(hasUnconfirmedCommands = true) }
        }
        if (!result.isSuccess && (requireAcknowledgement || result.status != CommandStatus.TRANSMITTED)) {
            addSystemLog("⚠️ Command '$commandHex' not confirmed: ${result.status} (${result.statusText}). Aborting macro!")
            // The caller's finally block handles the coordinated emergency stop.
            return false
        }

        return true
    }

    /**
     * Normal completion for ONE wheel only. During ordinary ABS brake release
     * the ISO and DUMP solenoids return to rest while the pump briefly continues
     * to evacuate the low-pressure accumulator (Lotus Service Notes JJ.12).
     *
     * Five seconds is an experimental setting, NOT a Lotus-prescribed duration
     * for service bleeding. Emergency shutdown never uses this delay.
     */
    private suspend fun completeWheelPulseWithPumpRecovery(wheel: WheelLocation): Boolean {
        updateMacroPhase("Ripristino valvole")
        addSystemLog("End of ISO pulses: return DUMP and ISO to their rest positions; keep pump ON.")

        addSystemLog("DUMP OFF (${wheel.dumpOffCommand}) while pump stays ON...")
        if (!executeMacroStep(wheel.dumpOffCommand, CommandCategory.ACTUATOR_CONTROL, wheel, requireAcknowledgement = true)) return false
        delay(300)
        if (checkStopRequested() || !activeTransport.isConnected) return false

        addSystemLog("ISO OFF (${wheel.isoOffCommand}) while pump stays ON...")
        if (!executeMacroStep(wheel.isoOffCommand, CommandCategory.ACTUATOR_CONTROL, wheel, requireAcknowledgement = true)) return false
        delay(300)
        if (checkStopRequested() || !activeTransport.isConnected) return false

        updateMacroPhase("Recupero accumulatore — pompa attiva")
        addSystemLog("Experimental accumulator recovery: keep pump ON for ${postPulsePumpRecoverySeconds}s.")
        _state.update {
            it.copy(
                activeOperation = "Accumulator recovery (${wheel.shortName}) - pump ON",
                timeRemainingSec = postPulsePumpRecoverySeconds
            )
        }
        for (secondsLeft in postPulsePumpRecoverySeconds downTo 1) {
            if (checkStopRequested() || !activeTransport.isConnected) return false
            _state.update { it.copy(timeRemainingSec = secondsLeft) }
            delay(1000)
        }

        if (checkStopRequested() || !activeTransport.isConnected) return false
        updateMacroPhase("Spegnimento pompa")
        addSystemLog("Accumulator recovery interval complete: pump OFF (30 02 07 00)...")
        if (!executeMacroStep("30 02 07 00", CommandCategory.ACTUATOR_CONTROL, wheel, requireAcknowledgement = true)) return false
        delay(300)
        if (checkStopRequested() || !activeTransport.isConnected) return false

        addSystemLog("Confirming pump OFF (30 02 07 00)...")
        if (!executeMacroStep("30 02 07 00", CommandCategory.ACTUATOR_CONTROL, wheel, requireAcknowledgement = true)) return false
        return true
    }

    /**
     * Single Coordinated Emergency Stop Execution.
     * Guaranteed to run only once per stop request via AtomicBoolean.
     */
    suspend fun executeCoordinatedStop(wheel: WheelLocation?) {
        if (!isStopInProgress.compareAndSet(false, true)) {
            addSystemLog("Coordinated shutdown already in progress, skipping duplicate execution.")
            return
        }

        try {
            _state.update {
                it.copy(
                    activeOperation = "ARRESTO EMERGENZA IN CORSO...",
                    executionState = BleedExecState.STOPPING
                )
            }

            withContext(NonCancellable) {
                addSystemLog("🛑 EXECUTING ATOMIC COORDINATED SHUTDOWN...")

                var allConfirmed = true
                var isTransmittedOnly = false

                // 1. Send Pump OFF
                addSystemLog("Shutdown Step 1/4: Shutting down ABS Pump (30 02 07 00)...")
                addSentLog("30 02 07 00")
                val pumpRes = activeTransport.executeCommand("30 02 07 00", CommandCategory.ACTUATOR_CONTROL)
                if (pumpRes.status == CommandStatus.TRANSMITTED) isTransmittedOnly = true
                if (!pumpRes.isSuccess) allConfirmed = false
                delay(200)

                // 2. Send DUMP and ISO OFF
                if (wheel != null) {
                    addSystemLog("Shutdown Step 2/4: Closing DUMP valve (${wheel.dumpOffCommand})...")
                    addSentLog(wheel.dumpOffCommand)
                    val dumpRes = activeTransport.executeCommand(wheel.dumpOffCommand, CommandCategory.ACTUATOR_CONTROL)
                    if (dumpRes.status == CommandStatus.TRANSMITTED) isTransmittedOnly = true
                    if (!dumpRes.isSuccess) allConfirmed = false
                    delay(200)

                    addSystemLog("Shutdown Step 3/4: Closing ISO valve (${wheel.isoOffCommand})...")
                    addSentLog(wheel.isoOffCommand)
                    val isoRes = activeTransport.executeCommand(wheel.isoOffCommand, CommandCategory.ACTUATOR_CONTROL)
                    if (isoRes.status == CommandStatus.TRANSMITTED) isTransmittedOnly = true
                    if (!isoRes.isSuccess) allConfirmed = false
                    delay(200)
                } else {
                    val closeCmds = listOf("30 11 07 00", "30 21 07 00", "30 31 07 00", "30 41 07 00")
                    for (cmd in closeCmds) {
                        addSentLog(cmd)
                        val res = activeTransport.executeCommand(cmd, CommandCategory.ACTUATOR_CONTROL)
                        if (res.status == CommandStatus.TRANSMITTED) isTransmittedOnly = true
                        if (!res.isSuccess) allConfirmed = false
                        delay(100)
                    }
                }

                // 3. Confirm Pump OFF
                addSystemLog("Shutdown Step 4/4: Confirming Pump OFF (30 02 07 00)...")
                addSentLog("30 02 07 00")
                val doubleCheckRes = activeTransport.executeCommand("30 02 07 00", CommandCategory.ACTUATOR_CONTROL)
                if (doubleCheckRes.status == CommandStatus.TRANSMITTED) isTransmittedOnly = true
                if (!doubleCheckRes.isSuccess) allConfirmed = false

                val isConn = activeTransport.isConnected

                val finalState = when {
                    !isConn -> BleedExecState.ERROR
                    allConfirmed -> BleedExecState.STOPPED_CONFIRMED
                    isTransmittedOnly -> BleedExecState.STOP_UNCONFIRMED
                    else -> BleedExecState.STOP_UNCONFIRMED
                }

                _state.update {
                    it.copy(
                        activeOperation = null,
                        timeRemainingSec = 0,
                        isRunningSequence = false,
                        executionState = finalState
                    )
                }

                when (finalState) {
                    BleedExecState.STOPPED_CONFIRMED -> addSystemLog("✅ EMERGENCY STOP COMPLETE: Actuator shutdown CONFIRMED by ECU.")
                    BleedExecState.STOP_UNCONFIRMED -> addSystemLog("⚠️ EMERGENCY STOP UNCONFIRMED: OFF commands transmitted, but ECU positive ACK not received.")
                    BleedExecState.ERROR -> addSystemLog("⚠️ EMERGENCY STOP ERROR: Transport DISCONNECTED during shutdown procedure.")
                    else -> {}
                }
            }
        } finally {
            isStopInProgress.set(false)
        }
    }

    fun emergencyStop() {
        val currentWheel = _state.value.selectedWheel
        _state.update {
            it.copy(
                activeOperation = "ARRESTO EMERGENZA IN CORSO...",
                executionState = BleedExecState.STOPPING
            )
        }

        activeJob?.cancel()
        activeJob = null

        viewModelScope.launch {
            executeCoordinatedStop(currentWheel)
        }
    }

    private fun checkStopRequested(): Boolean {
        return _state.value.executionState == BleedExecState.STOPPING
    }

    fun runWheelBleedPulseTestMacro(wheel: WheelLocation) {
        if (_state.value.isRunningSequence || checkStopRequested() ||
            _state.value.executionState in setOf(
                BleedExecState.STOP_UNCONFIRMED,
                BleedExecState.ERROR,
                BleedExecState.COMPLETED_UNVERIFIED
            )
        ) return

        activeJob = viewModelScope.launch {
            // Non-invasive capability check (sends 0 bytes to ECU)
            if (!activeTransport.canExecute(CommandCategory.ACTUATOR_CONTROL)) {
                addSystemLog("USB is read-only on real hardware: actuator commands are disabled.")
                return@launch
            }

            _state.update {
                it.copy(
                    activeOperation = "Bleeding Test (${wheel.shortName})",
                    timeRemainingSec = 0,
                    isRunningSequence = true,
                    macroPhase = "Inizializzazione",
                    pulseCycle = 0,
                    hasUnconfirmedCommands = false,
                    executionState = BleedExecState.RUNNING
                )
            }

            var completedNormally = false
            try {
                addSystemLog("--- Starting Fast Hydraulic Pulse Test Macro for ${wheel.title} (${wheel.shortName}) ---")

                executeSafeFastElm327Init()
                if (checkStopRequested()) return@launch

                updateMacroPhase("Preparazione DUMP")
                // 1. Send DUMP ON
                addSystemLog("Step 1/5: Opening DUMP valve (${wheel.dumpOnCommand})")
                if (!executeMacroStep(wheel.dumpOnCommand, CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch

                // 2. Wait 1000 ms
                addSystemLog("Step 2/5: Accumulating fluid (1000ms)...")
                delay(1000)
                if (checkStopRequested()) return@launch

                updateMacroPhase("Avvio pompa ABS")
                // 3. Send ABS Pump ON
                addSystemLog("Step 3/5: Starting ABS Pump ON (30 02 07 01)")
                if (!executeMacroStep("30 02 07 01", CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch

                // 4. Wait 1500 ms
                addSystemLog("Step 4/5: Forced recirculation (1500ms)...")
                delay(1500)
                if (checkStopRequested()) return@launch

                // 5. 6 Pulsation cycles on ISO valve (300ms ON / 300ms OFF)
                addSystemLog("Step 5/5: Executing 6 hydraulic pulsation cycles on ISO valve (300ms ON / 300ms OFF)...")
                for (cycle in 1..6) {
                    updateMacroPhase("Pulsazione ISO", cycle)
                    if (checkStopRequested()) return@launch
                    addSystemLog("Pulse $cycle/6: ISO ON (${wheel.isoOnCommand})")
                    if (!executeMacroStep(wheel.isoOnCommand, CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch
                    delay(300)

                    if (checkStopRequested()) return@launch
                    addSystemLog("Pulse $cycle/6: ISO OFF (${wheel.isoOffCommand})")
                    if (!executeMacroStep(wheel.isoOffCommand, CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch
                    delay(300)
                }

                if (checkStopRequested()) return@launch

                // Normal shutdown: valve solenoids OFF, then 5s pump recovery, then pump OFF.
                // Any failure or emergency stop falls through to coordinated immediate shutdown.
                if (!completeWheelPulseWithPumpRecovery(wheel)) return@launch

                addSystemLog("--- Completed Hydraulic Pulse Test Macro for ${wheel.shortName} ---")
                _state.update {
                    it.copy(
                        activeOperation = null,
                        timeRemainingSec = 0,
                        isRunningSequence = false,
                        executionState = if (it.hasUnconfirmedCommands) BleedExecState.COMPLETED_UNVERIFIED else BleedExecState.COMPLETED,
                        macroPhase = if (it.hasUnconfirmedCommands) "Terminata — conferme incomplete" else "Sequenza terminata"
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
        if (_state.value.isRunningSequence || checkStopRequested() ||
            _state.value.executionState in setOf(
                BleedExecState.STOP_UNCONFIRMED,
                BleedExecState.ERROR,
                BleedExecState.COMPLETED_UNVERIFIED
            )
        ) return

        activeJob = viewModelScope.launch {
            // Non-invasive capability check (sends 0 bytes to ECU)
            if (!activeTransport.canExecute(CommandCategory.ACTUATOR_CONTROL)) {
                addSystemLog("USB is read-only on real hardware: actuator commands are disabled.")
                return@launch
            }

            _state.update {
                it.copy(
                    activeOperation = "Bleeding Test Slow (${wheel.shortName})",
                    timeRemainingSec = 0,
                    isRunningSequence = true,
                    macroPhase = "Inizializzazione",
                    pulseCycle = 0,
                    hasUnconfirmedCommands = false,
                    executionState = BleedExecState.RUNNING
                )
            }

            var completedNormally = false
            try {
                addSystemLog("--- Starting Slow Hydraulic Pulse Test Macro for ${wheel.title} (${wheel.shortName}) ---")

                executeSlowElm327Init()
                if (checkStopRequested()) return@launch

                updateMacroPhase("Preparazione DUMP")
                // 1. Send DUMP ON
                addSystemLog("Step 1/5: Opening DUMP valve (${wheel.dumpOnCommand})")
                if (!executeMacroStep(wheel.dumpOnCommand, CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch

                // 2. Wait 1000 ms
                addSystemLog("Step 2/5: Accumulating fluid (1000ms)...")
                delay(1000)
                if (checkStopRequested()) return@launch

                updateMacroPhase("Avvio pompa ABS")
                // 3. Send ABS Pump ON
                addSystemLog("Step 3/5: Starting ABS Pump ON (30 02 07 01)")
                if (!executeMacroStep("30 02 07 01", CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch

                // 4. Wait 1500 ms
                addSystemLog("Step 4/5: Forced recirculation (1500ms)...")
                delay(1500)
                if (checkStopRequested()) return@launch

                // 5. 6 Pulsation cycles on ISO valve (300ms ON / 300ms OFF)
                addSystemLog("Step 5/5: Executing 6 hydraulic pulsation cycles on ISO valve (300ms ON / 300ms OFF)...")
                for (cycle in 1..6) {
                    updateMacroPhase("Pulsazione ISO", cycle)
                    if (checkStopRequested()) return@launch
                    addSystemLog("Pulse $cycle/6: ISO ON (${wheel.isoOnCommand})")
                    if (!executeMacroStep(wheel.isoOnCommand, CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch
                    delay(300)

                    if (checkStopRequested()) return@launch
                    addSystemLog("Pulse $cycle/6: ISO OFF (${wheel.isoOffCommand})")
                    if (!executeMacroStep(wheel.isoOffCommand, CommandCategory.ACTUATOR_CONTROL, wheel)) return@launch
                    delay(300)
                }

                if (checkStopRequested()) return@launch

                // Normal shutdown: valve solenoids OFF, then 5s pump recovery, then pump OFF.
                // Any failure or emergency stop falls through to coordinated immediate shutdown.
                if (!completeWheelPulseWithPumpRecovery(wheel)) return@launch

                addSystemLog("--- Completed Hydraulic Pulse Test Macro (Slow) for ${wheel.shortName} ---")
                _state.update {
                    it.copy(
                        activeOperation = null,
                        timeRemainingSec = 0,
                        isRunningSequence = false,
                        executionState = if (it.hasUnconfirmedCommands) BleedExecState.COMPLETED_UNVERIFIED else BleedExecState.COMPLETED,
                        macroPhase = if (it.hasUnconfirmedCommands) "Terminata — conferme incomplete" else "Sequenza terminata"
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

    fun runWheelBleedMacro(wheel: WheelLocation) {
        runWheelBleedPulseTestMacro(wheel)
    }

    fun runWheelBleedMacroSlow(wheel: WheelLocation) {
        runWheelBleedPulseTestMacroSlow(wheel)
    }

    /**
     * Timed diagnostic actuator test. Unlike unsupervised manual ON commands,
     * this enforces a finite duration and verifies OFF acknowledgement.
     * Any unconfirmed command triggers the immediate coordinated stop path.
     */
    fun runBleedSequence(name: String, onCommand: String, offCommand: String, durationSec: Int) {
        if (durationSec !in 1..15) {
            addSystemLog("Manual test duration out of range (1..15 seconds).")
            return
        }
        if (!activeTransport.canExecute(CommandCategory.ACTUATOR_CONTROL)) {
            addSystemLog("Actuator control disabled or adapter disconnected.")
            return
        }
        if (_state.value.isRunningSequence || checkStopRequested() ||
            _state.value.executionState in setOf(
                BleedExecState.STOP_UNCONFIRMED,
                BleedExecState.ERROR,
                BleedExecState.COMPLETED_UNVERIFIED
            )
        ) return
        val wheel = _state.value.selectedWheel

        activeJob = viewModelScope.launch {
            var stoppedAndConfirmed = false
            try {
                _state.update {
                    it.copy(
                        activeOperation = name,
                        macroPhase = "Test manuale temporizzato",
                        pulseCycle = 0,
                        timeRemainingSec = durationSec,
                        isRunningSequence = true,
                        executionState = BleedExecState.RUNNING
                    )
                }
                addSystemLog("Starting timed manual test: $name (${durationSec}s).")
                addSentLog(onCommand)
                val onResult = activeTransport.executeCommand(onCommand, CommandCategory.ACTUATOR_CONTROL)
                if (!onResult.isSuccess) {
                    addSystemLog("Manual ON not acknowledged: ${onResult.status}. Triggering stop.")
                    return@launch
                }

                for (secondsLeft in durationSec downTo 1) {
                    if (checkStopRequested() || !activeTransport.isConnected) return@launch
                    _state.update { it.copy(timeRemainingSec = secondsLeft) }
                    delay(1000)
                }

                addSentLog(offCommand)
                val offResult = activeTransport.executeCommand(offCommand, CommandCategory.ACTUATOR_CONTROL)
                if (!offResult.isSuccess) {
                    addSystemLog("Manual OFF not acknowledged: ${offResult.status}. Triggering stop.")
                    return@launch
                }

                addSentLog("30 02 07 00")
                val pumpResult = activeTransport.executeCommand("30 02 07 00", CommandCategory.ACTUATOR_CONTROL)
                if (!pumpResult.isSuccess) {
                    addSystemLog("Pump OFF not acknowledged: ${pumpResult.status}. Triggering stop.")
                    return@launch
                }

                addSentLog("30 03 07 00")
                val relayResult = activeTransport.executeCommand("30 03 07 00", CommandCategory.ACTUATOR_CONTROL)
                if (!relayResult.isSuccess) {
                    addSystemLog("Relay OFF not acknowledged: ${relayResult.status}. Triggering stop.")
                    return@launch
                }

                stoppedAndConfirmed = true
                _state.update {
                    it.copy(
                        activeOperation = null, timeRemainingSec = 0,
                        isRunningSequence = false,
                        executionState = BleedExecState.COMPLETED,
                        macroPhase = "Test manuale terminato"
                    )
                }
                addSystemLog("Manual test OFF commands acknowledged.")
            } catch (e: CancellationException) {
                addSystemLog("Timed manual test interrupted.")
                throw e
            } finally {
                if (!stoppedAndConfirmed) executeCoordinatedStop(wheel)
            }
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            addSentLog(command)
            activeTransport.executeCommand(command, CommandCategory.READ_ONLY)
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
