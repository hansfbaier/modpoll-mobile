package io.github.hansfbaier.modpoll.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hansfbaier.modpoll.engine.SamplePoint
import io.github.hansfbaier.modpoll.engine.SignalReading
import io.github.hansfbaier.modpoll.modbus.ValueCodec
import io.github.hansfbaier.modpoll.ui.AppViewModel
import io.github.hansfbaier.modpoll.ui.components.EmptyState
import io.github.hansfbaier.modpoll.ui.components.Led
import io.github.hansfbaier.modpoll.ui.components.MonoText
import io.github.hansfbaier.modpoll.ui.components.SectionCard
import io.github.hansfbaier.modpoll.ui.components.StatCell
import io.github.hansfbaier.modpoll.ui.theme.AccentAmber
import io.github.hansfbaier.modpoll.ui.theme.AccentCyan
import io.github.hansfbaier.modpoll.ui.theme.AccentLime
import io.github.hansfbaier.modpoll.ui.theme.AccentRose
import io.github.hansfbaier.modpoll.ui.theme.AccentTeal
import io.github.hansfbaier.modpoll.ui.theme.AccentViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val seriesColors = listOf(AccentCyan, AccentLime, AccentAmber, AccentViolet, AccentTeal, AccentRose)

private enum class TrendWindow(
    val label: String,
    val ms: Long,
) {
    ONE_MIN("1 min", 60_000),
    FIVE_MIN("5 min", 300_000),
    FIFTEEN_MIN("15 min", 900_000),
    ONE_HOUR("1 h", 3_600_000),
}

private data class TrendSeries(
    val reading: SignalReading,
    val color: Color,
    val points: List<SamplePoint>,
)

