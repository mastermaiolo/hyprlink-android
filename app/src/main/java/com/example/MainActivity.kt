// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.ui.kit.HyprIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.ui.kit.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.security.MessageDigest

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent

class MainActivity : ComponentActivity() {

    companion object {
        var currentActiveTab: String = "dashboard"
        const val ACTION_START_WEBCAM = "com.example.action.START_WEBCAM"
        const val ACTION_START_MIC = "com.example.action.START_MIC"
    }

    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastBackTapPeakTime: Long = 0L
    private var lastBackTapTriggerTime: Long = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                val z = event.values[2]
                val now = System.currentTimeMillis()
                if (kotlin.math.abs(z) > 15f) {
                    if (lastBackTapPeakTime > 0L && GestureLogic.isDoubleTapPeak(lastBackTapPeakTime, now)) {
                        if (GestureLogic.shouldTriggerDebouncedEvent(lastBackTapTriggerTime, now)) {
                            lastBackTapTriggerTime = now
                            lastBackTapPeakTime = 0L
                            val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
                            if (prefs.getBoolean("gestures_enabled", true) && prefs.getBoolean("gesture_double_tap_back_enabled", false)) {
                                lifecycleScope.launch {
                                    ConnectionRepository.sendGesture("double_tap_back", null)
                                }
                            }
                        }
                    } else {
                        lastBackTapPeakTime = now
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (currentActiveTab == "touchpad" && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("gestures_enabled", true) && prefs.getBoolean("gesture_volume_enabled", true)) {
                val dir = GestureLogic.mapVolumeKeyCode(keyCode)
                if (dir != null) {
                    lifecycleScope.launch {
                        ConnectionRepository.sendGesture("volume", dir)
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("gestures_enabled", true) && prefs.getBoolean("gesture_rotate_landscape_enabled", true)) {
                lifecycleScope.launch {
                    ConnectionRepository.sendGesture("rotate_landscape", null)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioStreamPlayer.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.initialize(applicationContext)
        ConnectionRepository.initialize(applicationContext)
        com.example.find.PhoneFindManager.checkAndRestoreInterruptedAlarmVolume(applicationContext)
        enableEdgeToEdge()
        handleShareIntent(intent)
        handleWebcamIntent(intent)
        handleMicIntent(intent)
        
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    HyprLinkDashboard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
        handleWebcamIntent(intent)
        handleMicIntent(intent)
    }

    private fun syncClipboardToPc(clipboard: ClipboardManager) {
        if (clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                if (text.isNotEmpty() && text != ConnectionRepository.lastReceivedClipboardText && text != ConnectionRepository.lastSentClipboardText) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            ConnectionRepository.sendClipboardText(text)
                        } catch (e: Exception) {
                            ConnectionRepository.appendLog("[CLIPBOARD] Error pushing: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Synchronize clipboard (foreground only)
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val listener = ClipboardManager.OnPrimaryClipChangedListener {
                syncClipboardToPc(clipboard)
            }
            clipboard.addPrimaryClipChangedListener(listener)
            clipboardListener = listener
            // Sincronizar também ao retomar o primeiro plano caso tenha sido copiado noutra app
            syncClipboardToPc(clipboard)
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[CLIPBOARD] Register failed: ${e.message}")
        }

        // Accelerometer for back-tap gestures
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("gestures_enabled", true) && prefs.getBoolean("gesture_double_tap_back_enabled", false)) {
                sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            // Ignore sensor init failure
        }
    }

    override fun onPause() {
        super.onPause()
        clipboardListener?.let {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(it)
        }
        clipboardListener = null

        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun handleWebcamIntent(intent: Intent?) {
        if (intent == null || intent.action != ACTION_START_WEBCAM) return
        val packetId = intent.getLongExtra("packet_id", 0L)
        val width = intent.getIntExtra("width", 1280)
        val height = intent.getIntExtra("height", 720)
        val fps = intent.getIntExtra("fps", 24)
        val codec = intent.getStringExtra("codec") ?: "h264"
        val conn = ConnectionRepository.activeConnection
        if (conn != null && packetId != 0L) {
            WebcamStreamer.start(this, conn, packetId, width, height, fps, codec)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(ConnectionRepository.NOTIFICATION_WEBCAM_PROMPT_ID)
    }

    private fun handleMicIntent(intent: Intent?) {
        if (intent == null || intent.action != ACTION_START_MIC) return
        WebcamStreamer.startMic(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(ConnectionRepository.NOTIFICATION_MIC_PROMPT_ID)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type || type.startsWith("text/")) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    lifecycleScope.launch {
                        delay(600)
                        if (ConnectionRepository.activeConnection != null) {
                            val isUrl = android.util.Patterns.WEB_URL.matcher(sharedText).matches() || android.webkit.URLUtil.isNetworkUrl(sharedText)
                            if (isUrl) {
                                val appStrings = getAppStrings(LanguageManager.currentLanguage.value)
                                if (ShareUrlValidator.isValidUrl(sharedText.trim())) {
                                    ConnectionRepository.sendUrl(sharedText.trim())
                                    Toast.makeText(this@MainActivity, appStrings.toastUrlShared, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, appStrings.urlOnlyHttpHttps, Toast.LENGTH_LONG).show()
                                    ConnectionRepository.appendLog("[SHARE] URL recusado (só http/https): $sharedText")
                                }
                            } else {
                                ConnectionRepository.sendClipboardText(sharedText)
                                val appStrings = getAppStrings(LanguageManager.currentLanguage.value)
        Toast.makeText(this@MainActivity, appStrings.toastSentToClipboard, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val appStrings = getAppStrings(LanguageManager.currentLanguage.value)
        Toast.makeText(this@MainActivity, appStrings.toastNoWorkstationConnected, Toast.LENGTH_LONG).show()
                            ConnectionRepository.appendLog("[SHARE] Share failed (offline): $sharedText")
                        }
                    }
                }
            } else {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    lifecycleScope.launch {
                        delay(600)
                        if (ConnectionRepository.activeConnection != null) {
                            val name = getFileName(uri) ?: "shared_file.bin"
                            val size = getFileSize(uri) ?: 0L
                            ConnectionRepository.sendFile(applicationContext, uri, name, size)
                        } else {
                            val appStrings = getAppStrings(LanguageManager.currentLanguage.value)
        Toast.makeText(this@MainActivity, appStrings.toastWorkstationOffline, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getFileSize(uri: Uri): Long? {
        var result: Long? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (index != -1) {
                        result = cursor.getLong(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        return result
    }
}

@Composable
fun HyprLinkDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appStrings = LocalAppStrings.current
    val sharedPrefs = remember { context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isBatteryOptIgnored by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }
    var isDndGranted by remember {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        mutableStateOf(nm?.isNotificationPolicyAccessGranted ?: false)
    }
    var isNotifListenerGranted by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                isBatteryOptIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                isDndGranted = nm?.isNotificationPolicyAccessGranted ?: false
                isNotifListenerGranted = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showInitialPermissionsDialog by remember { mutableStateOf(false) }
    var hasRequestedBatteryExemptionThisSession by rememberSaveable { mutableStateOf(false) }
    var showBatteryExemptionDialog by remember { mutableStateOf(false) }
    var hasRequestedDndThisSession by rememberSaveable { mutableStateOf(false) }
    var showDndDialog by remember { mutableStateOf(false) }

    var isManuallyConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasSeen = sharedPrefs.getBoolean("has_seen_initial_permissions_dialog", false)
        if (!hasSeen) {
            showInitialPermissionsDialog = true
        }
    }

    // Connect with ConnectionRepository observable flows
    val connStatus by ConnectionRepository.connectionStatus.collectAsStateWithLifecycle()

    LaunchedEffect(connStatus) {
        if (connStatus != ConnectionStatus.CONNECTING) {
            isManuallyConnecting = false
        }
    }
    val batteryLevel by ConnectionRepository.batteryLevel.collectAsStateWithLifecycle()
    val batteryCharging by ConnectionRepository.batteryCharging.collectAsStateWithLifecycle()
    val pcBattery by ConnectionRepository.pcBattery.collectAsStateWithLifecycle()
    val mediaState by ConnectionRepository.mediaState.collectAsStateWithLifecycle()
    val pcStatus by ConnectionRepository.pcStatus.collectAsStateWithLifecycle()
    val pcShortcuts by ConnectionRepository.pcShortcuts.collectAsStateWithLifecycle()
    val fileSendingProgress by ConnectionRepository.fileSendingProgress.collectAsStateWithLifecycle()
    val fileSendingStatus by ConnectionRepository.fileSendingStatus.collectAsStateWithLifecycle()
    val repositoryLogs by ConnectionRepository.logs.collectAsStateWithLifecycle()
    val isWebcamStreaming by WebcamStreamer.isStreaming.collectAsStateWithLifecycle()
    val lensLabel by WebcamStreamer.lensLabel.collectAsStateWithLifecycle()
    val rotationDeg by WebcamStreamer.rotationDeg.collectAsStateWithLifecycle()
    val mirrored by WebcamStreamer.mirrored.collectAsStateWithLifecycle()
    val codecPreference by WebcamStreamer.codecPreference.collectAsStateWithLifecycle()
    val activeCodec by WebcamStreamer.activeCodec.collectAsStateWithLifecycle()
    val isMicOn by WebcamStreamer.isMicOn.collectAsStateWithLifecycle()
    val isTorchOn by WebcamStreamer.isTorchOn.collectAsStateWithLifecycle()
    val zoomRatio by WebcamStreamer.zoomRatio.collectAsStateWithLifecycle()
    val currentWidth by WebcamStreamer.currentWidth.collectAsStateWithLifecycle()
    val currentHeight by WebcamStreamer.currentHeight.collectAsStateWithLifecycle()
    val currentFps by WebcamStreamer.currentFps.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                WebcamStreamer.startMic(context)
            } else {
                ConnectionRepository.appendLog("[WEBCAM] Permissão de microfone rejeitada pelo utilizador")
            }
        }
    )

    var isWebcamStarting by remember { mutableStateOf(false) }

    LaunchedEffect(isWebcamStreaming) {
        if (isWebcamStreaming) {
            isWebcamStarting = false
        }
    }

    LaunchedEffect(connStatus) {
        if (connStatus != ConnectionStatus.CONNECTED) {
            isWebcamStarting = false
        }
    }

    val startWebcamRequest: () -> Unit = {
        if (connStatus != ConnectionStatus.CONNECTED) {
            Toast.makeText(context, appStrings.disconnected, Toast.LENGTH_SHORT).show()
        } else {
            isWebcamStarting = true
            coroutineScope.launch {
                val width = WebcamStreamer.currentWidth.value
                val height = WebcamStreamer.currentHeight.value
                val fps = WebcamStreamer.currentFps.value
                val prefCodec = WebcamStreamer.codecPreference.value
                val codec = if (prefCodec != "auto") prefCodec else null

                try {
                    ConnectionRepository.appendLog("[WEBCAM] A pedir início da câmara ao PC...")
                    val result = ConnectionRepository.requestWebcam(width, height, fps, codec, timeoutMs = 10_000L)
                    if (!result.ok) {
                        isWebcamStarting = false
                        val errorMsg = WebcamLogic.translateWebcamError(result.error, appStrings)
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        ConnectionRepository.appendLog("[WEBCAM] Pedido recusado pelo PC: ${result.error ?: "desconhecido"}")
                    } else {
                        ConnectionRepository.appendLog("[WEBCAM] PC aceitou o pedido. A aguardar webcam.start...")
                        val startTime = System.currentTimeMillis()
                        while (System.currentTimeMillis() - startTime < 10_000L) {
                            if (WebcamStreamer.isStreaming.value) {
                                break
                            }
                            delay(200)
                        }
                        if (!WebcamStreamer.isStreaming.value && isWebcamStarting) {
                            isWebcamStarting = false
                            val timeoutMsg = WebcamLogic.translateWebcamError("timeout", appStrings)
                            Toast.makeText(context, timeoutMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    isWebcamStarting = false
                    val errorMsg = WebcamLogic.translateWebcamError(e.message, appStrings)
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    ConnectionRepository.appendLog("[WEBCAM] Falha no pedido: ${e.message}")
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startWebcamRequest()
            } else {
                Toast.makeText(context, appStrings.permRequiredCamera, Toast.LENGTH_SHORT).show()
                ConnectionRepository.appendLog("[WEBCAM] Permissão de câmara rejeitada pelo utilizador")
            }
        }
    )

    var isServiceActive by rememberSaveable {
        mutableStateOf(sharedPrefs.getBoolean("is_service_active", true))
    }

    // Room DB instances
    val db = remember { AppDatabase.getDatabase(context) }
    val repository = remember { WorkstationRepository(db.workstationDao()) }
    val workstations by repository.allWorkstations.collectAsStateWithLifecycle(initialValue = null)

    var activeWorkstationId by rememberSaveable {
        mutableStateOf(sharedPrefs.getString("active_workstation_id", "") ?: "")
    }

    var hasCheckedInitialPairing by rememberSaveable { mutableStateOf(false) }

    // Dialog state
    var showNewPairDialog by remember { mutableStateOf(false) }
    var showUrlShareDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirmDialog by remember { mutableStateOf(false) }
    var showShareChoiceDialog by remember { mutableStateOf(false) }
    var showMediaControlsDialog by remember { mutableStateOf(false) }
    var showWorkstationSelectorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workstations) {
        if (workstations == null) return@LaunchedEffect
        val resolution = WorkstationSelectionLogic.resolveActiveWorkstation(workstations, activeWorkstationId)
        if (activeWorkstationId != resolution.resolvedId) {
            activeWorkstationId = resolution.resolvedId
            sharedPrefs.edit().putString("active_workstation_id", resolution.resolvedId).apply()
        }
        if (resolution.shouldOpenPairing && !hasCheckedInitialPairing) {
            hasCheckedInitialPairing = true
            showNewPairDialog = true
        } else if (!resolution.shouldOpenPairing) {
            hasCheckedInitialPairing = true
        }
    }

    val activeWorkstation = remember(workstations, activeWorkstationId) {
        WorkstationSelectionLogic.resolveActiveWorkstation(workstations, activeWorkstationId).activeWorkstation
    }

    LaunchedEffect(isServiceActive, activeWorkstation, workstations) {
        if (workstations == null) {
            // Enquanto o Room estiver a carregar, não toca no serviço nem nas preferências
            return@LaunchedEffect
        }
        val intent = Intent(context, HyprLinkConnectionService::class.java)
        val editor = sharedPrefs.edit()
        editor.putBoolean("is_service_active", isServiceActive)
        if (activeWorkstation != null) {
            editor.putString("active_workstation_id", activeWorkstation.id)
            editor.apply()
            if (isServiceActive) {
                ContextCompat.startForegroundService(context, intent)
                if (!hasRequestedBatteryExemptionThisSession && !showInitialPermissionsDialog) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                    if (!isIgnoring && sharedPrefs.getBoolean("has_seen_initial_permissions_dialog", false)) {
                        showBatteryExemptionDialog = true
                    }
                }
            } else {
                context.stopService(intent)
            }
        } else {
            editor.putString("active_workstation_id", "")
            editor.apply()
            context.stopService(intent)
        }
    }

    val deviceName = activeWorkstation?.deviceName ?: appStrings.noStation
    val deviceHost = activeWorkstation?.host ?: "0.0.0.0"
    val devicePort = activeWorkstation?.port ?: "----"
    val deviceFingerprint = activeWorkstation?.fingerprint ?: appStrings.noFingerprintConfigured

    var showIdentityCorruptedBanner by rememberSaveable {
        mutableStateOf(DeviceIdentity.hasIdentityBeenResetDueToCorruption(context))
    }

    LaunchedEffect(Unit) {
        if (DeviceIdentity.hasIdentityBeenResetDueToCorruption(context)) {
            showIdentityCorruptedBanner = true
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val name = getFileName(context, uri) ?: "file.bin"
                val size = getFileSize(context, uri) ?: 0L
                ConnectionRepository.sendFile(context, uri, name, size)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    Toast.makeText(context, appStrings.toastSendingImage, Toast.LENGTH_SHORT).show()
                    val ok = ConnectionRepository.sendClipboardImageFromUri(context, uri)
                    if (ok) {
                        Toast.makeText(context, appStrings.toastImageSent, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, appStrings.toastImageSendFailed, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "${appStrings.errorTag}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var isWakingPc by remember { mutableStateOf(false) }
    var wakingSecondsRemaining by remember { mutableStateOf(0) }
    var wakeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(connStatus) {
        if (connStatus == ConnectionStatus.CONNECTED && isWakingPc) {
            isWakingPc = false
            wakingSecondsRemaining = 0
            wakeJob?.cancel()
            wakeJob = null
            Toast.makeText(context, appStrings.toastWolSuccess, Toast.LENGTH_SHORT).show()
        }
    }

    // Glow and pulse transition animations
    val infiniteTransition = rememberInfiniteTransition(label = "HyprGlowPulse")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )

    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationDegrees"
    )

    var currentTab by rememberSaveable { mutableStateOf("dashboard") }
    LaunchedEffect(currentTab) { MainActivity.currentActiveTab = currentTab }
    var showFullJournal by rememberSaveable { mutableStateOf(false) }
    var showFullScreenConsole by remember { mutableStateOf(false) }

    val linkColor by animateColorAsState(
        targetValue = when (connStatus) {
            ConnectionStatus.CONNECTED -> CyanActive
            ConnectionStatus.CONNECTING -> AmberWarning
            ConnectionStatus.DISCONNECTED -> RedError
        },
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "LinkColor"
    )

    Box(modifier = modifier) {
        // Main layout container with Scaffold for Navigation
            val isAudioStreamingPhone by AudioStreamPlayer.isPlaying.collectAsStateWithLifecycle()
            val transfersList by ConnectionRepository.transfers.collectAsStateWithLifecycle()

            var audioSinks by remember {
                mutableStateOf<List<com.example.ui.AudioSinkItem>>(emptyList())
            }

            var audioApps by remember {
                mutableStateOf<List<com.example.ui.AudioAppItem>>(emptyList())
            }

            LaunchedEffect(currentTab, connStatus) {
                if (connStatus == ConnectionStatus.CONNECTED && currentTab == "audio") {
                    try {
                        val fetched = ConnectionRepository.fetchAudioState()
                        if (fetched.sinks.isNotEmpty()) {
                            audioSinks = fetched.sinks.map { s ->
                                com.example.ui.AudioSinkItem(
                                    id = s.id,
                                    name = s.name,
                                    description = s.description,
                                    volume = s.volume,
                                    isMuted = s.muted,
                                    isDefault = s.is_default,
                                    isPhone = s.is_phone
                                )
                            }
                        }
                        if (fetched.apps.isNotEmpty()) {
                            audioApps = fetched.apps.map { a ->
                                com.example.ui.AudioAppItem(
                                    id = a.id,
                                    name = a.name,
                                    media = a.media ?: "",
                                    volume = a.volume,
                                    isMuted = a.muted,
                                    isPlaying = true,
                                    idleMinutes = 0
                                )
                            }
                        }
                    } catch (e: Exception) {}
                }
            }

            var mcWorkspaces by remember { mutableStateOf<List<com.example.ui.WorkspaceItem>>(emptyList()) }
            var mcWindows by remember { mutableStateOf<List<com.example.ui.WindowClientItem>>(emptyList()) }
            var isMcLoading by remember { mutableStateOf(false) }
            var showSuspendDialog by remember { mutableStateOf(false) }

            val isConnected = connStatus == ConnectionStatus.CONNECTED
            var customShortcutCmd by remember { mutableStateOf(sharedPrefs.getString("custom_shortcut_cmd", "")?.trim() ?: "") }

            fun refreshHypr() {
                if (!isConnected) return
                coroutineScope.launch {
                    isMcLoading = true
                    try {
                        val wsJson = ConnectionRepository.fetchHyprWorkspaces()
                        val clJson = ConnectionRepository.fetchHyprClients()
                        val rawWs = parseWorkspaces(wsJson)
                        val rawClients = parseClients(clJson)

                        val focusedWsId = rawClients.find { it.focusHistoryId == 0 }?.workspaceId
                            ?: rawWs.find { it.hasFullscreen }?.id

                        val maxId = maxOf(10, rawWs.maxOfOrNull { it.id } ?: 10)
                        mcWorkspaces = (1..maxId).mapNotNull { id ->
                            val found = rawWs.find { it.id == id }
                            val winCount = rawClients.count { it.workspaceId == id }
                            if (id <= 10 || found != null || winCount > 0) {
                                com.example.ui.WorkspaceItem(
                                    id = id,
                                    name = found?.name ?: "$id",
                                    windowsCount = winCount,
                                    isActive = (id == focusedWsId)
                                )
                            } else null
                        }
                        mcWindows = rawClients.map { c ->
                            com.example.ui.WindowClientItem(
                                address = c.address,
                                title = c.title,
                                clientClass = c.clazz,
                                workspaceId = c.workspaceId,
                                isFocused = c.focusHistoryId == 0 || (focusedWsId == null && c.fullscreen)
                            )
                        }
                    } catch (e: Exception) {
                        ConnectionRepository.appendLog("[HYPRLAND] Fetch error: ${e.message}")
                    } finally {
                        isMcLoading = false
                    }
                }
            }

            fun executeCommandAndRefresh(cmd: String) {
                coroutineScope.launch {
                    try {
                        val ok = ConnectionRepository.dispatchHyprCommand(cmd)
                        if (ok) {
                            Toast.makeText(context, appStrings.actionDone, Toast.LENGTH_SHORT).show()
                            delay(600)
                            refreshHypr()
                        } else {
                            Toast.makeText(context, "${appStrings.actionFailed}: ${appStrings.toastShortcutFailed}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        ConnectionRepository.appendLog("[HYPRLAND] Dispatch error: ${e.message}")
                        Toast.makeText(context, "${appStrings.actionFailed}: ${e.message ?: appStrings.toastShortcutFailed}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            fun executePcAction(name: String) {
                coroutineScope.launch {
                    try {
                        val result = ConnectionRepository.sendPcAction(name)
                        if (result.ok) {
                            Toast.makeText(context, appStrings.actionDone, Toast.LENGTH_SHORT).show()
                        } else {
                            val err = result.error ?: appStrings.toastShortcutFailed
                            Toast.makeText(context, "${appStrings.actionFailed}: $err", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "${appStrings.actionFailed}: ${e.message ?: appStrings.toastShortcutFailed}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            var runningShortcutId by remember { mutableStateOf<String?>(null) }

            fun executeShortcut(id: String) {
                if (runningShortcutId != null) return
                runningShortcutId = id
                coroutineScope.launch {
                    try {
                        val result = ConnectionRepository.runShortcut(id)
                        if (result.ok) {
                            Toast.makeText(context, appStrings.shortcutExecuted, Toast.LENGTH_SHORT).show()
                        } else {
                            val errorMsg = PcShortcutLogic.translateShortcutError(result.error, appStrings)
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, appStrings.shortcutErrorGeneric, Toast.LENGTH_SHORT).show()
                    } finally {
                        runningShortcutId = null
                    }
                }
            }

            LaunchedEffect(currentTab, isConnected) {
                if (currentTab == "desktop" && isConnected) {
                    refreshHypr()
                }
            }

            @OptIn(kotlinx.coroutines.FlowPreview::class)
            LaunchedEffect(isConnected) {
                if (isConnected) {
                    ConnectionRepository.hyprEvents
                        .debounce(300L)
                        .collect { event ->
                            if (currentTab == "desktop") {
                                refreshHypr()
                            }
                        }
                }
            }

            val currentLinkState = when (connStatus) {
                ConnectionStatus.CONNECTED -> com.example.ui.theme.LinkState.CONNECTED
                ConnectionStatus.CONNECTING -> com.example.ui.theme.LinkState.NEGOTIATING
                ConnectionStatus.DISCONNECTED -> com.example.ui.theme.LinkState.DISCONNECTED
            }

            val ms = mediaState
            var localPositionMs by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(ms?.positionMs, ms?.status, ms?.title) {
                localPositionMs = ms?.positionMs
                if (ms?.status.equals("playing", ignoreCase = true) && ms?.positionMs != null) {
                    while (isActive && ms?.status.equals("playing", ignoreCase = true)) {
                        delay(1000)
                        localPositionMs = (localPositionMs ?: ms?.positionMs ?: 0L) + 1000L
                        if (ms?.lengthMs != null && localPositionMs!! > ms.lengthMs!!) {
                            localPositionMs = ms.lengthMs!!
                        }
                    }
                }
            }

            val isMediaPlaying = isConnected && ms?.status.equals("playing", ignoreCase = true) && !ms?.title.isNullOrBlank()
            val pos = localPositionMs ?: ms?.positionMs
            val len = ms?.lengthMs
            val mediaProgress = if (isMediaPlaying && pos != null && len != null && len > 0) {
                (pos.toFloat() / len.toFloat()).coerceIn(0f, 1f)
            } else null
            val mediaElapsedTime = if (isMediaPlaying && pos != null) {
                val sec = pos / 1000
                String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)
            } else null
            val mediaTotalTime = if (isMediaPlaying && len != null) {
                val sec = len / 1000
                String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)
            } else null
            val currentMediaTitle = if (isConnected && ms != null && !ms.title.isNullOrBlank()) ms.title!! else ""
            val currentMediaArtist = if (isConnected && ms != null && !ms.artist.isNullOrBlank()) ms.artist!! else ""
            val rawPlayer = ms?.player ?: ""
            val currentMediaAppSource = if (rawPlayer.isNotBlank()) rawPlayer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } else ""
            val clipboardText = ConnectionRepository.lastReceivedClipboardText

            val sentTodayCount = remember(transfersList) {
                val calToday = java.util.Calendar.getInstance()
                transfersList.count { item ->
                    val calItem = java.util.Calendar.getInstance().apply { timeInMillis = item.timestamp }
                    item.direction == TransferDirection.UPLOAD &&
                    (item.status == TransferStatus.VERIFICADO || item.status == TransferStatus.NAO_VERIFICADO) &&
                    calToday.get(java.util.Calendar.YEAR) == calItem.get(java.util.Calendar.YEAR) &&
                    calToday.get(java.util.Calendar.DAY_OF_YEAR) == calItem.get(java.util.Calendar.DAY_OF_YEAR)
                }
            }

            fun formatTransferDate(timestamp: Long): String {
                val now = java.util.Calendar.getInstance()
                val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val timeStr = timeFormat.format(java.util.Date(timestamp))
                
                val isToday = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
                              now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
                if (isToday) return timeStr

                val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                val isYesterday = yesterday.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
                                  yesterday.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
                if (isYesterday) {
                    return "${appStrings.yesterdayAt} $timeStr"
                }

                val dateFormat = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault())
                return dateFormat.format(java.util.Date(timestamp))
            }

            val now = System.currentTimeMillis()
            val activeTransfers = remember(transfersList, appStrings) {
                com.example.ConnectionRepository.checkStalledTransfers()
                transfersList.filter { 
                    it.status == TransferStatus.EM_CURSO && it.progress < 1f && !(it.bytesTransferred == 0L && now - it.timestamp > 30_000L)
                }.map {
                    val speedText = com.example.TransferRateFormatter.transferSpeed(it.bytesPerSec, it.etaSeconds, appStrings)
                    com.example.ui.ActiveTransferItem(
                        id = it.id.toString(),
                        name = it.name,
                        isIncoming = it.direction == TransferDirection.DOWNLOAD,
                        currentBytes = if (it.bytesTransferred > 0L) it.bytesTransferred else (it.size * it.progress).toLong(),
                        totalBytes = it.size,
                        progress = it.progress,
                        speedText = speedText
                    )
                }
            }
            val completedTransfers = remember(transfersList, appStrings) {
                transfersList.filter { 
                    it.status == TransferStatus.VERIFICADO || it.status == TransferStatus.NAO_VERIFICADO || (it.status == TransferStatus.EM_CURSO && it.progress >= 1f)
                }.map {
                    val summaryText = if (it.durationMs != null && it.bytesPerSec != null) {
                        com.example.TransferRateFormatter.transferSummary(it.size, it.durationMs, it.bytesPerSec, appStrings)
                    } else null
                    com.example.ui.CompletedTransferItem(
                        id = it.id.toString(),
                        name = it.name,
                        isIncoming = it.direction == TransferDirection.DOWNLOAD,
                        totalBytes = it.size,
                        dateLabel = formatTransferDate(it.timestamp),
                        sha256Hash = it.sha256Local ?: appStrings.transferVerifiedHash,
                        isVerified = it.status == TransferStatus.VERIFICADO || it.progress >= 1f,
                        uri = it.uri,
                        savedNameOnPc = it.savedNameOnPc,
                        summaryText = summaryText
                    )
                }
            }
            val errorTransfers = remember(transfersList) {
                transfersList.filter { 
                    it.status == TransferStatus.ERRO || (it.status == TransferStatus.EM_CURSO && it.bytesTransferred == 0L && now - it.timestamp > 30_000L)
                }.map {
                    com.example.ui.ErrorTransferItem(
                        id = it.id.toString(),
                        name = it.name,
                        isIncoming = it.direction == TransferDirection.DOWNLOAD,
                        totalBytes = it.size,
                        failedPercentage = (it.progress * 100).toInt(),
                        technicalMessage = it.error ?: appStrings.transferTimeoutMessage,
                        friendlyExplanation = appStrings.transferNetworkErrorMessage
                    )
                }
            }

            val appStrings = LocalAppStrings.current

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = HyprColors.Background,
                topBar = {
                    val isHome = currentTab == "dashboard" && !showFullJournal
                    val batteryDisplay = PcBatteryLogic.pcBatteryLine(
                        battery = pcBattery,
                        isConnected = currentLinkState == com.example.ui.theme.LinkState.CONNECTED,
                        strings = appStrings
                    )
                    val homeSubtitle = when {
                        currentLinkState == com.example.ui.theme.LinkState.CONNECTED ->
                            batteryDisplay?.text
                        currentLinkState == com.example.ui.theme.LinkState.NEGOTIATING || isManuallyConnecting ->
                            appStrings.negotiatingTls
                        else ->
                            currentLinkState.getLocalizedLabel(appStrings)
                    }
                    val homeSubtitleColor = when {
                        currentLinkState == com.example.ui.theme.LinkState.CONNECTED ->
                            batteryDisplay?.color ?: com.example.ui.theme.Hypr.Sub
                        else ->
                            com.example.ui.theme.Hypr.Sub
                    }

                    val linkPath by ConnectionRepository.currentLinkPath.collectAsStateWithLifecycle()
                    val pathBadge = if (currentLinkState == com.example.ui.theme.LinkState.CONNECTED) {
                        if (linkPath == LinkPathType.USB) appStrings.chipUsb else appStrings.chipWifi
                    } else null

                    com.example.ui.kit.HyprMasthead(
                        state = currentLinkState,
                        device = deviceName.ifBlank { null }?.uppercase(),
                        stateLabel = currentLinkState.getLocalizedLabel(appStrings),
                        onDeviceClick = { showWorkstationSelectorDialog = true },
                        large = isHome,
                        subtitle = homeSubtitle,
                        subtitleColor = homeSubtitleColor,
                        pathBadge = pathBadge
                    )
                },
                bottomBar = {
                    val navItems = listOf(
                        com.example.ui.kit.HyprNavItem("dashboard", "01", appStrings.navHome),
                        com.example.ui.kit.HyprNavItem("audio", "02", appStrings.navMedia),
                        com.example.ui.kit.HyprNavItem("touchpad", "03", appStrings.navMouse),
                        com.example.ui.kit.HyprNavItem("desktop", "04", appStrings.navActions),
                        com.example.ui.kit.HyprNavItem("transfers", "05", appStrings.navFiles),
                        com.example.ui.kit.HyprNavItem("notifications", "06", appStrings.navSettings),
                    )
                    com.example.ui.kit.HyprBottomNav(
                        items = navItems,
                        selected = currentTab,
                        onSelect = {
                            if (it != "dashboard") {
                                showFullJournal = false
                            }
                            currentTab = it
                        },
                        modifier = Modifier.testTag("bottom_nav_bar")
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(HyprColors.Background)
                ) {
                    if (showIdentityCorruptedBanner && currentTab == "dashboard") {
                        com.example.ui.kit.Panel(
                            background = com.example.ui.theme.Hypr.HotTint,
                            border = com.example.ui.theme.Hypr.BorderRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            com.example.ui.kit.Kicker(appStrings.identityCorruptedBannerTitle, color = com.example.ui.theme.Hypr.Hot, strong = true)
                            com.example.ui.kit.Gap(4.dp)
                            com.example.ui.kit.Body(appStrings.identityCorruptedBannerMessage, color = com.example.ui.theme.Hypr.Paper)
                            com.example.ui.kit.Gap(8.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.example.ui.kit.HyprButton(
                                    label = appStrings.identityCorruptedBannerButton,
                                    kind = com.example.ui.kit.HyprButtonKind.Primary,
                                    onClick = {
                                        showIdentityCorruptedBanner = false
                                        DeviceIdentity.clearIdentityResetFlag(context)
                                        showNewPairDialog = true
                                    }
                                )
                                com.example.ui.kit.HyprButton(
                                    label = appStrings.closeDialog,
                                    kind = com.example.ui.kit.HyprButtonKind.Ghost,
                                    onClick = {
                                        showIdentityCorruptedBanner = false
                                        DeviceIdentity.clearIdentityResetFlag(context)
                                    }
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (currentTab) {
                        "dashboard" -> {
                            if (showFullJournal) {
                                com.example.ui.FullJournalScreen(
                                    terminalLogs = repositoryLogs,
                                    onBack = { showFullJournal = false },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                com.example.ui.DashboardScreen(
                                linkState = currentLinkState,
                                deviceName = deviceName,
                                onDeviceClick = { showWorkstationSelectorDialog = true },
                                onStateButtonClick = {
                                    if (workstations.isNullOrEmpty()) {
                                        showNewPairDialog = true
                                    } else {
                                        isManuallyConnecting = true
                                        if (!isServiceActive) {
                                            isServiceActive = true
                                        } else {
                                            val reconnectIntent = Intent(context, HyprLinkConnectionService::class.java).apply {
                                                action = "ACTION_RECONNECT"
                                            }
                                            context.startService(reconnectIntent)
                                        }
                                    }
                                },
                                mediaTitle = currentMediaTitle,
                                mediaArtist = currentMediaArtist,
                                mediaAppSource = currentMediaAppSource,
                                isPlaying = isMediaPlaying,
                                onPrevClick = { coroutineScope.launch { ConnectionRepository.sendMediaCommand("previous") } },
                                onPlayPauseClick = { coroutineScope.launch { ConnectionRepository.sendMediaCommand("playpause") } },
                                onNextClick = { coroutineScope.launch { ConnectionRepository.sendMediaCommand("next") } },
                                progress = mediaProgress,
                                elapsedTime = mediaElapsedTime,
                                totalTime = mediaTotalTime,
                                clipboardContent = clipboardText,
                                clipboardStateText = if (clipboardText.isNotEmpty()) appStrings.active else appStrings.inactive,
                                clipboardStateColor = if (clipboardText.isNotEmpty()) HyprColors.NeonGreen else HyprColors.InactiveGray,
                                onSendClipboardClick = {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    if (clipboardManager.hasPrimaryClip()) {
                                        val clipData = clipboardManager.primaryClip
                                        if (clipData != null && clipData.itemCount > 0) {
                                            val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                                            if (clipText.isNotEmpty()) {
                                                coroutineScope.launch {
                                                    try {
                                                        ConnectionRepository.sendClipboardText(clipText)
                                                        Toast.makeText(context, appStrings.toastClipSent, Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "${appStrings.errorTag}: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onReceiveClipboardClick = {
                                    val text = ConnectionRepository.lastReceivedClipboardText
                                    if (text.isNotEmpty()) {
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("HyprLink", text))
                                        Toast.makeText(context, appStrings.toastCopiedToAndroid, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSendClipboardImageClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                activeWorkstationPcMac = activeWorkstation?.pcMac,
                                isWakingPc = isWakingPc,
                                wakingCountdownSeconds = wakingSecondsRemaining,
                                onWakePcClick = {
                                    val ws = activeWorkstation
                                    val mac = ws?.pcMac
                                    if (ws != null && !mac.isNullOrBlank()) {
                                        wakeJob?.cancel()
                                        isWakingPc = true
                                        wakingSecondsRemaining = 60
                                        wakeJob = coroutineScope.launch {
                                            try {
                                                ConnectionRepository.appendLog("[WOL] A iniciar Wake-on-LAN para ${ws.deviceName} ($mac)...")
                                                val sent = WakeOnLan.sendMagicPacket(ws.host, mac)
                                                if (sent) {
                                                    Toast.makeText(context, appStrings.toastWolSent, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, appStrings.toastWolError, Toast.LENGTH_SHORT).show()
                                                }

                                                var elapsed = 0
                                                while (isActive && elapsed < 60 && connStatus != ConnectionStatus.CONNECTED) {
                                                    val intent = Intent(context, HyprLinkConnectionService::class.java).apply {
                                                        action = "ACTION_RECONNECT"
                                                    }
                                                    ContextCompat.startForegroundService(context, intent)

                                                    for (sec in 1..5) {
                                                        if (!isActive || connStatus == ConnectionStatus.CONNECTED) break
                                                        delay(1000)
                                                        elapsed++
                                                        wakingSecondsRemaining = (60 - elapsed).coerceAtLeast(0)
                                                    }
                                                }

                                                if (connStatus != ConnectionStatus.CONNECTED) {
                                                    ConnectionRepository.appendLog("[WOL] Timeout ao esperar pelo PC acordar.")
                                                    Toast.makeText(context, appStrings.toastWolTimeout, Toast.LENGTH_LONG).show()
                                                }
                                            } finally {
                                                isWakingPc = false
                                                wakingSecondsRemaining = 0
                                            }
                                        }
                                    }
                                },
                                onCancelWakePcClick = {
                                    wakeJob?.cancel()
                                    wakeJob = null
                                    isWakingPc = false
                                    wakingSecondsRemaining = 0
                                    ConnectionRepository.appendLog("[WOL] Utilizador cancelou a espera por WoL.")
                                },
                                onSendFilesClick = { showShareChoiceDialog = true },
                                onWebcamClick = {
                                    if (isWebcamStreaming) {
                                        WebcamStreamer.stop()
                                        isWebcamStarting = false
                                    } else if (!isWebcamStarting) {
                                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            startWebcamRequest()
                                        } else {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    }
                                },
                                webcamActive = isWebcamStreaming,
                                isWebcamStarting = isWebcamStarting,
                                isMicOn = isMicOn,
                                onMicToggleClick = {
                                    if (isMicOn) {
                                        WebcamStreamer.stopMic()
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            WebcamStreamer.startMic(context)
                                        } else {
                                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                controlService = if (currentLinkState == com.example.ui.theme.LinkState.CONNECTED) com.example.ui.theme.ServiceState.ACTIVE else com.example.ui.theme.ServiceState.INACTIVE,
                                webcamService = if (isWebcamStreaming) com.example.ui.theme.ServiceState.ACTIVE else com.example.ui.theme.ServiceState.INACTIVE,
                                micBridgeService = if (isMicOn) com.example.ui.theme.ServiceState.ACTIVE else com.example.ui.theme.ServiceState.INACTIVE,
                                notifsService = com.example.ui.theme.ServiceState.ACTIVE,
                                isAudioStreaming = isAudioStreamingPhone,
                                metricsProvider = {
                                    val status = pcStatus
                                    val usedGB = status?.ramUsedBytes?.let { it / 1_073_741_824f }
                                    val totalGB = status?.ramTotalBytes?.let { it / 1_073_741_824f }
                                    val freeDiskGB = status?.diskFreeBytes?.let { it / 1_073_741_824f }
                                    val totalDiskGB = status?.diskTotalBytes?.let { it / 1_073_741_824f }
                                    com.example.ui.PcConnectionMetrics(
                                        cpuUsage = status?.cpuPct,
                                        ramUsedGB = usedGB,
                                        ramTotalGB = totalGB,
                                        tempCelsius = status?.cpuTempC,
                                        batteryPercent = batteryLevel,
                                        pingMs = status?.rttMs,
                                        isConnected = currentLinkState == com.example.ui.theme.LinkState.CONNECTED,
                                        isAudioStreaming = isAudioStreamingPhone,
                                        hostname = status?.hostname,
                                        uptimeSeconds = status?.uptimeSeconds,
                                        diskFreeGB = freeDiskGB,
                                        diskTotalGB = totalDiskGB,
                                        gpuUsage = status?.gpuPct
                                    )
                                },
                                cpuUsage = pcStatus?.cpuPct,
                                ramUsageGB = pcStatus?.ramUsedBytes?.let { it / 1_073_741_824f },
                                ramTotalGB = pcStatus?.ramTotalBytes?.let { it / 1_073_741_824f },
                                tempCelsius = pcStatus?.cpuTempC,
                                diskFreeGB = pcStatus?.diskFreeBytes?.let { it / 1_073_741_824f },
                                diskTotalGB = pcStatus?.diskTotalBytes?.let { it / 1_073_741_824f },
                                gpuUsage = pcStatus?.gpuPct,
                                batteryPercent = batteryLevel,
                                onDetailClick = { currentTab = "desktop" },
                                terminalLogs = repositoryLogs,
                                onOpenFullJournal = { showFullJournal = true },
                                workstationHostPort = if (activeWorkstation != null) "${activeWorkstation.host} : ${activeWorkstation.port}" else "",
                                isConnecting = (currentLinkState == com.example.ui.theme.LinkState.NEGOTIATING) || isManuallyConnecting,
                                hasWorkstation = activeWorkstation != null && !workstations.isNullOrEmpty(),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                        "audio" -> {
                            val artworkBitmap by ConnectionRepository.currentArtworkBitmap.collectAsStateWithLifecycle()
                            com.example.ui.AudioScreen(
                                isConnected = connStatus == ConnectionStatus.CONNECTED,
                                onBack = { currentTab = "dashboard" },
                                mediaAppSource = currentMediaAppSource,
                                mediaTitle = currentMediaTitle,
                                mediaArtist = currentMediaArtist,
                                artworkBitmap = artworkBitmap,
                                elapsedTime = mediaElapsedTime,
                                totalTime = mediaTotalTime,
                                isPlaying = isMediaPlaying,
                                onPrevClick = { coroutineScope.launch { ConnectionRepository.sendMediaCommand("previous") } },
                                onPlayPauseClick = { coroutineScope.launch { ConnectionRepository.sendMediaCommand("playpause") } },
                                onNextClick = { coroutineScope.launch { ConnectionRepository.sendMediaCommand("next") } },
                                isListenOnPhone = isAudioStreamingPhone,
                                onToggleListenOnPhone = { enable ->
                                    if (enable) {
                                        coroutineScope.launch {
                                            val tapId = ConnectionRepository.startAudioTap()
                                            if (tapId == null) {
                                                Toast.makeText(context, appStrings.toastAudioStreamingError, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            ConnectionRepository.stopAudioTap()
                                            AudioStreamPlayer.stop()
                                        }
                                    }
                                },
                                sinks = audioSinks,
                                onSelectDefaultSink = { sinkName ->
                                    coroutineScope.launch { ConnectionRepository.setAudioDefaultSink(sinkName) }
                                },
                                onSinkVolumeChange = { sinkId, vol ->
                                    audioSinks = audioSinks.map { if (it.id == sinkId) it.copy(volume = vol) else it }
                                    coroutineScope.launch { ConnectionRepository.setAudioVolume("sink", sinkId, vol) }
                                },
                                onSinkMuteToggle = { sinkId, mute ->
                                    audioSinks = audioSinks.map { if (it.id == sinkId) it.copy(isMuted = mute) else it }
                                    coroutineScope.launch { ConnectionRepository.setAudioMute("sink", sinkId, mute) }
                                },
                                apps = audioApps,
                                onAppVolumeChange = { appId, vol ->
                                    audioApps = audioApps.map { if (it.id == appId) it.copy(volume = vol) else it }
                                    coroutineScope.launch { ConnectionRepository.setAudioVolume("app", appId, vol) }
                                },
                                onAppMuteToggle = { appId, mute ->
                                    audioApps = audioApps.map { if (it.id == appId) it.copy(isMuted = mute) else it }
                                    coroutineScope.launch { ConnectionRepository.setAudioMute("app", appId, mute) }
                                },
                                isMicOn = isMicOn,
                                onToggleMic = {
                                    if (isMicOn) {
                                        WebcamStreamer.stopMic()
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            WebcamStreamer.startMic(context)
                                        } else {
                                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "touchpad" -> {
                            com.example.ui.TouchpadScreen(
                                isConnected = connStatus == ConnectionStatus.CONNECTED,
                                deviceName = deviceName,
                                onMouseMove = { dx, dy ->
                                    coroutineScope.launch { ConnectionRepository.sendInputMove((dx * 1.5f).toInt(), (dy * 1.5f).toInt()) }
                                },
                                onMouseClick = { btn ->
                                    val btnStr = when (btn) {
                                        2 -> "middle"
                                        3 -> "right"
                                        else -> "left"
                                    }
                                    coroutineScope.launch { ConnectionRepository.sendInputClick(btnStr) }
                                },
                                onScroll = { dx, dy ->
                                    coroutineScope.launch { ConnectionRepository.sendInputScroll(dx.toInt(), dy.toInt()) }
                                },
                                onSendKey = { key ->
                                    coroutineScope.launch { ConnectionRepository.sendInputKey(key) }
                                },
                                onSendText = { text ->
                                    coroutineScope.launch { ConnectionRepository.sendInputType(text) }
                                },
                                onToggleKeyboardDialog = {
                                    // toggle software keyboard
                                },
                                onMouseButton = { button, down ->
                                    coroutineScope.launch { ConnectionRepository.sendMouseButton(button, down) }
                                },
                                onGesture = { name, dir ->
                                    val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
                                    if (prefs.getBoolean("gestures_enabled", true)) {
                                        val enabledForType = when (name) {
                                            "swipe_left_3", "swipe_right_3" -> prefs.getBoolean("gesture_swipe_3_enabled", true)
                                            "volume" -> prefs.getBoolean("gesture_volume_enabled", true)
                                            "rotate_landscape" -> prefs.getBoolean("gesture_rotate_landscape_enabled", true)
                                            "double_tap_back" -> prefs.getBoolean("gesture_double_tap_back_enabled", false)
                                            else -> false
                                        }
                                        if (enabledForType) {
                                            coroutineScope.launch {
                                                ConnectionRepository.sendGesture(name, dir)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "desktop" -> {
                            com.example.ui.MissionControlScreen(
                                workspaces = mcWorkspaces,
                                windows = mcWindows,
                                isConnected = isConnected,
                                isLoading = isMcLoading,
                                customShortcutCmd = customShortcutCmd,
                                onSelectWorkspace = { wsId ->
                                    executeCommandAndRefresh("workspace $wsId")
                                },
                                onLaunchApp = { appName ->
                                    executeCommandAndRefresh(appName)
                                },
                                onCloseWindow = { addr ->
                                    executeCommandAndRefresh("closewindow address:$addr")
                                },
                                onFocusWindow = { addr ->
                                    executeCommandAndRefresh("focuswindow address:$addr")
                                },
                                onRefresh = {
                                    refreshHypr()
                                },
                                onSuspendRequest = {
                                    showSuspendDialog = true
                                },
                                onPcAction = { actionName ->
                                    executePcAction(actionName)
                                },
                                pcShortcuts = pcShortcuts,
                                runningShortcutId = runningShortcutId,
                                onRunShortcut = { shortcutId ->
                                    executeShortcut(shortcutId)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "transfers" -> {
                            com.example.ui.TransfersScreen(
                                onBack = { currentTab = "dashboard" },
                                activeTransfers = activeTransfers,
                                completedTransfers = completedTransfers,
                                errorTransfers = errorTransfers,
                                onSendFileClick = { filePickerLauncher.launch("*/*") },
                                onRetryTransfer = { id ->
                                    filePickerLauncher.launch("*/*")
                                },
                                onDeleteErrorTransfer = { id ->
                                    ConnectionRepository.clearTransfers()
                                },
                                onClearHistory = {
                                    ConnectionRepository.clearPersistentHistory(context)
                                    ConnectionRepository.clearTransfers()
                                },
                                onOpenTransfer = { item ->
                                    val uri = item.uri
                                    if (uri != null) {
                                        try {
                                            val mime = context.contentResolver.getType(uri) ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, mime)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            android.widget.Toast.makeText(context, appStrings.toastNoAppToOpenFile, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "notifications" -> {
                            com.example.ui.SettingsScreen(
                                onBack = { currentTab = "dashboard" },
                                activeDeviceName = deviceName,
                                activeWorkstation = activeWorkstation,
                                isConnected = isConnected,
                                onOpenWorkstationSelector = { showWorkstationSelectorDialog = true },
                                onOpenNewPairDialog = { showNewPairDialog = true },
                                onRegenerateIdentity = { showRegenerateConfirmDialog = true },
                                customShortcutCmd = customShortcutCmd,
                                onCustomShortcutCmdChange = { customShortcutCmd = it },
                                onOpenPermissionsDialog = { showInitialPermissionsDialog = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

            if (showSuspendDialog) {
                Dialog(onDismissRequest = { showSuspendDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Hypr.Ink0, RectangleShape)
                            .border(1.dp, Hypr.LineStrong, RectangleShape)
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Kicker(appStrings.confirm, color = Hypr.Hot, strong = true)
                                Kicker(
                                    text = appStrings.btnClose,
                                    color = Hypr.Sub,
                                    modifier = Modifier.clickable { showSuspendDialog = false }
                                )
                            }
                            Rule(color = Hypr.Paper, thickness = 2.dp)
                            Headline(appStrings.suspendPcDialogTitle, size = 26.sp)
                            Deck(appStrings.suspendPcDialogDeck, small = true)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HyprButton(
                                    label = appStrings.cancel,
                                    onClick = { showSuspendDialog = false },
                                    modifier = Modifier.weight(1f),
                                    kind = HyprButtonKind.Ghost
                                )
                                HyprButton(
                                    label = appStrings.suspendAction,
                                    onClick = {
                                        showSuspendDialog = false
                                        coroutineScope.launch {
                                            try {
                                                val res = ConnectionRepository.sendPcAction("suspend")
                                                if (res.ok) {
                                                    Toast.makeText(context, appStrings.actionDone, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    if (res.error?.contains("unknown", ignoreCase = true) == true) {
                                                        val fbOk = ConnectionRepository.dispatchHyprCommand("exec systemctl suspend")
                                                        if (fbOk) {
                                                            Toast.makeText(context, appStrings.actionDone, Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "${appStrings.actionFailed}: ${res.error ?: appStrings.toastSuspendPcFailed}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "${appStrings.actionFailed}: ${res.error ?: appStrings.toastSuspendPcFailed}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                try {
                                                    val fbOk = ConnectionRepository.dispatchHyprCommand("exec systemctl suspend")
                                                    if (fbOk) {
                                                        Toast.makeText(context, appStrings.actionDone, Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "${appStrings.actionFailed}: ${appStrings.toastSuspendPcFailed}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e2: Exception) {
                                                    Toast.makeText(context, "${appStrings.actionFailed}: ${e.message ?: appStrings.toastSuspendPcFailed}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    kind = HyprButtonKind.Danger
                                )
                            }
                        }
                    }
                }
            }
    }

        // --- DIALOGS REGISTRATION ---

    if (isWebcamStreaming) {
        val currentView = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(Unit) {
            currentView.keepScreenOn = true
            onDispose {
                currentView.keepScreenOn = false
            }
        }

        androidx.activity.compose.BackHandler(enabled = true) {
            // Ignora o gesto/botão de voltar para não fechar acidentalmente a transmissão.
            // Para encerrar, o utilizador deve premir o botão PARAR TRANSMISSÃO.
        }

        com.example.ui.WebcamFullscreenDialog(
            deviceName = deviceName,
            lensLabel = lensLabel,
            rotationDeg = rotationDeg,
            mirrored = mirrored,
            codecPreference = codecPreference,
            activeCodec = activeCodec,
            isMicOn = isMicOn,
            isTorchOn = isTorchOn,
            zoomRatio = zoomRatio,
            currentWidth = currentWidth,
            currentHeight = currentHeight,
            currentFps = currentFps,
            micPermissionLauncher = micPermissionLauncher,
            modifier = Modifier.fillMaxSize()
        )
    }

    var workstationToDelete by remember { mutableStateOf<PairedWorkstation?>(null) }

    if (workstationToDelete != null) {
        Dialog(onDismissRequest = { workstationToDelete = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.confirm, color = Hypr.Hot, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { workstationToDelete = null }
                        )
                    }
                    Rule(color = Hypr.Paper, thickness = 2.dp)
                    Headline(appStrings.forgetStationDialogTitle, size = 26.sp)
                    Deck(String.format(java.util.Locale.getDefault(), appStrings.confirmDeleteStation, workstationToDelete?.deviceName ?: ""), small = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HyprButton(
                            label = appStrings.cancel,
                            onClick = { workstationToDelete = null },
                            modifier = Modifier.weight(1f),
                            kind = HyprButtonKind.Ghost
                        )
                        HyprButton(
                            label = appStrings.btnForget,
                            onClick = {
                                val target = workstationToDelete
                                if (target != null) {
                                    coroutineScope.launch {
                                        repository.delete(target)
                                        if (activeWorkstationId == target.id) {
                                            activeWorkstationId = ""
                                        }
                                        workstationToDelete = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            kind = HyprButtonKind.Danger
                        )
                    }
                }
            }
        }
    }

    if (showWorkstationSelectorDialog) {
        Dialog(onDismissRequest = { showWorkstationSelectorDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.workstationsTitle, color = Hypr.Acid, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { showWorkstationSelectorDialog = false }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)

                    Headline(appStrings.dialogChangePc, size = 30.sp)

                    if (workstations.isNullOrEmpty()) {
                        Deck(appStrings.noWorkstationConfigured, small = true)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            workstations?.forEach { ws ->
                                val isSelected = ws.id == activeWorkstationId
                                val shortFp = if (ws.fingerprint.length > 12) ws.fingerprint.take(12) + "…" else ws.fingerprint
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Square(
                                                color = if (isSelected) Hypr.Acid else Hypr.Faint,
                                                size = 8.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ws.deviceName,
                                                    color = Hypr.Paper,
                                                    style = TextStyle(
                                                        fontFamily = Inter,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp
                                                    )
                                                )
                                                Gap(3.dp)
                                                Mono(
                                                    text = "${ws.host}:${ws.port} · $shortFp",
                                                    color = Hypr.Muted,
                                                    size = 11.sp
                                                )
                                                Gap(4.dp)
                                                Kicker(
                                                    text = appStrings.btnForget,
                                                    color = Hypr.Hot,
                                                    modifier = Modifier.clickable {
                                                        workstationToDelete = ws
                                                    }
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        if (isSelected) {
                                            Tag(appStrings.tagActive, background = Hypr.Acid, color = Hypr.Void)
                                        } else {
                                            HyprButton(
                                                label = appStrings.btnUse,
                                                onClick = {
                                                    activeWorkstationId = ws.id
                                                    showWorkstationSelectorDialog = false
                                                },
                                                kind = HyprButtonKind.Ghost
                                            )
                                        }
                                    }
                                    Rule(color = Hypr.Line)
                                }
                            }
                        }
                    }

                    HyprButton(
                        label = appStrings.btnPairNew,
                        onClick = {
                            showWorkstationSelectorDialog = false
                            showNewPairDialog = true
                        },
                        kind = HyprButtonKind.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showInitialPermissionsDialog) {
        com.example.ui.InitialPermissionsDialog(
            onDismiss = {
                showInitialPermissionsDialog = false
                sharedPrefs.edit().putBoolean("has_seen_initial_permissions_dialog", true).apply()
                val hasOfferedFirstPairing = sharedPrefs.getBoolean("has_offered_first_pairing", false)
                if (!hasOfferedFirstPairing && workstations?.isEmpty() == true) {
                    sharedPrefs.edit().putBoolean("has_offered_first_pairing", true).apply()
                    showNewPairDialog = true
                }
            }
        )
    }

    if (showBatteryExemptionDialog) {
        Dialog(
            onDismissRequest = {
                showBatteryExemptionDialog = false
                hasRequestedBatteryExemptionThisSession = true
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.permissionsTitle, color = Hypr.Acid, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable {
                                showBatteryExemptionDialog = false
                                hasRequestedBatteryExemptionThisSession = true
                            }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)

                    Headline(appStrings.dialogBattery, size = 30.sp)

                    Deck(appStrings.dialogBatteryDesc, small = true)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = appStrings.dialogBattery,
                                color = Hypr.Paper,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            )
                            if (isBatteryOptIgnored) {
                                TagOutline(appStrings.tagActiveState, color = Hypr.Acid)
                            } else {
                                TagOutline(appStrings.tagPending, color = Hypr.LinkWait)
                            }
                        }
                        Gap(5.dp)
                        Mono(
                            text = appStrings.dialogBatteryReason,
                            color = Hypr.Muted,
                            size = 11.sp,
                            maxLines = 3
                        )
                        if (!isBatteryOptIgnored) {
                            Gap(10.dp)
                            HyprButton(
                                label = appStrings.btnGrant,
                                onClick = {
                                    showBatteryExemptionDialog = false
                                    hasRequestedBatteryExemptionThisSession = true
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${appStrings.toastSettingsError}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                kind = HyprButtonKind.Primary
                            )
                        }
                        Gap(13.dp)
                        Rule(color = Hypr.Line)
                    }

                    Gap(16.dp)

                    HyprButton(
                        label = appStrings.btnLater,
                        onClick = {
                            showBatteryExemptionDialog = false
                            hasRequestedBatteryExemptionThisSession = true
                        },
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDndDialog) {
        Dialog(
            onDismissRequest = {
                showDndDialog = false
                hasRequestedDndThisSession = true
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.permissionsTitle, color = Hypr.Acid, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable {
                                showDndDialog = false
                                hasRequestedDndThisSession = true
                            }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)

                    Headline(appStrings.dialogDnd, size = 30.sp)

                    Deck(appStrings.dialogDndDesc, small = true)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = appStrings.dialogDnd,
                                color = Hypr.Paper,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            )
                            if (isDndGranted) {
                                TagOutline(appStrings.tagActiveState, color = Hypr.Acid)
                            } else {
                                TagOutline(appStrings.tagPending, color = Hypr.LinkWait)
                            }
                        }
                        Gap(5.dp)
                        Mono(
                            text = appStrings.dialogDndReason,
                            color = Hypr.Muted,
                            size = 11.sp,
                            maxLines = 3
                        )
                        if (!isDndGranted) {
                            Gap(10.dp)
                            HyprButton(
                                label = appStrings.btnGrant,
                                onClick = {
                                    showDndDialog = false
                                    hasRequestedDndThisSession = true
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${appStrings.toastSettingsError}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                kind = HyprButtonKind.Primary
                            )
                        }
                        Gap(13.dp)
                        Rule(color = Hypr.Line)
                    }

                    Gap(16.dp)

                    HyprButton(
                        label = appStrings.btnLater,
                        onClick = {
                            showDndDialog = false
                            hasRequestedDndThisSession = true
                        },
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 1. Share URL dialog
    if (showUrlShareDialog) {
        var urlInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showUrlShareDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.shareTitle, color = Hypr.Acid, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { showUrlShareDialog = false }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)

                    Headline(appStrings.shareLinkTitle, size = 30.sp)

                    Deck(appStrings.customShortcutCmdDesc, small = true)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Kicker(appStrings.urlOrText)
                        BasicTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 12.sp,
                                color = Hypr.Paper
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Hypr.Acid),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Hypr.Ink1, RectangleShape)
                                .border(1.dp, Hypr.Muted, RectangleShape)
                                .padding(12.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (urlInput.isEmpty()) {
                                        Mono("https://exemplo.com", color = Hypr.Muted, size = 12.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HyprButton(
                            label = appStrings.cancel,
                            onClick = { showUrlShareDialog = false },
                            kind = HyprButtonKind.Ghost,
                            modifier = Modifier.weight(1f)
                        )
                        HyprButton(
                            label = appStrings.sendToPcAction,
                            onClick = {
                                val trimmed = urlInput.trim()
                                if (trimmed.isNotBlank()) {
                                    if (ShareUrlValidator.isValidUrl(trimmed)) {
                                        coroutineScope.launch {
                                            ConnectionRepository.sendUrl(trimmed)
                                            Toast.makeText(context, appStrings.toastUrlShared, Toast.LENGTH_SHORT).show()
                                        }
                                        showUrlShareDialog = false
                                    } else {
                                        Toast.makeText(context, appStrings.urlOnlyHttpHttps, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            kind = HyprButtonKind.Primary,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }

    // 1b. Share Choice Dialog
    if (showShareChoiceDialog) {
        Dialog(onDismissRequest = { showShareChoiceDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.shareTitle, color = Hypr.Acid, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { showShareChoiceDialog = false }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)

                    Headline(appStrings.shareWithPcTitle, size = 30.sp)

                    Deck(appStrings.shareWithPcDeck, small = true)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HyprButton(
                            label = appStrings.fileAction,
                            onClick = {
                                showShareChoiceDialog = false
                                filePickerLauncher.launch("*/*")
                            },
                            kind = HyprButtonKind.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HyprButton(
                            label = appStrings.linkAction,
                            onClick = {
                                showShareChoiceDialog = false
                                showUrlShareDialog = true
                            },
                            kind = HyprButtonKind.Ghost,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // 1c. Media Controls Dialog
    if (showMediaControlsDialog) {
        Dialog(onDismissRequest = { showMediaControlsDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = appStrings.mediaControlTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = Hypr.Paper
                    )

                    if (mediaState != null) {
                        val ms = mediaState!!
                        val isPlaying = ms.status.lowercase() == "playing"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = ms.title ?: appStrings.untitled,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Hypr.Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ms.artist ?: appStrings.unknownArtist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Hypr.Sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ms.player,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono,
                                color = Hypr.Acid
                            )
                        }
                    } else {
                        Text(
                            text = appStrings.noActivePlayerDetected,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Hypr.Sub,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            coroutineScope.launch { ConnectionRepository.sendMediaCommand("previous") }
                        }) {
                            Icon(
                                imageVector = HyprIcons.SkipPrevious,
                                contentDescription = appStrings.btnPrevious,
                                tint = Hypr.Paper,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Hypr.Acid, RectangleShape)
                                .border(1.dp, Hypr.LineStrong, RectangleShape)
                                .clickable {
                                    coroutineScope.launch { ConnectionRepository.sendMediaCommand("playpause") }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (mediaState?.status?.lowercase() == "playing") HyprIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = "${appStrings.btnPlay} / ${appStrings.btnPause}",
                                tint = Hypr.Void,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = {
                            coroutineScope.launch { ConnectionRepository.sendMediaCommand("next") }
                        }) {
                            Icon(
                                imageVector = HyprIcons.SkipNext,
                                contentDescription = appStrings.btnNext,
                                tint = Hypr.Paper,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    HyprButton(
                        label = appStrings.closeDialog,
                        onClick = { showMediaControlsDialog = false },
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 2. Pair Workstation Dialog
    if (showNewPairDialog) {
        var scanTabSelected by remember { mutableStateOf(true) }
        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                hasCameraPermission = granted
            }
        )

        val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US) }
        var pairingLogs by remember { mutableStateOf(listOf("${timeFormat.format(java.util.Date())} ${appStrings.readingQrCode}")) }
        var isPairingInProgress by remember { mutableStateOf(false) }

        var inputName by remember { mutableStateOf(if (deviceName.isNotBlank() && deviceName != appStrings.noStation) deviceName else "Workstation-Alpha") }
        var inputHost by remember { mutableStateOf(if (deviceHost.isNotBlank() && deviceHost != "0.0.0.0") deviceHost else "") }
        var inputPort by remember { mutableStateOf(if (devicePort.isNotBlank() && devicePort != "----") devicePort else ConnectionUtils.HYPRLINK_SERVICE_PORT.toString()) }
        var inputFingerprint by remember { mutableStateOf(if (deviceFingerprint.isNotBlank() && deviceFingerprint != appStrings.noFingerprintConfigured) deviceFingerprint else "") }

        Dialog(
            onDismissRequest = { showNewPairDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.workstationsTitle.uppercase(), color = Hypr.Acid, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { showNewPairDialog = false }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)

                    Headline(appStrings.connectToPcTitle, size = 30.sp)

                    Deck(appStrings.connectToPcDeck, small = true)

                    // Tab selector in HyprChip: QR · MANUAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HyprChip(
                            label = "QR",
                            selected = scanTabSelected,
                            onClick = { scanTabSelected = true }
                        )
                        HyprChip(
                            label = appStrings.manualAction,
                            selected = !scanTabSelected,
                            onClick = { scanTabSelected = false }
                        )
                    }

                    if (scanTabSelected) {
                        // Scan QR Code Tab - preenche dinamicamente o espaço restante
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!hasCameraPermission) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Hypr.Ink0, RectangleShape)
                                        .border(1.dp, Hypr.Line, RectangleShape)
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Body(
                                            text = appStrings.permCameraDesc,
                                            color = Hypr.Sub
                                        )
                                        HyprButton(
                                            label = appStrings.btnAllowCamera,
                                            onClick = { launcher.launch(Manifest.permission.CAMERA) },
                                            kind = HyprButtonKind.Primary
                                        )
                                    }
                                }
                            } else {
                                // Camera preview for QR scan with 4 acid corners (2 dp) and middle line (60% alpha)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Hypr.Ink0, RectangleShape)
                                        .border(1.dp, Hypr.Line, RectangleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CameraPreview(
                                        onQrCodeScanned = { qrText ->
                                            if (!isPairingInProgress) {
                                                isPairingInProgress = true
                                                val now = timeFormat.format(java.util.Date())
                                                pairingLogs = listOf("$now ${appStrings.readingQrCode}")
                                                coroutineScope.launch {
                                                    val logsList = mutableListOf<String>()
                                                    fun logPair(msg: String) {
                                                        val t = timeFormat.format(java.util.Date())
                                                        logsList.add("$t $msg")
                                                        pairingLogs = logsList.toList()
                                                    }

                                                    try {
                                                        val parsed = parsePairingQr(qrText)
                                                        if (parsed == null) {
                                                            logPair("[ERROR] ${appStrings.qrInvalidFormat}")
                                                            isPairingInProgress = false
                                                            return@launch
                                                        }
                                                        val host = parsed.host
                                                        val port = parsed.port.toString()
                                                        val fingerprint = parsed.fingerprint
                                                        val token = parsed.pairingToken
                                                        val shortFp = if (fingerprint.length > 12) fingerprint.take(4) + "…" + fingerprint.takeLast(4) else fingerprint

                                                        logPair("[SUCCESS] $host:$port · $shortFp")
                                                        logPair("·· handshake mTLS")
                                                        val handshakeResult = withContext(Dispatchers.IO) {
                                                            executePairingHandshake(
                                                                context = context,
                                                                localDeviceName = DeviceIdentity.getDeviceName(context),
                                                                data = parsed,
                                                                onLog = { msg -> logPair(msg) }
                                                            )
                                                        }

                                                        if (handshakeResult != null) {
                                                            val wkName = handshakeResult.deviceName
                                                            val pcMac = handshakeResult.pcMac
                                                            logPair("[SUCCESS] Workstation '$wkName' emparelhada com sucesso!${if (pcMac != null) " (MAC: $pcMac)" else ""}")
                                                            ConnectionRepository.appendLog(appStrings.logStationPaired)
                                                            val newWs = PairedWorkstation(
                                                                id = java.util.UUID.randomUUID().toString(),
                                                                deviceName = wkName,
                                                                host = parsed.host,
                                                                port = ConnectionUtils.HYPRLINK_SERVICE_PORT.toString(),
                                                                fingerprint = parsed.fingerprint,
                                                                pairingTokenHex = parsed.pairingToken,
                                                                pcMac = pcMac
                                                            )
                                                            repository.insert(newWs)
                                                            activeWorkstationId = newWs.id
                                                            delay(1800)
                                                            showNewPairDialog = false
                                                        } else {
                                                            logPair("[ERROR] ${appStrings.handshakeRejected}")
                                                        }
                                                    } catch (e: Exception) {
                                                        logPair("[ERROR] ${appStrings.errorTag}: ${e.message}")
                                                    } finally {
                                                        isPairingInProgress = false
                                                    }
                                                }
                                            }
                                        }
                                    )

                                    // Scanning reticle: 4 corners (2 dp acid) and 60% acid horizontal center line
                                    Canvas(modifier = Modifier.size(200.dp)) {
                                        val strokeW = 2.dp.toPx()
                                        val armLen = 26.dp.toPx()
                                        val acidColor = Hypr.Acid

                                        // Top-left corner
                                        drawLine(acidColor, Offset(0f, 0f), Offset(armLen, 0f), strokeW)
                                        drawLine(acidColor, Offset(0f, 0f), Offset(0f, armLen), strokeW)

                                        // Top-right corner
                                        drawLine(acidColor, Offset(size.width, 0f), Offset(size.width - armLen, 0f), strokeW)
                                        drawLine(acidColor, Offset(size.width, 0f), Offset(size.width, armLen), strokeW)

                                        // Bottom-left corner
                                        drawLine(acidColor, Offset(0f, size.height), Offset(armLen, size.height), strokeW)
                                        drawLine(acidColor, Offset(0f, size.height), Offset(0f, size.height - armLen), strokeW)

                                        // Bottom-right corner
                                        drawLine(acidColor, Offset(size.width, size.height), Offset(size.width - armLen, size.height), strokeW)
                                        drawLine(acidColor, Offset(size.width, size.height), Offset(size.width, size.height - armLen), strokeW)

                                        // Center scan line at 60% opacity
                                        drawLine(
                                            acidColor.copy(alpha = 0.6f),
                                            Offset(0f, size.height / 2f),
                                            Offset(size.width, size.height / 2f),
                                            1.dp.toPx()
                                        )
                                    }
                                }
                            }

                            // Terminal de emparelhamento
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Kicker(appStrings.pairingTerminalTitle, strong = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Kicker(
                                        text = appStrings.copyAction,
                                        color = Hypr.Sub,
                                        modifier = Modifier.clickable {
                                            val fullText = pairingLogs.joinToString("\n")
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("HyprLink Pairing Logs", fullText))
                                            Toast.makeText(context, appStrings.toastLogsCopied, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Kicker(
                                        text = appStrings.shareTitle.uppercase(),
                                        color = Hypr.Sub,
                                        modifier = Modifier.clickable {
                                            val fullText = pairingLogs.joinToString("\n")
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TITLE, "hyprlink-pairing-logs.txt")
                                                putExtra(Intent.EXTRA_TEXT, fullText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, appStrings.shareTitle))
                                        }
                                    )
                                }
                            }

                            // Pairing log box: Panel with Mono 11.5 sp (time Hypr.Faint, OK acid, wait gold, error red)
                            Panel(
                                background = Hypr.Ink0,
                                border = Hypr.Line,
                                padding = PaddingValues(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                val scroll = rememberScrollState()
                                LaunchedEffect(pairingLogs.size) {
                                    scroll.animateScrollTo(scroll.maxValue)
                                }
                                SelectionContainer {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scroll),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        pairingLogs.forEach { log ->
                                            val timeMatch = Regex("""^(\d{2}:\d{2}:\d{2})\s*(.*)""").find(log.trim())
                                            val (timeStr, content) = if (timeMatch != null) {
                                                Pair(timeMatch.groupValues[1], timeMatch.groupValues[2])
                                            } else {
                                                Pair("", log.trim())
                                            }

                                            val isOk = content.contains("[SUCCESS]", true) || content.startsWith("OK", true)
                                            val isWait = content.contains("wait", true) || content.contains("··") || content.contains("handshake", true) || content.contains("espera", true)
                                            val isError = content.contains("[ERROR]", true) || content.contains("erro", true) || content.contains("fail", true)

                                            val cleanContent = content
                                                .replace("[INFO]", "")
                                                .replace("[SUCCESS]", "")
                                                .replace("[ERROR]", "")
                                                .trim()

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (timeStr.isNotEmpty()) {
                                                    Mono(text = timeStr, color = Hypr.Faint, size = 11.5.sp)
                                                }
                                                when {
                                                    isError -> {
                                                        Mono(text = appStrings.errorTag, color = Hypr.Hot, size = 11.5.sp, weight = FontWeight.SemiBold)
                                                        Mono(text = cleanContent.removePrefix("ERRO").trim(), color = Hypr.Hot, size = 11.5.sp, maxLines = 3)
                                                    }
                                                    isOk -> {
                                                        Mono(text = "OK", color = Hypr.Acid, size = 11.5.sp, weight = FontWeight.SemiBold)
                                                        Mono(text = cleanContent.removePrefix("OK").trim(), color = Hypr.Paper, size = 11.5.sp, maxLines = 3)
                                                    }
                                                    isWait -> {
                                                        Mono(text = "··", color = Hypr.LinkWait, size = 11.5.sp, weight = FontWeight.SemiBold)
                                                        Mono(text = cleanContent.removePrefix("··").trim(), color = Hypr.Paper, size = 11.5.sp, maxLines = 3)
                                                    }
                                                    else -> {
                                                        Mono(text = cleanContent, color = Hypr.Sub, size = 11.5.sp, maxLines = 3)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Manual Configuration Tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Kicker(appStrings.nameField)
                                BasicTextField(
                                    value = inputName,
                                    onValueChange = { inputName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = Hypr.Paper),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Hypr.Acid),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Hypr.Ink1, RectangleShape)
                                        .border(1.dp, Hypr.Muted, RectangleShape)
                                        .padding(12.dp)
                                        .testTag("dialog_workstation_name_input"),
                                    decorationBox = { inner ->
                                        Box {
                                            if (inputName.isEmpty()) Mono("Workstation-Alpha", color = Hypr.Muted, size = 12.sp)
                                             inner()
                                        }
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Kicker(appStrings.hostField)
                                BasicTextField(
                                    value = inputHost,
                                    onValueChange = { inputHost = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = Hypr.Paper),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Hypr.Acid),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Hypr.Ink1, RectangleShape)
                                        .border(1.dp, Hypr.Muted, RectangleShape)
                                        .padding(12.dp)
                                        .testTag("dialog_workstation_ip_input"),
                                    decorationBox = { inner ->
                                        Box {
                                            if (inputHost.isEmpty()) Mono("192.168.x.x / hostname", color = Hypr.Muted, size = 12.sp)
                                            inner()
                                        }
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Kicker(appStrings.portField)
                                BasicTextField(
                                    value = inputPort,
                                    onValueChange = { inputPort = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = Hypr.Paper),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Hypr.Acid),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Hypr.Ink1, RectangleShape)
                                        .border(1.dp, Hypr.Muted, RectangleShape)
                                        .padding(12.dp)
                                        .testTag("dialog_workstation_port_input"),
                                    decorationBox = { inner ->
                                        Box {
                                            if (inputPort.isEmpty()) Mono("7443", color = Hypr.Muted, size = 12.sp)
                                            inner()
                                        }
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Kicker(appStrings.sha256Field)
                                BasicTextField(
                                    value = inputFingerprint,
                                    onValueChange = { inputFingerprint = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = Hypr.Paper),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Hypr.Acid),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Hypr.Ink1, RectangleShape)
                                        .border(1.dp, Hypr.Muted, RectangleShape)
                                        .padding(12.dp)
                                        .testTag("dialog_workstation_fingerprint_input"),
                                    decorationBox = { inner ->
                                        Box {
                                            if (inputFingerprint.isEmpty()) Mono("a1b2c3d4...", color = Hypr.Muted, size = 12.sp)
                                            inner()
                                        }
                                    }
                                )
                            }

                            Gap(6.dp)

                            HyprButton(
                                label = appStrings.save.uppercase(),
                                onClick = {
                                    if (inputName.isNotBlank() && inputHost.isNotBlank() && inputPort.isNotBlank() && inputFingerprint.isNotBlank()) {
                                        coroutineScope.launch {
                                            val newWs = PairedWorkstation(
                                                id = java.util.UUID.randomUUID().toString(),
                                                deviceName = inputName.trim(),
                                                host = inputHost.trim(),
                                                port = inputPort.trim(),
                                                fingerprint = inputFingerprint.trim(),
                                                pairingTokenHex = null
                                            )
                                            repository.insert(newWs)
                                            activeWorkstationId = newWs.id
                                            showNewPairDialog = false
                                            Toast.makeText(context, appStrings.toastWorkstationSaved, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, appStrings.toastFillAllFields, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                kind = HyprButtonKind.Primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_save_workstation_button")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRegenerateConfirmDialog) {
        Dialog(onDismissRequest = { showRegenerateConfirmDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(appStrings.confirm, color = Hypr.Hot, strong = true)
                        Kicker(
                            text = appStrings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { showRegenerateConfirmDialog = false }
                        )
                    }
                    Rule(color = Hypr.Paper, thickness = 2.dp)
                    Headline(appStrings.recreateIdentityTitle, size = 26.sp)
                    Deck(appStrings.regenerateWarning, small = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HyprButton(
                            label = appStrings.cancel,
                            onClick = { showRegenerateConfirmDialog = false },
                            modifier = Modifier.weight(1f),
                            kind = HyprButtonKind.Ghost
                        )
                        HyprButton(
                            label = appStrings.recreateAction,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    DeviceIdentity.regenerateInstance(context)
                                    withContext(Dispatchers.Main) {
                                        showRegenerateConfirmDialog = false
                                        Toast.makeText(context, appStrings.toastIdentityRecreated, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            kind = HyprButtonKind.Danger
                        )
                    }
                }
            }
        }
    }
}

// Helper query extensions to read SAF metadata in helper functions
private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

private fun getFileSize(context: Context, uri: Uri): Long? {
    var result: Long? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) {
                    result = cursor.getLong(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    return result
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    try {
        val enabledPackages = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(pkgName)) {
            return true
        }
    } catch (_: Exception) {
    }
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}
