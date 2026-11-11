package io.github.hansfbaier.modpoll.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import io.github.hansfbaier.modpoll.data.DataType
import io.github.hansfbaier.modpoll.data.DeviceProfile
import io.github.hansfbaier.modpoll.data.Endian
import io.github.hansfbaier.modpoll.data.ObjectType
import io.github.hansfbaier.modpoll.data.Signal
import io.github.hansfbaier.modpoll.data.SignalGroup
import io.github.hansfbaier.modpoll.ui.AppViewModel
import io.github.hansfbaier.modpoll.ui.components.ErrorText
import io.github.hansfbaier.modpoll.ui.components.Led
import io.github.hansfbaier.modpoll.ui.components.MonoText
import io.github.hansfbaier.modpoll.ui.components.SectionCard
import io.github.hansfbaier.modpoll.ui.components.StatusPill
import io.github.hansfbaier.modpoll.ui.components.objectAccent
import io.github.hansfbaier.modpoll.ui.theme.AccentCyan
import io.github.hansfbaier.modpoll.ui.theme.AccentLime
import io.github.hansfbaier.modpoll.ui.theme.AccentRose
import java.util.UUID

@Composable
fun ProfilesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeProfileId.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var csvExportProfile by remember { mutableStateOf<DeviceProfile?>(null) }

    BackHandler { onBack() }

    val importCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::importCsvFromUri)
        }
    val importJson =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::importJsonFromUri)
        }
    val exportJson =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(vm::exportProfilesJson)
        }
    val exportCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { target -> csvExportProfile?.let { profile -> vm.exportProfileCsv(target, profile.id) } }
        }

    val editing = profiles.firstOrNull { it.id == editingId }
    if (editing != null) {
        ProfileEditorScreen(
            vm = vm,
            profileId = editing.id,
            onBack = { editingId = null },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Device profiles", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${profiles.size} profiles · " +
                        if (activeId.isBlank()) {
                            "none active"
                        } else {
                            "active unit ${profiles.firstOrNull { it.id == activeId }?.unitId ?: "—"}"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, "New profile")
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (activeId.isBlank()) {
                SectionCard(accent = AccentRose) {
                    Text("No active profile", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Nothing is polled until you activate a profile. Activating one loads only its register map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionCard {
                Text("Import / export", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "modpoll CSV is imported as device profiles. JSON keeps groups, scale, units and favorites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { importCsv.launch(arrayOf("text/*", "text/csv", "application/csv")) }) {
                        Icon(Icons.Outlined.FileOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import CSV")
                    }
                    OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Outlined.FileOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import JSON")
                    }
                    OutlinedButton(onClick = { exportJson.launch("modpoll-profiles.json") }) {
                        Icon(Icons.Outlined.UploadFile, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export all")
                    }
                }
            }
            profiles.forEach { profile ->
                val isActive = profile.id == activeId
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border =
                        BorderStroke(
                            1.dp,
                            if (isActive) AccentCyan.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Led(if (isActive) AccentCyan else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (profile.builtIn) {
                                        Spacer(Modifier.width(6.dp))
                                        StatusPill("BUILT-IN", AccentLime)
                                    }
                                    if (isActive) {
                                        Spacer(Modifier.width(6.dp))
                                        StatusPill("ACTIVE", AccentCyan, filled = true)
                                    }
                                }
                                MonoText(
                                    "unit ${profile.unitId} · ${profile.groupCount} groups · ${profile.signalCount} signals",
                                    size = 10,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (profile.description.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isActive) {
                                TextButton(onClick = vm::deactivateProfile) {
                                    Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Deactivate")
                                }
                            } else {
                                TextButton(onClick = { vm.selectProfile(profile.id) }) {
                                    Icon(Icons.Filled.Check, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Activate")
                                }
                            }
                            TextButton(onClick = { editingId = profile.id }) {
                                Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit")
                            }
                            TextButton(onClick = { vm.duplicateProfile(profile.id) }) { Text("Duplicate") }
                            TextButton(onClick = {
                                csvExportProfile = profile
                                exportCsv.launch("${profile.name.replace(' ', '_')}.csv")
                            }) {
                                Icon(Icons.Outlined.Download, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("CSV")
                            }
                            if (!profile.builtIn) {
                                TextButton(onClick = { deleteProfile = profile }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        null,
                                        Modifier.size(15.dp),
                                        tint = AccentRose,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", color = AccentRose)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }

    if (showCreate) {
        CreateProfileDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                val created = vm.createProfile(name)
                showCreate = false
                editingId = created.id
            },
        )
    }
    deleteProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text("Delete profile?") },
            text = { Text("${target.name} and its ${target.signalCount} signals will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProfile(target.id)
                    deleteProfile = null
                }) {
                    Text("Delete", color = AccentRose)
                }
            },
            dismissButton = { TextButton(onClick = { deleteProfile = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("New device") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New device profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "A profile is a set of read groups and signal mappings for one slave id.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim().ifBlank { "New device" }) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileEditorScreen(
    vm: AppViewModel,
    profileId: String,
    onBack: () -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == profileId }
    var groupDialog by remember { mutableStateOf<SignalGroup?>(null) }
    var addGroupDialog by remember { mutableStateOf(false) }
    var signalDialogGroup by remember { mutableStateOf<SignalGroup?>(null) }
    var editSignal by remember { mutableStateOf<Signal?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<SignalGroup?>(null) }

    BackHandler { onBack() }

    if (profile == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Edit profile", style = MaterialTheme.typography.titleLarge)
                Text(profile.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { addGroupDialog = true }) {
                Icon(Icons.Filled.Add, "Add group")
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { vm.upsertProfile(profile.copy(name = it)) },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = profile.unitId.toString(),
                        onValueChange = { text ->
                            text.toIntOrNull()?.let { vm.upsertProfile(profile.copy(unitId = it.coerceIn(1, 247))) }
                        },
                        label = { Text("Unit id") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(
                        value = profile.description,
                        onValueChange = { vm.upsertProfile(profile.copy(description = it)) },
                        label = { Text("Description") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Groups", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { addGroupDialog = true }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Text("Add group")
                }
            }

            if (profile.groups.isEmpty()) {
                SectionCard {
                    Text(
                        "No groups yet. A group is one contiguous read block (FC01/02/03/04) plus its signals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            profile.groups.forEach { group ->
                val accent = objectAccent(group.objectType)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        group.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill(group.objectType.shortLabel, accent)
                                }
                                MonoText(
                                    "${group.start}..${group.endAddress} · ${group.count} ${if (group.objectType.isBitTable) "bits" else "regs"} · ${group.intervalMs} ms",
                                    size = 10,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { groupDialog = group }) {
                                Icon(Icons.Outlined.Edit, "Edit group")
                            }
                            IconButton(onClick = { deleteGroupTarget = group }) {
                                Icon(Icons.Outlined.Delete, "Delete group", tint = AccentRose)
                            }
                        }
                        if (group.signals.isEmpty()) {
                            Text(
                                "No signals. Add one to map an address.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            group.signals.forEach { signal ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (signal.favorite) {
                                        Icon(Icons.Filled.Star, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(signal.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        MonoText(
                                            "${signal.address} · ${signal.dataType.label}" +
                                                (if (signal.unit.isNotBlank()) " · ${signal.unit}" else "") +
                                                (if (signal.scale != 1.0) " · ×${signal.scale}" else ""),
                                            size = 9,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (signal.writable) StatusPill("RW", accent)
                                    IconButton(
                                        onClick = {
                                            signalDialogGroup = group
                                            editSignal = signal
                                        },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(Icons.Outlined.Edit, "Edit signal", Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = { vm.deleteSignal(profile.id, group.id, signal.id) },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(Icons.Outlined.Delete, "Delete signal", Modifier.size(15.dp), tint = AccentRose)
                                    }
                                }
                            }
                        }
                        TextButton(onClick = {
                            signalDialogGroup = group
                            editSignal = null
                        }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(15.dp))
                            Text("Add signal")
                        }
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }

    if (addGroupDialog) {
        GroupEditorDialog(
            group = null,
            nextIndex = profile.groups.size + 1,
            onDismiss = { addGroupDialog = false },
            onSave = { group ->
                vm.upsertGroup(profile.id, group)
                addGroupDialog = false
            },
        )
    }
    groupDialog?.let { group ->
        GroupEditorDialog(
            group = group,
            nextIndex = profile.groups.size + 1,
            onDismiss = { groupDialog = null },
            onSave = { updated ->
                vm.upsertGroup(profile.id, updated)
                groupDialog = null
            },
        )
    }
    signalDialogGroup?.let { group ->
        SignalEditorDialog(
            group = group,
            signal = editSignal,
            onDismiss = {
                signalDialogGroup = null
                editSignal = null
            },
            onSave = { signal ->
                vm.upsertSignal(profile.id, group.id, signal)
                signalDialogGroup = null
                editSignal = null
            },
        )
    }
    deleteGroupTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text("Delete group?") },
            text = { Text("${group.name} and its ${group.signals.size} signals will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGroup(profile.id, group.id)
                    deleteGroupTarget = null
                }) {
                    Text("Delete", color = AccentRose)
                }
            },
            dismissButton = { TextButton(onClick = { deleteGroupTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GroupEditorDialog(
    group: SignalGroup?,
    nextIndex: Int,
    onDismiss: () -> Unit,
    onSave: (SignalGroup) -> Unit,
) {
    val isNew = group == null
    var name by remember { mutableStateOf(group?.name ?: "Group $nextIndex") }
    var objectType by remember { mutableStateOf(group?.objectType ?: ObjectType.HOLDING_REGISTER) }
    var start by remember { mutableStateOf((group?.start ?: 0).toString()) }
    var count by remember { mutableStateOf((group?.count ?: 10).toString()) }
    var interval by remember { mutableStateOf((group?.intervalMs ?: 1000).toString()) }
    var enabled by remember { mutableStateOf(group?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New group" else "Edit group") },
        text = {
            Column(
                Modifier
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { ErrorText(it) }
                Text(
                    "Object type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ObjectType.entries.forEach { type ->
                        FilterChip(
                            selected = objectType == type,
                            onClick = {
                                if (group == null || group.signals.isEmpty()) objectType = type
                            },
                            enabled = group == null || group.signals.isEmpty(),
                            label = { Text(type.shortLabel) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it.filter(Char::isDigit).take(5) },
                        label = { Text("Start") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter(Char::isDigit).take(5) },
                        label = { Text("Count") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter(Char::isDigit).take(7) },
                    label = { Text("Interval ms") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val startValue = start.toIntOrNull()
                val countValue = count.toIntOrNull()
                if (startValue == null || countValue == null || countValue <= 0) {
                    error = "Enter a valid start and count"
                } else if (startValue + countValue > 65536) {
                    error = "Address range exceeds 65535"
                } else {
                    onSave(
                        (group ?: SignalGroup(id = UUID.randomUUID().toString())).copy(
                            name = name.ifBlank { "Group" },
                            objectType = objectType,
                            start = startValue,
                            count = countValue,
                            intervalMs = (interval.toLongOrNull() ?: 1000).coerceIn(50, 3_600_000),
                            enabled = enabled,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SignalEditorDialog(
    group: SignalGroup,
    signal: Signal?,
    onDismiss: () -> Unit,
    onSave: (Signal) -> Unit,
) {
    val isNew = signal == null
    val choices =
        if (group.objectType.isBitTable) {
            listOf(DataType.BOOL, DataType.BOOL8, DataType.BOOL16)
        } else {
            DataType.entries.filterNot { it == DataType.BOOL8 || it == DataType.BOOL16 }
        }
    var name by remember {
        mutableStateOf(signal?.name ?: "${group.objectType.shortLabel} ${signal?.address ?: group.start}")
    }
    var dataType by remember { mutableStateOf(signal?.dataType ?: if (group.objectType.isBitTable) DataType.BOOL8 else DataType.UINT16) }
    var address by remember { mutableStateOf((signal?.address ?: group.start).toString()) }
    var bit by remember { mutableStateOf(signal?.bit?.toString() ?: "0") }
    var stringBytes by remember { mutableStateOf((signal?.stringBytes ?: 8).toString()) }
    var endian by remember { mutableStateOf(signal?.endian ?: Endian.BE_BE) }
    var scale by remember { mutableStateOf(signal?.scale?.toString()?.removeSuffix(".0") ?: "1.0") }
    var offset by remember { mutableStateOf(signal?.offset?.toString()?.removeSuffix(".0") ?: "0.0") }
    var unit by remember { mutableStateOf(signal?.unit.orEmpty()) }
    var decimals by remember { mutableStateOf(signal?.decimals?.toString().orEmpty()) }
    var writable by remember { mutableStateOf(signal?.writable ?: false) }
    var favorite by remember { mutableStateOf(signal?.favorite ?: false) }
    var description by remember { mutableStateOf(signal?.description.orEmpty()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var endianExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(error) {
        if (error != null) scroll.animateScrollTo(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add signal" else "Edit signal") },
        text = {
            Column(
                Modifier
                    .height(440.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonoText("Group: ${group.name} · ${group.objectType.label}", size = 10, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { ErrorText(it) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Signal name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Type: ${dataType.label}")
                    }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        choices.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    dataType = type
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it.filter(Char::isDigit).take(5) },
                        label = { Text("Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    if (!group.objectType.isBitTable && dataType == DataType.BOOL) {
                        OutlinedTextField(
                            value = bit,
                            onValueChange = { bit = it.filter(Char::isDigit).take(2) },
                            label = { Text("Bit") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (dataType == DataType.STRING) {
                        OutlinedTextField(
                            value = stringBytes,
                            onValueChange = { stringBytes = it.filter(Char::isDigit).take(4) },
                            label = { Text("Bytes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!group.objectType.isBitTable) {
                    Box {
                        OutlinedButton(onClick = { endianExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Endian: ${endian.name}")
                        }
                        DropdownMenu(expanded = endianExpanded, onDismissRequest = { endianExpanded = false }) {
                            Endian.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    onClick = {
                                        endian = option
                                        endianExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = scale,
                        onValueChange = { scale = it },
                        label = { Text("Scale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = offset,
                        onValueChange = { offset = it },
                        label = { Text("Offset") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = decimals,
                        onValueChange = { decimals = it.filter(Char::isDigit).take(1) },
                        label = { Text("Decimals") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Writable", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = writable, onCheckedChange = { writable = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorite", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val addressValue = address.toIntOrNull()
                val scaleValue = scale.toDoubleOrNull()
                if (name.isBlank()) {
                    error = "Enter a signal name"
                } else if (addressValue == null) {
                    error = "Enter a valid address"
                } else if (scaleValue == null) {
                    error = "Enter a valid scale"
                } else {
                    val draft =
                        (signal ?: Signal(id = UUID.randomUUID().toString())).copy(
                            name = name.trim(),
                            address = addressValue,
                            dataType = dataType,
                            endian = endian,
                            bit =
                                if (!group.objectType.isBitTable &&
                                    dataType == DataType.BOOL
                                ) {
                                    bit.toIntOrNull()?.coerceIn(0, 15) ?: 0
                                } else {
                                    null
                                },
                            stringBytes = if (dataType == DataType.STRING) stringBytes.toIntOrNull() ?: 1 else 0,
                            scale = scaleValue,
                            offset = offset.toDoubleOrNull() ?: 0.0,
                            unit = unit.trim(),
                            writable = writable,
                            decimals = decimals.toIntOrNull(),
                            description = description.trim(),
                            favorite = favorite,
                        )
                    if (!group.contains(draft)) {
                        error = "Address+span is outside group range ${group.start}..${group.endAddress}. Increase the group size."
                    } else {
                        onSave(draft)
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
