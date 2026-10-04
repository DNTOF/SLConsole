# SLConsole

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)

**SLConsole** 是面向 SCP: Secret Laboratory 服务器管理员的 Android 原生管理 + 监控客户端:直连游戏服务器上的 [SLDataAPI](https://github.com/DNTOF/SLDataAPI) 插件,无需任何中间平台,凭据只保存在本机。

## 功能

- **概览监控**:在线状态、玩家数、回合阶段与时长、核弹倒计时、阵营分布、延迟 —— 按配置间隔轮询
- **玩家管理**:实时列表与搜索,踢出 / 封禁 / 禁言 / 私信 / 换角色 / 施加效果 / 传送
- **控制台**:WebUI 风格终端(命令回显历史、输出实时追加)
- **地图**:回合种子本地重建布局(.NET System.Random 逐位移植,与 Web 端 mapgen 同源),双指缩放 / 拖动 / 双击复位,标签防重叠,玩家实时定位,灯光 / 门 / 电梯控制
- **动态**:WebSocket 实时事件流(玩家进出 / 死亡、回合开始结束、电梯门使用)
- **语音监听**:直连语音转发端口,48kHz float32 实时收听,说话人列表与频道标识
- **封禁管理**:封禁列表、离线封禁(steam / ip)、解封
- **服务器日志**:文件列表、尾部读取(100–2000 行、关键字过滤、自动刷新)
- **控制审计**:操作审计记录查看
- **插件管理**:EXILED / LabAPI 插件列表、暂存启停、应用暂存、重载
- **文件管理**:FileRoot 目录浏览、文本文件查看 / 编辑 / 保存、受保护文件标识。服务器打开 2.6.1 的 `file_chunks` 内测后,较大的文件会分段读写
- **举报管理**:待处理举报列表与处理
- **多服务器**:添加任意数量的服务器,一键切换;凭据经 AndroidKeyStore AES-256-GCM 加密存储

## 架构

```text
SLConsole (Android, Kotlin + Jetpack Compose)
  │  HTTP   GET  /get_sl_data         Bearer <VerifyToken>   监控轮询
  │  HTTP   POST /control/*           Bearer <API Key>       控制操作
  │  WS     ws://host:8081/control    Bearer <API Key>       控制调用 + 实时事件
  │  WS     ws://host:<voice_port>/ws Bearer <API Key>       语音流(float32 PCM)
  ▼
SLDataAPI 2.6.0 及以上。2.6.1 是预发布，里面的内测功能默认关闭，不打开时和 2.6.0 一样。
```

- 数据面与控制面为两套独立凭据(与 Web 端 upstream.js 注入规则一致);
- 服务器 `control_transport: ws` 时,控制调用经 WS `call{reqId, path, body}` 信封发送,同一连接可订阅实时事件(25s 心跳、断线自动重连);
- 地图布局完全由回合种子在本地确定性重建,不请求房间数据。

## 构建

要求:Android Studio(含 JBR / JDK 17)+ Android SDK 36。

```bash
./gradlew assembleDebug
# 产物:app/build/outputs/apk/debug/app-debug.apk
```

- 最低支持 Android 8.0(API 26);
- `settings.gradle.kts` 默认优先使用阿里云 Maven 镜像(国内网络环境),海外可自行调整顺序;
- 命令行构建需 `JAVA_HOME` 指向完整 JDK(PATH 上只有 JRE 时 Gradle 会报 "No Java compiler found")。

## 服务器端前置条件

1. 游戏服务器安装 SLDataAPI 2.6.0 或更新版本,`verify_token` 为强口令(弱口令会触发 fail-closed 503)。2.6.1 预发布的适配插件动作和大文件分块默认关闭,需要时再在测试服打开;
2. `control_enabled: true`,按需选择 `control_transport: http | ws`(与 app 内设置一致);
3. 通过服务器控制台创建 API Key:`sldataapi apikey create <id> <duty|admin>`。Key 的角色在创建时就定了,事后改 `apikey.config` 里的角色不会生效,还会导致认证失败;要 admin 权限就新建一把 admin Key 并在 app 里换上。按需在 `apikey.config` 的 `endpoints_override` 中放开 plugins / files 等端点;
4. 启用语音需 `voice_enabled: true`,并确认语音端口可被手机直连(不走网页反向代理);
5. 防火墙放行 8081(及语音端口)。

## 安全说明

- 凭据经 AndroidKeyStore AES-256-GCM 加密后存储,密钥不可导出,卸载即失效;
- SLDataAPI 本身为明文 HTTP/WS,请在可信内网或 TLS 反向代理后使用;
- 封禁、引爆核弹、重启回合等破坏性操作均有二次确认;
- 本应用不收集、不上传任何数据。

## 特别鸣谢
感谢由 FXDYJ 开发的地图绘制JS,源自 [SCPSLMaps](https://scpslmaps.fxdyj.com/)

## 许可与版权

Copyright (C) 2026 DNT_OF

SLConsole 是自由软件。你可以依据自由软件基金会发布的 GNU 通用公共许可证第 3 版，或者（由你选择）任何更新的版本，重新分发和修改它（SPDX：`GPL-3.0-or-later`）。完整条款见 [LICENSE](LICENSE)。本程序按“原样”提供，不附带任何担保。

再分发或修改时需要注意：

- 分发修改版（包括只分发编译好的 APK）时，必须以 GPLv3 公开对应的完整源代码，接收者同样享有 GPLv3 的全部权利；
- 必须保留源文件开头的版权声明和 SPDX 许可标识，以及本 README、[NOTICE](NOTICE) 里的版权信息；
- 修改版需要显著注明你做了修改以及修改日期（GPLv3 第 5 条）。

官方版本使用作者自己的签名证书。别人自行编译或修改的版本签名不同，应用会在启动时和「关于」页提示「非官方版本」。请只从 [Releases](https://github.com/DNTOF/SLConsole/releases) 下载官方版本。

### 名称与图标

GPL 授权的是代码的版权，不包括以下标识：「SLConsole」这个名称、作者名「DNT_OF」以及应用图标。依据 GPLv3 第 7 条 (e) 款，本项目不授予这些名称和标识在商标法上的使用权。分发修改版时请换一个名称和图标，不要让人误以为它是官方版本或得到了作者认可。按要求保留版权声明和作者署名不受这条限制。

### 第三方组件

依赖库及其许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

液态玻璃绘制使用 [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6（Kyant，[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)）。界面里的 Lora、Poppins 字体以 SIL Open Font License 1.1 提供，许可全文在 `app/src/main/assets/licenses/`。

## License (English)

Copyright (C) 2026 DNT_OF

SLConsole is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version (`GPL-3.0-or-later`). See [LICENSE](LICENSE). It comes with ABSOLUTELY NO WARRANTY.

- If you distribute a modified version, including APK-only releases, you must make the complete corresponding source code available under GPLv3.
- Keep the copyright notices and SPDX headers in the source files, as well as the notices in this README and in [NOTICE](NOTICE), and mark your changes (GPLv3 section 5).
- Official builds are signed with the author's certificate. Builds signed with any other key are flagged by the app as unofficial (「非官方版本」).
- **Names and icon:** the GPL does not grant any rights to the name "SLConsole", the author name "DNT_OF" or the app icon. Under GPLv3 section 7(e), trademark rights to them are not granted; forks must use a different name and icon and must not suggest they are official or endorsed.

Third-party licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
