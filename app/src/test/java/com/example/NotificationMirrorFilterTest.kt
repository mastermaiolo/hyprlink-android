// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationMirrorFilterTest {

    private fun createSbn(
        packageName: String,
        flags: Int = 0,
        key: String = "key_1"
    ): StatusBarNotification {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = Notification.Builder(context, "default_channel")
            .setContentTitle("Test Notification")
            .setContentText("Test Content")
            .build()
        notification.flags = flags

        return StatusBarNotification(
            packageName,
            "op_pkg",
            100,
            "tag",
            1000,
            0,
            0,
            notification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )
    }

    @Test
    fun testNormalNotification_isMirrored() {
        val sbn = createSbn(packageName = "com.thirdparty.app", flags = 0)
        assertTrue(HyprNotificationListenerService.shouldMirror(sbn, "com.example"))
    }

    @Test
    fun testOwnAppNotification_isNotMirrored() {
        val sbn = createSbn(packageName = "com.example", flags = 0)
        assertFalse(HyprNotificationListenerService.shouldMirror(sbn, "com.example"))
    }

    @Test
    fun testOngoingEventNotification_isNotMirrored() {
        val sbn = createSbn(
            packageName = "com.thirdparty.app",
            flags = Notification.FLAG_ONGOING_EVENT
        )
        assertFalse(HyprNotificationListenerService.shouldMirror(sbn, "com.example"))
    }

    @Test
    fun testGroupSummaryNotification_isNotMirrored() {
        val sbn = createSbn(
            packageName = "com.thirdparty.app",
            flags = Notification.FLAG_GROUP_SUMMARY
        )
        assertFalse(HyprNotificationListenerService.shouldMirror(sbn, "com.example"))
    }

    @Test
    fun testNoClearNotification_isNotMirrored() {
        val sbn = createSbn(
            packageName = "com.thirdparty.app",
            flags = Notification.FLAG_NO_CLEAR
        )
        assertFalse(HyprNotificationListenerService.shouldMirror(sbn, "com.example"))
    }
}
