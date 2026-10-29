# HyprLink para Android / HyprLink for Android

*(Versão em português europeu abaixo / English version below)*

---

## Português (pt-PT)

Este repositório aloja as versões oficiais prontas a instalar (ficheiros APK) da aplicação **HyprLink** para Android.

O código-fonte completo da app Android, do daemon Linux para Hyprland e das ferramentas de linha de comandos e GUI está alojado no repositório principal:
👉 **[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)**

### Instalação

1. Transfere o ficheiro `.apk` e o respetivo `.sha256` da secção de [Releases](https://github.com/mastermaiolo/hyprlink-android/releases).
2. Podes instalar no teu telemóvel através de ADB no terminal:
   ```bash
   adb install -r hyprlink-0.1.1.apk
   ```
   Ou transferir o ficheiro diretamente para o dispositivo móvel e abri-lo através do gestor de ficheiros (autorizando a instalação de pacotes a partir dessa fonte).

### Verificação de Integridade e Assinatura

Para garantir a segurança e proveniência do binário:

1. **Verificação do Checksum (SHA-256):**
   ```bash
   sha256sum -c hyprlink-0.1.1.apk.sha256
   ```

2. **Verificação da Assinatura Oficial (apksigner):**
   Utilizando a ferramenta `apksigner` do Android SDK Build-Tools:
   ```bash
   apksigner verify --print-certs hyprlink-0.1.1.apk
   ```
   Deverás confirmar nos dados do certificado:
   ```text
   Signer #1 certificate DN: CN=Maggio
   ```

---

## English (en)

This repository hosts official release builds (APK packages) of the **HyprLink** Android companion app.

The full source code of the Android application, the Linux Hyprland daemon, CLI and desktop GUI is available in the main repository:
👉 **[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)**

### Installation

1. Download the `.apk` file and its matching `.sha256` from the [Releases](https://github.com/mastermaiolo/hyprlink-android/releases) page.
2. Install to your Android device via ADB:
   ```bash
   adb install -r hyprlink-0.1.1.apk
   ```
   Or transfer the file directly to your phone and open it via your file manager (granting permission to install unknown apps).

### Verification

To verify the integrity and signature of the release package:

1. **Check SHA-256 Checksum:**
   ```bash
   sha256sum -c hyprlink-0.1.1.apk.sha256
   ```

2. **Verify Official Signature (apksigner):**
   Using `apksigner` from the Android SDK Build-Tools:
   ```bash
   apksigner verify --print-certs hyprlink-0.1.1.apk
   ```
   Verify that the signer certificate matches:
   ```text
   Signer #1 certificate DN: CN=Maggio
   ```
