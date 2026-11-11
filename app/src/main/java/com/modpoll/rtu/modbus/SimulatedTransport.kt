package com.modpoll.rtu.modbus

import com.modpoll.rtu.data.Framer
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.sin

/**
 * In-app Modbus device simulator. It implements the same framing, function codes and
 * exception behavior as a real RTU device, so the complete app can be exercised without
 * USB hardware.
 */
class SimulatedTransport(
    private val framer: Framer,
    private val unitIds: Set<Int> = setOf(1, 2, 3),
    private val responseDelayMs: Long = 18,
) : SerialTransport {
    override val label: String = "Simulator (${framer.label})"
    private val bus = SimulatedBus(unitIds)
    private val pending = ArrayDeque<Byte>()
    private var readyAtNanos: Long = 0

    override var isOpen: Boolean = false
        private set

    override fun open() {
        pending.clear()
        isOpen = true
    }

    override fun close() {
        pending.clear()
        isOpen = false
    }

    override fun write(data: ByteArray) {
        if (!isOpen) throw ModbusException("Simulator is closed")
        val request = FramerCodec.decodeRequest(framer, data) ?: return
        if (request.size < 2) return
        val unitId = request[0].toInt() and 0xFF
        if (unitId !in unitIds) return
        val pdu = request.copyOfRange(1, request.size)
        val responsePdu = bus.handle(unitId, pdu) ?: return
        val response = FramerCodec.encode(framer, unitId, responsePdu)
        for (byte in response) pending.addLast(byte)
        readyAtNanos = System.nanoTime() + responseDelayMs * 1_000_000L
    }

    override fun readExact(
        count: Int,
        timeoutMs: Long,
    ): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        awaitResponse(deadline, count)
        val out = ByteArray(count)
        for (index in 0 until count) out[index] = pending.removeFirst()
        return out
    }

    override fun readUntil(
        terminator: Int,
        maxBytes: Int,
        timeoutMs: Long,
    ): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            val index = pending.indexOfFirst { it.toInt() and 0xFF == terminator }
            if (index >= 0) {
                val out = ByteArray(index + 1)
                for (i in out.indices) out[i] = pending.removeFirst()
                return out
            }
            if (pending.size >= maxBytes) throw ModbusProtocolException("Frame exceeds $maxBytes bytes")
            awaitResponse(deadline, pending.size + 1)
        }
    }

    override fun purge() {
        pending.clear()
        readyAtNanos = 0
    }

    private fun awaitResponse(
        deadline: Long,
        required: Int,
    ) {
        while (pending.size < required) {
            val now = System.nanoTime()
            if (now >= deadline) {
                throw ModbusTimeoutException("Simulated device did not respond")
            }
            if (readyAtNanos > now) {
                val waitMs = (readyAtNanos - now) / 1_000_000L
                Thread.sleep(waitMs.coerceIn(1, 20))
            } else {
                throw ModbusTimeoutException("Simulated device did not respond")
            }
        }
    }
}

