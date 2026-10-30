// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.LocalAppStrings
import com.example.ui.theme.Anton
import com.example.ui.theme.Hypr
import com.example.ui.theme.HyprSize
import com.example.ui.theme.InstrumentSerif
import com.example.ui.theme.Inter
import com.example.ui.theme.LinkState
import com.example.ui.theme.PlexMono

/*
 * HYPRLINK editorial kit for Compose — the Android twin of hyprlink-gui/src/ui.rs.
 *
 * Rules (same as the PC):
 *  · black, corners at 0, 1 dp hairlines, 4 dp grid, 20 dp side gutter on a phone
 *  · Anton for titles/numbers (uppercase), Instrument Serif italic for decks,
 *    Inter for interface, Plex Mono for labels and data
 *  · one accent: ACID. GREEN/GOLD/RED belong to the link state only.
 *  · on Android the OTHER side comes first: the PC, then this phone.
 *  · no emoji, no gradients, no shadows, no rounded corners.
 */

object HyprSpace {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
    /** Side gutter on a phone (the PC uses 40). */
    val Gutter = 20.dp
}

// ─────────────────────────────────────────────────────────── text ──

/** Small uppercase mono label: `§01`, `BATERIA`, `TX`. */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hypr.Muted,
    strong: Boolean = false,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        style = TextStyle(
            fontFamily = PlexMono,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = HyprSize.Label,
            letterSpacing = 0.1.em,
        ),
    )
}

/** Anton, uppercase, tight leading. */
@Composable
fun Headline(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = HyprSize.D2,
    color: Color = Hypr.Paper,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = Anton, fontSize = size, lineHeight = 0.98.em),
    )
}

/** The magazine voice: Instrument Serif italic. */
@Composable
fun Deck(
    text: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    color: Color = Hypr.Sub,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(
            fontFamily = InstrumentSerif,
            fontStyle = FontStyle.Italic,
            fontSize = if (small) HyprSize.DeckS else HyprSize.Deck,
            lineHeight = if (small) 1.2.em else 1.15.em,
        ),
    )
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Hypr.Sub) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(fontFamily = Inter, fontSize = HyprSize.Body, lineHeight = 1.5.em),
    )
}

/** Data in mono: values, hosts, hashes, timestamps. */
@Composable
fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hypr.Paper,
    size: TextUnit = HyprSize.Data,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = PlexMono, fontWeight = weight, fontSize = size),
    )
}

// ────────────────────────────────────────────────────────── rules ──

@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = Hypr.Line, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun VRule(modifier: Modifier = Modifier, color: Color = Hypr.Line) {
    Box(modifier.width(1.dp).fillMaxHeight().background(color))
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** A solid square — the HYPRLINK bullet. */
@Composable
fun Square(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).background(color, RectangleShape))
}

// ──────────────────────────────────────────────────── link state ──

/** Alpha that breathes on a 1.6 s cycle while connecting; 1.0 otherwise. */
@Composable
fun linkAlpha(state: LinkState): Float {
    if (state != LinkState.NEGOTIATING) return 1f
    val t = rememberInfiniteTransition(label = "linkBreath")
    val a by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "linkBreathAlpha",
    )
    return a
}

/** The LINK colour, animated between states (300 ms). */
@Composable
fun linkColor(state: LinkState): Color {
    val c by animateColorAsState(state.color, tween(300), label = "linkColor")
    return c
}

/**
 * The wordmark: HYPR in paper, LINK in the colour of the link.
 * Green connected · gold breathing while connecting · red disconnected.
 */
@Composable
fun HyprWordmark(
    state: LinkState,
    modifier: Modifier = Modifier,
    size: TextUnit = 40.sp,
    showState: Boolean = true,
    stateLabel: String = state.label,
) {
    val c = linkColor(state)
    val a = linkAlpha(state)
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "HYPR",
                color = Hypr.Paper,
                style = TextStyle(fontFamily = Anton, fontSize = size, lineHeight = 0.9.em),
            )
            Text(
                "LINK",
                modifier = Modifier.graphicsLayer { alpha = a },
                color = c,
                style = TextStyle(fontFamily = Anton, fontSize = size, lineHeight = 0.9.em),
            )
            Spacer(Modifier.width(6.dp))
            Square(c, Modifier.padding(bottom = 6.dp).graphicsLayer { alpha = a }, 9.dp)
        }
        if (showState) {
            Gap(6.dp)
            Kicker("■ $stateLabel", color = c, strong = true, modifier = Modifier.graphicsLayer { alpha = a })
        }
    }
}

