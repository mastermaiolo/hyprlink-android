// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import com.example.ui.theme.Hypr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PcBatteryLogicTest {

    private val stringsPt = STRINGS_PT_PT
    private val stringsEn = STRINGS_EN_UK
    private val stringsZh = STRINGS_ZH_CN

    @Test
    fun testDisconnectedReturnsNull() {
        val battery = PcBattery(level = 80, charging = false, plugged = true, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = false, strings = stringsPt)
        assertNull(result)
    }

    @Test
    fun testNullBatteryReturnsNull() {
        val result = PcBatteryLogic.pcBatteryLine(null, isConnected = true, strings = stringsPt)
        assertNull(result)
    }

    @Test
    fun testNotPresentBatteryReturnsNull() {
        val battery = PcBattery(level = 0, charging = false, plugged = false, present = false)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNull(result)
    }

    @Test
    fun testUsingBatteryNormal() {
        val battery = PcBattery(level = 64, charging = false, plugged = false, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("BATERIA 64 %", result?.text)
        assertEquals(Hypr.Sub, result?.color)
    }

    @Test
    fun testUsingBatteryWarningBelow20() {
        val battery = PcBattery(level = 15, charging = false, plugged = false, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("BATERIA 15 %", result?.text)
        assertEquals(Hypr.LinkWait, result?.color)
    }

    @Test
    fun testUsingBatteryCriticalBelow10() {
        val battery = PcBattery(level = 8, charging = false, plugged = false, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("BATERIA 8 %", result?.text)
        assertEquals(Hypr.Hot, result?.color)
    }

    @Test
    fun testCharging() {
        val battery = PcBattery(level = 82, charging = true, plugged = true, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("BATERIA 82 % · A CARREGAR", result?.text)
        assertEquals(Hypr.Acid, result?.color)
    }

    @Test
    fun testFullChargeOnCable() {
        val battery = PcBattery(level = 100, charging = false, plugged = true, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("LIGADO À CORRENTE", result?.text)
        assertEquals(Hypr.Acid, result?.color)
    }

    @Test
    fun testFullChargeEvenIfChargingTrue() {
        val battery = PcBattery(level = 100, charging = true, plugged = true, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("LIGADO À CORRENTE", result?.text)
        assertEquals(Hypr.Acid, result?.color)
    }

    @Test
    fun testPluggedNotChargingBelow100() {
        val battery = PcBattery(level = 80, charging = false, plugged = true, present = true)
        val result = PcBatteryLogic.pcBatteryLine(battery, isConnected = true, strings = stringsPt)
        assertNotNull(result)
        assertEquals("BATERIA 80 % · LIGADO À CORRENTE", result?.text)
        assertEquals(Hypr.Acid, result?.color)
    }

    @Test
    fun testEnglishStrings() {
        val charging = PcBattery(level = 82, charging = true, plugged = true, present = true)
        val resCharging = PcBatteryLogic.pcBatteryLine(charging, isConnected = true, strings = stringsEn)
        assertEquals("BATTERY 82 % · CHARGING", resCharging?.text)
        assertEquals(Hypr.Acid, resCharging?.color)

        val full = PcBattery(level = 100, charging = false, plugged = true, present = true)
        val resFull = PcBatteryLogic.pcBatteryLine(full, isConnected = true, strings = stringsEn)
        assertEquals("PLUGGED IN", resFull?.text)

        val discharging = PcBattery(level = 64, charging = false, plugged = false, present = true)
        val resDischarging = PcBatteryLogic.pcBatteryLine(discharging, isConnected = true, strings = stringsEn)
        assertEquals("BATTERY 64 %", resDischarging?.text)
    }

    @Test
    fun testChineseStrings() {
        val charging = PcBattery(level = 82, charging = true, plugged = true, present = true)
        val resCharging = PcBatteryLogic.pcBatteryLine(charging, isConnected = true, strings = stringsZh)
        assertEquals("电池 82 % · 正在充电", resCharging?.text)
        assertEquals(Hypr.Acid, resCharging?.color)

        val full = PcBattery(level = 100, charging = false, plugged = true, present = true)
        val resFull = PcBatteryLogic.pcBatteryLine(full, isConnected = true, strings = stringsZh)
        assertEquals("已连接电源", resFull?.text)
    }
}
