// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class DeviceIdentity private constructor(context: Context) {

    val certificate: X509Certificate
    val privateKey: PrivateKey
    val fingerprintHex: String
    val isTemporary: Boolean
    val wasRecreatedDueToCorruption: Boolean

    init {
        // Register Bouncy Castle Provider
        Security.addProvider(BouncyCastleProvider())

        val (sharedPrefs, wasRecreated) = getSecurePreferencesWithRecovery(context)
        wasRecreatedDueToCorruption = wasRecreated
        if (wasRecreated) {
            hasIdentityBeenResetDueToCorruption = true
            setIdentityResetDueToCorruption(context, true)
        }

        if (sharedPrefs != null) {
            isTemporary = false
            migrateFallbackIfNeeded(context, sharedPrefs)
        } else {
            isTemporary = true
            ConnectionRepository.appendLog("[SECURITY] Armazenamento seguro indisponível temporariamente. Identidade retida em memória sem autorização de ligação.")
        }

        val cachedKey = sharedPrefs?.getString("device_private_key", null)
        val cachedCert = sharedPrefs?.getString("device_certificate", null)

        var tempPrivateKey: PrivateKey? = null
        var tempCertificate: X509Certificate? = null

        if (cachedKey != null && cachedCert != null && !wasRecreated) {
            try {
                val privateKeyBytes = Base64.decode(cachedKey, Base64.DEFAULT)
                val certBytes = Base64.decode(cachedCert, Base64.DEFAULT)

                val kf = KeyFactory.getInstance("EC")
                tempPrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

                val cf = CertificateFactory.getInstance("X.509")
                tempCertificate = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            } catch (e: Exception) {
                // If parsing fails for any reason, let them remain null so they are regenerated
            }
        }

        if (tempPrivateKey != null && tempCertificate != null) {
            privateKey = tempPrivateKey
            certificate = tempCertificate
        } else {
            val identity = generateNewIdentity(context)
            privateKey = identity.privateKey
            certificate = identity.certificate
            if (sharedPrefs != null) {
                saveIdentity(sharedPrefs, privateKey, certificate)
            }
        }

        // Calculate SHA-256 fingerprint of the full DER cert
        val md = MessageDigest.getInstance("SHA-256")
        val fingerprintBytes = md.digest(certificate.encoded)
        fingerprintHex = fingerprintBytes.joinToString("") { "%02X".format(it) }
    }

    private data class GeneratedKeyPairAndCert(val privateKey: PrivateKey, val certificate: X509Certificate)

    companion object {
        @Volatile
        private var INSTANCE: DeviceIdentity? = null

        @Volatile
        var hasIdentityBeenResetDueToCorruption: Boolean = false

        const val PREF_IDENTITY_RESET_DUE_TO_CORRUPTION = "identity_reset_due_to_corruption"

        fun hasIdentityBeenResetDueToCorruption(context: Context): Boolean {
            val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_IDENTITY_RESET_DUE_TO_CORRUPTION, false) || hasIdentityBeenResetDueToCorruption
        }

        fun clearIdentityResetFlag(context: Context) {
            hasIdentityBeenResetDueToCorruption = false
            val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_IDENTITY_RESET_DUE_TO_CORRUPTION).commit()
        }

        @VisibleForTesting
        fun setIdentityResetDueToCorruption(context: Context, value: Boolean) {
            hasIdentityBeenResetDueToCorruption = value
            val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            if (value) {
                prefs.edit().putBoolean(PREF_IDENTITY_RESET_DUE_TO_CORRUPTION, true).commit()
            } else {
                prefs.edit().remove(PREF_IDENTITY_RESET_DUE_TO_CORRUPTION).commit()
            }
        }

        @VisibleForTesting
        @Volatile
        var prefsFactory: ((Context) -> android.content.SharedPreferences)? = null

        @VisibleForTesting
        @Volatile
        var allowMainThreadSleepForTesting: Boolean = false

        @VisibleForTesting
        @Volatile
        var backoffDelaysMs: LongArray = longArrayOf(2000L, 10000L, 60000L)

        @VisibleForTesting
        @Volatile
        var countedPermanentFailureThisProcess: Boolean = false

        @VisibleForTesting
        fun resetInstanceForTesting() {
            synchronized(this) {
                INSTANCE = null
                hasIdentityBeenResetDueToCorruption = false
                countedPermanentFailureThisProcess = false
                prefsFactory = null
                backoffDelaysMs = longArrayOf(2000L, 10000L, 60000L)
                allowMainThreadSleepForTesting = true
            }
        }

        fun peekInstance(): DeviceIdentity? {
            val inst = INSTANCE
            return if (inst != null && !inst.isTemporary) inst else null
        }

        fun getInstance(context: Context): DeviceIdentity {
            val existing = INSTANCE
            if (existing != null && !existing.isTemporary) {
                return existing
            }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && !current.isTemporary) {
                    current
                } else {
                    val newIdentity = DeviceIdentity(context.applicationContext)
                    if (!newIdentity.isTemporary) {
                        INSTANCE = newIdentity
                    }
                    newIdentity
                }
            }
        }

        private fun createPrefsInstance(context: Context): android.content.SharedPreferences {
            val custom = prefsFactory
            if (custom != null) {
                return custom(context)
            }
            return createEncryptedPrefs(context)
        }

        private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "hyprlink_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        fun isPermanentFailure(t: Throwable): Boolean {
            var cur: Throwable? = t
            while (cur != null) {
                val className = cur.javaClass.name
                if (cur is javax.crypto.AEADBadTagException ||
                    cur is javax.crypto.BadPaddingException ||
                    cur is android.security.keystore.KeyPermanentlyInvalidatedException ||
                    className.contains("AEADBadTag", ignoreCase = true) ||
                    className.contains("BadPadding", ignoreCase = true) ||
                    className.contains("InvalidProtocolBuffer", ignoreCase = true) ||
                    className.contains("KeyPermanentlyInvalidated", ignoreCase = true) ||
                    className.contains("CorruptedKey", ignoreCase = true) ||
                    className.contains("TinkBugException", ignoreCase = true)
                ) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }

        fun purgeSecureStorage(context: Context) {
            try {
                context.deleteSharedPreferences("hyprlink_secure_prefs")
                context.deleteSharedPreferences("__androidx_security_crypto_encrypted_prefs__")
                val dir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(dir, "hyprlink_secure_prefs.xml").delete()
                File(dir, "__androidx_security_crypto_encrypted_prefs__.xml").delete()
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Erro ao eliminar ficheiros de preferências seguras: ${e.message}")
            }

            try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                if (ks.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                    ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
                if (ks.containsAlias("_androidx_security_master_key_")) {
                    ks.deleteEntry("_androidx_security_master_key_")
                }
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Erro ao limpar entrada da MasterKey no AndroidKeyStore: ${e.message}")
            }
        }

        fun getSecurePreferencesWithRecovery(context: Context): Pair<android.content.SharedPreferences?, Boolean> {
            val normalPrefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            var lastError: Throwable? = null
            try {
                val prefs = createPrefsInstance(context)
                if (normalPrefs.getInt("permanent_failure_count", 0) != 0) {
                    normalPrefs.edit().putInt("permanent_failure_count", 0).commit()
                }
                countedPermanentFailureThisProcess = false
                return Pair(prefs, false)
            } catch (e: Throwable) {
                lastError = e
                ConnectionRepository.appendLog("[SECURITY] Falha na tentativa inicial de armazenamento seguro: ${e.message}")
            }

            val isMainThread = android.os.Looper.myLooper() != null &&
                               android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            if (isMainThread && !allowMainThreadSleepForTesting) {
                ConnectionRepository.appendLog("[WARN] Tentativa de inicialização de identidade na thread principal. Recuo com sleep evitado.")
                return Pair(null, false)
            }

            val delays = backoffDelaysMs
            for ((index, delayMs) in delays.withIndex()) {
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                try {
                    ConnectionRepository.appendLog("[SECURITY] Tentativa de recuo (${index + 1}/${delays.size}) ao armazenamento seguro...")
                    val prefs = createPrefsInstance(context)
                    ConnectionRepository.appendLog("[SECURITY] Armazenamento seguro recuperado com sucesso durante recuo.")
                    if (normalPrefs.getInt("permanent_failure_count", 0) != 0) {
                        normalPrefs.edit().putInt("permanent_failure_count", 0).commit()
                    }
                    countedPermanentFailureThisProcess = false
                    return Pair(prefs, false)
                } catch (retryError: Throwable) {
                    lastError = retryError
                    ConnectionRepository.appendLog("[SECURITY] Falha no recuo (${index + 1}): ${retryError.message}")
                }
            }

            if (isPermanentFailure(lastError)) {
                var currentFailures = normalPrefs.getInt("permanent_failure_count", 0)
                if (!countedPermanentFailureThisProcess) {
                    currentFailures += 1
                    normalPrefs.edit().putInt("permanent_failure_count", currentFailures).commit()
                    countedPermanentFailureThisProcess = true
                }

                if (currentFailures < 2) {
                    ConnectionRepository.appendLog("[SECURITY] Falha permanente detetada no 1.º arranque ($currentFailures/2). Tratada como transitória sem purga nem nova identidade.")
                    return Pair(null, false)
                }

                ConnectionRepository.appendLog("[ERROR] Falha permanente confirmada em 2 arranques consecutivos ($currentFailures). A purgar armazenamento seguro e a recriar identidade...")
                purgeSecureStorage(context)
                normalPrefs.edit().putInt("permanent_failure_count", 0).commit()
                countedPermanentFailureThisProcess = false
                try {
                    val recreatedPrefs = createPrefsInstance(context)
                    ConnectionRepository.appendLog("[SECURITY] Armazenamento seguro purgado e recriado com sucesso. Nova identidade necessária.")
                    return Pair(recreatedPrefs, true)
                } catch (recExc: Throwable) {
                    ConnectionRepository.appendLog("[ERROR] Falha crítica ao recriar armazenamento seguro após purga: ${recExc.message}")
                    return Pair(null, false)
                }
            }

            ConnectionRepository.appendLog("[WARN] Falha transitória persistente do Keystore. Nenhuma chave eliminada. Aguardando desbloqueio.")
            return Pair(null, false)
        }

        fun migrateFallbackIfNeeded(context: Context, securePrefs: android.content.SharedPreferences) {
            try {
                val fallbackPrefs = context.getSharedPreferences("hyprlink_secure_prefs_fallback", Context.MODE_PRIVATE)
                val fallbackKey = fallbackPrefs.getString("device_private_key", null)
                val fallbackCert = fallbackPrefs.getString("device_certificate", null)

                if (!fallbackKey.isNullOrBlank() && !fallbackCert.isNullOrBlank()) {
                    val currentKey = securePrefs.getString("device_private_key", null)
                    if (currentKey.isNullOrBlank()) {
                        val committed = securePrefs.edit()
                            .putString("device_private_key", fallbackKey)
                            .putString("device_certificate", fallbackCert)
                            .commit()

                        if (!committed) {
                            ConnectionRepository.appendLog("[WARN] Falha ao persistir migração de fallback (commit devolveu false). Fallback mantido.")
                            return
                        }

                        val readBackKey = securePrefs.getString("device_private_key", null)
                        val readBackCert = securePrefs.getString("device_certificate", null)
                        if (readBackKey != fallbackKey || readBackCert != fallbackCert) {
                            ConnectionRepository.appendLog("[WARN] Verificação de integridade pós-migração falhou (chave lida não coincide). Fallback mantido.")
                            return
                        }

                        ConnectionRepository.appendLog("[SECURITY] Chave migrada do fallback para EncryptedSharedPreferences com sucesso")
                    } else if (currentKey != fallbackKey) {
                        ConnectionRepository.appendLog("[WARN] Chave existente no armazenamento seguro difere do fallback. Fallback mantido.")
                        return
                    }
                }

                fallbackPrefs.edit().clear().commit()
                context.deleteSharedPreferences("hyprlink_secure_prefs_fallback")
                ConnectionRepository.appendLog("[SECURITY] Ficheiro de fallback em texto limpo eliminado")
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Não foi possível migrar/limpar fallback legado: ${e.message}")
            }
        }

        fun saveIdentity(sharedPrefs: android.content.SharedPreferences, privateKey: PrivateKey, certificate: X509Certificate): Boolean {
            val privateKeyBase64 = Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
            val certBase64 = Base64.encodeToString(certificate.encoded, Base64.DEFAULT)

            val committed = sharedPrefs.edit()
                .putString("device_private_key", privateKeyBase64)
                .putString("device_certificate", certBase64)
                .commit()

            if (!committed) {
                ConnectionRepository.appendLog("[ERROR] Falha ao persistir identidade no armazenamento seguro (commit devolveu false).")
            }
            return committed
        }

        // Function to force identity regeneration
        fun regenerateInstance(context: Context): DeviceIdentity {
            synchronized(this) {
                val appContext = context.applicationContext
                val (sharedPrefs, _) = getSecurePreferencesWithRecovery(appContext)

                val identity = generateNewIdentity(appContext)
                if (sharedPrefs != null) {
                    saveIdentity(sharedPrefs, identity.privateKey, identity.certificate)
                }

                val newIdentity = DeviceIdentity(appContext)
                INSTANCE = newIdentity
                return newIdentity
            }
        }

        fun defaultName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            return "$manufacturer ${Build.MODEL}"
        }

        fun getDeviceName(context: Context): String {
            val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            return prefs.getString("local_device_name", null)?.takeIf { it.isNotBlank() } ?: defaultName()
        }

        fun setDeviceName(context: Context, name: String) {
            val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("local_device_name", name.trim()).apply()
        }

        private fun generateNewIdentity(context: Context): GeneratedKeyPairAndCert {
            val deviceName = getDeviceName(context)

            // 1. Generate ECDSA P-256 keypair (secp256r1)
            val g = KeyPairGenerator.getInstance("EC")
            g.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = g.generateKeyPair()

            // 2. Generate self-signed X.509 Certificate with BouncyCastle
            val subject = X500Name("CN=$deviceName")
            val serial = BigInteger(64, SecureRandom())
            val notBefore = Date()
            val notAfter = Date(notBefore.time + 10L * 365 * 24 * 60 * 60 * 1000) // 10 years

            val certBuilder = JcaX509v3CertificateBuilder(
                subject, // issuer
                serial,
                notBefore,
                notAfter,
                subject, // subject (self-signed)
                keyPair.public
            )

            val signer = JcaContentSignerBuilder("SHA256withECDSA")
                .build(keyPair.private)

            val certHolder = certBuilder.build(signer)
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certHolder.encoded)) as X509Certificate

            return GeneratedKeyPairAndCert(keyPair.private, certificate)
        }
    }
}
