package com.modpoll.rtu.modbus

/** Base class for all Modbus/transport failures surfaced to the UI. */
open class ModbusException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class ModbusTimeoutException(
    message: String = "No response from device",
) : ModbusException(message)

class ModbusCrcException(
    val frame: ByteArray,
) : ModbusException("CRC mismatch: ${frame.toHex()}")

class ModbusProtocolException(
    message: String,
) : ModbusException(message)

/** Modbus exception response from the remote device (function | 0x80). */
class ModbusDeviceException(
    val functionCode: Int,
    val exceptionCode: Int,
) : ModbusException("Exception $exceptionCode: ${describeException(exceptionCode)}")

fun describeException(code: Int): String =
    when (code) {
        1 -> "Illegal function"
        2 -> "Illegal data address"
        3 -> "Illegal data value"
        4 -> "Slave device failure"
        5 -> "Acknowledge (processing)"
        6 -> "Slave device busy"
        8 -> "Memory parity error"
        10 -> "Gateway path unavailable"
        11 -> "Gateway target device failed to respond"
        else -> "Unknown exception"
    }

object ModbusLimits {
    const val MAX_READ_BITS = 2000
    const val MAX_READ_REGISTERS = 125
    const val MAX_WRITE_REGISTERS = 123
    const val MAX_WRITE_BITS = 1968
}

object ModbusFrame {
    fun request(
        unitId: Int,
        pdu: ByteArray,
    ): ByteArray {
        require(unitId in 0..255) { "Unit id out of range: $unitId" }
        val body = ByteArray(pdu.size + 1)
        body[0] = unitId.toByte()
        pdu.copyInto(body, 1)
        return Crc16.append(body)
    }

    fun readRequest(
        unitId: Int,
        function: Int,
        address: Int,
        quantity: Int,
    ): ByteArray {
        requireRead(function, address, quantity)
        val pdu =
            byteArrayOf(
                function.toByte(),
                (address shr 8).toByte(),
                (address and 0xFF).toByte(),
                (quantity shr 8).toByte(),
                (quantity and 0xFF).toByte(),
            )
        return request(unitId, pdu)
    }

    fun writeSingleCoil(
        unitId: Int,
        address: Int,
        on: Boolean,
    ): ByteArray {
        requireAddress(address)
        val value = if (on) 0xFF00 else 0x0000
        val pdu =
            byteArrayOf(
                5,
                (address shr 8).toByte(),
                (address and 0xFF).toByte(),
                (value shr 8).toByte(),
                (value and 0xFF).toByte(),
            )
        return request(unitId, pdu)
    }

    fun writeSingleRegister(
        unitId: Int,
        address: Int,
        value: Int,
    ): ByteArray {
        requireAddress(address)
        require(value in 0..0xFFFF) { "Register value out of range: $value" }
        val pdu =
            byteArrayOf(
                6,
                (address shr 8).toByte(),
                (address and 0xFF).toByte(),
                (value shr 8).toByte(),
                (value and 0xFF).toByte(),
            )
        return request(unitId, pdu)
    }

    fun writeMultipleCoils(
        unitId: Int,
        address: Int,
        values: BooleanArray,
    ): ByteArray {
        requireAddress(address)
        require(values.isNotEmpty()) { "No coil values" }
        require(values.size <= ModbusLimits.MAX_WRITE_BITS) { "Too many coils: ${values.size}" }
        val byteCount = (values.size + 7) / 8
        val pdu = ByteArray(6 + byteCount)
        pdu[0] = 15
        pdu[1] = (address shr 8).toByte()
        pdu[2] = (address and 0xFF).toByte()
        pdu[3] = (values.size shr 8).toByte()
        pdu[4] = (values.size and 0xFF).toByte()
        pdu[5] = byteCount.toByte()
        values.forEachIndexed { index, on ->
            if (on) {
                val byteIndex = 6 + index / 8
                pdu[byteIndex] = (pdu[byteIndex].toInt() or (1 shl (index % 8))).toByte()
            }
        }
        return request(unitId, pdu)
    }

