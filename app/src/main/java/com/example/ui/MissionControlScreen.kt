// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.LocalAppStrings
import com.example.ui.theme.*
import com.example.ui.kit.*

data class WorkspaceItem(
    val id: Int,
    val name: String,
    val windowsCount: Int,
    val isActive: Boolean
)

data class WindowClientItem(
    val address: String,
    val title: String,
    val clientClass: String,
    val workspaceId: Int,
    val isFocused: Boolean
)

@Composable
fun MissionControlScreen(
    workspaces: List<WorkspaceItem>,
    windows: List<WindowClientItem>,
    isConnected: Boolean,
    isLoading: Boolean,
    customShortcutCmd: String,
    onSelectWorkspace: (Int) -> Unit,
    onLaunchApp: (String) -> Unit,
    onCloseWindow: (String) -> Unit,
    onFocusWindow: (String) -> Unit,
    onRefresh: () -> Unit,
    onSuspendRequest: () -> Unit,
    onPcAction: (String) -> Unit = {},
    pcShortcuts: List<com.example.PcShortcut> = emptyList(),
    runningShortcutId: String? = null,
    onRunShortcut: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyprSpace.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = strings.desktopTitle
        )

        if (!isConnected) {
            Deck(strings.connectToSeeWindows, small = true)
        } else if (isLoading && workspaces.isEmpty() && windows.isEmpty()) {
            Kicker(strings.readingHyprland.uppercase(), color = Hypr.LinkWait)
        }

        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val fontScale = configuration.fontScale
        val columns = if (screenWidthDp <= 360 || fontScale >= 1.3f) 4 else 5

        // Workspaces grid: always 1-10, plus any extra workspaces > 10 in additional rows
        val baseWorkspaces = (1..10).map { id ->
            workspaces.find { it.id == id } ?: WorkspaceItem(id, "$id", 0, false)
        }
        val extraWorkspaces = workspaces.filter { it.id > 10 }.sortedBy { it.id }
        val allDisplayWorkspaces = baseWorkspaces + extraWorkspaces

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allDisplayWorkspaces.chunked(columns).forEach { rowWorkspaces ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowWorkspaces.forEach { ws ->
                        val id = ws.id
                        val isActive = ws.isActive
                        val winCount = ws.windowsCount
                        val isOccupied = winCount > 0

                        val bg = when {
                            !isConnected -> Hypr.Void
                            isActive -> Hypr.Acid
                            else -> Hypr.Ink0
                        }
                        val borderColor = when {
                            !isConnected -> Hypr.Faint
                            isActive -> Hypr.Acid
                            isOccupied -> Hypr.LineStrong
                            else -> Hypr.Line
                        }
                        val numColor = when {
                            !isConnected -> Hypr.Faint
                            isActive -> Hypr.Void
                            isOccupied -> Hypr.Paper
                            else -> Hypr.Faint
                        }
                        val labelColor = when {
                            !isConnected -> Hypr.Faint
                            isActive -> Hypr.Void
                            isOccupied -> Hypr.Sub
                            else -> Hypr.Muted
                        }
                        val labelText = when {
                            !isConnected -> "—"
                            winCount >= 1 -> String.format(java.util.Locale.getDefault(), strings.workspaceWindowsShort, winCount).uppercase()
                            else -> ""
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 76.dp)
                                .background(bg, RectangleShape)
                                .border(1.dp, borderColor, RectangleShape)
                                .clickable(enabled = isConnected) { onSelectWorkspace(id) }
                                .padding(HyprSpace.S),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Headline(
                                    text = if (id < 10) "0$id" else "$id",
                                    size = 24.sp,
                                    color = numColor
                                )
                                if (labelText.isNotEmpty()) {
                                    Gap(2.dp)
                                    Text(
                                        text = labelText,
                                        style = TextStyle(
                                            fontFamily = PlexMono,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.05.em
                                        ),
                                        color = labelColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }
                    if (rowWorkspaces.size < columns) {
                        for (i in 0 until (columns - rowWorkspaces.size)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Gap(8.dp)

        // 01. Janelas ativas
        Subhead(
            num = "01",
            title = strings.activeWindowsTitle,
            trailingAction = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Kicker("${windows.size}")
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Transparent, RectangleShape)
                            .border(1.dp, if (isConnected) Hypr.LineStrong else Hypr.Faint, RectangleShape)
                            .clickable(enabled = isConnected) { onRefresh() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⟳", fontSize = 16.sp, color = if (isConnected) Hypr.Paper else Hypr.Faint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )

        if (!isConnected) {
            Deck(strings.connectToSeeWindows, small = true)
        } else if (windows.isEmpty()) {
            Deck(strings.noWindowsOpen, small = true)
        } else {
            Column(Modifier.fillMaxWidth()) {
                windows.forEach { client ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TagOutline(
                            text = "WS ${client.workspaceId}",
                            color = if (client.isFocused) Hypr.Acid else Hypr.Sub
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onFocusWindow(client.address) }
                        ) {
                            Text(
                                text = client.title,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Hypr.Paper),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Gap(2.dp)
                            Mono(
                                text = client.clientClass,
                                color = Hypr.Muted,
                                size = 11.sp
                            )
                        }
                        Kicker(
                            text = strings.closeWindow.uppercase(),
                            color = Hypr.Hot,
                            strong = true,
                            modifier = Modifier.clickable { onCloseWindow(client.address) }
                        )
                    }
                    Rule()
                }
            }
        }

        Gap(8.dp)

        // 02. Atalhos
        Subhead(num = "02", title = strings.quickLaunchTitle, trailing = "PC.ACTION")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HyprButton(
                label = strings.btnLock,
                onClick = { onPcAction("lock") },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f),
                enabled = isConnected
            )
            HyprButton(
                label = strings.btnSuspend,
                onClick = { onSuspendRequest() },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f),
                enabled = isConnected
            )
        }
        Gap(8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HyprButton(
                label = strings.btnScreenshot,
                onClick = { onPcAction("screenshot") },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f),
                enabled = isConnected
            )
            HyprButton(
                label = strings.btnScreenshotArea,
                onClick = { onPcAction("screenshot_area") },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1f),
                enabled = isConnected
            )
        }
        Gap(8.dp)
        val shortcutLabel = customShortcutCmd.ifBlank { "EXEC KITTY" }
        val shortcutCmdToRun = customShortcutCmd.ifBlank { "exec kitty" }
        HyprButton(
            label = shortcutLabel,
            onClick = { onLaunchApp(shortcutCmdToRun) },
            kind = HyprButtonKind.Ghost,
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnected
        )

        if (isConnected && pcShortcuts.isNotEmpty()) {
            Gap(8.dp)
            Subhead(num = "03", title = strings.sectionPcShortcuts, trailing = "SHORTCUTS")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pcShortcuts.forEach { shortcut ->
                    key(shortcut.id) {
                        HyprButton(
                            label = shortcut.label.uppercase(),
                            onClick = { onRunShortcut(shortcut.id) },
                            kind = HyprButtonKind.Ghost,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isConnected && runningShortcutId == null
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
