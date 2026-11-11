package io.github.hansfbaier.modpoll.modbus

import io.github.hansfbaier.modpoll.data.Framer

/**
 * Serial framing for Modbus.
 *
 * RTU:  [unit][pdu][crcLo][crcHi]
 * ASCII: ':' + hex([unit][pdu][lrc]) + CRLF, LRC = two's complement of the byte sum.
 */
object FramerCodec {
    const val ASCII_START = ':'
    const val ASCII_LF = '\n'.code
    const val MAX_ASCII_FRAME = 513

    fun encode(
        framer: Framer,
        unitId: Int,
        pdu: ByteArray,
    ): ByteArray =
        when (framer) {
            Framer.RTU -> ModbusFrame.request(unitId, pdu)
            Framer.ASCII -> encodeAscii(unitId, pdu)
        }

    fun encodeAscii(
        unitId: Int,
        pdu: ByteArray,
    ): ByteArray {
        val body = ByteArray(pdu.size + 1)
        body[0] = unitId.toByte()
        pdu.copyInto(body, 1)
        val lrc = lrc(body)
        val out = StringBuilder(1 + (body.size + 1) * 2 + 2)
        out.append(ASCII_START)
        for (byte in body) out.append("%02X".format(byte.toInt() and 0xFF))
        out.append("%02X".format(lrc))
        out.append("\r\n")
        return out.toString().toByteArray(Charsets.US_ASCII)
    }

    fun lrc(data: ByteArray): Int {
        var sum = 0
        for (byte in data) sum += byte.toInt() and 0xFF
        return (0x100 - (sum and 0xFF)) and 0xFF
    }

    /**
     * Validates a received frame and returns the decoded response.
     * Throws [ModbusDeviceException] when the device answered with an exception PDU.
     */
    fun decodeResponse(
        framer: Framer,
        raw: ByteArray,
        expectedUnitId: Int,
        expectedFunction: Int,
    ): ModbusResponse =
        when (framer) {
            Framer.RTU -> ModbusResponseParser.parse(raw, expectedUnitId, expectedFunction)
            Framer.ASCII -> decodeAsciiResponse(raw, expectedUnitId, expectedFunction)
        }

    private fun decodeAsciiResponse(
        raw: ByteArray,
        expectedUnitId: Int,
        expectedFunction: Int,
    ): ModbusResponse {
        val text = raw.toString(Charsets.US_ASCII).trim(' ', '\r', '\n', '\u0000')
        val start = text.indexOf(ASCII_START)
        if (start < 0) throw ModbusProtocolException("ASCII frame missing ':'")
        val hexPart = text.substring(start + 1).trim()
        if (hexPart.isEmpty() || hexPart.length % 2 != 0) {
            throw ModbusProtocolException("ASCII frame has odd length")
        }
        val bytes =
            try {
                ByteArray(hexPart.length / 2) { index ->
                    hexPart.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            } catch (_: NumberFormatException) {
                throw ModbusProtocolException("ASCII frame contains non-hex characters")
            }
        if (bytes.size < 3) throw ModbusProtocolException("ASCII frame too short")
        if (bytes.sumOf { it.toInt() and 0xFF } and 0xFF != 0) {
            throw ModbusCrcException(raw)
        }
        val unitId = bytes[0].toInt() and 0xFF
        val function = bytes[1].toInt() and 0xFF
        if (unitId != expectedUnitId) {
            throw ModbusProtocolException("Unexpected unit id $unitId (expected $expectedUnitId)")
        }
        if (function and 0x80 != 0) {
            throw ModbusDeviceException(function and 0x7F, bytes[2].toInt() and 0xFF)
        }
        if (function != expectedFunction) {
            throw ModbusProtocolException(
                "Unexpected function 0x%02X (expected 0x%02X)".format(function, expectedFunction),
            )
        }
        return ModbusResponse(
            unitId = unitId,
            function = function,
            payload = bytes.copyOfRange(2, bytes.size - 1),
            raw = raw,
        )
    }

    /**
     * Decode a request frame for the built-in simulator.
     * Returns `[unitId][pdu]` or null when the frame is invalid / does not address this bus.
     */
    fun decodeRequest(
        framer: Framer,
        raw: ByteArray,
    ): ByteArray? =
        try {
            when (framer) {
                Framer.RTU -> {
                    if (raw.size < 5 || !Crc16.isValid(raw)) {
                        null
                    } else {
                        raw.copyOfRange(0, raw.size - 2)
                    }
                }

                Framer.ASCII -> {
                    val text = raw.toString(Charsets.US_ASCII)
                    val start = text.indexOf(ASCII_START)
                    if (start < 0) {
                        null
                    } else {
                        val hex = text.substring(start + 1).trim(' ', '\r', '\n')
                        if (hex.length % 2 != 0 || hex.length < 6) {
                            null
                        } else {
                            val bytes = ByteArray(hex.length / 2)
                            var ok = true
                            for (index in bytes.indices) {
                                val value = hex.substring(index * 2, index * 2 + 2).toIntOrNull(16)
                                if (value == null) ok = false else bytes[index] = value.toByte()
                            }
                            if (!ok || bytes.sumOf { it.toInt() and 0xFF } and 0xFF != 0) {
                                null
                            } else {
                                bytes.copyOfRange(0, bytes.size - 1)
                            }
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            null
        }
}
