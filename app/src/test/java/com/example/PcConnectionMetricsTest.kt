// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import com.example.ui.PcConnectionMetrics
import org.junit.Assert.*
import org.junit.Test

class PcConnectionMetricsTest {

    @Test
    fun testDefaultMetrics() {
        val metrics = PcConnectionMetrics()
        assertNull(metrics.cpuUsage)
        assertNull(metrics.ramUsedGB)
        assertNull(metrics.ramTotalGB)
        assertNull(metrics.tempCelsius)
        assertNull(metrics.batteryPercent)
        assertNull(metrics.pingMs)
        assertFalse(metrics.isConnected)
        assertFalse(metrics.isAudioStreaming)
        assertEquals("hyprlink/1", metrics.protocolVersion)
        assertNull(metrics.hostname)
        assertNull(metrics.uptimeSeconds)
    }

    @Test
    fun testAllFieldsNull() {
        val metrics = PcConnectionMetrics(
            cpuUsage = null,
            ramUsedGB = null,
            ramTotalGB = null,
            tempCelsius = null,
            batteryPercent = null,
            pingMs = null,
            isConnected = false,
            isAudioStreaming = false,
            protocolVersion = "hyprlink/1",
            hostname = null,
            uptimeSeconds = null
        )
        assertNull(metrics.cpuUsage)
        assertNull(metrics.ramUsedGB)
        assertNull(metrics.ramTotalGB)
        assertNull(metrics.tempCelsius)
        assertNull(metrics.batteryPercent)
        assertNull(metrics.pingMs)
        assertNull(metrics.hostname)
        assertNull(metrics.uptimeSeconds)
        assertFalse(metrics.isConnected)
    }

    @Test
    fun testCustomMetricsStateHoisting() {
        val metrics = PcConnectionMetrics(
            cpuUsage = 42.5f,
            ramUsedGB = 12.4f,
            ramTotalGB = 32.0f,
            tempCelsius = 68.0f,
            batteryPercent = 85,
            pingMs = 3.2f,
            isConnected = true,
            isAudioStreaming = true,
            protocolVersion = "hyprlink/1",
            hostname = "arch-workstation",
            uptimeSeconds = 3600L
        )
        assertTrue(metrics.isConnected)
        assertTrue(metrics.isAudioStreaming)
        assertEquals(42.5f, metrics.cpuUsage!!, 0.01f)
        assertEquals(12.4f, metrics.ramUsedGB!!, 0.01f)
        assertEquals(32.0f, metrics.ramTotalGB!!, 0.01f)
        assertEquals(68.0f, metrics.tempCelsius!!, 0.01f)
        assertEquals(85, metrics.batteryPercent)
        assertEquals(3.2f, metrics.pingMs!!, 0.01f)
        assertEquals("arch-workstation", metrics.hostname)
        assertEquals(3600L, metrics.uptimeSeconds)
        assertEquals("hyprlink/1", metrics.protocolVersion)
    }

    @Test
    fun testCopyAndMutation() {
        val original = PcConnectionMetrics(cpuUsage = 20.0f, isConnected = true)
        val updated = original.copy(cpuUsage = 55.0f, isAudioStreaming = true)

        assertEquals(20.0f, original.cpuUsage!!, 0.01f)
        assertFalse(original.isAudioStreaming)

        assertEquals(55.0f, updated.cpuUsage!!, 0.01f)
        assertTrue(updated.isAudioStreaming)
        assertTrue(updated.isConnected)
    }
}
