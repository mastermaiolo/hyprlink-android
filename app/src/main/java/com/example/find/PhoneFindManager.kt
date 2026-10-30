// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.find

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AppStrings
import com.example.HyprLinkConnectionService
import com.example.LanguageManager
import com.example.R
import com.example.WebcamStreamer
import com.example.getAppStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor central de «Onde está o telemóvel?» (phone.find).
 * Executa som (STREAM_ALARM), vibração, lanterna a piscar, notificação de alta prioridade
 * e ecrã de paragem em modo de ecrã bloqueado/segundo plano.
 */
object PhoneFindManager {

    const val CHANNEL_ID = "hyprlink_find_phone"
    const val NOTIFICATION_ID = 109

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isFindingFlow = MutableStateFlow(false)
    val isFindingFlow: StateFlow<Boolean> = _isFindingFlow.asStateFlow()

    @Volatile
    private var originalAlarmVolume: Int? = null

    @Volatile
    private var activeContext: Context? = null

    private var mediaPlayer: MediaPlayer? = null
    private var torchJob: Job? = null
    private var timeoutJob: Job? = null
    private var torchCameraId: String? = null
    private var torchUsingWebcam: Boolean = false
    private var findActivatedScreenLight: Boolean = false

    private var stateSender: (suspend (PhoneFindState) -> Unit)? = null
    private var logWriter: ((String) -> Unit)? = null

    /**
     * Inicia a procura do telemóvel conforme o pedido.
     * Se já estiver uma ativa, reinicia imediatamente com os novos modos e novo tempo (não empilha).
     */
    @Synchronized
    fun start(
        request: PhoneFindRequest,
        context: Context,
        sendState: (suspend (PhoneFindState) -> Unit)? = null,
        logger: ((String) -> Unit)? = null
    ) {
        if (!request.isValid) return

        this.activeContext = context.applicationContext
        if (sendState != null) this.stateSender = sendState
        if (logger != null) this.logWriter = logger

        // Se já estiver a correr, cancela os recursos em curso sem enviar stopped_by ao PC (reinicia limpo)
        if (_isFindingFlow.value) {
            cancelActiveResources(context.applicationContext, restoreVol = false)
        }

        _isFindingFlow.value = true

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        val untilMs = System.currentTimeMillis() + (request.durationS * 1000L)

        var dndError: String? = null
        var torchError: String? = null

        // 1. Som (USAGE_ALARM)
        var shouldPlaySound = request.sound
        if (shouldPlaySound) {
            if (isAlarmBlockedByDnd(context)) {
                dndError = "não incomodar"
                shouldPlaySound = false
            }
        }

        // Se o único modo pedido era som e o DnD bloqueia alarmes, termina logo com erro
        if (request.sound && !shouldPlaySound && !request.vibrate && !request.torch) {
            _isFindingFlow.value = false
            logJournal(strings, started = true, request = request)
            logJournal(strings, started = false, stoppedBy = PhoneFindStopReason.ERROR)
            notifyState(PhoneFindState(active = false, stoppedBy = PhoneFindStopReason.ERROR, error = dndError))
            return
        }

        if (shouldPlaySound) {
            startSound(context)
        }

        // 2. Vibração
        if (request.vibrate) {
            startVibration(context)
        }

        // 3. Lanterna a piscar
        if (request.torch) {
            val (ok, err) = startTorch(context, request, untilMs)
            if (!ok) {
                torchError = err
            }
        }

        // 4. Notificação de alta prioridade com botão ENCONTREI e Full Screen Intent
        postNotification(context, strings)

        // 5. Diário de início
        logJournal(strings, started = true, request = request)

        // 6. Enviar estado de início ao PC
        val initialError = listOfNotNull(dndError, torchError).joinToString("; ").ifEmpty { null }
        notifyState(PhoneFindState(active = true, untilMs = untilMs, error = initialError))

        // 7. Agendar timeout
        timeoutJob = managerScope.launch {
            delay(request.durationS * 1000L)
            stop(PhoneFindStopReason.TIMEOUT, error = null, context = context)
        }
    }

