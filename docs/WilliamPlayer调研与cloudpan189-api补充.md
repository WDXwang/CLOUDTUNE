# 补充调研：William Player / LumiPlayer 项目定位 + cloudpan189-api 接口细节

> 调研时间：2026-10-27 ｜ 承接《tianyi_cloud_api_research.md》
> 任务：定位 GitHub 上的「William Player」安卓音乐播放器（疑似商用名 LumiPlayer，包名 com.williamplayer.mobile）；若无开源源码，则改用 tickstep/cloudpan189-api 补齐扫码登录、账密 RSA 登录、listFiles/getFileDownloadUrl 真实字段三件细节。

---

## 1. William Player / LumiPlayer 源码定位结论：**未找到开源源码**

### 1.1 GitHub 检索过程与结果
通过 GitHub Search API（`api.github.com/search/repositories`）实际检索：

| 关键词 | 结果数 | 说明 |
|---|---|---|
| `williamplayer` | **0** | 无任何同名仓库 |
| `lumiplayer` | 4 | 逐一甄别，无一与"包名 com.williamplayer.mobile 的天翼云盘音乐/影音 App"对应 |

4 个 `lumiplayer` 仓库甄别如下：

| 仓库 | 语言/性质 | 是否目标项目 |
|---|---|---|
| `heyunan111/LumiPlayer`（2026-03，0 star） | **Vue** 工程，4MB，无 description | ❌ 疑似配套 Web 后台，非安卓 App 本体 |
| `viniciussousacardoso/LumiPlayer` | Python **PyQt5+VLC Windows 桌面播放器** | ❌ 无关 |
| `mamila-jx/LumiPlayer` | 一个 **Jetpack Compose 媒体播放器 UI 组件库**（side project） | ❌ 同名巧合，与天翼云盘无关 |
| `czx2311746770/lumi-apk-release` | **"LumiPlayer 安卓版发布频道：APK 下载与应用内更新源"** | ⚠️ 这是**闭源 APK 的分发/更新仓库，不含源码** |

- 来源（API 原始返回）：https://api.github.com/search/repositories?q=lumiplayer ；https://api.github.com/search/repositories?q=williamplayer
- 发布频道仓库：https://github.com/czx2311746770/lumi-apk-release

**结论**：名为 LumiPlayer（包名 com.williamplayer.mobile）的安卓 App 是**闭源商用 App**，GitHub 上只放了一个 APK 发布/更新渠道仓库，**没有公开源码**。因此无法对其做"技术栈/架构/天翼云盘调用方式"的源码级分析。

### 1.2 但从应用截图可确认它对接天翼云盘的方式
第三方下载站（西西软件园）对 LumiPlayer v1.1.11 的实测截图显示，其「添加新文件源」页面"云盘存储"一栏列出：

- **天翼云盘 —— 网页登录 · Cookie 授权**
- 夸克网盘 —— 网页登录 · Cookie 授权
- 123云盘 —— 网页登录 · Cookie 授权
- （媒体服务器一栏：Emby / Jellyfin / Plex / 飞牛影视）

即：**LumiPlayer 对接天翼云盘走的是"内嵌网页登录 + 读取/粘贴 Cookie 授权"路线，而不是 App 内账号密码 RSA 登录、也不是自实现扫码**。这与 AList 当前推荐的"Cookie 登录"完全一致，验证了主报告"路线 B（Cookie/网页授权，避免滑块风控）"是商用产品的现实选择。
- 来源（截图与描述）：https://m.cr173.com/x/1708436

> 因此，下面按任务要求，改用 **tickstep/cloudpan189-api** 作为接口参考，补齐三件缺失细节。

---

## 2. 以 cloudpan189-api 为准补齐的三件细节

参考库：`github.com/tickstep/cloudpan189-api/cloudpan`（Apache-2.0），其上层 CLI 为 `cloudpan189-go`。
- 文档：https://pkg.go.dev/github.com/tickstep/cloudpan189-api/cloudpan
- 登录源码实证：https://raw.githubusercontent.com/tickstep/cloudpan189-api/master/cloudpan/app_login.go
- 下载源码实证：https://raw.githubusercontent.com/tickstep/cloudpan189-api/master/cloudpan/app_file_download.go

