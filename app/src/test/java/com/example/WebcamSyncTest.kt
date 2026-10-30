// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebcamSyncTest {

    // 1. Teste de resolução de formato (WebcamStreamer.resolveEffectiveFormat)
    @Test
    fun testResolveFormat_1080pRequested_deviceOnlySupports720p() {
        val supported = listOf(
            Pair(640, 480),
            Pair(1280, 720)
        )
        val result = WebcamStreamer.resolveEffectiveFormat(
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 30,
            requestedCodec = "h264",
            supportedResolutions = supported,
            maxSupportedFps = 30,
            hasHevcHardwareEncoder = true
        )
        assertEquals(1280, result.width)
        assertEquals(720, result.height)
        assertEquals(30, result.fps)
        assertEquals("h264", result.codec)
    }

    @Test
    fun testResolveFormat_60FpsRequested_deviceLimitsTo30Fps() {
        val supported = listOf(Pair(1280, 720))
        val result = WebcamStreamer.resolveEffectiveFormat(
            requestedWidth = 1280,
            requestedHeight = 720,
            requestedFps = 60,
            requestedCodec = "h264",
            supportedResolutions = supported,
            maxSupportedFps = 30,
            hasHevcHardwareEncoder = true
        )
        assertEquals(30, result.fps)
    }

    @Test
    fun testResolveFormat_H265Requested_withoutHevcHardwareEncoder() {
        val supported = listOf(Pair(1280, 720))
        val result = WebcamStreamer.resolveEffectiveFormat(
            requestedWidth = 1280,
            requestedHeight = 720,
            requestedFps = 30,
            requestedCodec = "h265",
            supportedResolutions = supported,
            maxSupportedFps = 30,
            hasHevcHardwareEncoder = false
        )
        assertEquals("h264", result.codec)
    }

    @Test
    fun testResolveFormat_H264Requested_withHevcSupported_respectsRequest() {
        val supported = listOf(Pair(1280, 720))
        val result = WebcamStreamer.resolveEffectiveFormat(
            requestedWidth = 1280,
            requestedHeight = 720,
            requestedFps = 30,
            requestedCodec = "h264",
            supportedResolutions = supported,
            maxSupportedFps = 30,
            hasHevcHardwareEncoder = true
        )
        assertEquals("h264", result.codec)
    }

    @Test
    fun testResolveFormat_UnsupportedResolution_choosesClosestByArea() {
        val supported = listOf(
            Pair(1280, 720),  // área: 921_600
            Pair(1920, 1080) // área: 2_073_600
        )
        // 800x600 área = 480_000.
        // Diferença para 720p: |921_600 - 480_000| = 441_600
        // Diferença para 1080p: |2_073_600 - 480_000| = 1_593_600
        // Mais próxima é 1280x720.
        val result = WebcamStreamer.resolveEffectiveFormat(
            requestedWidth = 800,
            requestedHeight = 600,
            requestedFps = 30,
            requestedCodec = "h264",
            supportedResolutions = supported
        )
        assertEquals(1280, result.width)
        assertEquals(720, result.height)
    }

    // 2. Teste do pacote webcam.configure
    @Test
    fun testParseWebcamConfig_completePayload() {
        val body = mapOf<String, Any>(
            "width" to 1920,
            "height" to 1080,
            "fps" to 30,
            "codec" to "h264"
        )
        val config = WebcamLogic.parseWebcamConfig(body)
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertEquals(30, config.fps)
        assertEquals("h264", config.codec)
    }

    @Test
    fun testParseWebcamConfig_missingFields_usesDefaults() {
        val body = emptyMap<String, Any>()
        val config = WebcamLogic.parseWebcamConfig(
            body = body,
            defaultWidth = 1280,
            defaultHeight = 720,
            defaultFps = 24,
            defaultCodec = "h264"
        )
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(24, config.fps)
        assertEquals("h264", config.codec)
    }

    // 3. Teste do pacote webcam.state
    @Test
    fun testBuildWebcamStateBody_allFieldsLowercaseCompatibility() {
        val state = WebcamLogic.buildWebcamStateBody(
            width = 1920,
            height = 1080,
            fps = 60,
            codec = "H.265",
            lens = "FRONT",
            rotation = 90,
            mirror = true
        )
        assertEquals(1920, state["width"])
        assertEquals(1080, state["height"])
        assertEquals(60, state["fps"])
        assertEquals("h265", state["codec"])
        assertEquals("front", state["lens"])
        assertEquals(90, state["rotation"])
        assertEquals(true, state["mirror"])
    }

    @Test
    fun testBuildWebcamStateBody_h264AndBackLens() {
        val state = WebcamLogic.buildWebcamStateBody(
            width = 1280,
            height = 720,
            fps = 30,
            codec = "h264",
            lens = "back",
            rotation = 0,
            mirror = false
        )
        assertEquals("h264", state["codec"])
        assertEquals("back", state["lens"])
        assertEquals(0, state["rotation"])
        assertEquals(false, state["mirror"])
    }
}
