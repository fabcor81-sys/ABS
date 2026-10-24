package com.lotus.abs

import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import com.lotus.abs.transport.SimulatedDiagnosticTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothManagerTest {

    // TEST 9: Comando AT con risposta OK: nessuna attesa inutile del timeout KWP2000.
    @Test
    fun test9_AtCommandWithOkReturnsImmediatelyWithoutWaitingKwpTimeout() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.ACKNOWLEDGED
        )

        val result = transport.executeCommand("ATZ", CommandCategory.READ_ONLY, timeoutMs = 5000)

        assertTrue(result.isSuccess)
        assertEquals(CommandStatus.ACKNOWLEDGED, result.status)
        assertTrue("Execution duration must be fast (< 500ms)", result.totalRoundTripMs < 500.0)
    }

    // TEST 10: Comando AT con risposta ERROR: gestione dell'errore corretta.
    @Test
    fun test10_AtCommandWithErrorReturnsNegativeResponse() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.NEGATIVE_RESPONSE
        )

        val result = transport.executeCommand("ATINVALID", CommandCategory.READ_ONLY)

        assertFalse(result.isSuccess)
        assertEquals(CommandStatus.NEGATIVE_RESPONSE, result.status)
    }

    // TEST 11: Risposta KWP2000 non pertinente: non deve essere interpretata come ACK.
    @Test
    fun test11_IrrelevantKwpResponseNotInterpretedAsAck() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.TRANSMITTED_NO_ACK
        )

        val result = transport.executeCommand("2101", CommandCategory.READ_ONLY)

        assertFalse("Unconfirmed/Irrelevant response must NOT be interpreted as ACK", result.isSuccess)
        assertEquals(CommandStatus.TRANSMITTED, result.status)
    }
}
