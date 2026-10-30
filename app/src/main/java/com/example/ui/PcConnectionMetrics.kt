// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppStrings
import com.example.LocalAppStrings
import com.example.ui.theme.BreathingDot
import com.example.ui.theme.HyprColors
import com.example.ui.theme.JetBrainsMono

/**
 * Immutable state representation for PC connection and telemetry metrics.
 * Hoisting this state allows targeted recompositions during high-bandwidth operations (e.g. audio streaming).
 */
@Immutable
data class PcConnectionMetrics(
    val cpuUsage: Float? = null,
    val ramUsedGB: Float? = null,
    val ramTotalGB: Float? = null,
    val tempCelsius: Float? = null,
    val batteryPercent: Int? = null,
    val pingMs: Float? = null,
    val isConnected: Boolean = false,
    val isAudioStreaming: Boolean = false,
    val protocolVersion: String = "hyprlink/1",
    val hostname: String? = null,
    val uptimeSeconds: Long? = null,
    val diskFreeGB: Float? = null,
    val diskTotalGB: Float? = null,
    val gpuUsage: Float? = null
)

/**
 * Custom composable that optimizes PC connection metrics updates on the dashboard.
 * Uses state-hoisting via a provider lambda and [derivedStateOf] so that rapid telemetry
 * ticks or audio streaming events do not trigger full dashboard recomposition.
 */
