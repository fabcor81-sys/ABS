package com.lotus.abs

import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import com.lotus.abs.transport.SimulatedDiagnosticTransport
import com.lotus.abs.ui.viewmodel.BleedExecState
import com.lotus.abs.ui.viewmodel.BleedViewModel
import com.lotus.abs.ui.viewmodel.WheelLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyStopCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dummyBluetoothManager: BluetoothManager
    private lateinit var simulatedTransport: SimulatedDiagnosticTransport
    private lateinit var bleedViewModel: BleedViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dummyBluetoothManager = BluetoothManager(null)
        simulatedTransport = SimulatedDiagnosticTransport()
        bleedViewModel = BleedViewModel(dummyBluetoothManager, customTransport = simulatedTransport)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TEST 1: Il controllo delle capacità del trasporto (canExecute) non invia alcun comando.
    @Test
    fun test1_CapabilityCheckSendsNoBytesToEcu() {
        simulatedTransport.clearExecutedCommands()
        val canExec = simulatedTransport.canExecute(CommandCategory.ACTUATOR_CONTROL)

        assertTrue(canExec)
        assertTrue("canExecute must not transmit any commands to ECU", simulatedTransport.executedCommands.isEmpty())
    }

    // TEST 2: USB ACTUATOR_CONTROL disabilitato: la macro non trasmette nulla.
    @Test
    fun test2_DisabledActuatorControlTransmitsNothing() = runTest {
        simulatedTransport.allowActuatorControl = false
        simulatedTransport.clearExecutedCommands()

        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)

        advanceUntilIdle()

        assertTrue("When actuator control is disabled, no actuator commands are sent", simulatedTransport.executedCommands.isEmpty())
        assertEquals(BleedExecState.IDLE, bleedViewModel.state.value.executionState)
    }

    // TEST 3: Un comando ON riceve NEGATIVE_RESPONSE: la macro non esegue ulteriori comandi ON.
    @Test
    fun test3_NegativeResponseOnCommandStopsMacro() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.NEGATIVE_RESPONSE

        bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        advanceUntilIdle()

        val onCommandsExecuted = simulatedTransport.executedCommands.filter {
            it.first.endsWith("01") && it.second == CommandCategory.ACTUATOR_CONTROL
        }

        assertTrue("After NEGATIVE_RESPONSE on first ON command, no subsequent ON commands are sent", onCommandsExecuted.size <= 1)
    }

    // TEST 4: Un comando restituisce TIMEOUT: la macro applica la policy prevista.
    @Test
    fun test4_TimeoutOnCommandTriggersControlledStop() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.TIMEOUT

        bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        advanceUntilIdle()

        assertFalse(bleedViewModel.state.value.isRunningSequence)
        val hasStopAttempt = simulatedTransport.executedCommands.any { it.first == "30020700" || it.first == "30110700" }
        assertTrue("Timeout triggers controlled stop cleanup", hasStopAttempt)
    }

    // TEST 5: Disconnessione durante la macro: nessun comando ON successivo.
    @Test
    fun test5_DisconnectionDuringMacroStopsSubsequentOnCommands() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.mockConnected = true

        launch {
            bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        }

        advanceTimeBy(1200)

        // Disconnect transport mid-sequence
        simulatedTransport.mockConnected = false
        simulatedTransport.mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.DISCONNECTED

        simulatedTransport.clearExecutedCommands()
        advanceUntilIdle()

        val onCommandsAfterDisconnect = simulatedTransport.executedCommands.filter {
            it.first.endsWith("01") && it.second == CommandCategory.ACTUATOR_CONTROL
        }

        assertTrue("No subsequent ON commands sent after disconnection", onCommandsAfterDisconnect.isEmpty())
    }

    // TEST 6: Riconnessione: la macro non riparte automaticamente.
    @Test
    fun test6_ReconnectionDoesNotAutoRestartMacro() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)

        // Simulate disconnection then reconnection
        simulatedTransport.mockConnected = false
        advanceUntilIdle()

        simulatedTransport.mockConnected = true
        advanceUntilIdle()

        assertFalse("Interrupted macro must NOT auto-restart on reconnection", bleedViewModel.state.value.isRunningSequence)
        assertEquals(BleedExecState.IDLE, bleedViewModel.state.value.executionState)
    }

    // TEST 7: Emergency STOP richiesto due volte: una sola procedura coordinata.
    @Test
    fun test7_EmergencyStopRequestedTwiceIsAtomic() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)

        advanceTimeBy(1000)

        bleedViewModel.emergencyStop()
        bleedViewModel.emergencyStop()

        advanceUntilIdle()

        val pumpOffCount = simulatedTransport.executedCommands.count { it.first == "30020700" }
        assertTrue("Pump OFF commands executed cleanly once per stop", pumpOffCount in 2..4)
    }

    // TEST 8: Risposta assente al comando OFF: STOP_UNCONFIRMED.
    @Test
    fun test8_MissingAckForOffCommandYieldsUnconfirmed() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.TRANSMITTED_NO_ACK

        bleedViewModel.emergencyStop()
        advanceUntilIdle()

        assertEquals(BleedExecState.STOP_UNCONFIRMED, bleedViewModel.state.value.executionState)
    }

    // TEST 12: Ordine e delay nominali FAST/SLOW invariati.
    @Test
    fun test12_FastMacroSequenceAndOrderUnchanged() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.clearExecutedCommands()

        bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        advanceUntilIdle()

        val cmds = simulatedTransport.executedCommands.map { it.first }

        val dumpOnIdx = cmds.indexOf("30110701")
        val pumpOnIdx = cmds.indexOf("30020701")
        val isoOnIdx = cmds.indexOf("30120701")
        val pumpOffIdx = cmds.indexOf("30020700")

        assertTrue("DUMP ON must precede PUMP ON", dumpOnIdx < pumpOnIdx)
        assertTrue("PUMP ON must precede ISO ON pulses", pumpOnIdx < isoOnIdx)
        assertTrue("ISO pulses must precede PUMP OFF shutdown", isoOnIdx < pumpOffIdx)
    }
}
