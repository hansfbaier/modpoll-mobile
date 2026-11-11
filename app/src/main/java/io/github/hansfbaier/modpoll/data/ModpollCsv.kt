package io.github.hansfbaier.modpoll.data

import java.util.UUID

data class CsvImportResult(
    val profiles: List<DeviceProfile>,
    val warnings: List<String>,
)

/**
 * Import and export of modpoll-style CSV configuration files.
 *
 * ```
 * device,<name>,<unit_id>,,
 * poll,<object_type>,<start>,<count>,<endian>
 * ref,<name>,<address>,<dtype>,<rw>,<unit>,<scale>
 * ```
 *
 * Bit-table references identify 8-bit groups in the file (`bool8` at address 3 = bits 24..31),
 * matching modpoll. Internally the app stores absolute bit addresses.
 */
object ModpollCsv {
    fun import(text: String): CsvImportResult {
        val warnings = mutableListOf<String>()
        val profiles = mutableListOf<DeviceProfile>()
        var device: DeviceProfile? = null
        var group: SignalGroup? = null
        val usedNames = mutableSetOf<String>()

        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val columns = line.split(',').map { it.trim() }
            when (columns[0].lowercase()) {
                "device" -> {
                    if (columns.size < 3) {
                        warnings += "Line $lineNumber: device row needs a name and unit id"
                        return@forEachIndexed
                    }
                    val name = columns[1].ifBlank { "Device" }
                    val unit = columns[2].toIntOrNull()
                    if (unit == null || unit !in 1..247) {
                        warnings += "Line $lineNumber: invalid unit id '${columns[2]}' (1..247)"
                        device = null
                        group = null
                        return@forEachIndexed
                    }
                    usedNames.clear()
                    device =
                        DeviceProfile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            unitId = unit,
                            description = "Imported from modpoll CSV",
                        )
                    profiles += device!!
                    group = null
                }

                "poll" -> {
                    val profile = device
                    if (profile == null) {
                        warnings += "Line $lineNumber: poll before device row, skipped"
                        return@forEachIndexed
                    }
                    if (columns.size < 5) {
                        warnings += "Line $lineNumber: poll row needs type, start, size and endian"
                        return@forEachIndexed
                    }
                    val objectType = ObjectType.parse(columns[1])
                    if (objectType == null) {
                        warnings += "Line $lineNumber: unknown object type '${columns[1]}'"
                        group = null
                        return@forEachIndexed
                    }
                    val start = columns[2].toIntOrNull()
                    val count = columns[3].toIntOrNull()
                    val endian = Endian.parse(columns[4])
                    if (start == null || start !in 0..65535) {
                        warnings += "Line $lineNumber: invalid start address '${columns[2]}'"
                        group = null
                        return@forEachIndexed
                    }
                    if (count == null || count <= 0 || count > objectType.maximumReadQuantity) {
                        warnings += "Line $lineNumber: invalid poll size '${columns[3]}' (1..${objectType.maximumReadQuantity})"
                        group = null
                        return@forEachIndexed
                    }
                    if (start + count > 65536) {
                        warnings += "Line $lineNumber: poll range $start+$count exceeds 65535"
                        group = null
                        return@forEachIndexed
                    }
                    if (endian == null) {
                        warnings += "Line $lineNumber: invalid endian '${columns[4]}', using BE_BE"
                    }
                    val newGroup =
                        SignalGroup(
                            id = UUID.randomUUID().toString(),
                            name = "${objectType.label} $start",
                            objectType = objectType,
                            start = start,
                            count = count,
                            intervalMs = 1000,
                        )
                    group = newGroup
                    val updated = profile.copy(groups = profile.groups + newGroup)
                    profiles[profiles.lastIndex] = updated
                    device = updated
                }

                "ref" -> {
                    val currentDevice = device
                    val currentGroup = group
                    if (currentDevice == null || currentGroup == null) {
                        warnings += "Line $lineNumber: ref outside a poll block, skipped"
                        return@forEachIndexed
                    }
                    if (columns.size < 5) {
                        warnings += "Line $lineNumber: ref row needs name, address, dtype and rw"
                        return@forEachIndexed
                    }
                    val rawName = columns[1].ifBlank { "ref$lineNumber" }
                    var name = rawName
                    var suffix = 2
                    while (!usedNames.add(name)) {
                        name = "${rawName}_$suffix"
                        suffix++
                    }
                    if (name != rawName) warnings += "Line $lineNumber: duplicate ref '$rawName' renamed to '$name'"

                    val addressText = columns[2]
                    val addressParts = addressText.split(':')
                    val baseAddress = addressParts[0].toIntOrNull()
                    val bit =
                        addressParts.getOrNull(1)?.toIntOrNull().also {
                            if (addressParts.size > 1 && it == null) {
                                warnings += "Line $lineNumber: invalid bit index in '$addressText'"
                            }
                        }
                    if (baseAddress == null || baseAddress !in 0..65535) {
                        warnings += "Line $lineNumber: invalid address '$addressText'"
                        return@forEachIndexed
                    }

                    val dtypeText = columns[3].lowercase()
                    var dataType = DataType.parse(dtypeText)
                    if (dataType == null) {
                        warnings += "Line $lineNumber: unsupported data type '$dtypeText'"
                        return@forEachIndexed
                    }
                    var stringBytes = 0
                    if (dataType == DataType.STRING) {
                        stringBytes = dtypeText.removePrefix("string").toIntOrNull() ?: 0
                        if (stringBytes <= 0) {
                            warnings += "Line $lineNumber: invalid string length '$dtypeText'"
                            return@forEachIndexed
                        }
                    }

                    val writable = columns.getOrNull(4)?.lowercase()?.contains('w') == true
                    val unit = columns.getOrNull(5).orEmpty()
                    val scale =
                        columns.getOrNull(6)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                            ?: 1.0
                    if (columns.getOrNull(6)?.isNotBlank() == true && columns[6].toDoubleOrNull() == null) {
                        warnings += "Line $lineNumber: invalid scale '${columns[6]}', using 1.0"
                    }

                    if (currentGroup.objectType.isBitTable) {
                        if (bit != null) {
                            warnings += "Line $lineNumber: address:bit is only valid for register tables"
                            return@forEachIndexed
                        }
                        val type =
                            when (dataType) {
                                DataType.BOOL, DataType.BOOL8 -> {
                                    DataType.BOOL8
                                }

                                DataType.BOOL16 -> {
                                    DataType.BOOL16
                                }

                                else -> {
                                    warnings += "Line $lineNumber: ${dataType.label} is not valid in a bit table"
                                    return@forEachIndexed
                                }
                            }
                        val bitAddress = currentGroup.start + (baseAddress - currentGroup.start) * 8
                        val width = if (type == DataType.BOOL16) 16 else 8
                        if (baseAddress < currentGroup.start) {
                            warnings += "Line $lineNumber: ref '$name' starts before the poll block, skipped"
                            return@forEachIndexed
                        }
                        if (bitAddress < currentGroup.start || bitAddress + width > currentGroup.start + currentGroup.count) {
                            warnings += "Line $lineNumber: ref '$name' at bit $bitAddress exceeds poll range"
                            return@forEachIndexed
                        }
                        val signal =
                            Signal(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                address = bitAddress,
                                dataType = type,
                                endian = Endian.BE_BE,
                                writable = writable,
                                unit = if (writable) unit else unit,
                                description = "",
                            )
                        val (updatedDevice, updatedGroup) = appendSignal(currentDevice, currentGroup, signal, profiles)
                        device = updatedDevice
                        group = updatedGroup
                    } else {
                        if (dataType == DataType.BOOL8 || dataType == DataType.BOOL16) {
                            warnings += "Line $lineNumber: ${dataType.label} is only valid in bit tables, skipped"
                            return@forEachIndexed
                        }
                        var bitIndex = bit
                        if (dataType == DataType.BOOL && bitIndex == null) {
                            warnings += "Line $lineNumber: bool on a register should use address:bit, assuming bit 0"
                            bitIndex = 0
                        }
                        if (dataType != DataType.BOOL && bitIndex != null) {
                            warnings += "Line $lineNumber: address:bit is only valid for bool, skipped"
                            return@forEachIndexed
                        }
                        if (bitIndex != null && bitIndex !in 0..15) {
                            warnings += "Line $lineNumber: bit index $bitIndex out of range 0..15"
                            return@forEachIndexed
                        }
                        val signal =
                            Signal(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                address = baseAddress,
                                dataType = dataType,
                                endian = Endian.parse(columns[4]) ?: Endian.BE_BE,
                                bit = bitIndex,
                                stringBytes = stringBytes,
                                scale = scale,
                                unit = unit,
                                writable = writable,
                            )
                        if (!currentGroup.contains(signal)) {
                            warnings += "Line $lineNumber: ref '$name' at $baseAddress exceeds poll range " +
                                "${currentGroup.start}..${currentGroup.endAddress}"
                            return@forEachIndexed
                        }
                        val (updatedDevice, updatedGroup) = appendSignal(currentDevice, currentGroup, signal, profiles)
                        device = updatedDevice
                        group = updatedGroup
                    }
                }

                else -> {
                    warnings += "Line $lineNumber: unknown row type '${columns[0]}'"
                }
            }
        }

        val valid = profiles.filter { it.groups.any { group -> group.signals.isNotEmpty() } }
        if (valid.isEmpty()) warnings += "No usable device found in the CSV"
        return CsvImportResult(valid, warnings)
    }

    private fun appendSignal(
        device: DeviceProfile,
        group: SignalGroup,
        signal: Signal,
        profiles: MutableList<DeviceProfile>,
    ): Pair<DeviceProfile, SignalGroup> {
        val updatedGroup = group.copy(signals = group.signals + signal)
        val updatedGroups = device.groups.map { if (it.id == group.id) updatedGroup else it }
        val updatedDevice = device.copy(groups = updatedGroups)
        profiles[profiles.lastIndex] = updatedDevice
        return updatedDevice to updatedGroup
    }

    fun export(profile: DeviceProfile): Pair<String, List<String>> {
        val warnings = mutableListOf<String>()
        val out = StringBuilder()
        out.appendLine("# ModPoll RTU export")
        out.appendLine("device,${sanitize(profile.name)},${profile.unitId},,")
        profile.groups.forEach { group ->
            val endian = group.signals.firstOrNull()?.endian ?: Endian.BE_BE
            if (group.signals.map { it.endian }.distinct().size > 1) {
                warnings += "Group '${group.name}' mixes endian settings; exported as ${endian.name}"
            }
            if (group.signals.any { it.offset != 0.0 }) {
                warnings += "Group '${group.name}' uses offsets, which modpoll CSV cannot express"
            }
            out.appendLine(
                "poll,${group.objectType.name.lowercase()},${group.start},${group.count},${endian.name}",
            )
            group.signals.forEach { signal ->
                val (address, dtype) =
                    if (group.objectType.isBitTable) {
                        val relative = signal.address - group.start
                        if (relative % 8 != 0) {
                            warnings += "'${signal.name}' is not aligned to an 8-bit group; exported address rounded down"
                        }
                        (group.start + relative / 8) to if (signal.dataType == DataType.BOOL16) "bool16" else "bool8"
                    } else {
                        val type =
                            when {
                                signal.dataType == DataType.STRING -> "string${signal.stringBytes}"
                                signal.dataType == DataType.BOOL -> "bool"
                                else -> signal.dataType.label
                            }
                        val addressText =
                            if (signal.dataType == DataType.BOOL && signal.bit != null) {
                                "${signal.address}:${signal.bit}"
                            } else {
                                signal.address.toString()
                            }
                        addressText to type
                    }
                val rw = if (signal.writable) "rw" else "r"
                val scale = if (signal.scale != 1.0) signal.scale.toString() else ""
                out.appendLine(
                    "ref,${sanitize(signal.name)},$address,$dtype,$rw,${signal.unit},$scale",
                )
            }
        }
        return out.toString() to warnings
    }

    private fun sanitize(value: String): String = value.replace(',', ' ').replace('\n', ' ').trim()
}
