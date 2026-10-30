// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import tech.kwik.core.QuicClientConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

object WebcamStreamer {
    private var streamerJob = SupervisorJob()
    private var streamerScope = CoroutineScope(Dispatchers.IO + streamerJob)

    private fun getStreamerScope(): CoroutineScope {
        if (!streamerJob.isActive) {
            streamerJob = SupervisorJob()
            streamerScope = CoroutineScope(Dispatchers.IO + streamerJob)
        }
        return streamerScope
    }

    private var mediaCodec: MediaCodec? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private fun getDisplayRotation(context: Context): Int {
        return try {
            context.display?.rotation
        } catch (_: Throwable) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation
        } ?: android.view.Surface.ROTATION_0
    }
    private var streamJob: Job? = null
    private var micJob: Job? = null
    private var activeConn: QuicClientConnection? = null
    private var appContext: Context? = null

    private var encoderPreview: Preview? = null
    private var screenPreview: Preview? = null
    private var camera: androidx.camera.core.Camera? = null

    private val _isHighSpeedMode = MutableStateFlow(false)
    val isHighSpeedMode = _isHighSpeedMode.asStateFlow()

    private var highSpeedCameraDevice: CameraDevice? = null
    private var highSpeedSession: CameraCaptureSession? = null
    private var highSpeedThread: HandlerThread? = null
    private var highSpeedHandler: Handler? = null
    private var highSpeedSurfaceTexture: SurfaceTexture? = null
    private var highSpeedPreviewSurface: Surface? = null
    private var highSpeedCodecSurface: Surface? = null
    private var activeTextureView: TextureView? = null

    // Lentes: todas as câmaras que o dispositivo expõe via CameraX
    // (frontal, traseira principal e, em muitos aparelhos, ultra-wide/
    // tele como entradas próprias). O botão LENTE cicla por todas.
    private var cameraInfos: List<CameraInfo> = emptyList()
    private var lensIndex = 0

    private var mainExecutor: Executor? = null

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _isMicOn = MutableStateFlow(false)
    val isMicOn = _isMicOn.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn = _isTorchOn.asStateFlow()

    private val _isScreenLightOn = MutableStateFlow(false)
    val isScreenLightOn = _isScreenLightOn.asStateFlow()

    private var findTorchJob: kotlinx.coroutines.Job? = null
    private var preFindTorchState: Boolean = false

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio = _zoomRatio.asStateFlow()

    private val _lensLabel = MutableStateFlow("LENTE")
    val lensLabel = _lensLabel.asStateFlow()

    private val _rotationDeg = MutableStateFlow(0)
    val rotationDeg = _rotationDeg.asStateFlow()

    private val _mirrored = MutableStateFlow(false)
    val mirrored = _mirrored.asStateFlow()

    // "auto" segue o pedido do PC; "h264"/"h265" forçam (aplicado no
    // próximo início da câmara). O codec REAL usado é declarado ao PC
    // num byte no início do stream, por isso nunca há dessincronização.
    private val _codecPreference = MutableStateFlow("auto")
    val codecPreference = _codecPreference.asStateFlow()

    private val _activeCodec = MutableStateFlow("")
    val activeCodec = _activeCodec.asStateFlow()

    private val _currentWidth = MutableStateFlow(1280)
    val currentWidth = _currentWidth.asStateFlow()

    private val _currentHeight = MutableStateFlow(720)
    val currentHeight = _currentHeight.asStateFlow()

    private val _currentFps = MutableStateFlow(30)
    val currentFps = _currentFps.asStateFlow()

    data class PendingConfig(
        val width: Int,
        val height: Int,
        val fps: Int,
        val codec: String
    )

    var pendingConfig: PendingConfig? = null
        private set

    fun setPendingConfig(width: Int, height: Int, fps: Int, codec: String) {
        pendingConfig = PendingConfig(width, height, fps, codec)
        _currentWidth.value = width
        _currentHeight.value = height
        _currentFps.value = fps
        if (codec.isNotBlank() && codec != "auto") {
            lastRequestedCodec = codec
        }
    }

    private var lastPacketId: Long = 0L
    private var lastRequestedCodec: String = "h264"
    private var lastSurfaceProvider: Preview.SurfaceProvider? = null

    private fun reportError(message: String) {
        val finalMsg = if (message.isBlank()) "Erro desconhecido na câmara" else message
        ConnectionRepository.appendLog("[WEBCAM] $finalMsg")
        getStreamerScope().launch {
            ConnectionRepository.sendOneWayPacket("webcam.error", mapOf("message" to finalMsg))
        }
    }

    fun getSupportedResolutions(context: Context): List<Map<String, Any>> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()
        val cameraId = try {
            cameraManager.cameraIdList.getOrNull(lensIndex) ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            return emptyList()
        }

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return emptyList()
        val outputSizes = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
            ?: map.getOutputSizes(android.view.SurfaceHolder::class.java)
            ?: emptyArray()

        return outputSizes
            .sortedByDescending { it.width * it.height }
            .distinctBy { "${it.width}x${it.height}" }
            .map { size ->
                mapOf(
                    "name" to "${size.width}x${size.height}",
                    "width" to size.width,
                    "height" to size.height
                )
            }
    }

    fun getAvailableFpsRanges(context: Context): List<Pair<Int, Int>> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val cameraId = try {
            cameraManager.cameraIdList.getOrNull(lensIndex) ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ranges?.map { Pair(it.lower, it.upper) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getHighSpeedCapabilities(context: Context): List<WebcamLogic.HighSpeedCapability> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val cameraId = try {
            cameraManager.cameraIdList.getOrNull(lensIndex) ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
            val sizes = map.highSpeedVideoSizes ?: emptyArray()
            sizes.map { size ->
                val ranges = map.getHighSpeedVideoFpsRangesFor(size) ?: emptyArray()
                WebcamLogic.HighSpeedCapability(
                    size = Pair(size.width, size.height),
                    fpsRanges = ranges.map { Pair(it.lower, it.upper) }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCameraOrientationAndFacing(context: Context): Pair<Int, Boolean> {
        val isFront = isFrontCamera()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return Pair(if (isFront) 270 else 90, isFront)
        return try {
            val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            var resolvedOrientation: Int? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                    resolvedOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION)
                    break
                }
            }
            Pair(resolvedOrientation ?: (if (isFront) 270 else 90), isFront)
        } catch (_: Exception) {
            Pair(if (isFront) 270 else 90, isFront)
        }
    }

    private fun findHardwareEncoderName(mime: String, width: Int, height: Int): String? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val candidates = mutableListOf<String>()
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
            if (!info.isHardwareAccelerated) continue
            if (info.name.contains(".cq", ignoreCase = true)) continue
            val caps = try {
                info.getCapabilitiesForType(mime)
            } catch (e: Exception) {
                null
            } ?: continue

            // Verificar se o encoder suporta COLOR_FormatSurface
            val supportsSurface = caps.colorFormats?.contains(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            ) == true
            if (!supportsSurface) continue

            val vidCaps = caps.videoCapabilities
            if (vidCaps != null && vidCaps.isSizeSupported(width, height)) {
                candidates.add(info.name)
            }
        }
        return WebcamLogic.rankEncoderCandidates(candidates).firstOrNull()
    }

    fun hasHardwareEncoder(mime: String, width: Int = 1280, height: Int = 720): Boolean {
        return findHardwareEncoderName(mime, width, height) != null
    }

    fun resolveEffectiveFormat(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        requestedCodec: String,
        supportedResolutions: List<Pair<Int, Int>> = emptyList(),
        maxSupportedFps: Int? = null,
        hasHevcHardwareEncoder: Boolean = true
    ): WebcamLogic.EffectiveWebcamFormat {
        val fpsRanges = if (maxSupportedFps != null) listOf(Pair(maxSupportedFps / 2, maxSupportedFps)) else emptyList()
        return WebcamLogic.resolveEffectiveFormat(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            requestedFps = requestedFps,
            requestedCodec = requestedCodec,
            supportedResolutions = supportedResolutions,
            availableFpsRanges = fpsRanges,
            hasHevcHardwareEncoder = hasHevcHardwareEncoder
        )
    }

    private fun createMediaFormat(
        mime: String,
        width: Int,
        height: Int,
        fps: Int,
        isHevc: Boolean,
        isHighSpeed: Boolean = false
    ): MediaFormat {
        return MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, isHevc, fps))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
            if (isHighSpeed) {
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 60.0f)
            }
            if (isHevc) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
            } else {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            }
        }
    }

    fun sendWebcamState() {
        if (!_isStreaming.value) return
        val currentCodec = if (_activeCodec.value.equals("H.265", ignoreCase = true) || _activeCodec.value.equals("h265", ignoreCase = true)) "h265" else "h264"
        val info = cameraInfos.getOrNull(lensIndex)
        val lensStr = try {
            if (info?.lensFacing == CameraSelector.LENS_FACING_FRONT) "front" else "back"
        } catch (e: Exception) {
            "back"
        }

        val (stateWidth, stateHeight) = WebcamLogic.resolveEffectiveDimensions(
            baseWidth = _currentWidth.value,
            baseHeight = _currentHeight.value,
            rotation = _rotationDeg.value
        )

        val stateBody = WebcamLogic.buildWebcamStateBody(
            width = stateWidth,
            height = stateHeight,
            fps = _currentFps.value,
            codec = currentCodec,
            lens = lensStr,
            rotation = _rotationDeg.value,
            mirror = _mirrored.value
        )
        getStreamerScope().launch {
            try {
                ConnectionRepository.sendOneWayPacket("webcam.state", stateBody)
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WEBCAM] Falha ao enviar webcam.state: ${e.message}")
            }
        }
    }

    fun start(context: Context, connection: QuicClientConnection, packetId: Long, width: Int, height: Int, fps: Int, requestedCodec: String) {
        val targetWidth = pendingConfig?.width ?: width
        val targetHeight = pendingConfig?.height ?: height
        val targetFps = pendingConfig?.fps ?: fps
        val targetCodec = pendingConfig?.codec ?: requestedCodec
        pendingConfig = null

        val executor = ContextCompat.getMainExecutor(context)
        mainExecutor = executor
        appContext = context.applicationContext
        activeConn = connection
        lastPacketId = packetId
        lastRequestedCodec = targetCodec

        // O setup do CameraX corre na main thread
        executor.execute {
            try {
                stopOnMain()
                val promoted = HyprLinkConnectionService.instance?.promoteToCameraForeground() ?: false
                if (!promoted) {
                    reportError("Falha ao promover serviço para câmara em primeiro plano")
                    return@execute
                }

                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    reportError("Permissão de câmara não concedida — não é possível iniciar a transmissão")
                    return@execute
                }

                // 1. Orientação inicial determinística
                val (sensorOrientation, isFront) = getCameraOrientationAndFacing(context)
                val displayRot = getDisplayRotation(context)
                val displayRotDegrees = when (displayRot) {
                    android.view.Surface.ROTATION_90 -> 90
                    android.view.Surface.ROTATION_180 -> 180
                    android.view.Surface.ROTATION_270 -> 270
                    else -> 0
                }
                val initialRotation = WebcamLogic.calculateCameraRotation(sensorOrientation, isFront, displayRotDegrees)
                _rotationDeg.value = initialRotation
                _mirrored.value = isFront

                // 2. Resoluções e FPS suportados pelo hardware
                val supportedRaw = getSupportedResolutions(context)
                val supported = supportedRaw.mapNotNull {
                    val w = it["width"] as? Int
                    val h = it["height"] as? Int
                    if (w != null && h != null) Pair(w, h) else null
                }
                val fpsRanges = getAvailableFpsRanges(context)

                // Codec pretendido: preferência local > pedido do PC
                val wanted = when (_codecPreference.value) {
                    "auto" -> targetCodec.lowercase()
                    else -> _codecPreference.value.lowercase()
                }
                val lensFacingName = if (isFront) "frontal" else "traseira"
                val hasHevc = hasHardwareEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC, targetWidth, targetHeight)

                val highSpeedCaps = getHighSpeedCapabilities(context)

                // 3. Determinar o formato efetivo que o hardware aceita
                val effective = WebcamLogic.resolveEffectiveFormat(
                    requestedWidth = targetWidth,
                    requestedHeight = targetHeight,
                    requestedFps = targetFps,
                    requestedCodec = wanted,
                    supportedResolutions = supported,
                    availableFpsRanges = fpsRanges,
                    highSpeedCapabilities = highSpeedCaps,
                    hasHevcHardwareEncoder = hasHevc,
                    lensFacing = lensFacingName
                )

                if (effective.limitationReason != null) {
                    ConnectionRepository.appendLog("[WEBCAM] ${effective.limitationReason}")
                }

                if (wanted == "h265" && effective.codec != "h265") {
                    ConnectionRepository.appendLog("[WEBCAM] Sem encoder HEVC de hardware para ${targetWidth}x${targetHeight} — a usar H.264")
                }

                val effWidth = effective.width
                val effHeight = effective.height
                val effFps = effective.fps
                val aeRange = effective.aeRange
                val isHighSpeed = effective.isHighSpeed

                _currentWidth.value = effWidth
                _currentHeight.value = effHeight
                _currentFps.value = effFps
                _isHighSpeedMode.value = isHighSpeed

                if (isHighSpeed) {
                    startHighSpeedSession(
                        context = context,
                        connection = connection,
                        packetId = packetId,
                        width = effWidth,
                        height = effHeight,
                        codecName = effective.codec,
                        fallbackFn = { reason ->
                            ConnectionRepository.appendLog("[WEBCAM] alta velocidade indisponível ($reason), a usar 30 fps")
                            _isHighSpeedMode.value = false
                            _currentFps.value = 30
                            startNormalSession(
                                context = context,
                                connection = connection,
                                packetId = packetId,
                                width = effWidth,
                                height = effHeight,
                                fps = 30,
                                aeRange = Pair(15, 30),
                                codecName = effective.codec,
                                displayRot = displayRot
                            )
                        }
                    )
                } else {
                    startNormalSession(
                        context = context,
                        connection = connection,
                        packetId = packetId,
                        width = effWidth,
                        height = effHeight,
                        fps = effFps,
                        aeRange = aeRange,
                        codecName = effective.codec,
                        displayRot = displayRot
                    )
                }
            } catch (e: Exception) {
                val err = e.javaClass.simpleName + ": " + (e.message ?: "")
                android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao iniciar a câmara: $err", e)
                reportError("Falha ao iniciar a câmara: $err")
                stopOnMain()
            }
        }
    }

    private fun startNormalSession(
        context: Context,
        connection: QuicClientConnection,
        packetId: Long,
        width: Int,
        height: Int,
        fps: Int,
        aeRange: Pair<Int, Int>,
        codecName: String,
        displayRot: Int
    ) {
        val executor = mainExecutor ?: ContextCompat.getMainExecutor(context)
        var codec: MediaCodec? = null
        var isHevc = codecName == "h265"
        var effMime = if (isHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        if (isHevc) {
            try {
                val encoderName = findHardwareEncoderName(effMime, width, height)
                val c = if (encoderName != null) {
                    try {
                        MediaCodec.createByCodecName(encoderName)
                    } catch (e: Exception) {
                        MediaCodec.createEncoderByType(effMime)
                    }
                } else {
                    MediaCodec.createEncoderByType(effMime)
                }
                val fmt = createMediaFormat(effMime, width, height, fps, isHevc = true, isHighSpeed = false)
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec = c
            } catch (e: Exception) {
                val err = e.javaClass.simpleName + ": " + (e.message ?: "")
                android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao configurar HEVC ($err)", e)
                ConnectionRepository.appendLog("[WEBCAM] Falha ao configurar HEVC ($err) — a usar fallback H.264")
                isHevc = false
                effMime = MediaFormat.MIMETYPE_VIDEO_AVC
            }
        }

        if (codec == null) {
            try {
                val encoderName = findHardwareEncoderName(effMime, width, height)
                val c = if (encoderName != null) {
                    try {
                        MediaCodec.createByCodecName(encoderName)
                    } catch (e: Exception) {
                        MediaCodec.createEncoderByType(effMime)
                    }
                } else {
                    MediaCodec.createEncoderByType(effMime)
                }
                val fmt = createMediaFormat(effMime, width, height, fps, isHevc = false, isHighSpeed = false)
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec = c
            } catch (e: Exception) {
                val err = e.javaClass.simpleName + ": " + (e.message ?: "")
                android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao configurar encoder com nome ($err), a tentar por tipo", e)
                val c = MediaCodec.createEncoderByType(effMime)
                val fmt = createMediaFormat(effMime, width, height, fps, isHevc = false, isHighSpeed = false)
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec = c
            }
        }

        val inputSurface = codec.createInputSurface()
        codec.start()
        mediaCodec = codec

        val codecByte: Byte = if (isHevc) 0x02 else 0x01
        _activeCodec.value = if (isHevc) "H.265" else "H.264"

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(width, height),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val previewBuilder = Preview.Builder().setResolutionSelector(resolutionSelector)
        previewBuilder.setTargetRotation(displayRot)

        try {
            Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(aeRange.first, aeRange.second)
            )
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WEBCAM] Não foi possível pedir AE range [${aeRange.first}, ${aeRange.second}] à câmara: ${e.message}")
        }
        val encPreview = previewBuilder.build()
        encPreview.setSurfaceProvider { request ->
            request.provideSurface(inputSurface, executor) { }
        }
        encoderPreview = encPreview

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                cameraInfos = provider.availableCameraInfos
                if (lensIndex >= cameraInfos.size) lensIndex = 0
                updateLensLabel()

                // Se houver um surfaceProvider guardado (ex: após reinício de resolução/codec),
                // recria o screenPreview para restabelecer a pré-visualização local
                lastSurfaceProvider?.let { surfaceProvider ->
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(surfaceProvider)
                    screenPreview = preview
                }

                bindUseCases()
                sendTransform()
                sendWebcamState()
            } catch (e: Exception) {
                val err = e.javaClass.simpleName + ": " + (e.message ?: "")
                android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao vincular a câmara: $err", e)
                reportError("Falha ao vincular a câmara: $err")
                stopOnMain()
            }
        }, executor)

        _isStreaming.value = true
        sendTransform()
        sendWebcamState()

        startStreamJob(
            connection = connection,
            packetId = packetId,
            codec = codec,
            codecByte = codecByte,
            effWidth = width,
            effHeight = height,
            effFps = fps,
            isHevc = isHevc,
            isHighSpeed = false
        )
    }

    @SuppressLint("MissingPermission")
    private fun startHighSpeedSession(
        context: Context,
        connection: QuicClientConnection,
        packetId: Long,
        width: Int,
        height: Int,
        codecName: String,
        fallbackFn: (String) -> Unit
    ) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            fallbackFn("CameraManager indisponível")
            return
        }

        val cameraId = try {
            cameraManager.cameraIdList.getOrNull(lensIndex) ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        }
        if (cameraId == null) {
            fallbackFn("Nenhuma câmara identificada")
            return
        }

        var codec: MediaCodec? = null
        var isHevc = codecName == "h265"
        var effMime = if (isHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        if (isHevc) {
            try {
                val encoderName = findHardwareEncoderName(effMime, width, height)
                val c = if (encoderName != null) {
                    try {
                        MediaCodec.createByCodecName(encoderName)
                    } catch (e: Exception) {
                        MediaCodec.createEncoderByType(effMime)
                    }
                } else {
                    MediaCodec.createEncoderByType(effMime)
                }
                val fmt = createMediaFormat(effMime, width, height, 60, isHevc = true, isHighSpeed = true)
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec = c
            } catch (e: Exception) {
                val err = e.javaClass.simpleName + ": " + (e.message ?: "")
                android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao configurar HEVC 60 fps ($err)", e)
                ConnectionRepository.appendLog("[WEBCAM] Falha ao configurar HEVC 60 fps ($err) — a usar fallback H.264")
                isHevc = false
                effMime = MediaFormat.MIMETYPE_VIDEO_AVC
            }
        }

        if (codec == null) {
            try {
                val encoderName = findHardwareEncoderName(effMime, width, height)
                val c = if (encoderName != null) {
                    try {
                        MediaCodec.createByCodecName(encoderName)
                    } catch (e: Exception) {
                        MediaCodec.createEncoderByType(effMime)
                    }
                } else {
                    MediaCodec.createEncoderByType(effMime)
                }
                val fmt = createMediaFormat(effMime, width, height, 60, isHevc = false, isHighSpeed = true)
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec = c
            } catch (e: Exception) {
                val err = e.javaClass.simpleName + ": " + (e.message ?: "")
                android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao criar encoder H.264 a 60 fps: $err", e)
                fallbackFn("Falha ao criar encoder H.264 a 60 fps: $err")
                return
            }
        }

        val inputSurface = codec.createInputSurface()
        codec.start()
        val params = android.os.Bundle().apply {
            putFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 60.0f)
        }
        runCatching { codec.setParameters(params) }
        mediaCodec = codec
        highSpeedCodecSurface = inputSurface

        val codecByte: Byte = if (isHevc) 0x02 else 0x01
        _activeCodec.value = if (isHevc) "H.265" else "H.264"

        // HAL da Qualcomm exige exatamente 2 superfícies com tamanhos suportados pela tabela high-speed
        val st = highSpeedSurfaceTexture ?: SurfaceTexture(0).apply {
            setDefaultBufferSize(width, height)
            runCatching { detachFromGLContext() }
        }.also { highSpeedSurfaceTexture = it }
        st.setDefaultBufferSize(width, height)
        val previewSurface = Surface(st)
        highSpeedPreviewSurface = previewSurface

        val activeTv = activeTextureView
        if (activeTv != null && activeTv.isAvailable) {
            runCatching { activeTv.setSurfaceTexture(st) }
        }

        val thread = HandlerThread("CameraHighSpeedThread").apply { start() }
        val handler = Handler(thread.looper)
        highSpeedThread = thread
        highSpeedHandler = handler

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    highSpeedCameraDevice = device
                    try {
                        val surfaces = listOf(inputSurface)
                        device.createConstrainedHighSpeedCaptureSession(
                            surfaces,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    highSpeedSession = session
                                    val hsSession = session as? CameraConstrainedHighSpeedCaptureSession
                                    if (hsSession == null) {
                                        mainExecutor?.execute { fallbackFn("Sessão não implementa CameraConstrainedHighSpeedCaptureSession") }
                                        return
                                    }
                                    try {
                                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                            addTarget(inputSurface)
                                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(120, 120))
                                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                        }
                                        _isTorchOn.value = false
                                        val burstList = hsSession.createHighSpeedRequestList(builder.build())
                                        hsSession.setRepeatingBurst(burstList, null, handler)
                                        ConnectionRepository.appendLog("[WEBCAM] Sessão de alta velocidade configurada a 120 fps de sensor (encoder a 60 fps)")
                                        _isStreaming.value = true
                                        sendTransform()
                                        sendWebcamState()
                                    } catch (e: Exception) {
                                        mainExecutor?.execute { fallbackFn("Falha no setRepeatingBurst de alta velocidade: ${e.message}") }
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    mainExecutor?.execute { fallbackFn("createConstrainedHighSpeedCaptureSession falhou") }
                                }
                            },
                            handler
                        )
                    } catch (e: Exception) {
                        mainExecutor?.execute { fallbackFn("Exceção ao criar sessão de alta velocidade: ${e.message}") }
                    }
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    highSpeedCameraDevice = null
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    highSpeedCameraDevice = null
                    mainExecutor?.execute { fallbackFn("Erro no CameraDevice ($error)") }
                }
            }, handler)
        } catch (e: Exception) {
            fallbackFn("Exceção ao abrir CameraDevice: ${e.message}")
            return
        }

        startStreamJob(
            connection = connection,
            packetId = packetId,
            codec = codec,
            codecByte = codecByte,
            effWidth = width,
            effHeight = height,
            effFps = 60,
            isHevc = isHevc,
            isHighSpeed = true
        )
    }

    private fun startStreamJob(
        connection: QuicClientConnection,
        packetId: Long,
        codec: MediaCodec,
        codecByte: Byte,
        effWidth: Int,
        effHeight: Int,
        effFps: Int,
        isHevc: Boolean,
        isHighSpeed: Boolean
    ) {
        streamJob = getStreamerScope().launch {
            var out: java.io.OutputStream? = null
            try {
                val uniStream = connection.createStream(false)
                out = uniStream.outputStream
                val idBytes = ByteArray(8)
                for (i in 0..7) idBytes[i] = ((packetId shr (56 - i * 8)) and 0xFF).toByte()
                out.write(idBytes)
                out.write(byteArrayOf(codecByte))

                val bufferInfo = MediaCodec.BufferInfo()
                var consecutiveErrors = 0
                var encodedFrames = 0
                var lastLogTime = System.currentTimeMillis()

                while (isActive) {
                    try {
                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 100_000)
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val newFormat = codec.outputFormat
                            ConnectionRepository.appendLog("[WEBCAM] Formato de saída alterado: ${newFormat.getString(MediaFormat.KEY_MIME)}")
                            consecutiveErrors = 0
                        } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // Sem buffer pronto neste ciclo
                        } else if (outIndex >= 0) {
                            consecutiveErrors = 0
                            encodedFrames++
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime >= 5000) {
                                val durationSec = (now - lastLogTime) / 1000.0
                                val actualFps = encodedFrames / durationSec
                                encodedFrames = 0
                                lastLogTime = now
                                if (isHighSpeed) {
                                    ConnectionRepository.appendLog(
                                        "[WEBCAM] 60 fps ativo: encoder a debitar %.1f fps reais".format(
                                            java.util.Locale.US,
                                            actualFps
                                        )
                                    )
                                }
                            }

                            val encoded = codec.getOutputBuffer(outIndex)
                            if (encoded != null && bufferInfo.size > 0) {
                                val bytes = ByteArray(bufferInfo.size)
                                encoded.position(bufferInfo.offset)
                                encoded.get(bytes)
                                out.write(bytes)
                                out.flush()
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    } catch (e: IllegalStateException) {
                        consecutiveErrors++
                        ConnectionRepository.appendLog("[WEBCAM] Aviso transitório de buffer (#$consecutiveErrors): ${e.message}")
                        if (consecutiveErrors >= 5) {
                            throw e
                        }
                        kotlinx.coroutines.delay(20)
                    }
                }
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WEBCAM] Stream terminado ou erro: ${e.message}")
                if (effHeight > 720 && isActive) {
                    ConnectionRepository.appendLog("[WEBCAM] Rebaixando para 720p por estabilidade...")
                    mainExecutor?.execute {
                        val currentConn = activeConn ?: connection
                        restartWithResolution(appContext ?: return@execute, 1280, 720, effFps, if (isHevc) "h265" else "h264")
                    }
                }
            } finally {
                try { out?.close() } catch (e: Exception) {}
                _isStreaming.value = false
            }
        }
    }

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        lastSurfaceProvider = surfaceProvider
        if (!_isHighSpeedMode.value) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(surfaceProvider)
            screenPreview = preview
            bindUseCases()
        }
    }

    fun detachPreview() {
        lastSurfaceProvider = null
        screenPreview = null
        if (!_isHighSpeedMode.value) {
            bindUseCases()
        }
    }

    fun attachHighSpeedTextureView(textureView: TextureView) {
        activeTextureView = textureView
        val st = highSpeedSurfaceTexture
        if (st != null) {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    runCatching { textureView.setSurfaceTexture(st) }
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
            if (textureView.isAvailable) {
                runCatching { textureView.setSurfaceTexture(st) }
            }
        }
    }

    fun detachHighSpeedTextureView() {
        activeTextureView = null
    }

    fun restartWithResolution(
        context: Context,
        width: Int,
        height: Int,
        fps: Int = _currentFps.value,
        codec: String = lastRequestedCodec
    ) {
        val conn = activeConn ?: ConnectionRepository.activeConnection ?: return
        val packetId = if (lastPacketId != 0L) lastPacketId else System.currentTimeMillis()
        start(context, conn, packetId, width, height, fps, codec)
        activeTextureView?.let { tv ->
            attachHighSpeedTextureView(tv)
        }
    }

    fun onConnectionUpdated(newConn: QuicClientConnection) {
        activeConn = newConn
        if (_isStreaming.value) {
            val ctx = appContext
            if (ctx != null) {
                ConnectionRepository.appendLog("[WEBCAM] Ligação de transporte atualizada; a retomar stream...")
                restartWithResolution(ctx, _currentWidth.value, _currentHeight.value, _currentFps.value, lastRequestedCodec)
            }
        }
    }

    /// Cicla por TODAS as câmaras expostas (frontal/traseira/ultra-wide/
    /// tele, conforme o aparelho) sem interromper encoder nem stream.
    fun nextLens() {
        val total = if (cameraInfos.isNotEmpty()) cameraInfos.size else 2
        if (total <= 1) return

        lensIndex = (lensIndex + 1) % total
        updateLensLabel()

        if (isFrontCamera()) {
            if (_isTorchOn.value) toggleTorch(false)
        } else {
            if (_isScreenLightOn.value) toggleScreenLight(false)
        }

        if (!_isStreaming.value) {
            sendWebcamState()
            return
        }

        val ctx = appContext
        // Se estiver em modo de alta velocidade (Camera2, 60 fps), a frontal não suporta 60 fps
        // pelo que é necessário fazer transição para a sessão normal (30 fps)
        if (_isHighSpeedMode.value) {
            if (ctx != null) {
                restartWithResolution(ctx, _currentWidth.value, _currentHeight.value, 30, lastRequestedCodec)
            }
            return
        }

        // Modo normal CameraX (30 fps):
        // NÃO reiniciar o encoder nem o stream QUIC! O CameraX troca de lente em tempo real
        // mantendo as mesmas superfícies de encoding e preview, sem corte no PC.
        val exec = mainExecutor ?: (ctx?.let { ContextCompat.getMainExecutor(it) })
        exec?.execute {
            if (ctx != null) {
                val (sensorOrientation, isFront) = getCameraOrientationAndFacing(ctx)
                val displayRot = getDisplayRotation(ctx)
                val displayRotDegrees = when (displayRot) {
                    android.view.Surface.ROTATION_90 -> 90
                    android.view.Surface.ROTATION_180 -> 180
                    android.view.Surface.ROTATION_270 -> 270
                    else -> 0
                }
                _rotationDeg.value = WebcamLogic.calculateCameraRotation(sensorOrientation, isFront, displayRotDegrees)
                _mirrored.value = isFront
            }
            bindUseCases()
            sendTransform()
            sendWebcamState()
        }
    }

    private fun updateLensLabel() {
        val info = cameraInfos.getOrNull(lensIndex)
        val facing = if (info != null) {
            try {
                if (info.lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONTAL" else "TRASEIRA"
            } catch (e: Exception) {
                "?"
            }
        } else {
            if (lensIndex % 2 == 1) "FRONTAL" else "TRASEIRA"
        }
        val total = if (cameraInfos.isNotEmpty()) cameraInfos.size else 2
        _lensLabel.value = "LENTE ${lensIndex + 1}/$total · $facing"
    }

    /// Rotação em passos de 90° — aplicada no PC (videoflip), o encoder
    /// continua a mandar a imagem tal como a câmara a captura.
    fun rotate90() {
        _rotationDeg.value = (_rotationDeg.value + 90) % 360
        sendTransform()
        sendWebcamState()
    }

    fun toggleMirror() {
        _mirrored.value = !_mirrored.value
        sendTransform()
        sendWebcamState()
    }

    private fun sendTransform() {
        getStreamerScope().launch {
            ConnectionRepository.sendOneWayPacket(
                "webcam.transform",
                mapOf("rotation" to _rotationDeg.value, "mirror" to _mirrored.value)
            )
        }
    }

    fun cycleCodecPreference(context: Context? = null) {
        val nextPref = when (_codecPreference.value) {
            "auto" -> "h264"
            "h264" -> "h265"
            else -> "auto"
        }
        _codecPreference.value = nextPref
        ConnectionRepository.appendLog("[WEBCAM] Preferência de codec alterada para: $nextPref")

        if (_isStreaming.value) {
            val ctx = context ?: appContext
            if (ctx != null) {
                val wantedCodec = if (nextPref == "auto") lastRequestedCodec else nextPref
                restartWithResolution(
                    context = ctx,
                    width = _currentWidth.value,
                    height = _currentHeight.value,
                    fps = _currentFps.value,
                    codec = wantedCodec
                )
            }
        }
    }

    /// Microfone do telemóvel → PC (PCM 16-bit 48kHz mono, cru — na LAN
    /// não vale a pena codec). Segue o padrão share.file: anuncia com o
    /// próprio id e abre o uni stream com esse id.
    @SuppressLint("MissingPermission")
    fun startMic(context: Context) {
        if (micJob != null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ConnectionRepository.appendLog("[WEBCAM] Permissão de microfone não concedida")
            return
        }
        val conn = activeConn ?: ConnectionRepository.activeConnection ?: run {
            ConnectionRepository.appendLog("[WEBCAM] Microfone: sem conexão ativa com o PC")
            return
        }

        micJob = getStreamerScope().launch {
            var out: java.io.OutputStream? = null
            var recorder: AudioRecord? = null
            try {
                val packetId = ConnectionRepository.sendAnnouncedPacket(
                    "webcam.mic_start",
                    mapOf("rate" to 48000, "channels" to 1)
                ) ?: return@launch

                val uniStream = conn.createStream(false)
                out = uniStream.outputStream
                val idBytes = ByteArray(8)
                for (i in 0..7) idBytes[i] = ((packetId shr (56 - i * 8)) and 0xFF).toByte()
                out.write(idBytes)

                val minBuf = AudioRecord.getMinBufferSize(
                    48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuf, 9600)

                var rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (rec.state != AudioRecord.STATE_INITIALIZED || rec.sampleRate != 48000) {
                    val rate = if (rec.state == AudioRecord.STATE_INITIALIZED) rec.sampleRate else 0
                    ConnectionRepository.appendLog("[MIC] VOICE_COMMUNICATION sem 48kHz (${rate}Hz) — AEC indisponível neste aparelho, a usar MIC")
                    rec.release()
                    rec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }

                val promoted = HyprLinkConnectionService.instance?.promoteToMicrophoneForeground() ?: false
                if (!promoted) {
                    ConnectionRepository.appendLog("[WEBCAM] Não foi possível promover serviço para microfone. A cancelar gravação.")
                    rec.release()
                    if (!ConnectionRepository.isAppInForeground()) {
                        ConnectionRepository.postMicStartNotification(context)
                    }
                    return@launch
                }

                recorder = rec
                recorder.startRecording()
                _isMicOn.value = true

                val buf = ByteArray(4096)
                while (isActive) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                }
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WEBCAM] Microfone terminou: ${e.message}")
            } finally {
                runCatching { recorder?.stop() }
                recorder?.release()
                try { out?.close() } catch (e: Exception) {}
                _isMicOn.value = false
                HyprLinkConnectionService.instance?.demoteFromMicrophoneForeground()
            }
        }
    }

    fun stopMic() {
        micJob?.cancel()
        micJob = null
        _isMicOn.value = false
        HyprLinkConnectionService.instance?.demoteFromMicrophoneForeground()
        getStreamerScope().launch {
            ConnectionRepository.sendOneWayPacket("webcam.mic_stop", emptyMap())
        }
    }

    private var fallbackLifecycleOwner: HyprLinkConnectionService.CameraServiceLifecycleOwner? = null

    private fun getLifecycleOwner(): androidx.lifecycle.LifecycleOwner {
        val serviceOwner = HyprLinkConnectionService.instance?.getOrCreateCameraLifecycleOwner()
        if (serviceOwner != null) return serviceOwner
        val fallback = fallbackLifecycleOwner ?: HyprLinkConnectionService.CameraServiceLifecycleOwner().also {
            fallbackLifecycleOwner = it
        }
        fallback.start()
        return fallback
    }

    // Só na main thread (attachPreview/detachPreview/nextLens vêm do
    // Compose; o listener do provider usa o mainExecutor).
    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val useCases = listOfNotNull(encoderPreview, screenPreview).toTypedArray()
        if (useCases.isEmpty()) return
        val selector = cameraInfos.getOrNull(lensIndex)?.cameraSelector ?: if (lensIndex % 2 == 1) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(getLifecycleOwner(), selector, *useCases)
        } catch (e: Exception) {
            val err = e.javaClass.simpleName + ": " + (e.message ?: "")
            android.util.Log.e("WebcamStreamer", "[WEBCAM] Falha ao vincular use cases da câmara: $err", e)
            reportError("Falha ao vincular use cases da câmara: $err")
        }
    }

    fun focusAt(point: androidx.camera.core.MeteringPoint) {
        val action = androidx.camera.core.FocusMeteringAction.Builder(
            point,
            androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun focusAtNormalized(x: Float, y: Float) {
        val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
        val point = factory.createPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        focusAt(point)
    }

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceAtLeast(1.0f)
        _zoomRatio.value = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun toggleTorch(on: Boolean) {
        if (_isHighSpeedMode.value) {
            _isTorchOn.value = false
            ConnectionRepository.appendLog("[WEBCAM] Lanterna indisponível em alta velocidade (60 fps)")
            return
        }
        _isTorchOn.value = on
        camera?.cameraControl?.enableTorch(on)
    }

    fun toggleScreenLight(on: Boolean) {
        if (_isScreenLightOn.value == on) return
        _isScreenLightOn.value = on
        ConnectionRepository.appendLog(
            if (on) "[WEBCAM] Luz do ecrã ligada" else "[WEBCAM] Luz do ecrã desligada"
        )
    }

    fun isFrontCamera(): Boolean {
        val info = cameraInfos.getOrNull(lensIndex)
        return try {
            info?.lensFacing == CameraSelector.LENS_FACING_FRONT
        } catch (_: Exception) {
            lensIndex % 2 == 1
        }
    }

    fun hasFlashUnit(): Boolean {
        if (isFrontCamera()) return false
        val info = cameraInfos.getOrNull(lensIndex)
        return try {
            info?.hasFlashUnit() ?: true
        } catch (_: Exception) {
            !isFrontCamera()
        }
    }

    fun startBlinkingTorch(scope: CoroutineScope): Boolean {
        if (!_isStreaming.value || isFrontCamera()) return false
        preFindTorchState = _isTorchOn.value
        findTorchJob?.cancel()
        findTorchJob = scope.launch(Dispatchers.Default) {
            var currentTorch = preFindTorchState
            try {
                while (isActive) {
                    currentTorch = !currentTorch
                    toggleTorch(currentTorch)
                    kotlinx.coroutines.delay(500L)
                }
            } finally {
                toggleTorch(preFindTorchState)
            }
        }
        return true
    }

    fun stopBlinkingTorch() {
        findTorchJob?.cancel()
        findTorchJob = null
        toggleTorch(preFindTorchState)
    }

    // Pode ser chamado de qualquer thread — despacha o teardown do
    // CameraX para a main (unbindAll fora dela lança "Not in
    // application's main thread").
    fun stop() {
        val exec = mainExecutor
        if (exec != null) {
            exec.execute { stopOnMain() }
        } else {
            stopOnMain()
        }
    }

    private fun stopOnMain() {
        stopBlinkingTorch()
        if (_isScreenLightOn.value) {
            toggleScreenLight(false)
        }
        micJob?.cancel()
        micJob = null
        _isMicOn.value = false
        _isTorchOn.value = false
        _zoomRatio.value = 1.0f
        streamJob?.cancel()
        streamJob = null
        streamerJob.cancel()
        streamerJob = SupervisorJob()
        streamerScope = CoroutineScope(Dispatchers.IO + streamerJob)

        highSpeedSession?.let { runCatching { it.stopRepeating() }; runCatching { it.close() } }
        highSpeedSession = null
        highSpeedCameraDevice?.let { runCatching { it.close() } }
        highSpeedCameraDevice = null
        highSpeedThread?.let { runCatching { it.quitSafely() } }
        highSpeedThread = null
        highSpeedHandler = null
        highSpeedPreviewSurface?.let { runCatching { it.release() } }
        highSpeedPreviewSurface = null
        highSpeedCodecSurface?.let { runCatching { it.release() } }
        highSpeedCodecSurface = null
        highSpeedSurfaceTexture?.let { runCatching { it.release() } }
        highSpeedSurfaceTexture = null
        _isHighSpeedMode.value = false

        camera = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        encoderPreview = null
        screenPreview = null
        mediaCodec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        mediaCodec = null
        _isStreaming.value = false

        fallbackLifecycleOwner?.stop()
        fallbackLifecycleOwner = null
        HyprLinkConnectionService.instance?.stopCameraLifecycle()
        HyprLinkConnectionService.instance?.demoteFromCameraForeground()
        HyprLinkConnectionService.instance?.demoteFromMicrophoneForeground()
    }

    private fun bitrateFor(width: Int, height: Int, hevc: Boolean, fps: Int = 30): Int {
        val baseH264 = when {
            height <= 480 -> 1_200_000
            height <= 720 -> 3_000_000
            else -> 6_000_000
        }
        val h264 = if (fps >= 60) (baseH264 * 1.5).toInt() else baseH264
        // HEVC atinge qualidade equivalente com ~60% do bitrate.
        return if (hevc) (h264 * 6) / 10 else h264
    }
}
