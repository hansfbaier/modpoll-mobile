package com.modpoll.rtu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modpoll.rtu.data.DataType
import com.modpoll.rtu.data.DeviceProfile
import com.modpoll.rtu.data.ObjectType
import com.modpoll.rtu.ui.AppViewModel
import com.modpoll.rtu.ui.RegisterCell
import com.modpoll.rtu.ui.components.EmptyState
import com.modpoll.rtu.ui.components.ErrorText
import com.modpoll.rtu.ui.components.Led
import com.modpoll.rtu.ui.components.MonoText
import com.modpoll.rtu.ui.components.SectionCard
import com.modpoll.rtu.ui.components.StatusPill
import com.modpoll.rtu.ui.components.objectAccent
import com.modpoll.rtu.ui.theme.AccentAmber
import com.modpoll.rtu.ui.theme.AccentCyan
import com.modpoll.rtu.ui.theme.AccentLime
import com.modpoll.rtu.ui.theme.AccentRose
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

@Composable
fun ToolsScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val link by vm.link.collectAsStateWithLifecycle()
    val titles = listOf("Discover", "Register map", "Raw frame")
    val icons = listOf(Icons.Outlined.Search, Icons.Outlined.GridOn, Icons.Outlined.Terminal)
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                    icon = { Icon(icons[index], null, Modifier.size(17.dp)) },
                )
            }
        }
        if (!link.connected) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AccentAmber.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Led(AccentAmber, 7)
                Spacer(Modifier.width(7.dp))
                Text(
                    "Tools need an open link. Start the simulator or connect an adapter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber,
                )
            }
        }
        when (tab) {
            0 -> DiscoverTab(vm)
            1 -> RegisterMapTab(vm)
            else -> RawFrameTab(vm)
        }
    }
}

@Composable
private fun DiscoverTab(vm: AppViewModel) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    var startUnit by rememberSaveable { mutableStateOf("1") }
    var endUnit by rememberSaveable { mutableStateOf("16") }
    var function by rememberSaveable { mutableStateOf("3") }
    var address by rememberSaveable { mutableStateOf("0") }
    var quantity by rememberSaveable { mutableStateOf("2") }
    var timeout by rememberSaveable { mutableStateOf("250") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Text("Slave ID scan", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Probe a unit-id range with a single read. Devices that answer are listed with their round-trip time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(startUnit, { startUnit = it }, "From unit", Modifier.weight(1f))
                    NumberField(endUnit, { endUnit = it }, "To unit", Modifier.weight(1f))
                    NumberField(timeout, { timeout = it }, "Timeout", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(address, { address = it }, "Address", Modifier.weight(1f))
                    NumberField(quantity, { quantity = it }, "Quantity", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("3" to "FC03 HR", "4" to "FC04 IR", "1" to "FC01 Coil", "2" to "FC02 DI").forEach { (fc, label) ->
                        FilterChip(
                            selected = function == fc,
                            onClick = { function = fc },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            vm.scanUnits(
                                startUnit.toIntOrNull() ?: 1,
                                endUnit.toIntOrNull() ?: 16,
                                function.toIntOrNull() ?: 3,
                                address.toIntOrNull() ?: 0,
                                quantity.toIntOrNull() ?: 2,
                                timeout.toIntOrNull() ?: 250,
                            )
                        },
                        enabled = !scan.running,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (scan.running) "Scanning…" else "Start scan")
                    }
                    if (scan.running) {
                        OutlinedButton(onClick = vm::cancelScan) { Text("Cancel") }
                    }
                }
                if (scan.running) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { scan.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${scan.label}  ·  ${(scan.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                scan.error?.let {
                    Spacer(Modifier.height(6.dp))
                    ErrorText(it)
                }
            }
        }
        if (scan.hits.isNotEmpty()) {
            item {
                SectionCard(accent = AccentLime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Led(AccentLime)
                        Spacer(Modifier.width(8.dp))
                        Text("${scan.hits.size} device(s) found", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    scan.hits.forEach { hit ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusPill("${hit.unitId}", AccentCyan)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Unit ${hit.unitId}", style = MaterialTheme.typography.bodyMedium)
                                MonoText(
                                    "${hit.latencyMs} ms · ${hit.sample}",
                                    size = 10,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { vm.setProfileUnitId(hit.unitId) }) { Text("Use") }
                        }
                    }
                }
            }
        } else if (!scan.running && scan.error == null) {
            item {
                EmptyState(
                    title = "No scan yet",
                    body = "Start a scan to find slave ids on the bus. Broadcast (unit 0) is not used.",
                )
            }
        }
    }
}

@Composable
private fun RegisterMapTab(vm: AppViewModel) {
    val scan by vm.registerScan.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    var objectType by rememberSaveable { mutableStateOf(ObjectType.HOLDING_REGISTER) }
    var start by rememberSaveable { mutableStateOf("0") }
    var count by rememberSaveable { mutableStateOf("32") }
    var selected by remember { mutableStateOf<RegisterCell?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(accent = objectAccent(objectType)) {
                Text("Register map", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sweep a range in 32-register blocks. Tap a cell to inspect it or add a named signal to the active profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ObjectType.entries.forEach { type ->
                        FilterChip(
                            selected = objectType == type,
                            onClick = { objectType = type },
                            label = { Text(type.shortLabel) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(start, { start = it }, "Start", Modifier.weight(1f))
                    NumberField(count, { count = it }, "Count", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.scanRegisters(
                                objectType,
                                start.toIntOrNull() ?: 0,
                                count.toIntOrNull() ?: 32,
                            )
                        },
                        enabled = !scan.running,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.GridOn, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (scan.running) "Reading…" else "Read range")
                    }
                    if (scan.running) OutlinedButton(onClick = vm::cancelRegisterScan) { Text("Cancel") }
                }
                if (scan.running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { scan.progress }, modifier = Modifier.fillMaxWidth())
                }
                scan.error?.let {
                    Spacer(Modifier.height(6.dp))
                    ErrorText(it)
                }
            }
        }
        if (scan.cells.isNotEmpty()) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${objectType.label} ${scan.start}..${scan.start + scan.count - 1}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${scan.cells.count { it.value != null }} ok · ${scan.cells.count { it.value == null }} fail",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        scan.cells.chunked(8).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { cell ->
                                    HeatCell(
                                        cell = cell,
                                        objectType = objectType,
                                        onClick = { selected = cell },
                                    )
                                }
                                repeat(8 - row.size) { Spacer(Modifier.width(42.dp)) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        } else {
            item {
                EmptyState(
                    title = "No map data",
                    body = "Choose a table and read a range. Non-zero cells are brighter; failed blocks are red.",
                )
            }
        }
    }

    if (selected != null) {
        val cell = selected!!
        AddScannedSignalDialog(
            cell = cell,
            objectType = objectType,
            cells = scan.cells,
            profile = profile,
            onDismiss = { selected = null },
            onAdd = { name, dataType, writable ->
                if (vm.addSignalToActiveProfile(objectType, cell.address, dataType, name, writable)) {
                    selected = null
                }
            },
        )
    }
}

