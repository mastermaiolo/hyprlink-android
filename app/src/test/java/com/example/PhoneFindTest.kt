// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import com.example.find.PhoneFindRequest
import com.example.find.PhoneFindState
import com.example.find.PhoneFindStopReason
import org.junit.Assert.*
import org.junit.Test

class PhoneFindTest {

    @Test
    fun testValidationAllModesFalse() {
        val req = PhoneFindRequest(sound = false, vibrate = false, torch = false)
        assertFalse("Sem modos ativos deve ser inválido", req.isValid)

        val parsed = PhoneFindRequest.fromMap(
            mapOf("sound" to false, "vibrate" to false, "torch" to false)
        )
        assertNull("fromMap deve retornar null se nenhum modo for verdadeiro", parsed)
    }

    @Test
    fun testValidationSingleModes() {
        val soundOnly = PhoneFindRequest.fromMap(mapOf("sound" to true))
        assertNotNull(soundOnly)
        assertTrue(soundOnly!!.sound)
        assertFalse(soundOnly.vibrate)
        assertFalse(soundOnly.torch)
        assertEquals(30, soundOnly.durationS)

        val vibrateOnly = PhoneFindRequest.fromMap(mapOf("vibrate" to true))
        assertNotNull(vibrateOnly)
        assertFalse(vibrateOnly!!.sound)
        assertTrue(vibrateOnly.vibrate)
        assertFalse(vibrateOnly.torch)

        val torchOnly = PhoneFindRequest.fromMap(mapOf("torch" to true))
        assertNotNull(torchOnly)
        assertFalse(torchOnly!!.sound)
        assertFalse(torchOnly.vibrate)
        assertTrue(torchOnly.torch)
    }

    @Test
    fun testDurationClamping() {
        // Abaixo do mínimo (5s) -> 5
        val minReq = PhoneFindRequest.fromMap(mapOf("sound" to true, "duration_s" to 2))
        assertNotNull(minReq)
        assertEquals(5, minReq!!.durationS)

        // Acima do máximo (60s) -> 60
        val maxReq = PhoneFindRequest.fromMap(mapOf("sound" to true, "duration_s" to 120))
        assertNotNull(maxReq)
        assertEquals(60, maxReq!!.durationS)

        // Zero ou negativo -> 30 por omissão
        val zeroReq = PhoneFindRequest.fromMap(mapOf("sound" to true, "duration_s" to 0))
        assertNotNull(zeroReq)
        assertEquals(30, zeroReq!!.durationS)

        val negReq = PhoneFindRequest.fromMap(mapOf("sound" to true, "duration_s" to -10))
        assertNotNull(negReq)
        assertEquals(30, negReq!!.durationS)

        // Ausente -> 30 por omissão
        val omittedReq = PhoneFindRequest.fromMap(mapOf("sound" to true))
        assertNotNull(omittedReq)
        assertEquals(30, omittedReq!!.durationS)

        // Válido dentro da gama -> mantido
        val validReq = PhoneFindRequest.fromMap(mapOf("sound" to true, "duration_s" to 45))
        assertNotNull(validReq)
        assertEquals(45, validReq!!.durationS)
    }

    @Test
    fun testStateSerialization() {
        val activeState = PhoneFindState(active = true, untilMs = 1729000000000L)
        val activeMap = activeState.toMap()
        assertEquals(true, activeMap["active"])
        assertEquals(1729000000000L, activeMap["until_ms"])
        assertNull(activeMap["stopped_by"])
        assertNull(activeMap["error"])

        val userStoppedState = PhoneFindState(active = false, stoppedBy = PhoneFindStopReason.USER)
        val userMap = userStoppedState.toMap()
        assertEquals(false, userMap["active"])
        assertEquals("user", userMap["stopped_by"])
        assertNull(userMap["until_ms"])

        val timeoutState = PhoneFindState(active = false, stoppedBy = PhoneFindStopReason.TIMEOUT)
        assertEquals("timeout", timeoutState.toMap()["stopped_by"])

        val pcState = PhoneFindState(active = false, stoppedBy = PhoneFindStopReason.PC)
        assertEquals("pc", pcState.toMap()["stopped_by"])

        val errorState = PhoneFindState(
            active = false,
            stoppedBy = PhoneFindStopReason.ERROR,
            error = "não incomodar"
        )
        val errorMap = errorState.toMap()
        assertEquals(false, errorMap["active"])
        assertEquals("error", errorMap["stopped_by"])
        assertEquals("não incomodar", errorMap["error"])
    }

    @Test
    fun testVolumeRestoreSimulationAcrossAllExitPaths() {
        // Simulação da lógica de preservação e reposição de volume
        var originalVolume: Int? = 7
        var currentVolume = 15 // subiu ao máximo

        fun restoreVolume(): Int {
            val volToRestore = originalVolume ?: currentVolume
            currentVolume = volToRestore
            originalVolume = null
            return currentVolume
        }

        // Caminho 1: paragem normal pelo utilizador
        assertEquals(7, restoreVolume())
        assertNull(originalVolume)

        // Caminho 2: timeout
        originalVolume = 5
        currentVolume = 15
        assertEquals(5, restoreVolume())
        assertNull(originalVolume)

        // Caminho 3: paragem pelo PC
        originalVolume = 0 // modo silencioso no início
        currentVolume = 15
        assertEquals(0, restoreVolume())
        assertNull(originalVolume)

        // Caminho 4: erro / destruição de serviço
        originalVolume = 10
        currentVolume = 15
        assertEquals(10, restoreVolume())
        assertNull(originalVolume)
    }

