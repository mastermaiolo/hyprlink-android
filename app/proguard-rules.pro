# ─────────────────────────────────────────────────────────────────────────────
# Regras ProGuard / R8 para HyprLink Android (Prompt 76)
# Cada regra tem um comentário explicativo justificando a sua necessidade.
# ─────────────────────────────────────────────────────────────────────────────

# Kwik: motor QUIC puro Java. Usa reflexão interna para cifras TLS, inicialização
# de streams e conexões com o daemon Linux.
-keep class tech.kwik.** { *; }
-dontwarn tech.kwik.**

# BouncyCastle: JCA Security Provider registado por nome em DeviceIdentity ("BC").
# Mantém apenas os pacotes criptográficos estritamente utilizados pela app
# (geração e assinatura de certificados X.509 para mTLS).
# NÃO mantém org.bouncycastle.pqc.** (criptografia pós-quântica pesada e desnecessária).
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.** { *; }
-keep class org.bouncycastle.jce.** { *; }
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.pqc.**
-dontwarn org.bouncycastle.**

# CBOR: serialização e deserialização do protocolo binário de comunicação com o daemon Linux.
-keep class co.nstant.in.cbor.** { *; }
-dontwarn co.nstant.in.cbor.**

# Room Database: entidades, DAOs e banco de dados SQLite compilados via KSP.
-keep class com.example.PairedWorkstation { *; }
-keep class com.example.TransferRecord { *; }
-keep class com.example.WorkstationDao { *; }
-keep class com.example.TransferDao { *; }
-keep class com.example.AppDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# AndroidX Security Crypto / Google Tink: usado por EncryptedSharedPreferences para
# armazenamento seguro da chave privada e tokens mTLS.
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ZXing: leitor puro Java de códigos QR (sem dependências nativas nem GMS)
-keep class com.google.zxing.qrcode.** { *; }
-dontwarn com.google.zxing.**

# CameraX: classes de ciclo de vida, captura de vídeo/preview para a funcionalidade de webcam.
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-dontwarn androidx.camera.**

# Componentes do AndroidManifest e classes ativadas por intents/serviços
-keep class com.example.MainActivity { *; }
-keep class com.example.find.FindPhoneActivity { *; }
-keep class com.example.HyprLinkConnectionService { *; }
-keep class com.example.HyprNotificationListenerService { *; }
-keep class com.example.tiles.** { *; }

# Modelos de dados serializados em CBOR e dados de emparelhamento
-keep class com.example.PcAddr { *; }
-keep class com.example.find.PhoneFindRequest { *; }

# Localização / Idiomas: classes de strings dinâmicas
-keep class com.example.AppStrings { *; }
-keep class com.example.Strings* implements com.example.AppStrings { *; }
