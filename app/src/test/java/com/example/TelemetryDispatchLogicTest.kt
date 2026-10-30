// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.*
import org.junit.Test

class TelemetryDispatchLogicTest {

    @Test
    fun testPeriodicIntervalLimits() {
        // Com o ecrã aceso, o intervalo é de 30 segundos
        assertEquals(30_000L, TelemetryDispatchLogic.getPeriodicIntervalLimit(isScreenOn = true))

        // Com o ecrã apagado (repouso), o intervalo é de 120 segundos
        assertEquals(120_000L, TelemetryDispatchLogic.getPeriodicIntervalLimit(isScreenOn = false))
    }

    @Test
    fun testChargerConnectionChangesAreAlwaysRelevant() {
        // Ligar o carregador
        assertTrue(
            "Ligar o carregador deve ser considerado mudança relevante",
            TelemetryDispatchLogic.isRelevantBatteryChange(
                oldPct = 50,
                oldCharging = false,
                newPct = 50,
                newCharging = true
            )
        )

        // Desligar o carregador
        assertTrue(
            "Desligar o carregador deve ser considerado mudança relevante",
            TelemetryDispatchLogic.isRelevantBatteryChange(
                oldPct = 80,
                oldCharging = true,
                newPct = 80,
                newCharging = false
            )
        )
    }

    @Test
    fun testBatteryThresholdCrossing() {
        // Limiares conhecidos: 10, 15, 20, 100
        val thresholds = listOf(10, 15, 20, 100)
        assertEquals(thresholds, TelemetryDispatchLogic.BATTERY_THRESHOLDS)

        // Cruzar limiar de 20% para baixo (21 -> 20 ou 21 -> 19)
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(21, 20))
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(21, 19))
        // Cruzar limiar de 20% para cima (19 -> 20)
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(19, 20))

        // Cruzar limiar de 15% (alerta low_pct do daemon hyprlinkd)
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(16, 15))
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(15, 16))

        // Cruzar limiar crítico de 10%
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(11, 10))
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(10, 11))

        // Cruzar limiar de 100% (bateria cheia)
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(99, 100))
        assertTrue(TelemetryDispatchLogic.hasCrossedBatteryThreshold(100, 99))

        // Variação sem cruzar limiar (ex: 75 -> 74)
        assertFalse(TelemetryDispatchLogic.hasCrossedBatteryThreshold(75, 74))
        // Mesma percentagem
        assertFalse(TelemetryDispatchLogic.hasCrossedBatteryThreshold(50, 50))
    }

    @Test
    fun testBatteryPercentageChangesAreRelevant() {
        // Cruzando limiar ou mudando nível de carga
        assertTrue(
            TelemetryDispatchLogic.isRelevantBatteryChange(
                oldPct = 21,
                oldCharging = false,
                newPct = 20,
                newCharging = false
            )
        )

        assertTrue(
            TelemetryDispatchLogic.isRelevantBatteryChange(
                oldPct = 75,
                oldCharging = false,
                newPct = 74,
                newCharging = false
            )
        )

        // Evento de broadcast sem alteração de nível nem de carregador (ex: ruído do sensor térmico)
        assertFalse(
            "Variação sem alteração de percentagem ou carregador não deve ser relevante",
            TelemetryDispatchLogic.isRelevantBatteryChange(
                oldPct = 60,
                oldCharging = false,
                newPct = 60,
                newCharging = false
            )
        )
    }

    @Test
    fun testDebounceDelayWithScreenOffForRelevantChanges() {
        // Com o ecrã apagado (isScreenOn = false), uma mudança relevante deve usar o debounce existente (2000 ms)
        val debounceRelevant = TelemetryDispatchLogic.calculateDebounceDelay(
            isScreenOn = false,
            isRelevantChange = true,
            requestedDebounceMs = 2000L
        )
        assertEquals(
            "Mudanças relevantes com ecrã apagado devem usar o debounce imediato existente (2000 ms)",
            2000L,
            debounceRelevant
        )

        // Com o ecrã apagado e media playback (500 ms)
        val debounceMedia = TelemetryDispatchLogic.calculateDebounceDelay(
            isScreenOn = false,
            isRelevantChange = true,
            requestedDebounceMs = 500L
        )
        assertEquals(500L, debounceMedia)

        // Com o ecrã apagado e SEM mudança relevante, adia para repouso (10 000 ms)
        val debounceIdle = TelemetryDispatchLogic.calculateDebounceDelay(
            isScreenOn = false,
            isRelevantChange = false,
            requestedDebounceMs = 2000L
        )
        assertEquals(
            "Eventos não relevantes com ecrã apagado devem esperar o recuo de repouso (10 000 ms)",
            10_000L,
            debounceIdle
        )
    }

    @Test
    fun testDebounceDelayWithScreenOn() {
        // Com o ecrã aceso (isScreenOn = true), usa sempre o debounce solicitado
        assertEquals(
            2000L,
            TelemetryDispatchLogic.calculateDebounceDelay(
                isScreenOn = true,
                isRelevantChange = false,
                requestedDebounceMs = 2000L
            )
        )
        assertEquals(
            500L,
            TelemetryDispatchLogic.calculateDebounceDelay(
                isScreenOn = true,
                isRelevantChange = true,
                requestedDebounceMs = 500L
            )
        )
    }
}
