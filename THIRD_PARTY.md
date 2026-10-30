# Componentes e Dependências de Terceiros — HyprLink Android

Este documento lista as bibliotecas e componentes de terceiros utilizados na aplicação Android do HyprLink, com as respetivas versões e licenças.

## Bibliotecas Principais

| Componente | Versão | Licença | Finalidade |
|---|---|---|---|
| `tech.kwik:kwik` | 0.10.9 | LGPL-3.0-or-later | Implementação pura de QUIC em Java/Kotlin para a comunicação com o daemon |
| `org.bouncycastle:bcprov-jdk18on` | 1.78.1 | Bouncy Castle Licence (permissiva tipo MIT) | Primitivas criptográficas para mTLS e certificados X.509 |
| `org.bouncycastle:bcpkix-jdk18on` | 1.78.1 | Bouncy Castle Licence (permissiva tipo MIT) | Geração e manipulação de certificados PKIX/X.509 |
| `co.nstant.in:cbor` | 0.9 | Apache-2.0 | Codificação e descodificação binária de mensagens CBOR |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Apache-2.0 | Armazenamento cifrado seguro de chaves de identidade (EncryptedSharedPreferences) |
| `com.google.mlkit:barcode-scanning` | 17.3.0 | Apache-2.0 (com componentes GMS / modelo local) | Leitura ótica do código QR de emparelhamento com o PC |
| `androidx.media:media` | 1.7.0 | Apache-2.0 | Integração com controlos multimédia e notificações de reprodução |

## AndroidX e Jetpack Compose

Todas as bibliotecas do ecossistema AndroidX e Jetpack Compose são licenciadas sob **Apache-2.0**:

- **Jetpack Compose BOM**: `2024.09.00`
  - `androidx.compose.ui:ui` (1.7.2)
  - `androidx.compose.material3:material3` (1.3.0)
  - `androidx.compose.foundation:foundation` (1.7.2)
  - `androidx.compose.animation:animation` (1.7.2)
- **Jetpack Lifecycle**: `2.8.7` (`lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `lifecycle-process`)
- **Jetpack Activity Compose**: `1.10.1`
- **Jetpack Navigation Compose**: `2.8.9`
- **Jetpack Room**: `2.7.0` (`room-runtime`, `room-ktx`, compilador KSP)
- **Jetpack CameraX**: `1.5.0` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-extensions`)
- **Jetpack Core KTX**: `1.18.0`

## Kotlin e Coroutines

Licenciados sob **Apache-2.0**:
- `org.jetbrains.kotlin:kotlin-stdlib`: `2.2.10`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core`: `1.10.2`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`: `1.10.2`

## Fontes Tipográficas

Distribuídas em `app/src/main/res/font/` e `app/src/main/assets/licencas/`:

| Fonte | Autor / Projeto | Licença |
|---|---|---|
| **Anton** | Vernon Adams | SIL Open Font License 1.1 |
| **Instrument Serif** | Rodrigo Fuenzalida, Jordan Wilson | SIL Open Font License 1.1 |
| **Inter** | Rasmus Andersson | SIL Open Font License 1.1 |
| **IBM Plex Mono** | Mike Abbink, Bold Monday | SIL Open Font License 1.1 |
