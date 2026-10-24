package com.lotus.abs

import com.lotus.abs.usb.Kwp2000Frame
import com.lotus.abs.usb.Kwp2000KLineTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Kwp2000KLineTransportTest {

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
    fun testWhitelistBlocksActuatorAndWriteCommands() {
        val blockedCommands = listOf(
            "30020701", // ABS Pump ON
            "30110701", // DUMP LF ON
            "30120701", // ISO LF ON
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

        // Raw bytes: Format(0x82), Target(0x2B), Source(0xF1), 0x1A, 0x80, Checksum
        assertEquals(0x82.toByte(), frame.formatByte)
        assertEquals(0x2B.toByte(), frame.targetAddress)
        assertEquals(0xF1.toByte(), frame.sourceAddress)

        val parsed = Kwp2000Frame.parseFrame(frame.rawBytes)
        assertNotNull("Frame should parse successfully", parsed)
        assertEquals(frame.checksum, parsed?.checksum)
    }

    @Test
    fun testInvalidChecksumReturnsNullFrame() {
        val corruptBytes = byteArrayOf(0x82.toByte(), 0x2B.toByte(), 0xF1.toByte(), 0x1A.toByte(), 0x80.toByte(), 0x00.toByte())
        val parsed = Kwp2000Frame.parseFrame(corruptBytes)
        assertNull("Corrupt frame must return null", parsed)
    }
}
