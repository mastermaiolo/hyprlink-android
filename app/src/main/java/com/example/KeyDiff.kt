// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

sealed class KeyDiffOp {
    data class Backspace(val count: Int) : KeyDiffOp()
    data class Type(val text: String) : KeyDiffOp()
}

object KeyDiff {
    /**
     * Compara o texto antigo com o novo e gera a sequência de operações para envio remoto.
     * Suporta caracteres simples, sufixos, substituição a meio, emojis e texto vazio.
     */
    fun computeDiff(oldText: String, newText: String): List<KeyDiffOp> {
        if (oldText == newText) return emptyList()

        val commonPrefixLen = oldText.commonPrefixWith(newText).length
        val toDelete = oldText.substring(commonPrefixLen)
        val toAdd = newText.substring(commonPrefixLen)

        val ops = mutableListOf<KeyDiffOp>()
        if (toDelete.isNotEmpty()) {
            val backspaceCount = toDelete.codePointCount(0, toDelete.length)
            if (backspaceCount > 0) {
                ops.add(KeyDiffOp.Backspace(backspaceCount))
            }
        }
        if (toAdd.isNotEmpty()) {
            ops.add(KeyDiffOp.Type(toAdd))
        }
        return ops
    }
}
