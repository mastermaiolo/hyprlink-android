// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object PhoneStatusCollector {

    /**
     * Collects the current device telemetry conforming strictly to the phone.status contract.
     * All fields are optional: missing or unknown values are omitted (never 0 or "").
     */
    fun collect(context: Context, batteryIntent: Intent? = null, lightMode: Boolean = false): Map<String, Any> {
        val status = mutableMapOf<String, Any>()
        val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)

        // 1. Network & Connectivity
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)

        val networkType = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "none"
        }
        status["network"] = networkType

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (networkType == "cellular") {
            // cell_gen: only with mobile network and user permission
            val shareCellGen = prefs.getBoolean("share_cell_gen", false)
            val hasPhonePerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                               ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (shareCellGen && hasPhonePerm) {
                try {
                    val dataNetType = tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN

                    @Suppress("DEPRECATION")
                    val gen = when (dataNetType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                        TelephonyManager.NETWORK_TYPE_GPRS,
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
                        else -> null
                    }
                    if (gen != null) {
                        status["cell_gen"] = gen
                    }
                } catch (_: SecurityException) {}
            }

            // carrier
            val carrier = tm?.networkOperatorName?.trim()
            if (!carrier.isNullOrBlank()) {
                status["carrier"] = carrier
            }

            // signal_bars (uint 0-4)
            try {
                val signalStrength = tm?.signalStrength
                if (signalStrength != null) {
                    val level = signalStrength.level
                    if (level in 0..4) {
                        status["signal_bars"] = level.toLong()
                    }
                }
            } catch (_: Exception) {}
        } else if (networkType == "wifi") {
            try {
                val wifiInfo = (caps?.transportInfo as? WifiInfo) ?: run {
                    @Suppress("DEPRECATION")
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    @Suppress("DEPRECATION")
                    wm?.connectionInfo
                }

                if (wifiInfo != null) {
                    val wifiMap = mutableMapOf<String, Any>()

                    // SSID: only if user enabled sharing in Settings AND location permission is granted
                    val shareSsid = prefs.getBoolean("share_wifi_ssid", false)
                    val hasLocPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                     ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val strings = getAppStrings(LanguageManager.currentLanguage.value)
                    val ssidValue = WifiSsidLogic.resolveWifiSsid(
                        rawSsid = wifiInfo.ssid,
                        isLocationEnabled = WifiSsidLogic.isLocationServiceEnabled(context),
                        hasLocationPermission = hasLocPerm,
                        shareSsid = shareSsid,
                        locationDisabledMessage = strings.wifiLocationDisabledPrompt
                    )
                    if (ssidValue != null) {
                        wifiMap["ssid"] = ssidValue
                    }

                    // RSSI in dBm (negative integer, e.g. -61)
                    val rssi = wifiInfo.rssi
                    if (rssi in -127..-1) {
                        wifiMap["rssi_dbm"] = rssi.toLong()
                    }

                    if (wifiMap.isNotEmpty()) {
                        status["wifi"] = wifiMap
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Storage (Internal Data Directory) & 3. RAM (MemoryInfo)
        // Ignorados em lightMode (ecrã apagado) para evitar I/O de disco e IPC ao sistema
        if (!lightMode) {
            try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val totalBytes = stat.totalBytes
                val availableBytes = stat.availableBytes
                if (totalBytes > 0L) {
                    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
                    status["storage_used_b"] = usedBytes
                    status["storage_total_b"] = totalBytes
                }
            } catch (_: Exception) {}

            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                if (memInfo.totalMem > 0L) {
                    val usedMem = (memInfo.totalMem - memInfo.availMem).coerceAtLeast(0L)
                    status["ram_used_b"] = usedMem
                    status["ram_total_b"] = memInfo.totalMem
                }
            } catch (_: Exception) {}
        }

        // 4. Battery Temperature (Float °C)
        try {
            val bIntent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = bIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1000) ?: -1000
            if (rawTemp > -500) {
                val tempCelsius = rawTemp / 10.0f
                status["battery_temp_c"] = tempCelsius
            }
        } catch (_: Exception) {}

        // 5. Screen Interactive State
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                status["screen_on"] = pm.isInteractive
            }
        } catch (_: Exception) {}

        // 6. Do Not Disturb (DND)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                val filter = nm.currentInterruptionFilter
                status["dnd"] = (filter != NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (_: Exception) {}

        // 7. Active Clearable Notifications (using unified shouldMirror)
        try {
            val service = HyprNotificationListenerService.instance
            val sbns = service?.activeNotifications ?: HyprNotificationListenerService.cachedNotifications.values.toTypedArray()
            val clearable = sbns.filter { sbn ->
                HyprNotificationListenerService.shouldMirror(sbn, context.packageName)
            }
            status["notifications"] = clearable.size.toLong()
        } catch (_: Exception) {}

        // 8. Now Playing (Active Media Session)
        try {
            val nowPlaying = collectNowPlaying(context)
            if (nowPlaying != null) {
                status["now_playing"] = nowPlaying
            }
        } catch (_: Exception) {}

        return status
    }

    fun getActiveMediaController(context: Context): MediaController? {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
            val component = ComponentName(context, HyprNotificationListenerService::class.java)
            val sessions = msm.getActiveSessions(component)
            val externalSessions = sessions.filter { it.packageName != context.packageName && it.tag != "HyprLink_PC" && it.tag != "PC" }
            if (externalSessions.isEmpty()) return null

            externalSessions.firstOrNull { controller ->
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: externalSessions.firstOrNull { controller ->
                val state = controller.playbackState?.state
                state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
            } ?: externalSessions.firstOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun collectNowPlaying(context: Context): Map<String, Any>? {
        return try {
            val controller = getActiveMediaController(context) ?: return null
            val metadata = controller.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
            if (title.isNullOrBlank()) return null

            val map = mutableMapOf<String, Any>()
            map["title"] = title

            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            if (!artist.isNullOrBlank()) {
                map["artist"] = artist
            }

            val app = controller.packageName
            if (!app.isNullOrBlank()) {
                map["app"] = app
            }

            val playbackState = controller.playbackState
            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
            map["playing"] = isPlaying

            val rawPos = playbackState?.position ?: 0L
            val lastUpdateTime = playbackState?.lastPositionUpdateTime ?: 0L
            val speed = playbackState?.playbackSpeed ?: 1.0f
            val posMs = if (isPlaying && lastUpdateTime > 0L) {
                val elapsed = SystemClock.elapsedRealtime() - lastUpdateTime
                (rawPos + (elapsed * speed).toLong()).coerceAtLeast(0L)
            } else {
                rawPos.coerceAtLeast(0L)
            }
            map["position_ms"] = posMs

            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            if (duration > 0L) {
                map["duration_ms"] = duration
            }

            map
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
