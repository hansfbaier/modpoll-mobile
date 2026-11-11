package com.modpoll.rtu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modpoll.rtu.data.DataType
import com.modpoll.rtu.data.SignalGroup
import com.modpoll.rtu.data.displayName
import com.modpoll.rtu.engine.SignalReading
import com.modpoll.rtu.ui.theme.AccentLime

@Composable
fun ValueTile(
    reading: SignalReading,
    group: SignalGroup,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = objectAccent(group.objectType)
    val flash = remember { Animatable(0f) }
    LaunchedEffect(reading.changedAt) {
        if (reading.changedAt > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, animationSpec = tween(durationMillis = 900))
        }
    }
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val background = lerp(base, accent.copy(alpha = 0.55f), flash.value * 0.45f)
    val error = reading.error != null
    val stale = reading.measurement == null
    val valueColor =
        when {
            error -> MaterialTheme.colorScheme.error
            stale -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    val measurement = reading.measurement
    val bitfield = measurement != null && measurement.bits.size > 1

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = background,
        border =
            BorderStroke(
                1.dp,
                if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reading.signal.displayName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reading.signal.writable) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Writable",
                        modifier = Modifier.size(13.dp),
                        tint = accent.copy(alpha = 0.9f),
                    )
                }
                if (error) {
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                }
            }
            Spacer(Modifier.height(6.dp))
            if (bitfield) {
                BitRow(measurement.bits, accent, enabled = false)
                Spacer(Modifier.height(6.dp))
                MonoText(
                    text = measurement.formatted,
                    size = 13,
                    weight = FontWeight.SemiBold,
                    color = valueColor,
                )
            } else {
                Text(
                    text = measurement?.formatted ?: if (error) "—" else "…",
                    fontFamily = FontFamily.Monospace,
                    fontSize = if ((measurement?.formatted?.length ?: 0) > 11) 18.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoText(
                    text = measurement?.rawHex ?: "—",
                    modifier = Modifier.weight(1f),
                    size = 9,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Text(
                    text = reading.signal.dataType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                )
            }
            if (measurement?.numeric != null && reading.history.size > 1) {
                Sparkline(
                    values = reading.history.takeLast(40).map { it.value.toFloat() },
                    color = accent,
                    lineWidth = 1.4f,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(accent.copy(alpha = 0.15f)),
                )
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
fun BitRow(
    bits: List<Boolean>,
    color: Color,
    enabled: Boolean,
    onToggle: ((Int, Boolean) -> Unit)? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        bits.forEachIndexed { index, on ->
            val dotColor = if (on) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            Box(
                modifier =
                    Modifier
                        .size(if (bits.size > 12) 15.dp else 18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) dotColor.copy(alpha = 0.85f) else dotColor.copy(alpha = 0.16f))
                        .then(
                            if (enabled && onToggle != null) {
                                Modifier.clickable { onToggle(index, !on) }
                            } else {
                                Modifier
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$index",
                    fontSize = if (bits.size > 12) 7.sp else 8.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
fun BoolValuePill(on: Boolean) {
    val color = if (on) AccentLime else MaterialTheme.colorScheme.outline
    StatusPill(
        text = if (on) "ON" else "OFF",
        color = color,
        filled = on,
    )
}

@Composable
fun TypeBadge(type: DataType) {
    Text(
        type.label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
