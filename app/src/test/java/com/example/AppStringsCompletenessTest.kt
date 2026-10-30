// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

class AppStringsCompletenessTest {

    private val ptPt = StringsPtPt()
    private val ptBr = StringsPtBr()
    private val enUk = StringsEnUk()
    private val esEs = StringsEsEs()
    private val zhCn = StringsZhCn()

    private val stringGetters: List<Method> by lazy {
        AppStrings::class.java.methods.filter {
            it.parameterCount == 0 && it.returnType == String::class.java
        }
    }

    private val whitelistIdentical = setOf(
        "PC", "QR", "Wi-Fi", "OK", "LAN", "ms", "dBm", "GB", "%", "TEMP", "HOST",
        "0.5×", "1×", "2×", "5×", "—", "··", "✕", "HyprLink", "HYPRLINK", "QUIC",
        "Webcam", "WEBCAM", "SIL Open Font License 1.1", "CPU", "GPU", "TX/RX", "CODEC",
        "USB", "WI-FI",
        "↑ PC", "TERMINAL · —", "MANUAL",
        "FECHAR ✕", "CLOSE ✕", "CERRAR ✕", "关闭 ✕",
        "1 DEDO MOVE · 2 DEDOS SCROLL", "1 FINGER MOVE · 2 FINGERS SCROLL",
        "1 DEDO MUEVE · 2 DEDOS SCROLL", "单指移动 · 双指滚动",
        "Banner"
    )

    @Test
    fun testNoEmptyStringsInAnyLanguage() {
        val languages = listOf(
            "pt-PT" to ptPt,
            "pt-BR" to ptBr,
            "en-UK" to enUk,
            "es-ES" to esEs,
            "zh-CN" to zhCn
        )

        for ((langName, langObj) in languages) {
            for (method in stringGetters) {
                val value = method.invoke(langObj) as? String
                assertTrue(
                    "[$langName] Field '${method.name}' must not be null or blank",
                    !value.isNullOrBlank()
                )
            }
        }
    }

    @Test
    fun testEnAndZhDoNotLeakPortugueseText() {
        for (method in stringGetters) {
            val ptValue = method.invoke(ptPt) as String
            val enValue = method.invoke(enUk) as String
            val zhValue = method.invoke(zhCn) as String

            // Skip technical abbreviations, numbers or symbols
            if (whitelistIdentical.contains(ptValue.trim()) || ptValue.matches(Regex("^[0-9%×·—\\s]+$"))) {
                continue
            }

            // In EN, the text should not be identical to PT unless whitelisted
            assertFalse(
                "Field '${method.name}' in EN-UK ('$enValue') is identical to PT-PT ('$ptValue')",
                enValue == ptValue
            )

            // In ZH, Chinese should contain Chinese characters or whitelisted term
            assertFalse(
                "Field '${method.name}' in ZH-CN ('$zhValue') is identical to PT-PT ('$ptValue')",
                zhValue == ptValue
            )
        }
    }

    @Test
    fun testPtPtFollowsEuropeanPortugueseVocabulary() {
        val forbiddenPtBrTerms = listOf("conexão", "tela", "arquivo", "celular", "você")

        for (method in stringGetters) {
            val ptValue = (method.invoke(ptPt) as String).lowercase()
            for (term in forbiddenPtBrTerms) {
                assertFalse(
                    "PT-PT field '${method.name}' contains forbidden Brazilian term '$term': '$ptValue'",
                    ptValue.contains(Regex("\\b$term\\b"))
                )
            }
        }
    }
}
