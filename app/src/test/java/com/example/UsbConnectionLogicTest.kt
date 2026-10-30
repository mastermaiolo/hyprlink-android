// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.*
import org.junit.Test

class UsbConnectionLogicTest {

    @Test
    fun testParsePcAddrsFromMap() {
        val raw = mapOf(
            "pc_addrs" to listOf(
                mapOf("ip" to "192.168.1.100", "kind" to "wifi", "iface" to "wlan0"),
                mapOf("ip" to "192.168.42.129", "kind" to "usb", "iface" to "usb0")
            )
        )
        val addrs = UsbConnectionLogic.parsePcAddrs(raw)
        assertEquals(2, addrs.size)
        assertEquals("192.168.1.100", addrs[0].ip)
        assertEquals("wifi", addrs[0].kind)
        assertEquals("wlan0", addrs[0].iface)
        assertEquals("192.168.42.129", addrs[1].ip)
        assertEquals("usb", addrs[1].kind)
        assertEquals("usb0", addrs[1].iface)
    }

    @Test
    fun testParsePcAddrsFromList() {
        val raw = listOf(
            mapOf("ip" to "10.0.0.5", "kind" to "eth", "iface" to "eth0")
        )
        val addrs = UsbConnectionLogic.parsePcAddrs(raw)
        assertEquals(1, addrs.size)
        assertEquals("10.0.0.5", addrs[0].ip)
        assertEquals("eth", addrs[0].kind)
    }

    @Test
    fun testParsePcAddrsEmptyAndNull() {
        assertTrue(UsbConnectionLogic.parsePcAddrs(null).isEmpty())
        assertTrue(UsbConnectionLogic.parsePcAddrs(emptyMap<String, Any>()).isEmpty())
        assertTrue(UsbConnectionLogic.parsePcAddrs("invalid").isEmpty())
    }

    @Test
    fun testSelectRoutePrefersUsbWhenTetherActiveAndUsbAddrPresent() {
        val addrs = listOf(
            PcAddr(ip = "192.168.1.100", kind = "wifi", iface = "wlan0"),
            PcAddr(ip = "192.168.42.129", kind = "usb", iface = "usb0")
        )
        val decision = UsbConnectionLogic.selectConnectionRoute(
            availablePcAddrs = addrs,
            isUsbTetherActive = true,
            fallbackIp = "192.168.1.100"
        )
        assertEquals("192.168.42.129", decision.targetIp)
        assertEquals(LinkPathType.USB, decision.pathType)
        assertTrue(decision.isUsbPreferred)
    }

    @Test
    fun testSelectRouteFallbackWhenUsbTetherInactive() {
        val addrs = listOf(
            PcAddr(ip = "192.168.1.100", kind = "wifi", iface = "wlan0"),
            PcAddr(ip = "192.168.42.129", kind = "usb", iface = "usb0")
        )
        val decision = UsbConnectionLogic.selectConnectionRoute(
            availablePcAddrs = addrs,
            isUsbTetherActive = false,
            fallbackIp = "192.168.1.100"
        )
        assertEquals("192.168.1.100", decision.targetIp)
        assertEquals(LinkPathType.WIFI, decision.pathType)
        assertFalse(decision.isUsbPreferred)
    }

    @Test
    fun testSelectRouteFallbackWhenTetherActiveButNoUsbAddr() {
        val addrs = listOf(
            PcAddr(ip = "192.168.1.100", kind = "wifi", iface = "wlan0")
        )
        val decision = UsbConnectionLogic.selectConnectionRoute(
            availablePcAddrs = addrs,
            isUsbTetherActive = true,
            fallbackIp = "192.168.1.100"
        )
        assertEquals("192.168.1.100", decision.targetIp)
        assertFalse(decision.isUsbPreferred)
    }

    @Test
    fun testShouldSwitchPath() {
        val addrs = listOf(
            PcAddr(ip = "192.168.1.100", kind = "wifi", iface = "wlan0"),
            PcAddr(ip = "192.168.42.129", kind = "usb", iface = "usb0")
        )
        // Wi-Fi ativo e USB fica disponível -> deve comutar
        assertTrue(UsbConnectionLogic.shouldSwitchPath(LinkPathType.WIFI, isUsbTetherActive = true, availablePcAddrs = addrs))

        // Já em USB e USB mantém-se -> não deve comutar
        assertFalse(UsbConnectionLogic.shouldSwitchPath(LinkPathType.USB, isUsbTetherActive = true, availablePcAddrs = addrs))

        // Em USB e ancoragem cai -> deve comutar
        assertTrue(UsbConnectionLogic.shouldSwitchPath(LinkPathType.USB, isUsbTetherActive = false, availablePcAddrs = addrs))

        // Em USB e PC remove IP USB -> deve comutar
        val noUsb = listOf(PcAddr(ip = "192.168.1.100", kind = "wifi", iface = "wlan0"))
        assertTrue(UsbConnectionLogic.shouldSwitchPath(LinkPathType.USB, isUsbTetherActive = true, availablePcAddrs = noUsb))
    }
}
