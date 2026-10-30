// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ConnectionRepository
import com.example.LinkPathType
import com.example.LocalAppStrings
import com.example.WebcamStreamer
import com.example.ui.kit.*
import com.example.ui.theme.Anton
import com.example.ui.theme.Hypr
import com.example.ui.theme.PlexMono
import kotlinx.coroutines.delay

@Composable
fun WebcamFullscreenDialog(
    deviceName: String,
    lensLabel: String,
    rotationDeg: Int,
    mirrored: Boolean,
    codecPreference: String,
    activeCodec: String,
    isMicOn: Boolean,
    isTorchOn: Boolean,
    zoomRatio: Float,
    currentWidth: Int,
    currentHeight: Int,
    currentFps: Int,
    micPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val sharedPrefs = remember { context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isScreenOff by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableStateOf(zoomRatio.coerceIn(0.5f, 10.0f)) }

    // Focus indicator state
    var focusPointOffset by remember { mutableStateOf<Offset?>(null) }
    var showFocusSquare by remember { mutableStateOf(false) }

    LaunchedEffect(zoomRatio) {
        currentZoom = zoomRatio.coerceIn(0.5f, 10.0f)
    }

    // Supported resolutions (max 4 common ones)
    val supportedResolutions = remember(context) {
        val all = WebcamStreamer.getSupportedResolutions(context)
        val filtered = all.filter {
            val w = it["width"] as? Int ?: 0
            val h = it["height"] as? Int ?: 0
            (w == 640 && h == 480) ||
            (w == 1280 && h == 720) ||
            (w == 1920 && h == 1080) ||
            (w == 3840 && h == 2160)
        }
        val targetList = if (filtered.isNotEmpty()) filtered else all
        targetList.take(4)
    }

    // Active resolution string
    val activeResString = "${currentWidth}x${currentHeight}"

    // Formato efetivo da câmara
    val pLabel = when (currentHeight) {
        720 -> "720P"
        1080 -> "1080P"
        480 -> "480P"
        2160 -> "4K"
        else -> "${currentWidth}×${currentHeight}"
    }
    val codecLabel = if (activeCodec.isNotEmpty()) activeCodec else "H.264"
    val effectiveHeader = "$pLabel · $currentFps FPS · $codecLabel"

    val isScreenLightOn by WebcamStreamer.isScreenLightOn.collectAsStateWithLifecycle()
    val isFrontCamera = remember(lensLabel) {
        lensLabel.contains("FRONTAL", ignoreCase = true)
    }

    val activity = context as? Activity
    DisposableEffect(isScreenLightOn) {
        if (isScreenLightOn) {
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = 1.0f
                window.attributes = lp
            }
        } else {
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
        onDispose {
            if (isScreenLightOn) {
                activity?.window?.let { window ->
                    val lp = window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (WebcamStreamer.isScreenLightOn.value) {
                WebcamStreamer.toggleScreenLight(false)
            }
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isScreenLightOn) Hypr.WarmLight else Hypr.Void)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // 1. Barra de topo com separador nítido
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tag(text = "● " + strings.tagLive, background = Hypr.Hot, color = Hypr.Void)
                Spacer(Modifier.width(8.dp))
                Kicker(text = "$effectiveHeader → /dev/video42", color = if (isScreenLightOn) Hypr.Ink0 else Hypr.Paper)
                Spacer(Modifier.width(6.dp))
                Kicker(text = "·", color = if (isScreenLightOn) Hypr.LineStrong else Hypr.Muted)
                Spacer(Modifier.width(6.dp))
                Kicker(
                    text = deviceName.uppercase(),
                    color = if (isScreenLightOn) Hypr.Ink0 else Hypr.Acid,
                    strong = true
                )
                Spacer(Modifier.width(8.dp))
                val linkPath by ConnectionRepository.currentLinkPath.collectAsStateWithLifecycle()
                TagOutline(
                    text = if (linkPath == LinkPathType.USB) strings.chipUsb else strings.chipWifi,
                    color = if (linkPath == LinkPathType.USB) Hypr.Acid else (if (isScreenLightOn) Hypr.Ink0 else Hypr.Sub)
                )
            }

            val isHighSpeedMode by WebcamStreamer.isHighSpeedMode.collectAsStateWithLifecycle()

            // 2. Pré-visualização com cantos a 0 e borda 1 dp Hypr.Line — weight(1f) expansivo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Hypr.Void, RectangleShape)
                    .border(1.dp, Hypr.Line, RectangleShape)
            ) {
                if (isHighSpeedMode) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                WebcamStreamer.attachHighSpeedTextureView(this)
                            }
                        },
                        onRelease = {
                            WebcamStreamer.detachHighSpeedTextureView()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    currentZoom = (currentZoom * zoom).coerceIn(0.5f, 10.0f)
                                    WebcamStreamer.setZoomRatio(currentZoom)
                                }
                            }
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView = it }.apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                WebcamStreamer.attachPreview(this.surfaceProvider)
                            }
                        },
                        onRelease = {
                            previewView = null
                            WebcamStreamer.detachPreview()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    currentZoom = (currentZoom * zoom).coerceIn(0.5f, 10.0f)
                                    WebcamStreamer.setZoomRatio(currentZoom)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        focusPointOffset = offset
                                        showFocusSquare = true
                                        val point = previewView?.meteringPointFactory?.createPoint(offset.x, offset.y)
                                        if (point != null) {
                                            WebcamStreamer.focusAt(point)
                                        }
                                    }
                                )
                            }
                    )
                }

                // Focus square: 72 dp ácido 1 dp
                LaunchedEffect(showFocusSquare, focusPointOffset) {
                    if (showFocusSquare) {
                        delay(1500)
                        showFocusSquare = false
                    }
                }

                if (showFocusSquare && focusPointOffset != null) {
                    val pt = focusPointOffset!!
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (pt.x - 36.dp.toPx()).toInt(),
                                    (pt.y - 36.dp.toPx()).toInt()
                                )
                            }
                            .size(72.dp)
                            .border(1.dp, Hypr.Acid, RectangleShape)
                    )
                }

                // Sobreposições:
                // Canto superior esquerdo: «LENTE · 720P · 30 FPS · H.264»
                val facingName = if (lensLabel.contains("FRONTAL", ignoreCase = true)) strings.lensFront.uppercase() else strings.lensBack.uppercase()
                val topOverlayText = "$facingName · $effectiveHeader"

                Kicker(
                    text = topOverlayText,
                    color = Hypr.Sub,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )

                // Canto superior direito: activeCodec
                if (activeCodec.isNotEmpty()) {
                    Kicker(
                        text = activeCodec,
                        color = Hypr.Sub,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }

                // Canto inferior esquerdo: «TOCA PARA FOCAR» em Hypr.Faint
                Kicker(
                    text = strings.tapToFocus.uppercase(),
                    color = Hypr.Faint,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )

                // Canto inferior direito: Zoom chips (0,5× · 1× · 2× · 5×)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val zoomPresets = listOf(0.5f to "0,5×", 1.0f to "1×", 2.0f to "2×", 5.0f to "5×")
                    zoomPresets.forEach { (zoomVal, label) ->
                        val isSelected = kotlin.math.abs(currentZoom - zoomVal) < 0.25f
                        HyprChip(
                            label = label,
                            selected = isSelected,
                            onClick = {
                                currentZoom = zoomVal
                                WebcamStreamer.setZoomRatio(zoomVal)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3. Seis controlos em grelha 3×2 de Panel clicáveis
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Linha 1: LENTE, ROTAÇÃO, ESPELHO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // LENTE
                    val displayLens = if (lensLabel.contains("FRONTAL", ignoreCase = true)) "FRONTAL" else "TRASEIRA"
                    WebcamControlPanel(
                        kicker = strings.labelLens,
                        value = displayLens,
                        isActive = false,
                        onClick = { WebcamStreamer.nextLens() },
                        modifier = Modifier.weight(1f)
                    )

                    // ROTAÇÃO
                    WebcamControlPanel(
                        kicker = strings.labelRotation,
                        value = "${rotationDeg}°",
                        isActive = false,
                        onClick = { WebcamStreamer.rotate90() },
                        modifier = Modifier.weight(1f)
                    )

                    // ESPELHO
                    WebcamControlPanel(
                        kicker = strings.labelMirror,
                        value = if (mirrored) strings.labelYes.uppercase() else strings.labelNo.uppercase(),
                        isActive = mirrored,
                        onClick = { WebcamStreamer.toggleMirror() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Linha 2: LANTERNA, CODEC, MICROFONE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // LANTERNA (Traseira) ou LUZ DO ECRÃ (Frontal)
                    if (isFrontCamera) {
                        WebcamControlPanel(
                            kicker = strings.labelScreenLight,
                            value = if (isScreenLightOn) "ON" else "OFF",
                            isActive = isScreenLightOn,
                            onClick = { WebcamStreamer.toggleScreenLight(!isScreenLightOn) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val isTorchDisabled = isHighSpeedMode
                        val torchVal = if (isTorchDisabled) "—" else if (isTorchOn) "ON" else "OFF"
                        val torchKicker = if (isTorchDisabled) strings.torchUnavailable60Fps.uppercase() else strings.labelTorch
                        WebcamControlPanel(
                            kicker = torchKicker,
                            value = torchVal,
                            isActive = isTorchOn && !isTorchDisabled,
                            onClick = {
                                if (isTorchDisabled) {
                                    ConnectionRepository.appendLog("[WEBCAM] ${strings.torchUnavailable60Fps}")
                                } else {
                                    WebcamStreamer.toggleTorch(!isTorchOn)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // CODEC
                    val displayCodec = if (activeCodec.isNotEmpty()) activeCodec else codecPreference.uppercase()
                    WebcamControlPanel(
                        kicker = strings.labelCodec,
                        value = displayCodec,
                        isActive = false,
                        onClick = { WebcamStreamer.cycleCodecPreference(context) },
                        modifier = Modifier.weight(1f)
                    )

                    // MICROFONE
                    WebcamControlPanel(
                        kicker = strings.labelMicrophone,
                        value = if (isMicOn) "ON" else "OFF",
                        isActive = isMicOn,
                        onClick = {
                            if (isMicOn) {
                                WebcamStreamer.stopMic()
                            } else {
                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    WebcamStreamer.startMic(context)
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 4. Resolução
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Kicker(strings.labelResolution.uppercase(), color = Hypr.Muted)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val currentTargetCodec = if (activeCodec.isNotEmpty()) activeCodec.lowercase() else codecPreference.lowercase()
                    if (supportedResolutions.isEmpty()) {
                        val defaultResList = listOf("640x480" to Pair(640, 480), "1280x720" to Pair(1280, 720), "1920x1080" to Pair(1920, 1080))
                        defaultResList.forEach { (label, pair) ->
                            val isSelected = activeResString.equals(label, ignoreCase = true)
                            HyprChip(
                                label = label.replace("x", "×"),
                                selected = isSelected,
                                onClick = {
                                    sharedPrefs.edit().putString("webcam_resolution", label).apply()
                                    WebcamStreamer.restartWithResolution(context, pair.first, pair.second, currentFps, currentTargetCodec)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        supportedResolutions.forEach { item ->
                            val name = (item["name"] as? String) ?: ""
                            val w = (item["width"] as? Int) ?: 1280
                            val h = (item["height"] as? Int) ?: 720
                            val isSelected = activeResString.equals(name, ignoreCase = true) || (currentWidth == w && currentHeight == h)
                            HyprChip(
                                label = "${w}×${h}",
                                selected = isSelected,
                                onClick = {
                                    sharedPrefs.edit().putString("webcam_resolution", "${w}x${h}").apply()
                                    WebcamStreamer.restartWithResolution(context, w, h, currentFps, currentTargetCodec)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 5. Rodapé: APAGAR ECRÃ (Ghost) e PARAR TRANSMISSÃO (Danger)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HyprButton(
                    label = strings.btnBlankScreen,
                    onClick = { isScreenOff = true },
                    kind = HyprButtonKind.Ghost,
                    modifier = Modifier.weight(1f)
                )
                HyprButton(
                    label = strings.btnStopStream,
                    onClick = { WebcamStreamer.stop() },
                    kind = HyprButtonKind.Danger,
                    modifier = Modifier.weight(1f)
                )
            }

            if (currentFps == 60) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.webcamHighSpeedNotice,
                    color = if (isScreenLightOn) Hypr.Ink0 else Hypr.Sub,
                    style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Screen-off overlay (tap anywhere to re-awaken screen)
        if (isScreenOff) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { isScreenOff = false }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.tapAnywhereToWake,
                    color = Hypr.Sub,
                    style = TextStyle(fontFamily = PlexMono, fontSize = 12.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun WebcamControlPanel(
    kicker: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive) Hypr.Acid else Hypr.Line
    val valueColor = when {
        isActive -> Hypr.Acid
        value == "NÃO" || value == "OFF" -> Hypr.Faint
        else -> Hypr.Paper
    }

    Box(
        modifier = modifier
            .background(Hypr.Ink0, RectangleShape)
            .border(1.dp, borderColor, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Kicker(kicker, color = Hypr.Muted)
            Text(
                text = value.uppercase(),
                color = valueColor,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = Anton,
                    fontSize = 17.sp,
                    lineHeight = 1.0.sp
                )
            )
        }
    }
}
