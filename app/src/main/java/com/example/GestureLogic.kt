// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

object GestureLogic {
    const val SWIPE_3_THRESHOLD_DP = 80f
    const val DOUBLE_TAP_BACK_DEBOUNCE_MS = 500L

    // KeyCodes padrão do Android (24 = VOLUME_UP, 25 = VOLUME_DOWN)
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25

    /**
     * Avalia deslize com 3 dedos dado o deltaX acumulado (em dp).
     * Devolve "swipe_left_3" se deltaX <= -80 dp,
     * "swipe_right_3" se deltaX >= 80 dp,
     * ou null se abaixo do limiar ou se pointerCount != 3.
     */
    fun evaluate3FingerSwipe(pointerCount: Int, deltaXDp: Float): String? {
        if (pointerCount != 3) return null
        return when {
            deltaXDp <= -SWIPE_3_THRESHOLD_DP -> "swipe_left_3"
            deltaXDp >= SWIPE_3_THRESHOLD_DP -> "swipe_right_3"
            else -> null
        }
    }

    /**
     * Mapeia código de tecla KeyEvent para a direção do gesto de volume.
     * KEYCODE_VOLUME_UP (24) -> "up"
     * KEYCODE_VOLUME_DOWN (25) -> "down"
     * Qualquer outra -> null
     */
    fun mapVolumeKeyCode(keyCode: Int): String? {
        return when (keyCode) {
            KEYCODE_VOLUME_UP -> "up"
            KEYCODE_VOLUME_DOWN -> "down"
            else -> null
        }
    }

    /**
     * Verifica debounce de 500 ms para toques/eventos repetidos.
     * Retorna true se passou tempo suficiente desde o último evento.
     */
    fun shouldTriggerDebouncedEvent(lastTriggerMs: Long, currentMs: Long, debounceMs: Long = DOUBLE_TAP_BACK_DEBOUNCE_MS): Boolean {
        if (lastTriggerMs == 0L) return true
        return (currentMs - lastTriggerMs) >= debounceMs
    }

    /**
     * Deteção de toque duplo no verso através de dois picos de aceleração separados por entre 100 e 400 ms.
     */
    fun isDoubleTapPeak(firstPeakMs: Long, secondPeakMs: Long): Boolean {
        val diff = secondPeakMs - firstPeakMs
        return diff in 100L..400L
    }
}
