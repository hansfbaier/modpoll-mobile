package com.modpoll.rtu.modbus

import com.modpoll.rtu.data.DataType
import com.modpoll.rtu.data.Endian
import com.modpoll.rtu.data.Signal
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** A decoded signal sample. Pure data so it can be unit tested without Android. */
data class Measurement(
    val numeric: Double? = null,
    val bits: List<Boolean> = emptyList(),
    val text: String? = null,
    val formatted: String = "—",
    val rawHex: String = "",
    val rawWords: List<Int> = emptyList(),
    val valid: Boolean = true,
    val error: String? = null,
)

sealed interface EncodeResult {
    data class Ok(
        val words: IntArray,
    ) : EncodeResult

    data class Error(
        val message: String,
    ) : EncodeResult
}

data class Interpretation(
    val label: String,
    val value: String,
)

object ValueCodec {
    fun decodeRegister(
        signal: Signal,
        registers: IntArray,
        blockStart: Int,
    ): Measurement {
        val index = signal.address - blockStart
        val span = signal.registerSpan
        if (index < 0 || index + span > registers.size || span <= 0) {
            return invalid("Address ${signal.address} is outside this poll block")
        }
        val words = registers.copyOfRange(index, index + span)
        return decodeWords(signal, words)
    }

    fun decodeWords(
        signal: Signal,
        words: IntArray,
    ): Measurement {
        if (words.isEmpty()) return invalid("No data")
        return try {
            val rawHex = words.joinToString(" ") { "%04X".format(it and 0xFFFF) }
            when (signal.dataType) {
                DataType.BOOL -> decodeRegisterBool(signal, words, rawHex)
                DataType.BOOL8 -> decodeRegisterBitField(signal, words, 8, rawHex)
                DataType.BOOL16 -> decodeRegisterBitField(signal, words, 16, rawHex)
                DataType.STRING -> decodeString(signal, words, rawHex)
                else -> decodeNumeric(signal, words, rawHex)
            }
        } catch (failure: Exception) {
            invalid(failure.message ?: "Decode failed")
        }
    }

    fun decodeCoil(
        signal: Signal,
        coils: BooleanArray,
        blockStart: Int,
    ): Measurement {
        val index = signal.address - blockStart
        val width = signal.bitWidth.coerceAtLeast(1)
        if (index < 0 || index + width > coils.size) {
            return invalid("Coil ${signal.address} is outside this poll block")
        }
        val bits = (0 until width).map { coils[index + it] }
        val packed = bits.foldIndexed(0L) { position, acc, on -> if (on) acc or (1L shl position) else acc }
        return Measurement(
            numeric = if (width == 1) (if (bits[0]) 1.0 else 0.0) else null,
            bits = bits,
            formatted = if (width == 1) (if (bits[0]) "ON" else "OFF") else pattern(bits),
            rawHex = bitHex(packed, width),
            rawWords = listOf(packed.toInt() and 0xFFFF),
            valid = true,
        )
    }

    private fun decodeRegisterBool(
        signal: Signal,
        words: IntArray,
        rawHex: String,
    ): Measurement {
        val bit = (signal.bit ?: 0).coerceIn(0, 15)
        val word = canonicalWords(words, signal.endian).first()
        val on = ((word shr bit) and 1) == 1
        return Measurement(
            numeric = if (on) 1.0 else 0.0,
            bits = listOf(on),
            formatted = if (on) "ON" else "OFF",
            rawHex = rawHex,
            rawWords = words.toList(),
            valid = true,
        )
    }

    private fun decodeRegisterBitField(
        signal: Signal,
        words: IntArray,
        width: Int,
        rawHex: String,
    ): Measurement {
        val ordered = canonicalWords(words, signal.endian)
        var packed = 0L
        for (index in 0 until minOf(width, ordered.size * 16)) {
            val word = ordered[index / 16]
            if ((word shr (index % 16)) and 1 == 1) packed = packed or (1L shl index)
        }
        val bits = (0 until width).map { (packed shr it) and 1L == 1L }
        return Measurement(
            numeric = null,
            bits = bits,
            formatted = pattern(bits),
            rawHex = rawHex,
            rawWords = words.toList(),
            valid = true,
        )
    }

