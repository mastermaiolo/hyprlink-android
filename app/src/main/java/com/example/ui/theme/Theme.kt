// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.LocalAppStrings
import com.example.rememberAppStrings

// Compatibility aliases for legacy references
val DarkBackground = Hypr.Void
val DarkSurface = Hypr.Ink0
val DarkOutline = Hypr.Line
val PrimaryPurple = Hypr.Acid
val PrimaryViolet = Hypr.LineStrong
val CyanActive = Hypr.LinkUp      // only ever used for "connected"
val AmberWarning = Hypr.LinkWait
val RedError = Hypr.Hot
val TextPrimary = Hypr.Paper
val TextSecondary = Hypr.Sub
val TerminalBlack = Hypr.Ink0

private val DarkColorScheme = darkColorScheme(
    primary = Hypr.Acid,
    onPrimary = Hypr.Void,
    primaryContainer = Hypr.Ink1,
    onPrimaryContainer = Hypr.Paper,
    secondary = Hypr.Paper,
    onSecondary = Hypr.Void,
    secondaryContainer = Hypr.Ink2,
    onSecondaryContainer = Hypr.Paper,
    tertiary = Hypr.LinkWait,
    onTertiary = Hypr.Void,
    error = Hypr.Hot,
    onError = Hypr.Void,
    background = Hypr.Void,
    onBackground = Hypr.Paper,
    surface = Hypr.Void,
    onSurface = Hypr.Paper,
    surfaceVariant = Hypr.Ink1,
    onSurfaceVariant = Hypr.Sub,
    surfaceContainerLowest = Hypr.Void,
    surfaceContainerLow = Hypr.Ink0,
    surfaceContainer = Hypr.Ink0,
    surfaceContainerHigh = Hypr.Ink1,
    surfaceContainerHighest = Hypr.Ink2,
    outline = Hypr.LineStrong,
    outlineVariant = Hypr.Line,
    scrim = Hypr.Void,
)

/** Corners at zero, everywhere: dialogs, sheets, cards, buttons. */
private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(CornerSize(0)),
    small = RoundedCornerShape(CornerSize(0)),
    medium = RoundedCornerShape(CornerSize(0)),
    large = RoundedCornerShape(CornerSize(0)),
    extraLarge = RoundedCornerShape(CornerSize(0)),
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val appStrings = rememberAppStrings()
    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            shapes = SquareShapes,
            content = content
        )
    }
}
