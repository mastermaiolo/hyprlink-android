// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

/**
 * Regras e lógica de temporização e relevância de telemetria (phone.status e battery.state).
 *
 * Garante que em repouso (ecrã apagado):
 * 1. O intervalo periódico é de 120 s (poupança de energia e bateria).
 * 2. Com ecrã aceso, o intervalo periódico é de 30 s.
 * 3. Mudanças de estado relevantes (ligar/desligar carregador, ecrã a acender, bateria
 *    cruzando limiares críticos de aviso/alerta ou mudando percentagem) são despachadas
 *    de imediato com o debounce existente (2000 ms).
 * 4. Micro-eventos sem mudança de percentagem nem estado de carregador não perturbam o repouso.
 */
object TelemetryDispatchLogic {

    const val DEFAULT_DEBOUNCE_MS = 2000L
    const val BACKGROUND_IDLE_DEBOUNCE_MS = 10_000L
    const val SCREEN_ON_PERIODIC_INTERVAL_MS = 30_000L
    const val SCREEN_OFF_PERIODIC_INTERVAL_MS = 120_000L

    /**
     * Limiares de bateria usados no ecossistema HyprLink:
     * - 100%: Carga completa (notificação de bateria cheia)
     * - 20%: Aviso de bateria fraca / cor âmbar/dourada
     * - 15%: Alerta low_pct no daemon do PC (hyprlinkd)
     * - 10%: Alerta crítico / cor vermelha
     */
    val BATTERY_THRESHOLDS = listOf(10, 15, 20, 100)

    /**
     * Verifica se a transição de percentagem cruza algum dos limiares configurados.
     */
    fun hasCrossedBatteryThreshold(oldPct: Int, newPct: Int): Boolean {
        if (oldPct < 0 || newPct < 0) return true
        if (oldPct == newPct) return false
        val min = minOf(oldPct, newPct)
        val max = maxOf(oldPct, newPct)
        return BATTERY_THRESHOLDS.any { threshold ->
            min <= threshold && max >= threshold
        }
    }

    /**
     * Determina se uma alteração reportada pelo Intent de bateria é relevante
     * para despacho imediato de telemetria.
     */
    fun isRelevantBatteryChange(
        oldPct: Int,
        oldCharging: Boolean,
        newPct: Int,
        newCharging: Boolean
    ): Boolean {
        // Ligar ou desligar carregador é sempre relevante
        if (oldCharging != newCharging) return true

        // Mudança no nível percentual da bateria é relevante
        if (oldPct != newPct) return true

        return false
    }

    /**
     * Calcula o tempo de espera do debounce (ms).
     *
     * Com o ecrã aceso OU se a mudança de estado for relevante (carregador, ecrã aceso, limiares de bateria),
     * utiliza o debounce existente [requestedDebounceMs] (2000 ms por omissão), despachando de imediato.
     * Caso o ecrã esteja apagado e seja um evento secundário sem relevância de estado,
     * adia com [BACKGROUND_IDLE_DEBOUNCE_MS].
     */
    fun calculateDebounceDelay(
        isScreenOn: Boolean,
        isRelevantChange: Boolean,
        requestedDebounceMs: Long = DEFAULT_DEBOUNCE_MS
    ): Long {
        return if (isScreenOn || isRelevantChange) {
            requestedDebounceMs
        } else {
            maxOf(requestedDebounceMs, BACKGROUND_IDLE_DEBOUNCE_MS)
        }
    }

    /**
     * Devolve o intervalo periódico de envio de telemetria (ms).
     * - Ecrã aceso: 30 s
     * - Ecrã apagado (repouso): 120 s
     */
    fun getPeriodicIntervalLimit(isScreenOn: Boolean): Long {
        return if (isScreenOn) SCREEN_ON_PERIODIC_INTERVAL_MS else SCREEN_OFF_PERIODIC_INTERVAL_MS
    }
}
