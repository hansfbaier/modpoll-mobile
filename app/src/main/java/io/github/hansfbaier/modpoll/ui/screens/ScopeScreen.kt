package io.github.hansfbaier.modpoll.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hansfbaier.modpoll.data.ObjectType
import io.github.hansfbaier.modpoll.data.Signal
import io.github.hansfbaier.modpoll.data.SignalGroup
import io.github.hansfbaier.modpoll.ui.AppViewModel
import io.github.hansfbaier.modpoll.ui.components.EmptyState
import io.github.hansfbaier.modpoll.ui.components.ErrorText
import io.github.hansfbaier.modpoll.ui.components.Led
import io.github.hansfbaier.modpoll.ui.components.RealtimeChart
import io.github.hansfbaier.modpoll.ui.components.SectionCard
import io.github.hansfbaier.modpoll.ui.components.StatCell
import io.github.hansfbaier.modpoll.ui.components.StatusPill
import io.github.hansfbaier.modpoll.ui.components.scopeColors
import io.github.hansfbaier.modpoll.ui.theme.AccentAmber
import io.github.hansfbaier.modpoll.ui.theme.AccentCyan
import io.github.hansfbaier.modpoll.ui.theme.AccentLime
import io.github.hansfbaier.modpoll.ui.theme.AccentRose
import java.util.Locale

private data class HoldingChoice(
    val group: SignalGroup,
    val signal: Signal,
)

private val scopeIntervals = listOf(10L, 20L, 50L, 100L, 200L, 500L, 1_000L)
private val scopeWindows = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

@Composable
fun ScopeScreen(vm: AppViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()

    val candidates =
        remember(profile) {
            profile
                ?.groups
                .orEmpty()
                .filter { it.objectType == ObjectType.HOLDING_REGISTER }
                .flatMap { group ->
                    group.signals
                        .filter { it.dataType.isInteger || it.dataType.isFloat }
                        .map { HoldingChoice(group, it) }
                }
        }
    var selectedIds by remember(profile?.id) { mutableStateOf(emptyList<String>()) }
    var intervalMs by rememberSaveable { mutableLongStateOf(50L) }
    var windowMs by rememberSaveable { mutableLongStateOf(10_000L) }
    var normalized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(profile?.id, candidates.size) {
        vm.stopScope()
        if (selectedIds.isEmpty() && candidates.isNotEmpty()) {
            val preferred = candidates.filter { it.signal.favorite }
            selectedIds = (preferred.ifEmpty { candidates }).take(1).map { it.signal.id }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) vm.stopScope()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopScope()
        }
    }

    val running = scope.running
    val latest =
        scope.series
            .flatMap { it.points }
            .maxByOrNull { it.timeMs }
            ?.timeMs ?: 0L
    val now = if (running) System.currentTimeMillis() else latest.takeIf { it > 0 } ?: System.currentTimeMillis()
    val selectedCount = selectedIds.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(accent = if (running) AccentLime else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, null, Modifier.size(19.dp), tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Realtime scope", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Direct fast sampling of holding registers. Normal polling pauses while the scope runs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(if (running) "RUNNING" else "STOPPED", if (running) AccentLime else MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    scopeIntervals.forEach { option ->
                        FilterChip(
                            selected = intervalMs == option,
                            onClick = { if (!running) intervalMs = option },
                            enabled = !running,
                            label = { Text(if (option >= 1000) "${option / 1000} s" else "$option ms") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        Button(onClick = vm::stopScope, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Filled.Stop, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop scope")
                        }
                    } else {
                        Button(
                            onClick = { vm.startScope(selectedIds.toSet(), intervalMs) },
                            enabled = link.connected && profile != null && selectedCount > 0,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start scope")
                        }
                    }
                    Text(
                        "$selectedCount signal(s) · $intervalMs ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!link.connected) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect a link before starting the scope.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentAmber,
                    )
                }
            }
        }

        if (profile == null) {
            item {
                EmptyState(
                    title = "No active profile",
                    body = "Activate a profile with holding-register signals before opening the scope.",
                )
            }
            return@LazyColumn
        }
        if (candidates.isEmpty()) {
            item {
                EmptyState(
                    title = "No numeric holding registers",
                    body = "This profile has no numeric holding-register signals. Add one from the register map.",
                )
            }
            return@LazyColumn
        }

        item {
            Column {
                Text(
                    "Signals (max 6, holding registers)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    candidates.forEach { choice ->
                        val selected = selectedIds.contains(choice.signal.id)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!running) {
                                    selectedIds =
                                        if (selected) {
                                            selectedIds - choice.signal.id
                                        } else if (selectedIds.size >= 6) {
                                            selectedIds.drop(1) + choice.signal.id
                                        } else {
                                            selectedIds + choice.signal.id
                                        }
                                }
                            },
                            enabled = !running,
                            label = { Text("${choice.signal.name} · ${choice.signal.address}") },
                        )
                    }
                }
                val selectedNames =
                    candidates
                        .filter { selectedIds.contains(it.signal.id) }
                        .joinToString(", ") { it.signal.name }
                if (selectedNames.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Selected: $selectedNames",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    scopeWindows.forEach { option ->
                        FilterChip(
                            selected = windowMs == option,
                            onClick = { windowMs = option },
                            label = { Text(if (option >= 1000) "${option / 1000} s" else "$option ms") },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Scale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = !normalized,
                        onClick = { normalized = false },
                        label = { Text("Shared") },
                    )
                    FilterChip(
                        selected = normalized,
                        onClick = { normalized = true },
                        label = { Text("Each series") },
                    )
                }
            }
        }

        item {
            SectionCard {
                RealtimeChart(
                    series = scope.series,
                    windowMs = windowMs,
                    nowMs = now,
                    normalized = normalized,
                )
                if (normalized && scope.series.size > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Each series is normalized to its own min/max. Values in the legend are actual.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scope.series.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    scope.series.forEachIndexed { index, entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(scopeColors[index % scopeColors.size], CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                entry.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            Text(
                                entry.latestFormatted,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Interval", "${scope.intervalMs} ms")
                    StatCell(
                        "Actual",
                        if (scope.actualIntervalMs > 0) {
                            "%.1f Hz".format(Locale.US, scope.hertz)
                        } else {
                            "—"
                        },
                    )
                    StatCell("Reads", scope.reads.toString())
                    StatCell(
                        "Errors",
                        scope.errors.toString(),
                        valueColor = if (scope.errors > 0) AccentRose else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (scope.running) {
                        "Sampling ${scope.series.size} series · ${scope.series.sumOf { it.points.size }} points buffered"
                    } else if (scope.reads > 0) {
                        "Stopped · ${scope.series.sumOf { it.points.size }} points captured"
                    } else {
                        "Choose signals and press Start scope."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                scope.lastError?.let {
                    Spacer(Modifier.height(4.dp))
                    ErrorText(it)
                }
            }
        }

        item {
            Text(
                "Scope reads only the selected span in each holding-register group. Interval is a target; " +
                    "the actual rate depends on baud rate, response time and USB latency.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