/** Simple 64K register / bit memory with a live demo dataset. */
class SimulatedBus(
    private val unitIds: Set<Int>,
) {
    private val coils = HashMap<Int, BooleanArray>()
    private val discrete = HashMap<Int, BooleanArray>()
    private val holding = HashMap<Int, IntArray>()
    private val input = HashMap<Int, IntArray>()

    private val startedAt = System.currentTimeMillis()
    private val coilPatterns =
        mapOf(
            1 to booleanArrayOf(true, false, true, true, false, false, true, false, true, true, false, true, false, true, false, false),
            2 to booleanArrayOf(false, true, false, true, true, false, false, true),
            3 to booleanArrayOf(true, true, false, false, true, false, true, false),
        )

    init {
        for (unit in unitIds) {
            coils[unit] = BooleanArray(65536)
            discrete[unit] = BooleanArray(65536)
            holding[unit] = IntArray(65536)
            input[unit] = IntArray(65536)
            seed(unit)
        }
    }

    private fun seed(unit: Int) {
        val coil = coils.getValue(unit)
        val di = discrete.getValue(unit)
        val hr = holding.getValue(unit)
        val ir = input.getValue(unit)

        val pattern = coilPatterns[unit] ?: coilPatterns.getValue(1)
        pattern.copyInto(coil, 0, 0, minOf(pattern.size, 16))
        pattern.copyInto(di, 0, 0, minOf(pattern.size, 16))
        di[4] = true
        di[12] = true

        // Standard 0-based demo map (used by the built-in Demo PLC profile).
        putFloat(ir, 0, 230.4f)
        putFloat(ir, 2, 4.35f)
        putFloat(ir, 4, 1002.2f)
        putFloat(ir, 6, 50.01f)
        putFloat(ir, 8, 1234.5f)
        ir[10] = 0x0003
        putFloat(ir, 12, 38.5f)
        ir[14] = 0x00FF
        putUInt32(ir, 16, 123_456)
        putUInt32(ir, 18, 987_654)
        putString(ir, 20, "MODPOLL-DEMO", 12)

        hr[0] = 1
        hr[1] = 1500
        putFloat(hr, 2, 42.5f)
        putUInt32(hr, 4, 1_234_567)
        putFloat(hr, 6, 12.75f)
        hr[8] = 0
        hr[9] = 0
        putString(hr, 10, "PUMP-01", 7)
        hr[20] = 1
        hr[21] = 2
        hr[22] = 3
        hr[23] = 4
        putFloat(hr, 24, 0f)
        hr[26] = 0
        putUInt32(hr, 28, 0)

        // modsim-compatible addresses (3xxxx / 4xxxx), so imported modpoll examples work.
        putFloat(ir, 30000, 230.4f)
        putFloat(ir, 30002, 4.35f)
        putFloat(ir, 30004, 1002.2f)
        putFloat(ir, 30006, 50.01f)
        putFloat(ir, 30016, 230.4f)
        putFloat(ir, 30018, 4.35f)

        hr[40000] = 1
        hr[40001] = 1500
        putFloat(hr, 40002, 42.5f)
        putUInt32(hr, 40004, 1_234_567)
        putFloat(hr, 40006, 12.75f)
        putString(hr, 40036, "MODPOLL DEMO", 16)
    }

    fun handle(
        unitId: Int,
        pdu: ByteArray,
    ): ByteArray? {
        if (pdu.isEmpty()) return null
        val function = pdu[0].toInt() and 0xFF
        val hr = holding.getValue(unitId)
        val ir = input.getValue(unitId)
        val coil = coils.getValue(unitId)
        val di = discrete.getValue(unitId)
        updateLiveValues(unitId)

        return try {
            when (function) {
                1 -> readBits(pdu, coil, function)
                2 -> readBits(pdu, di, function)
                3 -> readRegisters(pdu, hr, function)
                4 -> readRegisters(pdu, ir, function)
                5 -> writeSingleCoil(pdu, coil)
                6 -> writeSingleRegister(pdu, hr)
                15 -> writeMultipleCoils(pdu, coil)
                16 -> writeMultipleRegisters(pdu, hr)
                else -> exception(function, 1)
            }
        } catch (e: SimException) {
            exception(function, e.code)
        }
    }

    private fun readBits(
        pdu: ByteArray,
        memory: BooleanArray,
        function: Int,
    ): ByteArray {
        requireSize(pdu, 5)
        val address = address(pdu, 1)
        val quantity = ((pdu[3].toInt() and 0xFF) shl 8) or (pdu[4].toInt() and 0xFF)
        if (quantity !in 1..2000) throw SimException(3)
        if (address + quantity > 65536) throw SimException(2)
        val byteCount = (quantity + 7) / 8
        val response = ByteArray(2 + byteCount)
        response[0] = function.toByte()
        response[1] = byteCount.toByte()
        for (index in 0 until quantity) {
            if (memory[address + index]) {
                response[2 + index / 8] =
                    (response[2 + index / 8].toInt() or (1 shl (index % 8))).toByte()
            }
        }
        return response
    }

    private fun readRegisters(
        pdu: ByteArray,
        memory: IntArray,
        function: Int,
    ): ByteArray {
        requireSize(pdu, 5)
        val address = address(pdu, 1)
        val quantity = ((pdu[3].toInt() and 0xFF) shl 8) or (pdu[4].toInt() and 0xFF)
        if (quantity !in 1..125) throw SimException(3)
        if (address + quantity > 65536) throw SimException(2)
        val response = ByteArray(2 + quantity * 2)
        response[0] = function.toByte()
        response[1] = (quantity * 2).toByte()
        for (index in 0 until quantity) {
            val value = memory[address + index]
            response[2 + index * 2] = (value shr 8).toByte()
            response[3 + index * 2] = (value and 0xFF).toByte()
        }
        return response
    }

    private fun writeSingleCoil(
        pdu: ByteArray,
        memory: BooleanArray,
    ): ByteArray {
        requireSize(pdu, 5)
        val address = address(pdu, 1)
        val raw = ((pdu[3].toInt() and 0xFF) shl 8) or (pdu[4].toInt() and 0xFF)
        if (raw != 0xFF00 && raw != 0x0000) throw SimException(3)
        if (address > 65535) throw SimException(2)
        memory[address] = raw == 0xFF00
        return pdu.copyOfRange(0, 5)
    }

    private fun writeSingleRegister(
        pdu: ByteArray,
        memory: IntArray,
    ): ByteArray {
        requireSize(pdu, 5)
        val address = address(pdu, 1)
        if (address > 65535) throw SimException(2)
        memory[address] = ((pdu[3].toInt() and 0xFF) shl 8) or (pdu[4].toInt() and 0xFF)
        return pdu.copyOfRange(0, 5)
    }

    private fun writeMultipleCoils(
        pdu: ByteArray,
        memory: BooleanArray,
    ): ByteArray {
        requireSize(pdu, 6)
        val address = address(pdu, 1)
        val quantity = ((pdu[3].toInt() and 0xFF) shl 8) or (pdu[4].toInt() and 0xFF)
        val byteCount = pdu[5].toInt() and 0xFF
        if (quantity !in 1..1968 || byteCount != (quantity + 7) / 8) throw SimException(3)
        if (address + quantity > 65536) throw SimException(2)
        if (pdu.size < 6 + byteCount) throw SimException(3)
        for (index in 0 until quantity) {
            val raw = pdu[6 + index / 8].toInt() and 0xFF
            memory[address + index] = (raw shr (index % 8)) and 1 == 1
        }
        return pdu.copyOfRange(0, 5)
    }

    private fun writeMultipleRegisters(
        pdu: ByteArray,
        memory: IntArray,
    ): ByteArray {
        requireSize(pdu, 6)
        val address = address(pdu, 1)
        val quantity = ((pdu[3].toInt() and 0xFF) shl 8) or (pdu[4].toInt() and 0xFF)
        val byteCount = pdu[5].toInt() and 0xFF
        if (quantity !in 1..123 || byteCount != quantity * 2) throw SimException(3)
        if (address + quantity > 65536) throw SimException(2)
        if (pdu.size < 6 + byteCount) throw SimException(3)
        for (index in 0 until quantity) {
            memory[address + index] =
                ((pdu[6 + index * 2].toInt() and 0xFF) shl 8) or
                (pdu[7 + index * 2].toInt() and 0xFF)
        }
        return pdu.copyOfRange(0, 5)
    }

    private fun exception(
        function: Int,
        code: Int,
    ): ByteArray = byteArrayOf((function or 0x80).toByte(), code.toByte())

    private fun address(
        pdu: ByteArray,
        offset: Int,
    ): Int = ((pdu[offset].toInt() and 0xFF) shl 8) or (pdu[offset + 1].toInt() and 0xFF)

    private fun requireSize(
        pdu: ByteArray,
        size: Int,
    ) {
        if (pdu.size < size) throw SimException(3)
    }

    private fun updateLiveValues(unit: Int) {
        val ir = input.getValue(unit)
        val hr = holding.getValue(unit)
        val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
        val voltage = 230.0 + 3.0 * sin(seconds / 7.0)
        val current = 4.2 + 0.35 * sin(seconds / 3.0)
        val power = voltage * current
        val frequency = 50.0 + 0.02 * sin(seconds / 11.0)
        val energy = 1234.5 + seconds * 0.01
        val temperature = 38.5 + 0.8 * sin(seconds / 17.0)

        putFloat(ir, 0, voltage.toFloat())
        putFloat(ir, 2, current.toFloat())
        putFloat(ir, 4, power.toFloat())
        putFloat(ir, 6, frequency.toFloat())
        putFloat(ir, 8, energy.toFloat())
        putFloat(ir, 12, temperature.toFloat())
        ir[10] = if ((seconds.toInt() / 5) % 2 == 0) 0x0003 else 0x0001
        putFloat(ir, 30000, voltage.toFloat())
        putFloat(ir, 30002, current.toFloat())
        putFloat(ir, 30004, power.toFloat())
        putFloat(ir, 30006, frequency.toFloat())

        // Fast-changing demo holding registers for the realtime scope.
        putFloat(hr, 24, (50.0 + 40.0 * sin(seconds * 6.0)).toFloat())
        hr[26] = ((seconds * 200).toInt() and 0xFFFF)
        putUInt32(hr, 28, (seconds * 1000).toLong())
    }

    private fun putFloat(
        memory: IntArray,
        address: Int,
        value: Float,
    ) {
        val bits = value.toRawBits()
        memory[address] = bits ushr 16
        memory[address + 1] = bits and 0xFFFF
    }

    private fun putUInt32(
        memory: IntArray,
        address: Int,
        value: Long,
    ) {
        memory[address] = ((value shr 16) and 0xFFFF).toInt()
        memory[address + 1] = (value and 0xFFFF).toInt()
    }

    private fun putString(
        memory: IntArray,
        address: Int,
        text: String,
        byteCount: Int,
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val registers = ceil(byteCount / 2.0).toInt()
        for (index in 0 until registers) {
            val high = bytes.getOrNull(index * 2)?.toInt()?.and(0xFF) ?: 0
            val low = bytes.getOrNull(index * 2 + 1)?.toInt()?.and(0xFF) ?: 0
            memory[address + index] = (high shl 8) or low
        }
    }

    private class SimException(
        val code: Int,
    ) : RuntimeException()
}
