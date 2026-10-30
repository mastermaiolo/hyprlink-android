// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.example.R
import com.example.SupportDonateLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.AppLanguage
import com.example.BuildConfig
import com.example.ConnectionRepository
import com.example.DeviceIdentity
import com.example.HyprLinkConnectionService
import com.example.LanguageManager
import com.example.LocalAppStrings
import com.example.PairedWorkstation
import com.example.PhotoAccessLogic
import com.example.UsbTetherHelper
import com.example.PhotoAccessStatus
import com.example.ui.kit.*
import com.example.ui.theme.Anton
import com.example.ui.theme.Hypr
import com.example.ui.theme.HyprSize
import com.example.ui.theme.Inter
import com.example.ui.theme.PlexMono
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private fun formatHexFingerprint(hex: String?): String {
    if (hex.isNullOrBlank()) return "NÃO EMPARELHADO"
    val clean = hex.replace(":", "").replace(" ", "").replace("-", "").uppercase()
    val chunks = clean.chunked(4)
    val line1 = chunks.take(4).joinToString(" ")
    val line2 = chunks.drop(4).take(4).joinToString(" ")
    return if (line2.isNotBlank()) "$line1\n$line2" else line1
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    activeDeviceName: String,
    activeWorkstation: PairedWorkstation? = null,
    isConnected: Boolean = false,
    onOpenWorkstationSelector: () -> Unit = {},
    onOpenNewPairDialog: () -> Unit = {},
    onRegenerateIdentity: () -> Unit = {},
    customShortcutCmd: String = "exec kitty",
    onCustomShortcutCmdChange: (String) -> Unit = {},
    onOpenPermissionsDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang = LanguageManager.currentLanguage.value
    val coroutineScope = rememberCoroutineScope()

    val sharedPrefs = remember { context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE) }
    var isMirrorEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("mirror_notifications", true)) }
    var isPcPlayerEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(com.example.PcPlayerNotificationManager.PREF_SHOW_PC_PLAYER, true))
    }
    var isAutoSyncScreenshots by remember { mutableStateOf(sharedPrefs.getBoolean("auto_sync_screenshots", false)) }
    var isShareWifiSsid by remember { mutableStateOf(sharedPrefs.getBoolean("share_wifi_ssid", false)) }
    var isShareCellGen by remember { mutableStateOf(sharedPrefs.getBoolean("share_cell_gen", true)) }
    var isKeyboardLive by remember { mutableStateOf(sharedPrefs.getBoolean("keyboard_live_mode", true)) }

    var isGesturesMasterEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gestures_enabled", true)) }
    var isGestureSwipe3Enabled by remember { mutableStateOf(sharedPrefs.getBoolean("gesture_swipe_3_enabled", true)) }
    var isGestureVolumeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gesture_volume_enabled", true)) }
    var isGestureRotateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gesture_rotate_landscape_enabled", true)) }
    var isGestureBackTapEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gesture_double_tap_back_enabled", false)) }

    // Ping test state
    var isPinging by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var pingError by remember { mutableStateOf(false) }

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showUsbTetherDialog by remember { mutableStateOf(false) }
    var localDeviceName by remember { mutableStateOf(com.example.DeviceIdentity.getDeviceName(context)) }
    var isEditingDeviceName by remember { mutableStateOf(false) }
    var selectedLicenseName by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isAutoSyncScreenshots = isGranted
        sharedPrefs.edit().putBoolean("auto_sync_screenshots", isGranted).apply()
        if (isGranted) {
            Toast.makeText(context, strings.toastScreenshotsSynced, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, strings.toastScreenshotsPermNeeded, Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val isGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        isShareWifiSsid = isGranted
        sharedPrefs.edit().putBoolean("share_wifi_ssid", isGranted).apply()
        if (isGranted) {
            com.example.HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()
            Toast.makeText(context, strings.toastWifiSsidShared, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, strings.toastWifiSsidPermNeeded, Toast.LENGTH_SHORT).show()
        }
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isShareCellGen = isGranted
        sharedPrefs.edit().putBoolean("share_cell_gen", isGranted).apply()
        if (isGranted) {
            com.example.HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()
            Toast.makeText(context, strings.toastCellGenShared, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, strings.toastCellGenPermNeeded, Toast.LENGTH_SHORT).show()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasDndPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = isNotificationListenerEnabled(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                hasDndPermission = nm?.isNotificationPolicyAccessGranted ?: false

                val hasImgPerm = PhotoAccessLogic.canAutoSyncScreenshots(context)
                if (!hasImgPerm && isAutoSyncScreenshots) {
                    isAutoSyncScreenshots = false
                    sharedPrefs.edit().putBoolean("auto_sync_screenshots", false).apply()
                }

                val hasLocPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                 ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!hasLocPerm && isShareWifiSsid) {
                    isShareWifiSsid = false
                    sharedPrefs.edit().putBoolean("share_wifi_ssid", false).apply()
                }

                val hasPhonePerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                                   ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (!hasPhonePerm && isShareCellGen) {
                    isShareCellGen = false
                    sharedPrefs.edit().putBoolean("share_cell_gen", false).apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val deviceIdentityState = produceState<DeviceIdentity?>(initialValue = DeviceIdentity.peekInstance()) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                DeviceIdentity.getInstance(context)
            }
        }
    }
    val deviceIdentity = deviceIdentityState.value
    val localFingerprint = deviceIdentity?.fingerprintHex ?: "—"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyprSpace.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = strings.settingsTitle
        )

        // ────────────────────────────────────────── 01 Workstation ──
        Subhead(
            num = "01",
            title = strings.sectionNetworkWorkstation,
            trailing = if (activeWorkstation != null) strings.labelActive else null
        )

        Headline(
            text = (activeWorkstation?.deviceName ?: activeDeviceName).ifBlank { strings.noStation },
            size = 30.sp
        )

        val host = activeWorkstation?.host
        val port = activeWorkstation?.port
        val addressText = if (!host.isNullOrBlank() && port != null) "$host : $port" else if (!host.isNullOrBlank()) host else strings.notConfigured
        Kv(strings.labelAddress, addressText)
        Kv(strings.labelProtocol, "hyprlink/1 · QUIC · mTLS")

        val mac = activeWorkstation?.pcMac
        if (!mac.isNullOrBlank()) {
            Kv("MAC", mac)
        }

        val remoteFingerprint = activeWorkstation?.fingerprint
        Kv(strings.labelPcSha, formatHexFingerprint(remoteFingerprint))

        Gap(4.dp)

        // Linha TESTAR LIGAÇÃO
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HyprButton(
                label = if (isPinging) strings.labelTesting else strings.labelTestConnection,
                onClick = {
                    coroutineScope.launch {
                        isPinging = true
                        pingError = false
                        if (!isConnected) {
                            val intent = Intent(context, HyprLinkConnectionService::class.java).apply {
                                action = "ACTION_RECONNECT"
                            }
                            ContextCompat.startForegroundService(context, intent)
                            delay(1000)
                        }
                        val t0 = System.nanoTime()
                        val success = ConnectionRepository.sendPing()
                        val t1 = System.nanoTime()
                        isPinging = false
                        if (success) {
                            val ms = (t1 - t0) / 1_000_000.0
                            pingResult = "%.1f".format(ms).replace('.', ',')
                            pingError = false
                        } else {
                            pingResult = null
                            pingError = true
                            val intent = Intent(context, HyprLinkConnectionService::class.java).apply {
                                action = "ACTION_RECONNECT"
                            }
                            ContextCompat.startForegroundService(context, intent)
                        }
                    }
                },
                kind = HyprButtonKind.Ghost,
                enabled = activeWorkstation != null && !isPinging
            )

            if (pingResult != null) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pingResult!!,
                        color = Hypr.Paper,
                        style = TextStyle(fontFamily = Anton, fontSize = 22.sp)
                    )
                    Kicker(text = "MS · " + strings.labelActive, color = Hypr.Muted)
                }
            } else if (pingError) {
                Kicker(text = strings.labelNoResponse, color = Hypr.Hot, strong = true)
            }
        }

        Gap(4.dp)

        // Botões MUDAR ESTAÇÃO e EMPARELHAR NOVA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HyprButton(
                label = strings.btnChangeStation,
                onClick = onOpenWorkstationSelector,
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f)
            )
            HyprButton(
                label = strings.btnPairNewStation,
                onClick = onOpenNewPairDialog,
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f)
            )
        }

        Gap(6.dp)

        HyprButton(
            label = strings.btnUseUsbCable,
            onClick = {
                val hasSeen = sharedPrefs.getBoolean("has_seen_usb_tether_guide", false)
                if (hasSeen) {
                    UsbTetherHelper.openUsbTetherSettings(context)
                } else {
                    showUsbTetherDialog = true
                }
            },
            kind = HyprButtonKind.Ghost,
            modifier = Modifier.fillMaxWidth()
        )

        // ────────────────────────────────────────── 02 Este telemóvel ──
        Subhead(
            num = "02",
            title = strings.thisPhoneTitle,
            trailing = strings.labelIdentity
        )

        if (!isEditingDeviceName) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditingDeviceName = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Kv(strings.labelName, localDeviceName)
                Kicker(strings.editAction, color = Hypr.Sub)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.Acid, RectangleShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Kicker(strings.labelName, color = Hypr.Acid)
                BasicTextField(
                    value = localDeviceName,
                    onValueChange = { localDeviceName = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 13.sp,
                        color = Hypr.Paper
                    ),
                    cursorBrush = SolidColor(Hypr.Acid),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HyprButton(
                        label = strings.save,
                        onClick = {
                            com.example.DeviceIdentity.setDeviceName(context, localDeviceName)
                            isEditingDeviceName = false
                        },
                        kind = HyprButtonKind.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    HyprButton(
                        label = strings.cancel,
                        onClick = {
                            localDeviceName = com.example.DeviceIdentity.getDeviceName(context)
                            isEditingDeviceName = false
                        },
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Kv(
            strings.labelLocalSha,
            if (localFingerprint != "—") formatHexFingerprint(localFingerprint) else "—"
        )

        Gap(4.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HyprButton(
                label = strings.btnCopySha,
                onClick = {
                    if (localFingerprint != "—") {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256", localFingerprint))
                        Toast.makeText(context, strings.toastShaCopied, Toast.LENGTH_SHORT).show()
                    }
                },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f)
            )
            HyprButton(
                label = strings.btnRegenerateIdentity,
                onClick = onRegenerateIdentity,
                kind = HyprButtonKind.Danger,
                modifier = Modifier.weight(1f)
            )
        }

        Gap(2.dp)
        Kicker(
            text = strings.regenerateWarning.uppercase(),
            color = Hypr.Faint
        )
        Gap(16.dp)

        // ────────────────────────────────────────── 03 Notificações ──
        Subhead(
            num = "03",
            title = strings.sectionNotifications
        )

        Setting(
            title = strings.notificationMirrorTitle,
            sub = strings.settingNotificationMirrorSub
        ) {
            HyprSwitch(
                checked = isMirrorEnabled,
                onCheckedChange = { checked ->
                    isMirrorEnabled = checked
                    sharedPrefs.edit().putBoolean("mirror_notifications", checked).apply()
                    if (checked) {
                        coroutineScope.launch {
                            com.example.HyprNotificationListenerService.replayActiveNotifications(context)
                        }
                    }
                }
            )
        }

        Setting(
            title = strings.settingPcPlayerNotificationTitle,
            sub = strings.settingPcPlayerNotificationSub
        ) {
            HyprSwitch(
                checked = isPcPlayerEnabled,
                onCheckedChange = { checked ->
                    isPcPlayerEnabled = checked
                    sharedPrefs.edit()
                        .putBoolean(com.example.PcPlayerNotificationManager.PREF_SHOW_PC_PLAYER, checked)
                        .apply()
                }
            )
        }

        Setting(
            title = strings.settingKeyboardLiveTitle,
            sub = strings.settingKeyboardLiveSub
        ) {
            HyprSwitch(
                checked = isKeyboardLive,
                onCheckedChange = { checked ->
                    isKeyboardLive = checked
                    sharedPrefs.edit()
                        .putBoolean("keyboard_live_mode", checked)
                        .apply()
                }
            )
        }

        val notifSubText = if (hasNotificationPermission) {
            strings.settingNotificationAccessSubActive
        } else if (ManufacturerGuidance.isRestrictedSettingsApplicable()) {
            strings.settingNotificationAccessSubNeeded + "\n" + strings.restrictedSettingsGuide
        } else {
            strings.settingNotificationAccessSubNeeded
        }

        Setting(
            title = strings.systemAccessTitle,
            sub = notifSubText
        ) {
            if (hasNotificationPermission) {
                HyprSwitch(
                    checked = true,
                    onCheckedChange = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            } else {
                HyprButton(
                    label = strings.btnGrant,
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                    kind = HyprButtonKind.Primary
                )
            }
        }

        Setting(
            title = strings.dndAccessTitle,
            sub = if (hasDndPermission) strings.settingDndSubActive else strings.settingDndSubNeeded
        ) {
            if (hasDndPermission) {
                HyprSwitch(
                    checked = true,
                    onCheckedChange = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            } else {
                HyprButton(
                    label = strings.btnGrant,
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    },
                    kind = HyprButtonKind.Primary
                )
            }
        }

        Setting(
            title = strings.settingsPermissionsRow,
            sub = strings.settingsPermissionsRowDesc
        ) {
            HyprButton(
                label = strings.btnGrant,
                onClick = onOpenPermissionsDialog,
                kind = HyprButtonKind.Ghost
            )
        }

        val manufacturerGuide = ManufacturerGuidance.getGuideText(strings, context)
        if (manufacturerGuide.isNotBlank()) {
            Setting(
                title = strings.manufacturerGuidanceTitle,
                sub = manufacturerGuide
            ) {
                HyprButton(
                    label = strings.btnConfigureSystem,
                    onClick = { ManufacturerGuidance.openAutostartOrBatterySettings(context) },
                    kind = HyprButtonKind.Ghost
                )
            }
        }

        // ────────────────────────────────────────── 04 Partilha com o PC ──
        Subhead(
            num = "04",
            title = strings.sectionShareWithPc
        )

        Setting(
            title = strings.settingShareCellGenTitle,
            sub = strings.settingShareCellGenSub
        ) {
            HyprSwitch(
                checked = isShareCellGen,
                onCheckedChange = { checked ->
                    if (checked) {
                        val hasPhonePerm = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_BASIC_PHONE_STATE
                        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPhonePerm) {
                            isShareCellGen = true
                            sharedPrefs.edit().putBoolean("share_cell_gen", true).apply()
                            com.example.HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()
                        } else {
                            phoneStatePermissionLauncher.launch(android.Manifest.permission.READ_BASIC_PHONE_STATE)
                        }
                    } else {
                        isShareCellGen = false
                        sharedPrefs.edit().putBoolean("share_cell_gen", false).apply()
                        com.example.HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()
                    }
                }
            )
        }

        Setting(
            title = strings.settingShareWifiSsidTitle,
            sub = strings.settingShareWifiSsidSub
        ) {
            HyprSwitch(
                checked = isShareWifiSsid,
                onCheckedChange = { checked ->
                    if (checked) {
                        val hasLocPerm = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasLocPerm) {
                            isShareWifiSsid = true
                            sharedPrefs.edit().putBoolean("share_wifi_ssid", true).apply()
                            com.example.HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    } else {
                        isShareWifiSsid = false
                        sharedPrefs.edit().putBoolean("share_wifi_ssid", false).apply()
                        com.example.HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce()
                    }
                }
            )
        }

        val photoStatus = PhotoAccessLogic.checkPhotoAccess(context)
        val screenshotSub = if (photoStatus == PhotoAccessStatus.PARTIAL) {
            strings.settingAutoScreenshotsSub + "\n" + strings.partialPhotosAccessWarning
        } else {
            strings.settingAutoScreenshotsSub
        }

        Setting(
            title = strings.settingAutoScreenshotsTitle,
            sub = screenshotSub
        ) {
            if (photoStatus == PhotoAccessStatus.PARTIAL) {
                HyprButton(
                    label = strings.btnAllowAllPhotos,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    kind = HyprButtonKind.Primary
                )
            } else {
                HyprSwitch(
                    checked = isAutoSyncScreenshots && photoStatus == PhotoAccessStatus.FULL,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val imagePerm = android.Manifest.permission.READ_MEDIA_IMAGES
                            val hasImgPerm = PhotoAccessLogic.canAutoSyncScreenshots(context)
                            if (hasImgPerm) {
                                isAutoSyncScreenshots = true
                                sharedPrefs.edit().putBoolean("auto_sync_screenshots", true).apply()
                            } else {
                                permissionLauncher.launch(imagePerm)
                            }
                        } else {
                            isAutoSyncScreenshots = false
                            sharedPrefs.edit().putBoolean("auto_sync_screenshots", false).apply()
                        }
                    }
                )
            }
        }

        // Atalho da cortina
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = strings.settingCustomShortcutTitle,
                color = Hypr.Paper,
                style = TextStyle(
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = HyprSize.Body
                )
            )
            Gap(3.dp)
            Mono(
                text = strings.settingCustomShortcutSub,
                color = Hypr.Muted,
                size = 11.sp
            )
            Gap(10.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.Muted, RectangleShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (customShortcutCmd.isEmpty()) {
                    Mono("exec, kitty", color = Hypr.Faint, size = 12.sp)
                }
                BasicTextField(
                    value = customShortcutCmd,
                    onValueChange = {
                        onCustomShortcutCmdChange(it)
                        sharedPrefs.edit().putString("custom_shortcut_cmd", it).apply()
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 12.sp,
                        color = Hypr.Paper
                    ),
                    cursorBrush = SolidColor(Hypr.Acid),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Rule()

        // ────────────────────────────────────────── 05 Gestos ──
        Subhead(
            num = "05",
            title = strings.sectionGestures
        )

        Setting(
            title = strings.settingGesturesMaster,
            sub = strings.settingGesturesMasterDesc
        ) {
            HyprSwitch(
                checked = isGesturesMasterEnabled,
                onCheckedChange = { checked ->
                    isGesturesMasterEnabled = checked
                    sharedPrefs.edit().putBoolean("gestures_enabled", checked).apply()
                }
            )
        }

        if (isGesturesMasterEnabled) {
            Setting(
                title = strings.settingGestureSwipe3,
                sub = strings.settingGestureSwipe3Desc
            ) {
                HyprSwitch(
                    checked = isGestureSwipe3Enabled,
                    onCheckedChange = { checked ->
                        isGestureSwipe3Enabled = checked
                        sharedPrefs.edit().putBoolean("gesture_swipe_3_enabled", checked).apply()
                    }
                )
            }

            Setting(
                title = strings.settingGestureVolume,
                sub = strings.settingGestureVolumeDesc
            ) {
                HyprSwitch(
                    checked = isGestureVolumeEnabled,
                    onCheckedChange = { checked ->
                        isGestureVolumeEnabled = checked
                        sharedPrefs.edit().putBoolean("gesture_volume_enabled", checked).apply()
                    }
                )
            }

            Setting(
                title = strings.settingGestureRotate,
                sub = strings.settingGestureRotateDesc
            ) {
                HyprSwitch(
                    checked = isGestureRotateEnabled,
                    onCheckedChange = { checked ->
                        isGestureRotateEnabled = checked
                        sharedPrefs.edit().putBoolean("gesture_rotate_landscape_enabled", checked).apply()
                    }
                )
            }

            Setting(
                title = strings.settingGestureBackTap,
                sub = strings.settingGestureBackTapDesc
            ) {
                HyprSwitch(
                    checked = isGestureBackTapEnabled,
                    onCheckedChange = { checked ->
                        isGestureBackTapEnabled = checked
                        sharedPrefs.edit().putBoolean("gesture_double_tap_back_enabled", checked).apply()
                    }
                )
            }
        }
        Rule()

        // ────────────────────────────────────────── 06 Idioma ──
        Subhead(
            num = "06",
            title = strings.sectionLanguage
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val languages = listOf(
                AppLanguage.PT_PT to "PT-PT",
                AppLanguage.PT_BR to "PT-BR",
                AppLanguage.EN_UK to "EN",
                AppLanguage.ES_ES to "ES",
                AppLanguage.ZH_CN to "中文"
            )
            languages.forEach { (lang, label) ->
                HyprChip(
                    label = label,
                    selected = currentLang == lang,
                    acidSelected = true,
                    onClick = {
                        LanguageManager.setLanguage(context, lang)
                    }
                )
            }
        }

        // ────────────────────────────────────────── 07 Sobre ──
        Subhead(
            num = "07",
            title = strings.sectionAbout
        )

        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        if (SupportDonateLogic.isSupportSectionVisible(BuildConfig.DONATE_URL)) {
            Gap(8.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.Line, RectangleShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Kicker(strings.supportSectionTitle, color = Hypr.Sub)

                Image(
                    painter = painterResource(id = R.drawable.kofi_banner),
                    contentDescription = strings.supportBannerContentDesc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1440f / 454f)
                        .border(1.dp, Hypr.Line, RectangleShape)
                        .clickable(role = Role.Button) {
                            SupportDonateLogic.openDonateUrl(
                                context = context,
                                donateUrl = BuildConfig.DONATE_URL,
                                fallbackMessage = strings.toastNoBrowserFound
                            )
                        },
                    contentScale = ContentScale.FillWidth
                )

                Text(
                    text = strings.supportDesc,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = Hypr.Sub,
                        lineHeight = 18.sp
                    )
                )

                HyprButton(
                    label = strings.btnSupportKofi,
                    onClick = {
                        SupportDonateLogic.openDonateUrl(
                            context = context,
                            donateUrl = BuildConfig.DONATE_URL,
                            fallbackMessage = strings.toastNoBrowserFound
                        )
                    },
                    kind = HyprButtonKind.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                )
            }
            Gap(12.dp)
        }

        Kv(strings.labelVersion, BuildConfig.VERSION_NAME)
        Kv(strings.labelLicense, "GPL-3.0-or-later")
        Kv(
            key = strings.labelRepository,
            value = "github.com/mastermaiolo/hyprlink",
            modifier = Modifier.clickable {
                try {
                    uriHandler.openUri("https://github.com/mastermaiolo/hyprlink")
                } catch (_: Exception) {}
            },
            valueColor = Hypr.Acid
        )
        val phoneDisplayName = com.example.DeviceIdentity.getDeviceName(context)
        Kv(strings.labelPhone, "$phoneDisplayName · Android ${Build.VERSION.RELEASE}")
        Kv(strings.labelBannerCredit, "© Maggio")
        Gap(12.dp)
        Setting(
            title = strings.licensesTitle,
            sub = strings.licensesRow
        ) {
            HyprButton(
                label = strings.detail,
                onClick = { showLicensesDialog = true },
                kind = HyprButtonKind.Ghost
            )
        }

        Gap(24.dp)
    }

    if (showLicensesDialog) {
        Dialog(
            onDismissRequest = {
                showLicensesDialog = false
                selectedLicenseName = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(strings.licensesTitle, color = Hypr.Acid, strong = true)
                        Kicker(
                            text = strings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable {
                                showLicensesDialog = false
                                selectedLicenseName = null
                            }
                        )
                    }

                    Rule(color = Hypr.Paper, thickness = 2.dp)
                    Gap(8.dp)

                    Headline(strings.licensesTitle, size = 26.sp)
                    Deck(strings.licensesDeck, small = true)
                    Gap(14.dp)

                    if (selectedLicenseName != null) {
                        val fileName = when (selectedLicenseName) {
                            "HyprLink" -> "GPL-3.0.txt"
                            "Anton" -> "OFL-Anton.txt"
                            "Instrument Serif" -> "OFL-InstrumentSerif.txt"
                            "Inter" -> "OFL-Inter.txt"
                            else -> "OFL-IBMPlexMono.txt"
                        }
                        val licenseText = remember(selectedLicenseName) {
                            try {
                                context.assets.open("licencas/$fileName").bufferedReader().use { it.readText() }
                            } catch (e: Exception) {
                                "Failed to load license: ${e.message}"
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLicenseName = null }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Kicker(strings.back, color = Hypr.Acid)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                selectedLicenseName ?: "",
                                color = Hypr.Paper,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            )
                        }
                        Gap(8.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Hypr.Ink1, RectangleShape)
                                .border(1.dp, Hypr.Line, RectangleShape)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Mono(licenseText, color = Hypr.Sub, size = 11.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val licensesList = listOf(
                                "HyprLink" to "GNU General Public License v3.0 or later",
                                "Anton" to "SIL Open Font License 1.1",
                                "Instrument Serif" to "SIL Open Font License 1.1",
                                "Inter" to "SIL Open Font License 1.1",
                                "IBM Plex Mono" to "SIL Open Font License 1.1"
                            )
                            licensesList.forEach { (name, licType) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Hypr.Ink1, RectangleShape)
                                        .border(1.dp, Hypr.Line, RectangleShape)
                                        .clickable { selectedLicenseName = name }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            name,
                                            color = Hypr.Paper,
                                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        )
                                        Mono(licType, color = Hypr.Muted, size = 11.sp)
                                    }
                                    Kicker("${strings.viewAction} →", color = Hypr.Acid)
                                }
                            }
                        }
                    }

                    Gap(12.dp)
                    HyprButton(
                        label = strings.closeDialog,
                        onClick = {
                            showLicensesDialog = false
                            selectedLicenseName = null
                        },
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showUsbTetherDialog) {
        Dialog(
            onDismissRequest = { showUsbTetherDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.LineStrong, RectangleShape)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Kicker(strings.dialogUsbTetherTitle, color = Hypr.Acid, strong = true)
                    Body(strings.dialogUsbTetherDesc, color = Hypr.Paper)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        HyprButton(
                            label = strings.cancel,
                            onClick = { showUsbTetherDialog = false },
                            kind = HyprButtonKind.Ghost
                        )
                        Spacer(Modifier.width(10.dp))
                        HyprButton(
                            label = strings.confirm,
                            onClick = {
                                sharedPrefs.edit().putBoolean("has_seen_usb_tether_guide", true).apply()
                                showUsbTetherDialog = false
                                UsbTetherHelper.openUsbTetherSettings(context)
                            },
                            kind = HyprButtonKind.Primary
                        )
                    }
                }
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}
