// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import kotlinx.coroutines.*
import java.io.File

class DiagnosticsLogBuffer(
    private val logFileProvider: () -> File?,
    private val maxMemoryEntries: Int = 1000,
    private val batchFlushThreshold: Int = 50,
    private val flushIntervalMs: Long = 3000L,
    private val maxFileSizeBytes: Long = 5 * 1024 * 1024L, // 5 MB
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val memoryRing = ArrayDeque<String>(maxMemoryEntries)
    private val lock = Any()

    private val diskQueue = ArrayDeque<String>()
    private var scheduledFlushJob: Job? = null

    init {
        startFlushTimer()
    }

    private fun startFlushTimer() {
        scheduledFlushJob = coroutineScope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flushInternal()
            }
        }
    }

    fun append(formattedMsg: String, enMsg: String, isImmediatePriority: Boolean = false) {
        var shouldFlushImmediately = isImmediatePriority

        if (!shouldFlushImmediately) {
            if (enMsg.contains("[ERR]", ignoreCase = true) ||
                enMsg.contains("[WARN]", ignoreCase = true) ||
                enMsg.contains("[ERROR]", ignoreCase = true) ||
                formattedMsg.contains("[ERR]", ignoreCase = true) ||
                formattedMsg.contains("[WARN]", ignoreCase = true)
            ) {
                shouldFlushImmediately = true
            }
        }

        var queueSize = 0
        synchronized(lock) {
            if (memoryRing.size >= maxMemoryEntries) {
                memoryRing.removeFirst()
            }
            memoryRing.addLast(formattedMsg)

            diskQueue.addLast(enMsg)
            queueSize = diskQueue.size
        }

        if (shouldFlushImmediately || queueSize >= batchFlushThreshold) {
            coroutineScope.launch {
                flushInternal()
            }
        }
    }

    fun getMemoryLogs(): List<String> {
        synchronized(lock) {
            return ArrayList(memoryRing)
        }
    }

    fun getPendingDiskQueueSize(): Int {
        synchronized(lock) {
            return diskQueue.size
        }
    }

    fun clear() {
        synchronized(lock) {
            memoryRing.clear()
            diskQueue.clear()
        }
        coroutineScope.launch {
            try {
                val file = logFileProvider()
                if (file != null && file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    fun flushInternal() {
        val linesToWrite = mutableListOf<String>()
        synchronized(lock) {
            if (diskQueue.isEmpty()) return
            while (diskQueue.isNotEmpty()) {
                linesToWrite.add(diskQueue.removeFirst())
            }
        }

        if (linesToWrite.isEmpty()) return

        val file = logFileProvider() ?: return
        try {
            rotateFileIfNeeded(file)
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            file.appendText(linesToWrite.joinToString("\n", postfix = "\n"))
        } catch (e: Exception) {
            try {
                android.util.Log.e("HyprLink", "Failed to flush diagnostics log", e)
            } catch (_: Throwable) {}
        }
    }

    private fun rotateFileIfNeeded(file: File) {
        if (!file.exists()) return
        if (file.length() > maxFileSizeBytes) {
            val lines = file.readLines()
            if (lines.size > 1000) {
                val kept = lines.drop(lines.size / 2)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun stop() {
        scheduledFlushJob?.cancel()
        flushInternal()
    }
}
