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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modpoll.rtu.data.Signal
import com.modpoll.rtu.data.SignalGroup
import com.modpoll.rtu.engine.GroupState
import com.modpoll.rtu.engine.PollSnapshot
import com.modpoll.rtu.engine.SignalReading
import com.modpoll.rtu.ui.AppViewModel
import com.modpoll.rtu.ui.LinkUiState
import com.modpoll.rtu.ui.components.EmptyState
import com.modpoll.rtu.ui.components.ErrorText
import com.modpoll.rtu.ui.components.Led
import com.modpoll.rtu.ui.components.MonoText
import com.modpoll.rtu.ui.components.SectionCard
import com.modpoll.rtu.ui.components.SignalDetailSheet
import com.modpoll.rtu.ui.components.StatCell
import com.modpoll.rtu.ui.components.StatusPill
import com.modpoll.rtu.ui.components.ValueTile
import com.modpoll.rtu.ui.components.formatTime
import com.modpoll.rtu.ui.components.objectAccent
import com.modpoll.rtu.ui.theme.AccentAmber
import com.modpoll.rtu.ui.theme.AccentLime
import com.modpoll.rtu.ui.theme.AccentRose
import kotlin.math.roundToInt

private enum class MonitorFilter(
    val label: String,
) {
    ALL("All"),
    FAVORITES("★"),
    WRITABLE("Writable"),
    CHANGED("Changed"),
    ERRORS("Errors"),
}

@Composable
fun MonitorScreen(
    vm: AppViewModel,
    onOpenLink: () -> Unit,
) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val poll by vm.poll.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val settings by vm.linkSettings.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MonitorFilter.ALL) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val selected =
        selectedKey?.let { key ->
            val groupId = key.substringBefore('/')
            val signalId = key.substringAfter('/')
            val group = profile?.groups?.firstOrNull { it.id == groupId }
            val signal = group?.signals?.firstOrNull { it.id == signalId }
            if (group != null && signal != null) {
                Triple(group, signal, poll.values[signal.id] ?: SignalReading(signal, group.id))
            } else {
                null
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(
                link = link,
                poll = poll,
                profileName = profile?.name ?: "No profile",
                unitId = profile?.unitId ?: 1,
                framer = settings.framer.label,
                onConnect = onOpenLink,
                onTogglePolling = vm::togglePolling,
                onReadOnce = vm::readOnce,
            )
        }
        if (profile == null) {
            item {
                EmptyState(
                    title = "No device profile",
                    body = "Create a profile or import a modpoll CSV to describe the registers of your device.",
                )
            }
            return@LazyColumn
        }
        val groups = profile?.groups.orEmpty()
        item {
            SearchAndFilters(
                query = query,
                onQuery = { query = it },
                filter = filter,
                onFilter = { filter = it },
                total = groups.sumOf { it.signals.size },
                shown =
                    groups.sumOf { group ->
                        group.signals.count { signal -> matches(group, signal, poll.values[signal.id], query, filter) }
                    },
            )
        }
        if (groups.isEmpty()) {
            item {
                EmptyState(
                    title = "No signal groups",
                    body = "Open Profiles and add a group, or import a modpoll CSV file.",
                )
            }
        }
        groups.forEach { group ->
            val visible = group.signals.filter { matches(group, it, poll.values[it.id], query, filter) }
            if (visible.isEmpty() && (query.isNotBlank() || filter != MonitorFilter.ALL)) {
                return@forEach
            }
            item(key = "group-${group.id}") {
                GroupCard(
                    group = group,
                    groupState = poll.groups[group.id],
                    readings = poll.values,
                    visibleSignals = if (query.isNotBlank() || filter != MonitorFilter.ALL) visible else group.signals,
                    collapsed = collapsed.contains(group.id),
                    connected = link.connected,
                    onToggleCollapse = {
                        collapsed =
                            if (collapsed.contains(group.id)) {
                                collapsed - group.id
                            } else {
                                collapsed + group.id
                            }
                    },
                    onSignalClick = { signal -> selectedKey = "${group.id}/${signal.id}" },
                    onResume = { vm.resetAutoDisabled(group.id) },
                )
            }
        }
    }

    if (selected != null && profile != null) {
        val (group, signal, reading) = selected
        SignalDetailSheet(
            profileName = profile!!.name,
            group = group,
            reading = reading,
            connected = link.connected,
            framer = settings.framer,
            onDismiss = { selectedKey = null },
            onFavorite = { vm.toggleFavorite(profile!!.id, group.id, signal.id) },
            onWrite = { text -> vm.writeSignal(group.id, signal.id, text) },
            onWriteBit = { bit, on -> vm.writeCoilBit(group.id, signal.id, bit, on) },
        )
    }
}

