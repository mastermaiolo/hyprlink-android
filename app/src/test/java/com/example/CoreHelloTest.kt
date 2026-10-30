// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CoreHelloTest {

    @Test
    fun testBuildHelloCborSerialization() {
        val deviceName = "Poco F4 Test"
        val pairingTokenHex = "aabbccddeeff"
        val bytes = buildHelloCbor(deviceName, pairingTokenHex)

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val dataItems = CborDecoder(ByteArrayInputStream(bytes)).decode()
        assertEquals(1, dataItems.size)

        val rootMap = dataItems[0] as co.nstant.`in`.cbor.model.Map
        val type = (rootMap.get(UnicodeString("type")) as UnicodeString).string
        assertEquals("core.hello", type)

        val body = rootMap.get(UnicodeString("body")) as co.nstant.`in`.cbor.model.Map
        val decodedDeviceName = (body.get(UnicodeString("device_name")) as UnicodeString).string
        assertEquals(deviceName, decodedDeviceName)

        val capabilitiesArray = body.get(UnicodeString("capabilities")) as co.nstant.`in`.cbor.model.Array
        val capabilitiesList = capabilitiesArray.dataItems.map { (it as UnicodeString).string }

        assertTrue(capabilitiesList.contains("core"))
        assertTrue(capabilitiesList.contains("clipboard"))
        assertTrue(capabilitiesList.contains("notification"))
        assertTrue(capabilitiesList.contains("media"))
        assertTrue(capabilitiesList.contains("battery"))
        assertTrue(capabilitiesList.contains("share"))
        assertTrue(capabilitiesList.contains("webcam"))
        assertTrue(capabilitiesList.contains("audio"))
        assertTrue(capabilitiesList.contains("input"))
        assertTrue(capabilitiesList.contains("hypr"))
        assertTrue(capabilitiesList.contains("phone"))
        assertTrue(capabilitiesList.contains("phone_status"))
        assertTrue(capabilitiesList.contains("media_session"))

        // Check pairing token
        val token = body.get(UnicodeString("pairing_token")) as? ByteString
        assertNotNull(token)
    }

    @Test
    fun testBuildHelloCborWithoutToken() {
        val deviceName = "Poco F4 Reconnect"
        val bytes = buildHelloCbor(deviceName, null)

        val dataItems = CborDecoder(ByteArrayInputStream(bytes)).decode()
        val rootMap = dataItems[0] as co.nstant.`in`.cbor.model.Map
        val body = rootMap.get(UnicodeString("body")) as co.nstant.`in`.cbor.model.Map

        val token = body.get(UnicodeString("pairing_token"))
        assertNull(token)
    }
}
