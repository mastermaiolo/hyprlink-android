// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import tech.kwik.core.QuicClientConnection

class HyprLinkConnectionService : Service() {

    companion object {
        @Volatile
        var instance: HyprLinkConnectionService? = null
            private set

        const val ACTION_RECONNECT = "ACTION_RECONNECT"
        const val ACTION_MEDIA_PLAY_PAUSE = "com.example.action.MEDIA_PLAY_PAUSE"
        const val ACTION_MEDIA_PREV = "com.example.action.MEDIA_PREV"
        const val ACTION_MEDIA_NEXT = "com.example.action.MEDIA_NEXT"
        const val ACTION_FIND_PHONE_STOP = "com.example.action.FIND_PHONE_STOP"
    }

    private var pcPlayerManager: PcPlayerNotificationManager? = null

    private val CHANNEL_ID = "hyprlink_service_channel"
    private val NOTIFICATION_ID = 101
    private val NOTIFICATION_FGS_RESTART_ID = 108

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loopCoordinator by lazy { ConnectionLoopCoordinator(serviceScope) }
    private var activeQuicConn: QuicClientConnection? = null
    private val connectionGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var batteryReceiver: android.content.BroadcastReceiver? = null
    private var lastSentPct: Int = -1
    private var lastSentCharging: Boolean = false

    private var wifiLock: WifiManager.WifiLock? = null

