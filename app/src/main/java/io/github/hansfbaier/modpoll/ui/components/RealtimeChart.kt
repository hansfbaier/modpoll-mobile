package io.github.hansfbaier.modpoll.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hansfbaier.modpoll.engine.ScopePoint
import io.github.hansfbaier.modpoll.engine.ScopeSeries
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
import kotlin.math.ceil

val scopeColors: List<Color> = listOf(AccentCyan, AccentLime, AccentAmber, AccentViolet, AccentTeal, AccentRose)

@Composable
fun RealtimeChart(
    series: List<ScopeSeries>,
    windowMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
    normalized: Boolean = false,
) {
    val start = nowMs - windowMs
    val visible =
        series.map { entry ->
            entry to entry.points.filter { it.timeMs in start..nowMs }
        }
    val allValues = visible.flatMap { (_, points) -> points.map { it.value } }
    if (allValues.isEmpty()) {
        Text(
            "No samples in this window yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 34.dp),
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
    val sharedSpan = (max - min).takeIf { it > 0.0 } ?: 1.0
    val axisFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    val horizontalGrid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val verticalGrid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    Column(modifier.fillMaxWidth()) {
        Row {
            Column(
                Modifier.width(58.dp).height(238.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                if (normalized) {
                    AxisLabel("100%")
                    AxisLabel("50%")
                    AxisLabel("0%")
                } else {
                    AxisLabel(formatAxis(max))
                    AxisLabel(formatAxis((max + min) / 2))
                    AxisLabel(formatAxis(min))
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier.weight(1f).height(238.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val w = size.width
                    val h = size.height
                    for (row in 0..4) {
                        val y = h * row / 4f
                        drawLine(
                            color = horizontalGrid,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                        )
                    }
                    for (col in 0..6) {
                        val x = w * col / 6f
                        drawLine(
                            color = verticalGrid,
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f,
                        )
                    }
                    visible.forEachIndexed { index, (_, points) ->
                        val color = scopeColors[index % scopeColors.size]
                        val drawPoints = decimate(points, MAX_DRAW_POINTS)
                        val seriesMin: Double
                        val seriesMax: Double
                        if (normalized) {
                            val values = points.map { it.value }
                            val pair = paddedRange(values)
                            seriesMin = pair.first
                            seriesMax = pair.second
                        } else {
                            seriesMin = min
                            seriesMax = max
                        }
                        val seriesSpan = (seriesMax - seriesMin).takeIf { it > 0.0 } ?: sharedSpan
                        if (drawPoints.size >= 2) {
                            val path = Path()
                            drawPoints.forEachIndexed { pointIndex, point ->
                                val x = ((point.timeMs - start).toFloat() / windowMs.coerceAtLeast(1)) * w
                                val y = (h - ((point.value - seriesMin) / seriesSpan).toFloat() * h).coerceIn(0f, h)
                                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                        }
                        drawPoints.lastOrNull()?.let { last ->
                            val x = ((last.timeMs - start).toFloat() / windowMs.coerceAtLeast(1)) * w
                            val y = (h - ((last.value - seriesMin) / seriesSpan).toFloat() * h).coerceIn(0f, h)
                            drawCircle(color, radius = 3.5f, center = Offset(x.coerceIn(0f, w), y))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(start = 64.dp)) {
            Text(
                axisFormat.format(Date(start)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                axisFormat.format(Date(nowMs)),
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

private fun paddedRange(values: List<Double>): Pair<Double, Double> {
    if (values.isEmpty()) return 0.0 to 1.0
    var lo = values.min()
    var hi = values.max()
    if (abs(hi - lo) < 1e-9) {
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.05
        lo -= pad
        hi += pad
    } else {
        val pad = (hi - lo) * 0.08
        lo -= pad
        hi += pad
    }
    return lo to hi
}

private fun formatAxis(value: Double): String =
    when {
        abs(value) >= 1_000_000 -> "%.2e".format(Locale.US, value)
        abs(value) >= 1000 -> "%.0f".format(Locale.US, value)
        abs(value) >= 10 -> "%.1f".format(Locale.US, value)
        else -> "%.3f".format(Locale.US, value)
    }

/** Keeps spikes visible by retaining the min and max point of each bucket. */
private fun decimate(
    points: List<ScopePoint>,
    maxPoints: Int,
): List<ScopePoint> {
    if (points.size <= maxPoints) return points
    val bucket = ceil(points.size.toDouble() / (maxPoints / 2)).toInt().coerceAtLeast(1)
    val out = ArrayList<ScopePoint>(maxPoints + 2)
    points.chunked(bucket).forEach { chunk ->
        val minPoint = chunk.minByOrNull { it.value }
        val maxPoint = chunk.maxByOrNull { it.value }
        if (minPoint != null && maxPoint != null) {
            if (minPoint.timeMs <= maxPoint.timeMs) {
                out += minPoint
                if (maxPoint !== minPoint) out += maxPoint
            } else {
                out += maxPoint
                out += minPoint
            }
        }
    }
    return out
}

private const val MAX_DRAW_POINTS = 1200
