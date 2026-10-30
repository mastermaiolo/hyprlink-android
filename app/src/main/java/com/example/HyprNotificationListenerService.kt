// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class HyprNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "HyprNotificationListener"

        // Cache for active notifications so we can trigger actions or dismiss them
        val cachedNotifications = ConcurrentHashMap<String, StatusBarNotification>()

        // Reference to active listener service instance
        @Volatile
        var instance: HyprNotificationListenerService? = null
            private set

        fun shouldMirror(sbn: StatusBarNotification, ownPackage: String): Boolean {
            val notification = sbn.notification ?: return false
            if (sbn.packageName == ownPackage) {
                return false
            }
            val flags = notification.flags
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
                return false
            }
            if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                return false
            }
            if ((flags and Notification.FLAG_NO_CLEAR) != 0) {
                return false
            }
            return true
        }

        fun buildPostBody(context: Context, sbn: StatusBarNotification, isReplay: Boolean = false): Map<String, Any> {
            val notification = sbn.notification
            val title = notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            var rawText = notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (rawText.isBlank()) {
                val bigText = notification?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                if (!bigText.isNullOrBlank()) {
                    rawText = bigText
                }
            }
            val text = if (rawText.length > 2000) rawText.substring(0, 2000) else rawText

            var appName = sbn.packageName
            try {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(sbn.packageName, 0)
                appName = pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {}

            val rawActions = notification?.actions
            val actionList = mutableListOf<Map<String, Any>>()
            if (rawActions != null) {
                for (i in rawActions.indices) {
                    val action = rawActions[i] ?: continue
                    val hasRemoteInput = action.remoteInputs != null && action.remoteInputs.isNotEmpty()
                    actionList.add(mapOf(
                        "idx" to i,
                        "label" to (action.title?.toString() ?: "Action"),
                        "is_reply" to hasRemoteInput
                    ))
                }
            }

            val body = mutableMapOf<String, Any>(
                "key" to sbn.key,
                "app" to appName,
                "title" to title,
                "text" to text,
                "actions" to actionList
            )
            if (isReplay) {
                body["replay"] = true
            }
            return body
        }

        suspend fun replayActiveNotifications(context: Context) {
            val sharedPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            val isMirrorEnabled = sharedPrefs.getBoolean("mirror_notifications", true)
            if (!isMirrorEnabled) {
                return
            }

            if (ConnectionRepository.connectionStatus.value != ConnectionStatus.CONNECTED ||
                ConnectionRepository.activeConnection == null) {
                return
            }

            val service = instance
            val sbns = service?.activeNotifications ?: cachedNotifications.values.toTypedArray()
            if (sbns.isEmpty()) {
                return
            }

            val candidates = sbns
                .filter { shouldMirror(it, context.packageName) }
                .sortedBy { it.postTime }
                .takeLast(50)

            if (candidates.isEmpty()) {
                return
            }

            for (sbn in candidates) {
                try {
                    val body = buildPostBody(context, sbn, isReplay = true)
                    ConnectionRepository.sendOneWayPacket("notification.post", body)
                    delay(30)
                } catch (e: Exception) {
                    Log.e(TAG, "Error replaying active notification ${sbn.key}: ${e.message}")
                }
            }

            val strings = getAppStrings(LanguageManager.currentLanguage.value)
            val logMsg = strings.logActiveNotificationsReplayed.replace("{n}", candidates.size.toString())
            ConnectionRepository.appendLog(logMsg)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "HyprNotificationListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "HyprNotificationListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "HyprNotificationListenerService connected")
        // Cache all currently active notifications
        try {
            val sbns = activeNotifications
            cachedNotifications.clear()
            if (sbns != null) {
                for (sbn in sbns) {
                    cachedNotifications[sbn.key] = sbn
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error caching active notifications: ${e.message}")
        }

        if (ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED) {
            serviceScope.launch {
                replayActiveNotifications(applicationContext)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        cachedNotifications.clear()
        Log.d(TAG, "HyprNotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        // Cache it
        cachedNotifications[sbn.key] = sbn
        HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()

        // Check if mirroring is enabled
        val sharedPrefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val isMirrorEnabled = sharedPrefs.getBoolean("mirror_notifications", true)
        if (!isMirrorEnabled) {
            return
        }

        // Check active connection
        if (ConnectionRepository.activeConnection == null ||
            ConnectionRepository.connectionStatus.value != ConnectionStatus.CONNECTED) {
            return
        }

        if (!shouldMirror(sbn, packageName)) {
            return
        }

        val body = buildPostBody(this, sbn, isReplay = false)
        val appName = body["app"] as? String ?: sbn.packageName
        val title = body["title"] as? String ?: ""

        serviceScope.launch {
            try {
                ConnectionRepository.sendOneWayPacket("notification.post", body)
                ConnectionRepository.incrementMirroredCount()
                ConnectionRepository.appendLog("[NOTIF] Mirrored notification from $appName: $title")
                ConnectionRepository.appendLog("Notificação espelhada: $appName")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending notification post: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        
        cachedNotifications.remove(sbn.key)
        HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()

        // Check if mirroring is enabled
        val sharedPrefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val isMirrorEnabled = sharedPrefs.getBoolean("mirror_notifications", true)
        if (!isMirrorEnabled) {
            return
        }

        // Check active connection
        val activeConn = ConnectionRepository.activeConnection ?: return

        val pkg = sbn.packageName
        if (pkg == packageName) {
            return
        }

        // Send dismiss notification to PC
        val body = mapOf("key" to sbn.key)
        serviceScope.launch {
            try {
                ConnectionRepository.sendOneWayPacket("notification.dismissed", body)
                ConnectionRepository.appendLog("[NOTIF] Notification dismissed: ${sbn.key}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending notification dismiss: ${e.message}")
            }
        }
    }
}
