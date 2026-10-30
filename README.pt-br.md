<!-- MAIOLO / SYSTEMS LAB — HYPRLINK ANDROID -->

<p align="center">
  <img src="assets/readme/hero.svg" width="100%" alt="HyprLink para Android — versões APK assinadas">
</p>

<p align="center"><sub><strong>ANDROID 13+ · CÓDIGO-FONTE · APK ASSINADO · SHA-256 · APKSIGNER · CN=MAGGIO</strong></sub></p>

<p align="center">
  <a href="README.md">🇬🇧 English (UK)</a>
  · 🇧🇷 <strong>Português (BR)</strong>
  · <a href="README.pt-pt.md">🇵🇹 Português (PT)</a>
  · <a href="README.es-es.md">🇪🇸 Español (ES)</a>
  · <a href="README.zh-cn.md">🇨🇳 简体中文</a>
</p>

> Este repositório hospeda o código-fonte completo e as versões oficiais (pacotes APK assinados) do aplicativo **HyprLink** para Android.

O daemon Linux para Hyprland, a interface de linha de comando (CLI), a GUI de desktop e as especificações de protocolo ficam no repositório principal:
👉 **[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)**

---

## 01 / VISÃO GERAL

| | |
|---|---|
| **O quê** | Código-fonte do app Android e APKs assinados de lançamento |
| **Pareia com** | O daemon HyprLink em Linux/Hyprland, mesma versão |
| **Android mínimo** | 13 |
| **Assinante** | `CN=Maggio` |
| **Downloads** | [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest) — `.apk` + `.sha256` correspondente |

---

## 02 / INSTALAÇÃO

### Via Obtainium (Recomendado)
Adicione este repositório diretamente no [Obtainium](https://github.com/ImranR98/Obtainium) para instalar e receber atualizações automáticas dos lançamentos oficiais:
```text
https://github.com/mastermaiolo/hyprlink-android
```

### Via ADB / Download Manual
1. Baixe o arquivo `.apk` e o `.sha256` correspondente na página de [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest).
   ```bash
   R=mastermaiolo/hyprlink-android
   gh release download "$(gh release list -R $R -L 1 --json tagName -q '.[0].tagName')" -R $R -p 'hyprlink-*.apk*'
   ```
2. Instale no celular via ADB pelo terminal:
   ```bash
   adb install -r hyprlink-*.apk
   ```
   Ou copie o arquivo direto para o celular e abra pelo gerenciador de arquivos (autorizando a instalação de apps dessa fonte).

---

## 03 / COMPILAR

O aplicativo é compilado com as ferramentas padrão do Android Gradle (JDK 21+, Android SDK 33–36).
Para instruções completas e pré-requisitos, veja o guia [`BUILDING.md`](BUILDING.md).
Sem variáveis de ambiente de assinatura, o comando `./gradlew assembleRelease` gera um APK de release limpo não assinado (`hyprlink-*-unsigned.apk`).

---

## 04 / VERIFICAÇÃO DE INTEGRIDADE E ASSINATURA

Para garantir a segurança e a procedência do binário:

1. **Verificação do checksum (SHA-256):**
   ```bash
   sha256sum -c hyprlink-*.apk.sha256
   ```

2. **Verificação da assinatura oficial (apksigner):**
   Usando a ferramenta `apksigner` do Android SDK Build-Tools:
   ```bash
   apksigner verify --print-certs hyprlink-*.apk
   ```
   Confirme nos dados do certificado:
   ```text
   Signer #1 certificate DN: CN=Maggio
   ```

<details>
<summary><strong>Impressão digital do certificado (SHA-256)</strong></summary>

```text
96:95:EF:25:82:CB:6C:62:28:5A:18:0A:BB:1A:A5:6C:1A:B7:AB:48:D4:16:79:55:F3:8E:79:FB:09:99:44:D4
```

</details>

---

## 05 / REQUISITOS E LIMITAÇÕES CONHECIDAS

- Android 13 ou superior; a mesma versão do HyprLink no PC ([mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)).
- Desde o Android 10, um app só pode ler a área de transferência quando está aberto: um texto copiado no celular é enviado ao PC quando você abre o HyprLink.
- Em ROMs Xiaomi/HyperOS/MIUI, confira nas configurações do app: inicialização automática, sem restrições de bateria e "ler área de transferência".
- Transferências em paralelo ainda não são suportadas: os envios ficam em fila.
- A conexão por cabo USB continua em teste.

As novidades de cada versão estão em cada [release](https://github.com/mastermaiolo/hyprlink-android/releases).

---

## 06 / COMO FOI FEITO

Este aplicativo foi desenvolvido com assistência contínua de inteligência artificial (iniciado como protótipo no Google AI Studio e posteriormente desenvolvido e refinado em conjunto com agentes de IA), sob a direção arquitetural, especificação rigorosa e revisão de código do autor (Maggio).

Todo o código segue padrões nativos de Kotlin e Jetpack Compose, sem uso de frameworks híbridos ou telemetria, estando sob licença GPL-3.0 para total transparência e auditoria aberta.

---

## 07 / PROCEDÊNCIA

Compilado e assinado por Maggio.
Código-fonte do app: este repositório; daemon Linux, protocolo e CLI: [mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink).
Política de segurança e reporte privado de vulnerabilidades: [`SECURITY.md`](SECURITY.md).

---

## 08 / LICENÇA

**GPL-3.0-or-later** — veja [`LICENSE`](LICENSE) e [`THIRD_PARTY.md`](THIRD_PARTY.md).

---

<p align="center"><sub>MAIOLO / SYSTEMS LAB · HYPRLINK · HL / 02 · 食</sub></p>