    @Test
    fun testMapTorchErrorMapping() {
        // 1. CameraAccessException com CAMERA_IN_USE
        val inUseEx = android.hardware.camera2.CameraAccessException(
            android.hardware.camera2.CameraAccessException.CAMERA_IN_USE,
            "Torch for camera 0 is not available due to an existing camera user"
        )
        assertEquals(
            "torch unavailable: camera in use",
            com.example.find.PhoneFindManager.mapTorchError(
                inUseEx,
                android.hardware.camera2.CameraAccessException.CAMERA_IN_USE
            )
        )

        // 2. CameraAccessException com MAX_CAMERAS_IN_USE
        val maxEx = android.hardware.camera2.CameraAccessException(
            android.hardware.camera2.CameraAccessException.MAX_CAMERAS_IN_USE,
            "Max cameras in use"
        )
        assertEquals(
            "torch unavailable: max cameras in use",
            com.example.find.PhoneFindManager.mapTorchError(
                maxEx,
                android.hardware.camera2.CameraAccessException.MAX_CAMERAS_IN_USE
            )
        )

        // 3. CameraAccessException com CAMERA_DISABLED
        val disabledEx = android.hardware.camera2.CameraAccessException(
            android.hardware.camera2.CameraAccessException.CAMERA_DISABLED,
            "Camera disabled by device policy"
        )
        assertEquals(
            "torch unavailable: camera disabled",
            com.example.find.PhoneFindManager.mapTorchError(
                disabledEx,
                android.hardware.camera2.CameraAccessException.CAMERA_DISABLED
            )
        )

        // 4. CameraAccessException com CAMERA_DISCONNECTED
        val disconnectedEx = android.hardware.camera2.CameraAccessException(
            android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED,
            "Camera disconnected"
        )
        assertEquals(
            "torch unavailable: camera disconnected",
            com.example.find.PhoneFindManager.mapTorchError(
                disconnectedEx,
                android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED
            )
        )

        // 5. Exceção com texto contendo CAMERA_IN_USE
        val genericInUse = IllegalStateException("setTorchMode failed: CAMERA_IN_USE (4)")
        assertEquals(
            "torch unavailable: camera in use",
            com.example.find.PhoneFindManager.mapTorchError(genericInUse)
        )

        // 6. Exceção genérica com mensagem qualquer
        val otherEx = RuntimeException("hardware failure")
        assertEquals(
            "torch unavailable: hardware failure",
            com.example.find.PhoneFindManager.mapTorchError(otherEx)
        )
    }

    @Test
    fun testTorchWhenWebcamActive_backCamera_blinksViaWebcamCaptureRequest() {
        var cameraAccessed = false
        var webcamBlinkCalled = false
        val (ok, err) = com.example.find.PhoneFindManager.startTorchInternal(
            isWebcamStreaming = true,
            isFrontCamera = false,
            hasFlashUnit = true,
            cameraManager = null,
            onTorchInit = {
                cameraAccessed = true
                true to null
            },
            onWebcamBlink = {
                webcamBlinkCalled = true
                true
            }
        )

        assertTrue("Deve ter sucesso ao piscar lanterna via webcam traseira", ok)
        assertNull(err)
        assertTrue("onWebcamBlink deve ter sido chamado", webcamBlinkCalled)
        assertFalse("CameraManager não deve ser acedido diretamente com webcam ativa", cameraAccessed)
    }

    @Test
    fun testTorchWhenWebcamActive_frontCamera_reportsErrorAndActivatesScreenLight() {
        var cameraAccessed = false
        var screenLightCalled = false
        val (ok, err) = com.example.find.PhoneFindManager.startTorchInternal(
            isWebcamStreaming = true,
            isFrontCamera = true,
            hasFlashUnit = false,
            cameraManager = null,
            onTorchInit = {
                cameraAccessed = true
                true to null
            },
            onScreenLight = {
                screenLightCalled = true
            }
        )

        assertFalse("Não deve ter sucesso na lanterna física na câmara frontal", ok)
        assertEquals("torch unavailable: front camera", err)
        assertTrue("Luz do ecrã deve ser ativada como alternativa visual", screenLightCalled)
        assertFalse("CameraManager não deve ser acedido", cameraAccessed)
    }

    @Test
    fun testAlarmVolumeInterruptionRecovery_restoresVolumeAndClearsKey() {
        var restoredVol: Int? = null
        var keyRemoved = false
        var loggedMessage: String? = null

        com.example.find.PhoneFindManager.processAlarmVolumeInterruption(
            hasSavedKey = true,
            savedVol = 8,
            setVolumeAction = { vol -> restoredVol = vol },
            removeKeyAction = { keyRemoved = true },
            logAction = { msg -> loggedMessage = msg }
        )

        assertEquals(8, restoredVol)
        assertTrue("A chave de volume salvo deve ter sido removida", keyRemoved)
        assertEquals("[ENCONTRAR] volume do alarme reposto após interrupção", loggedMessage)
    }

    @Test
    fun testAlarmVolumeInterruptionRecovery_withoutKey_doesNothing() {
        var restoredVol: Int? = null
        var keyRemoved = false
        var loggedMessage: String? = null

        com.example.find.PhoneFindManager.processAlarmVolumeInterruption(
            hasSavedKey = false,
            savedVol = -1,
            setVolumeAction = { vol -> restoredVol = vol },
            removeKeyAction = { keyRemoved = true },
            logAction = { msg -> loggedMessage = msg }
        )

        assertNull("Nenhum volume deve ser restaurado", restoredVol)
        assertFalse("Nenhuma chave deve ser removida", keyRemoved)
        assertNull("Nenhuma mensagem deve ser registada", loggedMessage)
    }
}
