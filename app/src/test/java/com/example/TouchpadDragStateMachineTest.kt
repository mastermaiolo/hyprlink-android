// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadDragStateMachineTest {

    @Test
    fun testQuickTapIsClick() {
        val sm = TouchpadDragStateMachine()

        // 1. Pressionar botão esquerdo -> SendButton(LEFT, down = true)
        val downActions = sm.onButtonPointerDown(DragButton.LEFT)
        assertEquals(1, downActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = true), downActions[0])
        assertTrue(sm.isLeftHeld())

        // 2. Levantar dedo -> SendButton(LEFT, down = false)
        val upActions = sm.onButtonPointerUp(DragButton.LEFT)
        assertEquals(1, upActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = false), upActions[0])
        assertFalse(sm.isLeftHeld())
    }

    @Test
    fun testButtonHeldWithIndependentMove() {
        val sm = TouchpadDragStateMachine()

        // Dedo 1 segura o botão esquerdo
        val btnActions = sm.onButtonPointerDown(DragButton.LEFT)
        assertEquals(1, btnActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = true), btnActions[0])

        // Dedo 2 arrasta no touchpad de forma independente
        val moveActions1 = sm.onTouchpadMove(15f, -20f)
        assertEquals(1, moveActions1.size)
        assertEquals(TouchpadAction.SendMove(15f, -20f), moveActions1[0])

        val moveActions2 = sm.onTouchpadMove(5f, 10f)
        assertEquals(1, moveActions2.size)
        assertEquals(TouchpadAction.SendMove(5f, 10f), moveActions2[0])

        // Dedo 1 solta o botão
        val releaseActions = sm.onButtonPointerUp(DragButton.LEFT)
        assertEquals(1, releaseActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = false), releaseActions[0])
        assertFalse(sm.isLeftHeld())
    }

    @Test
    fun testTapAndHoldGestureDrag() {
        val sm = TouchpadDragStateMachine(doubleTapTimeoutMs = 300L)

        // Primeiro toque no touchpad
        sm.onTouchpadPointerDown(100L)
        sm.onTouchpadPointerUp(150L)
        assertFalse(sm.isGestureDragActive)

        // Segundo toque dentro de 300 ms (250 - 150 = 100 ms)
        val dragStartActions = sm.onTouchpadPointerDown(250L)
        assertTrue(sm.isGestureDragActive)
        assertEquals(2, dragStartActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = true), dragStartActions[0])
        assertEquals(TouchpadAction.VibrateFeedback, dragStartActions[1])

        // Movimento durante o arrasto
        val moveActions = sm.onTouchpadMove(30f, 40f)
        assertEquals(1, moveActions.size)
        assertEquals(TouchpadAction.SendMove(30f, 40f), moveActions[0])

        // Levantar o dedo -> termina arrasto e envia UP
        val dragEndActions = sm.onTouchpadPointerUp(500L)
        assertFalse(sm.isGestureDragActive)
        assertEquals(1, dragEndActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = false), dragEndActions[0])
    }

    @Test
    fun testTapTooLateDoesNotTriggerGestureDrag() {
        val sm = TouchpadDragStateMachine(doubleTapTimeoutMs = 300L)

        // Primeiro toque
        sm.onTouchpadPointerDown(100L)
        sm.onTouchpadPointerUp(150L)

        // Segundo toque após 300 ms (500 - 150 = 350 ms)
        val secondDownActions = sm.onTouchpadPointerDown(500L)
        assertTrue(secondDownActions.isEmpty())
        assertFalse(sm.isGestureDragActive)
    }

    @Test
    fun testToggleDragMode() {
        val sm = TouchpadDragStateMachine()

        // Ativar alternador
        val onActions = sm.toggleDragMode()
        assertTrue(sm.isToggleDragActive)
        assertTrue(sm.isLeftHeld())
        assertEquals(2, onActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = true), onActions[0])
        assertEquals(TouchpadAction.VibrateFeedback, onActions[1])

        // Desativar alternador
        val offActions = sm.toggleDragMode()
        assertFalse(sm.isToggleDragActive)
        assertFalse(sm.isLeftHeld())
        assertEquals(1, offActions.size)
        assertEquals(TouchpadAction.SendButton(DragButton.LEFT, down = false), offActions[0])
    }

    @Test
    fun testResetSafetyReleasesAll() {
        val sm = TouchpadDragStateMachine()

        sm.onButtonPointerDown(DragButton.MIDDLE)
        sm.onButtonPointerDown(DragButton.RIGHT)
        sm.toggleDragMode() // segurando LEFT

        assertTrue(sm.isLeftHeld())
        assertTrue(sm.isMiddleHeld())
        assertTrue(sm.isRightHeld())

        // Reset geral (perda de conexão, sair do ecrã, background)
        val resetActions = sm.reset()
        assertFalse(sm.isLeftHeld())
        assertFalse(sm.isMiddleHeld())
        assertFalse(sm.isRightHeld())
        assertFalse(sm.isToggleDragActive)

        // Deve soltar os botões premidos
        assertTrue(resetActions.contains(TouchpadAction.SendButton(DragButton.LEFT, down = false)))
        assertTrue(resetActions.contains(TouchpadAction.SendButton(DragButton.MIDDLE, down = false)))
        assertTrue(resetActions.contains(TouchpadAction.SendButton(DragButton.RIGHT, down = false)))
    }
}
