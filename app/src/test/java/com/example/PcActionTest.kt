// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcActionTest {

    enum class QuickLaunchButton {
        LOCK,
        SUSPEND,
        SCREENSHOT,
        SCREENSHOT_AREA
    }

    private fun mapButtonToAction(button: QuickLaunchButton): String {
        return when (button) {
            QuickLaunchButton.LOCK -> "lock"
            QuickLaunchButton.SUSPEND -> "suspend"
            QuickLaunchButton.SCREENSHOT -> "screenshot"
            QuickLaunchButton.SCREENSHOT_AREA -> "screenshot_area"
        }
    }

    @Test
    fun testButtonToActionMapping() {
        assertEquals("lock", mapButtonToAction(QuickLaunchButton.LOCK))
        assertEquals("suspend", mapButtonToAction(QuickLaunchButton.SUSPEND))
        assertEquals("screenshot", mapButtonToAction(QuickLaunchButton.SCREENSHOT))
        assertEquals("screenshot_area", mapButtonToAction(QuickLaunchButton.SCREENSHOT_AREA))
    }

    @Test
    fun testPcActionResultSuccess() {
        val result = PcActionResult(ok = true, error = null)
        assertTrue(result.ok)
        assertEquals(null, result.error)
    }

    @Test
    fun testPcActionResultFailureWithError() {
        val result = PcActionResult(ok = false, error = "no grim tool found")
        assertFalse(result.ok)
        assertEquals("no grim tool found", result.error)
    }

    @Test
    fun testFallbackDecisionOnUnknownCommand() {
        fun shouldFallback(res: PcActionResult?, exception: Throwable?): Boolean {
            if (res != null && !res.ok && res.error?.contains("unknown", ignoreCase = true) == true) {
                return true
            }
            if (exception != null && exception.message?.contains("unknown", ignoreCase = true) == true) {
                return true
            }
            return false
        }

        assertTrue(shouldFallback(PcActionResult(ok = false, error = "unknown command"), null))
        assertTrue(shouldFallback(null, Exception("unknown command: pc.action")))
        assertFalse(shouldFallback(PcActionResult(ok = false, error = "cancelled"), null))
        assertFalse(shouldFallback(PcActionResult(ok = true), null))
    }

    @Test
    fun testFeedbackMessageFormatting() {
        val strings = StringsPtPt()

        fun formatFeedback(result: PcActionResult): String {
            return if (result.ok) {
                strings.actionDone
            } else {
                "${strings.actionFailed}: ${result.error ?: strings.toastShortcutFailed}"
            }
        }

        assertEquals("Feito", formatFeedback(PcActionResult(ok = true)))
        assertEquals("Falhou: no grim tool found", formatFeedback(PcActionResult(ok = false, error = "no grim tool found")))
        assertEquals("Falhou: ${strings.toastShortcutFailed}", formatFeedback(PcActionResult(ok = false, error = null)))
    }
}
