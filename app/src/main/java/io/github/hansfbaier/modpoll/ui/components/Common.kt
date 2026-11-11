package io.github.hansfbaier.modpoll.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hansfbaier.modpoll.data.ObjectType
import io.github.hansfbaier.modpoll.ui.theme.AccentAmber
import io.github.hansfbaier.modpoll.ui.theme.AccentCyan
import io.github.hansfbaier.modpoll.ui.theme.AccentLime
import io.github.hansfbaier.modpoll.ui.theme.AccentRose
import io.github.hansfbaier.modpoll.ui.theme.AccentTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun objectAccent(type: ObjectType): Color =
    when (type) {
        ObjectType.COIL -> AccentLime
        ObjectType.DISCRETE_INPUT -> AccentTeal
        ObjectType.INPUT_REGISTER -> AccentCyan
        ObjectType.HOLDING_REGISTER -> AccentAmber
    }

@Composable
fun Led(
    color: Color,
    size: Int = 9,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size((size + 5).dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((size + 5).dp)
                .background(color.copy(alpha = 0.18f), CircleShape),
        )
        Box(
            Modifier
                .size(size.dp)
                .background(color, CircleShape),
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (filled) color.copy(alpha = 0.18f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    weight: FontWeight = FontWeight.Medium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    accent?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(18.dp),
                ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    lineWidth: Float = 1.8f,
) {
    Canvas(modifier.fillMaxWidth().height(26.dp)) {
        val w = size.width
        val h = size.height
        if (values.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, h - 1f),
                end = Offset(w, h - 1f),
                strokeWidth = 1.2f,
            )
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0.000001f } ?: 1f
        val step = w / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = h - ((value - min) / span) * (h - 3f) - 1.5f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill =
            Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
        drawPath(
            fill,
            Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))),
        )
        drawPath(path, color, style = Stroke(width = lineWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.invoke()
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        MonoText(value, size = 15, weight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    mono: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

@Composable
fun DotSeparator(color: Color = MaterialTheme.colorScheme.outline) {
    Box(Modifier.size(3.dp).background(color, CircleShape))
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
private val shortTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

fun formatTime(ms: Long): String = if (ms <= 0) "—" else timeFormat.format(Date(ms))

fun formatTimePrecise(ms: Long): String = if (ms <= 0) "—" else shortTimeFormat.format(Date(ms))

@Composable
fun ValueText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 26,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
fun ErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun AccentBar(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(4.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.25f)))),
    )
}
