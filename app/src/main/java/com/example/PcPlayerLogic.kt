// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

sealed class ArtworkResolution {
    data class DecodeNew(val bytes: ByteArray, val artId: String?) : ArtworkResolution()
    data class ReuseCache(val artId: String) : ArtworkResolution()
    object ClearCache : ArtworkResolution()
}

object PcPlayerLogic {

    fun shouldShowNotification(
        status: String?,
        isConnected: Boolean,
        isSettingEnabled: Boolean,
        hasTitle: Boolean
    ): Boolean {
        if (!isConnected) return false
        if (!isSettingEnabled) return false
        if (!hasTitle) return false
        val s = status?.trim() ?: return false
        return s.equals("Playing", ignoreCase = true) || s.equals("Paused", ignoreCase = true)
    }

    fun resolveEffectiveStatus(
        pcStatus: String?,
        optimisticStatus: String?
    ): String {
        if (optimisticStatus != null) {
            return optimisticStatus
        }
        return pcStatus ?: "Stopped"
    }

    fun isPlaying(status: String?): Boolean {
        return status?.equals("Playing", ignoreCase = true) == true
    }

    fun togglePlayPause(currentStatus: String?): String {
        return if (isPlaying(currentStatus)) "Paused" else "Playing"
    }

    fun resolveArtworkAction(
        incomingBytes: ByteArray?,
        incomingArtId: String?,
        cachedArtId: String?,
        hasCachedBitmap: Boolean
    ): ArtworkResolution {
        if (incomingBytes != null && incomingBytes.isNotEmpty()) {
            return ArtworkResolution.DecodeNew(incomingBytes, incomingArtId)
        }
        if (!incomingArtId.isNullOrBlank() && incomingArtId == cachedArtId && hasCachedBitmap) {
            return ArtworkResolution.ReuseCache(incomingArtId)
        }
        return ArtworkResolution.ClearCache
    }

    fun determineArtworkShape(width: Int?, height: Int?): ArtworkShape {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return ArtworkShape.NONE
        }
        return when {
            width == height -> ArtworkShape.SQUARE
            width > height -> ArtworkShape.WIDE
            else -> ArtworkShape.TALL
        }
    }

    fun resolveVeilConfig(sdkInt: Int): VeilConfig {
        return if (sdkInt >= 31) {
            VeilConfig(
                blurDp = 32,
                topAlpha = 0.30f,
                midAlpha = 0.55f,
                midPoint = 0.50f,
                lowerAlpha = 0.92f,
                lowerPoint = 0.85f,
                bottomAlpha = 1.0f
            )
        } else {
            VeilConfig(
                blurDp = 0,
                topAlpha = 0.60f,
                midAlpha = 0.80f,
                midPoint = 0.50f,
                lowerAlpha = 0.92f,
                lowerPoint = 0.85f,
                bottomAlpha = 1.0f
            )
        }
    }

    fun calculateArtworkMaxHeight(maxHeightDp: Float = 290f): Float {
        return maxHeightDp
    }

    fun calculateDownscaledSize(originalWidth: Int, originalHeight: Int, maxDimension: Int = 720): Pair<Int, Int> {
        if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
            return Pair(originalWidth, originalHeight)
        }
        val ratio = originalWidth.toFloat() / originalHeight.toFloat()
        return if (originalWidth >= originalHeight) {
            val w = maxDimension
            val h = (maxDimension / ratio).toInt().coerceAtLeast(1)
            Pair(w, h)
        } else {
            val h = maxDimension
            val w = (maxDimension * ratio).toInt().coerceAtLeast(1)
            Pair(w, h)
        }
    }
}

enum class ArtworkShape {
    NONE,
    SQUARE,
    WIDE,
    TALL
}

data class VeilConfig(
    val blurDp: Int,
    val topAlpha: Float,
    val midAlpha: Float,
    val midPoint: Float = 0.50f,
    val lowerAlpha: Float = 0.92f,
    val lowerPoint: Float = 0.85f,
    val bottomAlpha: Float = 1.0f
)