    fun writeMultipleRegisters(
        unitId: Int,
        address: Int,
        values: IntArray,
    ): ByteArray {
        requireAddress(address)
        require(values.isNotEmpty()) { "No register values" }
        require(values.size <= ModbusLimits.MAX_WRITE_REGISTERS) { "Too many registers: ${values.size}" }
        require(values.all { it in 0..0xFFFF }) { "Register value out of range" }
        val pdu = ByteArray(6 + values.size * 2)
        pdu[0] = 16
        pdu[1] = (address shr 8).toByte()
        pdu[2] = (address and 0xFF).toByte()
        pdu[3] = (values.size shr 8).toByte()
        pdu[4] = (values.size and 0xFF).toByte()
        pdu[5] = (values.size * 2).toByte()
        values.forEachIndexed { index, value ->
            pdu[6 + index * 2] = (value shr 8).toByte()
            pdu[7 + index * 2] = (value and 0xFF).toByte()
        }
        return request(unitId, pdu)
    }

    /** Build a frame from a raw PDU (function code + payload) and append CRC. */
    fun rawRequest(
        unitId: Int,
        pdu: ByteArray,
    ): ByteArray {
        require(pdu.isNotEmpty()) { "PDU is empty" }
        return request(unitId, pdu)
    }

    private fun requireRead(
        function: Int,
        address: Int,
        quantity: Int,
    ) {
        require(function in 1..4) { "Unsupported read function: $function" }
        requireAddress(address)
        require(quantity >= 1) { "Quantity must be positive" }
        val limit = if (function <= 2) ModbusLimits.MAX_READ_BITS else ModbusLimits.MAX_READ_REGISTERS
        require(quantity <= limit) { "Quantity $quantity exceeds limit $limit" }
        require(address + quantity <= 65536) { "Address range exceeds 65535" }
    }

    private fun requireAddress(address: Int) {
        require(address in 0..65535) { "Address out of range: $address" }
    }
}

data class ModbusResponse(
    val unitId: Int,
    val function: Int,
    val payload: ByteArray,
    val raw: ByteArray,
) {
    /** Byte-count-prefixed payload for FC01..FC04, raw payload otherwise. */
    fun dataPayload(): ByteArray =
        if (function in 1..4 && payload.isNotEmpty()) payload.copyOfRange(1, payload.size) else payload

    fun registers(count: Int): IntArray {
        val data = dataPayload()
        if (data.size < count * 2) {
            throw ModbusProtocolException("Response too short for $count registers")
        }
        return IntArray(count) { index ->
            ((data[index * 2].toInt() and 0xFF) shl 8) or (data[index * 2 + 1].toInt() and 0xFF)
        }
    }

    fun bits(count: Int): BooleanArray {
        val data = dataPayload()
        val required = (count + 7) / 8
        if (data.size < required) {
            throw ModbusProtocolException("Response too short for $count bits")
        }
        return BooleanArray(count) { index ->
            (data[index / 8].toInt() shr (index % 8)) and 1 == 1
        }
    }
}

object ModbusResponseParser {
    const val MIN_FRAME = 5

    /** Total frame length implied by the first two bytes (unit id, function). */
    fun expectedTotalLength(
        unitId: Int,
        function: Int,
        thirdByte: Int,
    ): Int =
        when {
            function and 0x80 != 0 -> 5
            function in 1..4 -> 3 + thirdByte + 2
            function in 5..6 -> 8
            function in 15..16 -> 8
            else -> throw ModbusProtocolException("Unexpected function code 0x%02X".format(function))
        }

    fun parse(
        frame: ByteArray,
        expectedUnitId: Int,
        expectedFunction: Int,
    ): ModbusResponse {
        if (frame.size < MIN_FRAME) throw ModbusProtocolException("Frame too short: ${frame.size} bytes")
        if (!Crc16.isValid(frame)) throw ModbusCrcException(frame)
        val unitId = frame[0].toInt() and 0xFF
        val function = frame[1].toInt() and 0xFF
        if (unitId != expectedUnitId) {
            throw ModbusProtocolException("Unexpected unit id $unitId (expected $expectedUnitId)")
        }
        if (function and 0x80 != 0) {
            val code = frame[2].toInt() and 0xFF
            throw ModbusDeviceException(function and 0x7F, code)
        }
        if (function != expectedFunction) {
            throw ModbusProtocolException("Unexpected function 0x%02X (expected 0x%02X)".format(function, expectedFunction))
        }
        return ModbusResponse(
            unitId = unitId,
            function = function,
            payload = frame.copyOfRange(2, frame.size - 2),
            raw = frame,
        )
    }
}