    private fun decodeString(
        signal: Signal,
        words: IntArray,
        rawHex: String,
    ): Measurement {
        val canonical = canonicalWords(words, signal.endian)
        val bytes = ByteArray(canonical.size * 2)
        canonical.forEachIndexed { index, word ->
            bytes[index * 2] = (word shr 8).toByte()
            bytes[index * 2 + 1] = (word and 0xFF).toByte()
        }
        val length = signal.stringBytes.coerceAtMost(bytes.size)
        val text =
            bytes
                .copyOfRange(0, length)
                .toString(Charsets.UTF_8)
                .substringBefore('\u0000')
                .trim()
        return Measurement(
            numeric = null,
            text = text,
            formatted = text.ifBlank { "·" },
            rawHex = rawHex,
            rawWords = words.toList(),
            valid = true,
        )
    }

    private fun decodeNumeric(
        signal: Signal,
        words: IntArray,
        rawHex: String,
    ): Measurement {
        val ordered = canonicalWords(words, signal.endian)
        val bytes = ByteArray(ordered.size * 2)
        ordered.forEachIndexed { index, word ->
            bytes[index * 2] = (word shr 8).toByte()
            bytes[index * 2 + 1] = (word and 0xFF).toByte()
        }
        val raw: Double =
            when (signal.dataType) {
                DataType.UINT16 -> bytes.u16(0).toDouble()
                DataType.INT16 -> bytes.u16(0).toShort().toDouble()
                DataType.UINT32 -> bytes.u32(0).toDouble()
                DataType.INT32 -> bytes.i32(0).toDouble()
                DataType.UINT64 -> BigInteger(1, bytes.copyOfRange(0, 8)).toDouble()
                DataType.INT64 -> bytes.u64(0).toDouble()
                DataType.FLOAT16 -> halfToFloat(bytes.u16(0)).toDouble()
                DataType.FLOAT32 -> Float.fromBits(bytes.i32(0)).toDouble()
                DataType.FLOAT64 -> Double.fromBits(bytes.u64(0))
                else -> return invalid("Not a numeric type")
            }
        if (raw.isNaN() || raw.isInfinite()) return invalid("Non-finite value")
        val scaled = raw * signal.scale + signal.offset
        val decimals = decimalsFor(signal)
        val exact =
            if (signal.scale == 1.0 && signal.offset == 0.0) {
                when (signal.dataType) {
                    DataType.UINT32 -> bytes.u32(0).toString()
                    DataType.INT32 -> bytes.i32(0).toString()
                    DataType.UINT64 -> BigInteger(1, bytes.copyOfRange(0, 8)).toString()
                    DataType.INT64 -> bytes.u64(0).toString()
                    else -> null
                }
            } else {
                null
            }
        val text =
            if (exact !=
                null
            ) {
                (if (signal.unit.isBlank()) exact else "$exact ${signal.unit}")
            } else {
                formatNumber(scaled, decimals, signal.unit)
            }
        return Measurement(
            numeric = scaled,
            formatted = text,
            rawHex = rawHex,
            rawWords = words.toList(),
            valid = true,
        )
    }

    fun decodeBits(
        signal: Signal,
        bits: BooleanArray,
        blockStart: Int,
    ): Measurement {
        val index = signal.address - blockStart
        val width = signal.bitWidth.coerceAtLeast(1)
        if (index < 0 || index + width > bits.size) {
            return invalid("Bit ${signal.address} is outside this poll block")
        }
        val slice = (0 until width).map { bits[index + it] }
        val packed = slice.foldIndexed(0L) { position, acc, on -> if (on) acc or (1L shl position) else acc }
        return Measurement(
            numeric = if (width == 1) (if (slice[0]) 1.0 else 0.0) else null,
            bits = slice,
            formatted = if (width == 1) (if (slice[0]) "ON" else "OFF") else pattern(slice),
            rawHex = bitHex(packed, width),
            rawWords = listOf(packed.toInt() and 0xFFFF),
            valid = true,
        )
    }

