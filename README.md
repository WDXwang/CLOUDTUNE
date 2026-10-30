# CloudTune · 云盘音乐

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Release](https://img.shields.io/github/v/release/WDXwang/CLOUDTUNE?include_prereleases)](https://github.com/WDXwang/CLOUDTUNE/releases)
[![minSdk](https://img.shields.io/badge/minSdk-26-brightgreen.svg)](https://github.com/WDXwang/CLOUDTUNE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6-purple.svg)](https://developer.android.com/jetpack/compose)

一款安卓（Android）原生音乐播放器，**可以直接播放天翼云盘里的音乐文件，无需先下载到手机**；同时支持以标准 WebDAV 连接自建 [AList](https://alist.nn.ci/) / [OpenList](https://github.com/OpenListTeam/OpenList) 等网盘网关，支持 123 云盘 / 夸克网盘直连，并可导入本地音乐。**支持添加多个存储账号并在「文件」页统一聚合浏览、跨盘搜索。**

> 界面全部使用 Jetpack Compose 构建，播放内核为 Media3 / ExoPlayer，采用苹果播放器风格设计，支持在线流式播放、后台播放、通知栏控制、歌词、均衡器与本地持久化。

---

## 一、功能特性

### 云盘与音源
- 🗂️ **多网盘聚合**：可添加多个存储（多个 WebDAV / 多个网盘账号），在「文件」页以卡片形式统一浏览，支持跨网盘并行搜索（深度 / 目录数 / 结果数均有上限）。
- ☁️ **天翼云盘直连播放**：扫码登录，浏览云盘目录，点击即播，走云端 CDN 直链，支持 HTTP Range（可拖动进度）。
- 🔌 **WebDAV 后端**：连接自建 AList / OpenList（或任意 WebDAV 服务），把登录 / 风控 / 续期隔离在服务端。
- 🧪 **123 云盘（实验性）**：账号密码换取 Bearer token，浏览目录并解析下载直链。
- 🧪 **夸克网盘（实验性）**：粘贴浏览器 Cookie 登录，浏览目录并解析下载直链。
- 🧪 **光鸭云盘（实验性）**：手机号短信验证码登录（短信被风控时可直接粘贴开发者 token），浏览目录并解析下载直链。
- 🎬 **视频播放**：文件页点击视频进入播放页，支持横屏全屏、竖屏锁定、全屏下 5 秒无操作自动隐藏控制栏，点击画面中间显示 / 隐藏控制栏。
- 🎞️ **视频电影墙**：目录内含视频时，顶栏可切换「电影墙」视图，视频以海报卡片网格展示（按文件名生成稳定渐变封面），文件夹 / 音乐以紧凑卡片混排。
- 📱 **本地音乐导入**：扫描手机本地音频（MediaStore），与云盘音乐统一管理。
- 🔍 **搜索 / 多选 / 全选**：大文件夹下快速搜索，支持多选、全选批量操作。

### 播放与音效
- 🎵 **完整播放器**：播放 / 暂停、上一首 / 下一首、进度拖动、顺序 / 列表循环 / 单曲循环、随机播放。
- ⏩ **倍速调节**：多档播放速度可调。
- 🎚️ **均衡器**：内置 EQ，自定义音效。
- 🎤 **歌词显示**：播放页同步展示歌词，支持从网络自动获取。
- 🎧 **后台播放与通知栏控制**：基于 MediaSession，锁屏 / 通知栏 / 蓝牙耳机按键均可控制。
- 💾 **边播边缓存**：播放过的音频块写入本地缓存（LRU 自动淘汰）。

### 收藏与个性化
- 📃 **歌单**：创建 / 管理自定义歌单，Room 持久化。
- ✨ **按歌手生成歌单**：一键扫描全部已启用网盘中的音乐，按文件名「歌手 - 歌名」自动分组建歌单（同名歌单覆盖重建，无歌手信息的歌曲跳过）。
- ⭐ **我喜欢的**：收藏列表保存在本地数据库。
- 🍎 **苹果播放器风格 UI**：简洁、毛玻璃质感的现代化界面。
- 🔒 **持久化**：登录会话、歌单、收藏、设置均本地持久化，重启不丢失。
- 🎶 支持常见格式：`mp3 / flac / wav / ape / m4a / ogg / aac / wma / opus / aiff / dsf / dff` 等。

---

## 二、下载安装

前往 [**Releases 页面**](https://github.com/WDXwang/CLOUDTUNE/releases) 下载最新 `CloudTune.apk`，传到手机点击安装（需允许「安装未知来源应用」）。

> 当前为 Debug 签名构建，`minSdk 26`（Android 8.0）及以上。

---

## 三、天翼云盘接入说明

**结论：天翼云盘没有面向个人开发者开放、可直接注册使用的官方文件 API**（官方开放平台 `id.189.cn` 需企业开发者资质 + APPID/AppSecret 审批），且原生不支持 WebDAV。因此本项目提供三种登录方式：

| 方式 | 说明 | 切换网络 | 建议 |
|---|---|---|---|
| **扫码登录（推荐）** | App 内展示二维码，用手机天翼云盘 App 扫码确认，换 token 会话（HMAC-SHA1 签名） | ✅ **不绑定 IP，换网仍可用** | ✅ 首选 |
| **WebDAV + 自建 AList/OpenList** | App 作为标准 WebDAV 客户端，网关再对接天翼云盘 | ✅ 风险隔离在服务端 | ✅ 进阶 |
| **手动 Cookie（备选）** | 粘贴浏览器 Cookie，走非官方接口 | ⚠️ 绑定登录 IP，换网需重登 | 仅备选 |

### 扫码登录为什么换网不失效
扫码确认后，App 调用 `getSessionForPC.action` 换取 `sessionKey / sessionSecret / accessToken / refreshToken`，之后数据请求走 `api.cloud.189.cn` 的 **HMAC-SHA1 签名接口**，会话**不绑定 IP**；播放直链由 `getFileDownloadUrl.action` 一次 302 取得（CDN 一次性链接，不二次请求）。这与 [OpenList](https://github.com/OpenListTeam/OpenList) 的 189pc 驱动实现一致。

> 详见 [`docs/天翼云盘接入调研报告.md`](docs/天翼云盘接入调研报告.md) 与 [`docs/WilliamPlayer调研与cloudpan189-api补充.md`](docs/WilliamPlayer调研与cloudpan189-api补充.md)。

---

## 四、编译环境要求

- **Android Studio**：Iguana (2023.2) 或更高版本（内置 JDK 17）。
- **JDK**：17（命令行编译需 `JAVA_HOME` 指向 JDK 17）。
- **Gradle**：8.9（项目自带 Wrapper，无需单独安装）。
- **Android SDK**：`compileSdk 34`、`minSdk 26`、`targetSdk 34`。

---

## 五、编译方法

### 方式 1：Android Studio
1. 打开 Android Studio → `Open` → 选择 `CloudTune` 目录。
2. 等待 Gradle Sync 完成（首次自动下载依赖）。
3. 点击 ▶️ 运行到手机，或 `Build` → `Build APK(s)`。

### 方式 2：命令行
```bash
cd CloudTune
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # Release 包（需自行签名）
```

> 中国大陆网络下，项目已在 `settings.gradle.kts` 配置阿里云镜像、Gradle 发行版使用腾讯镜像。

---

## 六、使用方法

### 扫码登录（推荐）
1. 打开 CloudTune → 后端选择 **天翼直连** → 点 **扫码登录（推荐）**。
2. App 展示二维码；用手机天翼云盘 App 扫一扫（可先保存二维码到相册再扫码）。
3. 在手机上确认登录，App 自动回填会话并进入文件列表。
4. 若二维码过期，点右上角刷新即可。

### WebDAV + AList/OpenList
1. 部署 AList / OpenList（NAS / VPS / Docker 均可），添加「天翼云盘」存储。
2. 开启 WebDAV 读取权限。
3. CloudTune → 后端选择 **WebDAV**：填服务器地址（如 `http://你的地址:5244`）、路径 `/dav`、账号密码、起始目录。
4. 测试连接 → 保存。

### 多网盘聚合与管理
1. 进入「设置」→「网盘管理」，右上角 ＋ 可继续添加存储（WebDAV / 天翼 / 123 / 夸克 / 光鸭均可混合添加）。
2. 「文件」页会以卡片形式列出全部已启用的网盘：点击卡片进入浏览；顶部搜索框在首页即为**跨网盘搜索**。
3. 网盘卡片上的开关可临时停用某个网盘（不出现在首页、不参与搜索），铅笔可重命名，垃圾桶可删除（仅删本机配置）。

### 123 云盘 / 夸克网盘 / 光鸭云盘
- **123 云盘**：填写账号（手机号 / 邮箱）与密码 → 点「登录获取会话」换取 token → 测试连接 → 保存。token 仅保存在本机。
  个人盘接口（`yun.123pan.cn`）需要 `auth-key` / `auth-value` 签名与 `loginuuid` 设备标识，本版已按抓包实现；若仍提示登录失效，请重新登录一次以刷新设备标识。
- **夸克网盘**：在电脑浏览器登录 `pan.quark.cn`，从开发者工具复制完整 Cookie → 粘贴 → 测试连接 → 保存。
- **光鸭云盘**：填写手机号 → 点「发送验证码」→ 填入收到的短信验证码 → 点「验证码登录」→ 测试连接 → 保存。若短信被风控拦截（提示需要人机验证），可直接把官方提供的开发者 token 填入 token 输入框跳过短信。令牌过期时自动用 refresh token 续期。

### 视频播放
在文件页点击视频文件即可进入视频页：默认竖屏播放，点右下角全屏按钮切横屏沉浸式；全屏下播放栏 5 秒无操作自动隐藏，点击画面中间可显示 / 隐藏播放栏，返回键先退出全屏。

### 本地音乐
进入「文件」页切换到本地音乐（或在设置中扫描），授予存储权限后自动列出设备上的音频。

---

## 七、项目结构

```
CloudTune/
├── app/src/main/java/com/cloudtune/player/
│   ├── CloudTuneApp.kt          # Application + 手动依赖容器
│   ├── core/                    # 模型、工具
│   ├── data/
│   │   ├── tianyi/              # 天翼扫码登录 + 签名数据层
│   │   ├── webdav/              # WebDAV 实现
│   │   ├── pan123/              # 123 云盘（登录 + 数据源）
│   │   ├── quark/               # 夸克网盘（Cookie + 数据源）
│   │   ├── guangya/             # 光鸭云盘（登录 + 数据源）
│   │   ├── db/                  # Room（歌单 / 收藏）
│   │   └── settings/            # DataStore（多存储账号列表）
│   ├── player/                  # MediaSessionService + 直链解析 + 缓存
│   └── ui/                      # Compose 界面（setup / browser / storages / video / player …）
├── docs/                        # 调研报告与技术方案
└── settings.gradle.kts
```

---

## 八、风险与免责说明

1. **非官方接口无 SLA**：天翼云盘、123 云盘、夸克网盘、光鸭云盘的登录方式、签名算法、接口参数可能随时调整，需被动跟进。
2. **账号风险**：使用非官方接口可能违反云服务协议，存在风控可能。建议优先扫码登录或 WebDAV 网关，不在任何地方留存明文密码。
3. 本项目仅供**个人学习与技术研究**，不鼓励绕过风控、批量爬取或商用分发；由此产生的账号或数据问题，开发者概不负责。

---

## 九、License

本项目基于 [**MIT License**](LICENSE) 开源。

---

## 十、参考与致谢

- [OpenList](https://github.com/OpenListTeam/OpenList) / [AList](https://github.com/AlistGo/alist)：天翼云盘 189pc 驱动、扫码登录与签名实现、WebDAV 网关。
- [tickstep/cloudpan189-api](https://github.com/tickstep/cloudpan189-api)：天翼云盘 API 参考。
- [AndroidX Media3](https://developer.android.com/media/media3)：ExoPlayer 与 MediaSession。
- 完整来源清单见 `docs/` 下调研报告。
