// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStoreException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingValidationLogicTest {

    @Test
    fun testNullIdentityCannotPair() {
        assertFalse(
            "Identidade nula não pode emparelhar",
            PairingValidationLogic.canPairWithIdentity(null)
        )
    }

    @Test
    fun testTemporaryIdentityCannotPair() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DeviceIdentity.resetInstanceForTesting()
        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            throw KeyStoreException("Keystore locked")
        }

        val tempIdentity = DeviceIdentity.getInstance(context)
        assertTrue("Deve ser identidade temporária", tempIdentity.isTemporary)
        assertFalse(
            "Identidade temporária deve ser terminantemente recusada para emparelhamento",
            PairingValidationLogic.canPairWithIdentity(tempIdentity)
        )
    }

    @Test
    fun testPersistentIdentityCanPair() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPrefs = context.getSharedPreferences("persistent_pair_prefs", Context.MODE_PRIVATE)
        testPrefs.edit().clear().commit()

        DeviceIdentity.resetInstanceForTesting()
        DeviceIdentity.prefsFactory = { testPrefs }

        val persistentIdentity = DeviceIdentity.getInstance(context)
        assertFalse("Não deve ser temporária", persistentIdentity.isTemporary)
        assertTrue(
            "Identidade persistente válida tem permissão para emparelhar",
            PairingValidationLogic.canPairWithIdentity(persistentIdentity)
        )
    }

    @Test
    fun testAppStringsInAllFiveLanguages() {
        for (lang in listOf(AppLanguage.PT_PT, AppLanguage.PT_BR, AppLanguage.EN_UK, AppLanguage.ES_ES, AppLanguage.ZH_CN)) {
            val strings = getAppStrings(lang)
            val msg = strings.pairingTemporaryIdentityBlocked
            assertNotNull(msg)
            assertTrue("Mensagem de bloqueio temporário deve estar preenchida em $lang", msg.isNotBlank())
        }
        assertEquals(
            "Armazenamento seguro indisponível. Desbloqueia o telemóvel e tenta de novo.",
            getAppStrings(AppLanguage.PT_PT).pairingTemporaryIdentityBlocked
        )
    }
}
