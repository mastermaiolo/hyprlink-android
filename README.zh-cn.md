<!-- MAIOLO / SYSTEMS LAB — HYPRLINK ANDROID -->

<p align="center">
  <img src="assets/readme/hero.svg" width="100%" alt="HyprLink Android 版 — 已签名的 APK 发布">
</p>

<p align="center"><sub><strong>ANDROID 13+ · 完整源码 · 已签名 APK · SHA-256 · APKSIGNER · CN=MAGGIO</strong></sub></p>

<p align="center">
  <a href="README.md">🇬🇧 English (UK)</a>
  · <a href="README.pt-br.md">🇧🇷 Português (BR)</a>
  · <a href="README.pt-pt.md">🇵🇹 Português (PT)</a>
  · <a href="README.es-es.md">🇪🇸 Español (ES)</a>
  · 🇨🇳 <strong>简体中文</strong>
</p>

> 本仓库托管 **HyprLink** Android 应用的完整源代码及官方发布构建（已签名的 APK 安装包）。

Linux Hyprland 守护进程、命令行工具（CLI）、桌面 GUI 及协议规范位于主仓库：
👉 **[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)**

---

## 01 / 概览

| | |
|---|---|
| **内容** | Android 应用源代码与已签名的发布版 APK |
| **配对对象** | Linux/Hyprland 上的 HyprLink 守护进程，版本需一致 |
| **最低 Android 版本** | 13 |
| **签名者** | `CN=Maggio` |
| **下载** | [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest) — `.apk` 及对应的 `.sha256` |

---

## 02 / 安装

### 通过 Obtainium 安装（推荐）
将本仓库直接添加到 [Obtainium](https://github.com/ImranR98/Obtainium)，即可直接获取官方版本并自动接收后续更新：
```text
https://github.com/mastermaiolo/hyprlink-android
```

### 通过 ADB / 手动下载
1. 从 [Releases](https://github.com/mastermaiolo/hyprlink-android/releases/latest) 页面下载 `.apk` 文件及对应的 `.sha256` 文件。
   ```bash
   R=mastermaiolo/hyprlink-android
   gh release download "$(gh release list -R $R -L 1 --json tagName -q '.[0].tagName')" -R $R -p 'hyprlink-*.apk*'
   ```
2. 通过 ADB 安装到 Android 设备：
   ```bash
   adb install -r hyprlink-*.apk
   ```
   或者将文件直接传到手机，用文件管理器打开（需允许安装未知来源应用）。

---

## 03 / 构建

本项目使用标准 Android Gradle 工具链构建（需 JDK 21+ 及 Android SDK 33–36）。
构建前置条件及详细指令请参阅 [`BUILDING.md`](BUILDING.md)。
在未配置签名环境变量时，执行 `./gradlew assembleRelease` 将生成干净的未签名发布包（`hyprlink-*-unsigned.apk`）。

---

## 04 / 校验

校验发布包的完整性与签名：

1. **校验 SHA-256：**
   ```bash
   sha256sum -c hyprlink-*.apk.sha256
   ```

2. **验证官方签名（apksigner）：**
   使用 Android SDK Build-Tools 中的 `apksigner`：
   ```bash
   apksigner verify --print-certs hyprlink-*.apk
   ```
   确认签名证书与以下一致：
   ```text
   Signer #1 certificate DN: CN=Maggio
   ```

<details>
<summary><strong>签名证书指纹（SHA-256）</strong></summary>

```text
96:95:EF:25:82:CB:6C:62:28:5A:18:0A:BB:1A:A5:6C:1A:B7:AB:48:D4:16:79:55:F3:8E:79:FB:09:99:44:D4
```

</details>

---

## 05 / 要求与已知限制

- Android 13 或更高版本；PC 端需同版本的 HyprLink（[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)）。
- 自 Android 10 起，应用只能在前台时读取剪贴板：手机上复制的文本会在打开 HyprLink 时发送到 PC。
- 在小米/HyperOS/MIUI ROM 上，请在应用设置中确认：自启动、无电池限制以及“读取剪贴板”。
- 暂不支持并行传输：发送任务会排队。
- USB 线连接仍在测试中。

各版本的变更见对应的 [release](https://github.com/mastermaiolo/hyprlink-android/releases)。

---

## 06 / 开发背景与实现

本应用在作者（Maggio）的架构规划、严格技术规范与代码审查主导下，借助人工智能辅助持续开发（最初在 Google AI Studio 搭建原型，随后由 AI 智能体协助迭代完善）。

全部代码采用原生 Kotlin 与 Jetpack Compose 开发，无任何混合框架与专有遥测代码，并以 GPL-3.0 许可证完全开源，接受社区审查与审计。

---

## 07 / 来源

由 Maggio 构建并签名。
Android 应用源码：本仓库；Linux 守护进程、通信协议及 CLI：[mastermaiolo/hyprlink](https://github.com/mastermaiolo/hyprlink)。
安全策略及漏洞私密汇报渠道：[`SECURITY.md`](SECURITY.md)。

---

## 08 / 许可证

**GPL-3.0-or-later** — 见 [`LICENSE`](LICENSE) 与 [`THIRD_PARTY.md`](THIRD_PARTY.md)。

---

<p align="center"><sub>MAIOLO / SYSTEMS LAB · HYPRLINK · HL / 02 · 食</sub></p>
