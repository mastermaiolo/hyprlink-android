// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.LocalAppStrings
import com.example.ui.theme.*
import com.example.ui.kit.*

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import com.example.TouchpadDragStateMachine
import com.example.DragButton
import com.example.TouchpadAction
import com.example.GestureLogic

@Composable
fun TouchpadScreen(
    deviceName: String,
    onMouseMove: (Float, Float) -> Unit,
    onMouseClick: (Int) -> Unit, // 1: left, 2: middle, 3: right
    onScroll: (Float, Float) -> Unit,
    onSendKey: (String) -> Unit,
    onSendText: (String) -> Unit,
    onToggleKeyboardDialog: () -> Unit,
    isConnected: Boolean = true,
    onMouseButton: (String, Boolean) -> Unit = { _, _ -> },
    onGesture: (String, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }
    var showInstructions by remember { mutableStateOf(false) }
    var hasMovedOnce by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isKeyboardVisible by remember { mutableStateOf(false) }

    val hapticFeedback = LocalHapticFeedback.current
    val stateMachine = remember { TouchpadDragStateMachine() }
    var isToggleDragActive by remember { mutableStateOf(false) }
    var isLeftHeld by remember { mutableStateOf(false) }
    var isMiddleHeld by remember { mutableStateOf(false) }
    var isRightHeld by remember { mutableStateOf(false) }

    fun syncButtonStates() {
        isToggleDragActive = stateMachine.isToggleDragActive
        isLeftHeld = stateMachine.isLeftHeld()
        isMiddleHeld = stateMachine.isMiddleHeld()
        isRightHeld = stateMachine.isRightHeld()
    }

    fun dispatchActions(actions: List<TouchpadAction>) {
        actions.forEach { act ->
            when (act) {
                is TouchpadAction.SendButton -> onMouseButton(act.button.wireName, act.down)
                is TouchpadAction.SendMove -> onMouseMove(act.dx, act.dy)
                is TouchpadAction.VibrateFeedback -> hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        syncButtonStates()
    }

    // Segurança: se perder conexão, soltar todos os botões
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            dispatchActions(stateMachine.reset())
        }
    }

    // Segurança: ao sair do ecrã ou entrar em background/pausa
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                dispatchActions(stateMachine.reset())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dispatchActions(stateMachine.reset())
        }
    }

    val toggleKeyboard = {
        if (isKeyboardVisible) {
            keyboardController?.hide()
            isKeyboardVisible = false
        } else {
            focusRequester.requestFocus()
            keyboardController?.show()
            isKeyboardVisible = true
        }
        onToggleKeyboardDialog()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .padding(horizontal = HyprSpace.Gutter, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            title = strings.touchpadTitle,
            actionLabel = if (isKeyboardVisible) strings.actionKeyboardClose else strings.actionKeyboardOpen,
            onAction = toggleKeyboard
        )

        // Touchpad Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Hypr.Ink0, RectangleShape)
                .border(1.dp, Hypr.Line, RectangleShape)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    drawLine(Hypr.Line, Offset(w / 2f, 0f), Offset(w / 2f, h), 1.dp.toPx())
                    drawLine(Hypr.Line, Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx())
                }
                .pointerInput(isConnected) {
                    if (!isConnected) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val now = System.currentTimeMillis()
                        cursorPosition = down.position
                        if (!hasMovedOnce) {
                            hasMovedOnce = true
                            showInstructions = false
                        }

                        dispatchActions(stateMachine.onTouchpadPointerDown(now))

                        val pointerId = down.id
                        var hasMoved = false
                        var threeFingersStartX: Float? = null
                        var threeFingersTriggered = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }

                            // Gesto de 3 dedos (Prompt 29): limiar >= 80 dp
                            if (pressedChanges.size == 3) {
                                val avgX = (pressedChanges[0].position.x + pressedChanges[1].position.x + pressedChanges[2].position.x) / 3f
                                if (threeFingersStartX == null) {
                                    threeFingersStartX = avgX
                                } else if (!threeFingersTriggered) {
                                    val deltaXDp = (avgX - threeFingersStartX) / density
                                    val gesture = GestureLogic.evaluate3FingerSwipe(3, deltaXDp)
                                    if (gesture != null) {
                                        threeFingersTriggered = true
                                        onGesture(gesture, null)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                                pressedChanges.forEach { it.consume() }
                            } else if (pressedChanges.size < 3) {
                                threeFingersStartX = null
                            }

                            val change = event.changes.find { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                cursorPosition = null
                                val upTime = System.currentTimeMillis()
                                val upActions = stateMachine.onTouchpadPointerUp(upTime)
                                dispatchActions(upActions)

                                if (!hasMoved && !threeFingersTriggered && !stateMachine.isGestureDragActive && !stateMachine.isToggleDragActive && !stateMachine.isLeftHeld()) {
                                    onMouseClick(1)
                                }
                                break
                            }

                            val dragAmount = change.positionChange()
                            if (dragAmount.x != 0f || dragAmount.y != 0f) {
                                hasMoved = true
                                cursorPosition = change.position
                                change.consume()
                                dispatchActions(stateMachine.onTouchpadMove(dragAmount.x, dragAmount.y))
                            }
                        }
                    }
                }
        ) {
            Kicker(strings.navMouse, color = Hypr.Muted, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            if (!isConnected) {
                Mono(strings.needsPcConnection, color = Hypr.Muted, size = 11.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                Kicker(strings.touchpadHint, color = Hypr.Faint, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }

            cursorPosition?.let { pos ->
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((pos.x - 6).toInt(), (pos.y - 6).toInt()) }
                        .size(12.dp)
                        .border(1.dp, Hypr.Acid, RectangleShape)
                        .background(Color.Transparent, RectangleShape)
                )
            }
        }

        // Mouse buttons: ESQUERDO · MEIO · DIREITO · ARRASTAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HoldableMouseButton(
                label = strings.mouseLeft,
                isHeld = isLeftHeld,
                onDown = { dispatchActions(stateMachine.onButtonPointerDown(DragButton.LEFT)) },
                onUp = { dispatchActions(stateMachine.onButtonPointerUp(DragButton.LEFT)) },
                modifier = Modifier.weight(1f),
                enabled = isConnected
            )
            HoldableMouseButton(
                label = strings.mouseMiddle,
                isHeld = isMiddleHeld,
                onDown = { dispatchActions(stateMachine.onButtonPointerDown(DragButton.MIDDLE)) },
                onUp = { dispatchActions(stateMachine.onButtonPointerUp(DragButton.MIDDLE)) },
                modifier = Modifier.weight(0.7f),
                enabled = isConnected
            )
            HoldableMouseButton(
                label = strings.mouseRight,
                isHeld = isRightHeld,
                onDown = { dispatchActions(stateMachine.onButtonPointerDown(DragButton.RIGHT)) },
                onUp = { dispatchActions(stateMachine.onButtonPointerUp(DragButton.RIGHT)) },
                modifier = Modifier.weight(1f),
                enabled = isConnected
            )
            HyprButton(
                label = if (isToggleDragActive) strings.btnDragging else strings.btnDrag,
                onClick = { dispatchActions(stateMachine.toggleDragMode()) },
                kind = if (isToggleDragActive) HyprButtonKind.Primary else HyprButtonKind.Ghost,
                modifier = Modifier.weight(1.1f),
                enabled = isConnected
            )
        }

        Gap(6.dp)

        // 01. Teclado
        val context = androidx.compose.ui.platform.LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("hyprlink_prefs", android.content.Context.MODE_PRIVATE) }
        var isLiveMode by remember { mutableStateOf(sharedPrefs.getBoolean("keyboard_live_mode", true)) }
        var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
        var lastSyncedText by remember { mutableStateOf("") }

        Subhead(
            num = "01",
            title = strings.keyboardTitle,
            trailingAction = {
                HyprChip(
                    label = if (isLiveMode) strings.keyboardModeLive else strings.keyboardModeEnter,
                    selected = isLiveMode,
                    onClick = {
                        val newMode = !isLiveMode
                        isLiveMode = newMode
                        sharedPrefs.edit().putBoolean("keyboard_live_mode", newMode).apply()
                        lastSyncedText = textFieldValue.text
                    }
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(Hypr.Ink0, RectangleShape)
                    .border(1.dp, Hypr.Muted, RectangleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        if (isLiveMode) {
                            if (newValue.composition == null) {
                                val newText = newValue.text
                                val diffOps = com.example.KeyDiff.computeDiff(lastSyncedText, newText)
                                for (op in diffOps) {
                                    when (op) {
                                        is com.example.KeyDiffOp.Backspace -> {
                                            repeat(op.count) { onSendKey("backspace") }
                                        }
                                        is com.example.KeyDiffOp.Type -> {
                                            onSendText(op.text)
                                        }
                                    }
                                }
                                lastSyncedText = newText
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            if (isLiveMode) {
                                onSendKey("enter")
                                textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                                lastSyncedText = ""
                            } else {
                                if (textFieldValue.text.isNotEmpty()) {
                                    onSendText(textFieldValue.text)
                                    textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                                    lastSyncedText = ""
                                } else {
                                    onSendKey("enter")
                                }
                            }
                        }
                    ),
                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp, color = Hypr.Paper),
                    decorationBox = { innerTextField ->
                        if (textFieldValue.text.isEmpty()) {
                            Mono(strings.typeToPc, color = Hypr.Muted, size = 13.sp)
                        }
                        innerTextField()
                    }
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Hypr.Acid, RectangleShape)
                    .clickable {
                        if (isLiveMode) {
                            onSendKey("enter")
                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                            lastSyncedText = ""
                        } else {
                            if (textFieldValue.text.isNotEmpty()) {
                                onSendText(textFieldValue.text)
                                textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                                lastSyncedText = ""
                            } else {
                                onSendKey("enter")
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("↵", fontSize = 18.sp, color = Hypr.Void, fontWeight = FontWeight.Bold)
            }
        }

        Gap(6.dp)

        // Special keys in grouped FlowRows (no horizontal scroll)
        data class KeyShortcut(val label: String, val keyName: String)
        val row1Keys = remember {
            listOf(
                KeyShortcut("ESC", "esc"),
                KeyShortcut("TAB", "tab"),
                KeyShortcut("⌫", "backspace"),
                KeyShortcut("DEL", "del"),
                KeyShortcut("↵", "enter")
            )
        }
        val row2Keys = remember {
            listOf(
                KeyShortcut("←", "left"),
                KeyShortcut("↑", "up"),
                KeyShortcut("↓", "down"),
                KeyShortcut("→", "right"),
                KeyShortcut("HOME", "home"),
                KeyShortcut("END", "end"),
                KeyShortcut("PGUP", "pageup"),
                KeyShortcut("PGDN", "pagedown")
            )
        }
        val row3Keys = remember {
            listOf(
                KeyShortcut("SUPER", "super"),
                KeyShortcut("CTRL", "ctrl"),
                KeyShortcut("ALT", "alt")
            )
        }

        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row1Keys.forEach { item ->
                    HyprChip(
                        label = item.label,
                        selected = false,
                        onClick = { onSendKey(item.keyName) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row2Keys.forEach { item ->
                    HyprChip(
                        label = item.label,
                        selected = false,
                        onClick = { onSendKey(item.keyName) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row3Keys.forEach { item ->
                    HyprChip(
                        label = item.label,
                        selected = false,
                        onClick = { onSendKey(item.keyName) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun HoldableMouseButton(
    label: String,
    isHeld: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bg = if (isHeld) Hypr.Ink1 else Color.Transparent
    val line = if (isHeld) Hypr.Acid else (if (enabled) Hypr.Muted else Hypr.Line)
    val fg = if (isHeld) Hypr.Acid else (if (enabled) Hypr.Paper else Hypr.Faint)

    Box(
        modifier = modifier
            .background(bg, RectangleShape)
            .border(1.dp, line, RectangleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onDown()
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.find { it.id == pointerId }
                        if (change == null || !change.pressed) {
                            onUp()
                            break
                        }
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            color = fg,
            maxLines = 1,
            style = TextStyle(
                fontFamily = PlexMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.08.em
            )
        )
    }
}
