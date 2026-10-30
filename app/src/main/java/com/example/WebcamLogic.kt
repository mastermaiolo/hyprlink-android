// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

object WebcamLogic {

    fun translateWebcamError(error: String?, strings: AppStrings): String {
        val normalized = error?.trim()?.lowercase() ?: ""
        return when {
            normalized == "already active" -> strings.webcamErrorAlreadyActive
            normalized == "no phone connected" -> strings.webcamErrorNoPhone
            normalized == "no v4l2loopback" -> strings.webcamErrorNoV4l2
            normalized == "timeout" || normalized.contains("timed out") -> strings.webcamErrorTimeout
            normalized.isEmpty() -> strings.webcamErrorGeneric
            else -> "${strings.webcamErrorGeneric} ($error)"
        }
    }

    fun buildWebcamRequestBody(
        width: Int? = null,
        height: Int? = null,
        fps: Int? = null,
        codec: String? = null
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>()
        if (width != null && width > 0) body["width"] = width
        if (height != null && height > 0) body["height"] = height
        if (fps != null && fps > 0) body["fps"] = fps
        if (codec != null && codec.isNotBlank() && codec != "auto") body["codec"] = codec
        return body
    }

    data class WebcamConfigPacket(
        val width: Int,
        val height: Int,
        val fps: Int,
        val codec: String
    )

    data class WebcamStatePacket(
        val width: Int,
        val height: Int,
        val fps: Int,
        val codec: String,
        val lens: String,
        val rotation: Int,
        val mirror: Boolean
    )

    fun parseWebcamConfig(
        body: Map<String, Any?>?,
        defaultWidth: Int = 1280,
        defaultHeight: Int = 720,
        defaultFps: Int = 30,
        defaultCodec: String = "h264"
    ): WebcamConfigPacket {
        val width = (body?.get("width") as? Number)?.toInt() ?: defaultWidth
        val height = (body?.get("height") as? Number)?.toInt() ?: defaultHeight
        val fps = (body?.get("fps") as? Number)?.toInt() ?: defaultFps
        val codec = (body?.get("codec") as? String)?.lowercase()?.takeIf { it.isNotBlank() } ?: defaultCodec
        return WebcamConfigPacket(width, height, fps, codec)
    }

    fun buildWebcamStateBody(
        width: Int,
        height: Int,
        fps: Int,
        codec: String,
        lens: String,
        rotation: Int,
        mirror: Boolean
    ): Map<String, Any> {
        val normCodec = if (codec.equals("H.265", ignoreCase = true) || codec.equals("h265", ignoreCase = true)) "h265" else "h264"
        val normLens = lens.lowercase().trim()
        val validLens = if (normLens in listOf("back", "front", "other")) normLens else "back"
        return mapOf(
            "width" to width,
            "height" to height,
            "fps" to fps,
            "codec" to normCodec,
            "lens" to validLens,
            "rotation" to rotation,
            "mirror" to mirror
        )
    }

    data class HighSpeedCapability(
        val size: Pair<Int, Int>,
        val fpsRanges: List<Pair<Int, Int>>
    )

    data class WebcamModeDecision(
        val isHighSpeed: Boolean,
        val width: Int,
        val height: Int,
        val fps: Int,
        val codec: String,
        val aeRange: Pair<Int, Int>,
        val limitationReason: String? = null
    )

    data class EffectiveWebcamFormat(
        val width: Int,
        val height: Int,
        val fps: Int,
        val codec: String,
        val aeRange: Pair<Int, Int>,
        val limitationReason: String? = null,
        val isHighSpeed: Boolean = false
    )

    data class EffectiveFpsResult(
        val fps: Int,
        val aeRange: Pair<Int, Int>,
        val limitationReason: String? = null
    )

    fun resolveEffectiveCodec(requestedCodec: String, hasHevcHardware: Boolean): String {
        val wanted = requestedCodec.lowercase().trim()
        return if (wanted == "h265" && hasHevcHardware) "h265" else "h264"
    }

    fun resolveEffectiveResolution(
        requestedWidth: Int,
        requestedHeight: Int,
        supportedResolutions: List<Pair<Int, Int>>
    ): Pair<Int, Int> {
        if (supportedResolutions.isEmpty()) {
            return Pair(requestedWidth, requestedHeight)
        }
        val exactMatch = supportedResolutions.firstOrNull { it.first == requestedWidth && it.second == requestedHeight }
        if (exactMatch != null) return exactMatch

        val targetArea = requestedWidth * requestedHeight
        return supportedResolutions.minByOrNull { kotlin.math.abs((it.first * it.second) - targetArea) }
            ?: Pair(requestedWidth, requestedHeight)
    }

