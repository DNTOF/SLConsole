# 第三方组件 / Third-party notices

SLConsole 本身以 GPL-3.0-or-later 发布（见 [LICENSE](LICENSE)）。APK 里还包含下列第三方组件，它们各自的许可继续有效。

SLConsole itself is licensed under GPL-3.0-or-later. The APK also bundles the third-party components below, which remain under their own licenses.

| 组件 / Component | 版本 / Version | 许可 / License | 与 GPLv3 / GPLv3 compatibility |
|---|---|---|---|
| AndroidX Core KTX | 1.16.0 | Apache-2.0 | 兼容 / compatible |
| AndroidX Lifecycle (runtime-compose) | 2.9.1 | Apache-2.0 | 兼容 / compatible |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 | 兼容 / compatible |
| Jetpack Compose (BOM, UI, Foundation, Material 3) | BOM 2026.03.01 | Apache-2.0 | 兼容 / compatible |
| Compose Material Icons Extended | 1.7.8 | Apache-2.0 | 兼容 / compatible |
| AndroidX Navigation Compose | 2.9.0 | Apache-2.0 | 兼容 / compatible |
| AndroidX DataStore Preferences | 1.1.7 | Apache-2.0 | 兼容 / compatible |
| AndroidX Biometric | 1.1.0 | Apache-2.0 | 兼容 / compatible |
| AndroidX Fragment | 1.8.9 | Apache-2.0 | 兼容 / compatible |
| Kotlin standard library | 2.3.10 | Apache-2.0 | 兼容 / compatible |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | 兼容 / compatible |
| kotlinx.serialization JSON | 1.8.1 | Apache-2.0 | 兼容 / compatible |
| OkHttp (with Okio) | 4.12.0 | Apache-2.0 | 兼容 / compatible |
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (Kyant) | 1.0.6 | Apache-2.0 | 兼容 / compatible |
| [Microsoft Clarity SDK](https://clarity.microsoft.com/) (`clarity-compose`) | 3.10.0 | Maven 元数据标注为 MIT；只提供二进制，服务使用受 Microsoft Clarity 使用条款约束 / MIT per its Maven POM; binary-only, service use governed by the Microsoft Clarity Terms of Use | 见下方说明 / see note |
| └ Protocol Buffers Java Lite（Clarity 的传递依赖 / transitive） | — | BSD-3-Clause | 兼容 / compatible |
| └ Google Play Install Referrer（Clarity 的传递依赖 / transitive） | 2.2 | Android Software Development Kit License | 专有许可，见下方说明 / proprietary, see note |
| Lora 字体 / font | — | SIL Open Font License 1.1 | 作为独立字体文件随附 / bundled as separate font files |
| Poppins 字体 / font | — | SIL Open Font License 1.1 | 作为独立字体文件随附 / bundled as separate font files |
| JUnit（仅测试，不进 APK / tests only, not shipped） | 4.13.2 | EPL-1.0 | 不随应用分发 / not distributed |

字体许可全文 / Font license texts: `app/src/main/assets/licenses/OFL-Lora.txt`, `app/src/main/assets/licenses/OFL-Poppins.txt`.
Apache-2.0: <https://www.apache.org/licenses/LICENSE-2.0>

## 说明 / Notes

- **Microsoft Clarity**：用于匿名使用统计，默认开启，可以在新手引导或应用设置里关闭。如果在走到使用统计那一步之前跳过引导，则会保持关闭。开启后仍可能收到设备型号、系统版本、IP 地址和点击坐标。SDK 只以二进制 AAR 形式发布，没有公开源码；它的 Maven 元数据写的是 MIT 许可，但数据收集服务本身受 Microsoft 的使用条款约束。
  Microsoft Clarity is used for anonymous usage statistics. It is on by default and can be turned off during onboarding or in the app settings. Skipping onboarding before the analytics step leaves it off. When enabled it can still receive device model, OS version, IP address and tap coordinates. The SDK is distributed as a binary-only AAR; its Maven metadata states MIT, while the analytics service is governed by Microsoft's terms.
- **Google Play Install Referrer** is pulled in by the Clarity SDK and is licensed under the Android Software Development Kit License, which is not a free-software license.
- 与这两个组件一起分发由 [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md) 里的 GPLv3 第 7 条附加许可覆盖。
  Distributing SLConsole together with these two components is covered by the GPLv3 section 7 additional permission in [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md).
- 地图种子重建数据和算法改写自 FXDYJ 的 [SCPSLMaps](https://scpslmaps.fxdyj.com/) Web 端代码，在此致谢。
  The map seed reconstruction data and logic are adapted from FXDYJ's [SCPSLMaps](https://scpslmaps.fxdyj.com/) web code, with thanks.
