// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

enum class DragButton(val wireName: String) {
    LEFT("left"),
    MIDDLE("middle"),
    RIGHT("right")
}

sealed class TouchpadAction {
    data class SendButton(val button: DragButton, val down: Boolean) : TouchpadAction()
    data class SendMove(val dx: Float, val dy: Float) : TouchpadAction()
    object VibrateFeedback : TouchpadAction()
}

class TouchpadDragStateMachine(
    val doubleTapTimeoutMs: Long = 300L
) {
    private val heldButtons = mutableSetOf<DragButton>()
    var isToggleDragActive: Boolean = false
        private set
    var isGestureDragActive: Boolean = false
        private set

    private var lastTapUpTimeMs: Long = 0L

    fun onButtonPointerDown(button: DragButton): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        if (heldButtons.add(button)) {
            actions.add(TouchpadAction.SendButton(button, down = true))
        }
        return actions
    }

    fun onButtonPointerUp(button: DragButton): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        if (heldButtons.remove(button)) {
            if (!(button == DragButton.LEFT && (isToggleDragActive || isGestureDragActive))) {
                actions.add(TouchpadAction.SendButton(button, down = false))
            }
        }
        return actions
    }

    fun onTouchpadPointerDown(currentTimeMs: Long): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        if (lastTapUpTimeMs > 0L && currentTimeMs - lastTapUpTimeMs <= doubleTapTimeoutMs) {
            isGestureDragActive = true
            actions.add(TouchpadAction.SendButton(DragButton.LEFT, down = true))
            actions.add(TouchpadAction.VibrateFeedback)
        }
        return actions
    }

    fun onTouchpadMove(dx: Float, dy: Float): List<TouchpadAction> {
        return listOf(TouchpadAction.SendMove(dx, dy))
    }

    fun onTouchpadPointerUp(currentTimeMs: Long): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        if (isGestureDragActive) {
            isGestureDragActive = false
            lastTapUpTimeMs = 0L
            if (!isToggleDragActive && !heldButtons.contains(DragButton.LEFT)) {
                actions.add(TouchpadAction.SendButton(DragButton.LEFT, down = false))
            }
        } else {
            lastTapUpTimeMs = currentTimeMs
        }
        return actions
    }

    fun toggleDragMode(): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        isToggleDragActive = !isToggleDragActive
        if (isToggleDragActive) {
            actions.add(TouchpadAction.SendButton(DragButton.LEFT, down = true))
            actions.add(TouchpadAction.VibrateFeedback)
        } else {
            if (!heldButtons.contains(DragButton.LEFT) && !isGestureDragActive) {
                actions.add(TouchpadAction.SendButton(DragButton.LEFT, down = false))
            }
        }
        return actions
    }

    fun reset(): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        val hadLeft = heldButtons.contains(DragButton.LEFT) || isToggleDragActive || isGestureDragActive
        val hadMiddle = heldButtons.contains(DragButton.MIDDLE)
        val hadRight = heldButtons.contains(DragButton.RIGHT)

        heldButtons.clear()
        isToggleDragActive = false
        isGestureDragActive = false
        lastTapUpTimeMs = 0L

        if (hadLeft) actions.add(TouchpadAction.SendButton(DragButton.LEFT, down = false))
        if (hadMiddle) actions.add(TouchpadAction.SendButton(DragButton.MIDDLE, down = false))
        if (hadRight) actions.add(TouchpadAction.SendButton(DragButton.RIGHT, down = false))

        return actions
    }

    fun isLeftHeld(): Boolean = heldButtons.contains(DragButton.LEFT) || isToggleDragActive || isGestureDragActive
    fun isMiddleHeld(): Boolean = heldButtons.contains(DragButton.MIDDLE)
    fun isRightHeld(): Boolean = heldButtons.contains(DragButton.RIGHT)
}