@Composable
private fun StatusCard(
    link: LinkUiState,
    poll: PollSnapshot,
    profileName: String,
    unitId: Int,
    framer: String,
    onConnect: () -> Unit,
    onTogglePolling: () -> Unit,
    onReadOnce: () -> Unit,
) {
    val statusColor =
        when {
            link.connecting -> AccentAmber
            link.connected && link.polling -> AccentLime
            link.connected -> AccentAmber
            else -> MaterialTheme.colorScheme.error
        }
    val statusText =
        when {
            link.connecting -> "Connecting"
            link.connected && link.polling -> "Polling"
            link.connected -> "Paused"
            else -> "Offline"
        }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Led(statusColor, 10)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (link.connected) link.label else "No active link",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$profileName · unit $unitId · $framer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(statusText, statusColor, filled = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCell("Requests", poll.totalRequests.toString())
            StatCell(
                "Success",
                if (poll.totalRequests == 0L) "—" else "${(poll.successRate * 100).roundToInt()}%",
                valueColor = if (poll.successRate >= 0.99 || poll.totalRequests == 0L) MaterialTheme.colorScheme.onSurface else AccentRose,
            )
            StatCell("Cycle", "${poll.cycleMs} ms")
            StatCell("Updated", formatTime(poll.lastCycleAt))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (link.connected) {
                Button(onClick = onTogglePolling, shape = RoundedCornerShape(12.dp)) {
                    Icon(
                        if (link.polling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (link.polling) "Pause" else "Resume")
                }
                AssistChip(
                    onClick = onReadOnce,
                    label = { Text("Read once") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp)) },
                )
            } else {
                Button(onClick = onConnect, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Usb, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connect device")
                }
            }
        }
        link.lastError?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                ErrorText(it, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchAndFilters(
    query: String,
    onQuery: (String) -> Unit,
    filter: MonitorFilter,
    onFilter: (MonitorFilter) -> Unit,
    total: Int,
    shown: Int,
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search signal, address or description") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonitorFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.label) },
                )
            }
            Text(
                "$shown / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: SignalGroup,
    groupState: GroupState?,
    readings: Map<String, SignalReading>,
    visibleSignals: List<Signal>,
    collapsed: Boolean,
    connected: Boolean,
    onToggleCollapse: () -> Unit,
    onSignalClick: (Signal) -> Unit,
    onResume: () -> Unit,
) {
    val accent = objectAccent(group.objectType)
    val autoDisabled = groupState?.autoDisabled == true
    val error = groupState?.error
    val ok = error == null && groupState != null && groupState.requests > 0
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .background(accent, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusPill(group.objectType.shortLabel, accent)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${group.spanLabel} · ${group.signals.size} signals · ${group.intervalMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Led(
                        if (autoDisabled) {
                            AccentAmber
                        } else if (error !=
                            null
                        ) {
                            AccentRose
                        } else if (ok) {
                            AccentLime
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    Spacer(Modifier.height(3.dp))
                    MonoText(
                        text =
                            if (groupState?.lastDurationMs != null &&
                                groupState.lastDurationMs > 0
                            ) {
                                "${groupState.lastDurationMs} ms"
                            } else {
                                "—"
                            },
                        size = 10,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = if (collapsed) "Expand" else "Collapse",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (error != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ErrorText(error, Modifier.weight(1f))
                    if (autoDisabled) {
                        TextButton(onClick = onResume) { Text("Resume") }
                    }
                }
            }
            if (!collapsed) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    if (visibleSignals.isEmpty()) {
                        Text(
                            "No signals in this group",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                    visibleSignals.chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { signal ->
                                val reading = readings[signal.id] ?: SignalReading(signal, group.id)
                                ValueTile(
                                    reading = reading,
                                    group = group,
                                    connected = connected,
                                    onClick = { onSignalClick(signal) },
                                    modifier = Modifier.weight(1f).height(138.dp),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun matches(
    group: SignalGroup,
    signal: Signal,
    reading: SignalReading?,
    query: String,
    filter: MonitorFilter,
): Boolean {
    val q = query.trim()
    if (q.isNotEmpty()) {
        val haystack = "${signal.name} ${signal.address} ${signal.description} ${signal.unit} ${signal.dataType.label}"
        if (!haystack.contains(q, ignoreCase = true)) return false
    }
    return when (filter) {
        MonitorFilter.ALL -> true
        MonitorFilter.FAVORITES -> signal.favorite
        MonitorFilter.WRITABLE -> signal.writable
        MonitorFilter.CHANGED -> reading != null && reading.updatedAt > 0 && reading.changedAt == reading.updatedAt
        MonitorFilter.ERRORS -> reading?.error != null
    }
}
