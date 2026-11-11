package io.github.hansfbaier.modpoll.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hansfbaier.modpoll.data.Framer
import io.github.hansfbaier.modpoll.data.Parity
import io.github.hansfbaier.modpoll.ui.AppViewModel
import io.github.hansfbaier.modpoll.ui.TransportKind
import io.github.hansfbaier.modpoll.ui.components.EmptyState
import io.github.hansfbaier.modpoll.ui.components.ErrorText
import io.github.hansfbaier.modpoll.ui.components.KeyValueRow
import io.github.hansfbaier.modpoll.ui.components.Led
import io.github.hansfbaier.modpoll.ui.components.MonoText
import io.github.hansfbaier.modpoll.ui.components.SectionCard
import io.github.hansfbaier.modpoll.ui.components.StatusPill
import io.github.hansfbaier.modpoll.ui.theme.AccentAmber
import io.github.hansfbaier.modpoll.ui.theme.AccentCyan
import io.github.hansfbaier.modpoll.ui.theme.AccentLime
import io.github.hansfbaier.modpoll.ui.theme.AccentRose

@Composable
fun LinkScreen(
    vm: AppViewModel,
    onOpenMonitor: () -> Unit,
) {
    val link by vm.link.collectAsStateWithLifecycle()
    val settings by vm.linkSettings.collectAsStateWithLifecycle()
    val devices by vm.usbDevices.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeProfile by vm.activeProfile.collectAsStateWithLifecycle()

    var selectedKey by rememberSaveable { mutableStateOf(link.selectedDeviceKey) }
    LaunchedEffect(devices, link.selectedDeviceKey) {
        if (selectedKey.isBlank()) {
            selectedKey = devices.firstOrNull { it.supported }?.key ?: devices.firstOrNull()?.key ?: ""
        }
    }
    val selected = devices.firstOrNull { it.key == selectedKey }
    val serial = settings.serial
    var baudExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    val baudOptions = listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
    var unitText by remember(activeProfile?.unitId) { mutableStateOf(activeProfile?.unitId?.toString() ?: "1") }
    var timeoutText by remember(settings.timeoutMs) { mutableStateOf(settings.timeoutMs.toString()) }
    var retriesText by remember(settings.retries) { mutableStateOf(settings.retries.toString()) }
    var delayText by remember(settings.interFrameDelayMs) { mutableStateOf(settings.interFrameDelayMs.toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(accent = if (link.connected) AccentLime else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Led(
                    when {
                        link.connecting -> AccentAmber
                        link.connected -> AccentLime
                        else -> MaterialTheme.colorScheme.outline
                    },
                    10,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            link.connecting -> "Opening link…"
                            link.connected -> "Connected"
                            else -> "Link offline"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (link.connected) link.label else "USB serial or built-in simulator",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (link.connected) {
                    OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
                }
            }
            if (link.connected) {
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Framing", settings.framer.label)
                KeyValueRow("Serial", "${serial.baudRate} ${serial.dataBits}${serial.parity.name.first()}${serial.stopBits}")
                KeyValueRow("Unit id", (activeProfile?.unitId ?: 0).toString())
                KeyValueRow("Timeout", "${settings.timeoutMs} ms / ${settings.retries} retries")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenMonitor, shape = RoundedCornerShape(12.dp)) {
                    Text("Open live monitor")
                }
            }
            link.lastError?.let {
                Spacer(Modifier.height(8.dp))
                ErrorText(it)
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Devices, null, modifier = Modifier.size(18.dp), tint = AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("USB serial devices", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refreshDevices) {
                    Icon(Icons.Outlined.Refresh, "Refresh devices")
                }
            }
            if (devices.isEmpty()) {
                EmptyState(
                    title = "No USB device",
                    body = "Connect a USB-RS485 adapter with an OTG cable. Supported chips: CH340, CH341, CP210x, FTDI, PL2303, CDC-ACM.",
                    icon = { Icon(Icons.Outlined.Usb, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            } else {
                devices.forEach { device ->
                    val isSelected = device.key == selectedKey
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedKey = device.key },
                        shape = RoundedCornerShape(12.dp),
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        border =
                            BorderStroke(
                                1.dp,
                                if (isSelected) AccentCyan.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Led(if (device.supported) AccentLime else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    device.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MonoText(
                                    "${device.vidsPid} · ${device.driverName ?: "unsupported"}",
                                    size = 10,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.Check, "Selected", tint = AccentCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { selected?.let(vm::connectUsb) },
                    enabled = selected != null && selected.supported && !link.connecting && activeProfile != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Usb, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (link.connected && link.kind == TransportKind.USB) "Reconnect" else "Connect selected device")
                }
                if (selected != null && !selected.supported) {
                    Spacer(Modifier.height(6.dp))
                    ErrorText("No driver for this device. Try a CH340/CP210x/FTDI adapter.")
                }
                if (activeProfile == null) {
                    Spacer(Modifier.height(6.dp))
                    ErrorText("No active profile. Activate one in Profiles before connecting.")
                }
            }
        }

        SectionCard {
            Text("Serial parameters", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Baud rate", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(92.dp))
                Box {
                    OutlinedButton(onClick = { baudExpanded = true }) {
                        Text("${serial.baudRate} baud")
                    }
                    DropdownMenu(expanded = baudExpanded, onDismissRequest = { baudExpanded = false }) {
                        baudOptions.forEach { value ->
                            DropdownMenuItem(
                                text = { Text("$value") },
                                onClick = {
                                    baudExpanded = false
                                    vm.updateSerialSettings(serial.copy(baudRate = value))
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ParameterChips(
                label = "Data bits",
                values = listOf("5", "6", "7", "8"),
                selected = serial.dataBits.toString(),
                onSelect = { vm.updateSerialSettings(serial.copy(dataBits = it.toInt())) },
            )
            ParameterChips(
                label = "Parity",
                values = listOf("None", "Even", "Odd"),
                selected = serial.parity.label,
                onSelect = {
                    vm.updateSerialSettings(
                        serial.copy(
                            parity =
                                when (it) {
                                    "Even" -> Parity.EVEN
                                    "Odd" -> Parity.ODD
                                    else -> Parity.NONE
                                },
                        ),
                    )
                },
            )
            ParameterChips(
                label = "Stop bits",
                values = listOf("1", "2"),
                selected = serial.stopBits.toString(),
                onSelect = { vm.updateSerialSettings(serial.copy(stopBits = it.toInt())) },
            )
            ParameterChips(
                label = "Framing",
                values = Framer.entries.map { it.label },
                selected = settings.framer.label,
                onSelect = { label ->
                    vm.updateModbusSettings(framer = Framer.entries.firstOrNull { it.label == label } ?: Framer.RTU)
                },
            )
            if (link.connected) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Serial and framing changes apply on the next connection.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber,
                )
            }
        }

        SectionCard {
            Text("Modbus device", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(92.dp))
                Box {
                    OutlinedButton(onClick = { profileExpanded = true }) {
                        Text(activeProfile?.name ?: "Select profile", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text("${profile.name}  ·  unit ${profile.unitId}") },
                                onClick = {
                                    profileExpanded = false
                                    vm.selectProfile(profile.id)
                                },
                            )
                        }
                    }
                }
            }
            if (activeProfile == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No active profile. Open Profiles → Activate to load a register map.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = unitText,
                    onValueChange = {
                        unitText = it.filter { ch -> ch.isDigit() }.take(3)
                        unitText.toIntOrNull()?.let(vm::setProfileUnitId)
                    },
                    label = { Text("Unit id") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = {
                        timeoutText = it.filter { ch -> ch.isDigit() }.take(5)
                        timeoutText.toIntOrNull()?.let { value -> vm.updateModbusSettings(timeoutMs = value) }
                    },
                    label = { Text("Timeout ms") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = retriesText,
                    onValueChange = {
                        retriesText = it.filter { ch -> ch.isDigit() }.take(1)
                        retriesText.toIntOrNull()?.let { value -> vm.updateModbusSettings(retries = value) }
                    },
                    label = { Text("Retries") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = delayText,
                    onValueChange = {
                        delayText = it.filter { ch -> ch.isDigit() }.take(3)
                        delayText.toIntOrNull()?.let { value -> vm.updateModbusSettings(interFrameDelayMs = value) }
                    },
                    label = { Text("Gap ms") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                label = "Auto reconnect",
                hint = "Reopen the link after a serial failure",
                checked = settings.autoReconnect,
                onChecked = { vm.updateModbusSettings(autoReconnect = it) },
            )
            ToggleRow(
                label = "Auto-disable after 3 failures",
                hint = "Mirrors modpoll --autoremove",
                checked = settings.autoDisableAfterFailures,
                onChecked = { vm.updateModbusSettings(autoDisableAfterFailures = it) },
            )
        }

        SectionCard(accent = AccentCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Science, null, modifier = Modifier.size(18.dp), tint = AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("Built-in simulator", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Three simulated slaves answer on units 1–3 with live float, integer, string, coil and discrete data. Use the Demo PLC profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::connectDemo, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start simulator")
                }
                TextButton(onClick = { vm.selectProfile("builtin.demo-plc") }) {
                    Text("Use demo profile")
                }
            }
        }

        SectionCard {
            Text("Quick start", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "1. Select a USB adapter or start the simulator.\n" +
                    "2. Pick a profile or import a modpoll CSV.\n" +
                    "3. Set unit id, baud rate and framing.\n" +
                    "4. Connect, then use Live, Trends, Tools and Log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            StatusPill("RTU / ASCII", AccentCyan)
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun ParameterChips(
    label: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(92.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(value) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
