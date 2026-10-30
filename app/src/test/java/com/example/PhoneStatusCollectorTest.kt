// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneStatusCollectorTest {

    @Test
    fun testCollectBasicFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = PhoneStatusCollector.collect(context)

        assertNotNull(status)
        assertTrue(status.containsKey("network"))
        val network = status["network"] as String
        assertTrue(network in listOf("wifi", "cellular", "ethernet", "none"))

        // Storage check if present
        if (status.containsKey("storage_total_b")) {
            val total = status["storage_total_b"] as Long
            val used = status["storage_used_b"] as Long
            assertTrue(total > 0L)
            assertTrue(used >= 0L)
            assertTrue(used <= total)
        }

        // RAM check if present
        if (status.containsKey("ram_total_b")) {
            val total = status["ram_total_b"] as Long
            val used = status["ram_used_b"] as Long
            assertTrue(total > 0L)
            assertTrue(used >= 0L)
            assertTrue(used <= total)
        }

        // Screen check
        if (status.containsKey("screen_on")) {
            assertTrue(status["screen_on"] is Boolean)
        }

        // DND check
        if (status.containsKey("dnd")) {
            assertTrue(status["dnd"] is Boolean)
        }

        // Battery temperature check
        if (status.containsKey("battery_temp_c")) {
            val temp = status["battery_temp_c"]
            assertTrue(temp is Float)
        }
    }

    @Test
    fun testEncodePacketWithPhoneStatus() {
        val status = mapOf<String, Any>(
            "network" to "wifi",
            "storage_used_b" to 15000000000L,
            "storage_total_b" to 128000000000L,
            "ram_used_b" to 3500000000L,
            "ram_total_b" to 8000000000L,
            "battery_temp_c" to 28.5f,
            "screen_on" to true,
            "dnd" to false,
            "notifications" to 3L
        )

        val packet = Packet(id = 100L, type = "phone.status", body = status)
        val encodedBytes = ConnectionRepository.encodePacket(packet)
        assertNotNull(encodedBytes)
        assertTrue(encodedBytes.isNotEmpty())

        val decodedPacket = ConnectionRepository.decodePacket(encodedBytes)
        assertNotNull(decodedPacket)
        assertEquals("phone.status", decodedPacket?.type)
        val decodedBody = decodedPacket?.body
        assertNotNull(decodedBody)
        assertEquals("wifi", decodedBody?.get("network"))
        assertEquals(true, decodedBody?.get("screen_on"))
        assertEquals(false, decodedBody?.get("dnd"))
        assertEquals(3L, decodedBody?.get("notifications"))

        val decodedTemp = decodedBody?.get("battery_temp_c")
        assertNotNull(decodedTemp)
        assertEquals(28.5f, (decodedTemp as Number).toFloat(), 0.01f)
    }

    @Test
    fun testEncodePacketWithFullPhoneStatusAndNowPlaying() {
        val wifiMap = mapOf<String, Any>(
            "ssid" to "MyHomeWifi",
            "rssi_dbm" to -61L
        )
        val nowPlaying = mapOf<String, Any>(
            "title" to "Song Title",
            "artist" to "Artist Name",
            "app" to "com.spotify.music",
            "playing" to true,
            "position_ms" to 42000L,
            "duration_ms" to 210000L
        )
        val status = mapOf<String, Any>(
            "network" to "wifi",
            "cell_gen" to "5G",
            "carrier" to "Vodafone",
            "signal_bars" to 4L,
            "wifi" to wifiMap,
            "storage_used_b" to 20000000000L,
            "storage_total_b" to 128000000000L,
            "ram_used_b" to 4000000000L,
            "ram_total_b" to 8000000000L,
            "battery_temp_c" to 31.7f,
            "screen_on" to true,
            "dnd" to false,
            "notifications" to 2L,
            "now_playing" to nowPlaying
        )

        val packet = Packet(id = 101L, type = "phone.status", body = status)
        val encodedBytes = ConnectionRepository.encodePacket(packet)
        assertNotNull(encodedBytes)

        val decodedPacket = ConnectionRepository.decodePacket(encodedBytes)
        assertNotNull(decodedPacket)
        assertEquals("phone.status", decodedPacket?.type)

        val decodedBody = decodedPacket?.body
        assertNotNull(decodedBody)
        assertEquals("5G", decodedBody?.get("cell_gen"))
        assertEquals("Vodafone", decodedBody?.get("carrier"))
        assertEquals(4L, decodedBody?.get("signal_bars"))

        @Suppress("UNCHECKED_CAST")
        val decodedWifi = decodedBody?.get("wifi") as? Map<String, Any>
        assertNotNull(decodedWifi)
        assertEquals("MyHomeWifi", decodedWifi?.get("ssid"))
        assertEquals(-61L, (decodedWifi?.get("rssi_dbm") as? Number)?.toLong())

        @Suppress("UNCHECKED_CAST")
        val decodedNowPlaying = decodedBody?.get("now_playing") as? Map<String, Any>
        assertNotNull(decodedNowPlaying)
        assertEquals("Song Title", decodedNowPlaying?.get("title"))
        assertEquals("Artist Name", decodedNowPlaying?.get("artist"))
        assertEquals("com.spotify.music", decodedNowPlaying?.get("app"))
        assertEquals(true, decodedNowPlaying?.get("playing"))
        assertEquals(42000L, (decodedNowPlaying?.get("position_ms") as? Number)?.toLong())
        assertEquals(210000L, (decodedNowPlaying?.get("duration_ms") as? Number)?.toLong())
    }

    @Test
    fun testPcStatusPacketDecoding() {
        val body = mapOf<String, Any>(
            "hostname" to "archlinux-desktop",
            "cpu_pct" to 18.5f,
            "ram_used_b" to 8589934592L,
            "ram_total_b" to 34359738368L,
            "cpu_temp_c" to 47.2f,
            "uptime_s" to 7200L,
            "rtt_ms" to 2.4f
        )
        val packet = Packet(id = 200L, type = "pc.status", body = body)
        val encodedBytes = ConnectionRepository.encodePacket(packet)
        val decoded = ConnectionRepository.decodePacket(encodedBytes)

        assertNotNull(decoded)
        assertEquals("pc.status", decoded?.type)
        assertEquals("archlinux-desktop", decoded?.body?.get("hostname"))
        assertEquals(18.5f, (decoded?.body?.get("cpu_pct") as Number).toFloat(), 0.01f)
        assertEquals(8589934592L, (decoded?.body?.get("ram_used_b") as Number).toLong())
        assertEquals(34359738368L, (decoded?.body?.get("ram_total_b") as Number).toLong())
        assertEquals(47.2f, (decoded?.body?.get("cpu_temp_c") as Number).toFloat(), 0.01f)
        assertEquals(7200L, (decoded?.body?.get("uptime_s") as Number).toLong())
        assertEquals(2.4f, (decoded?.body?.get("rtt_ms") as Number).toFloat(), 0.01f)
    }

    @Test
    fun testPhoneMediaPacket() {
        for (action in listOf("previous", "play_pause", "next")) {
            val packet = Packet(id = 301L, type = "phone.media", body = mapOf("action" to action))
            val encoded = ConnectionRepository.encodePacket(packet)
            val decoded = ConnectionRepository.decodePacket(encoded)
            assertNotNull(decoded)
            assertEquals("phone.media", decoded?.type)
            assertEquals(action, decoded?.body?.get("action"))
        }
    }

    @Test
    fun testReadBasicPhoneStatePermissionSupported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("share_cell_gen", true).commit()

        // Calling collect with share_cell_gen=true should execute safely with READ_BASIC_PHONE_STATE
        val status = PhoneStatusCollector.collect(context)
        assertNotNull(status)
    }

    @Test
    fun testCollectLightModeOmitsStorageAndRam() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lightStatus = PhoneStatusCollector.collect(context, lightMode = true)

        assertNotNull(lightStatus)
        assertFalse(lightStatus.containsKey("storage_total_b"))
        assertFalse(lightStatus.containsKey("storage_used_b"))
        assertFalse(lightStatus.containsKey("ram_total_b"))
        assertFalse(lightStatus.containsKey("ram_used_b"))
        assertTrue(lightStatus.containsKey("network"))
    }
}
