// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

data class NotificationReplyRequest(
    val key: String,
    val idx: Int?,
    val text: String
)

object NotificationReplyLogic {
    fun parseNotificationReply(body: Map<String, Any?>?): NotificationReplyRequest? {
        if (body == null) return null
        val key = (body["key"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val text = body["text"] as? String ?: return null
        val idxVal = body["idx"] ?: body["action"]
        val idx = when (idxVal) {
            is Number -> idxVal.toInt()
            is String -> idxVal.toIntOrNull()
            else -> null
        }
        return NotificationReplyRequest(key = key, idx = idx, text = text)
    }
}
