// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import com.example.ui.KnownManufacturer
import com.example.ui.ManufacturerGuidance
import org.junit.Assert.*
import org.junit.Test

class ManufacturerGuidanceTest {

    @Test
    fun testXiaomiManufacturer_whenMiuiSecurityCenterInstalled_resolvesXiaomi() {
        val resolved = ManufacturerGuidance.resolveManufacturer(
            manufacturer = "Xiaomi"
        ) { pkg, activity ->
            pkg == "com.miui.securitycenter"
        }
        assertEquals(KnownManufacturer.XIAOMI, resolved)
    }

    @Test
    fun testPocoManufacturer_whenMiuiSecurityCenterNotInstalled_resolvesNone() {
        // Caso do Poco F4 com AOSP/Pixel Experience: Build.MANUFACTURER diz Xiaomi/Poco, mas não tem com.miui.securitycenter
        val resolved = ManufacturerGuidance.resolveManufacturer(
            manufacturer = "POCO"
        ) { pkg, activity ->
            false // sem pacotes MIUI
        }
        assertEquals(KnownManufacturer.NONE, resolved)
    }

    @Test
    fun testOppoManufacturer_whenColorOsCenterInstalled_resolvesOppo() {
        val resolved = ManufacturerGuidance.resolveManufacturer(
            manufacturer = "OPPO"
        ) { pkg, activity ->
            pkg == "com.coloros.safecenter"
        }
        assertEquals(KnownManufacturer.OPPO, resolved)
    }

    @Test
    fun testRealmeManufacturer_whenSecurityCentersNotInstalled_resolvesNone() {
        val resolved = ManufacturerGuidance.resolveManufacturer(
            manufacturer = "realme"
        ) { pkg, activity ->
            false
        }
        assertEquals(KnownManufacturer.NONE, resolved)
    }

    @Test
    fun testSamsungManufacturer_alwaysResolvesSamsung() {
        val resolved = ManufacturerGuidance.resolveManufacturer(
            manufacturer = "samsung"
        ) { _, _ ->
            false
        }
        assertEquals(KnownManufacturer.SAMSUNG, resolved)
    }

    @Test
    fun testGenericManufacturer_resolvesNone() {
        val resolved = ManufacturerGuidance.resolveManufacturer(
            manufacturer = "Google"
        ) { _, _ ->
            false
        }
        assertEquals(KnownManufacturer.NONE, resolved)
    }

    @Test
    fun testGuideText_forNone_returnsGenericAutostartGuide() {
        val ptStrings = StringsPtPt()
        val guide = ptStrings.genericAutostartGuide
        assertTrue("Deve conter 'Otimização de bateria'", guide.contains("Otimização de bateria"))
        assertTrue("Deve conter 'Sem restrições'", guide.contains("Sem restrições"))

        val enStrings = StringsEnUk()
        assertTrue("Deve conter 'Unrestricted'", enStrings.genericAutostartGuide.contains("Unrestricted"))
    }
}
