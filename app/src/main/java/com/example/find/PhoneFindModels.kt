// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.find

/**
 * Modelos e validação do protocolo «Onde está o telemóvel?» (phone.find).
 * Contrato canónico definido no PROTOCOL.md e prompt 55:
 *
 * - phone.find: PC -> telemóvel {sound: bool, vibrate: bool, torch: bool, duration_s: uint}
 * - phone.find_stop: PC -> telemóvel {}
 * - phone.find.state: telemóvel -> PC {active: bool, until_ms?: uint (epoch ms), stopped_by?: "user"|"timeout"|"pc"|"error", error?: String}
 */
data class PhoneFindRequest(
    val sound: Boolean = false,
    val vibrate: Boolean = false,
    val torch: Boolean = false,
    val durationS: Int = DEFAULT_DURATION_S
) {
    /**
     * Validação do pedido:
     * - Pelo menos um modo ativo (sound, vibrate, torch).
     */
    val isValid: Boolean get() = sound || vibrate || torch

    companion object {
        const val DEFAULT_DURATION_S = 30
        const val MIN_DURATION_S = 5
        const val MAX_DURATION_S = 60

        /**
         * Converte um mapa recebido por CBOR para um PhoneFindRequest validado.
         * Retorna null se nenhum modo for verdadeiro ou mapa for nulo.
         */
        fun fromMap(map: Map<String, Any?>?): PhoneFindRequest? {
            if (map == null) return null

            val sound = map["sound"] as? Boolean ?: false
            val vibrate = map["vibrate"] as? Boolean ?: false
            val torch = map["torch"] as? Boolean ?: false

            val durationRaw = when (val d = map["duration_s"]) {
                is Number -> d.toInt()
                else -> DEFAULT_DURATION_S
            }

            val durationS = if (durationRaw <= 0) {
                DEFAULT_DURATION_S
            } else {
                durationRaw.coerceIn(MIN_DURATION_S, MAX_DURATION_S)
            }

            val request = PhoneFindRequest(
                sound = sound,
                vibrate = vibrate,
                torch = torch,
                durationS = durationS
            )

            return if (request.isValid) request else null
        }
    }
}

/**
 * Razões válidas de finalização da procura de telemóvel.
 */
object PhoneFindStopReason {
    const val USER = "user"
    const val TIMEOUT = "timeout"
    const val PC = "pc"
    const val ERROR = "error"
}

/**
 * Estado reportado ao PC em phone.find.state.
 */
data class PhoneFindState(
    val active: Boolean,
    val untilMs: Long? = null,
    val stoppedBy: String? = null,
    val error: String? = null
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>("active" to active)
        if (untilMs != null) {
            map["until_ms"] = untilMs
        }
        if (stoppedBy != null) {
            map["stopped_by"] = stoppedBy
        }
        if (error != null) {
            map["error"] = error
        }
        return map
    }
}