常量约定（从源码反推）：
- `WEB_URL` = `https://open.e.189.cn`（登录域）
- `API_URL` = `https://api.cloud.189.cn`（业务域）

### 2.1 扫码 / 二维码登录：**开源库未实现；官方 Web 支持扫码，但无公开可复用的程序化轮询端点**
- 天翼云盘 Web 登录框确实支持"扫码登录"：用户用**天翼云盘安卓客户端（或微信等）扫网页二维码 → 手机端点确认**完成登录。官方帮助："登录天翼云盘网页端首页，点击登录框右上角二维码标签……打开天翼云盘安卓客户端【+】->扫一扫。"
  - 来源：https://cloud.189.cn/web/static/template/help.html?showService=1
  - 天翼账号扫码说明：https://e.189.cn/help/registerHelp.do
- **但**：cloudpan189-api 只实现了 `Login(username,password)` / `LoginWithCaptcha(username,password,captcha)` / `AppLogin(username,password)` 三种**账密（+验证码）**登录，**没有任何二维码生成/轮询登录的函数**；AList 189 驱动也因滑块验证码而改推"Cookie 登录"。
  - 来源：https://pkg.go.dev/github.com/tickstep/cloudpan189-api/cloudpan
- **结论与建议**：在安卓 App 上替代"明文密码 / 手动粘贴 Cookie"的现实方案不是自研扫码轮询（端点未公开、需逆向且易变），而是：
  1. **内嵌 WebView 打开官方 Web 登录页**（含扫码登录），登录成功后从 WebView CookieJar 中取出 `COOKIE_SESSION`/`SSON` 等 Cookie 复用 —— 这正是 LumiPlayer"网页登录·Cookie 授权"和 AList 的做法；
  2. 真要程序化登录，才用下面 2.2 的账密 RSA 流程（需自己扛滑块/验证码风控）。

### 2.2 账号密码登录在安卓端的完整加密流程（源码级）
来自 `app_login.go` 的 `AppLogin()` 真实流程：

**第 1 步：拉取登录上下文（含 RSA 公钥）**
```
GET https://open.e.189.cn/unifyLoginForPC.action
    ?appId=8025431004&clientType=10020
    &returnURL=https://m.cloud.189.cn/zhuanti/2020/loginErrorPc/index.html
    &timeStamp=<毫秒>
```
从返回 HTML 用正则提取 6 个值：
- `captchaToken' value='...'`
- `lt = "..."`
- `returnUrl = '...'`
- `paramId = "..."`
- `reqId = "..."`
- `j_rsaKey" value="..."` ← **RSA 公钥（裸 base64，需自行包成 PEM）**

**第 2 步：组装公钥并 RSA 加密账密**
```
pubPEM = "-----BEGIN PUBLIC KEY-----\n" + j_rsaKey + "\n-----END PUBLIC KEY-----"
rsaUserName = RSA_Encrypt(pubPEM, username)   // PKCS1
rsaPassword = RSA_Encrypt(pubPEM, password)
```

**第 3 步：提交登录**
```
POST https://open.e.189.cn/api/logbox/oauth2/loginSubmit.do
Content-Type: application/x-www-form-urlencoded
Referer: https://open.e.189.cn/api/logbox/oauth2/unifyAccountLogin.do
Cookie: LT=<lt>
X-Requested-With: XMLHttpRequest
REQID: <reqId>
lt: <lt>

appKey=8025431004
accountType=02
userName={RSA}<B64toHex(base64(rsaUserName))>
password={RSA}<B64toHex(base64(rsaPassword))>
validateCode=
captchaToken=<captchaToken>
returnUrl=<returnUrl>
mailSuffix=@189.cn
dynamicCheck=FALSE
clientType=10020
cb_SaveName=1
isOauth2=false
state=
paramId=<paramId>
```
> 关键细节：RSA 密文先 base64，再经 `B64toHex` 转成**十六进制串**，前面拼 `{RSA}` 前缀提交（与主报告 tianyiyun.py 完全一致）。
- 返回 JSON：`{result:0, toUrl:"..."}`；`result!=0` 即登录失败（常为风控/验证码）。