    private var screenshotObserver: ContentObserver? = null
    private var screenshotDebounceJob: Job? = null
    private var lastSeenScreenshotId: Long = -1L
    private val sentScreenshotKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var phoneStatusDebounceJob: Job? = null
    private var lastBatteryIntent: Intent? = null

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val activeMediaControllers = mutableListOf<MediaController>()
    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            triggerPhoneStatusDebounce(500L, isRelevantChange = true)
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            triggerPhoneStatusDebounce(500L, isRelevantChange = true)
        }
    }

    fun triggerPhoneStatusDebounce(
        debounceMs: Long = TelemetryDispatchLogic.DEFAULT_DEBOUNCE_MS,
        isRelevantChange: Boolean = false
    ) {
        phoneStatusDebounceJob?.cancel()
        if (activeQuicConn?.isConnected != true || ConnectionRepository.activeConnection == null) {
            return
        }
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenOn = pm?.isInteractive ?: true
        val effectiveDebounce = TelemetryDispatchLogic.calculateDebounceDelay(
            isScreenOn = isScreenOn,
            isRelevantChange = isRelevantChange,
            requestedDebounceMs = debounceMs
        )

        phoneStatusDebounceJob = serviceScope.launch {
            delay(effectiveDebounce)
            if (activeQuicConn?.isConnected == true && ConnectionRepository.activeConnection != null) {
                try {
                    val status = PhoneStatusCollector.collect(applicationContext, lastBatteryIntent, lightMode = !isScreenOn)
                    ConnectionRepository.sendPhoneStatus(status)
                } catch (e: Exception) {
                    ConnectionRepository.appendLog("[WARN] phone.status debounce send failed: ${e.message}")
                }
            }
        }
    }

    private fun acquireWifiLockIfNeeded() {
        if (wifiLock?.isHeld == true) return
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            wifiLock = wifiManager?.createWifiLock(
                mode,
                "HyprLink:AudioStreamingLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
            ConnectionRepository.appendLog("[STREAM] WifiLock (HIGH_PERF) adquirido com sucesso")
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WARN] Falha ao adquirir WifiLock: ${e.message}")
        }
    }

    private fun releaseWifiLockIfHeld() {
        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    ConnectionRepository.appendLog("[STREAM] WifiLock liberado com sucesso")
                }
            }
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WARN] Falha ao liberar WifiLock: ${e.message}")
        }
        wifiLock = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        registerNetworkCallback()
        registerBatteryReceiver()
        setupScreenshotObserver()
        setupMediaSessionObserver()
        pcPlayerManager = PcPlayerNotificationManager(this, serviceScope)
        com.example.find.PhoneFindManager.checkAndRestoreInterruptedAlarmVolume(applicationContext)
        UsbTetherHelper.startMonitoring(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MEDIA_PLAY_PAUSE -> {
                pcPlayerManager?.onPlayPauseClicked()
                return START_STICKY
            }
            ACTION_MEDIA_PREV -> {
                pcPlayerManager?.onPreviousClicked()
                return START_STICKY
            }
            ACTION_MEDIA_NEXT -> {
                pcPlayerManager?.onNextClicked()
                return START_STICKY
            }
            ACTION_FIND_PHONE_STOP -> {
                com.example.find.PhoneFindManager.stop(com.example.find.PhoneFindStopReason.USER, context = this)
                return START_STICKY
            }
        }

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        val notification = createNotification(strings.negotiatingTls)
        ConnectionRepository.appendLog("[SERVICE] startForeground type=connectedDevice")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[SERVICE] Falha ao iniciar FGS: ${e.javaClass.simpleName} - ${e.message}")
            postServiceRestartPromptNotification(this)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == "ACTION_RECONNECT") {
            loopCoordinator.lastStationId = null
        }
        startConnectionLoop()
        return START_STICKY
    }

    private fun postServiceRestartPromptNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = CHANNEL_ID
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_FGS_RESTART_ID, intent, flags)

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(strings.fgsRestartPromptTitle)
            .setContentText(strings.fgsRestartPromptDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_FGS_RESTART_ID, notif)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        ConnectionRepository.appendLog("[SERVICE] FGS timeout type=$fgsType")
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.find.PhoneFindManager.release(this)
        pcPlayerManager?.destroy()
        pcPlayerManager = null
        if (instance == this) {
            instance = null
        }
        phoneStatusDebounceJob?.cancel()
        serviceScope.cancel()
        unregisterNetworkCallback()
        unregisterBatteryReceiver()
        unregisterScreenshotObserver()
        teardownMediaSessionObserver()
        prefListener?.let {
            val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        releaseWifiLockIfHeld()
        stopCameraLifecycle()
        UsbTetherHelper.stopMonitoring(applicationContext)
        closeConnection()
    }

    class CameraServiceLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                registry.currentState = Lifecycle.State.RESUMED
            } else {
                Handler(Looper.getMainLooper()).post {
                    registry.currentState = Lifecycle.State.RESUMED
                }
            }
        }

        fun stop() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                registry.currentState = Lifecycle.State.DESTROYED
            } else {
                Handler(Looper.getMainLooper()).post {
                    registry.currentState = Lifecycle.State.DESTROYED
                }
            }
        }
    }

    private var cameraLifecycleOwner: CameraServiceLifecycleOwner? = null

    fun getOrCreateCameraLifecycleOwner(): LifecycleOwner {
        val existing = cameraLifecycleOwner
        if (existing != null && existing.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            existing.start()
            return existing
        }
        val newOwner = CameraServiceLifecycleOwner()
        newOwner.start()
        cameraLifecycleOwner = newOwner
        return newOwner
    }

    fun stopCameraLifecycle() {
        cameraLifecycleOwner?.stop()
        cameraLifecycleOwner = null
    }

    @Volatile private var isCameraActive: Boolean = false
    @Volatile private var isMicActive: Boolean = false

    @Synchronized
    fun updateForegroundServiceTypes(camera: Boolean? = null, mic: Boolean? = null): Boolean {
        val prevCamera = isCameraActive
        val prevMic = isMicActive

        if (camera != null) isCameraActive = camera
        if (mic != null) isMicActive = mic

        return try {
            var fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (isCameraActive) {
                    fgsType = fgsType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                if (isMicActive) {
                    fgsType = fgsType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            }

            val strings = getAppStrings(LanguageManager.currentLanguage.value)
            val notifText = when {
                isCameraActive && isMicActive -> "${strings.serviceWebcam} + Mic (Streaming)"
                isCameraActive -> "${strings.serviceWebcam} (Streaming)"
                isMicActive -> "Microfone (Streaming)"
                activeQuicConn?.isConnected == true -> "Ligado ao PC"
                else -> strings.negotiatingTls
            }
            val notification = createNotification(notifText)

            ConnectionRepository.appendLog("[SERVICE] updateForegroundServiceTypes: camera=$isCameraActive, mic=$isMicActive, fgsType=$fgsType")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[SERVICE] Falha ao atualizar FGS types: ${e.message}")
            isCameraActive = prevCamera
            isMicActive = prevMic
            false
        }
    }

    fun promoteToCameraForeground(): Boolean {
        return updateForegroundServiceTypes(camera = true)
    }

    fun demoteFromCameraForeground(): Boolean {
        return updateForegroundServiceTypes(camera = false)
    }

    fun promoteToMicrophoneForeground(): Boolean {
        return updateForegroundServiceTypes(mic = true)
    }

    fun demoteFromMicrophoneForeground(): Boolean {
        return updateForegroundServiceTypes(mic = false)
    }

    private fun setupScreenshotObserver() {
        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == PcPlayerNotificationManager.PREF_SHOW_PC_PLAYER) {
                val enabled = sp.getBoolean(PcPlayerNotificationManager.PREF_SHOW_PC_PLAYER, true)
                pcPlayerManager?.onSettingChanged(enabled)
            } else if (key == "auto_sync_screenshots") {
                val enabled = sp.getBoolean("auto_sync_screenshots", true)
                if (enabled) {
                    registerScreenshotObserver()
                } else {
                    unregisterScreenshotObserver()
                }
            } else if (key == "mirror_notifications") {
                val enabled = sp.getBoolean("mirror_notifications", true)
                if (enabled) {
                    serviceScope.launch {
                        HyprNotificationListenerService.replayActiveNotifications(applicationContext)
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        val enabled = prefs.getBoolean("auto_sync_screenshots", true)
        if (enabled) {
            registerScreenshotObserver()
        }
    }

    private fun hasReadImagesPermission(): Boolean {
        return PhotoAccessLogic.canAutoSyncScreenshots(this)
    }

    private fun disableAutoSyncDueToPermission() {
        val status = PhotoAccessLogic.checkPhotoAccess(this)
        if (status == PhotoAccessStatus.PARTIAL) {
            ConnectionRepository.appendLog("[SYNC] Acesso parcial a fotos detetado (READ_MEDIA_VISUAL_USER_SELECTED). A sincronização automática de capturas requer acesso a todas as fotos.")
        } else {
            ConnectionRepository.appendLog("[SYNC] Permissão de imagens ausente/revogada. Auto-sync desativado.")
        }
        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_sync_screenshots", false).apply()
        unregisterScreenshotObserver()
    }

    private fun initLastSeenScreenshotId() {
        if (lastSeenScreenshotId >= 0L) return
        if (!hasReadImagesPermission()) return

        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Pictures/Screenshots%", "DCIM/Screenshots%")
            val sortOrder = "${MediaStore.Images.Media._ID} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    lastSeenScreenshotId = cursor.getLong(idCol)
                    ConnectionRepository.appendLog("[SYNC] Screenshot sync inicializado. Último ID: $lastSeenScreenshotId")
                } else {
                    lastSeenScreenshotId = 0L
                    ConnectionRepository.appendLog("[SYNC] Screenshot sync inicializado. Nenhum screenshot prévio.")
                }
            }
        } catch (e: SecurityException) {
            ConnectionRepository.appendLog("[WARN] Sem permissão ao inicializar screenshots: ${e.message}")
            disableAutoSyncDueToPermission()
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WARN] Erro ao inicializar MAX(_id) screenshots: ${e.message}")
            if (lastSeenScreenshotId < 0L) lastSeenScreenshotId = 0L
        }
    }

    private fun registerScreenshotObserver() {
        if (screenshotObserver != null) return
        if (!hasReadImagesPermission()) {
            ConnectionRepository.appendLog("[SYNC] Permissão de imagens ausente. Auto-sync inativo.")
            return
        }

        initLastSeenScreenshotId()

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                onScreenshotContentChanged()
            }

            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                super.onChange(selfChange, uri)
                onScreenshotContentChanged()
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            screenshotObserver = observer
            ConnectionRepository.appendLog("[SYNC] Observador de screenshots registrado com sucesso.")
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WARN] Falha ao registrar observador de screenshots: ${e.message}")
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotDebounceJob?.cancel()
        screenshotDebounceJob = null
        screenshotObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
                ConnectionRepository.appendLog("[SYNC] Observador de screenshots desregistrado.")
            } catch (e: Exception) {}
        }
        screenshotObserver = null
    }

    private fun onScreenshotContentChanged() {
        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_sync_screenshots", true)) return

        if (!hasReadImagesPermission()) {
            disableAutoSyncDueToPermission()
            return
        }

        // Debounce de 3s antes de processar: editores de screenshot (corte, marcação) atualizam a MESMA linha
        screenshotDebounceJob?.cancel()
        screenshotDebounceJob = serviceScope.launch {
            delay(3000)
            processNewScreenshots()
        }
    }

    private suspend fun processNewScreenshots() = withContext(Dispatchers.IO) {
        if (!hasReadImagesPermission()) {
            disableAutoSyncDueToPermission()
            return@withContext
        }

        if (lastSeenScreenshotId < 0L) {
            initLastSeenScreenshotId()
            return@withContext
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )
        val selection = "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?) AND ${MediaStore.Images.Media._ID} > ?"
        val selectionArgs = arrayOf("Pictures/Screenshots%", "DCIM/Screenshots%", lastSeenScreenshotId.toString())
        val sortOrder = "${MediaStore.Images.Media._ID} ASC"

        data class ScreenshotItem(val id: Long, val name: String, val size: Long, val uri: android.net.Uri)
        val itemsToSend = mutableListOf<ScreenshotItem>()

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawName = cursor.getString(nameCol) ?: "screenshot_${id}.png"
                    var size = cursor.getLong(sizeCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    if (size <= 0L) {
                        try {
                            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                size = afd.length
                            }
                        } catch (e: Exception) {}
                    }

                    // Sanitizar DISPLAY_NAME (rejeitar nomes com /)
                    val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
                    val sanitizedName = if (cleanName.isEmpty() || cleanName.contains('/') || cleanName.contains('\\')) {
                        "screenshot_${id}.png"
                    } else {
                        cleanName
                    }

                    itemsToSend.add(ScreenshotItem(id, sanitizedName, size, uri))
                }
            }
        } catch (e: SecurityException) {
            ConnectionRepository.appendLog("[WARN] Permissão revogada ao consultar novos screenshots: ${e.message}")
            disableAutoSyncDueToPermission()
            return@withContext
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WARN] Erro ao consultar novos screenshots: ${e.message}")
            return@withContext
        }

        for (item in itemsToSend) {
            if (item.id <= lastSeenScreenshotId) continue

            val itemKey = "${item.name}_${item.size}"
            if (sentScreenshotKeys.contains(itemKey)) {
                lastSeenScreenshotId = maxOf(lastSeenScreenshotId, item.id)
                continue
            }

            // Só quando a conexão estiver ativa (sem fila offline nesta fase)
            if (ConnectionRepository.connectionStatus.value != ConnectionStatus.CONNECTED) {
                ConnectionRepository.appendLog("[SYNC] screenshot ignorado, sem conexão")
                lastSeenScreenshotId = maxOf(lastSeenScreenshotId, item.id)
                continue
            }

            try {
                ConnectionRepository.appendLog("[SYNC] A enviar screenshot: ${item.name} (${item.size / 1024} KB)...")
                ConnectionRepository.sendFile(applicationContext, item.uri, item.name, item.size, kind = "screenshot")
                lastSeenScreenshotId = maxOf(lastSeenScreenshotId, item.id)
                sentScreenshotKeys.add(itemKey)
                ConnectionRepository.appendLog("[SYNC] screenshot enviado: ${item.name} (${item.size / 1024} KB)")
                ConnectionRepository.appendLog("Captura enviada")
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Falha ao enviar screenshot ${item.name}: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HyprLink Connection Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for active HyprLink connection in background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HyprLink Daemon Connection")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun startConnectionLoop() {
        val sharedPrefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val activeId = sharedPrefs.getString("active_workstation_id", "") ?: ""

        serviceScope.launch {
            loopCoordinator.requestLoop(
                stationId = activeId,
                isConnected = activeQuicConn?.isConnected == true,
                onClosePreviousConnection = {
                    try {
                        activeQuicConn?.close()
                    } catch (_: Exception) {}
                    activeQuicConn = null
                },
                loopRunner = { cycleId ->
                    runConnectionCycle(cycleId, sharedPrefs)
                }
            )
        }
    }

    private suspend fun runConnectionCycle(cycleId: Int, sharedPrefs: SharedPreferences) {
        var backoffMs = 1000L
        val maxBackoffMs = 30000L

        while (currentCoroutineContext().isActive) {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.workstationDao()

            val currentActiveId = sharedPrefs.getString("active_workstation_id", "") ?: ""
            val allStations = try {
                dao.getAll().first()
            } catch (e: Exception) {
                emptyList()
            }
            val resolution = WorkstationSelectionLogic.resolveActiveWorkstation(allStations, currentActiveId)
            val ws = resolution.activeWorkstation
            if (ws != null && currentActiveId != resolution.resolvedId) {
                sharedPrefs.edit().putString("active_workstation_id", resolution.resolvedId).apply()
            }

            if (ws == null) {
                val strings = getAppStrings(LanguageManager.currentLanguage.value)
                updateNotification(strings.noWorkstationPairPrompt)
                delay(5000)
                continue
            }

            ConnectionRepository.appendLog("[SERVICE] Connection loop started #$cycleId (station=${ws.deviceName})")
            val strings = getAppStrings(LanguageManager.currentLanguage.value)
            updateNotification(strings.negotiatingTls)
            ConnectionRepository.setStatus(ConnectionStatus.CONNECTING)

            loopCoordinator.isAttemptInProgress = true
            loopCoordinator.immediateRetryRequested.set(false)

            val success = try {
                connectAndKeepAlive(ws)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                loopCoordinator.isAttemptInProgress = false
            }

            if (success) {
                backoffMs = 1000L
            } else {
                if (loopCoordinator.immediateRetryRequested.compareAndSet(true, false)) {
                    ConnectionRepository.appendLog("[CONN] Immediate retry requested (skipping backoff)")
                    backoffMs = 1000L
                } else {
                    val retrySec = backoffMs / 1000
                    ConnectionRepository.appendLog("[CONN] Retry in ${retrySec}s (reason: connection failed or closed)")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                }
            }
        }
    }

    private suspend fun connectAndKeepAlive(ws: PairedWorkstation): Boolean = withContext(Dispatchers.IO) {
        ConnectionRepository.currentStationId = ws.id
        val knownAddrs = ConnectionRepository.getStationPcAddrs(ws.id)
        val isUsbActive = UsbTetherHelper.isUsbNetworkActive.value
        val routeDecision = UsbConnectionLogic.selectConnectionRoute(
            availablePcAddrs = knownAddrs,
            isUsbTetherActive = isUsbActive,
            fallbackIp = ws.host
        )

        val portInt = ws.port.toIntOrNull() ?: ConnectionUtils.HYPRLINK_SERVICE_PORT
        val identity = DeviceIdentity.getInstance(applicationContext)
        if (identity.isTemporary) {
            ConnectionRepository.appendLog("[SECURITY] Identidade segura indisponível (Keystore em espera). Ligação cancelada até o armazenamento estar pronto.")
            return@withContext false
        }

        val myGeneration = connectionGeneration.incrementAndGet()
        var quicConn: QuicClientConnection? = null
        var effectivePath = LinkPathType.WIFI
        var connectedHost = ws.host

        try {
            if (routeDecision.isUsbPreferred) {
            ConnectionRepository.appendLog("[CONN] Tentativa prioritária USB → ${routeDecision.targetIp}:$portInt (${routeDecision.reason})")
            try {
                withTimeoutOrNull(3000L) {
                    quicConn = ConnectionUtils.connectPinned(
                        host = routeDecision.targetIp,
                        port = portInt,
                        applicationProtocol = "hyprlink/1",
                        expectedFingerprint = ws.fingerprint,
                        identity = identity
                    )
                }
                if (quicConn?.isConnected == true) {
                    effectivePath = LinkPathType.USB
                    connectedHost = routeDecision.targetIp
                    ConnectionRepository.appendLog("[USB] Ligação estabelecida via cabo USB (${routeDecision.targetIp})")
                } else {
                    quicConn?.close()
                    quicConn = null
                }
            } catch (e: Exception) {
                quicConn?.close()
                quicConn = null
                ConnectionRepository.appendLog("[USB] Rota USB falhou: ${e.message}; a tentar rota padrão...")
            }
        }

        if (quicConn == null || !quicConn!!.isConnected) {
            ConnectionRepository.appendLog("[CONN] Attempt → ${ws.host}:$portInt")
            quicConn = ConnectionUtils.connectPinned(
                host = ws.host,
                port = portInt,
                applicationProtocol = "hyprlink/1",
                expectedFingerprint = ws.fingerprint,
                identity = identity
            )
            effectivePath = LinkPathType.WIFI
            connectedHost = ws.host
        }

        if (myGeneration != connectionGeneration.get()) {
            quicConn?.close()
            return@withContext false // fui superado, não assumo o estado partilhado
        }

        val conn = quicConn ?: return@withContext false
        if (!conn.isConnected) {
            return@withContext false
        }

        val activeConn: QuicClientConnection = conn
        activeQuicConn = activeConn
        updateNotification("Ligado a ${ws.deviceName}")

            // Open stream and send hello
            val stream = conn.createStream(true)
            val localDeviceName = DeviceIdentity.getDeviceName(applicationContext)

            // Send core.hello with pairingToken = null since we are already paired
            val cborHello = buildHelloCbor(localDeviceName, null)
            val size = cborHello.size

            val frameHeader = byteArrayOf(
                ((size shr 24) and 0xFF).toByte(),
                ((size shr 16) and 0xFF).toByte(),
                ((size shr 8) and 0xFF).toByte(),
                (size and 0xFF).toByte()
            )

            val out = stream.outputStream
            out.write(frameHeader)
            out.write(cborHello)
            out.flush()

            // Read response
            val inp = stream.inputStream
            val header = ByteArray(4)
            var headerReadBytes = 0
            while (headerReadBytes < 4) {
                val r = ConnectionUtils.readWithDeadline(stream, 10_000) {
                    inp.read(header, headerReadBytes, 4 - headerReadBytes)
                }
                if (r == -1) {
                    quicConn.close()
                    return@withContext false
                }
                headerReadBytes += r
            }

            val responseSize = ((header[0].toInt() and 0xFF) shl 24) or
                               ((header[1].toInt() and 0xFF) shl 16) or
                               ((header[2].toInt() and 0xFF) shl 8) or
                               (header[3].toInt() and 0xFF)

            if (responseSize <= 0 || responseSize > 5 * 1024 * 1024) {
                quicConn.close()
                return@withContext false
            }

            val responseBody = ByteArray(responseSize)
            var bodyReadBytes = 0
            while (bodyReadBytes < responseSize) {
                val r = ConnectionUtils.readWithDeadline(stream, 10_000) {
                    inp.read(responseBody, bodyReadBytes, responseSize - bodyReadBytes)
                }
                if (r == -1) {
                    quicConn.close()
                    return@withContext false
                }
                bodyReadBytes += r
            }

            val workstationName = parseCoreHelloDeviceName(responseBody)
            if (workstationName == null) {
                quicConn.close()
                return@withContext false
            }

            val pcMac = parseCoreHelloPcMac(responseBody)
            if (pcMac != null && pcMac != ws.pcMac) {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.workstationDao().updatePcMac(ws.id, pcMac)
                    ConnectionRepository.appendLog("[CORE] Workstation PC MAC atualizado: $pcMac")
                } catch (e: Exception) {
                    ConnectionRepository.appendLog("[WARN] Falha ao atualizar PC MAC: ${e.message}")
                }
            }

            val pcAddrs = parseCoreHelloPcAddrs(responseBody)
            if (pcAddrs.isNotEmpty()) {
                ConnectionRepository.updateStationPcAddrs(ws.id, pcAddrs)
                ConnectionRepository.appendLog("[CORE] Endereços anunciados pelo PC: ${pcAddrs.map { "${it.kind}:${it.ip}" }}")
            }

            // Connection handshake succeeded! Register with ConnectionRepository
            if (myGeneration != connectionGeneration.get()) {
                activeConn.close()
                return@withContext false
            }
            ConnectionRepository.registerActiveConnection(activeConn, applicationContext, effectivePath, connectedHost)
            updateNotification("Ligado a ${ws.deviceName}")
            ConnectionRepository.setStatus(ConnectionStatus.CONNECTED)
            ConnectionRepository.appendLog("[SERVICE] Successfully connected to workstation ${ws.deviceName} ($workstationName)")

            // Send initial battery status immediately after core.hello
            var initialBatteryIntent: Intent? = null
            try {
                val batteryStatus: Intent? = applicationContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                initialBatteryIntent = batteryStatus
                if (batteryStatus != null) {
                    val level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                    val pct = if (scale > 0) (level * 100 / scale) else -1
                    val status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == android.os.BatteryManager.BATTERY_STATUS_FULL
                    
                    lastSentPct = pct
                    lastSentCharging = charging
                    
                    ConnectionRepository.sendPhoneBatteryState(pct, charging)
                }
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Initial phone battery send failed: ${e.message}")
            }

            // Send initial phone.status immediately after battery.state
            try {
                val initialStatus = PhoneStatusCollector.collect(applicationContext, initialBatteryIntent)
                ConnectionRepository.sendPhoneStatus(initialStatus)
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Initial phone.status send failed: ${e.message}")
            }

            // Replay active notifications that were already posted
            try {
                HyprNotificationListenerService.replayActiveNotifications(applicationContext)
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Initial notification replay failed: ${e.message}")
            }

            // Connection handshake succeeded! Monitor connection with application-level keepalive
            var consecutiveFailures = 0
            var pingTimer = 0L
            var phoneStatusTimer = 0L
            var wasStreaming = false
            while (isActive && activeConn.isConnected) {
                delay(1000)

                // Verificação de comutação dinâmica USB / Wi-Fi
                val curPath = ConnectionRepository.currentLinkPath.value
                val isUsbNow = UsbTetherHelper.isUsbNetworkActive.value
                val addrsNow = ConnectionRepository.getStationPcAddrs(ws.id)
                if (UsbConnectionLogic.shouldSwitchPath(curPath, isUsbNow, addrsNow)) {
                    ConnectionRepository.appendLog("[CONN] Transição de rota detetada ($curPath → USB: $isUsbNow); a comutar ligação...")
                    loopCoordinator.immediateRetryRequested.set(true)
                    break
                }

                val isStreamingNow = AudioStreamPlayer.isPlaying.value || WebcamStreamer.isStreaming.value
                if (isStreamingNow != wasStreaming) {
                    wasStreaming = isStreamingNow
                    if (isStreamingNow) {
                        acquireWifiLockIfNeeded()
                    } else {
                        releaseWifiLockIfHeld()
                    }
                }

                // Periodic phone.status send: 30s com ecrã aceso; 120s com ecrã apagado
                phoneStatusTimer += 1000
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val isScreenOn = pm?.isInteractive ?: true
                val intervalLimit = TelemetryDispatchLogic.getPeriodicIntervalLimit(isScreenOn)

                if (phoneStatusTimer >= intervalLimit) {
                    phoneStatusTimer = 0L
                    try {
                        val currentStatus = PhoneStatusCollector.collect(
                            applicationContext,
                            lastBatteryIntent,
                            lightMode = !isScreenOn
                        )
                        ConnectionRepository.sendPhoneStatus(currentStatus)
                    } catch (e: Exception) {
                        ConnectionRepository.appendLog("[WARN] phone.status send failed: ${e.message}")
                    }
                }

                pingTimer += 1000
                if (pingTimer >= 10_000) {
                    pingTimer = 0
                    if (isStreamingNow) {
                        // ping de app é redundante e destrutivo durante streaming contínuo —
                        // o keep_alive_interval nativo do QUIC (5s, lado do daemon) já cobre isso.
                        consecutiveFailures = 0
                    } else if (ConnectionRepository.activeConnection != null) {
                        val pingSuccess = ConnectionRepository.sendPing()
                        if (pingSuccess) {
                            consecutiveFailures = 0
                        } else {
                            consecutiveFailures++
                            ConnectionRepository.appendLog("[PING] Ping failed. Consecutive failures: $consecutiveFailures")
                            if (consecutiveFailures >= 2) {
                                ConnectionRepository.appendLog("[PING] 2 consecutive keepalive failures. Declaring connection dead.")
                                activeConn.close()
                                break
                            }
                        }
                    }
                }
            }

            activeConn.close()
            return@withContext true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            if (myGeneration == connectionGeneration.get()) {
                closeConnection(quicConn) // só limpo se ainda for a tentativa atual
            } else {
                try { quicConn?.close() } catch (e: Exception) {} // fecho a MINHA conexão sem tocar no estado partilhado
            }
        }
    }

    private fun closeConnection(connectionToClose: QuicClientConnection? = null) {
        try {
            releaseWifiLockIfHeld()
            if (connectionToClose != null) {
                connectionToClose.close()
                if (activeQuicConn === connectionToClose) {
                    activeQuicConn = null
                    ConnectionRepository.clearActiveConnection()
                }
            } else {
                activeQuicConn?.close()
                activeQuicConn = null
                ConnectionRepository.clearActiveConnection()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                forceReconnect()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                triggerPhoneStatusDebounce()
                forceReconnect()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                triggerPhoneStatusDebounce()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        networkCallback = null
    }

    private fun forceReconnect() {
        startConnectionLoop()
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        lastBatteryIntent = intent
                        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                        val pct = if (scale > 0) (level * 100 / scale) else -1
                        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                       status == android.os.BatteryManager.BATTERY_STATUS_FULL

                        val isRelevant = TelemetryDispatchLogic.isRelevantBatteryChange(
                            oldPct = lastSentPct,
                            oldCharging = lastSentCharging,
                            newPct = pct,
                            newCharging = charging
                        )

                        if (pct != lastSentPct || charging != lastSentCharging) {
                            lastSentPct = pct
                            lastSentCharging = charging
                            serviceScope.launch {
                                try {
                                    if (ConnectionRepository.activeConnection != null) {
                                        ConnectionRepository.sendPhoneBatteryState(pct, charging)
                                    }
                                } catch (e: Exception) {
                                    ConnectionRepository.appendLog("[WARN] Failed to send battery state change: ${e.message}")
                                }
                            }
                        }
                        if (isRelevant) {
                            triggerPhoneStatusDebounce(isRelevantChange = true)
                        }
                    }
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_SCREEN_OFF,
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        triggerPhoneStatusDebounce(isRelevantChange = true)
                    }
                    Intent.ACTION_USER_UNLOCKED -> {
                        ConnectionRepository.appendLog("[SECURITY] ACTION_USER_UNLOCKED recebido. A tentar restabelecer identidade e ligar...")
                        if (activeQuicConn?.isConnected != true) {
                            forceReconnect()
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        try {
            batteryReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        batteryReceiver = null
    }

    private fun setupMediaSessionObserver() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val comp = ComponentName(this, HyprNotificationListenerService::class.java)
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                updateMediaControllers(controllers)
                triggerPhoneStatusDebounce(500L, isRelevantChange = true)
            }
            msm.addOnActiveSessionsChangedListener(listener, comp)
            activeSessionsListener = listener
            mediaSessionManager = msm

            val initial = msm.getActiveSessions(comp)
            updateMediaControllers(initial)
        } catch (_: Exception) {}
    }

    private fun updateMediaControllers(controllers: List<MediaController>?) {
        activeMediaControllers.forEach { ctrl ->
            try { ctrl.unregisterCallback(mediaCallback) } catch (_: Exception) {}
        }
        activeMediaControllers.clear()
        controllers?.filter { it.packageName != packageName }?.forEach { ctrl ->
            try {
                ctrl.registerCallback(mediaCallback)
                activeMediaControllers.add(ctrl)
            } catch (_: Exception) {}
        }
    }

    private fun teardownMediaSessionObserver() {
        try {
            activeSessionsListener?.let {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(it)
            }
            updateMediaControllers(emptyList())
        } catch (_: Exception) {}
        activeSessionsListener = null
        mediaSessionManager = null
    }
}
