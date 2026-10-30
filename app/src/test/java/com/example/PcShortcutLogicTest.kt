// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcShortcutLogicTest {

    private val stringsPt = STRINGS_PT_PT
    private val stringsEn = STRINGS_EN_UK
    private val stringsZh = STRINGS_ZH_CN

    // ──────────────────────────────────────── 1. Descodificação ──

    @Test
    fun testParseShortcutsListEmpty() {
        val body = mapOf<String, Any?>("shortcuts" to emptyList<Any>())
        val result = PcShortcutLogic.parseShortcutsList(body)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testParseShortcutsListMultiple() {
        val body = mapOf(
            "shortcuts" to listOf(
                mapOf("id" to "3fa9c01e", "label" to "Terminal"),
                mapOf("id" to "a1b2c3d4", "label" to "Navegador Web")
            )
        )
        val result = PcShortcutLogic.parseShortcutsList(body)
        assertEquals(2, result.size)
        assertEquals("3fa9c01e", result[0].id)
        assertEquals("Terminal", result[0].label)
        assertEquals("a1b2c3d4", result[1].id)
        assertEquals("Navegador Web", result[1].label)
    }

    @Test
    fun testParseShortcutsListUnknownKeysIgnored() {
        val body = mapOf(
            "shortcuts" to listOf(
                mapOf(
                    "id" to "99887766",
                    "label" to "Editor",
                    "icon" to "app-icon-custom",
                    "command" to "foot -e nvim", // deve ser ignorado
                    "extra_key" to 12345
                )
            ),
            "unknown_root_key" to "ignore_me"
        )
        val result = PcShortcutLogic.parseShortcutsList(body)
        assertEquals(1, result.size)
        assertEquals("99887766", result[0].id)
        assertEquals("Editor", result[0].label)
    }

    @Test
    fun testParseShortcutsListInvalidEntriesFiltered() {
        val body = mapOf(
            "shortcuts" to listOf(
                mapOf("id" to "", "label" to "Vazio"),
                mapOf("id" to "11223344", "label" to ""),
                mapOf("id" to null, "label" to "Nulo"),
                "not_a_map",
                mapOf("id" to "valid123", "label" to "Válido")
            )
        )
        val result = PcShortcutLogic.parseShortcutsList(body)
        assertEquals(1, result.size)
        assertEquals("valid123", result[0].id)
        assertEquals("Válido", result[0].label)
    }

    @Test
    fun testParseShortcutsListNullOrMissingBody() {
        assertTrue(PcShortcutLogic.parseShortcutsList(null).isEmpty())
        assertTrue(PcShortcutLogic.parseShortcutsList(emptyMap()).isEmpty())
        assertTrue(PcShortcutLogic.parseShortcutsList(mapOf("other" to 123)).isEmpty())
    }

    // ──────────────────────────────────────── 2. Estado limpo ao desligar ──

    @Test
    fun testStateClearedOnDisconnect() {
        val list = listOf(PcShortcut("3fa9c01e", "Terminal"))
        ConnectionRepository.handleShortcutsList(mapOf("shortcuts" to listOf(mapOf("id" to "3fa9c01e", "label" to "Terminal"))))
        assertEquals(1, ConnectionRepository.pcShortcuts.value.size)

        // Ao desligar, deve limpar
        ConnectionRepository.setStatus(ConnectionStatus.DISCONNECTED)
        assertTrue(ConnectionRepository.pcShortcuts.value.isEmpty())

        // Reabastece e testa clearActiveConnection()
        ConnectionRepository.handleShortcutsList(mapOf("shortcuts" to listOf(mapOf("id" to "3fa9c01e", "label" to "Terminal"))))
        assertEquals(1, ConnectionRepository.pcShortcuts.value.size)

        ConnectionRepository.clearActiveConnection()
        assertTrue(ConnectionRepository.pcShortcuts.value.isEmpty())
    }

    // ──────────────────────────────────────── 3. Envio e pacote shortcut.run ──

    @Test
    fun testShortcutRunPacketEncodingAndDecoding() {
        val targetId = "3fa9c01e"
        val packet = Packet(id = 101L, type = "shortcut.run", body = mapOf("id" to targetId))
        val encoded = ConnectionRepository.encodePacket(packet)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = ConnectionRepository.decodePacket(encoded)
        assertNotNull(decoded)
        assertEquals("shortcut.run", decoded?.type)
        assertEquals(targetId, decoded?.body?.get("id"))
    }

    @Test
    fun testShortcutRunResultPacketDecodingSuccess() {
        val body = mapOf<String, Any>(
            "id" to "3fa9c01e",
            "ok" to true
        )
        val packet = Packet(id = 102L, type = "shortcut.run_result", body = body)
        val encoded = ConnectionRepository.encodePacket(packet)
        val decoded = ConnectionRepository.decodePacket(encoded)

        assertNotNull(decoded)
        assertEquals("shortcut.run_result", decoded?.type)
        assertEquals("3fa9c01e", decoded?.body?.get("id"))
        assertEquals(true, decoded?.body?.get("ok"))
    }

    @Test
    fun testShortcutRunResultPacketDecodingError() {
        val body = mapOf<String, Any>(
            "id" to "3fa9c01e",
            "ok" to false,
            "error" to "rate limited"
        )
        val packet = Packet(id = 103L, type = "shortcut.run_result", body = body)
        val encoded = ConnectionRepository.encodePacket(packet)
        val decoded = ConnectionRepository.decodePacket(encoded)

        assertNotNull(decoded)
        assertEquals("shortcut.run_result", decoded?.type)
        assertEquals("3fa9c01e", decoded?.body?.get("id"))
        assertEquals(false, decoded?.body?.get("ok"))
        assertEquals("rate limited", decoded?.body?.get("error"))
    }

    // ──────────────────────────────────────── 4. Mapeamento de erros para texto ──

    @Test
    fun testErrorMappingPtPt() {
        assertEquals("Atalho já não existe no PC", PcShortcutLogic.translateShortcutError("unknown shortcut", stringsPt))
        assertEquals("Demasiado depressa, tente outra vez", PcShortcutLogic.translateShortcutError("rate limited", stringsPt))
        assertEquals("Não foi possível executar o atalho", PcShortcutLogic.translateShortcutError("failed", stringsPt))
        assertEquals("Não foi possível executar o atalho", PcShortcutLogic.translateShortcutError("random error", stringsPt))
        assertEquals("Não foi possível executar o atalho", PcShortcutLogic.translateShortcutError(null, stringsPt))
    }

    @Test
    fun testErrorMappingEnUk() {
        assertEquals("Shortcut no longer exists on PC", PcShortcutLogic.translateShortcutError("unknown shortcut", stringsEn))
        assertEquals("Too fast, please try again", PcShortcutLogic.translateShortcutError("rate limited", stringsEn))
        assertEquals("Could not execute shortcut", PcShortcutLogic.translateShortcutError("failed", stringsEn))
    }

    @Test
    fun testErrorMappingZhCn() {
        assertEquals("快捷方式在电脑上已不存在", PcShortcutLogic.translateShortcutError("unknown shortcut", stringsZh))
        assertEquals("操作过于频繁，请重试", PcShortcutLogic.translateShortcutError("rate limited", stringsZh))
        assertEquals("无法执行快捷方式", PcShortcutLogic.translateShortcutError("failed", stringsZh))
    }
}