/**
 * Unified screen header across screens.
 * Compact D3 headline with optional back button and right action kicker.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionColor: Color = Hypr.Sub
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Transparent, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = strings.back,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "←",
                    fontSize = 16.sp,
                    color = Hypr.Paper,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(HyprSpace.M))
        }

        Headline(
            text = title,
            size = HyprSize.D3,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        if (!actionLabel.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        enabled = onAction != null,
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { onAction?.invoke() }
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Kicker(
                    text = actionLabel,
                    strong = true,
                    color = actionColor
                )
            }
        }
    }
}

/**
 * Page opener — identical rhythm to the PC:
 *   §NN  TÍTULO ............ HYPRLINK
 *   ══════════════════════════ (2 dp paper)
 *   HEADLINE
 *   deck in italic
 */
@Deprecated("Substituído por ScreenHeader")
@Composable
fun Opener(
    num: String,
    title: String,
    headline: String,
    deck: String?,
    modifier: Modifier = Modifier,
    right: String = "HYPRLINK",
    onRightClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("§$num", color = Hypr.Acid, strong = true)
            Spacer(Modifier.width(HyprSpace.M))
            Kicker(title)
            Fill()
            Kicker(
                text = right,
                modifier = if (onRightClick != null) Modifier.clickable { onRightClick() } else Modifier
            )
        }
        Gap(HyprSpace.S)
        Rule(color = Hypr.Paper, thickness = 2.dp)
        Gap(HyprSpace.XL)
        Headline(headline, size = HyprSize.D2)
        if (deck != null) {
            Gap(HyprSpace.M)
            Deck(deck)
        }
        Gap(HyprSpace.XXL)
    }
}

/** `01 — Título ─────────` inside a page. */
@Composable
fun Subhead(
    num: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(num, color = Hypr.Acid, strong = true)
            Spacer(Modifier.width(HyprSpace.M))
            Text(
                title,
                color = Hypr.Paper,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = HyprSize.Body),
            )
            Fill()
            if (trailingAction != null) {
                trailingAction()
            } else if (trailing != null) {
                Kicker(trailing)
            }
        }
        Gap(HyprSpace.S)
        Rule()
        Gap(HyprSpace.L)
    }
}

/** Framed panel: INK_0, 1 dp hairline, corners at 0. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Hypr.Line,
    background: Color = Hypr.Ink0,
    padding: PaddingValues = PaddingValues(HyprSpace.L),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background, RectangleShape)
            .border(1.dp, border, RectangleShape)
            .padding(padding),
        content = content,
    )
}

// ────────────────────────────────────────────────────────── data ──

/** Big editorial number: label / VALUE unit. Unknown → pass "—". */
@Composable
fun Stat(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    color: Color = Hypr.Paper,
    size: TextUnit = HyprSize.D3,
) {
    Column(modifier) {
        Kicker(label)
        Gap(HyprSpace.S)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = color,
                style = TextStyle(fontFamily = Anton, fontSize = size, lineHeight = 1.0.em),
                maxLines = 1,
                softWrap = false
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Mono(
                    text = unit,
                    color = Hypr.Muted,
                    size = HyprSize.Small,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1
                )
            }
        }
    }
}

/** Key / value line with hairline below. */
@Composable
fun Kv(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Hypr.Paper) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Kicker(key, modifier = Modifier.weight(2f))
            Mono(value, color = valueColor, modifier = Modifier.weight(3f))
        }
        Rule()
    }
}

/** Solid tag: `LIGADO`, `TX`, `PROPOSTO`. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, background: Color = Hypr.Acid, color: Color = Hypr.Void) {
    Box(modifier.background(background, RectangleShape).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Kicker(text, color = color, strong = true)
    }
}

@Composable
fun TagOutline(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, color, RectangleShape).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Kicker(text, color = color, strong = true)
    }
}

/** Thin progress bar, 0..1. */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = Hypr.Acid, height: Dp = 3.dp) {
    Box(modifier.fillMaxWidth().height(height).background(Hypr.Ink2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(color))
    }
}

/** LED meter (mic/volume), `segments` cells, lit up to `level` 0..1. */
@Composable
fun Meter(level: Float, modifier: Modifier = Modifier, segments: Int = 24, color: Color = Hypr.Acid) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (level.coerceIn(0f, 1f) * segments).toInt()
        for (i in 0 until segments) {
            val c = when {
                i >= lit -> Hypr.Ink2
                i >= segments - 2 -> Hypr.Hot
                else -> color
            }
            drawRect(c, topLeft = Offset(i * (w + gap), 0f), size = Size(w, size.height))
        }
    }
}

/** Sparkline: thin line, last point marked with a square. */
@Composable
fun Spark(values: List<Float>, modifier: Modifier = Modifier, color: Color = Hypr.Acid, height: Dp = 32.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max().let { if (it - lo < 1e-3f) lo + 1f else it }
        val dx = size.width / (values.size - 1)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        val p = Path()
        values.forEachIndexed { i, v -> if (i == 0) p.moveTo(0f, y(v)) else p.lineTo(i * dx, y(v)) }
        drawPath(p, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter))
        val s = 5.dp.toPx()
        drawRect(color, Offset(size.width - s, y(values.last()) - s / 2), Size(s, s))
    }
}

