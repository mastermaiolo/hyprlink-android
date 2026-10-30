// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLogTranslatorTest {

    private fun hasCjk(text: String): Boolean {
        return text.any { it in '\u4e00'..'\u9fff' }
    }

    private val sampleKnownRecords = listOf(
        LogRecord("12:00:01", "HYPR", "hypr_event", listOf("activewindow")),
        LogRecord("12:00:02", "HYPR", "hypr_event", listOf("workspace")),
        LogRecord("12:00:03", "RECV", "recv_stream_packet", listOf("pc.status", "pc")),
        LogRecord("12:00:04", "CONN", "connected_to", listOf("ARCH-DESKTOP")),
        LogRecord("12:00:05", "CONN", "registered_conn"),
        LogRecord("12:00:06", "CONN", "conn_attempt", listOf("192.168.1.10", "4242")),
        LogRecord("12:00:07", "CONN", "conn_retry", listOf("5")),
        LogRecord("12:00:08", "SERVICE", "conn_service_loop", listOf("ARCH-DESKTOP")),
        LogRecord("12:00:09", "SERVICE", "conn_service_success", listOf("ARCH-DESKTOP", "workstation")),
        LogRecord("12:00:10", "CLIP", "tx_bytes", listOf("CLIP", "128")),
        LogRecord("12:00:11", "AUDIO", "rx_stream", listOf("AUDIO", "48kHz stereo")),
        LogRecord("12:00:12", "NOTIF", "notif_mirrored", listOf("Spotify", "Song")),
        LogRecord("12:00:13", "NOTIF", "notif_dismissed", listOf("key123")),
        LogRecord("12:00:14", "NOTIF", "notif_reply_sent", listOf("key123")),
        LogRecord("12:00:15", "NOTIF", "notif_action_triggered", listOf("0", "Archive", "key123")),
        LogRecord("12:00:16", "NOTIF", "notif_posted", listOf("New message")),
        LogRecord("12:00:17", "SHARE", "share_init_send", listOf("doc.pdf", "1024", "42")),
        LogRecord("12:00:18", "SHARE", "share_send_success", listOf("doc.pdf", "abcdef123456")),
        LogRecord("12:00:19", "SHARE", "share_file_received", listOf("photo.jpg", "2048", "43")),
        LogRecord("12:00:20", "SHARE", "share_download_verified", listOf("43", "abcdef123456")),
        LogRecord("12:00:21", "SHARE", "share_download_failed", listOf("43", "disk error")),
        LogRecord("12:00:22", "SHARE", "share_download_mismatch", listOf("43", "hash1", "hash2")),
        LogRecord("12:00:23", "SHARE", "share_error_sending", listOf("timeout")),
        LogRecord("12:00:24", "CLIP", "clip_img_sent", listOf("256")),
        LogRecord("12:00:25", "CLIP", "clip_img_received", listOf("512")),
        LogRecord("12:00:26", "CLIPBOARD", "clip_copied_workstation"),
        LogRecord("12:00:27", "BATTERY", "battery_phone_sending", listOf("85", "true")),
        LogRecord("12:00:28", "BATTERY", "battery_workstation", listOf("92", "false")),
        LogRecord("12:00:29", "MEDIA", "media_now_playing", listOf("Solar Power", "Lorde", "Playing", "art1")),
        LogRecord("12:00:30", "MEDIA", "media_command", listOf("play_pause")),
        LogRecord("12:00:31", "SYNC", "sync_screenshot_sending", listOf("screen.png", "120")),
        LogRecord("12:00:32", "SYNC", "sync_screenshot_sent", listOf("screen.png", "120")),
        LogRecord("12:00:33", "SYNC", "sync_screenshot_ignored"),
        LogRecord("12:00:34", "WOL", "wol_magic_sent", listOf("00:11:22:33:44:55", "192.168.1.255")),
        LogRecord("12:00:35", "WOL", "wol_timeout"),
        LogRecord("12:00:36", "WOL", "wol_cancelled"),
        LogRecord("12:00:37", "WEBCAM", "webcam_hevc_fallback"),
        LogRecord("12:00:38", "WEBCAM", "webcam_resolution_warning", listOf("1920", "1080")),
        LogRecord("12:00:39", "WEBCAM", "webcam_mic_no_conn"),
        LogRecord("12:00:40", "WEBCAM", "webcam_mic_denied"),
        LogRecord("12:00:41", "INFO", "station_paired"),
        LogRecord("12:00:42", "INFO", "file_sent", listOf("file.txt")),
        LogRecord("12:00:43", "INFO", "screenshot_sent"),
        LogRecord("12:00:44", "INFO", "notif_mirrored_readable", listOf("WhatsApp")),
        LogRecord("12:00:45", "INFO", "pc_connected"),
        LogRecord("12:00:46", "INFO", "connection_lost")
    )

    @Test
    fun testKnownModelsEnHasNoCjkAndZhHasCjk() {
        for (record in sampleKnownRecords) {
            val en = TerminalLogTranslator.translate(record, AppLanguage.EN_UK)
            val zh = TerminalLogTranslator.translate(record, AppLanguage.ZH_CN)

            assertFalse(
                "Key '${record.key}' EN translation must not contain CJK characters: '$en'",
                hasCjk(en)
            )

            assertTrue(
                "Key '${record.key}' ZH translation must contain CJK characters: '$zh'",
                hasCjk(zh)
            )

            assertNotEquals(
                "Key '${record.key}' EN and ZH translations should be distinct",
                en, zh
            )
        }
    }

    @Test
    fun testUnknownRecordPassesIntact() {
        val unknown = LogRecord("14:20:00", "CUSTOM", "", emptyList(), "arbitrary diagnostic string 12345")
        val en = TerminalLogTranslator.translate(unknown, AppLanguage.EN_UK)
        val zh = TerminalLogTranslator.translate(unknown, AppLanguage.ZH_CN)

        assertEquals("arbitrary diagnostic string 12345", en)
        assertEquals("arbitrary diagnostic string 12345", zh)
    }

    @Test
    fun testLanguageSwitchingTranslatesSameLine() {
        val line = "14:20:01 [CONN] Connected to ARCH-DESKTOP"
        val en = TerminalLogTranslator.translate(line, AppLanguage.EN_UK)
        val zh = TerminalLogTranslator.translate(line, AppLanguage.ZH_CN)

        assertEquals("14:20:01 [CONN] Connected to ARCH-DESKTOP", en)
        assertEquals("14:20:01 [CONN] 已连接到工作站 ARCH-DESKTOP", zh)
    }

    @Test
    fun testHyprEventNoMixing() {
        val line = "14:20:01 [HYPR] Received workstation event: activewindow"
        val en = TerminalLogTranslator.translate(line, AppLanguage.EN_UK)
        val zh = TerminalLogTranslator.translate(line, AppLanguage.ZH_CN)

        assertEquals("14:20:01 [HYPR] Received workstation event: active window changed", en)
        assertEquals("14:20:01 [HYPR] 收到工作站事件: 活动窗口已切换", zh)

        assertFalse("EN should not have CJK", hasCjk(en))
        assertTrue("ZH should have CJK", hasCjk(zh))
    }

    @Test
    fun testNeutralSerializationAndParsing() {
        val original = LogRecord("15:30:00", "SHARE", "share_init_send", listOf("video.mp4", "1048576", "99"))
        val serialized = original.toNeutralString()
        val parsed = LogRecord.parse(serialized)

        assertEquals(original.timestamp, parsed.timestamp)
        assertEquals(original.tag, parsed.tag)
        assertEquals(original.key, parsed.key)
        assertEquals(original.args, parsed.args)
    }
}