    /**
     * Pára a procura do telemóvel e repõe todos os estados (volume, lanterna, vibração, notificação).
     */
    @Synchronized
    fun stop(
        stoppedBy: String,
        error: String? = null,
        context: Context? = null
    ) {
        if (!_isFindingFlow.value) {
            // Garante mesmo assim reposição de volume se por alguma razão estava pendente
            val ctx = context?.applicationContext ?: activeContext
            if (ctx != null) restoreAlarmVolume(ctx)
            return
        }

        _isFindingFlow.value = false
        val ctx = context?.applicationContext ?: activeContext

        timeoutJob?.cancel()
        timeoutJob = null

        if (ctx != null) {
            cancelActiveResources(ctx, restoreVol = true)
        }

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        logJournal(strings, started = false, stoppedBy = stoppedBy)

        notifyState(PhoneFindState(active = false, stoppedBy = stoppedBy, error = error))
    }

    /**
     * Limpeza absoluta na destruição do serviço.
     */
    @Synchronized
    fun release(context: Context) {
        if (_isFindingFlow.value) {
            stop(PhoneFindStopReason.ERROR, error = "serviço destruído", context = context)
        } else {
            restoreAlarmVolume(context)
        }
        activeContext = null
    }

    private fun cancelActiveResources(context: Context, restoreVol: Boolean) {
        // Pára lanterna de webcam se ativa
        if (torchUsingWebcam) {
            WebcamStreamer.stopBlinkingTorch()
            torchUsingWebcam = false
        }
        if (findActivatedScreenLight) {
            WebcamStreamer.toggleScreenLight(false)
            findActivatedScreenLight = false
        }

        // Pára lanterna CameraManager
        torchJob?.cancel()
        torchJob = null
        torchCameraId?.let { camId ->
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                cm?.setTorchMode(camId, false)
            } catch (e: Exception) {
                // ignora
            }
        }
        torchCameraId = null

