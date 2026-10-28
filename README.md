# CloudTune · 云盘音乐

一款安卓（Android）音乐播放器，**可以直接播放天翼云盘里的音乐文件，无需先下载到手机**。同时支持以标准 WebDAV 方式连接自建 [AList](https://alist.nn.ci/) 等网盘网关。

> 应用界面全部使用 Jetpack Compose 构建，播放内核为 Media3 / ExoPlayer，支持在线流式播放、后台播放、通知栏控制、播放队列与本地收藏。

---

## 一、功能特性

- ☁️ **天翼云盘直连播放**：浏览云盘目录，点击即播，走云端 CDN 直链，支持 HTTP Range（可拖动进度）。
- 🔌 **WebDAV 后端（推荐）**：连接自建 AList（或任意 WebDAV 服务），把天翼云盘的登录 / 风控 / 续期隔离在服务端。
- 🎵 **完整播放器**：播放 / 暂停、上一首 / 下一首、进度条拖动、顺序 / 列表循环 / 单曲循环、随机播放。
- 📋 **播放队列管理**：查看队列、删除、上下移动排序。
- 🎧 **后台播放与通知栏控制**：基于 MediaSession，锁屏 / 通知栏 / 蓝牙耳机按键均可控制。
- ⭐ **我喜欢的**：收藏列表保存在本地数据库（Room）。
- 💾 **边播边缓存**：播放过的音频块写入本地缓存（默认上限 512MB，LRU 自动淘汰）。
- 🎶 支持常见音乐格式：`mp3 / flac / wav / ape / m4a / ogg / aac / wma / opus / aiff / dsf / dff` 等。

---

## 二、关于天翼云盘接入（务必先读）

**结论：天翼云盘没有面向个人开发者开放、可直接注册使用的官方文件 API。**

- 官方开放平台（`id.189.cn`）虽有完整的云盘文件 REST API（`listFiles.action`、`getFileDownloadUrl.action` 等），但需要 **企业开发者资质 + APPID/AppSecret 审批**，个人独立开发者基本拿不到。
- 天翼云盘**原生不支持 WebDAV**。
- 因此本项目提供两条路线（详见 [`docs/天翼云盘接入调研报告.md`](docs/天翼云盘接入调研报告.md)）：

| 路线 | 说明 | 稳定性 | 账号风险 | 建议 |
|---|---|---|---|---|
| **B. WebDAV + 自建 AList** | App 作为标准 WebDAV 客户端连接 AList，AList 再对接天翼云盘 | 较好 | 风险隔离在服务端 | ✅ **推荐** |
| **A. App 内直连非官方 API** | 粘贴浏览器 Cookie 登录，直接调用云端接口 | 一般，可能随官方更新失效 | 直接落在本人账号 | ⚠️ 实验性 |

> 关于参考项目：用户提到的 **William Player / LumiPlayer**（包名 `com.williamplayer.mobile`）经核实**未开源**，其天翼云盘接入同样使用“网页登录取 Cookie”的方式。详见 [`docs/WilliamPlayer调研与cloudpan189-api补充.md`](docs/WilliamPlayer调研与cloudpan189-api补充.md)。

---

## 三、编译环境要求

- **Android Studio**：Iguana (2023.2) 或更高版本（内置 JDK 17）。
- **JDK**：17（命令行编译时需 `JAVA_HOME` 指向 JDK 17）。
- **Gradle**：8.9（项目自带 Wrapper，无需单独安装）。
- **Android SDK**：`compileSdk 34`、`minSdk 26`、`build-tools 34.0.0`（首次构建 Android Studio 会自动下载）。

---

## 四、编译方法

### 方式 1：Android Studio（最简单）

1. 打开 Android Studio → `Open` → 选择 `CloudTune` 目录。
2. 等待 Gradle Sync 完成（首次会自动下载依赖）。
3. 点击 ▶️ 运行到手机，或 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`。

### 方式 2：命令行

```bash
# 进入项目根目录
cd CloudTune

# Debug 包（首次会自动下载 Gradle 8.9）
./gradlew assembleDebug
# 产物位置：
# app/build/outputs/apk/debug/app-debug.apk

# Release 包
./gradlew assembleRelease
# 产物位置：
# app/build/outputs/apk/release/app-release-unsigned.apk（需自行签名）
```

> 中国大陆网络下，项目已默认在 `settings.gradle.kts` 中配置了阿里云镜像；Gradle 发行版使用了腾讯镜像。若仍下载缓慢，可在 `gradle/wrapper/gradle-wrapper.properties` 中更换为官方源。

---

## 五、安装到手机

- 用数据线连接手机（开启“开发者选项 / USB 调试”），Android Studio 直接运行；或
- 把 `app-debug.apk` 传到手机，点击安装（需允许“安装未知来源应用”）。

---

## 六、使用方法

### 路线 B：WebDAV + AList（推荐）

1. **部署 AList**：NAS / VPS / 路由器均可，单二进制或 Docker：
   ```bash
   docker run -d --restart=always -v /etc/alist:/opt/alist/data \
     -p 5244:5244 --name=alist 'xhofe/alist:latest'
   ```
2. 登录 AList 后台，添加存储 → 选择 **“电信天翼云盘”（189pc）**，按提示填入天翼云盘 Cookie（AList 文档有获取方法）。
3. 在 AList **用户 → 权限**中开启 **Webdav Read**（v3.42.0+）。
4. 打开 CloudTune → 后端选择 **WebDAV（推荐）**：
   - 服务器地址：`http://你的地址:5244`
   - WebDAV 路径：`/dav`
   - 用户名 / 密码：AList 后台账号密码
   - 起始目录：`/`（或填音乐目录，如 `/音乐`）
5. 点击“测试连接”，成功后“保存并进入”即可浏览、播放。

### 路线 A：天翼直连（实验性）

1. 用电脑浏览器登录 **cloud.189.cn**。
2. 打开开发者工具（F12）→ Network（或 Application）面板，复制完整的 **Cookie** 字符串。
3. 打开 CloudTune → 后端选择 **天翼直连（实验）**，粘贴 Cookie，根目录 ID 保持默认 `-11`。
4. 测试连接 → 保存并进入。

> Cookie 等同于登录凭据，应用仅将其保存在本机（DataStore）。Cookie 过期后需重新获取；如出现滑块验证，请在浏览器完成验证后再复制新 Cookie。

---

## 七、项目结构

```
CloudTune/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                         # 图标、主题、配置
│       └── java/com/cloudtune/player/
│           ├── CloudTuneApp.kt          # Application + 手动依赖容器（组合根）
│           ├── MainActivity.kt
│           ├── core/
│           │   ├── model/Models.kt      # 数据模型（CloudFile / MusicTrack / ServerConfig）
│           │   └── util/MusicUtils.kt   # 扩展名识别、格式化
│           ├── data/
│           │   ├── CloudDataSource.kt   # 云端数据源统一接口
│           │   ├── MusicRepository.kt   # 仓库接口
│           │   ├── webdav/              # WebDAV 实现（PROPFIND）
│           │   ├── tianyi/              # 天翼云盘直连实现（非官方 API）
│           │   ├── db/                  # Room 收藏数据库
│           │   ├── settings/            # DataStore 配置
│           │   └── repo/                # 仓库实现
│           ├── player/
│           │   ├── PlaybackService.kt   # MediaSessionService（后台播放）
│           │   ├── PlayerControllerManager.kt  # 前台 MediaController + 状态流
│           │   ├── CloudMedia.kt        # 自定义播放 URI 编解码
│           │   ├── CloudStreamResolverFactory.kt # 直链解析 + 缓存
│           │   └── PlaybackModels.kt
│           └── ui/
│               ├── theme/  navigation/ components/
│               ├── setup/  browser/
│               ├── player/ queue/
│               ├── favorites/ settings/
├── docs/                                # 调研报告与技术方案
├── gradle/libs.versions.toml            # 版本目录
└── settings.gradle.kts
```

---

## 八、风险与免责说明

1. **非官方接口无 SLA**：天翼云盘的登录方式、签名算法、接口参数可能随时调整，导致直连功能失效，需被动跟进。
2. **账号风险**：使用非官方客户端 / 逆向接口可能违反天翼云盘用户服务协议，存在被风控、限制功能甚至冻结账号的可能。**强烈建议使用 WebDAV + AList 路线，把风险隔离在自己的服务端，并优先使用 Cookie 方式而非在任何地方留存明文密码。**
3. 本项目仅供**个人学习与技术研究**，不鼓励绕过风控、批量爬取或商用分发；由此产生的任何账号或数据问题，开发者概不负责。

---

## 九、参考与致谢

- [AList](https://github.com/AlistGo/alist)：天翼云盘 189pc / 189CloudPc 驱动、WebDAV 网关。
- [tickstep/cloudpan189-api](https://github.com/tickstep/cloudpan189-api)：天翼云盘 API 封装参考。
- [AndroidX Media3](https://developer.android.com/media/media3)：ExoPlayer 与 MediaSession。
- 完整来源清单见 `docs/` 下两份调研报告。
