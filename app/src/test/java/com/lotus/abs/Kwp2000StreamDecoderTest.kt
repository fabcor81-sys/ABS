package com.lotus.abs

import com.lotus.abs.usb.Kwp2000Frame
import com.lotus.abs.usb.Kwp2000StreamDecoder
import com.lotus.abs.usb.KwpDecodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Kwp2000StreamDecoderTest {

    private val decoder = Kwp2000StreamDecoder(
        expectedTargetAddress = 0xF1.toByte(), // Tester
        expectedSourceAddress = 0x2B.toByte(), // ECU EBC430
        isEchoFilterEnabled = true
    )

    @Test
    fun testSuccessfulDecodeWithEchoFiltering() {
        val requestPayload = byteArrayOf(0x1A.toByte(), 0x80.toByte())
        val reqFrame = Kwp2000Frame.buildFrame(requestPayload, targetAddress = 0x2B.toByte(), sourceAddress = 0xF1.toByte())
        val txBytes = reqFrame.rawBytes

        val respPayload = byteArrayOf(0x5A.toByte(), 0x80.toByte(), 0x01.toByte())
        val respFrame = Kwp2000Frame.buildFrame(respPayload, targetAddress = 0xF1.toByte(), sourceAddress = 0x2B.toByte())
        val ecuBytes = respFrame.rawBytes

        // Combined stream: TX Echo + ECU Response
        val combinedStream = txBytes + ecuBytes

        val result = decoder.decodeStream(combinedStream, txBytes, requestedServiceId = 0x1A)

        assertTrue(result.isSuccess)
        assertEquals(KwpDecodeStatus.SUCCESS, result.status)
        assertTrue(result.isEchoMatched)
        assertEquals(txBytes.size, result.echoBytes.size)
        assertEquals(ecuBytes.size, result.ecuBytes.size)
    }

    @Test
    fun testIncompleteResponseReturnsIncompleteStatus() {
        val txBytes = byteArrayOf(0x82.toByte(), 0x2B.toByte(), 0xF1.toByte(), 0x1A.toByte(), 0x80.toByte(), 0x48.toByte())
        val partialEcuBytes = byteArrayOf(0x83.toByte(), 0xF1.toByte(), 0x2B.toByte()) // Incomplete header

        val result = decoder.decodeStream(txBytes + partialEcuBytes, txBytes, requestedServiceId = 0x1A)

        assertFalse(result.isSuccess)
        assertEquals(KwpDecodeStatus.INCOMPLETE_RESPONSE, result.status)
    }

    @Test
    fun testNegativeResponseCodeNRCParsing() {
        val txBytes = byteArrayOf(0x82.toByte(), 0x2B.toByte(), 0xF1.toByte(), 0x21.toByte(), 0x01.toByte(), 0x1E.toByte())

        // 0x7F 0x21 0x12 (NRC 12 = SubFunctionNotSupported)
        val nrcPayload = byteArrayOf(0x7F.toByte(), 0x21.toByte(), 0x12.toByte())
        val nrcFrame = Kwp2000Frame.buildFrame(nrcPayload, targetAddress = 0xF1.toByte(), sourceAddress = 0x2B.toByte())

        val result = decoder.decodeStream(txBytes + nrcFrame.rawBytes, txBytes, requestedServiceId = 0x21)

        assertFalse(result.isSuccess)
        assertEquals(KwpDecodeStatus.NEGATIVE_RESPONSE, result.status)
        assertTrue(result.statusText.contains("NRC"))
    }
}
