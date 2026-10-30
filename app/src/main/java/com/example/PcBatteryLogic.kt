// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.Hypr

data class PcBattery(
    val level: Int,
    val charging: Boolean,
    val plugged: Boolean,
    val present: Boolean = true
)

data class PcBatteryDisplay(
    val text: String,
    val color: Color
)

object PcBatteryLogic {
    fun pcBatteryLine(
        battery: PcBattery?,
        isConnected: Boolean,
        strings: AppStrings
    ): PcBatteryDisplay? {
        if (!isConnected || battery == null || !battery.present) {
            return null
        }

        val level = battery.level.coerceIn(0, 100)
        return when {
            // Carga completa, só a usar o fio: LIGADO À CORRENTE (sem percentagem)
            battery.plugged && level >= 100 -> {
                PcBatteryDisplay(
                    text = strings.pcBatteryPlugged,
                    color = Hypr.Acid
                )
            }
            // A carregar: BATERIA 82 % · A CARREGAR
            battery.charging -> {
                PcBatteryDisplay(
                    text = "${strings.pcBatteryLabel} $level % · ${strings.pcBatteryCharging}",
                    color = Hypr.Acid
                )
            }
            // Fio ligado mas sem carregar abaixo de 100 %: BATERIA 80 % · LIGADO À CORRENTE
            battery.plugged -> {
                PcBatteryDisplay(
                    text = "${strings.pcBatteryLabel} $level % · ${strings.pcBatteryPlugged}",
                    color = Hypr.Acid
                )
            }
            // A usar a bateria: BATERIA 64 %
            else -> {
                val color = when {
                    level < 10 -> Hypr.Hot
                    level < 20 -> Hypr.LinkWait
                    else -> Hypr.Sub
                }
                PcBatteryDisplay(
                    text = "${strings.pcBatteryLabel} $level %",
                    color = color
                )
            }
        }
    }
}
