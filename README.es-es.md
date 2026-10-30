<!-- MAIOLO / SYSTEMS LAB — HYPRLINK ANDROID -->

<p align="center">
  <img src="assets/readme/hero.svg" width="100%" alt="HyprLink para Android — versiones APK firmadas">
</p>

<p align="center"><sub><strong>ANDROID 13+ · CÓDIGO FUENTE · APK FIRMADO · SHA-256 · APKSIGNER · CN=MAGGIO</strong></sub></p>

<p align="center">
  <a href="README.md">🇬🇧 English (UK)</a>
  · <a href="README.pt-br.md">🇧🇷 Português (BR)</a>
  · <a href="README.pt-pt.md">🇵🇹 Português (PT)</a>
  · 🇪🇸 <strong>Español (ES)</strong>
  · <a href="README.zh-cn.md">🇨🇳 简体中文</a>
</p>

> Este repositorio aloja el código fuente completo y las versiones oficiales (paquetes APK firmados) de la aplicación **HyprLink** para Android.

El daemon Linux para Hyprland, la interfaz de línea de órdenes (CLI), la GUI de escritorio y las especificaciones de protocolo están en el repositorio principal:
👉 **[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)**

---

## 01 / DE UN VISTAZO

| | |
|---|---|
| **Qué** | Código fuente de la app Android y APKs firmados de lanzamiento |
| **Se empareja con** | El daemon HyprLink en Linux/Hyprland, misma versión |
| **Android mínimo** | 13 |
| **Firmante** | `CN=Maggio` |
| **Descargas** | [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest) — `.apk` + `.sha256` correspondiente |

---

## 02 / INSTALACIÓN

### Mediante Obtainium (Recomendado)
Añade este repositorio directamente en [Obtainium](https://github.com/ImranR98/Obtainium) para instalar y recibir actualizaciones automáticas de los lanzamientos oficiales:
```text
https://github.com/mastermaiolo/hyprlink-android
```

### Mediante ADB / Descarga Manual
1. Descarga el archivo `.apk` y su `.sha256` correspondiente desde la página de [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest).
   ```bash
   R=mastermaiolo/hyprlink-android
   gh release download "$(gh release list -R $R -L 1 --json tagName -q '.[0].tagName')" -R $R -p 'hyprlink-*.apk*'
   ```
2. Instálalo en el móvil mediante ADB:
   ```bash
   adb install -r hyprlink-*.apk
   ```
   O copia el archivo directamente al móvil y ábrelo con el gestor de archivos (autorizando la instalación de aplicaciones desconocidas).

---

## 03 / COMPILAR

La aplicación se compila con las herramientas estándar de Android Gradle (JDK 21+, Android SDK 33–36).
Para instrucciones completas y requisitos previos, consulta la guía [`BUILDING.md`](BUILDING.md).
Sin variables de entorno de firma, el comando `./gradlew assembleRelease` genera un APK de release limpio sin firmar (`hyprlink-*-unsigned.apk`).

---

## 04 / VERIFICACIÓN

Para comprobar la integridad y la firma del paquete:

1. **Comprobar la suma SHA-256:**
   ```bash
   sha256sum -c hyprlink-*.apk.sha256
   ```

2. **Verificar la firma oficial (apksigner):**
   Con `apksigner` de Android SDK Build-Tools:
   ```bash
   apksigner verify --print-certs hyprlink-*.apk
   ```
   Comprueba que el certificado del firmante coincide:
   ```text
   Signer #1 certificate DN: CN=Maggio
   ```

<details>
<summary><strong>Huella del certificado de firma (SHA-256)</strong></summary>

```text
96:95:EF:25:82:CB:6C:62:28:5A:18:0A:BB:1A:A5:6C:1A:B7:AB:48:D4:16:79:55:F3:8E:79:FB:09:99:44:D4
```

</details>

---

## 05 / REQUISITOS Y LIMITACIONES CONOCIDAS

- Android 13 o superior; la misma versión de HyprLink en el PC ([mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)).
- Desde Android 10, una app solo puede leer el portapapeles mientras está abierta: el texto copiado en el móvil se envía al PC al abrir HyprLink.
- En ROM Xiaomi/HyperOS/MIUI, revisa los ajustes de la app: inicio automático, sin restricciones de batería y «leer el portapapeles».
- Las transferencias en paralelo aún no están soportadas: los envíos se ponen en cola.
- La conexión por cable USB sigue en pruebas.

Los cambios de cada versión figuran en cada [release](https://github.com/mastermaiolo/hyprlink-android/releases).

---

## 06 / CÓMO SE HIZO

Esta aplicación fue desarrollada con asistencia continua de inteligencia artificial (iniciada como prototipo en Google AI Studio y posteriormente desarrollada y refinada junto con agentes de IA), bajo la dirección arquitectónica, especificación rigurosa y revisión de código del autor (Maggio).

Todo el código sigue patrones nativos de Kotlin y Jetpack Compose, sin frameworks híbridos ni telemetría, y se encuentra bajo licencia GPL-3.0 para total transparencia y auditoría abierta.

---

## 07 / PROCEDENCIA

Compilado y firmado por Maggio.
Código fuente de la app: este repositorio; daemon Linux, protocolo y CLI: [mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink).
Política de seguridad y reporte privado de vulnerabilidades: [`SECURITY.md`](SECURITY.md).

---

## 08 / LICENCIA

**GPL-3.0-or-later** — véase [`LICENSE`](LICENSE) y [`THIRD_PARTY.md`](THIRD_PARTY.md).

---

<p align="center"><sub>MAIOLO / SYSTEMS LAB · HYPRLINK · HL / 02 · 食</sub></p>
