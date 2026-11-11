package io.github.hansfbaier.modpoll.modbus

import io.github.hansfbaier.modpoll.data.DataType
import io.github.hansfbaier.modpoll.data.Endian
import io.github.hansfbaier.modpoll.data.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ValueCodecTest {
    private fun signal(
        type: DataType,
        endian: Endian = Endian.BE_BE,
        address: Int = 0,
        bit: Int? = null,
        stringBytes: Int = 0,
        scale: Double = 1.0,
    ) = Signal(
        id = "test",
        name = "test",
        address = address,
        dataType = type,
        endian = endian,
        bit = bit,
        stringBytes = stringBytes,
        scale = scale,
    )

    private fun close(
        expected: Double,
        actual: Double?,
        tolerance: Double = 0.001,
    ) {
        assertNotNull(actual)
        assertTrue("expected $expected, got $actual", abs(expected - actual!!) < tolerance)
    }

    @Test
    fun float32BigEndian() {
        val words = intArrayOf(0x4366, 0x6666)
        val measurement = ValueCodec.decodeWords(signal(DataType.FLOAT32), words)
        close(230.4, measurement.numeric)
        assertEquals("230.40", measurement.formatted)
    }

    @Test
    fun float32WordOrderLittleEndian() {
        val words = intArrayOf(0x6666, 0x4366)
        val measurement = ValueCodec.decodeWords(signal(DataType.FLOAT32, Endian.BE_LE), words)
        close(230.4, measurement.numeric)
    }

    @Test
    fun float32ByteOrderLittleEndian() {
        val words = intArrayOf(0x6643, 0x6666)
        val measurement = ValueCodec.decodeWords(signal(DataType.FLOAT32, Endian.LE_BE), words)
        close(230.4, measurement.numeric)
    }

    @Test
    fun signedInt16() {
        val measurement = ValueCodec.decodeWords(signal(DataType.INT16), intArrayOf(0xFFD6))
        close(-42.0, measurement.numeric)
    }

    @Test
    fun uint32AndScale() {
        val measurement = ValueCodec.decodeWords(signal(DataType.UINT32, scale = 0.001), intArrayOf(0x0001, 0x0000))
        close(65.536, measurement.numeric)
    }

    @Test
    fun registerBit() {
        val measurement = ValueCodec.decodeWords(signal(DataType.BOOL, bit = 3), intArrayOf(0x0008))
        assertEquals("ON", measurement.formatted)
        assertEquals(true, measurement.bits.single())
    }

    @Test
    fun stringValue() {
        val words = intArrayOf(0x5055, 0x4D50, 0x2D30, 0x3100)
        val measurement = ValueCodec.decodeWords(signal(DataType.STRING, stringBytes = 7), words)
        assertEquals("PUMP-01", measurement.text)
    }

    @Test
    fun float16Half() {
        val measurement = ValueCodec.decodeWords(signal(DataType.FLOAT16), intArrayOf(0x3C00))
        close(1.0, measurement.numeric)
    }

    @Test
    fun uint64() {
        val words = intArrayOf(0x0000, 0x0000, 0x0000, 0x0002)
        val measurement = ValueCodec.decodeWords(signal(DataType.UINT64), words)
        close(2.0, measurement.numeric)
    }

    @Test
    fun bitfieldDecodesFromCoils() {
        val bits = booleanArrayOf(true, true, false, true, false, false, false, true)
        val measurement = ValueCodec.decodeCoil(signal(DataType.BOOL8), bits, 0)
        assertEquals(8, measurement.bits.size)
        assertEquals("0x8B", measurement.rawHex)
        assertEquals(
            "1",
            measurement.bits
                .first()
                .toString()
                .replace("true", "1")
                .replace("false", "0"),
        )
    }

    @Test
    fun encodeFloat32RoundTrip() {
        val sig = signal(DataType.FLOAT32, Endian.BE_BE)
        val encoded = ValueCodec.encodeRegister(sig, "230.4")
        assertTrue(encoded is EncodeResult.Ok)
        val words = (encoded as EncodeResult.Ok).words
        val decoded = ValueCodec.decodeWords(sig, words)
        close(230.4, decoded.numeric, 0.01)
    }

    @Test
    fun encodeInt32WithWordSwap() {
        val sig = signal(DataType.INT32, Endian.LE_BE)
        val encoded = ValueCodec.encodeRegister(sig, "-100000")
        assertTrue(encoded is EncodeResult.Ok)
        val decoded = ValueCodec.decodeWords(sig, (encoded as EncodeResult.Ok).words)
        close(-100000.0, decoded.numeric)
    }

    @Test
    fun rangeCheckRejectsOverflow() {
        val result = ValueCodec.encodeRegister(signal(DataType.UINT16), "70000")
        assertTrue(result is EncodeResult.Error)
    }

    @Test
    fun hexInputIsAccepted() {
        val result = ValueCodec.encodeRegister(signal(DataType.UINT16), "0x1234")
        assertTrue(result is EncodeResult.Ok)
        assertEquals(0x1234, (result as EncodeResult.Ok).words[0])
    }

    @Test
    fun scaleRoundTrip() {
        val sig = signal(DataType.UINT16, scale = 0.1)
        val encoded = ValueCodec.encodeRegister(sig, "12.3")
        assertTrue(encoded is EncodeResult.Ok)
        val decoded = ValueCodec.decodeWords(sig, (encoded as EncodeResult.Ok).words)
        close(12.3, decoded.numeric, 0.001)
    }
}
