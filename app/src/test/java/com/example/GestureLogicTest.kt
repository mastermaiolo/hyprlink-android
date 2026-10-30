// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureLogicTest {

    @Test
    fun test3FingerSwipeDetectionAndThreshold() {
        // Exatamente 3 dedos: limiar de 80 dp
        assertNull(GestureLogic.evaluate3FingerSwipe(pointerCount = 3, deltaXDp = -79.9f))
        assertEquals("swipe_left_3", GestureLogic.evaluate3FingerSwipe(pointerCount = 3, deltaXDp = -80.0f))
        assertEquals("swipe_left_3", GestureLogic.evaluate3FingerSwipe(pointerCount = 3, deltaXDp = -120.0f))

        assertNull(GestureLogic.evaluate3FingerSwipe(pointerCount = 3, deltaXDp = 79.9f))
        assertEquals("swipe_right_3", GestureLogic.evaluate3FingerSwipe(pointerCount = 3, deltaXDp = 80.0f))
        assertEquals("swipe_right_3", GestureLogic.evaluate3FingerSwipe(pointerCount = 3, deltaXDp = 150.0f))

        // Não deve disparar se não forem exatamente 3 dedos
        assertNull(GestureLogic.evaluate3FingerSwipe(pointerCount = 1, deltaXDp = 100.0f))
        assertNull(GestureLogic.evaluate3FingerSwipe(pointerCount = 2, deltaXDp = 100.0f))
        assertNull(GestureLogic.evaluate3FingerSwipe(pointerCount = 4, deltaXDp = 100.0f))
        assertNull(GestureLogic.evaluate3FingerSwipe(pointerCount = 2, deltaXDp = -100.0f))
    }

    @Test
    fun testVolumeKeyMapping() {
        assertEquals("up", GestureLogic.mapVolumeKeyCode(GestureLogic.KEYCODE_VOLUME_UP))
        assertEquals("down", GestureLogic.mapVolumeKeyCode(GestureLogic.KEYCODE_VOLUME_DOWN))

        // Outras teclas devem retornar null
        assertNull(GestureLogic.mapVolumeKeyCode(66)) // ENTER
        assertNull(GestureLogic.mapVolumeKeyCode(4))  // BACK
        assertNull(GestureLogic.mapVolumeKeyCode(0))
    }

    @Test
    fun testDebounce500ms() {
        // Primeiro evento sempre passa
        assertTrue(GestureLogic.shouldTriggerDebouncedEvent(lastTriggerMs = 0L, currentMs = 100L))

        // Evento antes de 500 ms não deve passar
        assertFalse(GestureLogic.shouldTriggerDebouncedEvent(lastTriggerMs = 1000L, currentMs = 1300L)) // 300 ms
        assertFalse(GestureLogic.shouldTriggerDebouncedEvent(lastTriggerMs = 1000L, currentMs = 1499L)) // 499 ms

        // Evento após 500 ms deve passar
        assertTrue(GestureLogic.shouldTriggerDebouncedEvent(lastTriggerMs = 1000L, currentMs = 1500L)) // 500 ms
        assertTrue(GestureLogic.shouldTriggerDebouncedEvent(lastTriggerMs = 1000L, currentMs = 1800L)) // 800 ms
    }

    @Test
    fun testDoubleTapPeak() {
        // Toque rápido demais (< 100 ms)
        assertFalse(GestureLogic.isDoubleTapPeak(firstPeakMs = 1000L, secondPeakMs = 1050L))

        // Intervalo válido (100 ms .. 400 ms)
        assertTrue(GestureLogic.isDoubleTapPeak(firstPeakMs = 1000L, secondPeakMs = 1150L))
        assertTrue(GestureLogic.isDoubleTapPeak(firstPeakMs = 1000L, secondPeakMs = 1300L))
        assertTrue(GestureLogic.isDoubleTapPeak(firstPeakMs = 1000L, secondPeakMs = 1400L))

        // Toque lento demais (> 400 ms)
        assertFalse(GestureLogic.isDoubleTapPeak(firstPeakMs = 1000L, secondPeakMs = 1450L))
    }
}
