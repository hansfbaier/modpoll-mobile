package io.github.hansfbaier.modpoll.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hansfbaier.modpoll.data.Framer
import io.github.hansfbaier.modpoll.modbus.ExchangeKind
import io.github.hansfbaier.modpoll.modbus.FrameExchange
import io.github.hansfbaier.modpoll.modbus.describeException
import io.github.hansfbaier.modpoll.modbus.toHex
import io.github.hansfbaier.modpoll.ui.AppViewModel
import io.github.hansfbaier.modpoll.ui.components.EmptyState
import io.github.hansfbaier.modpoll.ui.components.ErrorText
import io.github.hansfbaier.modpoll.ui.components.Led
import io.github.hansfbaier.modpoll.ui.components.MonoText
import io.github.hansfbaier.modpoll.ui.components.SectionCard
import io.github.hansfbaier.modpoll.ui.components.StatCell
import io.github.hansfbaier.modpoll.ui.components.StatusPill
import io.github.hansfbaier.modpoll.ui.components.formatTimePrecise
import io.github.hansfbaier.modpoll.ui.theme.AccentAmber
import io.github.hansfbaier.modpoll.ui.theme.AccentCyan
import io.github.hansfbaier.modpoll.ui.theme.AccentLime
import io.github.hansfbaier.modpoll.ui.theme.AccentRose

private enum class LogFilter(
    val label: String,
) {
    ALL("All"),
    READS("Reads"),
    WRITES("Writes"),
    ERRORS("Errors"),
}

@Composable
fun ConsoleScreen(vm: AppViewModel) {
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var expanded by remember { mutableStateOf(emptySet<Long>()) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let(vm::exportTrafficCsv)
        }

    val filtered =
        traffic.asReversed().filter { entry ->
            when (filter) {
                LogFilter.ALL -> true
                LogFilter.READS -> entry.kind == ExchangeKind.READ || entry.kind == ExchangeKind.SCAN
                LogFilter.WRITES -> entry.kind == ExchangeKind.WRITE
                LogFilter.ERRORS -> !entry.ok
            }
        }
    val errors = traffic.count { !it.ok }
    val avgLatency = if (traffic.isEmpty()) 0 else traffic.map { it.durationMs }.average().toInt()

    Column(Modifier.fillMaxSize()) {
        SectionCard(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp), tint = AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("Bus traffic", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { exportLauncher.launch("modbus-traffic.csv") }) {
                    Icon(Icons.Outlined.Download, "Export log")
                }
                IconButton(onClick = vm::clearTraffic) {
                    Icon(Icons.Outlined.DeleteSweep, "Clear log")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("Frames", traffic.size.toString())
                StatCell("Errors", errors.toString(), valueColor = if (errors > 0) AccentRose else MaterialTheme.colorScheme.onSurface)
                StatCell("Avg", "$avgLatency ms")
                StatCell("Framing", traffic.lastOrNull()?.framer?.label ?: "—")
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LogFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = if (traffic.isEmpty()) "No frames yet" else "No matching frames",
                body =
                    if (traffic.isEmpty()) {
                        "Requests and responses appear here with CRC/LRC status, timing and exception codes."
                    } else {
                        "Change the filter to see other exchanges."
                    },
                icon = { Icon(Icons.Outlined.Terminal, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    TrafficRow(
                        entry = entry,
                        expanded = expanded.contains(entry.id),
                        onToggle = {
                            expanded =
                                if (expanded.contains(entry.id)) {
                                    expanded - entry.id
                                } else {
                                    expanded + entry.id
                                }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficRow(
    entry: FrameExchange,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val statusColor =
        when {
            entry.ok -> AccentLime
            entry.exceptionCode != null -> AccentAmber
            else -> AccentRose
        }
    val accent = if (entry.kind == ExchangeKind.WRITE) AccentAmber else AccentCyan
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Led(statusColor, 7)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.functionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusPill(
                            "u${entry.unitId}",
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.attempt > 1) {
                            Spacer(Modifier.width(4.dp))
                            StatusPill("try ${entry.attempt}", AccentAmber)
                        }
                    }
                    MonoText(
                        "${formatTimePrecise(entry.timeMs)} · ${entry.kind.label} · ${entry.durationMs} ms · ${entry.framer.label}",
                        size = 9,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!entry.ok) {
                    Text(
                        entry.exceptionCode?.let { "EX $it" } ?: "ERR",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(start = 24.dp, end = 10.dp, bottom = 8.dp)) {
                MonoText(
                    frameText(entry, entry.request),
                    size = 9,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(10.dp),
                ) {
                    FrameLine("TX", frameText(entry, entry.request), accent)
                    FrameLine(
                        "RX",
                        entry.response?.let { frameText(entry, it) } ?: "(no response)",
                        if (entry.response == null) AccentRose else AccentLime,
                    )
                    if (entry.error != null) {
                        Spacer(Modifier.height(4.dp))
                        ErrorText(entry.error)
                    }
                    if (entry.exceptionCode != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Exception ${entry.exceptionCode}: ${describeException(entry.exceptionCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentAmber,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kind=${entry.kind.label}  function=${entry.function}  attempt=${entry.attempt}  time=${formatTimePrecise(
                            entry.timeMs,
                        )}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun frameText(entry: FrameExchange, bytes: ByteArray): String =
    if (entry.framer == Framer.ASCII) {
        bytes.toString(Charsets.US_ASCII).trim()
    } else {
        bytes.toHex(" ")
    }

@Composable
private fun FrameLine(
    label: String,
    hex: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .width(28.dp)
                .padding(top = 1.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            hex,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
