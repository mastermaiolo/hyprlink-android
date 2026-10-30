// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupportDonateLogicTest {

    @Test
    fun testIsSupportSectionVisible() {
        assertFalse(SupportDonateLogic.isSupportSectionVisible(null))
        assertFalse(SupportDonateLogic.isSupportSectionVisible(""))
        assertFalse(SupportDonateLogic.isSupportSectionVisible("   "))
        assertTrue(SupportDonateLogic.isSupportSectionVisible("https://ko-fi.com/mastermaggio"))
    }

    @Test
    fun testCreateDonateIntent() {
        val targetUrl = "https://ko-fi.com/mastermaggio"
        val intent = SupportDonateLogic.createDonateIntent(targetUrl)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(targetUrl), intent.data)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun testBuildConfigDonateUrl() {
        assertTrue(SupportDonateLogic.isSupportSectionVisible(BuildConfig.DONATE_URL))
        assertEquals("https://ko-fi.com/mastermaggio", BuildConfig.DONATE_URL)
    }
}
