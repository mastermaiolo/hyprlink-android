// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.LocalAppStrings
import com.example.ui.theme.*
import com.example.ui.kit.*

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.PcPlayerLogic

data class AudioSinkItem(
    val id: Long,
    val name: String,
    val description: String,
    val volume: Int, // 0 to 150
    val isMuted: Boolean,
    val isDefault: Boolean,
    val isPhone: Boolean = false
)

data class AudioAppItem(
    val id: Long,
    val name: String,
    val media: String,
    val volume: Int,
    val isMuted: Boolean,
    val isPlaying: Boolean = true,
    val idleMinutes: Int = 0
)

@Composable
fun AudioScreen(
    onBack: () -> Unit,
    mediaAppSource: String = "",
    mediaTitle: String = "",
    mediaArtist: String = "",
    artworkBitmap: Bitmap? = null,
    elapsedTime: String? = null,
    totalTime: String? = null,
    isPlaying: Boolean = false,
    progress: Float? = null,
    onPrevClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    isListenOnPhone: Boolean = false,
    onToggleListenOnPhone: (Boolean) -> Unit = {},
    sinks: List<AudioSinkItem> = emptyList(),
    onSelectDefaultSink: (String) -> Unit = {},
    onSinkVolumeChange: (Long, Int) -> Unit = { _, _ -> },
    onSinkMuteToggle: (Long, Boolean) -> Unit = { _, _ -> },
    apps: List<AudioAppItem> = emptyList(),
    onAppVolumeChange: (Long, Int) -> Unit = { _, _ -> },
    onAppMuteToggle: (Long, Boolean) -> Unit = { _, _ -> },
    isMicOn: Boolean = false,
    onToggleMic: (Boolean) -> Unit = {},
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedSink by remember(strings) { mutableStateOf(strings.thisPhoneUpper) }
    val displayApps = apps
    val hasMedia = mediaTitle.isNotBlank() && mediaTitle != strings.noActivePlayback

    // Desempenho: memorizar bitmap reduzido para o fundo (<= 720 px)
    val backgroundBitmap = remember(artworkBitmap, hasMedia) {
        if (hasMedia && artworkBitmap != null) {
            if (artworkBitmap.width > 720 || artworkBitmap.height > 720) {
                val (w, h) = PcPlayerLogic.calculateDownscaledSize(artworkBitmap.width, artworkBitmap.height, 720)
                Bitmap.createScaledBitmap(artworkBitmap, w, h, true)
            } else {
                artworkBitmap
            }
        } else {
            null
        }
    }

    val sdkInt = android.os.Build.VERSION.SDK_INT
    val veilConfig = remember(sdkInt) { PcPlayerLogic.resolveVeilConfig(sdkInt) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
    ) {
        // Fundo fixo com transição (crossfade de 300 ms)
        AnimatedVisibility(
            visible = hasMedia && backgroundBitmap != null,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            if (backgroundBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val blurModifier = Modifier.blur(32.dp)
                    Image(
                        bitmap = backgroundBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier)
                    )
                    // Véu escuro por cima (tokens do tema Hypr.Void)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Hypr.Void.copy(alpha = veilConfig.topAlpha),
                                        veilConfig.midPoint to Hypr.Void.copy(alpha = veilConfig.midAlpha),
                                        veilConfig.lowerPoint to Hypr.Void.copy(alpha = veilConfig.lowerAlpha),
                                        1.0f to Hypr.Void.copy(alpha = veilConfig.bottomAlpha)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Conteúdo scrollable por cima do fundo fixo
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HyprSpace.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader(
                title = strings.audioTitle,
                onBack = onBack
            )

            // A tocar no PC
            if (hasMedia) {
                // Capa nítida (se existir artworkBitmap)
                if (artworkBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = artworkBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 290.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RectangleShape,
                                    clip = false,
                                    ambientColor = Color.Black.copy(alpha = 0.55f),
                                    spotColor = Color.Black.copy(alpha = 0.55f)
                                )
                        )
                    }
                    Gap(4.dp)
                }

                val kickerText = if (mediaAppSource.isNotBlank()) {
                    "${strings.playingOnPc} · ${mediaAppSource.uppercase()}"
                } else {
                    strings.playingOnPc
                }
                Text(
                    text = kickerText.uppercase(),
                    color = Hypr.Sub.copy(alpha = 0.75f),
                    style = TextStyle(
                        fontFamily = PlexMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em
                    )
                )

                Text(
                    text = mediaTitle.uppercase(),
                    color = Hypr.Paper,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = Anton,
                        fontSize = 28.sp,
                        lineHeight = 30.sp
                    )
                )

                if (mediaArtist.isNotBlank()) {
                    Text(
                        text = mediaArtist,
                        color = Hypr.Sub.copy(alpha = 0.85f),
                        style = TextStyle(
                            fontFamily = InstrumentSerif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = HyprSize.Deck,
                            lineHeight = 1.15.em
                        )
                    )
                }

                val currentProgress = (progress ?: 0f).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(11.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Hypr.Paper.copy(alpha = 0.25f), RectangleShape)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = currentProgress)
                                .height(3.dp)
                                .background(Hypr.Acid, RectangleShape)
                        )
                        if (currentProgress > 0f) {
                            Box(
                                modifier = Modifier.fillMaxWidth(fraction = currentProgress),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(11.dp)
                                        .background(Hypr.Acid, RectangleShape)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Mono(text = elapsedTime ?: "0:00", color = Hypr.Muted, size = 10.sp)
                        Mono(text = totalTime ?: "--:--", color = Hypr.Muted, size = 10.sp)
                    }
                }

                Gap(4.dp)

                // Controlos do leitor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.dp, Hypr.LineStrong, RectangleShape)
                            .clickable(onClick = onPrevClick)
                            .semantics { contentDescription = strings.btnPrevious },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(com.example.R.drawable.ic_media_prev),
                            contentDescription = strings.btnPrevious,
                            tint = Hypr.Paper,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Hypr.Acid, RectangleShape)
                            .clickable(onClick = onPlayPauseClick)
                            .semantics { contentDescription = if (isPlaying) strings.btnPause else strings.btnPlay },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) com.example.R.drawable.ic_media_pause else com.example.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) strings.btnPause else strings.btnPlay,
                            tint = Hypr.Void,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.dp, Hypr.LineStrong, RectangleShape)
                            .clickable(onClick = onNextClick)
                            .semantics { contentDescription = strings.btnNext },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(com.example.R.drawable.ic_media_next),
                            contentDescription = strings.btnNext,
                            tint = Hypr.Paper,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Rule(color = Hypr.Line)
            } else {
                Kicker(strings.playingOnPcNone)
                Gap(10.dp)
                Headline(text = strings.noPlayerHeader, size = 34.sp, color = Hypr.Faint)
                Gap(8.dp)
                Deck(text = strings.noPlayerDesc, small = true, color = Hypr.Muted)
                Rule(color = Hypr.Line)
            }

            Gap(8.dp)

            // 01. Saída de som
        Subhead(num = "01", title = strings.soundOutput)
        Setting(
            title = strings.listenOnPhone,
            sub = if (!isConnected) strings.needsPcConnection else strings.listenOnPhoneDesc,
            modifier = if (!isConnected) Modifier.alpha(0.5f) else Modifier,
            control = {
                HyprSwitch(
                    checked = isListenOnPhone && isConnected,
                    onCheckedChange = onToggleListenOnPhone,
                    enabled = isConnected
                )
            }
        )
        if (isListenOnPhone && isConnected) {
            Gap(8.dp)
            Meter(level = 0.85f)
            Gap(8.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sinks.isEmpty()) {
                HyprChip(
                    label = strings.thisPhoneUpper,
                    selected = true,
                    onClick = {}
                )
            } else {
                sinks.forEach { sink ->
                    val displayName = sink.description.ifBlank { sink.name.uppercase() }
                    val isSelected = selectedSink == displayName || sink.isDefault
                    HyprChip(
                        label = displayName,
                        selected = isSelected,
                        maxLines = 2,
                        onClick = {
                            selectedSink = displayName
                            onSelectDefaultSink(sink.name)
                        }
                    )
                }
            }
        }

        Gap(8.dp)

        // 02. Volume por aplicação
        Subhead(num = "02", title = strings.appVolume, trailing = if (displayApps.isEmpty()) "—" else "${displayApps.size} APPS")
        if (displayApps.isEmpty()) {
            Deck(text = strings.noAppAudio, color = Hypr.Muted, small = true)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                displayApps.forEach { app ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.name,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Hypr.Paper)
                            )
                            Fill()
                            Mono("${app.volume} %", color = Hypr.Paper, size = 12.sp)
                        }
                        Gap(6.dp)
                        AudioCustomSlider(
                            volume = app.volume,
                            isActive = app.isPlaying && !app.isMuted,
                            onVolumeChange = { onAppVolumeChange(app.id, it) }
                        )
                        Gap(4.dp)
                        val statusText = when {
                            app.isMuted -> strings.audioMuted
                            !app.isPlaying && app.idleMinutes > 0 -> String.format(java.util.Locale.getDefault(), strings.audioIdleForMin, app.idleMinutes)
                            !app.isPlaying -> strings.audioIdle
                            else -> strings.audioPlaying
                        }
                        val statusColor = if (app.isMuted) Hypr.Hot else if (app.isPlaying) Hypr.Acid else Hypr.Sub
                        Kicker(statusText, color = statusColor)
                        Gap(8.dp)
                        Rule()
                    }
                }
            }
        }

        Gap(8.dp)

        // 03. Microfone → PC
        Subhead(num = "03", title = strings.micToPc, trailing = if (isMicOn) "WEBCAM.MIC" else null)
        Setting(
            title = strings.sendMicTitle,
            sub = if (!isConnected) strings.needsPcConnection else strings.sendMicSub,
            modifier = if (!isConnected) Modifier.alpha(0.5f) else Modifier,
            control = {
                HyprSwitch(
                    checked = isMicOn && isConnected,
                    onCheckedChange = onToggleMic,
                    enabled = isConnected
                )
            }
        )
        if (isMicOn && isConnected) {
            Gap(8.dp)
            Meter(level = 0.65f)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
}

@Composable
fun AudioCustomSlider(
    volume: Int, // 0 to 150
    isActive: Boolean,
    onVolumeChange: (Int) -> Unit
) {
    var sliderVal by remember(volume) { mutableFloatStateOf(volume.toFloat()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val width = size.width
                    if (width > 0f) {
                        val x = change.position.x.coerceIn(0f, width.toFloat())
                        val pct = (x / width) * 150f
                        sliderVal = pct
                        onVolumeChange(pct.toInt())
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Background track (3.dp height, Hypr.Ink2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Hypr.Ink2, RectangleShape)
        ) {
            val fraction = (sliderVal / 150f).coerceIn(0f, 1f)
            val fillColor = if (isActive) Hypr.Acid else Hypr.Muted
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(fillColor, RectangleShape)
            )
        }

        // Square thumb (14.dp, Hypr.Paper, no shadow)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fraction = (sliderVal / 150f).coerceIn(0f, 1f)
            val thumbX = (maxWidth * fraction) - 7.dp
            Box(
                modifier = Modifier
                    .offset(x = thumbX)
                    .size(14.dp)
                    .background(Hypr.Paper, RectangleShape)
            )
        }
    }
}
