// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*
import com.example.ui.kit.*
import com.example.ui.theme.Hypr
import com.example.ui.theme.LinkState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi", sdk = [34])
class ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screenshot_dashboard_connected() {
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalAppStrings provides STRINGS_PT_PT) {
                MyApplicationTheme {
                    DashboardScreen(
                    linkState = LinkState.CONNECTED,
                    deviceName = "ARCH-DESKTOP",
                    onDeviceClick = {},
                    onStateButtonClick = {},
                    mediaTitle = "Solar Power",
                    mediaArtist = "Lorde",
                    isPlaying = true,
                    onPrevClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {},
                    progress = 0.42f,
                    elapsedTime = "1:42",
                    totalTime = "3:58",
                    clipboardContent = "https://hyprland.org",
                    clipboardStateText = "SINCRONIZADO",
                    clipboardStateColor = Hypr.Acid,
                    onSendClipboardClick = {},
                    onReceiveClipboardClick = {},
                    onSendFilesClick = {},
                    onWebcamClick = {},
                    webcamActive = false,
                    isMicOn = false,
                    cpuUsage = 24.5f,
                    ramUsageGB = 8.2f,
                    ramTotalGB = 32.0f,
                    diskFreeGB = 450.0f,
                    diskTotalGB = 1000.0f,
                    gpuUsage = 38.0f,
                    tempCelsius = 48.0f,
                    uptimeSeconds = 3600L,
                    batteryPercent = 88,
                    onDetailClick = {},
                    terminalLogs = listOf(
                        "14:20:01 [CONN] Connected to ARCH-DESKTOP",
                        "14:20:05 [CLIP] tx 24 bytes",
                        "14:21:12 [AUDIO] rx 48kHz stereo"
                    ),
                    mediaAppSource = "Spotify",
                    sentTodayCount = 3
                )
            }
        }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard_connected.png")
    }

    @Test
    fun screenshot_dashboard_connected_zh() {
        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppStrings provides STRINGS_ZH_CN) {
                    DashboardScreen(
                        linkState = LinkState.CONNECTED,
                        deviceName = "ARCH-DESKTOP",
                        onDeviceClick = {},
                        onStateButtonClick = {},
                        mediaTitle = "Solar Power",
                        mediaArtist = "Lorde",
                        isPlaying = true,
                        onPrevClick = {},
                        onPlayPauseClick = {},
                        onNextClick = {},
                        progress = 0.42f,
                        elapsedTime = "1:42",
                        totalTime = "3:58",
                        clipboardContent = "https://hyprland.org",
                        clipboardStateText = "已同步",
                        clipboardStateColor = Hypr.Acid,
                        onSendClipboardClick = {},
                        onReceiveClipboardClick = {},
                        onSendFilesClick = {},
                        onWebcamClick = {},
                        webcamActive = false,
                        isMicOn = false,
                        cpuUsage = 24.5f,
                        ramUsageGB = 8.2f,
                        ramTotalGB = 32.0f,
                        diskFreeGB = 450.0f,
                        diskTotalGB = 1000.0f,
                        gpuUsage = 38.0f,
                        tempCelsius = 48.0f,
                        uptimeSeconds = 3600L,
                        batteryPercent = 88,
                        onDetailClick = {},
                        terminalLogs = listOf(
                            "14:20:01 [CONN] Connected to ARCH-DESKTOP",
                            "14:20:05 [CLIP] tx 24 bytes",
                            "14:21:12 [AUDIO] rx 48kHz stereo"
                        ),
                        mediaAppSource = "Spotify",
                        sentTodayCount = 3
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard_connected_zh.png")
    }

    @Test
    fun screenshot_dashboard_negotiating() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    linkState = LinkState.NEGOTIATING,
                    deviceName = "ARCH-DESKTOP",
                    onDeviceClick = {},
                    onStateButtonClick = {},
                    mediaTitle = "",
                    mediaArtist = "",
                    isPlaying = false,
                    onPrevClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {},
                    progress = null,
                    elapsedTime = null,
                    totalTime = null,
                    clipboardContent = "",
                    clipboardStateText = "A LIGAR…",
                    clipboardStateColor = Hypr.LinkWait,
                    onSendClipboardClick = {},
                    onReceiveClipboardClick = {},
                    onSendFilesClick = {},
                    onWebcamClick = {},
                    webcamActive = false,
                    onDetailClick = {},
                    terminalLogs = listOf("14:20:00 [CONN] Initiating handshake…")
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard_negotiating.png")
    }

    @Test
    fun screenshot_dashboard_disconnected() {
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalAppStrings provides STRINGS_PT_PT) {
                MyApplicationTheme {
                    DashboardScreen(
                        linkState = LinkState.DISCONNECTED,
                        deviceName = "ARCH-DESKTOP",
                        onDeviceClick = {},
                        onStateButtonClick = {},
                        mediaTitle = "",
                        mediaArtist = "",
                        isPlaying = false,
                        onPrevClick = {},
                        onPlayPauseClick = {},
                        onNextClick = {},
                        progress = null,
                        elapsedTime = null,
                        totalTime = null,
                        clipboardContent = "",
                        clipboardStateText = "DESLIGADO",
                        clipboardStateColor = Hypr.Muted,
                        onSendClipboardClick = {},
                        onReceiveClipboardClick = {},
                        onSendFilesClick = {},
                        onWebcamClick = {},
                        webcamActive = false,
                        onDetailClick = {},
                        terminalLogs = emptyList()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard_disconnected.png")
    }

    @Test
    fun screenshot_audio_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AudioScreen(
                    onBack = {},
                    mediaAppSource = "Spotify",
                    mediaTitle = "Solar Power",
                    mediaArtist = "Lorde",
                    artworkBitmap = null,
                    elapsedTime = "1:42",
                    totalTime = "3:58",
                    isPlaying = true,
                    progress = 0.42f,
                    sinks = listOf(
                        AudioSinkItem(1L, "Speakers", "Realtek ALC1220", 72, isMuted = false, isDefault = true),
                        AudioSinkItem(2L, "Headphones", "WH-1000XM4", 50, isMuted = false, isDefault = false)
                    ),
                    apps = listOf(
                        AudioAppItem(1L, "Spotify", "Solar Power - Lorde", 80, isMuted = false, isPlaying = true),
                        AudioAppItem(2L, "Firefox", "YouTube - Talk", 60, isMuted = false, isPlaying = false, idleMinutes = 12)
                    )
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/audio_screen.png")
    }

    @Test
    fun screenshot_audio_screen_with_artwork() {
        val testBmp = android.graphics.Bitmap.createBitmap(290, 290, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.DKGRAY)
        }
        composeTestRule.setContent {
            MyApplicationTheme {
                AudioScreen(
                    onBack = {},
                    mediaAppSource = "Spotify",
                    mediaTitle = "Solar Power",
                    mediaArtist = "Lorde",
                    artworkBitmap = testBmp,
                    elapsedTime = "1:42",
                    totalTime = "3:58",
                    isPlaying = true,
                    progress = 0.42f,
                    sinks = listOf(
                        AudioSinkItem(1L, "Speakers", "Realtek ALC1220", 72, isMuted = false, isDefault = true),
                        AudioSinkItem(2L, "Headphones", "WH-1000XM4", 50, isMuted = false, isDefault = false)
                    ),
                    apps = listOf(
                        AudioAppItem(1L, "Spotify", "Solar Power - Lorde", 80, isMuted = false, isPlaying = true),
                        AudioAppItem(2L, "Firefox", "YouTube - Talk", 60, isMuted = false, isPlaying = false, idleMinutes = 12)
                    )
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/audio_screen_with_artwork.png")
    }

    @Test
    fun screenshot_touchpad_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                TouchpadScreen(
                    deviceName = "ARCH-DESKTOP",
                    onMouseMove = { _, _ -> },
                    onMouseClick = {},
                    onScroll = { _, _ -> },
                    onSendKey = {},
                    onSendText = {},
                    onToggleKeyboardDialog = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/touchpad_screen.png")
    }

    @Test
    fun screenshot_mission_control_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MissionControlScreen(
                    workspaces = listOf(
                        WorkspaceItem(1, "1: term", 2, isActive = true),
                        WorkspaceItem(2, "2: web", 3, isActive = false),
                        WorkspaceItem(3, "3: code", 1, isActive = false),
                        WorkspaceItem(4, "4: music", 1, isActive = false)
                    ),
                    windows = listOf(
                        WindowClientItem("0x123", "kitty — vim", "kitty", 1, isFocused = true),
                        WindowClientItem("0x124", "Firefox — Hyprland Wiki", "firefox", 2, isFocused = false),
                        WindowClientItem("0x125", "Spotify Premium", "spotify", 4, isFocused = false)
                    ),
                    isConnected = true,
                    isLoading = false,
                    customShortcutCmd = "exec kitty",
                    onSelectWorkspace = {},
                    onLaunchApp = {},
                    onCloseWindow = {},
                    onFocusWindow = {},
                    onRefresh = {},
                    onSuspendRequest = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mission_control_screen.png")
    }

    @Test
    fun screenshot_transfers_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.TransfersScreen(
                    onBack = {},
                    activeTransfers = listOf(
                        ActiveTransferItem(
                            id = "tx1",
                            name = "release-v1.0.tar.gz",
                            isIncoming = false,
                            currentBytes = 14_500_000L,
                            totalBytes = 32_000_000L,
                            progress = 0.45f
                        )
                    ),
                    completedTransfers = listOf(
                        CompletedTransferItem(
                            id = "c1",
                            name = "photo_archive.zip",
                            isIncoming = true,
                            totalBytes = 4_200_000L,
                            dateLabel = "Hoje · 14:15",
                            sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                            isVerified = true
                        )
                    ),
                    errorTransfers = emptyList<ErrorTransferItem>(),
                    onSendFileClick = {},
                    onRetryTransfer = {},
                    onDeleteErrorTransfer = {},
                    onClearHistory = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/transfers_screen.png")
    }

    @Test
    fun screenshot_settings_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    onBack = {},
                    activeDeviceName = "ARCH-DESKTOP",
                    activeWorkstation = PairedWorkstation(
                        id = "ws1",
                        deviceName = "ARCH-DESKTOP",
                        host = "192.168.1.100",
                        port = "4242",
                        fingerprint = "AB:CD:EF:12:34:56:78:90:12:34:56:78:90:12:34:56",
                        pairingTokenHex = "1234567890abcdef"
                    ),
                    isConnected = true
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings_screen.png")
    }

    @Test
    fun screenshot_hyprmasthead_states() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Hypr.Void)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    HyprMasthead(
                        state = LinkState.CONNECTED,
                        device = "ARCH-DESKTOP",
                        stateLabel = "LIGADO"
                    )
                    HyprMasthead(
                        state = LinkState.NEGOTIATING,
                        device = "ARCH-DESKTOP",
                        stateLabel = "A LIGAR…"
                    )
                    HyprMasthead(
                        state = LinkState.DISCONNECTED,
                        device = "ARCH-DESKTOP",
                        stateLabel = "DESLIGADO"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/hyprmasthead_states.png")
    }

    @Test
    fun screenshot_full_journal_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                FullJournalScreen(
                    terminalLogs = listOf(
                        "14:20:00 [CONN] Discovery initiated on 192.168.1.255",
                        "14:20:01 [CONN] Handshake OK: TLS 1.3 mTLS established",
                        "14:20:05 [CLIP] tx 24 bytes (clipboard synced)",
                        "14:21:00 [WARN] Ping jitter > 45ms",
                        "14:21:12 [AUDIO] rx 48kHz Opus stream active",
                        "14:22:00 [ERR] packet timeout seq=42 (recovered)"
                    ),
                    onBack = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/full_journal_screen.png")
    }

    @Test
    fun screenshot_dialogs() {
        val strings = STRINGS_PT_PT
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Hypr.Void)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog Workstation Card Mockup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Hypr.Ink0, RectangleShape)
                            .border(1.dp, Hypr.LineStrong, RectangleShape)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Kicker("ESTAÇÕES", color = Hypr.Acid, strong = true)
                                Kicker(strings.btnClose, color = Hypr.Sub)
                            }
                            Headline(strings.dialogChangePc, size = 24.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("ARCH-DESKTOP", color = Hypr.Paper, fontSize = 14.sp)
                                    Mono("192.168.1.100:4242 · ABCD…", color = Hypr.Muted, size = 11.sp)
                                }
                                Tag(strings.tagActive, background = Hypr.Acid, color = Hypr.Void)
                            }
                        }
                    }

                    // Dialog Battery Card Mockup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Hypr.Ink0, RectangleShape)
                            .border(1.dp, Hypr.LineStrong, RectangleShape)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Kicker("PERMISSÕES", color = Hypr.Acid, strong = true)
                                Kicker(strings.btnClose, color = Hypr.Sub)
                            }
                            Headline(strings.dialogBattery, size = 24.sp)
                            Deck(strings.dialogBatteryDesc, small = true)
                            Mono(strings.dialogBatteryReason, color = Hypr.Muted, size = 11.sp)
                        }
                    }

                    // Dialog DND Card Mockup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Hypr.Ink0, RectangleShape)
                            .border(1.dp, Hypr.LineStrong, RectangleShape)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Kicker("PERMISSÕES", color = Hypr.Acid, strong = true)
                                Kicker(strings.btnClose, color = Hypr.Sub)
                            }
                            Headline(strings.dialogDnd, size = 24.sp)
                            Deck(strings.dialogDndDesc, small = true)
                            Mono(strings.dialogDndReason, color = Hypr.Muted, size = 11.sp)
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dialogs.png")
    }

    @Test
    fun screenshot_camera_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Hypr.Void)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Kicker("CÂMARA → /dev/video42", color = Hypr.Paper)
                            Tag("EM DIRETO", background = Hypr.Acid, color = Hypr.Void)
                        }

                        // Simulated Camera viewfinder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(Hypr.Ink1, RectangleShape)
                                .border(1.dp, Hypr.LineStrong, RectangleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Mono("1080p @ 30fps · H.264", color = Hypr.Sub, size = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HyprButton("LANTERNA", {}, kind = HyprButtonKind.Ghost, modifier = Modifier.weight(1f))
                            HyprButton("VIRAR", {}, kind = HyprButtonKind.Ghost, modifier = Modifier.weight(1f))
                            HyprButton("PARAR", {}, kind = HyprButtonKind.Danger, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/camera_screen.png")
    }

    @Test
    fun screenshot_initial_permissions_dialog() {
        composeTestRule.setContent {
            MyApplicationTheme {
                InitialPermissionsDialog(onDismiss = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/initial_permissions_dialog.png")
    }

    @Test
    fun screenshot_dashboard_no_workstation() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    linkState = LinkState.DISCONNECTED,
                    deviceName = "Sem Workstation",
                    onDeviceClick = {},
                    onStateButtonClick = {},
                    mediaTitle = "",
                    mediaArtist = "",
                    isPlaying = false,
                    onPrevClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {},
                    progress = null,
                    elapsedTime = null,
                    totalTime = null,
                    clipboardContent = "",
                    clipboardStateText = "DESLIGADO",
                    clipboardStateColor = Hypr.Muted,
                    onSendClipboardClick = {},
                    onReceiveClipboardClick = {},
                    onSendFilesClick = {},
                    onWebcamClick = {},
                    webcamActive = false,
                    onDetailClick = {},
                    terminalLogs = emptyList(),
                    hasWorkstation = false
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard_no_workstation.png")
    }

    @Test
    fun screenshot_masthead_pc_battery_states() {
        val strings = STRINGS_PT_PT
        val states = listOf(
            PcBattery(level = 64, charging = false, plugged = false, present = true),
            PcBattery(level = 82, charging = true, plugged = true, present = true),
            PcBattery(level = 100, charging = false, plugged = true, present = true),
            PcBattery(level = 80, charging = false, plugged = true, present = true),
            PcBattery(level = 8, charging = false, plugged = false, present = true)
        )
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalAppStrings provides strings) {
                MyApplicationTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Hypr.Void)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        states.forEach { battery ->
                            val display = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = strings)
                            HyprMasthead(
                                state = LinkState.CONNECTED,
                                device = "ARCH-DESKTOP",
                                stateLabel = "LIGADO",
                                large = true,
                                subtitle = display?.text,
                                subtitleColor = display?.color ?: Hypr.Sub
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/masthead_pc_battery_states.png")
    }
}
