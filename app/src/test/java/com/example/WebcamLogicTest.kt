// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebcamLogicTest {

    private val ptStrings = StringsPtPt()
    private val enStrings = StringsEnUk()
    private val zhStrings = StringsZhCn()

    @Test
    fun testTranslateWebcamErrorAlreadyActive() {
        assertEquals("A câmara já está ativa no PC", WebcamLogic.translateWebcamError("already active", ptStrings))
        assertEquals("Webcam is already active on PC", WebcamLogic.translateWebcamError("already active", enStrings))
        assertEquals("电脑端摄像头已在运行", WebcamLogic.translateWebcamError("already active", zhStrings))
    }

    @Test
    fun testTranslateWebcamErrorNoPhoneConnected() {
        assertEquals("Nenhum telemóvel ligado", WebcamLogic.translateWebcamError("no phone connected", ptStrings))
        assertEquals("No phone connected", WebcamLogic.translateWebcamError("no phone connected", enStrings))
        assertEquals("未连接手机", WebcamLogic.translateWebcamError("no phone connected", zhStrings))
    }

    @Test
    fun testTranslateWebcamErrorNoV4l2loopback() {
        assertEquals("Módulo v4l2loopback não encontrado no PC", WebcamLogic.translateWebcamError("no v4l2loopback", ptStrings))
        assertEquals("v4l2loopback module not found on PC", WebcamLogic.translateWebcamError("no v4l2loopback", enStrings))
        assertEquals("电脑未找到 v4l2loopback 模块", WebcamLogic.translateWebcamError("no v4l2loopback", zhStrings))
    }

    @Test
    fun testTranslateWebcamErrorTimeout() {
        assertEquals("Tempo limite esgotado ao iniciar a câmara", WebcamLogic.translateWebcamError("timeout", ptStrings))
        assertEquals("Tempo limite esgotado ao iniciar a câmara", WebcamLogic.translateWebcamError("Stream read timed out", ptStrings))
        assertEquals("Timed out waiting for webcam", WebcamLogic.translateWebcamError("timeout", enStrings))
        assertEquals("启动摄像头超时", WebcamLogic.translateWebcamError("timeout", zhStrings))
    }

    @Test
    fun testTranslateWebcamErrorGenericAndUnknown() {
        assertEquals("Falha ao iniciar a câmara no PC", WebcamLogic.translateWebcamError(null, ptStrings))
        assertEquals("Falha ao iniciar a câmara no PC", WebcamLogic.translateWebcamError("", ptStrings))
        assertTrue(WebcamLogic.translateWebcamError("device busy", ptStrings).startsWith("Falha ao iniciar a câmara no PC"))
        assertTrue(WebcamLogic.translateWebcamError("device busy", enStrings).startsWith("Failed to start webcam on PC"))
    }

    @Test
    fun testBuildWebcamRequestBody() {
        val bodyAll = WebcamLogic.buildWebcamRequestBody(
            width = 1920,
            height = 1080,
            fps = 60,
            codec = "h265"
        )
        assertEquals(1920, bodyAll["width"])
        assertEquals(1080, bodyAll["height"])
        assertEquals(60, bodyAll["fps"])
        assertEquals("h265", bodyAll["codec"])

        val bodyAuto = WebcamLogic.buildWebcamRequestBody(
            width = 1280,
            height = 720,
            fps = 30,
            codec = "auto"
        )
        assertEquals(1280, bodyAuto["width"])
        assertEquals(720, bodyAuto["height"])
        assertEquals(30, bodyAuto["fps"])
        assertFalse(bodyAuto.containsKey("codec"))

        val bodyEmpty = WebcamLogic.buildWebcamRequestBody()
        assertTrue(bodyEmpty.isEmpty())
    }

    @Test
    fun testResolveEffectiveCodec() {
        assertEquals("h265", WebcamLogic.resolveEffectiveCodec("h265", hasHevcHardware = true))
        assertEquals("h264", WebcamLogic.resolveEffectiveCodec("h265", hasHevcHardware = false))
        assertEquals("h264", WebcamLogic.resolveEffectiveCodec("h264", hasHevcHardware = true))
        assertEquals("h264", WebcamLogic.resolveEffectiveCodec("auto", hasHevcHardware = true))
    }

    @Test
    fun testResolveEffectiveResolution() {
        val supported = listOf(
            Pair(1920, 1080),
            Pair(1280, 720),
            Pair(640, 480)
        )
        // Exact match
        assertEquals(Pair(1920, 1080), WebcamLogic.resolveEffectiveResolution(1920, 1080, supported))
        assertEquals(Pair(1280, 720), WebcamLogic.resolveEffectiveResolution(1280, 720, supported))

        // Closest match
        assertEquals(Pair(1280, 720), WebcamLogic.resolveEffectiveResolution(1200, 700, supported))
        assertEquals(Pair(640, 480), WebcamLogic.resolveEffectiveResolution(320, 240, supported))

        // Empty supported list falls back to requested
        assertEquals(Pair(1920, 1080), WebcamLogic.resolveEffectiveResolution(1920, 1080, emptyList()))
    }

    @Test
    fun testResolveEffectiveFpsAndExposureRange() {
        // Poco F4 ranges: [14, 15], [15, 15], [24, 24], [25, 25], [14, 30], [30, 30]
        val pocoRanges = listOf(
            Pair(14, 15),
            Pair(15, 15),
            Pair(24, 24),
            Pair(25, 25),
            Pair(14, 30),
            Pair(30, 30)
        )

        // Caso 4: pedido 60 fps -> rebaixa para 30 fps (máximo suportado pelo hardware) sem falhar
        val (fps60, range60) = WebcamLogic.resolveEffectiveFpsAndRange(60, pocoRanges)
        assertEquals(30, fps60)
        // Caso 6: prefere [14, 30] a [30, 30] para permitir tempo de exposição maior em baixa luminosidade
        assertEquals(Pair(14, 30), range60)

        // Pedido 30 fps
        val (fps30, range30) = WebcamLogic.resolveEffectiveFpsAndRange(30, pocoRanges)
        assertEquals(30, fps30)
        assertEquals(Pair(14, 30), range30)

        // Pedido 24 fps
        val (fps24, range24) = WebcamLogic.resolveEffectiveFpsAndRange(24, pocoRanges)
        assertEquals(24, fps24)
        assertEquals(Pair(24, 24), range24)

        // Sem ranges especificados
        val (fpsFallback, rangeFallback) = WebcamLogic.resolveEffectiveFpsAndRange(30, emptyList())
        assertEquals(30, fpsFallback)
        assertEquals(Pair(15, 30), rangeFallback)
    }

    @Test
    fun testCalculateCameraRotation() {
        // Poco F4: Câmara traseira sensorOrientation = 90
        // Telemóvel na vertical (deviceRotation = 0)
        assertEquals(90, WebcamLogic.calculateCameraRotation(90, isFrontCamera = false, deviceRotationDegrees = 0))
        // Telemóvel rodado para paisagem (deviceRotation = 90)
        assertEquals(0, WebcamLogic.calculateCameraRotation(90, isFrontCamera = false, deviceRotationDegrees = 90))
        // Telemóvel invertido (deviceRotation = 180)
        assertEquals(270, WebcamLogic.calculateCameraRotation(90, isFrontCamera = false, deviceRotationDegrees = 180))
        // Telemóvel paisagem inversa (deviceRotation = 270)
        assertEquals(180, WebcamLogic.calculateCameraRotation(90, isFrontCamera = false, deviceRotationDegrees = 270))

        // Poco F4: Câmara frontal sensorOrientation = 270
        assertEquals(270, WebcamLogic.calculateCameraRotation(270, isFrontCamera = true, deviceRotationDegrees = 0))
        assertEquals(0, WebcamLogic.calculateCameraRotation(270, isFrontCamera = true, deviceRotationDegrees = 90))
    }

    @Test
    fun testResolveEffectiveDimensions() {
        // Vertical (90 ou 270) -> dimensões verticais (720x1280)
        assertEquals(Pair(720, 1280), WebcamLogic.resolveEffectiveDimensions(1280, 720, rotation = 90))
        assertEquals(Pair(720, 1280), WebcamLogic.resolveEffectiveDimensions(1280, 720, rotation = 270))
        assertEquals(Pair(1080, 1920), WebcamLogic.resolveEffectiveDimensions(1920, 1080, rotation = 90))

        // Horizontal (0 ou 180) -> dimensões horizontais (1280x720)
        assertEquals(Pair(1280, 720), WebcamLogic.resolveEffectiveDimensions(1280, 720, rotation = 0))
        assertEquals(Pair(1280, 720), WebcamLogic.resolveEffectiveDimensions(1280, 720, rotation = 180))
        assertEquals(Pair(1280, 720), WebcamLogic.resolveEffectiveDimensions(720, 1280, rotation = 0))
    }

    @Test
    fun testResolveEffectiveFormatFull() {
        val supportedRes = listOf(Pair(1920, 1080), Pair(1280, 720))
        val fpsRanges = listOf(Pair(15, 30), Pair(30, 30))

        val result = WebcamLogic.resolveEffectiveFormat(
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 60,
            requestedCodec = "h265",
            supportedResolutions = supportedRes,
            availableFpsRanges = fpsRanges,
            hasHevcHardwareEncoder = true
        )

        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(30, result.fps)
        assertEquals("h265", result.codec)
        assertEquals(Pair(15, 30), result.aeRange)
        assertEquals("a câmara traseira só faz 30 fps a 1080p", result.limitationReason)
    }

    @Test
    fun testResolveEffectiveFpsAndRangeWithReason() {
        val rangesWith60 = listOf(
            Pair(15, 30),
            Pair(30, 30),
            Pair(30, 60),
            Pair(60, 60)
        )
        val result60 = WebcamLogic.resolveEffectiveFpsAndRangeWithReason(
            requestedFps = 60,
            availableRanges = rangesWith60,
            lensFacing = "traseira",
            height = 720
        )
        assertEquals(60, result60.fps)
        assertEquals(Pair(30, 60), result60.aeRange)
        assertEquals(null, result60.limitationReason)

        val rangesOnly30 = listOf(
            Pair(15, 30),
            Pair(30, 30)
        )
        val result30 = WebcamLogic.resolveEffectiveFpsAndRangeWithReason(
            requestedFps = 60,
            availableRanges = rangesOnly30,
            lensFacing = "traseira",
            height = 720
        )
        assertEquals(30, result30.fps)
        assertEquals(Pair(15, 30), result30.aeRange)
        assertEquals("a câmara traseira só faz 30 fps a 720p", result30.limitationReason)
    }

    @Test
    fun testResolveWebcamModeHighSpeedScenarios() {
        // Capacidades de alta velocidade reais do Poco F4:
        val pocoBackHighSpeed = listOf(
            WebcamLogic.HighSpeedCapability(Pair(1920, 1080), listOf(Pair(30, 120), Pair(120, 120))),
            WebcamLogic.HighSpeedCapability(Pair(1280, 720), listOf(Pair(30, 120), Pair(120, 120), Pair(30, 240), Pair(240, 240))),
            WebcamLogic.HighSpeedCapability(Pair(640, 480), listOf(Pair(30, 120), Pair(120, 120)))
        )
        val pocoFrontHighSpeed = listOf(
            WebcamLogic.HighSpeedCapability(Pair(1280, 720), listOf(Pair(30, 120), Pair(120, 120))),
            WebcamLogic.HighSpeedCapability(Pair(640, 480), listOf(Pair(30, 120), Pair(120, 120)))
        )
        val normalRanges = listOf(Pair(14, 30), Pair(30, 30))
        val supportedResBack = listOf(Pair(3840, 2160), Pair(1920, 1080), Pair(1280, 720), Pair(640, 480))
        val supportedResFront = listOf(Pair(1920, 1080), Pair(1280, 720), Pair(640, 480))

        // Cenário 1: Traseira 720p @ 60fps -> Alta velocidade 60fps com AE [120, 120]
        val back720p60 = WebcamLogic.resolveWebcamMode(
            requestedWidth = 1280,
            requestedHeight = 720,
            requestedFps = 60,
            requestedCodec = "h265",
            supportedResolutions = supportedResBack,
            availableNormalFpsRanges = normalRanges,
            highSpeedCapabilities = pocoBackHighSpeed,
            lensFacing = "traseira"
        )
        assertTrue(back720p60.isHighSpeed)
        assertEquals(60, back720p60.fps)
        assertEquals(Pair(120, 120), back720p60.aeRange)
        assertEquals(null, back720p60.limitationReason)

        // Cenário 2: Traseira 1080p @ 60fps -> Alta velocidade 60fps com AE [120, 120]
        val back1080p60 = WebcamLogic.resolveWebcamMode(
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 60,
            requestedCodec = "h265",
            supportedResolutions = supportedResBack,
            availableNormalFpsRanges = normalRanges,
            highSpeedCapabilities = pocoBackHighSpeed,
            lensFacing = "traseira"
        )
        assertTrue(back1080p60.isHighSpeed)
        assertEquals(60, back1080p60.fps)
        assertEquals(Pair(120, 120), back1080p60.aeRange)
        assertEquals(null, back1080p60.limitationReason)

        // Cenário 3: Frontal 1080p @ 60fps -> Não há alta velocidade 1080p na frontal -> mantém 1080p e baixa para 30fps com motivo explícito
        val front1080p60 = WebcamLogic.resolveWebcamMode(
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 60,
            requestedCodec = "h265",
            supportedResolutions = supportedResFront,
            availableNormalFpsRanges = normalRanges,
            highSpeedCapabilities = pocoFrontHighSpeed,
            lensFacing = "frontal"
        )
        assertFalse(front1080p60.isHighSpeed)
        assertEquals(1080, front1080p60.height)
        assertEquals(30, front1080p60.fps)
        assertEquals("a câmara frontal só faz 60 fps a 720p e 480p", front1080p60.limitationReason)

        // Cenário 4: Frontal 720p @ 60fps -> Alta velocidade 60fps disponível
        val front720p60 = WebcamLogic.resolveWebcamMode(
            requestedWidth = 1280,
            requestedHeight = 720,
            requestedFps = 60,
            requestedCodec = "h265",
            supportedResolutions = supportedResFront,
            availableNormalFpsRanges = normalRanges,
            highSpeedCapabilities = pocoFrontHighSpeed,
            lensFacing = "frontal"
        )
        assertTrue(front720p60.isHighSpeed)
        assertEquals(60, front720p60.fps)
        assertEquals(Pair(120, 120), front720p60.aeRange)
        assertEquals(null, front720p60.limitationReason)

        // Cenário 5: Pedido 30fps -> NUNCA alta velocidade, mesmo que suporte
        val request30 = WebcamLogic.resolveWebcamMode(
            requestedWidth = 1280,
            requestedHeight = 720,
            requestedFps = 30,
            requestedCodec = "h265",
            supportedResolutions = supportedResBack,
            availableNormalFpsRanges = normalRanges,
            highSpeedCapabilities = pocoBackHighSpeed,
            lensFacing = "traseira"
        )
        assertFalse(request30.isHighSpeed)
        assertEquals(30, request30.fps)
        assertEquals(Pair(14, 30), request30.aeRange)
        assertEquals(null, request30.limitationReason)

        // Cenário 6: Resolução sem alta velocidade (ex: 4K 3840x2160) com pedido de 60fps -> Normal a 30fps com motivo
        val back4k60 = WebcamLogic.resolveWebcamMode(
            requestedWidth = 3840,
            requestedHeight = 2160,
            requestedFps = 60,
            requestedCodec = "h265",
            supportedResolutions = supportedResBack,
            availableNormalFpsRanges = normalRanges,
            highSpeedCapabilities = pocoBackHighSpeed,
            lensFacing = "traseira"
        )
        assertFalse(back4k60.isHighSpeed)
        assertEquals(30, back4k60.fps)
        assertEquals("a câmara traseira só faz 30 fps a 2160p", back4k60.limitationReason)
    }

    @Test
    fun testResolveFindPhoneTorch() {
        // Cenário 1: Webcam inativa, câmara tem flash -> usa CameraManager
        val idleWithFlash = WebcamLogic.resolveFindPhoneTorch(
            isWebcamStreaming = false,
            isFrontCamera = false,
            hasFlashUnit = true
        )
        assertEquals(WebcamLogic.FindPhoneTorchAction.CAMERA_MANAGER_TORCH, idleWithFlash.action)
        assertEquals(null, idleWithFlash.error)
        assertFalse(idleWithFlash.activateScreenLight)

        // Cenário 2: Webcam inativa, sem flash -> indisponível
        val idleNoFlash = WebcamLogic.resolveFindPhoneTorch(
            isWebcamStreaming = false,
            isFrontCamera = false,
            hasFlashUnit = false
        )
        assertEquals(WebcamLogic.FindPhoneTorchAction.UNAVAILABLE, idleNoFlash.action)
        assertEquals("sem lanterna", idleNoFlash.error)
        assertFalse(idleNoFlash.activateScreenLight)

        // Cenário 3: Webcam ativa, câmara traseira com flash -> usa pedido de captura da webcam
        val streamingBack = WebcamLogic.resolveFindPhoneTorch(
            isWebcamStreaming = true,
            isFrontCamera = false,
            hasFlashUnit = true
        )
        assertEquals(WebcamLogic.FindPhoneTorchAction.WEBCAM_CAPTURE_REQUEST, streamingBack.action)
        assertEquals(null, streamingBack.error)
        assertFalse(streamingBack.activateScreenLight)

        // Cenário 4: Webcam ativa, câmara frontal -> erro e aciona luz de ecrã
        val streamingFront = WebcamLogic.resolveFindPhoneTorch(
            isWebcamStreaming = true,
            isFrontCamera = true,
            hasFlashUnit = false
        )
        assertEquals(WebcamLogic.FindPhoneTorchAction.FRONT_CAMERA_SCREEN_LIGHT, streamingFront.action)
        assertEquals("torch unavailable: front camera", streamingFront.error)
        assertTrue(streamingFront.activateScreenLight)

        // Cenário 5: Webcam ativa, traseira sem flash -> aciona luz de ecrã
        val streamingBackNoFlash = WebcamLogic.resolveFindPhoneTorch(
            isWebcamStreaming = true,
            isFrontCamera = false,
            hasFlashUnit = false
        )
        assertEquals(WebcamLogic.FindPhoneTorchAction.FRONT_CAMERA_SCREEN_LIGHT, streamingBackNoFlash.action)
        assertEquals("torch unavailable: front camera", streamingBackNoFlash.error)
        assertTrue(streamingBackNoFlash.activateScreenLight)
    }

    @Test
    fun testRankEncoderCandidatesPrioritizesCodec2OverOmx() {
        val mixed = listOf(
            "OMX.qcom.video.encoder.avc",
            "c2.qti.avc.encoder",
            "OMX.google.h264.encoder",
            "c2.android.avc.encoder"
        )
        val ranked = WebcamLogic.rankEncoderCandidates(mixed)
        assertEquals("c2.qti.avc.encoder", ranked[0])
        assertEquals("c2.android.avc.encoder", ranked[1])
        assertEquals("OMX.qcom.video.encoder.avc", ranked[2])
        assertEquals("OMX.google.h264.encoder", ranked[3])
    }
}