    fun decimalsFor(signal: Signal): Int {
        signal.decimals?.let { return it.coerceIn(0, 9) }
        return if (signal.dataType.isFloat || signal.scale != 1.0 || signal.offset != 0.0) 2 else 0
    }

    fun formatNumber(
        value: Double,
        decimals: Int,
        unit: String,
    ): String {
        val safe = if (abs(value) < 1e-12) 0.0 else value
        val text =
            when {
                abs(safe) >= 1e9 || (safe != 0.0 && abs(safe) < 1e-6) -> {
                    String.format(Locale.US, "%.4E", safe)
                }

                else -> {
                    String.format(Locale.US, "%.${decimals.coerceIn(0, 9)}f", safe)
                }
            }
        return if (unit.isBlank()) text else "$text $unit"
    }

    fun pattern(bits: List<Boolean>): String =
        bits.reversed().chunked(4).joinToString(" ") { chunk ->
            chunk.joinToString("") { if (it) "1" else "0" }
        }

    fun encodeRegister(
        signal: Signal,
        input: String,
    ): EncodeResult {
        return try {
            when (signal.dataType) {
                DataType.BOOL -> {
                    val on = parseBoolean(input) ?: return EncodeResult.Error("Expected 0/1, ON/OFF")
                    EncodeResult.Ok(intArrayOf(if (on) 1 else 0))
                }

                DataType.BOOL8, DataType.BOOL16 -> {
                    val width = if (signal.dataType == DataType.BOOL8) 8 else 16
                    val value = parseInteger(input) ?: return EncodeResult.Error("Expected an integer pattern")
                    val limit = BigInteger.ONE.shiftLeft(width)
                    if (value < BigInteger.ZERO || value >= limit) {
                        return EncodeResult.Error("Value must be 0..${limit.subtract(BigInteger.ONE)}")
                    }
                    val number = value.toInt()
                    val words = if (width <= 8) intArrayOf(number) else intArrayOf(number and 0xFFFF)
                    EncodeResult.Ok(words)
                }

                DataType.STRING -> {
                    val raw = input.toByteArray(Charsets.UTF_8)
                    val byteCount = signal.stringBytes.coerceAtLeast(1)
                    val padded = raw.copyOf(byteCount).let { it.copyOf(byteCount) }
                    val words =
                        IntArray((byteCount + 1) / 2) { index ->
                            val high = padded.getOrNull(index * 2)?.toInt()?.and(0xFF) ?: 0
                            val low = padded.getOrNull(index * 2 + 1)?.toInt()?.and(0xFF) ?: 0
                            (high shl 8) or low
                        }
                    EncodeResult.Ok(applyEndian(words.toList(), signal.endian))
                }

                else -> {
                    encodeNumeric(signal, input)
                }
            }
        } catch (failure: Exception) {
            EncodeResult.Error(failure.message ?: "Invalid value")
        }
    }

