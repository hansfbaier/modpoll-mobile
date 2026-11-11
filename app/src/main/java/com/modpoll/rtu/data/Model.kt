package com.modpoll.rtu.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Modbus object (table) types. Addresses are always raw protocol addresses (0..65535). */
@Serializable
enum class ObjectType(
    val label: String,
    val shortLabel: String,
    val readFunction: Int,
    val writeFunction: Int?,
    val referenceBase: Int,
) {
    COIL("Coils", "COIL", 1, 5, 0),
    DISCRETE_INPUT("Discrete Inputs", "DI", 2, null, 10000),
    INPUT_REGISTER("Input Registers", "IR", 4, null, 30000),
    HOLDING_REGISTER("Holding Registers", "HR", 3, 6, 40000),
    ;

    val isBitTable: Boolean get() = this == COIL || this == DISCRETE_INPUT

    val maximumReadQuantity: Int get() = if (isBitTable) 2000 else 125

    companion object {
        fun parse(value: String): ObjectType? =
            when (value.trim().lowercase()) {
                "coil", "coils", "c" -> COIL
                "discrete_input", "discrete_inputs", "discrete", "di", "input_status" -> DISCRETE_INPUT
                "input_register", "input_registers", "ir" -> INPUT_REGISTER
                "holding_register", "holding_registers", "hr", "holding" -> HOLDING_REGISTER
                else -> null
            }
    }
}

@Serializable
enum class DataType(
    val label: String,
    val registerSpan: Int,
    val isFloat: Boolean = false,
    val isSigned: Boolean = false,
) {
    BOOL("bool", 1),
    BOOL8("bool8", 1),
    BOOL16("bool16", 2),
    UINT16("uint16", 1),
    INT16("int16", 1, isSigned = true),
    UINT32("uint32", 2),
    INT32("int32", 2, isSigned = true),
    FLOAT16("float16", 1, isFloat = true),
    FLOAT32("float32", 2, isFloat = true),
    FLOAT64("float64", 4, isFloat = true),
    UINT64("uint64", 4),
    INT64("int64", 4, isSigned = true),
    STRING("stringN", 0),
    ;

    val isBitField: Boolean get() = this == BOOL8 || this == BOOL16
    val isInteger: Boolean get() = this in INTEGER_TYPES
    val isString: Boolean get() = this == STRING

    /** Bit width when the signal lives in a bit table. */
    val bitWidth: Int
        get() =
            when (this) {
                BOOL -> 1
                BOOL8 -> 8
                BOOL16 -> 16
                else -> 0
            }

    companion object {
        private val INTEGER_TYPES = setOf(UINT16, INT16, UINT32, INT32, UINT64, INT64)

        fun parse(raw: String): DataType? {
            val value = raw.trim().lowercase()
            if (value.startsWith("string")) return STRING
            return entries.firstOrNull { it.label == value }
        }
    }
}

@Serializable
enum class Endian(
    val byteLittle: Boolean,
    val wordLittle: Boolean,
) {
    BE_BE(false, false),
    BE_LE(false, true),
    LE_BE(true, false),
    LE_LE(true, true),
    ;

    val label: String get() = "${if (byteLittle) "LE" else "BE"}/${if (wordLittle) "LE" else "BE"}"

    companion object {
        fun parse(value: String?): Endian? = entries.firstOrNull { it.name == value?.trim()?.uppercase() }
    }
}

@Serializable
enum class Parity(
    val label: String,
) {
    NONE("None"),
    EVEN("Even"),
    ODD("Odd"),
}

/** Serial framing. RTU uses binary + CRC16, ASCII uses hex + LRC + CRLF. */
@Serializable
enum class Framer(
    val label: String,
) {
    RTU("RTU"),
    ASCII("ASCII"),
}

@Serializable
data class SerialSettings(
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Parity = Parity.NONE,
)

@Serializable
data class LinkSettings(
    val serial: SerialSettings = SerialSettings(),
    val timeoutMs: Int = 1000,
    val retries: Int = 1,
    val interFrameDelayMs: Int = 20,
    val unitId: Int = 1,
    val autoReconnect: Boolean = false,
    val autoDisableAfterFailures: Boolean = false,
    val framer: Framer = Framer.RTU,
    val lastDeviceKey: String = "",
)