@Composable
fun TrendsScreen(vm: AppViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val poll by vm.poll.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()

    val numeric = poll.values.values.filter { it.measurement?.numeric != null }
    var selectedIds by remember(profile?.id) { mutableStateOf(emptyList<String>()) }
    var window by rememberSaveable { mutableStateOf(TrendWindow.FIVE_MIN) }
    var frozenAt by remember { mutableStateOf<Long?>(null) }
    val now = frozenAt ?: System.currentTimeMillis()

    LaunchedEffect(profile?.id, numeric.size) {
        if (selectedIds.isEmpty() && numeric.isNotEmpty()) {
            val preferred = numeric.filter { it.signal.favorite }
            val initial = (preferred.ifEmpty { numeric }).take(4)
            selectedIds = initial.map { it.signal.id }
        }
    }

    val series =
        selectedIds.mapIndexedNotNull { index, id ->
            numeric.firstOrNull { it.signal.id == id }?.let { reading ->
                TrendSeries(
                    reading = reading,
                    color = seriesColors[index % seriesColors.size],
                    points = reading.history.filter { it.timeMs >= now - window.ms && it.timeMs <= now },
                )
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShowChart, null, Modifier.size(18.dp), tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Trend recorder", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (link.connected) {
                                "Live samples, ${poll.values.values.sumOf {
                                    it.history.size
                                }} buffered"
                            } else {
                                "Connect a link or start the simulator"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { frozenAt = if (frozenAt == null) System.currentTimeMillis() else null },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            if (frozenAt == null) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (frozenAt == null) "Freeze" else "Live")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TrendWindow.entries.forEach { option ->
                        FilterChip(
                            selected = window == option,
                            onClick = { window = option },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }
        if (numeric.isEmpty()) {
            item {
                EmptyState(
                    title = "No numeric samples yet",
                    body = "Start polling to build trend history. Numeric register and boolean signals are recorded here.",
                    icon = {
                        Icon(
                            Icons.Outlined.ShowChart,
                            null,
                            Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            return@LazyColumn
        }
        item {
            SignalPicker(
                readings =
                    numeric.sortedWith(
                        compareBy(
                            { !selectedIds.contains(it.signal.id) },
                            { !it.signal.favorite },
                            { it.groupId },
                        ),
                    ),
                selected = selectedIds,
                onToggle = { id ->
                    selectedIds =
                        if (selectedIds.contains(id)) {
                            selectedIds - id
                        } else {
                            if (selectedIds.size >= 6) selectedIds.drop(1) + id else selectedIds + id
                        }
                },
            )
        }
        item {
            SectionCard {
                if (series.isEmpty()) {
                    Text(
                        "Select one or more signals above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val units = series.map { it.reading.signal.unit }.filter { it.isNotBlank() }.distinct()
                    if (units.size > 1) {
                        Text(
                            "Mixed units: each series shares one Y axis (${units.joinToString(", ")}).",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAmber,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    TrendChart(
                        series = series,
                        windowMs = window.ms,
                        now = now,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        series.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(entry.color, CircleShape))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    entry.reading.signal.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (series.isNotEmpty()) {
            item {
                SectionCard {
                    Text("Statistics", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    series.forEach { entry ->
                        val values = entry.points.map { it.value }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(entry.color, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.reading.signal.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            if (values.isEmpty()) {
                                Text("—", style = MaterialTheme.typography.bodySmall)
                            } else {
                                StatCell(
                                    "min",
                                    ValueCodec.formatNumber(values.min(), ValueCodec.decimalsFor(entry.reading.signal), ""),
                                )
                                Spacer(Modifier.width(12.dp))
                                StatCell(
                                    "avg",
                                    ValueCodec.formatNumber(values.average(), ValueCodec.decimalsFor(entry.reading.signal), ""),
                                )
                                Spacer(Modifier.width(12.dp))
                                StatCell(
                                    "max",
                                    ValueCodec.formatNumber(values.max(), ValueCodec.decimalsFor(entry.reading.signal), ""),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalPicker(
    readings: List<SignalReading>,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    Column {
        Text(
            "Signals (max 6)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            readings.forEach { reading ->
                val isSelected = selected.contains(reading.signal.id)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(reading.signal.id) },
                    label = { Text(reading.signal.name, maxLines = 1) },
                    leadingIcon =
                        if (isSelected) {
                            { Box(Modifier.size(7.dp).background(seriesColors[0], CircleShape)) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun TrendChart(
    series: List<TrendSeries>,
    windowMs: Long,
    now: Long,
) {
    val allValues = series.flatMap { entry -> entry.points.map { it.value } }
    if (allValues.isEmpty()) {
        Text(
            "No samples in this window yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 30.dp),
        )
        return
    }
    var min = allValues.min()
    var max = allValues.max()
    if (abs(max - min) < 1e-9) {
        val pad = if (abs(max) < 1e-9) 1.0 else abs(max) * 0.05
        min -= pad
        max += pad
    } else {
        val pad = (max - min) * 0.08
        min -= pad
        max += pad
    }
    val start = now - windowMs
    val axisFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    Column {
        Row {
            Column(
                Modifier
                    .width(56.dp)
                    .height(190.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisLabel(axisFormatNumber(max))
                AxisLabel(axisFormatNumber((max + min) / 2))
                AxisLabel(axisFormatNumber(min))
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier.weight(1f).height(190.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val w = size.width
                    val h = size.height
                    for (row in 0..4) {
                        val y = h * row / 4f
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                        )
                    }
                    for (col in 0..6) {
                        val x = w * col / 6f
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f,
                        )
                    }
                    series.forEach { entry ->
                        if (entry.points.size < 2) {
                            val point = entry.points.firstOrNull() ?: return@forEach
                            val x = ((point.timeMs - start).toFloat() / windowMs) * w
                            val y = h - ((point.value - min) / (max - min)).toFloat() * h
                            drawCircle(entry.color, radius = 3f, center = Offset(x.coerceIn(0f, w), y.coerceIn(0f, h)))
                        } else {
                            val path = Path()
                            entry.points.forEachIndexed { index, point ->
                                val x = ((point.timeMs - start).toFloat() / windowMs) * w
                                val y = h - ((point.value - min) / (max - min)).toFloat() * h
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path,
                                entry.color,
                                style = Stroke(width = 2f, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(start = 62.dp)) {
            Text(
                axisFormat.format(Date(start)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                axisFormat.format(Date(now)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

private fun axisFormatNumber(value: Double): String =
    when {
        abs(value) >= 1_000_000 -> "%.2e".format(Locale.US, value)
        abs(value) >= 1000 -> "%.0f".format(Locale.US, value)
        abs(value) >= 10 -> "%.1f".format(Locale.US, value)
        else -> "%.3f".format(Locale.US, value)
    }
