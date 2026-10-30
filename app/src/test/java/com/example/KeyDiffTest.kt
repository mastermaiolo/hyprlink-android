// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyDiffTest {

    @Test
    fun testEmptyToEmpty() {
        val ops = KeyDiff.computeDiff("", "")
        assertEquals(emptyList<KeyDiffOp>(), ops)
    }

    @Test
    fun testEmptyToText() {
        val ops = KeyDiff.computeDiff("", "hello")
        assertEquals(listOf(KeyDiffOp.Type("hello")), ops)
    }

    @Test
    fun testAppendSuffix() {
        val ops = KeyDiff.computeDiff("hello", "hello world")
        assertEquals(listOf(KeyDiffOp.Type(" world")), ops)
    }

    @Test
    fun testDeleteSuffix() {
        val ops = KeyDiff.computeDiff("hello world", "hello")
        assertEquals(listOf(KeyDiffOp.Backspace(6)), ops)
    }

    @Test
    fun testTextToEmpty() {
        val ops = KeyDiff.computeDiff("hypr", "")
        assertEquals(listOf(KeyDiffOp.Backspace(4)), ops)
    }

    @Test
    fun testMiddleReplacement() {
        // "casa" -> "cama": prefixo "ca", remove "sa" (2), adiciona "ma"
        val ops = KeyDiff.computeDiff("casa", "cama")
        assertEquals(listOf(KeyDiffOp.Backspace(2), KeyDiffOp.Type("ma")), ops)
    }

    @Test
    fun testMiddleInsertion() {
        // "abcdef" -> "abcXdef": prefixo "abc", remove "def" (3), adiciona "Xdef"
        val ops = KeyDiff.computeDiff("abcdef", "abcXdef")
        assertEquals(listOf(KeyDiffOp.Backspace(3), KeyDiffOp.Type("Xdef")), ops)
    }

    @Test
    fun testEmojisAddAndDelete() {
        // "ola" -> "ola 👋": adiciona emoji
        val opsAdd = KeyDiff.computeDiff("ola", "ola 👋")
        assertEquals(listOf(KeyDiffOp.Type(" 👋")), opsAdd)

        // "ola 👋" -> "ola ": emoji tem surrogate pair em UTF-16 mas 1 code point
        val opsDel = KeyDiff.computeDiff("ola 👋", "ola ")
        assertEquals(listOf(KeyDiffOp.Backspace(1)), opsDel)

        // "🎉" -> "🎊"
        val opsSwap = KeyDiff.computeDiff("🎉", "🎊")
        assertEquals(listOf(KeyDiffOp.Backspace(1), KeyDiffOp.Type("🎊")), opsSwap)
    }

    @Test
    fun testIdenticalReturnsEmpty() {
        val ops = KeyDiff.computeDiff("terminal", "terminal")
        assertEquals(emptyList<KeyDiffOp>(), ops)
    }
}
