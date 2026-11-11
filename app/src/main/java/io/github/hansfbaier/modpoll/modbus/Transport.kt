package io.github.hansfbaier.modpoll.modbus

import io.github.hansfbaier.modpoll.data.Framer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

interface SerialTransport {
    val label: String
    val isOpen: Boolean

    fun open()

    fun close()

    fun write(data: ByteArray)

    /** Reads exactly [count] bytes or throws [ModbusTimeoutException]. */
    fun readExact(
        count: Int,
        timeoutMs: Long,
    ): ByteArray

    /** Reads up to and including [terminator] (used by Modbus ASCII). */
    fun readUntil(
        terminator: Int,
        maxBytes: Int,
        timeoutMs: Long,
    ): ByteArray

    fun purge()
}

enum class ExchangeKind(
    val label: String,
) {
    READ("Read"),
    WRITE("Write"),
    SCAN("Scan"),
    RAW("Raw"),
    CONNECT("Connect"),
}

data class FrameExchange(
    val id: Long,
    val timeMs: Long,
    val kind: ExchangeKind,
    val framer: Framer,
    val unitId: Int,
    val function: Int,
    val request: ByteArray,
    val response: ByteArray?,
    val durationMs: Long,
    val attempt: Int,
    val ok: Boolean,
    val exceptionCode: Int? = null,
    val error: String? = null,
) {
    val functionLabel: String
        get() =
            when (function) {
                1 -> "FC01 Read Coils"
                2 -> "FC02 Read Discrete"
                3 -> "FC03 Read Holding"
                4 -> "FC04 Read Input"
                5 -> "FC05 Write Coil"
                6 -> "FC06 Write Register"
                15 -> "FC15 Write Coils"
                16 -> "FC16 Write Registers"
                else -> "FC%02d".format(function)
            }
}

class TrafficRecorder(
    private val capacity: Int = 1000,
) {
    private val sequence = AtomicLong(0)
    private val _entries = MutableStateFlow<List<FrameExchange>>(emptyList())
    val entries: StateFlow<List<FrameExchange>> = _entries.asStateFlow()

    fun nextId(): Long = sequence.incrementAndGet()

    fun record(entry: FrameExchange) {
        _entries.value = (_entries.value + entry).takeLast(capacity)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun snapshot(): List<FrameExchange> = _entries.value
}

fun ByteArray.asciiOrHex(): String {
    val printable = all { it.toInt() in 9..13 || it.toInt() in 32..126 }
    return if (printable && isNotEmpty()) toString(Charsets.US_ASCII) else toHex()
}
