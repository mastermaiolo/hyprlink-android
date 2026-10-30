// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários para [TransferRateMeter] e [TransferRateFormatter] (prompt 61 Parte B).
 * Paridade com os testes em hyprlink-proto::rate e hyprlink-proto::fmt.
 */
class TransferRateMeterTest {

    @Test
    fun arranqueSemAmostraNaoTemVelocidadeNemEta() {
        val m = TransferRateMeter()
        assertNull(m.bytesPerSec())
        m.sample(200L, 1_000_000L) // ainda não passaram 500 ms
        assertNull(m.bytesPerSec())
        assertNull(m.etaSeconds(200L, 1_000_000L, 10_000_000L))
    }

    @Test
    fun ritmoConstanteDaAVelocidadeEOEtaCertos() {
        val m = TransferRateMeter()
        // 10 MB/s, amostras a cada 500 ms (e refrescos a meio, que não contam).
        for (i in 1L..8L) {
            m.sample(i * 500L - 250L, (i * 500L - 250L) * 10_000L)
            m.sample(i * 500L, i * 5_000_000L)
        }
        assertEquals(10_000_000L, m.bytesPerSec())
        // 4 s, 40 MB de 100 MB → faltam 6 s.
        assertEquals(6L, m.etaSeconds(4000L, 40_000_000L, 100_000_000L))
    }

    @Test
    fun etaSoDepoisDeDoisSegundos() {
        val m = TransferRateMeter()
        m.sample(500L, 5_000_000L)
        m.sample(1000L, 10_000_000L)
        assertNotNull(m.bytesPerSec())
        assertNull(m.etaSeconds(1999L, 10_000_000L, 100_000_000L))
        assertNotNull(m.etaSeconds(2000L, 10_000_000L, 100_000_000L))
    }

    @Test
    fun umPicoNaoSaltaAVelocidade() {
        val m = TransferRateMeter()
        m.sample(500L, 5_000_000L) // 10 MB/s
        m.sample(1000L, 10_000_000L) // 10 MB/s
        m.sample(1500L, 25_000_000L) // pico de 30 MB/s
        // 0,3·30 + 0,7·10 = 16 MB/s, não 30.
        assertEquals(16_000_000L, m.bytesPerSec())
    }

    @Test
    fun pausaFazDescerAVelocidadeEParadoNaoTemEta() {
        val m = TransferRateMeter()
        m.sample(500L, 5_000_000L)
        m.sample(1000L, 10_000_000L)
        val before = m.bytesPerSec()!!
        for (i in 3L..60L) {
            m.sample(i * 500L, 10_000_000L)
        }
        assertTrue(m.bytesPerSec()!! < before / 100L)
        // Abaixo de 1 B/s conta como parado.
        assertNull(m.etaSeconds(30_000L, 10_000_000L, 20_000_000L))
    }

    @Test
    fun fimEtaZeroEMedia() {
        val m = TransferRateMeter()
        m.sample(1000L, 10_000_000L)
        m.sample(2000L, 20_000_000L)
        assertEquals(0L, m.etaSeconds(2000L, 20_000_000L, 20_000_000L))
        assertEquals(
            12_582_781L,
            TransferRateMeter.average(1_900_000_000L, 151_000L)
        )
        assertNull(TransferRateMeter.average(10L, 0L))
    }