    private fun encodeNumeric(
        signal: Signal,
        input: String,
    ): EncodeResult {
        val parsed = parseNumber(input) ?: return EncodeResult.Error("Enter a number")
        if (parsed.value.isNaN() || parsed.value.isInfinite()) {
            return EncodeResult.Error("Value must be finite")
        }
        val target = (parsed.value - signal.offset) / signal.scale
        if (target.isNaN() || target.isInfinite()) {
            return EncodeResult.Error("Value cannot be scaled")
        }

        val widthBytes =
            when (signal.dataType) {
                DataType.UINT16, DataType.INT16, DataType.FLOAT16 -> 2
                DataType.UINT32, DataType.INT32, DataType.FLOAT32 -> 4
                DataType.UINT64, DataType.INT64, DataType.FLOAT64 -> 8
                else -> return EncodeResult.Error("Unsupported type for write")
            }

        val bytes =
            when (signal.dataType) {
                DataType.FLOAT16 -> {
                    putU16(halfFromFloat(target.toFloat()))
                }

                DataType.FLOAT32 -> {
                    putU32(target.toFloat().toRawBits())
                }

                DataType.FLOAT64 -> {
                    putU64(target.toDouble().toRawBits())
                }

                else -> {
                    val integer =
                        when {
                            parsed.integer != null && signal.scale == 1.0 && signal.offset == 0.0 -> parsed.integer
                            else -> BigDecimal(target).setScale(0, RoundingMode.HALF_UP).toBigInteger()
                        }
                    val signed = signal.dataType.isSigned
                    val bits = widthBytes * 8
                    val min = if (signed) BigInteger.ONE.shiftLeft(bits - 1).negate() else BigInteger.ZERO
                    val max =
                        if (signed) {
                            BigInteger.ONE
                                .shiftLeft(
                                    bits - 1,
                                ).subtract(BigInteger.ONE)
                        } else {
                            BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
                        }
                    if (integer < min || integer > max) {
                        return EncodeResult.Error("Out of range for ${signal.dataType.label} ($min..$max)")
                    }
                    bigIntegerToBytes(integer, widthBytes)
                }
            }

        val words =
            IntArray(widthBytes / 2) { index ->
                ((bytes[index * 2].toInt() and 0xFF) shl 8) or (bytes[index * 2 + 1].toInt() and 0xFF)
            }
        return EncodeResult.Ok(applyEndian(words.toList(), signal.endian))
    }

    fun parseBoolean(input: String): Boolean? =
        when (input.trim().lowercase()) {
            "1", "on", "true", "yes", "high" -> true
            "0", "off", "false", "no", "low" -> false
            else -> null
        }

