package com.modpoll.rtu.modbus

import com.modpoll.rtu.data.Framer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RawResult(
    val response: ModbusResponse,
    val request: ByteArray,
    val responseRaw: ByteArray,
    val durationMs: Long,
    val attempts: Int,
) {
    val registers: IntArray
        get() {
            val byteCount =
                response.payload
                    .firstOrNull()
                    ?.toInt()
                    ?.and(0xFF) ?: 0
            return response.registers(byteCount / 2)
        }
}

data class ReadResult(
    val registers: IntArray?,
    val bits: BooleanArray?,
    val durationMs: Long,
    val attempts: Int,
)

data class WriteResult(
    val durationMs: Long,
    val attempts: Int,
)

/**
 * Modbus master for one serial link.
 *
 * A single mutex serializes requests, which is required for half-duplex RTU/ASCII buses.
 * Timeouts and retries can be overridden per call (used by the scanner and raw tool).
 */
class ModbusMaster(
    private val transport: SerialTransport,
    private val framer: Framer,
    val defaultUnitId: Int = 1,
    private val defaultTimeoutMs: Int = 1000,
    private val defaultRetries: Int = 1,
    private val interFrameDelayMs: Long = 20,
    private val recorder: TrafficRecorder? = null,
) {
    private val mutex = Mutex()

    val transportLabel: String get() = transport.label
    val framing: Framer get() = framer

    suspend fun readCoils(
        address: Int,
        quantity: Int,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.READ,
    ): ReadResult = read(1, address, quantity, unitId, timeoutMs, retries, kind)

    suspend fun readDiscreteInputs(
        address: Int,
        quantity: Int,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.READ,
    ): ReadResult = read(2, address, quantity, unitId, timeoutMs, retries, kind)

    suspend fun readHoldingRegisters(
        address: Int,
        quantity: Int,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.READ,
    ): ReadResult = read(3, address, quantity, unitId, timeoutMs, retries, kind)

    suspend fun readInputRegisters(
        address: Int,
        quantity: Int,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.READ,
    ): ReadResult = read(4, address, quantity, unitId, timeoutMs, retries, kind)

    suspend fun read(
        function: Int,
        address: Int,
        quantity: Int,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.READ,
    ): ReadResult {
        if (function !in 1..4) throw ModbusProtocolException("Unsupported read function $function")
        if (quantity < 1 || quantity > if (function <= 2) 2000 else 125) {
            throw ModbusProtocolException("Invalid quantity $quantity for FC$function")
        }
        if (address < 0 || address + quantity > 65536) {
            throw ModbusProtocolException("Address range $address+$quantity exceeds 65535")
        }
        val pdu =
            byteArrayOf(
                function.toByte(),
                (address shr 8).toByte(),
                (address and 0xFF).toByte(),
                (quantity shr 8).toByte(),
                (quantity and 0xFF).toByte(),
            )
        val result = execute(pdu, function, unitId, timeoutMs, retries, kind)
        return when (function) {
            1, 2 -> {
                val byteCount =
                    result.response.payload
                        .firstOrNull()
                        ?.toInt()
                        ?.and(0xFF) ?: 0
                if (byteCount < (quantity + 7) / 8) {
                    throw ModbusProtocolException("Short bit response: $byteCount bytes for $quantity bits")
                }
                ReadResult(
                    registers = null,
                    bits = result.response.bits(quantity),
                    durationMs = result.durationMs,
                    attempts = result.attempts,
                )
            }

            3, 4 -> {
                if (result.response.payload.size < quantity * 2 + 1) {
                    throw ModbusProtocolException(
                        "Short register response: ${result.response.payload.size} bytes for $quantity registers",
                    )
                }
                ReadResult(
                    registers = result.response.registers(quantity),
                    bits = null,
                    durationMs = result.durationMs,
                    attempts = result.attempts,
                )
            }

            else -> {
                throw ModbusProtocolException("Unsupported read function $function")
            }
        }
    }

    suspend fun writeSingleCoil(
        address: Int,
        on: Boolean,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.WRITE,
    ): WriteResult {
        val frame = ModbusFrame.writeSingleCoil(unitId, address, on)
        val result = executeFrame(frame, 5, unitId, timeoutMs, retries, kind)
        return WriteResult(result.durationMs, result.attempts)
    }

    suspend fun writeSingleRegister(
        address: Int,
        value: Int,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.WRITE,
    ): WriteResult {
        val frame = ModbusFrame.writeSingleRegister(unitId, address, value)
        val result = executeFrame(frame, 6, unitId, timeoutMs, retries, kind)
        return WriteResult(result.durationMs, result.attempts)
    }

    suspend fun writeMultipleCoils(
        address: Int,
        values: BooleanArray,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.WRITE,
    ): WriteResult {
        val frame = ModbusFrame.writeMultipleCoils(unitId, address, values)
        val result = executeFrame(frame, 15, unitId, timeoutMs, retries, kind)
        return WriteResult(result.durationMs, result.attempts)
    }

    suspend fun writeMultipleRegisters(
        address: Int,
        values: IntArray,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = defaultRetries,
        kind: ExchangeKind = ExchangeKind.WRITE,
    ): WriteResult {
        val frame = ModbusFrame.writeMultipleRegisters(unitId, address, values)
        val result = executeFrame(frame, 16, unitId, timeoutMs, retries, kind)
        return WriteResult(result.durationMs, result.attempts)
    }

    /** Send a raw PDU (function code + payload). CRC/LRC and framing are added here. */
    suspend fun rawRequest(
        pdu: ByteArray,
        unitId: Int = defaultUnitId,
        timeoutMs: Int = defaultTimeoutMs,
        retries: Int = 0,
        kind: ExchangeKind = ExchangeKind.RAW,
    ): RawResult {
        if (pdu.isEmpty()) throw ModbusProtocolException("PDU is empty")
        val function = pdu[0].toInt() and 0xFF
        return execute(pdu, function, unitId, timeoutMs, retries, kind)
    }

    private suspend fun execute(
        pdu: ByteArray,
        function: Int,
        unitId: Int,
        timeoutMs: Int,
        retries: Int,
        kind: ExchangeKind,
    ): RawResult = executeFrame(FramerCodec.encode(framer, unitId, pdu), function, unitId, timeoutMs, retries, kind)

    private suspend fun executeFrame(
        requestFrame: ByteArray,
        function: Int,
        unitId: Int,
        timeoutMs: Int,
        retries: Int,
        kind: ExchangeKind,
    ): RawResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val attempts = (retries.coerceAtLeast(0) + 1).coerceAtMost(MAX_ATTEMPTS)
                var lastError: ModbusException? = null
                for (attempt in 1..attempts) {
                    val started = System.nanoTime()
                    var responseFrame: ByteArray? = null
                    try {
                        transport.purge()
                        if (interFrameDelayMs > 0) delay(interFrameDelayMs)
                        transport.write(requestFrame)
                        val raw = receiveResponse(unitId, function, timeoutMs)
                        responseFrame = raw
                        val elapsed = (System.nanoTime() - started) / 1_000_000L
                        val response = FramerCodec.decodeResponse(framer, raw, unitId, function)
                        recorder?.record(
                            FrameExchange(
                                id = recorder.nextId(),
                                timeMs = System.currentTimeMillis(),
                                kind = kind,
                                framer = framer,
                                unitId = unitId,
                                function = function,
                                request = requestFrame,
                                response = raw,
                                durationMs = elapsed,
                                attempt = attempt,
                                ok = true,
                            ),
                        )
                        return@withLock RawResult(response, requestFrame, raw, elapsed, attempt)
                    } catch (device: ModbusDeviceException) {
                        val elapsed = (System.nanoTime() - started) / 1_000_000L
                        recorder?.record(
                            FrameExchange(
                                id = recorder.nextId(),
                                timeMs = System.currentTimeMillis(),
                                kind = kind,
                                framer = framer,
                                unitId = unitId,
                                function = function,
                                request = requestFrame,
                                response = responseFrame,
                                durationMs = elapsed,
                                attempt = attempt,
                                ok = false,
                                exceptionCode = device.exceptionCode,
                                error = device.message,
                            ),
                        )
                        throw device
                    } catch (failure: Exception) {
                        val elapsed = (System.nanoTime() - started) / 1_000_000L
                        val error =
                            when (failure) {
                                is ModbusException -> failure
                                else -> ModbusException("Serial error: ${failure.message ?: failure.javaClass.simpleName}", failure)
                            }
                        recorder?.record(
                            FrameExchange(
                                id = recorder.nextId(),
                                timeMs = System.currentTimeMillis(),
                                kind = kind,
                                framer = framer,
                                unitId = unitId,
                                function = function,
                                request = requestFrame,
                                response = responseFrame,
                                durationMs = elapsed,
                                attempt = attempt,
                                ok = false,
                                error = error.message,
                            ),
                        )
                        lastError = error
                    }
                    if (attempt < attempts) delay(RETRY_DELAY_MS)
                }
                throw lastError ?: ModbusException("Request failed")
            }
        }

    private fun receiveResponse(
        unitId: Int,
        function: Int,
        timeoutMs: Int,
    ): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        return when (framer) {
            Framer.RTU -> {
                val header = transport.readExact(3, timeoutMs.toLong())
                val responseFunction = header[1].toInt() and 0xFF
                val third = header[2].toInt() and 0xFF
                val total = ModbusResponseParser.expectedTotalLength(unitId, responseFunction, third)
                if (total < 3) throw ModbusProtocolException("Invalid response length $total")
                if (total > 3) {
                    val remaining = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
                    val rest = transport.readExact(total - 3, remaining)
                    header + rest
                } else {
                    header
                }
            }

            Framer.ASCII -> {
                transport.readUntil(FramerCodec.ASCII_LF, FramerCodec.MAX_ASCII_FRAME, timeoutMs.toLong())
            }
        }
    }

    companion object {
        private const val RETRY_DELAY_MS = 40L
        private const val MAX_ATTEMPTS = 6
    }
}
