// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.R

/*
 * HYPRLINK type — the four families of the PC GUI, embedded (SIL OFL 1.1).
 *
 *   Anton            titles and numbers, ALWAYS UPPERCASE
 *   Instrument Serif italic — the "deck", the magazine voice
 *   Inter            interface text
 *   IBM Plex Mono    labels, data, protocol
 *
 * Every weight the app asks for is mapped to a real file, so Compose never
 * fakes a bold (Anton has one weight; Bold/Black point to it too).
 */

val Anton = FontFamily(
    Font(R.font.anton_regular, FontWeight.Normal),
    Font(R.font.anton_regular, FontWeight.Medium),
    Font(R.font.anton_regular, FontWeight.SemiBold),
    Font(R.font.anton_regular, FontWeight.Bold),
    Font(R.font.anton_regular, FontWeight.ExtraBold),
    Font(R.font.anton_regular, FontWeight.Black),
)

val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold, FontWeight.ExtraBold),
    Font(R.font.inter_bold, FontWeight.Black),
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_semibold, FontWeight.Bold),
    Font(R.font.plex_mono_semibold, FontWeight.ExtraBold),
    Font(R.font.plex_mono_semibold, FontWeight.Black),
)

// ── Legacy names used across the app (kept so nothing breaks) ──────────────
val ArchivoBlack = Anton
val JetBrainsMono = PlexMono
val ArchivoLogoFont = Anton
val ArchivoModuleFont = PlexMono

private val NoFake = FontSynthesis.None

val LogoTextStyle = TextStyle(
    fontFamily = Anton,
    fontSize = 48.sp,
    lineHeight = 0.98.em,
    fontSynthesis = NoFake,
)

val ModuleTitleTextStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = 0.08.em,
    fontSynthesis = NoFake,
)

/** Type scale (sp). Mirrors `size::*` in the PC GUI, scaled for a phone. */
object HyprSize {
    val Masthead = 64.sp
    val D1 = 56.sp
    val D2 = 44.sp
    val D3 = 32.sp
    val H1 = 24.sp
    val H2 = 18.sp
    val Deck = 21.sp
    val DeckS = 17.sp
    val Body = 14.sp
    val Small = 12.5.sp
    val Label = 10.5.sp
    val Data = 12.5.sp
}

val Typography = Typography(
    // Anton — big words and numbers
    displayLarge = TextStyle(fontFamily = Anton, fontSize = HyprSize.D1, lineHeight = 0.98.em, fontSynthesis = NoFake),
    displayMedium = TextStyle(fontFamily = Anton, fontSize = HyprSize.D2, lineHeight = 0.98.em, fontSynthesis = NoFake),
    displaySmall = TextStyle(fontFamily = Anton, fontSize = HyprSize.D3, lineHeight = 1.0.em, fontSynthesis = NoFake),
    headlineLarge = TextStyle(fontFamily = Anton, fontSize = 30.sp, lineHeight = 1.0.em, fontSynthesis = NoFake),
    headlineMedium = TextStyle(fontFamily = Anton, fontSize = HyprSize.H1, lineHeight = 1.0.em, fontSynthesis = NoFake),
    headlineSmall = TextStyle(fontFamily = Anton, fontSize = 20.sp, lineHeight = 1.05.em, fontSynthesis = NoFake),
    // Inter — interface
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, fontSynthesis = NoFake),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, fontSynthesis = NoFake),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, fontSynthesis = NoFake),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, fontSynthesis = NoFake),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = HyprSize.Body, lineHeight = 21.sp, fontSynthesis = NoFake),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp, fontSynthesis = NoFake),
    // Plex Mono — labels, data
    labelLarge = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.06.em, fontSynthesis = NoFake),
    labelMedium = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.08.em, fontSynthesis = NoFake),
    labelSmall = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = HyprSize.Label, letterSpacing = 0.1.em, fontSynthesis = NoFake),
)