    fun resolveEffectiveFpsAndRangeWithReason(
        requestedFps: Int,
        availableRanges: List<Pair<Int, Int>>,
        lensFacing: String = "traseira",
        height: Int = 720
    ): EffectiveFpsResult {
        if (availableRanges.isEmpty()) {
            val lower = (requestedFps / 2).coerceAtLeast(1)
            return EffectiveFpsResult(requestedFps, Pair(lower, requestedFps), null)
        }

        val maxAvailableFps = availableRanges.maxOf { it.second }
        val effectiveFps = requestedFps.coerceAtMost(maxAvailableFps)

        // Preferir ranges com upper == effectiveFps.
        // Se houver mais de um (ex: [14, 30] e [30, 30], ou [30, 60] e [60, 60]),
        // preferir o com menor lower para dar mais margem de exposição em baixa luz.
        val matchingRanges = availableRanges.filter { it.second == effectiveFps }
        val chosenRange = if (matchingRanges.isNotEmpty()) {
            matchingRanges.minByOrNull { it.first } ?: matchingRanges.first()
        } else {
            // Se nenhum tiver upper exatamente igual, pegar o mais próximo
            availableRanges.minByOrNull { kotlin.math.abs(it.second - effectiveFps) } ?: availableRanges.first()
        }

        val reason = if (requestedFps > effectiveFps) {
            "a câmara $lensFacing só faz $effectiveFps fps a ${height}p"
        } else {
            null
        }

        return EffectiveFpsResult(effectiveFps, chosenRange, reason)
    }

    fun resolveEffectiveFpsAndRange(
        requestedFps: Int,
        availableRanges: List<Pair<Int, Int>>
    ): Pair<Int, Pair<Int, Int>> {
        val result = resolveEffectiveFpsAndRangeWithReason(requestedFps, availableRanges)
        return Pair(result.fps, result.aeRange)
    }

    fun calculateCameraRotation(
        sensorOrientation: Int,
        isFrontCamera: Boolean,
        deviceRotationDegrees: Int
    ): Int {
        val normDeviceRot = ((deviceRotationDegrees % 360) + 360) % 360
        val normSensorRot = ((sensorOrientation % 360) + 360) % 360
        return if (isFrontCamera) {
            (normSensorRot + normDeviceRot) % 360
        } else {
            (normSensorRot - normDeviceRot + 360) % 360
        }
    }

    fun resolveEffectiveDimensions(
        baseWidth: Int,
        baseHeight: Int,
        rotation: Int
    ): Pair<Int, Int> {
        val isVertical = rotation == 90 || rotation == 270
        val (longSide, shortSide) = if (baseWidth >= baseHeight) {
            Pair(baseWidth, baseHeight)
        } else {
            Pair(baseHeight, baseWidth)
        }

        return if (isVertical) {
            Pair(shortSide, longSide)
        } else {
            Pair(longSide, shortSide)
        }
    }

    fun resolveWebcamMode(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        requestedCodec: String,
        supportedResolutions: List<Pair<Int, Int>> = emptyList(),
        availableNormalFpsRanges: List<Pair<Int, Int>> = emptyList(),
        highSpeedCapabilities: List<HighSpeedCapability> = emptyList(),
        hasHevcHardwareEncoder: Boolean = true,
        lensFacing: String = "traseira"
    ): WebcamModeDecision {
        val effectiveCodec = resolveEffectiveCodec(requestedCodec, hasHevcHardwareEncoder)
        val (effWidth, effHeight) = resolveEffectiveResolution(requestedWidth, requestedHeight, supportedResolutions)
        val isFront = lensFacing.contains("front", ignoreCase = true)

        if (requestedFps >= 60) {
            // Regra 4: frontal a 1080p60 não existe -> mantém a resolução (1080p) e baixa para 30 fps
            if (isFront && effHeight >= 1080) {
                val normalFps = resolveEffectiveFpsAndRangeWithReason(30, availableNormalFpsRanges, lensFacing, effHeight)
                return WebcamModeDecision(
                    isHighSpeed = false,
                    width = effWidth,
                    height = effHeight,
                    fps = 30,
                    codec = effectiveCodec,
                    aeRange = normalFps.aeRange,
                    limitationReason = "a câmara frontal só faz 60 fps a 720p e 480p"
                )
            }

            // Verifica se há capacidade de alta velocidade com faixa de 120 fps para esta resolução
            val hsMatch = highSpeedCapabilities.firstOrNull { it.size == Pair(effWidth, effHeight) }
            val has120Range = hsMatch != null && hsMatch.fpsRanges.any { it.second >= 120 }

            if (has120Range) {
                return WebcamModeDecision(
                    isHighSpeed = true,
                    width = effWidth,
                    height = effHeight,
                    fps = 60,
                    codec = effectiveCodec,
                    aeRange = Pair(120, 120),
                    limitationReason = null
                )
            } else {
                // Sem configuração de alta velocidade para a resolução pedida -> baixa para 30 fps com motivo
                val normalFps = resolveEffectiveFpsAndRangeWithReason(30, availableNormalFpsRanges, lensFacing, effHeight)
                return WebcamModeDecision(
                    isHighSpeed = false,
                    width = effWidth,
                    height = effHeight,
                    fps = 30,
                    codec = effectiveCodec,
                    aeRange = normalFps.aeRange,
                    limitationReason = "a câmara $lensFacing só faz 30 fps a ${effHeight}p"
                )
            }
        } else {
            // Pedido < 60 fps (ex: 30 ou 24): nunca alta velocidade
            val normalFps = resolveEffectiveFpsAndRangeWithReason(requestedFps, availableNormalFpsRanges, lensFacing, effHeight)
            return WebcamModeDecision(
                isHighSpeed = false,
                width = effWidth,
                height = effHeight,
                fps = normalFps.fps,
                codec = effectiveCodec,
                aeRange = normalFps.aeRange,
                limitationReason = normalFps.limitationReason
            )
        }
    }

