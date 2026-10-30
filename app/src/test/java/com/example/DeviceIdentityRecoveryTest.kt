// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import java.security.KeyStoreException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceIdentityRecoveryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DeviceIdentity.resetInstanceForTesting()
        DeviceIdentity.clearIdentityResetFlag(context)
        context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        DeviceIdentity.resetInstanceForTesting()
        DeviceIdentity.clearIdentityResetFlag(context)
        context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testMigrateFallback_whenCommitFails_preservesFallback() {
        val fallbackPrefs = context.getSharedPreferences("hyprlink_secure_prefs_fallback", Context.MODE_PRIVATE)
        val dummyKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgDummyKey123"
        val dummyCert = "MIIBkzCCATigAwIBAgIIDummyCert123"
        fallbackPrefs.edit()
            .putString("device_private_key", dummyKey)
            .putString("device_certificate", dummyCert)
            .commit()

        val realPrefs = context.getSharedPreferences("failing_commit_test_prefs", Context.MODE_PRIVATE)

        // Wrapper de SharedPreferences cujo Editor devolve sempre false em commit()
        val failingPrefs = object : SharedPreferences by realPrefs {
            override fun edit(): SharedPreferences.Editor {
                val realEditor = realPrefs.edit()
                return object : SharedPreferences.Editor by realEditor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        realEditor.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        return false // Simula falha de escrita no armazenamento
                    }
                }
            }
        }

        DeviceIdentity.migrateFallbackIfNeeded(context, failingPrefs)

        // Fallback não pode ter sido apagado porque o commit falhou
        assertEquals(dummyKey, fallbackPrefs.getString("device_private_key", null))
        assertEquals(dummyCert, fallbackPrefs.getString("device_certificate", null))
    }

    @Test
    fun testTransientFailure_recoversDuringBackoff() {
        var attempts = 0
        val targetPrefs = context.getSharedPreferences("transient_success_prefs", Context.MODE_PRIVATE)
        targetPrefs.edit().clear().commit()

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L, 1L)
        DeviceIdentity.prefsFactory = { _ ->
            attempts++
            if (attempts == 1) {
                throw KeyStoreException("Device locked - Keystore not available yet")
            }
            targetPrefs
        }

        val identity = DeviceIdentity.getInstance(context)

        assertFalse("Não deve ser temporária após recuperação", identity.isTemporary)
        assertFalse("Não deve ter sido purgada e recriada por corrupção", identity.wasRecreatedDueToCorruption)
        assertFalse("Flag global de aviso não deve estar ativa", DeviceIdentity.hasIdentityBeenResetDueToCorruption)
        assertTrue("Deve ter tentado mais de 1 vez", attempts > 1)
        assertNotNull(identity.certificate)
        assertNotNull(identity.privateKey)
    }

    @Test
    fun testGeneralSecurityException_keystoreNotInitialized_isTreatedAsTransient() {
        val exc = GeneralSecurityException("Keystore not initialized")
        assertFalse("GeneralSecurityException genérica não deve ser permanente", DeviceIdentity.isPermanentFailure(exc))

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            throw exc
        }

        val identity = DeviceIdentity.getInstance(context)
        assertTrue("Deve resultar em identidade temporária", identity.isTemporary)
        assertFalse("Não deve recriar nem purgar", identity.wasRecreatedDueToCorruption)
    }

    @Test
    fun testPermanentFailure_firstBoot_doesNotPurge_treatedAsTransient() {
        val normalPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        normalPrefs.edit().remove("permanent_failure_count").commit()

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            throw javax.crypto.AEADBadTagException("mac check failed")
        }

        val identity = DeviceIdentity.getInstance(context)
        assertTrue("No 1.º arranque com falha permanente deve ser tratada como temporária", identity.isTemporary)
        assertFalse("Não deve purgar no primeiro arranque", identity.wasRecreatedDueToCorruption)
        assertEquals("Contador de falhas deve ser incrementado para 1", 1, normalPrefs.getInt("permanent_failure_count", 0))
    }

    @Test
    fun testPermanentFailure_multipleCallsSameProcess_doesNotIncrementPastOneAndDoesNotPurge() {
        // (a) várias chamadas no mesmo processo com AEADBadTag -> contador = 1 e nada é apagado
        val normalPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        normalPrefs.edit().remove("permanent_failure_count").commit()

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            throw javax.crypto.AEADBadTagException("mac check failed")
        }

        val id1 = DeviceIdentity.getInstance(context)
        assertTrue(id1.isTemporary)
        assertFalse(id1.wasRecreatedDueToCorruption)
        assertEquals(1, normalPrefs.getInt("permanent_failure_count", 0))

        val id2 = DeviceIdentity.getInstance(context)
        assertTrue(id2.isTemporary)
        assertFalse(id2.wasRecreatedDueToCorruption)
        assertEquals("Contador deve manter-se em 1 no mesmo processo", 1, normalPrefs.getInt("permanent_failure_count", 0))

        val id3 = DeviceIdentity.getInstance(context)
        assertTrue(id3.isTemporary)
        assertFalse(id3.wasRecreatedDueToCorruption)
        assertEquals("Contador não deve subir para 2 nem purgar no mesmo processo", 1, normalPrefs.getInt("permanent_failure_count", 0))
    }

    @Test
    fun testPermanentFailure_secondBoot_purgesAndRecreates() {
        // (b) simula um novo processo (repõe a flag com resetInstanceForTesting) com AEADBadTag -> purga e recria
        val normalPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        normalPrefs.edit().putInt("permanent_failure_count", 1).commit()

        var attempts = 0
        val targetPrefs = context.getSharedPreferences("permanent_recreate_prefs_2", Context.MODE_PRIVATE)
        targetPrefs.edit().clear().commit()

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            attempts++
            if (attempts <= 2) {
                throw javax.crypto.AEADBadTagException("mac check failed")
            }
            targetPrefs
        }

        val identity = DeviceIdentity.getInstance(context)
        assertTrue("No 2.º arranque consecutivo deve purgar e recriar", identity.wasRecreatedDueToCorruption)
        assertFalse("Não é temporária porque foi persistida no novo cofre limpo", identity.isTemporary)
        assertEquals("Contador de falhas permanentes deve ser reposto a zero após recriação", 0, normalPrefs.getInt("permanent_failure_count", 0))
        assertNotNull(targetPrefs.getString("device_private_key", null))
        assertNotNull(targetPrefs.getString("device_certificate", null))
    }

    @Test
    fun testPermanentFailure_interveningSuccess_resetsFailureCountToZero() {
        // (c) um sucesso pelo meio põe o contador a 0
        val normalPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        normalPrefs.edit().putInt("permanent_failure_count", 1).commit()

        val successPrefs = context.getSharedPreferences("p47c_success_prefs", Context.MODE_PRIVATE)
        successPrefs.edit().clear().commit()

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            successPrefs
        }

        val identity = DeviceIdentity.getInstance(context)
        assertFalse(identity.isTemporary)
        assertEquals("Sucesso no arranque deve repor permanent_failure_count a 0", 0, normalPrefs.getInt("permanent_failure_count", 0))
        assertFalse("Flag countedPermanentFailureThisProcess deve ser reposta a false", DeviceIdentity.countedPermanentFailureThisProcess)
    }

    @Test
    fun testTransientFailure_persistingUntilEnd_doesNotPurgeAndMarksTemporary() {
        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            throw KeyStoreException("Keystore locked before user unlock")
        }

        val identity = DeviceIdentity.getInstance(context)

        assertTrue("Identidade mantida como temporária em RAM", identity.isTemporary)
        assertFalse("Não deve ter ocorrido reset permanente de identidade", identity.wasRecreatedDueToCorruption)
        assertFalse("Não deve mostrar aviso de corrupção para falhas transitórias", DeviceIdentity.hasIdentityBeenResetDueToCorruption)
    }

    @Test
    fun testTemporaryIdentityNotCached_secondCallReturnsPersistent() {
        DeviceIdentity.resetInstanceForTesting()
        var attempts = 0
        val targetPrefs = context.getSharedPreferences("temp_recovery_prefs", Context.MODE_PRIVATE)
        targetPrefs.edit().clear().commit()

        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            attempts++
            if (attempts <= 2) {
                throw KeyStoreException("Keystore locked")
            }
            targetPrefs
        }

        // Primeira chamada falha de forma transitória
        val firstIdentity = DeviceIdentity.getInstance(context)
        assertTrue("Primeira identidade deve ser temporária", firstIdentity.isTemporary)
        assertNull("INSTANCE não deve reter identidade temporária", DeviceIdentity.peekInstance())

        // Segunda chamada (após desbloqueio do Keystore) tem sucesso e persiste
        val secondIdentity = DeviceIdentity.getInstance(context)
        assertFalse("Segunda identidade deve ser persistente", secondIdentity.isTemporary)
        assertEquals("Deve ter guardado a identidade persistente em INSTANCE", secondIdentity, DeviceIdentity.peekInstance())
    }

    @Test
    fun testCorruptionResetFlag_survivesProcessAndClearedOnDismiss() {
        val normalPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        normalPrefs.edit().putInt("permanent_failure_count", 1).commit()

        val targetPrefs = context.getSharedPreferences("p5_corruption_prefs", Context.MODE_PRIVATE)
        targetPrefs.edit().clear().commit()

        var attempts = 0
        DeviceIdentity.backoffDelaysMs = longArrayOf(1L)
        DeviceIdentity.prefsFactory = { _ ->
            attempts++
            if (attempts <= 2) {
                throw javax.crypto.AEADBadTagException("mac check failed")
            }
            targetPrefs
        }

        val identity = DeviceIdentity.getInstance(context)
        assertTrue("Deve ter sido recriada por corrupção no 2.º arranque", identity.wasRecreatedDueToCorruption)
        assertTrue("A flag em memória deve estar ativa", DeviceIdentity.hasIdentityBeenResetDueToCorruption)
        assertTrue("A flag persistente em hyprlink_prefs deve estar ativa", DeviceIdentity.hasIdentityBeenResetDueToCorruption(context))

        // Simula reinício de processo: INSTANCE é nula e variável de memória é reposta
        DeviceIdentity.resetInstanceForTesting()
        assertFalse("Em memória a flag foi limpa com o processo", DeviceIdentity.hasIdentityBeenResetDueToCorruption)
        assertTrue("Mas a flag persistente sobreviveu à morte do processo", DeviceIdentity.hasIdentityBeenResetDueToCorruption(context))

        // Simula fechar o aviso/banner
        DeviceIdentity.clearIdentityResetFlag(context)
        assertFalse("Após fechar o banner, a flag não deve estar ativa", DeviceIdentity.hasIdentityBeenResetDueToCorruption(context))
        assertFalse(normalPrefs.getBoolean(DeviceIdentity.PREF_IDENTITY_RESET_DUE_TO_CORRUPTION, false))
    }
}