@Composable
private fun AddScannedSignalDialog(
    cell: RegisterCell,
    objectType: ObjectType,
    cells: List<RegisterCell>,
    profile: DeviceProfile?,
    onDismiss: () -> Unit,
    onAdd: (String, DataType, Boolean) -> Unit,
) {
    val types =
        if (objectType.isBitTable) {
            listOf(DataType.BOOL, DataType.BOOL8, DataType.BOOL16)
        } else {
            listOf(DataType.UINT16, DataType.INT16, DataType.UINT32, DataType.INT32, DataType.FLOAT32, DataType.FLOAT64)
        }
    val suggested = remember(cell.address, objectType, cells) { suggestDataType(objectType, cell, cells) }
    var name by remember { mutableStateOf("${objectType.shortLabel} ${cell.address}") }
    var dataType by remember { mutableStateOf(suggested) }
    var writable by remember {
        mutableStateOf(objectType == ObjectType.HOLDING_REGISTER || objectType == ObjectType.COIL)
    }
    var typeMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val targetGroup =
        profile?.groups?.firstOrNull {
            it.objectType == objectType && cell.address >= it.start && cell.address <= it.endAddress
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (objectType.isBitTable) "Coil / input ${cell.address}" else "Register ${cell.address}")
        },
        text = {
            Column(
                Modifier
                    .height(440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cell.value == null) {
                    ErrorText(cell.error ?: "Read failed")
                } else {
                    KeyValueMini("Hex", "0x%04X".format(cell.value))
                    KeyValueMini("Decimal", cell.value.toString())
                    KeyValueMini("Binary", "%016d".format(cell.value.toString(2).toLong()))
                }
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Text(
                    if (profile != null) {
                        "Adds a named signal to the active profile “${profile.name}”. It appears as a card on Live."
                    } else {
                        "No active profile. Activate one in Profiles → Activate before adding a signal."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (targetGroup != null) {
                        "Group: ${targetGroup.name} (${targetGroup.start}..${targetGroup.endAddress})"
                    } else {
                        "Group: a new ${objectType.shortLabel} group will be created automatically."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Signal name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Data type: ${dataType.label}${if (dataType == suggested) " (suggested)" else ""}")
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        types.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    dataType = option
                                    typeMenu = false
                                },
                            )
                        }
                    }
                }
                if (objectType == ObjectType.HOLDING_REGISTER || objectType == ObjectType.COIL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Writable", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Add write controls to the detail sheet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = writable, onCheckedChange = { writable = it })
                    }
                }
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = "Enter a signal name"
                    } else {
                        onAdd(name, dataType, writable)
                    }
                },
                enabled = profile != null,
            ) {
                Text("Add signal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun suggestDataType(
    objectType: ObjectType,
    cell: RegisterCell,
    cells: List<RegisterCell>,
): DataType {
    if (objectType.isBitTable) return DataType.BOOL
    val value = cell.value ?: return DataType.UINT16
    val next = cells.firstOrNull { it.address == cell.address + 1 }?.value ?: return DataType.UINT16
    // A plausible float32 usually has at least one word above 0x1000. Small integer pairs
    // like 1/0 would otherwise decode as 1.4e-45 and make a bad default.
    if (value <= 0x1000 && next <= 0x1000) return DataType.UINT16
    val float = Float.fromBits((value shl 16) or next)
    return if (float.isFinite() && float != 0f && abs(float.toDouble()) in 1e-3..1e9) {
        DataType.FLOAT32
    } else {
        DataType.UINT16
    }
}

@Composable
private fun KeyValueMini(
    label: String,
    value: String,
) {
    Row {
        Text(
            label,
            modifier = Modifier.width(70.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MonoText(value, size = 12)
    }
}

@Composable
private fun HeatCell(
    cell: RegisterCell,
    objectType: ObjectType,
    onClick: () -> Unit,
) {
    val accent = objectAccent(objectType)
    val intensity =
        when {
            cell.value == null -> 0f
            cell.value == 0 -> 0.05f
            else -> (0.18 + 0.55 * min(1.0, log10(cell.value.toDouble() + 1.0) / 4.5)).toFloat()
        }
    val color =
        when {
            cell.value == null -> AccentRose.copy(alpha = 0.25f)
            else -> accent.copy(alpha = intensity)
        }
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${cell.address}",
                fontSize = 7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                cell.value?.let { "%04X".format(it) } ?: "ERR",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RawFrameTab(vm: AppViewModel) {
    val raw by vm.raw.collectAsStateWithLifecycle()
    var unit by rememberSaveable { mutableStateOf("1") }
    var timeout by rememberSaveable { mutableStateOf("1000") }
    var pdu by rememberSaveable { mutableStateOf("03 00 00 00 0A") }
    var quickFunction by rememberSaveable { mutableStateOf("3") }
    var address by rememberSaveable { mutableStateOf("0") }
    var quantity by rememberSaveable { mutableStateOf("10") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(accent = AccentCyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bolt, null, Modifier.size(18.dp), tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Raw PDU", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    StatusPill("CRC/LRC auto", AccentCyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(unit, { unit = it }, "Unit", Modifier.weight(1f))
                    NumberField(timeout, { timeout = it }, "Timeout ms", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Quick builder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("3" to "Read HR", "4" to "Read IR", "1" to "Read Coil", "2" to "Read DI", "6" to "Write Reg").forEach { (fc, label) ->
                        FilterChip(
                            selected = quickFunction == fc,
                            onClick = { quickFunction = fc },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(address, { address = it }, "Address", Modifier.weight(1f))
                    NumberField(quantity, { quantity = it }, if (quickFunction == "6") "Value" else "Quantity", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val fc = quickFunction.toIntOrNull() ?: 3
                        val addr = address.toIntOrNull() ?: 0
                        val qty = quantity.toIntOrNull() ?: 1
                        pdu =
                            when (fc) {
                                6 -> "06 %04X %04X".format(addr, qty.coerceIn(0, 0xFFFF))
                                else -> "%02X %04X %04X".format(fc, addr, qty)
                            }
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Build PDU")
                }
            }
        }
        item {
            SectionCard {
                Text("PDU hex", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = pdu,
                    onValueChange = { pdu = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 2,
                    label = { Text("Function + payload, without unit id / CRC") },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.sendRaw(unit.toIntOrNull() ?: 1, pdu, timeout.toIntOrNull() ?: 1000) },
                    enabled = !raw.sending,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Terminal, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (raw.sending) "Waiting…" else "Send frame")
                }
                raw.error?.let {
                    Spacer(Modifier.height(8.dp))
                    ErrorText(it)
                }
            }
        }
        if (raw.requestHex.isNotBlank() || raw.responseHex.isNotBlank() || raw.error != null) {
            item {
                SectionCard {
                    Text("Result", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (raw.requestHex.isNotBlank()) {
                        MonoLabel("Request")
                        Text(
                            raw.requestHex,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (raw.responseHex.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        MonoLabel("Response · ${raw.durationMs} ms · attempt ${raw.attempts}")
                        Text(
                            raw.responseHex,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (raw.decoded.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        MonoLabel("Decoded")
                        Text(raw.decoded, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        item {
            SectionCard {
                Text("Byte-level log", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every request, retry and exception is recorded in the Log tab with timing and hex frames.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonoLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}
