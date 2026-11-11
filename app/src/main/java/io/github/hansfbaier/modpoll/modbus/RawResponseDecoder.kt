package io.github.hansfbaier.modpoll.modbus

/**
 * Human-readable decode of a raw-tool reply.
 *
 * The request PDU is needed to recover the start address and the requested quantity,
 * because a Modbus read response only carries a byte count.
 */
object RawResponseDecoder {
    fun decode(
        response: ModbusResponse,
        requestPdu: ByteArray,
    ): String {
        val function = response.function
        val address = requestU16(requestPdu, 1)
        val quantity = requestU16(requestPdu, 3)
        return when (function) {
            1, 2 -> {
                val count = quantity?.coerceAtLeast(1) ?: (response.dataPayload().size * 8).coerceAtLeast(1)
                val bits =
                    runCatching { response.bits(count) }
                        .getOrElse { response.bits(response.dataPayload().size * 8) }
                buildString {
                    append("FC%02d %s".format(function, if (function == 1) "coils" else "discrete inputs"))
                    if (address != null) append(" starting at $address")
                    append(" — ${bits.size} value(s)\n")
                    bits.forEachIndexed { index, on ->
                        append("  ")
                        append(if (address != null) address + index else index)
                        append(": ")
                        append(if (on) "ON" else "OFF")
                        if (index != bits.lastIndex) append('\n')
                    }
                }
            }

            3, 4 -> {
                val count = quantity?.coerceAtLeast(1) ?: (response.dataPayload().size / 2).coerceAtLeast(1)
                val registers =
                    runCatching { response.registers(count) }
                        .getOrElse { response.registers(response.dataPayload().size / 2) }
                buildString {
                    append("FC%02d %s".format(function, if (function == 3) "holding registers" else "input registers"))
                    if (address != null) append(" starting at $address")
                    append(" — ${registers.size} value(s)\n")
                    registers.forEachIndexed { index, value ->
                        append("  ")
                        append(if (address != null) address + index else index)
                        append(": ")
                        append(value)
                        append("  (0x%04X)".format(value))
                        if (index != registers.lastIndex) append('\n')
                    }
                    if (registers.isNotEmpty()) {
                        append("\n  int16 view: ")
                        append(registers.joinToString(" ") { it.toShort().toString() })
                    }
                }
            }

            5 -> {
                val target = address ?: requestU16(requestPdu, 1)
                val raw = requestU16(requestPdu, 3)
                val state = if (raw == 0xFF00) "ON" else "OFF"
                "Coil ${target ?: "?"} acknowledged: $state"
            }

            6 -> {
                val target = address
                val value = requestU16(requestPdu, 3)
                "Register ${target ?: "?"} acknowledged: ${value ?: "?"}" +
                    if (value != null) " (0x%04X)".format(value) else ""
            }

            15, 16 -> {
                val what = if (function == 15) "coils" else "registers"
                "FC%02d write acknowledged for %s at %s: %s value(s)".format(
                    function,
                    what,
                    address?.toString() ?: "?",
                    quantity?.toString() ?: "?",
                )
            }

            else -> {
                response.dataPayload().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            }
        }
    }

    private fun requestU16(
        pdu: ByteArray,
        offset: Int,
    ): Int? {
        if (pdu.size < offset + 2) return null
        return ((pdu[offset].toInt() and 0xFF) shl 8) or (pdu[offset + 1].toInt() and 0xFF)
    }
}