/** Signal bars, 0..4. */
@Composable
fun SignalBars(bars: Int?, modifier: Modifier = Modifier, color: Color = Hypr.Paper) {
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(
                Modifier.width(4.dp).height((3 + i * 2.75f).dp)
                    .background(if (bars != null && i <= bars) color else Hypr.Ink2)
            )
        }
    }
}

// ─────────────────────────────────────────────────────── controls ──

enum class HyprButtonKind { Primary, Ghost, Danger, Bare }

/** Square, mono, uppercase. Primary = acid block; Ghost = hairline; Danger = hot hairline. */
@Composable
fun HyprButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: HyprButtonKind = HyprButtonKind.Ghost,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (bg, fg, line) = when {
        !enabled -> Triple(Hypr.Ink1, Hypr.Faint, Hypr.Line)
        kind == HyprButtonKind.Primary -> if (pressed) Triple(Hypr.AcidDim, Hypr.Void, Hypr.AcidDim) else Triple(Hypr.Acid, Hypr.Void, Hypr.Acid)
        kind == HyprButtonKind.Danger -> if (pressed) Triple(Hypr.Hot, Hypr.Void, Hypr.Hot) else Triple(Color.Transparent, Hypr.Hot, Hypr.Hot)
        kind == HyprButtonKind.Bare -> Triple(Color.Transparent, if (pressed) Hypr.Acid else Hypr.Sub, Color.Transparent)
        else -> if (pressed) Triple(Hypr.Ink1, Hypr.Acid, Hypr.Acid) else Triple(Color.Transparent, Hypr.Paper, Hypr.Muted)
    }
    Box(
        modifier
            .background(bg, RectangleShape)
            .border(1.dp, line, RectangleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            color = fg,
            maxLines = 1,
            style = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.08.em),
        )
    }
}

/** Filter chip: selected = paper block with black text (or acid when acidSelected=true). */
@Composable
fun HyprChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    acidSelected: Boolean = false,
    maxLines: Int = 1
) {
    val bg = when {
        selected && acidSelected -> Hypr.Acid
        selected -> Hypr.Paper
        else -> Color.Transparent
    }
    val fg = when {
        selected && acidSelected -> Hypr.Void
        selected -> Hypr.Void
        else -> Hypr.Sub
    }
    val line = when {
        selected && acidSelected -> Hypr.Acid
        selected -> Hypr.Paper
        else -> Hypr.Muted
    }
    Box(
        modifier
            .background(bg, RectangleShape)
            .border(1.dp, line, RectangleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label.uppercase(), color = fg, maxLines = maxLines, style = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp))
    }
}

/** Square switch: acid track + black knob when on. */
@Composable
fun HyprSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track = when { !enabled -> Hypr.Ink1; checked -> Hypr.Acid; else -> Hypr.Ink1 }
    val knob = when { !enabled -> Hypr.Faint; checked -> Hypr.Void; else -> Hypr.Sub }
    val knobX by androidx.compose.animation.core.animateDpAsState(if (checked) 18.dp else 2.dp, tween(140), label = "knob")
    Box(
        modifier
            .size(width = 38.dp, height = 22.dp)
            .background(track, RectangleShape)
            .border(1.dp, if (checked) Hypr.Acid else Hypr.Muted, RectangleShape)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
    ) {
        Box(Modifier.offset(x = knobX, y = 2.dp).size(18.dp).background(knob, RectangleShape))
    }
}

/** Setting line: title + explanation left, control right, hairline below. */
@Composable
fun Setting(title: String, sub: String?, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Hypr.Paper, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = HyprSize.Body))
                if (sub != null) {
                    Gap(3.dp)
                    Mono(sub, color = Hypr.Muted, size = 11.sp, maxLines = 3)
                }
            }
            Spacer(Modifier.width(HyprSpace.L))
            control()
        }
        Rule()
    }
}

