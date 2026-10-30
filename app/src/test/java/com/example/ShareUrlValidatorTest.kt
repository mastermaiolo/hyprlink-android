// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareUrlValidatorTest {

    @Test
    fun testValidHttpUrl() {
        assertTrue(ShareUrlValidator.isValidUrl("http://example.com"))
        assertTrue(ShareUrlValidator.isValidUrl("http://localhost:8080/path?query=1"))
    }

    @Test
    fun testValidHttpsUrl() {
        assertTrue(ShareUrlValidator.isValidUrl("https://example.com"))
        assertTrue(ShareUrlValidator.isValidUrl("https://github.com/hyprlink/daemon#section"))
    }

    @Test
    fun testInvalidFileScheme() {
        assertFalse(ShareUrlValidator.isValidUrl("file:///etc/passwd"))
        assertFalse(ShareUrlValidator.isValidUrl("file://localhost/home/user/test.txt"))
    }

    @Test
    fun testInvalidIntentScheme() {
        assertFalse(ShareUrlValidator.isValidUrl("intent:#Intent;action=android.intent.action.VIEW;end"))
    }

    @Test
    fun testInvalidJavascriptScheme() {
        assertFalse(ShareUrlValidator.isValidUrl("javascript:alert(1)"))
    }

    @Test
    fun testInvalidWithoutHost() {
        assertFalse(ShareUrlValidator.isValidUrl("http://"))
        assertFalse(ShareUrlValidator.isValidUrl("https://"))
        assertFalse(ShareUrlValidator.isValidUrl("http:///path/only"))
    }

    @Test
    fun testInvalidLongUrl() {
        val longPath = "a".repeat(4090)
        val tooLongUrl = "https://example.com/$longPath"
        assertTrue(tooLongUrl.length > 4096)
        assertFalse(ShareUrlValidator.isValidUrl(tooLongUrl))
    }

    @Test
    fun testInvalidNullOrEmpty() {
        assertFalse(ShareUrlValidator.isValidUrl(null))
        assertFalse(ShareUrlValidator.isValidUrl(""))
        assertFalse(ShareUrlValidator.isValidUrl("   "))
    }

    @Test
    fun testInvalidWhitespaceOrControl() {
        assertFalse(ShareUrlValidator.isValidUrl("https://example.com/test with spaces"))
        assertFalse(ShareUrlValidator.isValidUrl("https://example.com/test\nnewline"))
    }
}
