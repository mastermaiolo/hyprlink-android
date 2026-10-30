// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiSsidLogicTest {

    private val stringsPt = STRINGS_PT_PT
    private val stringsEn = STRINGS_EN_UK
    private val stringsZh = STRINGS_ZH_CN

    @Test
    fun testSharingDisabledReturnsNull() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "\"MinhaRede\"",
            isLocationEnabled = true,
            hasLocationPermission = true,
            shareSsid = false,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertNull(result)
    }

    @Test
    fun testNoPermissionReturnsNull() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "\"MinhaRede\"",
            isLocationEnabled = true,
            hasLocationPermission = false,
            shareSsid = true,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertNull(result)
    }

    @Test
    fun testLocationDisabledReturnsPromptInsteadOfUnknownSsid() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "<unknown ssid>",
            isLocationEnabled = false,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertEquals("Wi-Fi: ative a localização do sistema para ver o nome da rede", result)
    }

    @Test
    fun testLocationDisabledReturnsPromptEvenIfRawSsidNull() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = null,
            isLocationEnabled = false,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertEquals("Wi-Fi: ative a localização do sistema para ver o nome da rede", result)
    }

    @Test
    fun testValidSsidCleansQuotesAndSpaces() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = " \"HyprNetwork_5G\" ",
            isLocationEnabled = true,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertEquals("HyprNetwork_5G", result)
    }

    @Test
    fun testUnknownSsidReturnsNullWhenLocationEnabled() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "<unknown ssid>",
            isLocationEnabled = true,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertNull(result)
    }

    @Test
    fun testBlankSsidReturnsNullWhenLocationEnabled() {
        val result = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "   ",
            isLocationEnabled = true,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsPt.wifiLocationDisabledPrompt
        )
        assertNull(result)
    }

    @Test
    fun testDifferentLanguages() {
        val resEn = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "<unknown ssid>",
            isLocationEnabled = false,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsEn.wifiLocationDisabledPrompt
        )
        assertEquals("Wi-Fi: enable system location to see network name", resEn)

        val resZh = WifiSsidLogic.resolveWifiSsid(
            rawSsid = "<unknown ssid>",
            isLocationEnabled = false,
            hasLocationPermission = true,
            shareSsid = true,
            locationDisabledMessage = stringsZh.wifiLocationDisabledPrompt
        )
        assertEquals("Wi-Fi：请开启系统定位以查看网络名称", resZh)
    }
}
