package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConversionOptions
import com.example.model.QualityLevel
import com.example.model.Resolution
import com.example.model.VideoOrientation
import com.example.model.VideoSourceInfo
import com.example.model.formatDuration
import com.example.model.formatFileSize
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PurpleAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionsCard(
    sourceInfo: VideoSourceInfo,
    options: ConversionOptions,
    onOptionsChanged: (ConversionOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("conversion_options_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Web Optimization Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GreenAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, GreenAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Est. ${formatFileSize(options.estimateSizeBytes())}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = DarkBorder)

            // 1. Output Resolution (480p / 720p / 1080p)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Output Resolution",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Resolution.entries.forEach { res ->
                        val selected = options.resolution == res
                        SelectablePill(
                            title = res.label,
                            subtitle = res.subtitle,
                            selected = selected,
                            onClick = { onOptionsChanged(options.copy(resolution = res)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resolution_option_${res.label.lowercase()}")
                        )
                    }
                }
            }

            // 2. Orientation (Landscape vs Portrait - crop to fill)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Orientation & Framing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Crop to fill • No stretch",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoOrientation.entries.forEach { orient ->
                        val selected = options.orientation == orient
                        SelectablePill(
                            title = orient.label,
                            subtitle = if (orient == VideoOrientation.LANDSCAPE) "16:9 (${options.resolution.width}x${options.resolution.height})"
                            else "9:16 (${options.resolution.height}x${options.resolution.width})",
                            selected = selected,
                            onClick = { onOptionsChanged(options.copy(orientation = orient)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("orientation_option_${orient.name.lowercase()}")
                        )
                    }
                }
            }

            // 3. Remove Audio (Default ON)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (options.removeAudio) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (options.removeAudio) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "Remove Audio Track",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Essential for web background video loops to enable autoplay across browsers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = options.removeAudio,
                        onCheckedChange = { onOptionsChanged(options.copy(removeAudio = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyanAccent
                        ),
                        modifier = Modifier.testTag("remove_audio_switch")
                    )
                }
            }

            // 4. Video Trim (Sliders for start and end, default max 20 seconds)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val totalDuration = sourceInfo.durationMs.coerceAtLeast(1000L)
                val currentTrimDuration = (options.trimEndMs - options.trimStartMs).coerceAtLeast(0L)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trim Range",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatDuration(options.trimStartMs)} → ${formatDuration(options.trimEndMs)} (${String.format("%.1f", currentTrimDuration / 1000.0)}s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentTrimDuration <= 20_000L) CyanAccent else PurpleAccent
                    )
                }

                RangeSlider(
                    value = (options.trimStartMs.toFloat())..(options.trimEndMs.toFloat()),
                    onValueChange = { range ->
                        val newStart = range.start.toLong().coerceAtLeast(0L)
                        val newEnd = range.endInclusive.toLong().coerceAtMost(totalDuration)
                        if (newEnd > newStart) {
                            onOptionsChanged(
                                options.copy(
                                    trimStartMs = newStart,
                                    trimEndMs = newEnd
                                )
                            )
                        }
                    },
                    valueRange = 0f..totalDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = CyanAccent,
                        inactiveTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("trim_range_slider")
                )

                // Quick trim presets for fast web loops
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val max20 = minOf(20_000L, totalDuration)
                    TrimChip(
                        label = "Loop 0 - 20s (Default)",
                        onClick = {
                            onOptionsChanged(
                                options.copy(
                                    trimStartMs = 0L,
                                    trimEndMs = max20
                                )
                            )
                        }
                    )
                    if (totalDuration > 10_000L) {
                        TrimChip(
                            label = "First 10s",
                            onClick = {
                                onOptionsChanged(
                                    options.copy(
                                        trimStartMs = 0L,
                                        trimEndMs = 10_000L
                                    )
                                )
                            }
                        )
                    }
                    if (totalDuration > 15_000L) {
                        TrimChip(
                            label = "First 15s",
                            onClick = {
                                onOptionsChanged(
                                    options.copy(
                                        trimStartMs = 0L,
                                        trimEndMs = 15_000L
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // 5. Quality / Bitrate (Low / Medium / High)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quality & Bitrate",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QualityLevel.entries.forEach { q ->
                        val selected = options.quality == q
                        SelectablePill(
                            title = q.label,
                            subtitle = q.description,
                            selected = selected,
                            onClick = { onOptionsChanged(options.copy(quality = q)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quality_option_${q.name.lowercase()}")
                        )
                    }
                }
            }

            // 6. Output File Name (editable, default "space-background.mp4")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Save As File Name",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = options.outputFileName,
                    onValueChange = { onOptionsChanged(options.copy(outputFileName = it)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("output_filename_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    supportingText = {
                        Text(
                            text = "Saved to Downloads/WebVideoConverter/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectablePill(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CyanAccent.copy(alpha = 0.15f) else DarkSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) CyanAccent else DarkBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TrimChip(
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DarkSurfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, DarkBorder)
    )
}