        // Pára som
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignora
        }
        mediaPlayer = null

        // Repõe volume do alarme se pedido
        if (restoreVol) {
            restoreAlarmVolume(context)
        }

        // Pára vibração
        try {
            val vibrator = getVibrator(context)
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignora
        }

        // Cancela notificação
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // ignora
        }
    }

    private fun startSound(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (originalAlarmVolume == null) {
                    val vol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    originalAlarmVolume = vol
                    val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("find_alarm_volume_saved", vol).apply()
                }
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }

            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alertUri)
                isLooping = true
                prepare()
                start()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            logWriter?.invoke("[ENCONTRAR] Erro ao iniciar som de alarme: ${e.message}")
        }
    }

    private fun startVibration(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            val timings = longArrayOf(0, 500, 300)
            val amplitudes = intArrayOf(0, 255, 0)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, 1)
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
        } catch (e: Exception) {
            logWriter?.invoke("[ENCONTRAR] Erro ao iniciar vibração: ${e.message}")
        }
    }

    fun mapTorchError(e: Throwable, explicitReason: Int? = null): String {
        val msg = e.message ?: ""
        val reason = explicitReason ?: try {
            if (e is CameraAccessException) e.reason else null
        } catch (_: Throwable) {
            null
        }

        return if (reason == CameraAccessException.CAMERA_IN_USE) {
            "torch unavailable: camera in use"
        } else if (msg.contains("CAMERA_IN_USE", ignoreCase = true) || msg.contains("camera in use", ignoreCase = true)) {
            "torch unavailable: camera in use"
        } else if (reason == CameraAccessException.MAX_CAMERAS_IN_USE || msg.contains("MAX_CAMERAS_IN_USE", ignoreCase = true) || msg.contains("max cameras in use", ignoreCase = true)) {
            "torch unavailable: max cameras in use"
        } else if (reason == CameraAccessException.CAMERA_DISABLED || msg.contains("CAMERA_DISABLED", ignoreCase = true) || msg.contains("camera disabled", ignoreCase = true)) {
            "torch unavailable: camera disabled"
        } else if (reason == CameraAccessException.CAMERA_DISCONNECTED || msg.contains("CAMERA_DISCONNECTED", ignoreCase = true) || msg.contains("camera disconnected", ignoreCase = true)) {
            "torch unavailable: camera disconnected"
        } else {
            "torch unavailable: ${msg.ifBlank { e.javaClass.simpleName }}"
        }
    }

    internal fun startTorchInternal(
        isWebcamStreaming: Boolean,
        isFrontCamera: Boolean = false,
        hasFlashUnit: Boolean = true,
        cameraManager: CameraManager?,
        onTorchInit: ((CameraManager) -> Pair<Boolean, String?>)? = null,
        onWebcamBlink: (() -> Boolean)? = null,
        onScreenLight: (() -> Unit)? = null
    ): Pair<Boolean, String?> {
        val decision = com.example.WebcamLogic.resolveFindPhoneTorch(
            isWebcamStreaming = isWebcamStreaming,
            isFrontCamera = isFrontCamera,
            hasFlashUnit = hasFlashUnit
        )
        return when (decision.action) {
            com.example.WebcamLogic.FindPhoneTorchAction.WEBCAM_CAPTURE_REQUEST -> {
                logWriter?.invoke("[ENCONTRAR] Webcam ativa: a piscar lanterna por pedidos de captura")
                val ok = onWebcamBlink?.invoke() ?: false
                if (ok) true to null else false to "erro ao piscar lanterna"
            }
            com.example.WebcamLogic.FindPhoneTorchAction.FRONT_CAMERA_SCREEN_LIGHT -> {
                logWriter?.invoke("[ENCONTRAR] Webcam com câmara frontal ativa: lanterna física indisponível, a ativar luz de ecrã")
                onScreenLight?.invoke()
                false to decision.error
            }
            com.example.WebcamLogic.FindPhoneTorchAction.CAMERA_MANAGER_TORCH -> {
                val cm = cameraManager ?: return false to "camera_manager_indisponível"
                onTorchInit?.invoke(cm) ?: (false to "sem lanterna")
            }
            com.example.WebcamLogic.FindPhoneTorchAction.UNAVAILABLE -> {
                false to (decision.error ?: "sem lanterna")
            }
        }
    }

    private fun startTorch(
        context: Context,
        request: PhoneFindRequest,
        untilMs: Long
    ): Pair<Boolean, String?> {
        return startTorchInternal(
            isWebcamStreaming = WebcamStreamer.isStreaming.value,
            isFrontCamera = WebcamStreamer.isFrontCamera(),
            hasFlashUnit = WebcamStreamer.hasFlashUnit(),
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager,
            onWebcamBlink = {
                val ok = WebcamStreamer.startBlinkingTorch(managerScope)
                if (ok) {
                    torchUsingWebcam = true
                }
                ok
            },
            onScreenLight = {
                WebcamStreamer.toggleScreenLight(true)
                findActivatedScreenLight = true
            },
            onTorchInit = { cm ->
                try {
                    val id = cm.cameraIdList.firstOrNull { camId ->
                        val chars = cm.getCameraCharacteristics(camId)
                        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        val facing = chars.get(CameraCharacteristics.LENS_FACING)
                        hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
                    } ?: cm.cameraIdList.firstOrNull { camId ->
                        val chars = cm.getCameraCharacteristics(camId)
                        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }

                    if (id == null) {
                        return@startTorchInternal false to "sem lanterna"
                    }

                    torchCameraId = id

                    // Testa a primeira chamada setTorchMode antes de reportar o estado inicial ao PC
                    var firstError: String? = null
                    var isCurrentlyOn = false
                    try {
                        cm.setTorchMode(id, true)
                        isCurrentlyOn = true
                    } catch (e: Exception) {
                        firstError = mapTorchError(e)
                        logWriter?.invoke("[ENCONTRAR] Primeira tentativa da lanterna falhou: $firstError")
                    }

                    torchJob = managerScope.launch(Dispatchers.Default) {
                        var on = isCurrentlyOn
                        var failures = if (firstError != null) 1 else 0
                        var lastError: String? = firstError
                        while (isActive) {
                            delay(500L)
                            if (!isActive) break
                            try {
                                on = !on
                                cm.setTorchMode(id, on)
                                failures = 0
                                lastError = null
                            } catch (e: Exception) {
                                failures++
                                lastError = mapTorchError(e)
                                if (failures == 1) {
                                    logWriter?.invoke("[ENCONTRAR] Lanterna temporariamente ocupada ($lastError)")
                                }
                                if (failures >= 20) {
                                    logWriter?.invoke("[ENCONTRAR] Lanterna desativada após múltiplas tentativas falhadas: $lastError")
                                    if (_isFindingFlow.value) {
                                        notifyState(PhoneFindState(active = true, untilMs = untilMs, error = lastError))
                                    }
                                    if (!request.sound && !request.vibrate && _isFindingFlow.value) {
                                        stop(PhoneFindStopReason.ERROR, error = lastError, context = context)
                                    }
                                    break
                                }
                            }
                        }
                        // Desliga no fim
                        try {
                            cm.setTorchMode(id, false)
                        } catch (_: Exception) {}
                    }

                    if (firstError != null) {
                        false to firstError
                    } else {
                        true to null
                    }
                } catch (e: Exception) {
                    false to mapTorchError(e)
                }
            }
        )
    }

    fun restoreAlarmVolume(context: Context) {
        val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val savedFromPrefs = if (prefs.contains("find_alarm_volume_saved")) prefs.getInt("find_alarm_volume_saved", -1) else null
        val volToRestore = originalAlarmVolume ?: savedFromPrefs
        if (volToRestore != null && volToRestore >= 0) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volToRestore, 0)
            } catch (e: Exception) {
                // ignora
            }
        }
        originalAlarmVolume = null
        if (prefs.contains("find_alarm_volume_saved")) {
            prefs.edit().remove("find_alarm_volume_saved").apply()
        }
    }

    internal fun processAlarmVolumeInterruption(
        hasSavedKey: Boolean,
        savedVol: Int,
        setVolumeAction: (Int) -> Unit,
        removeKeyAction: () -> Unit,
        logAction: (String) -> Unit
    ) {
        if (hasSavedKey && savedVol >= 0) {
            setVolumeAction(savedVol)
            removeKeyAction()
            logAction("[ENCONTRAR] volume do alarme reposto após interrupção")
        }
    }

    /**
     * Verifica no arranque se ficou volume de alarme pendente de reposição após
     * o processo ter sido interrompido/morto durante o toque.
     */
    fun checkAndRestoreInterruptedAlarmVolume(context: Context) {
        if (_isFindingFlow.value) return
        val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        processAlarmVolumeInterruption(
            hasSavedKey = prefs.contains("find_alarm_volume_saved"),
            savedVol = prefs.getInt("find_alarm_volume_saved", -1),
            setVolumeAction = { vol ->
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
                } catch (_: Exception) {}
            },
            removeKeyAction = {
                prefs.edit().remove("find_alarm_volume_saved").apply()
                originalAlarmVolume = null
            },
            logAction = { msg ->
                logWriter?.invoke(msg)
            }
        )
    }

    private fun getVibrator(context: Context): Vibrator? {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        return vm?.defaultVibrator
    }

    private fun isAlarmBlockedByDnd(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        val filter = nm.currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            return true
        }
        if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            val policy = nm.notificationPolicy
            if (policy != null && (policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) == 0) {
                return true
            }
        }
        return false
    }

    private fun postNotification(context: Context, strings: AppStrings) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.findPhoneChannelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = strings.findPhoneNotificationDesc
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)

        // Ação ENCONTREI (PendingIntent para o serviço)
        val stopIntent = Intent(context, HyprLinkConnectionService::class.java).apply {
            action = HyprLinkConnectionService.ACTION_FIND_PHONE_STOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPendingIntent = PendingIntent.getService(context, 1091, stopIntent, flags)

        // Full Screen Intent para FindPhoneActivity
        val fullScreenIntent = Intent(context, FindPhoneActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(context, 1092, fullScreenIntent, flags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(strings.findPhoneNotificationTitle)
            .setContentText(strings.findPhoneNotificationDesc)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_stat_hyprlink, strings.btnFound, stopPendingIntent)

        val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else {
            true
        }

        if (canFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun notifyState(state: PhoneFindState) {
        managerScope.launch(Dispatchers.IO) {
            try {
                stateSender?.invoke(state)
            } catch (e: Exception) {
                logWriter?.invoke("[WARN] Erro ao enviar phone.find.state: ${e.message}")
            }
        }
    }

    private fun logJournal(
        strings: AppStrings,
        started: Boolean,
        request: PhoneFindRequest? = null,
        stoppedBy: String? = null
    ) {
        val line = if (started && request != null) {
            val modes = mutableListOf<String>()
            if (request.sound) modes.add(strings.findPhoneModeSound)
            if (request.vibrate) modes.add(strings.findPhoneModeVibrate)
            if (request.torch) modes.add(strings.findPhoneModeTorch)
            String.format(strings.findPhoneStartedJournal, modes.joinToString(", "))
        } else {
            val who = when (stoppedBy) {
                PhoneFindStopReason.USER -> strings.findPhoneStoppedByUser
                PhoneFindStopReason.TIMEOUT -> strings.findPhoneStoppedByTimeout
                PhoneFindStopReason.PC -> strings.findPhoneStoppedByPc
                else -> strings.findPhoneStoppedByError
            }
            String.format(strings.findPhoneStoppedJournal, who)
        }
        logWriter?.invoke("[ENCONTRAR] $line")
    }
}
