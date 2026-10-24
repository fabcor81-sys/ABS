package com.lotus.abs

import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.transport.CommandStatus
import com.lotus.abs.transport.SimulatedDiagnosticTransport
import com.lotus.abs.usb.Kwp2000Frame
import com.lotus.abs.usb.Kwp2000KLineTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Kwp2000KLineTransportTest {

    // TEST 1: Connessione USB e risposta KWP2000 positiva.
    @Test
    fun test1_UsbConnectionAndPositiveResponseAcknowledged() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.ACKNOWLEDGED
        )

        val result = transport.executeCommand("2101", CommandCategory.READ_ONLY)

        assertTrue(result.isSuccess)
        assertEquals(CommandStatus.ACKNOWLEDGED, result.status)
        assertEquals("2101", result.requestHex)
    }

    // TEST 2: Checksum errato.
    @Test
    fun test2_CorruptChecksumYieldsCommunicationError() = runTest {
        val corruptBytes = byteArrayOf(0x82.toByte(), 0x2B.toByte(), 0xF1.toByte(), 0x1A.toByte(), 0x80.toByte(), 0x00.toByte())
        val parsedFrame = Kwp2000Frame.parseFrame(corruptBytes)

        assertNull("Corrupt frame with invalid checksum must parse to null", parsedFrame)

        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.CORRUPT_CHECKSUM
        )
        val result = transport.executeCommand("2101", CommandCategory.READ_ONLY)

        assertFalse(result.isSuccess)
        assertEquals(CommandStatus.COMMUNICATION_ERROR, result.status)
    }

    // TEST 3: Risposta negativa KWP2000.
    @Test
    fun test3_NegativeKwpResponseYieldsNegativeResponseStatus() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.NEGATIVE_RESPONSE
        )

        val result = transport.executeCommand("2101", CommandCategory.READ_ONLY)

        assertFalse(result.isSuccess)
        assertEquals(CommandStatus.NEGATIVE_RESPONSE, result.status)
        assertEquals("12", result.nrcCode)
    }

    // TEST 4: Timeout della risposta ECU.
    @Test
    fun test4_EcuResponseTimeoutYieldsTimeoutStatus() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            mockResponseMode = SimulatedDiagnosticTransport.ResponseMode.TIMEOUT
        )

        val result = transport.executeCommand("2101", CommandCategory.READ_ONLY, timeoutMs = 500)

        assertFalse(result.isSuccess)
        assertEquals(CommandStatus.TIMEOUT, result.status)
    }

    // TEST 10: Comandi USB attuatori bloccati sull'hardware reale durante questa fase.
    @Test
    fun test10_ActuatorCommandsBlockedOnRealUsbHardware() = runTest {
        val transport = SimulatedDiagnosticTransport(
            mockConnected = true,
            allowActuatorControl = false // Real hardware safety guard
        )

        val result = transport.executeCommand("30020701", CommandCategory.ACTUATOR_CONTROL)

        assertFalse("Actuator control must be blocked on real USB hardware", result.isSuccess)
        assertEquals(CommandStatus.BLOCKED_SAFETY_GUARD, result.status)
    }

    @Test
    fun testWhitelistAllowsApprovedReadCommands() {
        val allowedCommands = listOf(
            "1A80", "1A91", "1A92", "1A94",
            "2101", "2102",
            "2130", "2131", "2132", "2133", "2134"
        )
        for (cmd in allowedCommands) {
            assertTrue(
                "Command $cmd should be allowed by USB Whitelist",
                Kwp2000KLineTransport.ALLOWED_READ_COMMANDS_HEX.contains(cmd)
            )
        }
    }

    @Test
    fun testWhitelistBlocksUnapprovedCommands() {
        val blockedCommands = listOf(
            "14FF00",   // Clear DTC
            "3B010203", // Write Data
            "2199"      // Unapproved Local ID
        )
        for (cmd in blockedCommands) {
            assertFalse(
                "Command $cmd MUST be blocked by USB Whitelist",
                Kwp2000KLineTransport.ALLOWED_READ_COMMANDS_HEX.contains(cmd)
            )
        }
    }

    @Test
    fun testKwp2000FrameChecksumAndParsing() {
        val payload = byteArrayOf(0x1A.toByte(), 0x80.toByte())
        val frame = Kwp2000Frame.buildFrame(
            payload = payload,
            targetAddress = 0x2B.toByte(),
            sourceAddress = 0xF1.toByte()
        )

        assertEquals(0x82.toByte(), frame.formatByte)
        assertEquals(0x2B.toByte(), frame.targetAddress)
        assertEquals(0xF1.toByte(), frame.sourceAddress)

        val parsed = Kwp2000Frame.parseFrame(frame.rawBytes)
        assertNotNull("Frame should parse successfully", parsed)
        assertEquals(frame.checksum, parsed?.checksum)
    }
}
