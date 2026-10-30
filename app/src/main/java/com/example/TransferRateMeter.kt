// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import kotlin.math.ceil
import kotlin.math.roundToLong

val AppStrings.decimalComma: Boolean
    get() = decimalSeparator == ","

/**
 * Medidor de velocidade e tempo restante de transferências (prompt 61).
 * Implementação pura em Kotlin com paridade matemática estrita com hyprlink-proto::rate.
 *
 * - Velocidade: média móvel exponencial dos bytes por segundo, com uma
 *   amostra a cada ≥ [SAMPLE_EVERY_MS] e peso [ALPHA] para a nova amostra.
 * - Tempo restante: (total − feito) / velocidade, só depois de
 *   [ETA_AFTER_MS] de transferência e com velocidade >= 1 B/s.
 */
class TransferRateMeter {

    companion object {
        /** Intervalo mínimo entre amostras (500 ms). Amostras antes disto são ignoradas. */
        const val SAMPLE_EVERY_MS: Long = 500L

        /** Peso da amostra nova na média móvel exponencial (α = 0,3). */
        const val ALPHA: Double = 0.3

        /** Antes de 2000 ms de transferência, o tempo restante é nulo ("a calcular…"). */
        const val ETA_AFTER_MS: Long = 2000L

        /**
         * Média de uma transferência acabada: [bytes] em [elapsedMs].
         * Retorna null se elapsedMs <= 0.
         */
        fun average(bytes: Long, elapsedMs: Long): Long? {
            val s = elapsedMs / 1000.0
            return if (s > 0.0) {
                (bytes / s).roundToLong()
            } else {
                null
            }
        }
    }

    /** Instante (ms decorridos desde o início) e bytes feitos na última amostra. Começa em (0 s, 0 B). */
    private var lastElapsedMs: Long = 0L
    private var lastBytes: Long = 0L

    /** Bytes por segundo; null até à primeira amostra válida. */
    private var ema: Double? = null

    /**
     * Regista [done] bytes concluídos passados [elapsedMs] desde o início da transferência.
     * Só conta como amostra passados pelo menos [SAMPLE_EVERY_MS] desde a anterior.
     * Numa pausa (sem bytes novos), a amostra é 0 e a média desce.
     */
    fun sample(elapsedMs: Long, done: Long) {
        val dt = (elapsedMs - lastElapsedMs).coerceAtLeast(0L)
        if (dt < SAMPLE_EVERY_MS) {
            return
        }
        val bytesDiff = (done - lastBytes).coerceAtLeast(0L)
        val dtSeconds = dt / 1000.0
        val currentRate = bytesDiff.toDouble() / dtSeconds

        ema = when (val prev = ema) {
            null -> currentRate
            else -> ALPHA * currentRate + (1.0 - ALPHA) * prev
        }
        lastElapsedMs = elapsedMs
        lastBytes = done
    }

    /** Bytes por segundo (arredondado); null antes da primeira amostra. */
    fun bytesPerSec(): Long? {
        return ema?.roundToLong()
    }

    /** Segundos que faltam; null antes de [ETA_AFTER_MS] ou se parado (< 1 B/s). */
    fun etaSeconds(elapsedMs: Long, done: Long, total: Long): Long? {
        if (elapsedMs < ETA_AFTER_MS) {
            return null
        }
        val rate = ema ?: return null
        if (rate < 1.0) {
            return null
        }
        val remaining = (total - done).coerceAtLeast(0L)
        return ceil(remaining.toDouble() / rate).toLong()
    }
}

/**
 * Formatador puro de tamanhos, velocidades, durações e resumos de transferências.
 * Paridade com hyprlink-proto::fmt.
 */
object TransferRateFormatter {

    /**
     * Formatação de número decimal com [places] casas.
     * Se [decimalComma] for true, usa vírgula; caso contrário usa ponto.
     */
    fun decimal(v: Double, places: Int, decimalComma: Boolean): String {
        val out = String.format(java.util.Locale.US, "%.${places}f", v)
        return if (decimalComma) out.replace('.', ',') else out
    }

    /**
     * 1 234 567 bytes → "1,2 MB" (unidades SI MB/GB/KB ou B).
     */
    fun formatBytes(bytes: Long, decimalComma: Boolean): String {
        val b = bytes.toDouble()
        val (v, u) = when {
            b >= 1e9 -> Pair(b / 1e9, "GB")
            b >= 1e6 -> Pair(b / 1e6, "MB")
            b >= 1e3 -> Pair(b / 1e3, "KB")
            else -> return "$bytes B"
        }
        return "${decimal(v, 1, decimalComma)} $u"
    }

    /**
     * 12 400 000 B/s → "12,4 MB/s".
     */
    fun formatRate(bytesPerSec: Long, decimalComma: Boolean): String {
        return "${formatBytes(bytesPerSec, decimalComma)}/s"
    }

    /**
     * 45 → "45 s"; 130 → "2 min 10 s"; 3900 → "1 h 05 min".
     */
    fun formatDuration(secs: Long, strings: AppStrings): String {
        return when {
            secs < 60 -> strings.durationSeconds(secs)
            secs < 3600 -> strings.durationMinutesSeconds(secs / 60, secs % 60)
            else -> strings.durationHoursMinutes(secs / 3600, (secs % 3600) / 60)
        }
    }

    /**
     * Segunda linha de transferência em curso:
     * "12,4 MB/s · faltam 2 min 10 s", "12,4 MB/s · a calcular…" ou "a calcular…".
     */
    fun transferSpeed(bytesPerSec: Long?, etaSeconds: Long?, strings: AppStrings): String {
        return when {
            bytesPerSec != null && etaSeconds != null -> {
                val r = formatRate(bytesPerSec, strings.decimalComma)
                val d = formatDuration(etaSeconds, strings)
                "$r · ${strings.transferEta(d)}"
            }
            bytesPerSec != null -> {
                val r = formatRate(bytesPerSec, strings.decimalComma)
                "$r · ${strings.transferCalculating}"
            }
            else -> strings.transferCalculating
        }
    }

    /**
     * Transferência concluída:
     * "1,9 GB em 2 min 31 s (12,6 MB/s média)".
     * Menos de 1 segundo arredonda para cima para 1 s.
     */
    fun transferSummary(bytes: Long, durationMs: Long, bytesPerSec: Long, strings: AppStrings): String {
        val durationSecs = if (durationMs > 0L) (durationMs + 999L) / 1000L else 0L
        val s = formatBytes(bytes, strings.decimalComma)
        val d = formatDuration(durationSecs, strings)
        val r = formatRate(bytesPerSec, strings.decimalComma)
        return strings.transferSummary(s, d, r)
    }
}
