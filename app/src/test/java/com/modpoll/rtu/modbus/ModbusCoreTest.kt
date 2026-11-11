package com.modpoll.rtu.modbus

import com.modpoll.rtu.data.Framer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModbusCoreTest {
    @Test
    fun crcMatchesKnownVector() {
        val request = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A)
        val crc = Crc16.compute(request)
        assertEquals(0xCDC5, crc)
        val framed = Crc16.append(request)
        assertEquals(0xC5.toByte(), framed[6])
        assertEquals(0xCD.toByte(), framed[7])
        assertTrue(Crc16.isValid(framed))
    }

    @Test
    fun readRequestFrameIsWellFormed() {
        val frame = ModbusFrame.readRequest(1, 3, 0, 10)
        assertArrayEquals(
            byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A, 0xC5.toByte(), 0xCD.toByte()),
            frame,
        )
        assertTrue(Crc16.isValid(frame))
    }

    @Test
    fun asciiRequestMatchesLrcDefinition() {
        val pdu = byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x0A)
        val frame = FramerCodec.encode(Framer.ASCII, 1, pdu).toString(Charsets.US_ASCII)
        assertEquals(":01030000000AF2\r\n", frame)
    }

    @Test
    fun asciiResponseIsDecoded() {
        val payload = byteArrayOf(0x01, 0x03, 0x04, 0x43, 0x66, 0x66, 0x66)
        val lrc = FramerCodec.lrc(payload)
        val hex = payload.joinToString("") { "%02X".format(it.toInt() and 0xFF) } + "%02X".format(lrc)
        val response = FramerCodec.decodeResponse(Framer.ASCII, ":$hex\r\n".toByteArray(), 1, 3)
        assertEquals(3, response.function)
        assertArrayEquals(byteArrayOf(0x04, 0x43, 0x66, 0x66, 0x66), response.payload)
    }

    @Test(expected = ModbusCrcException::class)
    fun asciiBadLrcIsRejected() {
        FramerCodec.decodeResponse(Framer.ASCII, ":0103044366666600\r\n".toByteArray(), 1, 3)
    }

    @Test
    fun exceptionResponseCarriesCode() {
        val frame = Crc16.append(byteArrayOf(0x01, 0x83.toByte(), 0x02))
        try {
            ModbusResponseParser.parse(frame, 1, 3)
            throw AssertionError("Expected ModbusDeviceException")
        } catch (expected: ModbusDeviceException) {
            assertEquals(2, expected.exceptionCode)
            assertEquals(3, expected.functionCode)
        }
    }

    @Test
    fun responseLengthUsesByteCount() {
        assertEquals(3 + 4 + 2, ModbusResponseParser.expectedTotalLength(1, 3, 4))
        assertEquals(3 + 8 + 2, ModbusResponseParser.expectedTotalLength(1, 2, 8))
        assertEquals(5, ModbusResponseParser.expectedTotalLength(1, 0x83, 2))
        assertEquals(8, ModbusResponseParser.expectedTotalLength(1, 6, 0))
    }

    @Test
    fun crcRejectsCorruption() {
        val frame = Crc16.append(byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A))
        frame[3] = 0x01
        assertFalse(Crc16.isValid(frame))
    }

    @Test
    fun multipleCoilsPackLsbFirst() {
        val frame = ModbusFrame.writeMultipleCoils(1, 16, booleanArrayOf(true, false, true, true, false, false, false, true))
        // unit + fc + addr(2) + qty(2) + byteCount(1) + 1 data byte + crc(2)
        assertEquals(10, frame.size)
        assertEquals(0b10001101, frame[7].toInt() and 0xFF)
        assertTrue(Crc16.isValid(frame))
    }
}