    @Test
    fun formatosPtPt() {
        val pt = StringsPtPt()
        assertEquals("12,4 MB/s", TransferRateFormatter.formatRate(12_400_000L, pt.decimalComma))
        assertEquals("512 B/s", TransferRateFormatter.formatRate(512L, pt.decimalComma))
        assertEquals("45 s", TransferRateFormatter.formatDuration(45L, pt))
        assertEquals("2 min 10 s", TransferRateFormatter.formatDuration(130L, pt))
        assertEquals("1 h 05 min", TransferRateFormatter.formatDuration(3900L, pt))
        assertEquals(
            "12,4 MB/s · faltam 2 min 10 s",
            TransferRateFormatter.transferSpeed(12_400_000L, 130L, pt)
        )
        assertEquals(
            "12,4 MB/s · faltam 45 s",
            TransferRateFormatter.transferSpeed(12_400_000L, 45L, pt)
        )
        assertEquals(
            "12,4 MB/s · a calcular…",
            TransferRateFormatter.transferSpeed(12_400_000L, null, pt)
        )
        assertEquals(
            "a calcular…",
            TransferRateFormatter.transferSpeed(null, null, pt)
        )
        assertEquals(
            "1,9 GB em 2 min 31 s (12,6 MB/s média)",
            TransferRateFormatter.transferSummary(1_900_000_000L, 151_000L, 12_600_000L, pt)
        )
        assertEquals(
            "4,8 MB em 1 s (9,0 MB/s média)",
            TransferRateFormatter.transferSummary(4_812_334L, 535L, 9_000_000L, pt)
        )
    }

    @Test
    fun formatosPtBr() {
        val br = StringsPtBr()
        assertEquals(
            "12,4 MB/s · faltam 2 min 10 s",
            TransferRateFormatter.transferSpeed(12_400_000L, 130L, br)
        )
        assertEquals(
            "calculando…",
            TransferRateFormatter.transferSpeed(null, null, br)
        )
        assertEquals(
            "1,9 GB em 2 min 31 s (média de 12,6 MB/s)",
            TransferRateFormatter.transferSummary(1_900_000_000L, 151_000L, 12_600_000L, br)
        )
    }

    @Test
    fun formatosEnUk() {
        val en = StringsEnUk()
        assertEquals("12.4 MB/s", TransferRateFormatter.formatRate(12_400_000L, en.decimalComma))
        assertEquals(
            "12.4 MB/s · 2 min 10 s left",
            TransferRateFormatter.transferSpeed(12_400_000L, 130L, en)
        )
        assertEquals(
            "calculating…",
            TransferRateFormatter.transferSpeed(null, null, en)
        )
        assertEquals(
            "1.9 GB in 2 min 31 s (12.6 MB/s average)",
            TransferRateFormatter.transferSummary(1_900_000_000L, 151_000L, 12_600_000L, en)
        )
    }

    @Test
    fun formatosEsEs() {
        val es = StringsEsEs()
        assertEquals(
            "12,4 MB/s · quedan 2 min 10 s",
            TransferRateFormatter.transferSpeed(12_400_000L, 130L, es)
        )
        assertEquals(
            "calculando…",
            TransferRateFormatter.transferSpeed(null, null, es)
        )
        assertEquals(
            "1,9 GB en 2 min 31 s (12,6 MB/s de media)",
            TransferRateFormatter.transferSummary(1_900_000_000L, 151_000L, 12_600_000L, es)
        )
    }

    @Test
    fun formatosZhCn() {
        val zh = StringsZhCn()
        assertEquals("12.4 MB/s", TransferRateFormatter.formatRate(12_400_000L, zh.decimalComma))
        assertEquals("45 秒", TransferRateFormatter.formatDuration(45L, zh))
        assertEquals("2 分 10 秒", TransferRateFormatter.formatDuration(130L, zh))
        assertEquals("1 小时 05 分", TransferRateFormatter.formatDuration(3900L, zh))
        assertEquals(
            "12.4 MB/s · 剩余 2 分 10 秒",
            TransferRateFormatter.transferSpeed(12_400_000L, 130L, zh)
        )
        assertEquals(
            "计算中…",
            TransferRateFormatter.transferSpeed(null, null, zh)
        )
        assertEquals(
            "1.9 GB，用时 2 分 31 秒（平均 12.6 MB/s）",
            TransferRateFormatter.transferSummary(1_900_000_000L, 151_000L, 12_600_000L, zh)
        )
    }
}
