// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.net.Uri

object ShareUrlValidator {

    const val MAX_URL_LENGTH = 4096

    /**
     * Valida um URL antes de ser enviado ao PC via share.url.
     * Requisitos do protocolo HyprLink:
     * - Esquema estritamente http ou https
     * - Anfitrião (host) presente e não vazio
     * - Comprimento máximo de 4096 caracteres
     */
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.length > MAX_URL_LENGTH) return false

        // Não aceita espaços ou caracteres de controlo
        if (url.any { it.isWhitespace() || it.isISOControl() }) return false

        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") {
            return false
        }

        val host = uri.host
        return !host.isNullOrBlank()
    }
}
