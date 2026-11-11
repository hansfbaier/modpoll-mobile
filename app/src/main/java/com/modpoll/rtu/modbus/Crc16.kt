package com.modpoll.rtu.modbus

/**
 * Modbus CRC-16 (polynomial 0xA001, initial value 0xFFFF).
 * Transmitted low byte first.
 */
object Crc16 {
    fun compute(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
    ): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        return crc and 0xFFFF
    }

    /** Returns [frame] with the CRC appended (low byte, high byte). */
    fun append(frame: ByteArray): ByteArray {
        val crc = compute(frame)
        val out = frame.copyOf(frame.size + 2)
        out[frame.size] = (crc and 0xFF).toByte()
        out[frame.size + 1] = ((crc shr 8) and 0xFF).toByte()
        return out
    }

    fun isValid(
        frame: ByteArray,
        length: Int = frame.size,
    ): Boolean {
        if (length < 4 || length > frame.size) return false
        val crc = compute(frame, 0, length - 2)
        val received = (frame[length - 2].toInt() and 0xFF) or ((frame[length - 1].toInt() and 0xFF) shl 8)
        return crc == received
    }
}

fun ByteArray.toHex(separator: String = " "): String = joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

fun ByteArray.toHexDump(): String {
    val builder = StringBuilder()
    for (offset in indices step 16) {
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(copyOfRange(offset, minOf(offset + 16, size)).toHex(" "))
    }
    return builder.toString()
}
