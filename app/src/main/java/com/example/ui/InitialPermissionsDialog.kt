// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.AppStrings
import com.example.LocalAppStrings
import com.example.PhotoAccessLogic
import com.example.PhotoAccessStatus
import com.example.ui.kit.Deck
import com.example.ui.kit.Gap
import com.example.ui.kit.Headline
import com.example.ui.kit.HyprButton
import com.example.ui.kit.HyprButtonKind
import com.example.ui.kit.Kicker
import com.example.ui.kit.Mono
import com.example.ui.kit.Rule
import com.example.ui.kit.TagOutline
import com.example.ui.theme.Hypr
import com.example.ui.theme.Inter

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun isNotificationListenerGranted(context: Context): Boolean {
    val pkgName = context.packageName
    try {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(pkgName)) {
            return true
        }
    } catch (_: Exception) {}
    val flat = Settings.Secure.getString(
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

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

fun isDndPolicyAccessGranted(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
    return nm?.isNotificationPolicyAccessGranted ?: false
}

private fun isRuntimePermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private enum class SpecialSequenceStep {
    IDLE,
    WAITING_RUNTIME,
    BATTERY,
    LISTENER,
    DND,
    DONE
}

@Composable
fun InitialPermissionsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sharedPrefs = remember { context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE) }
    var requestedSet by remember {
        mutableStateOf(sharedPrefs.getStringSet("requested_permissions", emptySet())?.toSet() ?: emptySet())
    }

    val imagesPermission = Manifest.permission.READ_MEDIA_IMAGES

    // Permission states
    var postNotifGranted by remember {
        mutableStateOf(isRuntimePermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS))
    }
    var batteryGranted by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var notifListenerGranted by remember { mutableStateOf(isNotificationListenerGranted(context)) }

    var cameraGranted by remember { mutableStateOf(isRuntimePermissionGranted(context, Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(isRuntimePermissionGranted(context, Manifest.permission.RECORD_AUDIO)) }
    var locationGranted by remember { mutableStateOf(isRuntimePermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) || isRuntimePermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    var phoneStateGranted by remember { mutableStateOf(isRuntimePermissionGranted(context, Manifest.permission.READ_BASIC_PHONE_STATE) || isRuntimePermissionGranted(context, Manifest.permission.READ_PHONE_STATE)) }
    var photoAccessStatus by remember { mutableStateOf(PhotoAccessLogic.checkPhotoAccess(context)) }
    var imagesGranted by remember { mutableStateOf(photoAccessStatus == PhotoAccessStatus.FULL) }
    var dndGranted by remember { mutableStateOf(isDndPolicyAccessGranted(context)) }

    var sequenceStep by remember { mutableStateOf(SpecialSequenceStep.IDLE) }
    var isGrantingAllActive by remember { mutableStateOf(false) }
    var hasCompletedGrantAll by remember { mutableStateOf(false) }

    fun refreshStates() {
        postNotifGranted = isRuntimePermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        batteryGranted = isBatteryOptimizationIgnored(context)
        notifListenerGranted = isNotificationListenerGranted(context)

        cameraGranted = isRuntimePermissionGranted(context, Manifest.permission.CAMERA)
        micGranted = isRuntimePermissionGranted(context, Manifest.permission.RECORD_AUDIO)
        locationGranted = isRuntimePermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                          isRuntimePermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        phoneStateGranted = isRuntimePermissionGranted(context, Manifest.permission.READ_BASIC_PHONE_STATE) ||
                            isRuntimePermissionGranted(context, Manifest.permission.READ_PHONE_STATE)
        photoAccessStatus = PhotoAccessLogic.checkPhotoAccess(context)
        imagesGranted = photoAccessStatus == PhotoAccessStatus.FULL
        dndGranted = isDndPolicyAccessGranted(context)
    }

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openDndSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun advanceToNextSpecialStep(currentAfter: SpecialSequenceStep) {
        if (!isBatteryOptimizationIgnored(context) && currentAfter < SpecialSequenceStep.BATTERY) {
            sequenceStep = SpecialSequenceStep.BATTERY
            openBatterySettings()
            return
        }
        if (!isNotificationListenerGranted(context) && currentAfter < SpecialSequenceStep.LISTENER) {
            sequenceStep = SpecialSequenceStep.LISTENER
            openNotificationListenerSettings()
            return
        }
        if (!isDndPolicyAccessGranted(context) && currentAfter < SpecialSequenceStep.DND) {
            sequenceStep = SpecialSequenceStep.DND
            openDndSettings()
            return
        }
        sequenceStep = SpecialSequenceStep.DONE
        isGrantingAllActive = false
        hasCompletedGrantAll = true
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshStates()
    }

    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshStates()
        if (isGrantingAllActive) {
            advanceToNextSpecialStep(SpecialSequenceStep.WAITING_RUNTIME)
        }
    }

    fun requestSingle(perm: String) {
        val updated = requestedSet + perm
        requestedSet = updated
        sharedPrefs.edit().putStringSet("requested_permissions", updated).apply()
        singleLauncher.launch(perm)
    }

    fun requestLocation() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val updated = requestedSet + perms
        requestedSet = updated
        sharedPrefs.edit().putStringSet("requested_permissions", updated).apply()
        multipleLauncher.launch(perms)
    }

    fun launchGrantAll() {
        refreshStates()
        val missingRuntime = mutableListOf<String>()
        if (!postNotifGranted) {
            missingRuntime.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!cameraGranted) missingRuntime.add(Manifest.permission.CAMERA)
        if (!micGranted) missingRuntime.add(Manifest.permission.RECORD_AUDIO)
        if (!locationGranted) {
            missingRuntime.add(Manifest.permission.ACCESS_FINE_LOCATION)
            missingRuntime.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!phoneStateGranted) missingRuntime.add(Manifest.permission.READ_BASIC_PHONE_STATE)
        if (!imagesGranted) missingRuntime.add(imagesPermission)

        val updated = requestedSet + missingRuntime
        requestedSet = updated
        sharedPrefs.edit().putStringSet("requested_permissions", updated).apply()

        isGrantingAllActive = true
        if (missingRuntime.isNotEmpty()) {
            sequenceStep = SpecialSequenceStep.WAITING_RUNTIME
            multipleLauncher.launch(missingRuntime.toTypedArray())
        } else {
            advanceToNextSpecialStep(SpecialSequenceStep.IDLE)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
                if (isGrantingAllActive) {
                    advanceToNextSpecialStep(sequenceStep)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun isPermanentlyDenied(perm: String): Boolean {
        if (isRuntimePermissionGranted(context, perm)) return false
        if (!requestedSet.contains(perm)) return false
        val activity = context.findActivity() ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
    }

    val allGranted = postNotifGranted && batteryGranted && notifListenerGranted &&
            cameraGranted && micGranted && locationGranted && phoneStateGranted && imagesGranted && dndGranted

    Dialog(
        onDismissRequest = onDismiss,
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Kicker(strings.permissionsTitle, color = Hypr.Acid, strong = true)
                    Kicker(
                        text = strings.btnClose,
                        color = Hypr.Sub,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Rule(color = Hypr.Paper, thickness = 2.dp)
                Gap(8.dp)

                Headline(strings.permissionsHeadline, size = 26.sp)
                Deck(strings.permissionsDeck, small = true)
                Gap(10.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── ESSENCIAIS ──
                    Kicker(strings.sectionEssential, color = Hypr.Acid, strong = true)
                    Gap(4.dp)

                    val postNotifDenied = isPermanentlyDenied(Manifest.permission.POST_NOTIFICATIONS)
                    PermissionRow(
                        title = strings.permAppNotifications,
                        description = strings.permAppNotificationsDesc,
                        isGranted = postNotifGranted,
                        isDenied = postNotifDenied,
                        onGrant = { requestSingle(Manifest.permission.POST_NOTIFICATIONS) },
                        onOpenSettings = { openAppSettings() },
                        strings = strings
                    )

                    PermissionRow(
                        title = strings.permBattery,
                        description = strings.permBatteryDesc,
                        isGranted = batteryGranted,
                        isDenied = false,
                        onGrant = { openBatterySettings() },
                        onOpenSettings = { openBatterySettings() },
                        strings = strings
                    )

                    PermissionRow(
                        title = strings.permNotificationListener,
                        description = strings.permNotificationListenerDesc,
                        isGranted = notifListenerGranted,
                        isDenied = false,
                        onGrant = { openNotificationListenerSettings() },
                        onOpenSettings = { openNotificationListenerSettings() },
                        strings = strings
                    )

                    if (!notifListenerGranted && ManufacturerGuidance.isRestrictedSettingsApplicable()) {
                        Gap(4.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Hypr.Acid.copy(alpha = 0.5f), RectangleShape)
                                .background(Hypr.Paper.copy(alpha = 0.05f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = strings.restrictedSettingsGuide,
                                style = TextStyle(
                                    fontFamily = Inter,
                                    fontSize = 12.sp,
                                    color = Hypr.Acid
                                )
                            )
                        }
                    }

                    Gap(14.dp)

                    // ── OPCIONAIS ──
                    Kicker(strings.sectionOptional, color = Hypr.Acid, strong = true)
                    Gap(4.dp)

                    PermissionRow(
                        title = strings.permCamera,
                        description = strings.permCameraDesc,
                        isGranted = cameraGranted,
                        isDenied = isPermanentlyDenied(Manifest.permission.CAMERA),
                        onGrant = { requestSingle(Manifest.permission.CAMERA) },
                        onOpenSettings = { openAppSettings() },
                        strings = strings
                    )

                    PermissionRow(
                        title = strings.permMicrophone,
                        description = strings.permMicrophoneDesc,
                        isGranted = micGranted,
                        isDenied = isPermanentlyDenied(Manifest.permission.RECORD_AUDIO),
                        onGrant = { requestSingle(Manifest.permission.RECORD_AUDIO) },
                        onOpenSettings = { openAppSettings() },
                        strings = strings
                    )

                    PermissionRow(
                        title = strings.permLocation,
                        description = strings.permLocationDesc,
                        isGranted = locationGranted,
                        isDenied = isPermanentlyDenied(Manifest.permission.ACCESS_FINE_LOCATION) && isPermanentlyDenied(Manifest.permission.ACCESS_COARSE_LOCATION),
                        onGrant = { requestLocation() },
                        onOpenSettings = { openAppSettings() },
                        strings = strings
                    )

                    PermissionRow(
                        title = strings.permPhoneState,
                        description = strings.permPhoneStateDesc,
                        isGranted = phoneStateGranted,
                        isDenied = isPermanentlyDenied(Manifest.permission.READ_BASIC_PHONE_STATE),
                        onGrant = { requestSingle(Manifest.permission.READ_BASIC_PHONE_STATE) },
                        onOpenSettings = { openAppSettings() },
                        strings = strings
                    )

                    val isPartialPhotos = photoAccessStatus == PhotoAccessStatus.PARTIAL
                    PermissionRow(
                        title = strings.permImages,
                        description = if (isPartialPhotos) strings.permImagesDesc + "\n" + strings.partialPhotosAccessWarning else strings.permImagesDesc,
                        isGranted = imagesGranted,
                        isDenied = isPermanentlyDenied(imagesPermission),
                        onGrant = { requestSingle(imagesPermission) },
                        onOpenSettings = { openAppSettings() },
                        strings = strings,
                        tagOverride = if (isPartialPhotos) strings.tagPartialAccess else null,
                        forceOpenSettings = isPartialPhotos
                    )

                    PermissionRow(
                        title = strings.permDnd,
                        description = strings.permDndDesc,
                        isGranted = dndGranted,
                        isDenied = false,
                        onGrant = { openDndSettings() },
                        onOpenSettings = { openDndSettings() },
                        strings = strings
                    )

                    val manufacturerGuide = ManufacturerGuidance.getGuideText(strings, context)
                    if (manufacturerGuide.isNotBlank()) {
                        Gap(14.dp)
                        Kicker(strings.manufacturerGuidanceTitle, color = Hypr.Acid, strong = true)
                        Gap(6.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Hypr.Line, RectangleShape)
                                .background(Hypr.Paper.copy(alpha = 0.03f))
                                .padding(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = manufacturerGuide,
                                    style = TextStyle(
                                        fontFamily = Inter,
                                        fontSize = 12.sp,
                                        color = Hypr.Paper
                                    )
                                )
                                Gap(8.dp)
                                HyprButton(
                                    label = strings.btnConfigureSystem,
                                    onClick = { ManufacturerGuidance.openAutostartOrBatterySettings(context) },
                                    kind = HyprButtonKind.Ghost,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Gap(12.dp)

                if (allGranted || hasCompletedGrantAll) {
                    HyprButton(
                        label = strings.btnComplete,
                        onClick = onDismiss,
                        kind = HyprButtonKind.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!allGranted) {
                        Gap(6.dp)
                        Mono(
                            text = strings.permissionsFooterHint,
                            color = Hypr.Muted,
                            size = 11.sp,
                            maxLines = 2
                        )
                    }
                } else {
                    HyprButton(
                        label = strings.btnGrantAll,
                        onClick = { launchGrantAll() },
                        kind = HyprButtonKind.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Gap(8.dp)
                    HyprButton(
                        label = strings.btnLater,
                        onClick = onDismiss,
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    isDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    strings: AppStrings,
    tagOverride: String? = null,
    forceOpenSettings: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Hypr.Paper,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            )
            when {
                tagOverride != null -> TagOutline(tagOverride, color = Hypr.LinkWait)
                isGranted -> TagOutline(strings.tagActiveState, color = Hypr.Acid)
                isDenied -> TagOutline(strings.tagDenied, color = Hypr.Hot)
                else -> TagOutline(strings.tagPending, color = Hypr.LinkWait)
            }
        }
        Gap(4.dp)
        Mono(
            text = description,
            color = Hypr.Muted,
            size = 11.sp,
            maxLines = 4
        )
        if (!isGranted || forceOpenSettings) {
            Gap(8.dp)
            HyprButton(
                label = if (isDenied || forceOpenSettings) strings.btnOpenSettings else strings.btnGrant,
                onClick = if (isDenied || forceOpenSettings) onOpenSettings else onGrant,
                kind = HyprButtonKind.Primary
            )
        }
        Gap(10.dp)
        Rule(color = Hypr.Line)
    }
}
