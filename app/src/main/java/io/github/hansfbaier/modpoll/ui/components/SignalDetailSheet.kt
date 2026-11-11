package io.github.hansfbaier.modpoll.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hansfbaier.modpoll.data.DataType
import io.github.hansfbaier.modpoll.data.Framer
import io.github.hansfbaier.modpoll.data.SignalGroup
import io.github.hansfbaier.modpoll.engine.SignalReading
import io.github.hansfbaier.modpoll.modbus.EncodeResult
import io.github.hansfbaier.modpoll.modbus.Interpretation
import io.github.hansfbaier.modpoll.modbus.ValueCodec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalDetailSheet(
    profileName: String,
    group: SignalGroup,
    reading: SignalReading,
    connected: Boolean,
    framer: Framer,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onWrite: suspend (String) -> Result<Unit>,
    onWriteBit: suspend (Int, Boolean) -> Result<Unit>,
) {
    val signal = reading.signal
    val measurement = reading.measurement
    val scope = rememberCoroutineScope()
    var input by remember(signal.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyBit by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = objectAccent(group.objectType)
    val writable = signal.writable && connected
    val coilBitfield = group.objectType == io.github.hansfbaier.modpoll.data.ObjectType.COIL && signal.dataType.isBitField
    val inputHint =
        when (signal.dataType) {
            DataType.STRING -> "Text"
            DataType.BOOL -> "ON / OFF"
            DataType.BOOL8, DataType.BOOL16 -> "Integer bitmask"
            else -> if (signal.scale != 1.0) "Engineering value (scale ${signal.scale})" else "Value"
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(signal.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${group.name} · ${group.objectType.label} · $profileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (signal.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (signal.favorite) "Unfavorite" else "Favorite",
                        tint = if (signal.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = measurement?.formatted ?: "—",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 34.sp,
                    color = if (reading.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (signal.writable) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill("RW", accent)
                }
            }
            if (reading.error != null) {
                Spacer(Modifier.height(4.dp))
                ErrorText(reading.error)
            }
            Spacer(Modifier.height(14.dp))
            KeyValueRow("Address", "${signal.address}  (0x%04X)".format(signal.address))
            KeyValueRow("Function", "FC%02d %s".format(group.objectType.readFunction, group.objectType.label))
            KeyValueRow("Data type", signal.dataType.label + if (signal.dataType == DataType.STRING) " (${signal.stringBytes} B)" else "")
            if (!group.objectType.isBitTable) {
                KeyValueRow("Endian", signal.endian.name)
                if (signal.dataType == DataType.BOOL) KeyValueRow("Bit", (signal.bit ?: 0).toString())
            }
            KeyValueRow("Scale / offset", "${signal.scale} / ${signal.offset}")
            KeyValueRow("Raw", measurement?.rawHex ?: "—")
            KeyValueRow("Updated", formatTime(reading.updatedAt))
            KeyValueRow("Framing", framer.label)
            if (signal.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                KeyValueRow("Description", signal.description, mono = false)
            }

            if (measurement != null && measurement.bits.size > 1) {
                Spacer(Modifier.height(14.dp))
                Text("Bit field", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                BitRow(
                    bits = measurement.bits,
                    color = accent,
                    enabled = writable && coilBitfield,
                    onToggle = { index, on ->
                        if (busyBit == null) {
                            scope.launch {
                                busyBit = index
                                error = null
                                message = null
                                onWriteBit(index, on).fold(
                                    onSuccess = { message = "Bit $index ${if (on) "set" else "cleared"}" },
                                    onFailure = { error = it.message ?: "Write failed" },
                                )
                                busyBit = null
                            }
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (coilBitfield) "Tap a cell to toggle that coil. LSB is bit 0." else "Read-only bitmap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busyBit != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Writing bit $busyBit…", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (measurement?.rawWords?.isNotEmpty() == true && !group.objectType.isBitTable) {
                Spacer(Modifier.height(16.dp))
                Text("Interpretations", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                val interpretations: List<Interpretation> =
                    remember(measurement.rawWords, signal.endian) {
                        ValueCodec.interpretations(measurement.rawWords.toIntArray(), signal.endian)
                    }
                interpretations.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            entry.label,
                            modifier = Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            entry.value,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            val history = reading.history
            if (history.size > 1) {
                val values = history.map { it.value }
                Spacer(Modifier.height(16.dp))
                Text("Trend window", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatCell("Min", ValueCodec.formatNumber(values.min(), ValueCodec.decimalsFor(signal), signal.unit))
                    StatCell("Avg", ValueCodec.formatNumber(values.average(), ValueCodec.decimalsFor(signal), signal.unit))
                    StatCell("Max", ValueCodec.formatNumber(values.max(), ValueCodec.decimalsFor(signal), signal.unit))
                    StatCell("Samples", values.size.toString())
                }
                Sparkline(values.takeLast(120).map { it.toFloat() }, accent, Modifier.padding(top = 8.dp))
            }

            if (signal.writable) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Text("Write value", style = MaterialTheme.typography.titleSmall)
                if (!connected) {
                    Text(
                        "Connect a link to enable writes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (coilBitfield) {
                    Text(
                        "Use the bit field above to toggle individual coils.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                            message = null
                        },
                        label = { Text(inputHint) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    when (signal.dataType) {
                                        DataType.STRING, DataType.BOOL -> KeyboardType.Text
                                        else -> if (signal.dataType.isInteger && signal.scale == 1.0) KeyboardType.Number else KeyboardType.Decimal
                                    },
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val preview =
                        remember(input, signal) {
                            if (input.isBlank()) null else ValueCodec.encodeRegister(signal, input)
                        }
                    when (preview) {
                        is EncodeResult.Error -> {
                            ErrorText(preview.message, Modifier.padding(top = 4.dp))
                        }

                        is EncodeResult.Ok -> {
                            val hex = preview.words.joinToString(" ") { "%04X".format(it) }
                            Text(
                                "FC%02d @ ${signal.address} → $hex".format(
                                    if (preview.words.size == 1) 6 else 16,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        null -> {
                            Unit
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                message = null
                                onWrite(input).fold(
                                    onSuccess = {
                                        message = "Write acknowledged"
                                        input = ""
                                    },
                                    onFailure = { error = it.message ?: "Write failed" },
                                )
                                busy = false
                            }
                        },
                        enabled = !busy && input.isNotBlank() && preview is EncodeResult.Ok,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (busy) "Writing…" else "Write to device")
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                error?.let { ErrorText(it) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
