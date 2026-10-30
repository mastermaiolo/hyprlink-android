// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import com.example.AppLanguage
import com.example.getAppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManufacturerGuidanceTest {

    @Test
    fun testRestrictedSettingsGuideText() {
        val pt = getAppStrings(AppLanguage.PT_PT)
        assertTrue(pt.restrictedSettingsGuide.contains("Definições"))
        assertTrue(pt.restrictedSettingsGuide.contains("Permitir definições restritas"))
    }

    @Test
    fun testManufacturerGuidanceStringsExist() {
        val pt = getAppStrings(AppLanguage.PT_PT)
        assertNotNull(pt.xiaomiAutostartGuide)
        assertNotNull(pt.samsungAutostartGuide)
        assertNotNull(pt.oppoAutostartGuide)

        assertTrue(pt.xiaomiAutostartGuide.contains("Início automático"))
        assertTrue(pt.samsungAutostartGuide.contains("Sem restrições"))
        assertTrue(pt.oppoAutostartGuide.contains("segundo plano"))
    }
}
