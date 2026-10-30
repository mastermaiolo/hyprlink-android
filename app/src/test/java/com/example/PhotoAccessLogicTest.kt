// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAccessLogicTest {

    @Test
    fun testPhotoAccessStatusEnum() {
        assertEquals(3, PhotoAccessStatus.values().size)
        assertTrue(PhotoAccessStatus.values().contains(PhotoAccessStatus.FULL))
        assertTrue(PhotoAccessStatus.values().contains(PhotoAccessStatus.PARTIAL))
        assertTrue(PhotoAccessStatus.values().contains(PhotoAccessStatus.DENIED))
    }

    @Test
    fun testStringsPartialAccessContainExplanations() {
        val pt = getAppStrings(AppLanguage.PT_PT)
        assertTrue(pt.partialPhotosAccessWarning.contains("Acesso parcial"))
        assertTrue(pt.partialPhotosAccessWarning.contains("todas as fotos"))
        assertEquals("PARCIAL", pt.tagPartialAccess)
        assertEquals("PERMITIR TODAS AS FOTOS", pt.btnAllowAllPhotos)
    }
}
