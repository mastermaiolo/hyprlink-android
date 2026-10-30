// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationReplyLogicTest {

    @Test
    fun testParseValidReplyWithIntegerIdx() {
        val body = mapOf<String, Any?>(
            "key" to "0|com.whatsapp|123",
            "idx" to 0,
            "text" to "Olá do PC!"
        )
        val result = NotificationReplyLogic.parseNotificationReply(body)
        assertNotNull(result)
        assertEquals("0|com.whatsapp|123", result?.key)
        assertEquals(0, result?.idx)
        assertEquals("Olá do PC!", result?.text)
    }

    @Test
    fun testParseValidReplyWithLongIdx() {
        val body = mapOf<String, Any?>(
            "key" to "0|org.telegram.messenger|456",
            "idx" to 1L,
            "text" to "Mensagem de teste"
        )
        val result = NotificationReplyLogic.parseNotificationReply(body)
        assertNotNull(result)
        assertEquals("0|org.telegram.messenger|456", result?.key)
        assertEquals(1, result?.idx)
        assertEquals("Mensagem de teste", result?.text)
    }

    @Test
    fun testParseValidReplyWithStringIdx() {
        val body = mapOf<String, Any?>(
            "key" to "0|com.google.android.apps.messaging|789",
            "idx" to "2",
            "text" to "OK"
        )
        val result = NotificationReplyLogic.parseNotificationReply(body)
        assertNotNull(result)
        assertEquals("0|com.google.android.apps.messaging|789", result?.key)
        assertEquals(2, result?.idx)
        assertEquals("OK", result?.text)
    }

    @Test
    fun testParseValidReplyFallbackAction() {
        val body = mapOf<String, Any?>(
            "key" to "0|com.example|1",
            "action" to 0,
            "text" to "Resposta"
        )
        val result = NotificationReplyLogic.parseNotificationReply(body)
        assertNotNull(result)
        assertEquals("0|com.example|1", result?.key)
        assertEquals(0, result?.idx)
        assertEquals("Resposta", result?.text)
    }

    @Test
    fun testParseValidReplyWithoutIdx() {
        val body = mapOf<String, Any?>(
            "key" to "0|com.example|2",
            "text" to "Resposta direta"
        )
        val result = NotificationReplyLogic.parseNotificationReply(body)
        assertNotNull(result)
        assertEquals("0|com.example|2", result?.key)
        assertNull(result?.idx)
        assertEquals("Resposta direta", result?.text)
    }

    @Test
    fun testParseNullBodyReturnsNull() {
        assertNull(NotificationReplyLogic.parseNotificationReply(null))
    }

    @Test
    fun testParseMissingKeyReturnsNull() {
        val body = mapOf<String, Any?>(
            "idx" to 0,
            "text" to "Olá"
        )
        assertNull(NotificationReplyLogic.parseNotificationReply(body))
    }

    @Test
    fun testParseBlankKeyReturnsNull() {
        val body = mapOf<String, Any?>(
            "key" to "   ",
            "idx" to 0,
            "text" to "Olá"
        )
        assertNull(NotificationReplyLogic.parseNotificationReply(body))
    }

    @Test
    fun testParseMissingTextReturnsNull() {
        val body = mapOf<String, Any?>(
            "key" to "0|com.example|3",
            "idx" to 0
        )
        assertNull(NotificationReplyLogic.parseNotificationReply(body))
    }
}
