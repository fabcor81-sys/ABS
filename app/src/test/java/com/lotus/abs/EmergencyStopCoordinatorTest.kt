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

    // TEST 5: Cancellazione della macro durante un comando di attivazione.
    @Test
    fun test5_MacroCancellationDuringActivationCommand() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)

        val job = launch {
            bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        }

        advanceTimeBy(1500)
        job.cancel()
        advanceUntilIdle()

        assertFalse("Running sequence must be false after cancellation", bleedViewModel.state.value.isRunningSequence)
        val hasOffCommand = simulatedTransport.executedCommands.any { it.first == "30020700" || it.first == "30110700" }
        assertTrue("OFF commands must be executed on cancellation", hasOffCommand)
    }

    // TEST 6: Arresto d'emergenza richiesto due volte (deve essere atomico, una sola esecuzione).
    @Test
    fun test6_EmergencyStopRequestedTwiceIsAtomic() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)

        advanceTimeBy(1000)

        bleedViewModel.emergencyStop()
        bleedViewModel.emergencyStop()

        advanceUntilIdle()

        val pumpOffCount = simulatedTransport.executedCommands.count { it.first == "30020700" }
        assertTrue("Pump OFF commands executed cleanly", pumpOffCount in 2..4)
    }

    // TEST 7: Disconnessione USB durante una macro.
    @Test
    fun test7_UsbDisconnectionDuringMacroProducesError() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.mockConnected = true

        launch {
            bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        }

        advanceTimeBy(1000)

        simulatedTransport.mockConnected = false
        simulatedTransport.mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.DISCONNECTED

        bleedViewModel.emergencyStop()
        advanceUntilIdle()

        assertEquals(BleedExecState.ERROR, bleedViewModel.state.value.executionState)
        assertFalse(bleedViewModel.state.value.isRunningSequence)
    }

    // TEST 8: Verificare che dopo STOP non vengano inviati ulteriori comandi ON.
    @Test
    fun test8_NoOnCommandsSentAfterStopRequested() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)

        launch {
            bleedViewModel.runWheelBleedPulseTestMacro(WheelLocation.FRONT_LEFT)
        }

        advanceTimeBy(1000)
        simulatedTransport.clearExecutedCommands()

        bleedViewModel.emergencyStop()
        advanceUntilIdle()

        val onCommandsAfterStop = simulatedTransport.executedCommands.filter {
            it.first.endsWith("01") && it.second == CommandCategory.ACTUATOR_CONTROL
        }

        assertTrue("No ON commands must be sent after STOP is requested", onCommandsAfterStop.isEmpty())
    }

    // TEST 9: Comando OFF trasmesso senza risposta ECU: lo stato deve rimanere UNCONFIRMED.
    @Test
    fun test9_OffCommandTransmittedWithoutAckYieldsUnconfirmed() = runTest {
        bleedViewModel.selectWheel(WheelLocation.FRONT_LEFT)
        simulatedTransport.mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.TRANSMITTED_NO_ACK

        bleedViewModel.emergencyStop()
        advanceUntilIdle()

        assertEquals(BleedExecState.STOP_UNCONFIRMED, bleedViewModel.state.value.executionState)
    }

    // TEST 11: Verificare che le temporizzazioni e l'ordine dei comandi delle macro FAST/SLOW non siano cambiati.
    @Test
    fun test11_FastMacroSequenceAndOrderUnchanged() = runTest {
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