**第 4 步：用 toUrl 换业务 session**
```
GET https://api.cloud.189.cn/getSessionForPC.action
    ?clientType=TELEMAC&version=1.0.0&channelId=web_cloud.189.cn
    &redirectURL=<urlencode(toUrl)>
Accept: application/json;charset=UTF-8
```
返回 `appSessionResp`（JSON），核心字段：
```json
{ "res_code":0,
  "sessionKey":"...", "sessionSecret":"...",
  "familySessionKey":"...", "familySessionSecret":"...",
  "accessToken":"...", "refreshToken":"...",
  "loginName":"..." }
```

**第 5 步：换长期 accessToken（ssk）**
```
GET https://api.cloud.189.cn/open/oauth2/getAccessTokenBySsKey.action?sessionKey=<sessionKey>
AppKey: 601102120
Timestamp: <毫秒>
Signature: md5(Timestamp=<t>&sessionKey=<sk>&AppKey=601102120)
Sign-Type: 1
```
返回 `{accessToken, expiresIn(默认约30天)}`。

> 安卓实现提示：RSA 用 JDK/`javax.crypto` 或 BouncyCastle；`B64toHex` 是自写的 base64→hex 映射（主报告 tianyiyun.py 里有 Python 版可对照移植）。`appId=8025431004` 是 MAC/PC 客户端伪装 ID。

### 2.3 个人云 listFiles 与 getFileDownloadUrl 的真实请求/响应字段

**(a) 获取下载直链 —— `AppGetFileDownloadUrl(fileId)`**（源码实证）
```
GET https://api.cloud.189.cn/getFileDownloadUrl.action?fileId=<fileId>&dt=3&flag=1&<PcClientInfoSuffixParam>
Headers:
  Date: <GMT 时间串>
  SessionKey: <sessionKey>
  Signature: Hmac(SessionSecret, SessionKey, "GET", <完整URL>, <Date>)
  X-Request-ID: <uuid>
```
- 注意：这里业务签名用的是 **HMAC（SessionSecret 为密钥，内容含 SessionKey+Method+URL+Date）**，与取 ssk 时的 md5 不同。
- 响应是 **XML**：`<fileDownloadUrl>https://...cdn...</fileDownloadUrl>`（取 inner text，并把 `&amp;` 还原为 `&`）。
- 错误：body 里带统一错误节点时直接抛出 ApiError（如 session 失效）。

**(b) 用直链流式下载（支持 Range）—— `AppDownloadFileData(url, range, cb)`**
```
GET <fileDownloadUrl>&<PcClientInfoSuffixParam>
Headers:
  Date: <GMT>
  SessionKey: <sessionKey>
  Signature: Hmac(SessionSecret, SessionKey, "GET", <完整URL>, <Date>)
  X-Request-ID: <uuid>
  Range: bytes=<offset>-<end>     // 仅当需要分段/拖动时加
```
- `AppFileDownloadRange{ Offset int64; End int64 }`（都含端点）；`End=0` 表示 `bytes=<offset>-` 到文件尾。
- 即：**直链本身就是支持 HTTP Range(206) 的 CDN 地址**，ExoPlayer/Media3 可直接把 `<fileDownloadUrl>` 当媒体源，拖动进度由播放器发 Range 实现 —— 与主报告结论一致。

**(c) 文件列表 —— `AppFileList(AppFileListParam)`**（结构体实证）
请求参数 `AppFileListParam`：
| 字段 | 含义 |
|---|---|
| `FamilyId` | 家庭云 ID（个人云置 0） |
| `FileId` | 要列的文件夹 ID（根目录 `-11`） |
| `OrderBy` | `filename` / `filesize` / `lastOpTime` |
| `OrderSort` | `ASC` / `DESC` |
| `PageNum` | 页码，从 1 开始 |
| `PageSize` | 每页大小，默认 60 |
| `ConstructPath` | 是否额外构建完整路径 |

