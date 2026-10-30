// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ConnectionRepository
import com.example.LanguageManager
import com.example.LocalAppStrings
import com.example.TerminalLogTranslator
import com.example.ui.kit.*
import com.example.ui.theme.Hypr
import com.example.ui.theme.PlexMono

/**
 * Full Journal Screen corresponding to mockups/11-diario.png.
 */
@Composable
fun FullJournalScreen(
    terminalLogs: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang = LanguageManager.currentLanguage.value

    var selectedFilter by remember(strings) { mutableStateOf(strings.filterAll) }
    var searchQuery by remember { mutableStateOf("") }
    var showTechnicalDetail by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filterOptions = listOf(strings.filterAll, strings.filterTxRx, strings.filterWarnings, strings.filterErrors)

    val rawLogs = remember(terminalLogs) { terminalLogs }

    val filteredLogs = remember(rawLogs, selectedFilter, searchQuery, showTechnicalDetail) {
        rawLogs.filter { line ->
            if (!showTechnicalDetail) {
                val isRoutine = line.contains("Incoming stream packet type:", true) ||
                        line.contains("Outgoing packet type: keepalive", true) ||
                        line.contains("Outgoing packet type: ping", true) ||
                        line.contains("Incoming stream packet type: ping", true) ||
                        line.contains("Audio sink list updated", true) ||
                        line.contains("Received workstation event:", true)
                if (isRoutine) return@filter false
            }
            val matchesFilter = when (selectedFilter) {
                strings.filterTxRx -> line.contains("tx", true) || line.contains("rx", true)
                strings.filterWarnings -> line.contains("warn", true) || line.contains("aviso", true) || line.contains("!!", true)
                strings.filterErrors -> line.contains("error", true) || line.contains("erro", true) || line.contains("fail", true) || line.contains("er ", true)
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                line.contains(searchQuery.trim(), ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom as new items arrive, unless user has scrolled up
    val isScrolledToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || lastVisible >= total - 2
        }
    }

    LaunchedEffect(Unit) {
        if (filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty() && isScrolledToBottom) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .padding(horizontal = HyprSpace.Gutter, vertical = 12.dp)
    ) {
        ScreenHeader(
            title = strings.journalHeadline,
            onBack = onBack
        )

        Gap(16.dp)

        // Technical detail switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Mono(strings.technicalDetail.uppercase(), color = Hypr.Sub, size = 11.sp)
            HyprSwitch(
                checked = showTechnicalDetail,
                onCheckedChange = { showTechnicalDetail = it }
            )
        }

        // Filters: HyprChip TUDO · TX/RX · AVISOS · ERROS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filterOptions.forEach { filterName ->
                HyprChip(
                    label = filterName,
                    selected = selectedFilter == filterName,
                    onClick = { selectedFilter = filterName }
                )
            }
        }

        Gap(12.dp)

        // Search field: Mono with 1 dp border
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = Hypr.Paper
            ),
            cursorBrush = SolidColor(Hypr.Acid),
            modifier = Modifier
                .fillMaxWidth()
                .background(Hypr.Ink0, RectangleShape)
                .border(1.dp, Hypr.Muted, RectangleShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Mono(strings.filterByText.uppercase() + "...", color = Hypr.Muted, size = 12.sp)
                        }
                        innerTextField()
                    }
                    if (searchQuery.isNotEmpty()) {
                        Kicker(
                            text = "✕",
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { searchQuery = "" }
                        )
                    }
                }
            }
        )

        Gap(12.dp)

        // LazyColumn with all lines
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Mono(if (rawLogs.isEmpty()) strings.journalEmptySession else strings.noRecordsFound, color = Hypr.Muted, size = 12.sp)
                    }
                }
            } else {
                items(filteredLogs) { logLine ->
                    val translated = TerminalLogTranslator.translate(logLine, currentLang, isTechnical = showTechnicalDetail)
                    val color = when {
                        translated.contains("erro", true) || translated.contains("fail", true) || translated.contains("er ", true) -> Hypr.Hot
                        translated.contains("warn", true) || translated.contains("!!", true) -> Hypr.LinkWait
                        translated.contains("tx", true) -> Hypr.Acid
                        translated.contains("rx", true) -> Hypr.Cold
                        else -> Hypr.Sub
                    }

                    // Format: timestamp in Hypr.Faint, message body in category color
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
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Gap(12.dp)

        // Rodapé: COPIAR TUDO (Ghost) · PARTILHAR (Ghost) · LIMPAR (Danger)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HyprButton(
                label = strings.btnCopyAll,
                onClick = {
                    val fullText = ConnectionRepository.getFullLogText()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("HyprLink Journal", fullText))
                    Toast.makeText(context, strings.toastJournalCopied, Toast.LENGTH_SHORT).show()
                },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1.2f)
            )

            HyprButton(
                label = strings.btnShare,
                onClick = {
                    val fullText = ConnectionRepository.getFullLogText()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TITLE, "hyprlink-diario.txt")
                        putExtra(Intent.EXTRA_TEXT, fullText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, strings.shareJournalTitle)
                    context.startActivity(shareIntent)
                },
                kind = HyprButtonKind.Ghost,
                modifier = Modifier.weight(1.1f)
            )

            HyprButton(
                label = strings.clear.uppercase(),
                onClick = {
                    showClearConfirmDialog = true
                },
                kind = HyprButtonKind.Danger,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showClearConfirmDialog) {
        Dialog(onDismissRequest = { showClearConfirmDialog = false }) {
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
                        Kicker(strings.confirm.uppercase(), color = Hypr.Hot, strong = true)
                        Kicker(
                            text = strings.btnClose,
                            color = Hypr.Sub,
                            modifier = Modifier.clickable { showClearConfirmDialog = false }
                        )
                    }
                    Rule(color = Hypr.Paper, thickness = 2.dp)
                    Headline(strings.cleanJournalConfirmTitle, size = 26.sp)
                    Deck(strings.cleanJournalConfirmDeck, small = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HyprButton(
                            label = strings.cancel.uppercase(),
                            onClick = { showClearConfirmDialog = false },
                            modifier = Modifier.weight(1f),
                            kind = HyprButtonKind.Ghost
                        )
                        HyprButton(
                            label = strings.clear.uppercase(),
                            onClick = {
                                ConnectionRepository.clearLogs()
                                showClearConfirmDialog = false
                                Toast.makeText(context, strings.toastJournalCleared, Toast.LENGTH_SHORT).show()
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
