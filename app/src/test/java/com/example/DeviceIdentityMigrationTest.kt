// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceIdentityMigrationTest {

    @Test
    fun testFallbackMigrationAndDeletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Simular a existência de chave legada no fallback em texto limpo
        val fallbackPrefs = context.getSharedPreferences("hyprlink_secure_prefs_fallback", Context.MODE_PRIVATE)
        val dummyKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgDummyPrivateKey123"
        val dummyCert = "MIIBkzCCATigAwIBAgIIDummyCert123"
        fallbackPrefs.edit()
            .putString("device_private_key", dummyKey)
            .putString("device_certificate", dummyCert)
            .commit()

        val mockSecurePrefs = context.getSharedPreferences("test_simulated_secure_prefs", Context.MODE_PRIVATE)

        // 2. Executar a migração
        DeviceIdentity.migrateFallbackIfNeeded(context, mockSecurePrefs)

        // 3. Garantir que as credenciais foram migradas para o destino seguro
        assertEquals(dummyKey, mockSecurePrefs.getString("device_private_key", null))
        assertEquals(dummyCert, mockSecurePrefs.getString("device_certificate", null))

        // 4. Garantir que o fallback em texto limpo foi completamente limpo
        assertNull(fallbackPrefs.getString("device_private_key", null))
        assertNull(fallbackPrefs.getString("device_certificate", null))
    }
}
