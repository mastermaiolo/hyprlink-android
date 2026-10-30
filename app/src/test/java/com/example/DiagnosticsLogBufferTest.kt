// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsLogBufferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMemoryRingBufferCapacity() {
        val testFile = File(tempFolder.root, "test.log")
        val buffer = DiagnosticsLogBuffer(
            logFileProvider = { testFile },
            maxMemoryEntries = 5,
            batchFlushThreshold = 50,
            flushIntervalMs = 100_000L
        )

        for (i in 1..10) {
            buffer.append("line-$i", "line-$i", isImmediatePriority = false)
        }

        val memoryLogs = buffer.getMemoryLogs()
        assertEquals(5, memoryLogs.size)
        assertEquals("line-6", memoryLogs.first())
        assertEquals("line-10", memoryLogs.last())
        buffer.stop()
    }

    @Test
    fun testBatchFlushThreshold() {
        val testFile = File(tempFolder.root, "batch_test.log")
        val buffer = DiagnosticsLogBuffer(
            logFileProvider = { testFile },
            maxMemoryEntries = 100,
            batchFlushThreshold = 10,
            flushIntervalMs = 100_000L
        )

        for (i in 1..9) {
            buffer.append("msg $i", "msg $i", isImmediatePriority = false)
        }
        // Menos de 10 itens: deve estar pendente na fila
        assertEquals(9, buffer.getPendingDiskQueueSize())

        // 10.º item dispara o flush do lote
        buffer.append("msg 10", "msg 10", isImmediatePriority = false)
        buffer.flushInternal()

        var attempts = 0
        while ((!testFile.exists() || testFile.length() == 0L) && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        assertEquals(0, buffer.getPendingDiskQueueSize())
        assertTrue(testFile.exists())
        val lines = testFile.readLines()
        assertEquals(10, lines.size)
        buffer.stop()
    }

    @Test
    fun testImmediateFlushOnErrorOrWarning() {
        val testFile = File(tempFolder.root, "error_test.log")
        val buffer = DiagnosticsLogBuffer(
            logFileProvider = { testFile },
            maxMemoryEntries = 100,
            batchFlushThreshold = 50,
            flushIntervalMs = 100_000L
        )

        buffer.append("normal info", "normal info", isImmediatePriority = false)
        assertEquals(1, buffer.getPendingDiskQueueSize())

        // Mensagem de erro deve acionar flush de imediato
        buffer.append("failure [ERR]", "failure [ERR]", isImmediatePriority = false)
        buffer.flushInternal()

        var attempts = 0
        while ((!testFile.exists() || testFile.length() == 0L) && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        assertEquals(0, buffer.getPendingDiskQueueSize())
        assertTrue(testFile.exists())
        val lines = testFile.readLines()
        assertEquals(2, lines.size)
        buffer.stop()
    }

    @Test
    fun testFileRotationOverLimit() {
        val testFile = File(tempFolder.root, "rotate_test.log")
        // Limite baixo de 200 bytes para testar rotação
        val buffer = DiagnosticsLogBuffer(
            logFileProvider = { testFile },
            maxMemoryEntries = 100,
            batchFlushThreshold = 5,
            flushIntervalMs = 100_000L,
            maxFileSizeBytes = 100L
        )

        val longText = "A".repeat(40)
        for (i in 1..10) {
            buffer.append(longText, longText, isImmediatePriority = true)
        }
        buffer.flushInternal()

        assertTrue(testFile.exists())
        assertTrue(testFile.length() > 0)
        buffer.stop()
    }
}