/**
 * One measurement point mapped onto a register or bit range.
 *
 * [address] is the raw protocol address. For bit tables it is the bit index.
 * [scale] and [offset] are applied as `raw * scale + offset`.
 */
@Serializable
data class Signal(
    val id: String = "",
    val name: String = "",
    val address: Int = 0,
    val dataType: DataType = DataType.UINT16,
    val endian: Endian = Endian.BE_BE,
    val bit: Int? = null,
    val stringBytes: Int = 0,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String = "",
    val writable: Boolean = false,
    val decimals: Int? = null,
    val description: String = "",
    val favorite: Boolean = false,
) {
    val registerSpan: Int
        get() = if (dataType == DataType.STRING) (stringBytes.coerceAtLeast(1) + 1) / 2 else dataType.registerSpan

    val bitWidth: Int
        get() =
            when (dataType) {
                DataType.BOOL -> 1
                DataType.BOOL8 -> 8
                DataType.BOOL16 -> 16
                else -> 0
            }

    val referenceAddress: Int
        get() = address

    fun ensureId(): Signal = if (id.isBlank()) copy(id = UUID.randomUUID().toString()) else this
}

@Serializable
data class SignalGroup(
    val id: String = "",
    val name: String = "",
    val objectType: ObjectType = ObjectType.HOLDING_REGISTER,
    val start: Int = 0,
    val count: Int = 1,
    val intervalMs: Long = 1000L,
    val enabled: Boolean = true,
    val signals: List<Signal> = emptyList(),
) {
    val endAddress: Int get() = start + count - 1
    val spanLabel: String
        get() = if (count <= 1) "$start" else "$start..$endAddress"

    fun contains(signal: Signal): Boolean =
        if (objectType.isBitTable) {
            signal.address >= start && signal.address + signal.bitWidth - 1 <= endAddress
        } else {
            signal.address >= start && signal.address + signal.registerSpan - 1 <= endAddress
        }

    fun ensureIds(): SignalGroup =
        copy(
            id = id.ifBlank { UUID.randomUUID().toString() },
            signals = signals.map { it.ensureId() },
        )
}

@Serializable
data class DeviceProfile(
    val id: String = "",
    val name: String = "New device",
    val unitId: Int = 1,
    val description: String = "",
    val groups: List<SignalGroup> = emptyList(),
    val builtIn: Boolean = false,
) {
    val signalCount: Int get() = groups.sumOf { it.signals.size }
    val groupCount: Int get() = groups.size

    fun allSignals(): List<Pair<SignalGroup, Signal>> = groups.flatMap { group -> group.signals.map { group to it } }

    fun ensureIds(): DeviceProfile =
        copy(
            id = id.ifBlank { UUID.randomUUID().toString() },
            groups = groups.map { it.ensureIds() },
        )

    fun normalize(): DeviceProfile =
        ensureIds().let { profile ->
            profile.copy(
                unitId = profile.unitId.coerceIn(1, 247),
                groups =
                    profile.groups.map { group ->
                        group.copy(
                            start = group.start.coerceIn(0, 65535),
                            count = group.count.coerceIn(1, group.objectType.maximumReadQuantity),
                            intervalMs = group.intervalMs.coerceIn(50L, 3_600_000L),
                            signals = group.signals.filter { it.name.isNotBlank() },
                        )
                    },
            )
        }
}

/** All read requests needed for a group, split on the protocol limit. */
fun SignalGroup.readBlocks(maxBlock: Int = objectType.maximumReadQuantity): List<IntRange> {
    if (count <= 0) return emptyList()
    val blocks = mutableListOf<IntRange>()
    var offset = 0
    while (offset < count) {
        val size = minOf(maxBlock, count - offset)
        blocks += start + offset until start + offset + size
        offset += size
    }
    return blocks
}

fun Signal.displayName(): String = name.ifBlank { "0x%04X".format(address) }
