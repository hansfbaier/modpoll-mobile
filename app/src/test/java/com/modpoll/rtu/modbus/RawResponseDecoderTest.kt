package com.modpoll.rtu.modbus

import org.junit.Assert.assertTrue
import org.junit.Test

class RawResponseDecoderTest {
    @Test
    fun decodesHoldingRegisterValues() {
        val response =
            ModbusResponse(
                unitId = 1,
                function = 3,
                payload = byteArrayOf(4, 0x00, 0x02, 0x05, 0xDC.toByte()),
                raw = ByteArray(0),
            )
        val text = RawResponseDecoder.decode(response, byteArrayOf(3, 0, 0, 0, 2))
        assertTrue(text.contains("FC03 holding registers starting at 0"))
        assertTrue(text.contains("0: 2  (0x0002)"))
        assertTrue(text.contains("1: 1500  (0x05DC)"))
    }

    @Test
    fun decodesCoilStates() {
        val response =
            ModbusResponse(
                unitId = 1,
                function = 1,
                payload = byteArrayOf(1, 0b1000_1101.toByte()),
                raw = ByteArray(0),
            )
        val text = RawResponseDecoder.decode(response, byteArrayOf(1, 0, 0, 0, 8))
        assertTrue(text.contains("0: ON"))
        assertTrue(text.contains("1: OFF"))
        assertTrue(text.contains("2: ON"))
        assertTrue(text.contains("7: ON"))
    }

    @Test
    fun decodesSingleCoilWrite() {
        val response =
            ModbusResponse(
                unitId = 1,
                function = 5,
                payload = byteArrayOf(0, 4, 0xFF.toByte(), 0),
                raw = ByteArray(0),
            )
        val text = RawResponseDecoder.decode(response, byteArrayOf(5, 0, 4, 0xFF.toByte(), 0))
        assertTrue(text.contains("Coil 4"))
        assertTrue(text.contains("ON"))
    }

    @Test
    fun decodesRegisterWrite() {
        val response =
            ModbusResponse(
                unitId = 1,
                function = 6,
                payload = byteArrayOf(0, 1, 0x06, 0x40),
                raw = ByteArray(0),
            )
        val text = RawResponseDecoder.decode(response, byteArrayOf(6, 0, 1, 0x06, 0x40))
        assertTrue(text.contains("Register 1"))
        assertTrue(text.contains("1600"))
        assertTrue(text.contains("0x0640"))
    }
}
