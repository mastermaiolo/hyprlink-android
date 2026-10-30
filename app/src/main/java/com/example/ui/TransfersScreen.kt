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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.LocalAppStrings
import com.example.decimalComma
import com.example.ui.theme.*
import com.example.ui.kit.*

data class ActiveTransferItem(
    val id: String,
    val name: String,
    val isIncoming: Boolean,
    val currentBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val speedText: String? = null
)

data class CompletedTransferItem(
    val id: String,
    val name: String,
    val isIncoming: Boolean,
    val totalBytes: Long,
    val dateLabel: String,
    val sha256Hash: String,
    val isVerified: Boolean = true,
    val uri: android.net.Uri? = null,
    val savedNameOnPc: String? = null,
    val summaryText: String? = null
)

data class ErrorTransferItem(
    val id: String,
    val name: String,
    val isIncoming: Boolean,
    val totalBytes: Long,
    val failedPercentage: Int,
    val technicalMessage: String,
    val friendlyExplanation: String
)

@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    activeTransfers: List<ActiveTransferItem>,
    completedTransfers: List<CompletedTransferItem>,
    errorTransfers: List<ErrorTransferItem>,
    onSendFileClick: () -> Unit,
    onRetryTransfer: (String) -> Unit,
    onDeleteErrorTransfer: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenTransfer: (CompletedTransferItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    val activeList = activeTransfers
    val completedList = completedTransfers
    val errorList = errorTransfers

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyprSpace.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = strings.transfersTitle,
            onBack = onBack,
            actionLabel = strings.clear.uppercase(),
            onAction = onClearHistory
        )

        // Send File Button
        HyprButton(
            label = "+ " + strings.sendFileButton,
            onClick = onSendFileClick,
            kind = HyprButtonKind.Primary,
            modifier = Modifier.fillMaxWidth()
        )

        Gap(8.dp)

        // 01. A decorrer
        Subhead(num = "01", title = strings.transfersActive, trailing = "${activeList.size}")
        if (activeList.isEmpty()) {
            Deck(strings.transferNothingInProgress, small = true, color = Hypr.Muted)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                activeList.forEach { active ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Kicker(
                                text = if (active.isIncoming) strings.transferToPhone else strings.transferToPc,
                                color = if (active.isIncoming) Hypr.Cold else Hypr.Acid,
                                strong = true
                            )
                            Fill()
                            Text(
                                text = active.name,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Hypr.Paper),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Gap(4.dp)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${(active.progress * 100).toInt()}",
                                    style = TextStyle(fontFamily = Anton, fontSize = 44.sp, lineHeight = 0.9.em, color = Hypr.Paper)
                                )
                                Spacer(Modifier.width(2.dp))
                                Mono("%", color = Hypr.Muted, size = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Mono("${formatBytes(active.currentBytes, strings.decimalComma)} / ${formatBytes(active.totalBytes, strings.decimalComma)}", color = Hypr.Sub, size = 12.sp)
                        }
                        Gap(6.dp)
                        Bar(fraction = active.progress)
                        Gap(8.dp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Kicker(active.speedText ?: "${(active.progress * 100).toInt()} %", color = Hypr.Sub)
                            Kicker(strings.cancel.uppercase(), color = Hypr.Hot, strong = true, modifier = Modifier.clickable { onDeleteErrorTransfer(active.id) })
                        }
                        Gap(8.dp)
                        Rule()
                    }
                }
            }
        }

        Gap(8.dp)

        // 02. Concluídas
        Subhead(num = "02", title = strings.transfersCompletedSection, trailing = "${completedList.size}")
        if (completedList.isEmpty()) {
            Kicker(strings.transferNothingYetTitle)
            Gap(8.dp)
            Deck(strings.transferNothingYetDesc, small = true, color = Hypr.Muted)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                completedList.forEach { comp ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenTransfer(comp) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Kicker(
                                text = if (comp.isIncoming) strings.transferToPhone else strings.transferToPc,
                                color = if (comp.isIncoming) Hypr.Cold else Hypr.Acid,
                                strong = true
                            )
                            Fill()
                            Text(
                                text = comp.name,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Hypr.Paper),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(12.dp))
                            Mono(formatBytes(comp.totalBytes, strings.decimalComma), color = Hypr.Paper, size = 12.sp)
                        }
                        Gap(4.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (comp.isVerified) {
                                Kicker("✓ SHA-256", color = Hypr.Acid, strong = true)
                                Mono(comp.sha256Hash.ifBlank { strings.verified }, color = Hypr.Faint, size = 11.sp)
                            } else {
                                Kicker(strings.unverified, color = Hypr.LinkWait, strong = true)
                            }
                        }
                        if (comp.summaryText != null) {
                            Gap(4.dp)
                            Mono(comp.summaryText, color = Hypr.Faint, size = 11.sp)
                        }
                        if (comp.savedNameOnPc != null) {
                            Gap(4.dp)
                            Kicker(com.example.ShareErrorMapper.formatSavedOnPcAs(comp.savedNameOnPc, strings), color = Hypr.Sub, strong = false)
                        }
                        Gap(10.dp)
                        Rule()
                    }
                }
            }
        }

        // 03. Falhas
        if (errorList.isNotEmpty()) {
            Gap(8.dp)
            Subhead(num = "03", title = strings.transfersFailedSection, trailing = "${errorList.size}")
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                errorList.forEach { err ->
                    Panel(
                        background = Hypr.HotTint,
                        border = Hypr.BorderRed
                    ) {
                        Tag(strings.tagFailed, background = Hypr.Hot, color = Hypr.Void)
                        Gap(8.dp)
                        Text(
                            text = err.name,
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Hypr.Paper)
                        )
                        Gap(4.dp)
                        Mono(text = err.technicalMessage, color = Hypr.Sub, size = 11.5.sp)
                        Gap(12.dp)
                        HyprButton(
                            label = strings.retry,
                            onClick = { onRetryTransfer(err.id) },
                            kind = HyprButtonKind.Danger
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatBytes(bytes: Long, decimalComma: Boolean = true): String {
    return com.example.TransferRateFormatter.formatBytes(bytes, decimalComma)
}