    fun parseInteger(input: String): BigInteger? {
        val text = input.trim().replace(" ", "").replace("_", "")
        if (text.isEmpty()) return null
        return try {
            when {
                text.startsWith("0x", true) -> BigInteger(text.substring(2), 16)
                text.startsWith("0b", true) -> BigInteger(text.substring(2), 2)
                else -> BigInteger(text)
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private data class ParsedNumber(
        val value: Double,
        val integer: BigInteger?,
    )

    private fun parseNumber(input: String): ParsedNumber? {
        val text = input.trim().replace(" ", "").replace("_", "")
        if (text.isEmpty()) return null
        val integer = if (text.startsWith("0x", true) || text.startsWith("0b", true)) parseInteger(text) else null
        if (integer != null) return ParsedNumber(integer.toDouble(), integer)
        return try {
            val value = text.toDouble()
            if (text.matches(Regex("-?\\d+"))) ParsedNumber(value, BigInteger(text)) else ParsedNumber(value, null)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun bigIntegerToBytes(
        value: BigInteger,
        width: Int,
    ): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(width) { if (value.signum() < 0) 0xFF.toByte() else 0 }
        if (raw.size <= width) {
            raw.copyInto(out, width - raw.size)
        } else {
            raw.copyInto(out, 0, raw.size - width, raw.size)
        }
        return out
    }

    private fun bitHex(
        packed: Long,
        width: Int,
    ): String = "0x" + packed.toString(16).uppercase().padStart((width + 3) / 4, '0')

    private fun putU16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun putU32(value: Int): ByteArray =
        byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun putU64(value: Long): ByteArray = ByteArray(8) { index -> (value ushr (56 - index * 8)).toByte() }

    fun canonicalWords(
        deviceWords: IntArray,
        endian: Endian,
    ): List<Int> {
        var words = deviceWords.toList()
        if (endian.byteLittle) words = words.map { swapBytes(it) }
        if (endian.wordLittle) words = words.reversed()
        return words
    }

    fun applyEndian(
        canonicalWords: List<Int>,
        endian: Endian,
    ): IntArray {
        var words = canonicalWords
        if (endian.wordLittle) words = words.reversed()
        if (endian.byteLittle) words = words.map { swapBytes(it) }
        return words.toIntArray()
    }

    private fun swapBytes(value: Int): Int = ((value and 0xFF) shl 8) or ((value ushr 8) and 0xFF)

    fun halfToFloat(half: Int): Float {
        val sign = (half and 0x8000) shl 16
        val exponent = (half ushr 10) and 0x1F
        val mantissa = half and 0x03FF
        val bits =
            when (exponent) {
                0 -> {
                    if (mantissa == 0) {
                        sign
                    } else {
                        var m = mantissa
                        var e = -1
                        while (m and 0x0400 == 0) {
                            m = m shl 1
                            e++
                        }
                        sign or ((e + 127 - 15) shl 23) or ((m and 0x03FF) shl 13)
                    }
                }

                31 -> {
                    sign or 0x7F800000.toInt() or (mantissa shl 13)
                }

                else -> {
                    sign or ((exponent - 15 + 127) shl 23) or (mantissa shl 13)
                }
            }
        return Float.fromBits(bits)
    }

    fun halfFromFloat(value: Float): Int {
        val bits = value.toRawBits()
        val sign = (bits ushr 16) and 0x8000
        val exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        val mantissa = bits and 0x7FFFFF
        return when {
            value.isNaN() -> {
                sign or 0x7E00
            }

            value.isInfinite() -> {
                sign or 0x7C00
            }

            exponent <= 0 -> {
                if (exponent < -10) {
                    sign
                } else {
                    val shifted = (mantissa or 0x800000) ushr (14 - exponent)
                    sign or (shifted.coerceAtMost(0x3FF))
                }
            }

            exponent >= 31 -> {
                sign or 0x7C00
            }

            else -> {
                sign or (exponent shl 10) or (mantissa ushr 13)
            }
        }
    }

    fun interpretations(
        words: IntArray,
        endian: Endian,
    ): List<Interpretation> {
        val canonical = canonicalWords(words, endian)
        val bytes = ByteArray(canonical.size * 2)
        canonical.forEachIndexed { index, word ->
            bytes[index * 2] = (word shr 8).toByte()
            bytes[index * 2 + 1] = (word and 0xFF).toByte()
        }
        val result = mutableListOf<Interpretation>()
        result += Interpretation("HEX", words.joinToString(" ") { "%04X".format(it and 0xFFFF) })
        if (bytes.size >= 2) {
            result += Interpretation("uint16", bytes.u16(0).toString())
            result += Interpretation("int16", bytes.u16(0).toShort().toString())
            result += Interpretation("bin16", "%016d".format(bytes.u16(0).toString(2).toLong()))
            result += Interpretation("float16", halfToFloat(bytes.u16(0)).toString())
        }
        if (bytes.size >= 4) {
            result += Interpretation("uint32", bytes.u32(0).toString())
            result += Interpretation("int32", bytes.i32(0).toString())
            result += Interpretation("float32", "%.7g".format(Locale.US, Float.fromBits(bytes.i32(0))))
        }
        if (bytes.size >= 8) {
            result += Interpretation("uint64", BigInteger(1, bytes.copyOfRange(0, 8)).toString())
            result += Interpretation("int64", bytes.u64(0).toString())
            result += Interpretation("float64", "%.15g".format(Locale.US, Double.fromBits(bytes.u64(0))))
        }
        return result
    }

    private fun invalid(message: String): Measurement = Measurement(formatted = "—", valid = false, error = message)

    private fun ByteArray.u16(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.u32(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)

    private fun ByteArray.i32(offset: Int): Int = u32(offset).toInt()

    private fun ByteArray.u64(offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (this[offset + index].toLong() and 0xFF)
        }
        return value
    }
}