/** "PROPOSTO" banner for features the daemon doesn't have yet. */
@Composable
fun ProposedBanner(text: String, modifier: Modifier = Modifier, label: String = "PROPOSTO") {
    Row(
        modifier.fillMaxWidth().background(Hypr.WaitTint).border(1.dp, Hypr.BorderAmber).padding(HyprSpace.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag(label, background = Hypr.LinkWait, color = Hypr.Void)
        Spacer(Modifier.width(HyprSpace.M))
        Mono(text, color = Hypr.Sub, size = 11.5.sp, maxLines = 3)
    }
}

// ───────────────────────────────────────────────────── navigation ──

data class HyprNavItem(val route: String, val num: String, val label: String)

/**
 * Bottom navigation as a magazine index: number + word, no icons.
 * Selected = acid bar on top, paper text. 1 dp hairline above.
 */
@Composable
fun HyprBottomNav(
    items: List<HyprNavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Hypr.Void)
            .drawBehind { drawRect(Hypr.Line, size = Size(size.width, 1.dp.toPx())) }
            .height(58.dp)
    ) {
        items.forEach { item ->
            val on = item.route == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Tab) { onSelect(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) Hypr.Acid else Color.Transparent))
                Gap(9.dp)
                Text(
                    item.num,
                    color = if (on) Hypr.Acid else Hypr.Faint,
                    style = TextStyle(fontFamily = Anton, fontSize = 18.sp, lineHeight = 1.0.em),
                )
                Gap(3.dp)
                Kicker(item.label, color = if (on) Hypr.Paper else Hypr.Muted, strong = on)
            }
        }
    }
}

/** Masthead strip for the top of every screen: wordmark + state + device. */
/** Masthead strip for the top of every screen: wordmark + state + device. */
@Composable
fun HyprMasthead(
    state: LinkState,
    device: String?,
    modifier: Modifier = Modifier,
    stateLabel: String = state.label,
    onDeviceClick: (() -> Unit)? = null,
    large: Boolean = false,
    subtitle: String? = null,
    subtitleColor: Color = Hypr.Sub,
    pathBadge: String? = null
) {
    val c = linkColor(state)
    val a = linkAlpha(state)
    val stateDescription = "HYPRLINK, $stateLabel" + if (device != null) ", $device" else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HyprSpace.Gutter)
            .semantics { contentDescription = stateDescription }
    ) {
        Gap(HyprSpace.M)
        if (!large) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HYPR", color = Hypr.Paper, style = TextStyle(fontFamily = Anton, fontSize = 22.sp, lineHeight = 1.0.em))
                Text("LINK", color = c, modifier = Modifier.graphicsLayer { alpha = a }, style = TextStyle(fontFamily = Anton, fontSize = 22.sp, lineHeight = 1.0.em))
                Spacer(Modifier.width(HyprSpace.M))
                Square(c, Modifier.graphicsLayer { alpha = a }, 7.dp)
                Spacer(Modifier.width(6.dp))
                Kicker(stateLabel, color = c, strong = true, modifier = Modifier.graphicsLayer { alpha = a })
                if (state == LinkState.CONNECTED && !pathBadge.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    TagOutline(
                        text = pathBadge,
                        color = if (pathBadge.equals("USB", ignoreCase = true)) Hypr.Acid else Hypr.Sub
                    )
                }
                Fill()
                if (device != null) {
                    Kicker(
                        "$device ▾",
                        color = Hypr.Sub,
                        modifier = if (onDeviceClick != null) Modifier.clickable(onClick = onDeviceClick) else Modifier,
                    )
                }
            }
        } else {
            // Cabeçalho expandido exclusivo do Início
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logótipo grande (≈ 38-40 sp em Anton) + Indicador de estado
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HYPR", color = Hypr.Paper, style = TextStyle(fontFamily = Anton, fontSize = 38.sp, lineHeight = 1.0.em))
                        Text("LINK", color = c, modifier = Modifier.graphicsLayer { alpha = a }, style = TextStyle(fontFamily = Anton, fontSize = 38.sp, lineHeight = 1.0.em))
                        Spacer(Modifier.width(8.dp))
                        Square(c, Modifier.graphicsLayer { alpha = a }, 8.dp)
                    }
                    if (state == LinkState.CONNECTED && !pathBadge.isNullOrBlank()) {
                        TagOutline(
                            text = pathBadge,
                            color = if (pathBadge.equals("USB", ignoreCase = true)) Hypr.Acid else Hypr.Sub
                        )
                    } else if (state != LinkState.CONNECTED) {
                        Kicker(stateLabel, color = c, strong = true, modifier = Modifier.graphicsLayer { alpha = a })
                    }
                }

                // Dispositivo + RTT/Subtítulo à direita
                if (device != null) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 8.dp)
                            .then(if (onDeviceClick != null) Modifier.clickable(onClick = onDeviceClick) else Modifier)
                    ) {
                        Text(
                            text = "$device ▾",
                            color = Hypr.Paper,
                            style = TextStyle(fontFamily = Anton, fontSize = 28.sp, lineHeight = 1.05.em),
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Gap(2.dp)
                            Text(
                                text = subtitle,
                                color = subtitleColor,
                                style = TextStyle(
                                    fontFamily = InstrumentSerif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 15.sp,
                                    lineHeight = 1.1.em
                                ),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Gap(HyprSpace.M)
        Rule()
    }
}