响应 `AppFileListResult`：
| 字段 | 含义 |
|---|---|
| `LastRev` | 列表版本号 |
| `Count` | 条目总数 |
| `FileList` | `[]*AppFileEntity` |

条目 `AppFileEntity`（XML tag）：
| 字段 | XML tag | 说明 |
|---|---|---|
| FileId | `id` | 文件/文件夹 ID |
| ParentId | `parentId` | 父文件夹 ID |
| FileSize | `size` | 字节 |
| FileMd5 | (md5) | 空文件默认 `D41D8CD98F00B204E9800998ECF8427E` |
| CreateTime | `createDate` | |
| LastOpTime | `lastOpTime` | |
| Path | `path` | 完整路径 |
| MediaType | `mediaType` | **2=音乐**（官方口径：1图片/2音乐/3视频/4文档） |
| IsFolder | (isFolder) | 文件夹/文件 |
| SubFileCount | `fileCount` | 文件夹子项数 |
| Rev | `rev` | 版本号 |

- 官方开放文档对 `mediaType` 的权威枚举：`1 图片 / 2 音乐 / 3 视频 / 4 文档`，列音乐即按 `mediaType=2` 过滤。
  - 来源：https://id.189.cn/html/api_detail_141.html
- 家庭云：`AppFamilyGetFamilyList()` 取 `familyId` 列表后，列目录/取下载直链都在参数里带 `FamilyId`，并用 `familySessionKey/familySessionSecret` 体系签名；`AppFamilyGetFileDownloadUrl(familyId, fileId)` 取家庭云直链。
  - 来源：https://pkg.go.dev/github.com/tickstep/cloudpan189-api/cloudpan

---

## 3. 对 App 技术选型的补充结论

1. **别指望拿到 William Player/LumiPlayer 源码**——它是闭源商用 App，GitHub 只有 APK 分发仓库。但它的产品形态（Web 内嵌登录 + Cookie 授权 + 聚合 Emby/Jellyfin/Plex/多网盘 + 自带播放器）就是你要做的 App 的现成参照。
2. **登录优先级建议**：
   - 首选 **WebView 内嵌官方登录页取 Cookie**（可让用户扫码，避免明文密码和滑块风控）——与 LumiPlayer/AList 一致；
   - 次选才是上面 2.2 的账密 RSA 直登（要自处理 `captchaToken`/滑块，安卓端需自行维护 RSA+Hex 提交与 `getSessionForPC`/`getAccessTokenBySsKey` 两步换 token）。
3. **列音乐 + 播放**：用 `AppFileListParam{FileId=文件夹, PageNum/PageSize, OrderBy=filename}` 列目录、按 `mediaType==2` 过滤出音乐；播放时对每个 `fileId` 调 `getFileDownloadUrl.action` 现取 `<fileDownloadUrl>`，直接喂给 Media3/ExoPlayer（支持 Range 拖动），直链短缓存、过期重取。
4. **个人云与家庭云都支持**：家庭云只是多一步 `AppFamilyGetFamilyList()` 拿 `familyId`，后续列表/取链换用 family 会话即可。

---

### 来源汇总
- LumiPlayer 应用实测（含"网页登录·Cookie 授权"截图）：https://m.cr173.com/x/1708436
- GitHub 仓库检索 API：https://api.github.com/search/repositories?q=williamplayer ；https://api.github.com/search/repositories?q=lumiplayer
- LumiPlayer APK 发布渠道（无源码）：https://github.com/czx2311746770/lumi-apk-release
- cloudpan189-api 登录源码：https://raw.githubusercontent.com/tickstep/cloudpan189-api/master/cloudpan/app_login.go
- cloudpan189-api 下载/Range 源码：https://raw.githubusercontent.com/tickstep/cloudpan189-api/master/cloudpan/app_file_download.go
- cloudpan189-api 全部类型/函数清单：https://pkg.go.dev/github.com/tickstep/cloudpan189-api/cloudpan
- 官方扫码登录说明：https://cloud.189.cn/web/static/template/help.html?showService=1 ；https://e.189.cn/help/registerHelp.do
- 官方 mediaType 枚举（1图片/2音乐/3视频/4文档）：https://id.189.cn/html/api_detail_141.html