@Composable
fun PcConnectionMetricsSection(
    metricsProvider: () -> PcConnectionMetrics,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
    strings: AppStrings = LocalAppStrings.current
) {
    // Derive UI state so child nodes only recompose when their specific values change
    val isConnected by remember { derivedStateOf { metricsProvider().isConnected } }
    val isAudioStreaming by remember { derivedStateOf { metricsProvider().isAudioStreaming } }

    val headerTitle by remember {
        derivedStateOf {
            val m = metricsProvider()
            val h = m.hostname
            if (m.isConnected && !h.isNullOrBlank()) {
                "TELEMETRIA · ${h.uppercase()}"
            } else {
                strings.pcTelemetry
            }
        }
    }

    val cpuValue by remember {
        derivedStateOf {
            val m = metricsProvider()
            val cpu = m.cpuUsage
            if (m.isConnected && cpu != null) "${cpu.toInt()}%" else "—"
        }
    }
    val cpuProgress by remember {
        derivedStateOf {
            val m = metricsProvider()
            val cpu = m.cpuUsage
            if (m.isConnected && cpu != null) (cpu / 100f).coerceIn(0f, 1f) else 0f
        }
    }

    val ramValue by remember {
        derivedStateOf {
            val m = metricsProvider()
            val used = m.ramUsedGB
            if (m.isConnected && used != null) {
                String.format(java.util.Locale.US, "%.1fG", used)
            } else "—"
        }
    }
    val ramProgress by remember {
        derivedStateOf {
            val m = metricsProvider()
            val used = m.ramUsedGB
            val total = m.ramTotalGB ?: 16f
            if (m.isConnected && used != null && total > 0f) {
                (used / total).coerceIn(0f, 1f)
            } else 0f
        }
    }

    val tempValue by remember {
        derivedStateOf {
            val m = metricsProvider()
            val t = m.tempCelsius
            if (m.isConnected && t != null) "${t.toInt()}°" else "—"
        }
    }
    val tempProgress by remember {
        derivedStateOf {
            val m = metricsProvider()
            val t = m.tempCelsius
            if (m.isConnected && t != null) (t / 100f).coerceIn(0f, 1f) else 0f
        }
    }
    val tempColor by remember {
        derivedStateOf {
            val t = metricsProvider().tempCelsius
            if (t != null) {
                if (t >= 80f) HyprColors.Red else if (t >= 65f) HyprColors.Amber else HyprColors.NeonGreen
            } else {
                HyprColors.NeonGreen
            }
        }
    }

    val batteryValue by remember {
        derivedStateOf {
            val m = metricsProvider()
            val b = m.batteryPercent
            if (m.isConnected && b != null) "$b%" else "—"
        }
    }
    val batteryProgress by remember {
        derivedStateOf {
            val m = metricsProvider()
            val b = m.batteryPercent
            if (m.isConnected && b != null) (b / 100f).coerceIn(0f, 1f) else 0f
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header with optional high-bandwidth Audio Stream indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerTitle,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                letterSpacing = 0.16.sp,
                color = HyprColors.TextCaption,
                fontWeight = FontWeight.Bold
            )

            AnimatedVisibility(
                visible = isAudioStreaming,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RectangleShape)
                        .background(HyprColors.SurfaceCard)
                        .border(1.dp, HyprColors.NeonGreen.copy(alpha = 0.4f), RectangleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("audio_streaming_indicator")
                ) {
                    BreathingDot(color = HyprColors.NeonGreen, size = 5.dp, periodMs = 800)
                    Text(
                        text = strings.audioStreamIndicator,
                        fontFamily = JetBrainsMono,
                        fontSize = 8.sp,
                        color = HyprColors.NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Row of Metric Boxes and Detail Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OptimizedTelemetryBox(
                label = "CPU",
                value = cpuValue,
                progress = cpuProgress,
                color = HyprColors.NeonGreen,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_cpu"
            )
            OptimizedTelemetryBox(
                label = "RAM",
                value = ramValue,
                progress = ramProgress,
                color = HyprColors.NeonGreen,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_ram"
            )
            OptimizedTelemetryBox(
                label = strings.statTemp,
                value = tempValue,
                progress = tempProgress,
                color = tempColor,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_temp"
            )
            OptimizedTelemetryBox(
                label = "BAT",
                value = batteryValue,
                progress = batteryProgress,
                color = HyprColors.NeonGreen,
                modifier = Modifier.weight(1f),
                testTag = "telemetry_bat"
            )

            // Detail button (touch target >= 48dp, 58dp tall)
            Box(
                modifier = Modifier
                    .weight(0.72f)
                    .height(58.dp)
                    .clip(RectangleShape)
                    .border(1.dp, HyprColors.BorderHighlight, RectangleShape)
                    .clickable { onDetailClick() }
                    .testTag("telemetry_detail_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("＋", fontSize = 14.sp, color = HyprColors.TextSecondary)
                    Text(
                        text = strings.detail,
                        fontFamily = JetBrainsMono,
                        fontSize = 7.sp,
                        color = HyprColors.TextCaption,
                        letterSpacing = 0.08.sp
                    )
                }
            }
        }
    }
}

/**
 * Overload of [PcConnectionMetricsSection] accepting hoisted immutable state directly.
 */
@Composable
fun PcConnectionMetricsSection(
    metrics: PcConnectionMetrics,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
    strings: AppStrings = LocalAppStrings.current
) {
    PcConnectionMetricsSection(
        metricsProvider = { metrics },
        onDetailClick = onDetailClick,
        modifier = modifier,
        strings = strings
    )
}

/**
 * Isolated Telemetry Card with progress gauge and testTag.
 */
@Composable
fun OptimizedTelemetryBox(
    label: String,
    value: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    testTag: String = "telemetry_${label.lowercase()}"
) {
    Box(
        modifier = modifier
            .clip(RectangleShape)
            .background(HyprColors.SurfaceCard)
            .border(1.dp, HyprColors.BorderNormal, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 9.dp)
            .testTag(testTag)
    ) {
        Column {
            Text(
                text = label,
                fontFamily = JetBrainsMono,
                fontSize = 8.sp,
                color = HyprColors.TextCaption
            )
            Text(
                text = value,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                color = if (value == "--") HyprColors.TextCaption else color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RectangleShape)
                    .background(HyprColors.BorderNormal)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}
