<!-- MAIOLO / SYSTEMS LAB — HYPRLINK ANDROID -->

<p align="center">
  <img src="assets/readme/hero.svg" width="100%" alt="HyprLink for Android — signed APK releases">
</p>

<p align="center"><sub><strong>ANDROID 13+ · SIGNED APK · SHA-256 · APKSIGNER · CN=MAGGIO</strong></sub></p>

<p align="center">
  🇬🇧 <strong>English (UK)</strong>
  · <a href="README.pt-br.md">🇧🇷 Português (BR)</a>
  · <a href="README.pt-pt.md">🇵🇹 Português (PT)</a>
  · <a href="README.es-es.md">🇪🇸 Español (ES)</a>
  · <a href="README.zh-cn.md">🇨🇳 简体中文</a>
</p>

> This repository hosts the official release builds (APK packages) of the **HyprLink** Android companion app.

The full source code of the Android application, the Linux Hyprland daemon, CLI and desktop GUI lives in the main repository:
👉 **[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)**

---

## 01 / AT A GLANCE

| | |
|---|---|
| **What** | Signed APK builds of the HyprLink phone app |
| **Pairs with** | The HyprLink daemon on Linux/Hyprland, same version |
| **Minimum Android** | 13 |
| **Signer** | `CN=Maggio` |
| **Downloads** | [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest) — `.apk` + matching `.sha256` |

---

## 02 / INSTALL

1. Download the `.apk` file and its matching `.sha256` from the [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest) page.
   ```bash
   R=mastermaiolo/hyprlink-android
   gh release download "$(gh release list -R $R -L 1 --json tagName -q '.[0].tagName')" -R $R -p 'hyprlink-*.apk*'
   ```
2. Install to your Android device via ADB:
   ```bash
   adb install -r hyprlink-*.apk
   ```
   Or transfer the file directly to your phone and open it via your file manager (granting permission to install unknown apps).

---

## 03 / VERIFICATION

To verify the integrity and signature of the release package:

1. **Check SHA-256 checksum:**
   ```bash
   sha256sum -c hyprlink-*.apk.sha256
   ```

2. **Verify official signature (apksigner):**
   Using `apksigner` from the Android SDK Build-Tools:
   ```bash
   apksigner verify --print-certs hyprlink-*.apk
   ```
   Verify that the signer certificate matches:
   ```text
   Signer #1 certificate DN: CN=Maggio
   ```

<details>
<summary><strong>Signing certificate fingerprint (SHA-256)</strong></summary>

```text
96:95:EF:25:82:CB:6C:62:28:5A:18:0A:BB:1A:A5:6C:1A:B7:AB:48:D4:16:79:55:F3:8E:79:FB:09:99:44:D4
```

</details>

---

## 04 / REQUIREMENTS & KNOWN LIMITATIONS

- Android 13 or later; the same version of HyprLink on the PC ([mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)).
- Since Android 10, an app can only read the clipboard while it is open: text copied on the phone is sent to the PC when you open HyprLink.
- On Xiaomi/HyperOS/MIUI ROMs, check the app settings: autostart, no battery restrictions and "read clipboard".
- Parallel transfers are not supported yet: uploads are queued.
- USB cable connection is still in testing.

Per-version changes are listed in each [release](https://github.com/mastermaiolo/hyprlink-android/releases).

---

## 05 / PROVENANCE

Built and signed by Maggio. Source, protocol and changelog: [mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink).

---

## 06 / LICENCE

**GPL-3.0** — see [`LICENSE`](LICENSE).

---

<p align="center"><sub>MAIOLO / SYSTEMS LAB · HYPRLINK · HL / 02 · 食</sub></p>
