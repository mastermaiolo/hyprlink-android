// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * HYPRLINK design tokens — the same values as the PC GUI (hyprlink-gui/src/theme.rs).
 * Black, editorial, one accent (ACID). Never pure white for text: PAPER.
 * GREEN / GOLD / RED are reserved for the state of the link.
 */
object Hypr {
    // Surfaces
    val Void = Color(0xFF000000)
    val Ink0 = Color(0xFF080808)
    val Ink1 = Color(0xFF0E0E0E)
    val Ink2 = Color(0xFF161616)

    // Hairlines
    val Line = Color(0xFF1C1C1C)
    val LineStrong = Color(0xFF2C2C2C)

    // Text
    val Paper = Color(0xFFEDEBE4)
    val Sub = Color(0xFFA3A19A)
    val Muted = Color(0xFF807F7A)
    val Faint = Color(0xFF3D3C3A)

    // The one accent
    val Acid = Color(0xFFD4FF3A)
    val AcidDim = Color(0xFF5B6B1E)

    // Alarm
    val Hot = Color(0xFFFF3355)
    val BorderRed = Color(0xFF401019)
    val BorderAmber = Color(0xFF3D2E0C)

    // RX traffic only
    val Cold = Color(0xFF5FD4E8)

    // Luz de ecrã para câmara frontal (prompt 73)
    val WarmLight = Color(0xFFFFE4B5)

    // Link state — the LINK word, the masthead square, the notification dot
    val LinkUp = Color(0xFF2BE07A)   // ligado
    val LinkWait = Color(0xFFF4B731) // a ligar
    val LinkDown = Hot              // desligado

    // 6 % tints over black, for state-coloured blocks
    val AcidTint = Color(0xFF0D0F03)
    val WaitTint = Color(0xFF0F0B03)
    val HotTint = Color(0xFF0F0305)
}

/**
 * Legacy names used across the app. Kept so nothing breaks; the values now
 * point at the HYPRLINK tokens. New code should use [Hypr] directly.
 */
object HyprColors {
    // Surfaces
    val Background = Hypr.Void
    val SurfaceCard = Hypr.Ink0
    val SurfaceElevated = Hypr.Ink1
    val TerminalSurface = Hypr.Ink0
    val ClipboardBlock = Hypr.Ink0

    // Borders
    val BorderNormal = Hypr.Line
    val BorderElevated = Hypr.Line
    val BorderHighlight = Hypr.LineStrong
    val BorderTouchpad = Hypr.LineStrong
    val BorderAmber = Color(0xFF3D2E0C)
    val BorderRed = Color(0xFF401019)

    // Status colours. "NeonGreen" was the accent everywhere → now ACID.
    val NeonGreen = Hypr.Acid
    val Amber = Hypr.LinkWait
    val Red = Hypr.Hot
    val InactiveGray = Hypr.Muted
    val SubInactiveGray = Hypr.Sub

    // Tinted surfaces
    val GreenTintSurface = Hypr.AcidTint
    val AmberTintSurface = Hypr.WaitTint
    val RedTintSurface = Hypr.HotTint
    val RedTintBlock = Hypr.HotTint

    // Text
    val TextTitle = Hypr.Paper
    val TextBody = Hypr.Paper
    val TextSecondary = Hypr.Sub
    val TextCaption = Hypr.Muted
    val TextLog = Color(0xFF55544F)
    val TextInstruction = Hypr.Faint

    // On the accent
    val OnNeonGreen = Hypr.Void
}

enum class LinkState {
    DISCONNECTED,
    NEGOTIATING,
    CONNECTED;

    /** The LINK colour: green / gold / red. Same as the PC GUI. */
    val color: Color
        get() = when (this) {
            CONNECTED -> Hypr.LinkUp
            NEGOTIATING -> Hypr.LinkWait
            DISCONNECTED -> Hypr.LinkDown
        }

    val label: String
        get() = when (this) {
            CONNECTED -> "LIGADO"
            NEGOTIATING -> "A LIGAR…"
            DISCONNECTED -> "DESLIGADO"
        }

    fun getLocalizedLabel(strings: com.example.AppStrings): String = when (this) {
        CONNECTED -> strings.linkActive
        NEGOTIATING -> strings.negotiatingTls
        DISCONNECTED -> strings.noConnection
    }

    val buttonText: String
        get() = when (this) {
            CONNECTED -> "ON"
            NEGOTIATING -> "···"
            DISCONNECTED -> "LIGAR"
        }

    fun getLocalizedButtonText(strings: com.example.AppStrings): String = when (this) {
        CONNECTED -> strings.btnOn
        NEGOTIATING -> "···"
        DISCONNECTED -> strings.btnConnect
    }
}

enum class ServiceState {
    ACTIVE,
    PENDING,
    INACTIVE,
    ERROR;

    val color: Color
        get() = when (this) {
            ACTIVE -> Hypr.Acid
            PENDING -> Hypr.LinkWait
            INACTIVE -> Hypr.Muted
            ERROR -> Hypr.Hot
        }

    val label: String
        get() = when (this) {
            ACTIVE -> "ativo"
            PENDING -> "pendente"
            INACTIVE -> "inativo"
            ERROR -> "sem permissão"
        }

    fun getLocalizedLabel(strings: com.example.AppStrings): String = when (this) {
        ACTIVE -> strings.active
        PENDING -> strings.permissionPending.lowercase()
        INACTIVE -> strings.inactive
        ERROR -> strings.permissionPending.lowercase()
    }
}

/**
 * Status mark. HYPRLINK uses squares, not circles, and does not scale:
 * only the alpha breathes. Same signature as before.
 */
@Composable
fun BreathingDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    periodMs: Int = 1600
) {
    val half = (periodMs / 2).coerceAtLeast(100)
    val transition = rememberInfiniteTransition(label = "BreathingDot")
    val a by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = half, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingAlpha"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = a }
            .background(color, RectangleShape)
    )
}
