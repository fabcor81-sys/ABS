package com.lotus.abs

import com.lotus.abs.ui.viewmodel.BleedExecState
import com.lotus.abs.usb.Kwp2000Frame
import com.lotus.abs.usb.Kwp2000KLineTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyStopCoordinatorTest {

    @Test
    fun testBleedExecStateEnumValues() {
        val states = BleedExecState.entries.map { it.name }
        assertTrue("Enum must contain IDLE", states.contains("IDLE"))
        assertTrue("Enum must contain RUNNING", states.contains("RUNNING"))
        assertTrue("Enum must contain STOPPING", states.contains("STOPPING"))
        assertTrue("Enum must contain STOPPED_CONFIRMED", states.contains("STOPPED_CONFIRMED"))
        assertTrue("Enum must contain STOP_UNCONFIRMED", states.contains("STOP_UNCONFIRMED"))
        assertTrue("Enum must contain ERROR", states.contains("ERROR"))
    }

    @Test
    fun testUsbWhitelistDisablesActuatorsAndClearDTC() {
        val pumpOn = "30020701"
        val dumpOn = "30110701"
        val clearDtc = "14FF00"

        assertFalse("Pump ON must not be in USB whitelist", Kwp2000KLineTransport.ALLOWED_READ_COMMANDS_HEX.contains(pumpOn))
        assertFalse("DUMP ON must not be in USB whitelist", Kwp2000KLineTransport.ALLOWED_READ_COMMANDS_HEX.contains(dumpOn))
        assertFalse("Clear DTC must not be in USB whitelist", Kwp2000KLineTransport.ALLOWED_READ_COMMANDS_HEX.contains(clearDtc))
    }

    @Test
    fun testKwp2000FrameBuildingAndParsing() {
        val payload = byteArrayOf(0x1A.toByte(), 0x80.toByte())
        val frame = Kwp2000Frame.buildFrame(payload, targetAddress = 0x2B.toByte(), sourceAddress = 0xF1.toByte())

        assertEquals(0x82.toByte(), frame.formatByte)
        assertEquals(0x2B.toByte(), frame.targetAddress)
        assertEquals(0xF1.toByte(), frame.sourceAddress)
        assertEquals(6, frame.rawBytes.size)

        val parsed = Kwp2000Frame.parseFrame(frame.rawBytes)
        assertEquals(frame.checksum, parsed?.checksum)
    }
}
