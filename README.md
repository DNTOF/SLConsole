# SLConsole

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)

**SLConsole** 是面向 SCP: Secret Laboratory 服务器管理员的 Android 原生管理 + 监控客户端:直连游戏服务器上的 [SLDataAPI](https://github.com/DNTOF/SLDataAPI) 插件,无需任何中间平台,凭据只保存在本机。

由 DNT_OF 与 FXDYJ 共同开发,源自 Foundation Console(scpsl_webui)移动端化项目。

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
- **文件管理**:FileRoot 目录浏览、文本文件查看 / 编辑 / 保存、受保护文件标识
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
SLDataAPI 2.6.0+ 插件(SCP:SL 游戏服务器,默认端口 8081)
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

1. 游戏服务器安装 SLDataAPI 2.6.0+,`verify_token` 为强口令(弱口令会触发 fail-closed 503);
2. `control_enabled: true`,按需选择 `control_transport: http | ws`(与 app 内设置一致);
3. 通过服务器控制台创建 API Key:`sldataapi apikey create <id> <duty|admin>`,按需在 `apikey.config` 的 `endpoints_override` 中放开 plugins / files 等端点;
4. 启用语音需 `voice_enabled: true`,并确认语音端口可被手机直连(不走网页反向代理);
5. 防火墙放行 8081(及语音端口)。

## 安全说明

- 凭据经 AndroidKeyStore AES-256-GCM 加密后存储,密钥不可导出,卸载即失效;
- SLDataAPI 本身为明文 HTTP/WS,请在可信内网或 TLS 反向代理后使用;
- 封禁、引爆核弹、重启回合等破坏性操作均有二次确认;
- 本应用不收集、不上传任何数据。

## 许可

本项目以 [GPL-3.0](LICENSE) 许可发布。

Copyright (C) 2026 DNT_OF

液态玻璃绘制使用 [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6（Kyant，[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)）。
