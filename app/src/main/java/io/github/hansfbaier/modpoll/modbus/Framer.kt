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
            Framer.TCP -> encodeTcp(unitId, pdu, 0)
        }

    fun encodeTcp(
        unitId: Int,
        pdu: ByteArray,
        transactionId: Int,
    ): ByteArray {
        require(unitId in 0..255) { "Unit id out of range: $unitId" }
        val length = pdu.size + 1
        val frame = ByteArray(7 + pdu.size)
        frame[0] = (transactionId ushr 8).toByte()
        frame[1] = transactionId.toByte()
        frame[2] = 0
        frame[3] = 0
        frame[4] = (length ushr 8).toByte()
        frame[5] = length.toByte()
        frame[6] = unitId.toByte()
        pdu.copyInto(frame, 7)
        return frame
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
            Framer.TCP -> throw ModbusProtocolException("Use decodeTcpResponse for TCP framing")
        }

    fun decodeTcpResponse(
        raw: ByteArray,
        expectedTransactionId: Int,
        expectedUnitId: Int,
        expectedFunction: Int,
    ): ModbusResponse {
        if (raw.size < 9) throw ModbusProtocolException("MBAP frame too short: ${raw.size} bytes")
        val transactionId = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
        val protocol = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val length = ((raw[4].toInt() and 0xFF) shl 8) or (raw[5].toInt() and 0xFF)
        val unitId = raw[6].toInt() and 0xFF
        if (protocol != 0) throw ModbusProtocolException("Unexpected MBAP protocol id $protocol")
        if (transactionId != expectedTransactionId) {
            throw ModbusProtocolException(
                "Unexpected MBAP transaction $transactionId (expected $expectedTransactionId)",
            )
        }
        if (length < 2 || raw.size != 6 + length) {
            throw ModbusProtocolException("Invalid MBAP length $length for ${raw.size} bytes")
        }
        if (unitId != expectedUnitId) {
            throw ModbusProtocolException("Unexpected unit id $unitId (expected $expectedUnitId)")
        }
        val function = raw[7].toInt() and 0xFF
        if (function and 0x80 != 0) {
            if (raw.size < 9) throw ModbusProtocolException("Short exception frame")
            throw ModbusDeviceException(function and 0x7F, raw[8].toInt() and 0xFF)
        }
        if (function != expectedFunction) {
            throw ModbusProtocolException(
                "Unexpected function 0x%02X (expected 0x%02X)".format(function, expectedFunction),
            )
        }
        return ModbusResponse(
            unitId = unitId,
            function = function,
            payload = raw.copyOfRange(8, raw.size),
            raw = raw,
        )
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

                Framer.TCP -> null
            }
        } catch (_: RuntimeException) {
            null
        }
}
