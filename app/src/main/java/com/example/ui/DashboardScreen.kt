// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LocalAppStrings
import com.example.LanguageManager
import com.example.TerminalLogTranslator
import com.example.UsbTetherHelper
import com.example.ui.theme.*
import com.example.ui.kit.*

@Composable
fun DashboardScreen(
    linkState: LinkState,
    deviceName: String,
    onDeviceClick: () -> Unit,
    onStateButtonClick: () -> Unit,
    mediaTitle: String,
    mediaArtist: String,
    isPlaying: Boolean,
    onPrevClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    progress: Float?,
    elapsedTime: String?,
    totalTime: String?,
    clipboardContent: String,
    clipboardStateText: String,
    clipboardStateColor: Color,
    onSendClipboardClick: () -> Unit,
    onReceiveClipboardClick: () -> Unit,
    onSendClipboardImageClick: () -> Unit = {},
    activeWorkstationPcMac: String? = null,
    isWakingPc: Boolean = false,
    wakingCountdownSeconds: Int = 0,
    onWakePcClick: () -> Unit = {},
    onCancelWakePcClick: () -> Unit = {},
    onSendFilesClick: () -> Unit,
    onWebcamClick: () -> Unit,
    webcamActive: Boolean,
    isWebcamStarting: Boolean = false,
    isMicOn: Boolean = false,
    onMicToggleClick: () -> Unit = {},
    isAudioStreaming: Boolean = false,
    metricsProvider: (() -> PcConnectionMetrics)? = null,
    controlService: ServiceState = ServiceState.ACTIVE,
    webcamService: ServiceState = if (webcamActive) ServiceState.ACTIVE else ServiceState.INACTIVE,
    micBridgeService: ServiceState = ServiceState.ACTIVE,
    notifsService: ServiceState = ServiceState.ERROR,
    cpuUsage: Float? = null,
    ramUsageGB: Float? = null,
    ramTotalGB: Float? = null,
    tempCelsius: Float? = null,
    diskFreeGB: Float? = null,
    diskTotalGB: Float? = null,
    gpuUsage: Float? = null,
    uptimeSeconds: Long? = null,
    batteryPercent: Int? = null,
    onDetailClick: () -> Unit,
    terminalLogs: List<String>,
    mediaAppSource: String = "",
    sentTodayCount: Int = 0,
    onOpenFullJournal: () -> Unit = {},
    workstationHostPort: String? = null,
    isConnecting: Boolean = false,
    hasWorkstation: Boolean = deviceName.isNotBlank(),
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang = LanguageManager.currentLanguage.value
    var selectedFilter by remember(strings) { mutableStateOf(strings.filterAll) }

    val phoneBatteryPercent = remember(context) {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val cap = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (cap in 0..100) cap else null
        } catch (_: Exception) { null }
    }
    val phoneStorageFreeGB = remember(context) {
        try {
            val path = android.os.Environment.getDataDirectory().path
            val stat = android.os.StatFs(path)
            stat.availableBytes / (1024L * 1024L * 1024L)
        } catch (_: Exception) { null }
    }
    val phoneNetwork = remember(context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            when {
                caps == null -> "Offline"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> strings.networkMobile
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Ligado"
            }
        } catch (_: Exception) { "—" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyprSpace.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val metrics = metricsProvider?.invoke()
        val cpu = cpuUsage ?: metrics?.cpuUsage
        val ramUsed = ramUsageGB ?: metrics?.ramUsedGB
        val ramTotal = ramTotalGB ?: metrics?.ramTotalGB
        val temp = tempCelsius ?: metrics?.tempCelsius
        val ping = metrics?.pingMs

        if (linkState == LinkState.DISCONNECTED) {
            val hasConfiguredDevice = hasWorkstation && deviceName.isNotBlank()
            Panel(
                background = Hypr.HotTint,
                border = Hypr.BorderRed
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Square(Hypr.Hot, size = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Kicker(strings.disconnected, color = Hypr.Hot, strong = true)
                    Fill()
                    val hostPortText = if (!workstationHostPort.isNullOrBlank()) workstationHostPort else "— : —"
                    Mono(hostPortText, color = Hypr.Muted, size = 11.sp)
                }
                Gap(8.dp)
                Body(
                    if (hasConfiguredDevice) {
                        strings.disconnectedRetryPrompt
                    } else {
                        strings.noWorkstationPairPrompt
                    },
                    color = Hypr.Sub
                )
            }

            Gap(4.dp)

            if (isWakingPc) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Hypr.Ink0)
                        .border(1.dp, Hypr.LinkWait, RectangleShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Kicker(String.format(java.util.Locale.getDefault(), strings.waitingForPcToWake, wakingCountdownSeconds), color = Hypr.LinkWait, strong = true)
                        HyprButton(strings.btnGiveUp, onCancelWakePcClick, kind = HyprButtonKind.Danger)
                    }
                    Bar((60 - wakingCountdownSeconds) / 60f, color = Hypr.LinkWait)
                }
            } else {
                HyprButton(
                    label = if (!hasConfiguredDevice) strings.btnPairPc else if (isConnecting) "···" else strings.btnConnect,
                    onClick = onStateButtonClick,
                    kind = HyprButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!activeWorkstationPcMac.isNullOrBlank()) {
                    Gap(8.dp)
                    HyprButton(
                        label = strings.btnWakePc,
                        onClick = onWakePcClick,
                        kind = HyprButtonKind.Ghost,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Gap(8.dp)
                HyprButton(
                    label = strings.btnUseUsbCable,
                    onClick = {
                        UsbTetherHelper.openUsbTetherSettings(context)
                    },
                    kind = HyprButtonKind.Ghost,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Gap(16.dp)

            Subhead(num = "02", title = strings.thisPhoneTitle, trailing = android.os.Build.MODEL ?: "ANDROID")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val bVal = phoneBatteryPercent ?: batteryPercent
                Stat(label = strings.statBattery, value = if (bVal != null) "$bVal" else "—", unit = "%", modifier = Modifier.weight(1f))
                Stat(label = strings.statNetwork, value = phoneNetwork, unit = if (phoneNetwork == "Wi-Fi") strings.networkLan else if (phoneNetwork == strings.networkMobile) strings.networkData else "", modifier = Modifier.weight(1f))
                Stat(label = strings.statFree, value = if (phoneStorageFreeGB != null) "$phoneStorageFreeGB" else "—", unit = "GB", modifier = Modifier.weight(1f))
            }

        } else {
            // Telemetria do PC (3 colunas x 2 linhas)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Linha 1: CPU | MEMÓRIA | DISCO LIVRE
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(
                        label = strings.statCpu,
                        value = if (cpu != null) "${cpu.toInt()}" else "—",
                        unit = "%",
                        color = if (cpu != null) Hypr.Paper else Hypr.Faint,
                        size = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Stat(
                        label = strings.statMemory,
                        value = if (ramUsed != null && ramTotal != null) String.format(java.util.Locale.US, "%.1f / %.0f", ramUsed, ramTotal) else "—",
                        unit = "GB",
                        color = if (ramUsed != null && ramTotal != null) Hypr.Paper else Hypr.Faint,
                        size = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                    val diskFree = diskFreeGB ?: metrics?.diskFreeGB
                    val (diskVal, diskUnit) = when {
                        diskFree == null -> "—" to "GB"
                        diskFree >= 1000f -> String.format(java.util.Locale.US, "%.1f", diskFree / 1024f) to "TB"
                        else -> String.format(java.util.Locale.US, "%.0f", diskFree) to "GB"
                    }
                    Stat(
                        label = strings.metricDiskFree,
                        value = diskVal,
                        unit = diskUnit,
                        color = if (diskFree != null) Hypr.Paper else Hypr.Faint,
                        size = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Linha 2: TEMPERATURA | LATÊNCIA | GPU
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(
                        label = strings.statTemperature,
                        value = if (temp != null) "${temp.toInt()}" else "—",
                        unit = "°C",
                        color = if (temp != null) Hypr.Paper else Hypr.Faint,
                        size = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Stat(
                        label = strings.statLatency,
                        value = if (ping != null) String.format(java.util.Locale.US, "%.1f", ping) else "—",
                        unit = "ms",
                        color = if (ping != null) Hypr.Paper else Hypr.Faint,
                        size = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                    val gpu = gpuUsage ?: metrics?.gpuUsage
                    Stat(
                        label = strings.metricGpu,
                        value = if (gpu != null) "${gpu.toInt()}" else "—",
                        unit = "%",
                        color = if (gpu != null) Hypr.Paper else Hypr.Faint,
                        size = 28.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Gap(8.dp)

            // A tocar no PC
            val hasMedia = mediaTitle.isNotBlank() && mediaTitle != strings.noActivePlayback
            Subhead(
                num = "01",
                title = strings.playingOnPc,
                trailing = if (hasMedia) mediaAppSource.uppercase().ifBlank { null } else "—"
            )
            if (hasMedia) {
                Panel {
                    Headline(text = mediaTitle, size = 30.sp)
                    if (mediaArtist.isNotBlank()) {
                        Gap(4.dp)
                        Deck(text = mediaArtist, small = true)
                    }
                    if (progress != null) {
                        Gap(12.dp)
                        Bar(fraction = progress)
                    }
                    if (elapsedTime != null && totalTime != null) {
                        Gap(6.dp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Mono(text = elapsedTime, color = Hypr.Muted, size = 10.sp)
                            Mono(text = totalTime, color = Hypr.Muted, size = 10.sp)
                        }
                    }
                    Gap(12.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HyprButton(strings.btnPrevious, onPrevClick, kind = HyprButtonKind.Ghost, modifier = Modifier.weight(1f))
                        HyprButton(if (isPlaying) strings.btnPause else strings.btnPlay, onPlayPauseClick, kind = HyprButtonKind.Primary, modifier = Modifier.weight(1.2f))
                        HyprButton(strings.btnNext, onNextClick, kind = HyprButtonKind.Ghost, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Deck(text = strings.noActivePlayback, small = true, color = Hypr.Muted)
            }

            // Clipboard
            val hasClipboard = clipboardContent.isNotBlank()
            Subhead(
                num = "02",
                title = strings.clipboardTitle,
                trailing = if (hasClipboard) clipboardStateText.uppercase().ifBlank { "—" } else "—"
            )
            if (hasClipboard) {
                Panel {
                    Mono(
                        text = clipboardContent,
                        color = Hypr.Paper,
                        size = 12.sp,
                        maxLines = 4
                    )
                    Gap(12.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HyprButton(strings.sendToPc, onSendClipboardClick, kind = HyprButtonKind.Primary, modifier = Modifier.weight(1f))
                        HyprButton(strings.receiveClipboard, onReceiveClipboardClick, kind = HyprButtonKind.Ghost, modifier = Modifier.weight(1f))
                    }
                    Gap(8.dp)
                    HyprButton(strings.sendImage, onSendClipboardImageClick, kind = HyprButtonKind.Ghost, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Deck(text = strings.clipboardEmpty, small = true, color = Hypr.Muted)
                Gap(12.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HyprButton(strings.sendToPc, onSendClipboardClick, kind = HyprButtonKind.Primary, modifier = Modifier.weight(1f))
                    HyprButton(strings.receiveClipboard, onReceiveClipboardClick, kind = HyprButtonKind.Ghost, modifier = Modifier.weight(1f))
                }
            }

            // Ações rápidas
            Subhead(num = "03", title = strings.quickActions)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Linha 1: FICHEIROS e WEBCAM
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Panel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 114.dp)
                            .clickable(onClick = onSendFilesClick),
                        padding = PaddingValues(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Kicker(strings.navFiles)
                                Gap(4.dp)
                                Box(modifier = Modifier.height(52.dp), contentAlignment = Alignment.TopStart) {
                                    Headline(strings.sendFiles.uppercase(), size = 20.sp, maxLines = 2)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (sentTodayCount > 0) {
                                    Mono(String.format(java.util.Locale.getDefault(), strings.sentToday, sentTodayCount), color = Hypr.Sub, size = 10.sp, maxLines = 1)
                                } else {
                                    Mono("—", color = Hypr.Sub, size = 10.sp)
                                }
                            }
                        }
                    }

                    Panel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 114.dp)
                            .clickable(onClick = onWebcamClick),
                        padding = PaddingValues(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Kicker(strings.permCamera.uppercase())
                                Gap(4.dp)
                                Box(modifier = Modifier.height(52.dp), contentAlignment = Alignment.TopStart) {
                                    Headline(strings.webcamTitle.uppercase(), size = 20.sp, maxLines = 2)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val statusText = when {
                                    webcamActive -> strings.active.uppercase()
                                    isWebcamStarting -> strings.webcamConnecting.uppercase()
                                    else -> strings.inactive.uppercase()
                                }
                                val statusColor = when {
                                    webcamActive || isWebcamStarting -> Hypr.Acid
                                    else -> Hypr.Sub
                                }
                                Kicker(
                                    statusText,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }

                // Linha 2: MICROFONE e WAKE-ON-LAN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Panel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 114.dp)
                            .border(1.dp, if (isMicOn) Hypr.Acid else Hypr.Line, RectangleShape)
                            .clickable(onClick = onMicToggleClick),
                        padding = PaddingValues(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Kicker(strings.permMicrophone.uppercase())
                                    if (isMicOn) {
                                        Tag(strings.btnOn)
                                    }
                                }
                                Gap(4.dp)
                                Box(modifier = Modifier.height(52.dp), contentAlignment = Alignment.TopStart) {
                                    Headline(strings.micToPc.uppercase(), size = 20.sp, maxLines = 2)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Kicker(
                                    if (isMicOn) strings.active.uppercase() else strings.disconnected.uppercase(),
                                    color = if (isMicOn) Hypr.Acid else Hypr.Sub
                                )
                            }
                        }
                    }

                    Panel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 114.dp)
                            .clickable(onClick = onWakePcClick),
                        padding = PaddingValues(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Kicker("WAKE-ON-LAN")
                                Gap(4.dp)
                                Box(modifier = Modifier.height(52.dp), contentAlignment = Alignment.TopStart) {
                                    Headline(strings.actionWake.uppercase(), size = 20.sp, maxLines = 2)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (!activeWorkstationPcMac.isNullOrBlank()) {
                                    Mono(activeWorkstationPcMac, color = Hypr.Sub, size = 10.sp, maxLines = 1)
                                } else {
                                    Kicker(strings.noMacSaved, color = Hypr.Sub)
                                }
                            }
                        }
                    }
                }
            }

            // Serviços
            Subhead(num = "04", title = strings.services)
            Column(Modifier.fillMaxWidth()) {
                val servicesList = listOf(
                    strings.serviceControl to controlService,
                    strings.serviceMicrophone to micBridgeService,
                    strings.serviceWebcam to webcamService,
                    strings.serviceNotifications to notifsService
                )
                servicesList.forEach { (name, srvState) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Kicker(name, color = Hypr.Paper)
                        TagOutline(text = srvState.getLocalizedLabel(strings), color = srvState.color)
                    }
                    Rule()
                }
            }

            // Este telemóvel
            Subhead(num = "05", title = strings.thisPhoneTitle, trailing = android.os.Build.MODEL ?: "ANDROID")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val bVal = phoneBatteryPercent ?: batteryPercent
                Stat(label = strings.statBattery, value = if (bVal != null) "$bVal" else "—", unit = "%", modifier = Modifier.weight(1f))
                Stat(label = strings.statNetwork, value = phoneNetwork, unit = if (phoneNetwork == "Wi-Fi") strings.networkLan else if (phoneNetwork == strings.networkMobile) strings.networkData else "", modifier = Modifier.weight(1f))
                Stat(label = strings.statFree, value = if (phoneStorageFreeGB != null) "$phoneStorageFreeGB" else "—", unit = "GB", modifier = Modifier.weight(1f))
            }

            // Diário
            val hasLogs = terminalLogs.isNotEmpty()
            if (hasLogs) {
                Subhead(
                    num = "06",
                    title = strings.journalTitle,
                    trailingAction = {
                        Kicker(
                            text = strings.viewAllArrow,
                            color = Hypr.Acid,
                            strong = true,
                            modifier = Modifier.clickable { onOpenFullJournal() }
                        )
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterOptions = listOf(strings.filterAll, strings.filterTxRx, strings.filterWarnings, strings.filterErrors)
                    filterOptions.forEach { filterName ->
                        HyprChip(
                            label = filterName,
                            selected = selectedFilter == filterName,
                            onClick = { selectedFilter = filterName }
                        )
                    }
                }
                Gap(8.dp)
                Panel {
                    val filteredLogs = remember(terminalLogs, selectedFilter) {
                        terminalLogs.filter { line ->
                            when (selectedFilter) {
                                strings.filterTxRx -> line.contains("tx", true) || line.contains("rx", true)
                                strings.filterWarnings -> line.contains("warn", true) || line.contains("aviso", true) || line.contains("!!", true)
                                strings.filterErrors -> line.contains("error", true) || line.contains("erro", true) || line.contains("fail", true)
                                else -> true
                            }
                        }.takeLast(10)
                    }

                    if (filteredLogs.isEmpty()) {
                        Deck(
                            text = strings.noFilterMatch,
                            small = true,
                            color = Hypr.Muted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredLogs.forEach { logLine ->
                                val translated = TerminalLogTranslator.translate(logLine, currentLang)
                                val color = when {
                                    translated.contains("erro", true) || translated.contains("fail", true) -> Hypr.Hot
                                    translated.contains("warn", true) || translated.contains("!!", true) -> Hypr.LinkWait
                                    translated.contains("tx", true) -> Hypr.Acid
                                    translated.contains("rx", true) -> Hypr.Cold
                                    else -> Hypr.Sub
                                }
                                val timeMatch = Regex("""^(\d{1,2}:\d{2}(?::\d{2})?)\s+(.*)""").find(translated)
                                val annotated = if (timeMatch != null) {
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = Hypr.Faint)) {
                                            append(timeMatch.groupValues[1])
                                            append("  ")
                                        }
                                        withStyle(SpanStyle(color = color)) {
                                            append(timeMatch.groupValues[2])
                                        }
                                    }
                                } else {
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = color)) {
                                            append(translated)
                                        }
                                    }
                                }
                                Text(
                                    text = annotated,
                                    style = TextStyle(
                                        fontFamily = PlexMono,
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Subhead(num = "06", title = strings.journalTitle, trailing = "—")
                Deck(text = strings.journalEmpty, small = true, color = Hypr.Muted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}


