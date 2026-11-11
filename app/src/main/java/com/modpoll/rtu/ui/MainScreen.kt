package com.modpoll.rtu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modpoll.rtu.data.Framer
import com.modpoll.rtu.ui.components.Led
import com.modpoll.rtu.ui.components.MonoText
import com.modpoll.rtu.ui.screens.ConsoleScreen
import com.modpoll.rtu.ui.screens.GraphsScreen
import com.modpoll.rtu.ui.screens.LinkScreen
import com.modpoll.rtu.ui.screens.MonitorScreen
import com.modpoll.rtu.ui.screens.ProfilesScreen
import com.modpoll.rtu.ui.screens.ToolsScreen
import com.modpoll.rtu.ui.theme.AccentCyan
import com.modpoll.rtu.ui.theme.AccentLime
import com.modpoll.rtu.ui.theme.AccentRose

private enum class HomeTab(
    val label: String,
    val icon: ImageVector,
) {
    LIVE("Live", Icons.Outlined.Speed),
    GRAPHS("Graphs", Icons.Outlined.ShowChart),
    TOOLS("Tools", Icons.Outlined.Construction),
    LOG("Log", Icons.Outlined.Terminal),
    LINK("Link", Icons.Outlined.Usb),
}

@Composable
fun ModPollRoot(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.LIVE) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }

    val link by vm.link.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val settings by vm.linkSettings.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()

    val snapshotLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(vm::exportSnapshot)
        }

    if (showProfiles) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            ProfilesScreen(vm = vm, onBack = { showProfiles = false })
        }
    } else {
        Scaffold(
            topBar = {
                AppBar(
                    profileName = profile?.name ?: "No profile",
                    unitId = profile?.unitId ?: 1,
                    connected = link.connected,
                    polling = link.polling,
                    connecting = link.connecting,
                    framer = settings.framer,
                    profileMenu = profileMenu,
                    onProfileMenu = { profileMenu = it },
                    profiles = profiles,
                    activeProfileId = profile?.id.orEmpty(),
                    onSelectProfile = {
                        profileMenu = false
                        vm.selectProfile(it)
                    },
                    onManageProfiles = {
                        profileMenu = false
                        showProfiles = true
                    },
                    overflowMenu = overflowMenu,
                    onOverflowMenu = { overflowMenu = it },
                    onExportSnapshot = {
                        overflowMenu = false
                        snapshotLauncher.launch("modbus-snapshot.json")
                    },
                    onAbout = {
                        overflowMenu = false
                        showAbout = true
                    },
                    onOpenProfiles = {
                        overflowMenu = false
                        showProfiles = true
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    HomeTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    HomeTab.LIVE -> {
                        MonitorScreen(
                            vm = vm,
                            onOpenLink = { tab = HomeTab.LINK },
                            onOpenProfiles = { showProfiles = true },
                        )
                    }
                    HomeTab.GRAPHS -> GraphsScreen(vm)
                    HomeTab.TOOLS -> ToolsScreen(vm)
                    HomeTab.LOG -> ConsoleScreen(vm)
                    HomeTab.LINK -> LinkScreen(vm = vm, onOpenMonitor = { tab = HomeTab.LIVE })
                }
            }
        }
    }

    report?.let { current ->
        AlertDialog(
            onDismissRequest = vm::clearReport,
            title = { Text(current.title) },
            text = {
                Column(
                    Modifier.height(260.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (current.warnings.isEmpty()) {
                        Text("No warnings.")
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(current.warnings.size) { index ->
                                Text(
                                    "• ${current.warnings[index]}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::clearReport) { Text("OK") }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("ModPoll RTU 1.2") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Modbus RTU/ASCII master for USB serial adapters. Modelled on the modpoll configuration format, without MQTT.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MonoText("RTU: binary + CRC16", size = 10)
                    MonoText("ASCII: hex + LRC + CRLF", size = 10)
                    MonoText("FC01 / 02 / 03 / 04 reads", size = 10)
                    MonoText("FC05 / 06 / 15 / 16 writes", size = 10)
                    MonoText("Types: bool, int/uint 16/32/64, float16/32/64, stringN", size = 10)
                    Text(
                        "USB drivers: usb-serial-for-android (MIT).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun AppBar(
    profileName: String,
    unitId: Int,
    connected: Boolean,
    polling: Boolean,
    connecting: Boolean,
    framer: Framer,
    profileMenu: Boolean,
    onProfileMenu: (Boolean) -> Unit,
    profiles: List<com.modpoll.rtu.data.DeviceProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onManageProfiles: () -> Unit,
    overflowMenu: Boolean,
    onOverflowMenu: (Boolean) -> Unit,
    onExportSnapshot: () -> Unit,
    onAbout: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(
                            Brush.linearGradient(listOf(AccentCyan, AccentLime)),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Usb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "MODPOLL RTU",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Led(
                            when {
                                connecting -> com.modpoll.rtu.ui.theme.AccentAmber
                                connected && polling -> AccentLime
                                connected -> com.modpoll.rtu.ui.theme.AccentAmber
                                else -> AccentRose
                            },
                            6,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "unit $unitId · ${framer.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { onProfileMenu(true) },
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                profileName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(120.dp),
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
                        }
                    }
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { onProfileMenu(false) }) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            profile.name,
                                            modifier = Modifier.width(170.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (profile.id == activeProfileId) {
                                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = AccentCyan)
                                        }
                                    }
                                },
                                onClick = { onSelectProfile(profile.id) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Manage profiles…") },
                            onClick = onManageProfiles,
                            leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { onOverflowMenu(true) }) {
                        Icon(Icons.Filled.MoreVert, "More")
                    }
                    DropdownMenu(expanded = overflowMenu, onDismissRequest = { onOverflowMenu(false) }) {
                        DropdownMenuItem(
                            text = { Text("Profiles & import") },
                            onClick = onOpenProfiles,
                            leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export snapshot") },
                            onClick = onExportSnapshot,
                            leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) },
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = onAbout,
                            leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}
