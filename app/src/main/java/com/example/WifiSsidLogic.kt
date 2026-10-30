// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import android.location.LocationManager
import android.os.Build

object WifiSsidLogic {
    fun isLocationServiceEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            lm.isLocationEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun resolveWifiSsid(
        rawSsid: String?,
        isLocationEnabled: Boolean,
        hasLocationPermission: Boolean,
        shareSsid: Boolean,
        locationDisabledMessage: String
    ): String? {
        if (!shareSsid) return null
        if (!hasLocationPermission) return null
        if (!isLocationEnabled) {
            return locationDisabledMessage
        }
        val clean = rawSsid?.trim('\"', ' ')
        if (clean.isNullOrBlank() || clean == "<unknown ssid>") {
            return null
        }
        return clean
    }
}