    fun resolveEffectiveFormat(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        requestedCodec: String,
        supportedResolutions: List<Pair<Int, Int>> = emptyList(),
        availableFpsRanges: List<Pair<Int, Int>> = emptyList(),
        highSpeedCapabilities: List<HighSpeedCapability> = emptyList(),
        hasHevcHardwareEncoder: Boolean = true,
        lensFacing: String = "traseira"
    ): EffectiveWebcamFormat {
        val decision = resolveWebcamMode(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            requestedFps = requestedFps,
            requestedCodec = requestedCodec,
            supportedResolutions = supportedResolutions,
            availableNormalFpsRanges = availableFpsRanges,
            highSpeedCapabilities = highSpeedCapabilities,
            hasHevcHardwareEncoder = hasHevcHardwareEncoder,
            lensFacing = lensFacing
        )

        return EffectiveWebcamFormat(
            width = decision.width,
            height = decision.height,
            fps = decision.fps,
            codec = decision.codec,
            aeRange = decision.aeRange,
            limitationReason = decision.limitationReason,
            isHighSpeed = decision.isHighSpeed
        )
    }

    enum class FindPhoneTorchAction {
        CAMERA_MANAGER_TORCH,
        WEBCAM_CAPTURE_REQUEST,
        FRONT_CAMERA_SCREEN_LIGHT,
        UNAVAILABLE
    }

    data class FindPhoneTorchDecision(
        val action: FindPhoneTorchAction,
        val error: String? = null,
        val activateScreenLight: Boolean = false
    )

    fun resolveFindPhoneTorch(
        isWebcamStreaming: Boolean,
        isFrontCamera: Boolean,
        hasFlashUnit: Boolean
    ): FindPhoneTorchDecision {
        if (!isWebcamStreaming) {
            return if (hasFlashUnit) {
                FindPhoneTorchDecision(FindPhoneTorchAction.CAMERA_MANAGER_TORCH)
            } else {
                FindPhoneTorchDecision(FindPhoneTorchAction.UNAVAILABLE, error = "sem lanterna")
            }
        }
        // Webcam ativa
        if (isFrontCamera || !hasFlashUnit) {
            return FindPhoneTorchDecision(
                action = FindPhoneTorchAction.FRONT_CAMERA_SCREEN_LIGHT,
                error = "torch unavailable: front camera",
                activateScreenLight = true
            )
        }
        return FindPhoneTorchDecision(
            action = FindPhoneTorchAction.WEBCAM_CAPTURE_REQUEST,
            error = null,
            activateScreenLight = false
        )
    }

    /**
     * Ordena uma lista de nomes de encoders candidatos priorizando os encoders
     * modernos Codec 2.0 ("c2.") sobre implementações legadas OMX ("OMX.").
     */
    fun rankEncoderCandidates(candidateNames: List<String>): List<String> {
        return candidateNames.sortedWith(
            compareByDescending<String> { it.startsWith("c2.", ignoreCase = true) }
                .thenBy { it.startsWith("OMX.", ignoreCase = true) }
        )
    }
}
