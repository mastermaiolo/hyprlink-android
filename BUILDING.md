# Compilação e Construção — HyprLink Android

Instruções para compilar a aplicação HyprLink para Android a partir do código-fonte.

## Requisitos do Sistema

1. **JDK (Java Development Kit):**
   * JDK 21 ou superior (Open Source / OpenJDK recomendado).
2. **Android SDK:**
   * `compileSdk`: 36 (com extensão API minor nível 1)
   * `minSdk`: 33 (Android 13)
   * `targetSdk`: 36 (Android 15)
   * Android SDK Build-Tools e Platform 36 instalados.
3. **Ambiente:**
   * Linux, macOS ou Windows.
   * Não são necessárias ferramentas proprietárias nem variáveis secretas para compilar versões de depuração ou de teste.

## Variáveis de Ambiente para Assinatura de Release

A compilação de release oficial assina o pacote APK com recurso a uma keystore de produção.
A configuração é lida exclusivamente através das seguintes variáveis de ambiente (nunca armazenadas no repositório):

```bash
export KEYSTORE_PATH="/caminho/para/a/tua/keystore.jks"
export STORE_PASSWORD="tua_palavra_passe_da_keystore"
export KEY_ALIAS="teu_alias_de_chave"
export KEY_PASSWORD="tua_palavra_passe_da_chave"
```

> **Nota:** Se estas variáveis de ambiente não estiverem definidas, o comando `./gradlew assembleRelease` continuará a compilar com sucesso, gerando um APK de release **não assinado** (`hyprlink-0.1.3-unsigned.apk`) e o respetivo ficheiro de checksum SHA-256.

## Comandos de Compilação e Testes

A partir da raiz deste projeto (`app android hyprlink`):

### 1. Compilar versão Debug e executar Testes Unitários
```bash
./gradlew assembleDebug testDebugUnitTest
```

### 2. Compilar versão Release de Teste (assinada com chave de depuração local)
```bash
./gradlew assembleReleaseTest
```
O APK gerado ficará em `app/build/outputs/apk/releaseTest/`.

### 3. Compilar versão Release de Produção
```bash
./gradlew assembleRelease
```
O APK gerado e a respetiva soma de verificação ficarão disponíveis em `app/build/outputs/apk/release/`.
