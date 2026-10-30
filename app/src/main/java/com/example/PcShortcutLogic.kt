// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

data class PcShortcut(
    val id: String,
    val label: String
)

data class ShortcutRunResult(
    val id: String,
    val ok: Boolean,
    val error: String? = null
)

object PcShortcutLogic {
    fun parseShortcutsList(body: Map<String, Any?>?): List<PcShortcut> {
        if (body == null) return emptyList()
        val rawList = body["shortcuts"] as? List<*> ?: return emptyList()
        val result = ArrayList<PcShortcut>()
        for (item in rawList) {
            if (item is Map<*, *>) {
                val id = item["id"] as? String
                val label = item["label"] as? String
                if (!id.isNullOrBlank() && !label.isNullOrBlank()) {
                    result.add(PcShortcut(id = id.trim(), label = label.trim()))
                }
            }
        }
        return result
    }

    fun translateShortcutError(error: String?, strings: AppStrings): String {
        return when (error) {
            "unknown shortcut" -> strings.shortcutErrorUnknown
            "rate limited" -> strings.shortcutErrorRateLimited
            else -> strings.shortcutErrorGeneric
        }
    }
}
