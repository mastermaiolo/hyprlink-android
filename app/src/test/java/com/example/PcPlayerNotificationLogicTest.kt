// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcPlayerNotificationLogicTest {

    @Test
    fun testShouldShowNotificationLifecycle() {
        // Should show when playing and connected with setting on and title present
        assertTrue(
            PcPlayerLogic.shouldShowNotification(
                status = "Playing",
                isConnected = true,
                isSettingEnabled = true,
                hasTitle = true
            )
        )

        // Should show when paused
        assertTrue(
            PcPlayerLogic.shouldShowNotification(
                status = "Paused",
                isConnected = true,
                isSettingEnabled = true,
                hasTitle = true
            )
        )

        // Should NOT show when stopped
        assertFalse(
            PcPlayerLogic.shouldShowNotification(
                status = "Stopped",
                isConnected = true,
                isSettingEnabled = true,
                hasTitle = true
            )
        )

        // Should NOT show when disconnected
        assertFalse(
            PcPlayerLogic.shouldShowNotification(
                status = "Playing",
                isConnected = false,
                isSettingEnabled = true,
                hasTitle = true
            )
        )

        // Should NOT show when setting disabled
        assertFalse(
            PcPlayerLogic.shouldShowNotification(
                status = "Playing",
                isConnected = true,
                isSettingEnabled = false,
                hasTitle = true
            )
        )

        // Should NOT show without title/track
        assertFalse(
            PcPlayerLogic.shouldShowNotification(
                status = "Playing",
                isConnected = true,
                isSettingEnabled = true,
                hasTitle = false
            )
        )
    }

    @Test
    fun testOptimisticVsPcState() {
        // Initially follows PC status
        assertEquals("Playing", PcPlayerLogic.resolveEffectiveStatus(pcStatus = "Playing", optimisticStatus = null))
        assertEquals("Paused", PcPlayerLogic.resolveEffectiveStatus(pcStatus = "Paused", optimisticStatus = null))

        // When user toggles, optimistic status overrides PC status
        val optimisticPaused = PcPlayerLogic.togglePlayPause("Playing")
        assertEquals("Paused", optimisticPaused)
        assertEquals("Paused", PcPlayerLogic.resolveEffectiveStatus(pcStatus = "Playing", optimisticStatus = optimisticPaused))

        val optimisticPlaying = PcPlayerLogic.togglePlayPause("Paused")
        assertEquals("Playing", optimisticPlaying)
        assertEquals("Playing", PcPlayerLogic.resolveEffectiveStatus(pcStatus = "Paused", optimisticStatus = optimisticPlaying))
    }

    @Test
    fun testArtworkResolutionCache() {
        val testBytes = byteArrayOf(1, 2, 3, 4)

        // 1. First state with bytes and artId -> DecodeNew
        val res1 = PcPlayerLogic.resolveArtworkAction(
            incomingBytes = testBytes,
            incomingArtId = "art123",
            cachedArtId = null,
            hasCachedBitmap = false
        )
        assertTrue(res1 is ArtworkResolution.DecodeNew)
        assertEquals("art123", (res1 as ArtworkResolution.DecodeNew).artId)

        // 2. Subsequent state with same artId but no bytes -> ReuseCache
        val res2 = PcPlayerLogic.resolveArtworkAction(
            incomingBytes = null,
            incomingArtId = "art123",
            cachedArtId = "art123",
            hasCachedBitmap = true
        )
        assertTrue(res2 is ArtworkResolution.ReuseCache)
        assertEquals("art123", (res2 as ArtworkResolution.ReuseCache).artId)

        // 3. Track without art -> ClearCache
        val res3 = PcPlayerLogic.resolveArtworkAction(
            incomingBytes = null,
            incomingArtId = null,
            cachedArtId = "art123",
            hasCachedBitmap = true
        )
        assertTrue(res3 is ArtworkResolution.ClearCache)
    }

    @Test
    fun testArtworkShapeDecision() {
        assertEquals(ArtworkShape.NONE, PcPlayerLogic.determineArtworkShape(null, null))
        assertEquals(ArtworkShape.NONE, PcPlayerLogic.determineArtworkShape(0, 100))
        assertEquals(ArtworkShape.NONE, PcPlayerLogic.determineArtworkShape(100, 0))
        assertEquals(ArtworkShape.SQUARE, PcPlayerLogic.determineArtworkShape(500, 500))
        assertEquals(ArtworkShape.WIDE, PcPlayerLogic.determineArtworkShape(1920, 1080))
        assertEquals(ArtworkShape.TALL, PcPlayerLogic.determineArtworkShape(600, 800))
    }

    @Test
    fun testVeilConfigByApiLevel() {
        // API 31+ (Android 12+) -> blur 32dp, top 30%, mid 55%
        val config31 = PcPlayerLogic.resolveVeilConfig(31)
        assertEquals(32, config31.blurDp)
        assertEquals(0.30f, config31.topAlpha, 0.001f)
        assertEquals(0.55f, config31.midAlpha, 0.001f)
        assertEquals(0.92f, config31.lowerAlpha, 0.001f)
        assertEquals(1.0f, config31.bottomAlpha, 0.001f)

        // API < 31 -> no blur (0dp), top 60%, mid 80%
        val config30 = PcPlayerLogic.resolveVeilConfig(30)
        assertEquals(0, config30.blurDp)
        assertEquals(0.60f, config30.topAlpha, 0.001f)
        assertEquals(0.80f, config30.midAlpha, 0.001f)
        assertEquals(0.92f, config30.lowerAlpha, 0.001f)
        assertEquals(1.0f, config30.bottomAlpha, 0.001f)
    }

    @Test
    fun testArtworkMaxHeightAndDownscale() {
        assertEquals(290f, PcPlayerLogic.calculateArtworkMaxHeight(290f), 0.001f)

        val (w1, h1) = PcPlayerLogic.calculateDownscaledSize(1920, 1080, 720)
        assertEquals(720, w1)
        assertEquals(405, h1)

        val (w2, h2) = PcPlayerLogic.calculateDownscaledSize(500, 500, 720)
        assertEquals(500, w2)
        assertEquals(500, h2)
    }
}
