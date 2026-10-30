// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class PhotoAccessStatus {
    FULL,
    PARTIAL,
    DENIED
}

object PhotoAccessLogic {
    fun checkPhotoAccess(context: Context): PhotoAccessStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fullGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED

            if (fullGranted) return PhotoAccessStatus.FULL

            val partialGranted = ContextCompat.checkSelfPermission(
                context,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            ) == PackageManager.PERMISSION_GRANTED

            return if (partialGranted) PhotoAccessStatus.PARTIAL else PhotoAccessStatus.DENIED
        } else {
            val fullGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            return if (fullGranted) PhotoAccessStatus.FULL else PhotoAccessStatus.DENIED
        }
    }

    fun canAutoSyncScreenshots(context: Context): Boolean {
        return checkPhotoAccess(context) == PhotoAccessStatus.FULL
    }
}
