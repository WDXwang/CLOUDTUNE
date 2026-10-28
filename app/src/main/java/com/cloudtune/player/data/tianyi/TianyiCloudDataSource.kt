package com.cloudtune.player.data.tianyi

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/**
 * ============================================================
 *  实验性 · 非官方接口，风险自担
 * ------------------------------------------------------------
 *  天翼云盘数据源，支持两种登录态：
 *
 *  A. token 会话（OpenList 189pc 方式，推荐，不绑定 IP）：
 *     扫码登录后经 getSessionForPC 换到 sessionKey/sessionSecret，
 *     每次请求带 HMAC-SHA1 签名走 api.cloud.189.cn，切换网络仍有效。
 *
 *  B. Cookie（legacy，绑定登录 IP）：
 *     走 cloud.189.cn/api/portal 网页接口，切换网络需重新登录。
 *     仅在未配置 token 会话时作为回退。
 *
 *  已从 OpenList 189pc 驱动移植（drivers/189pc）：
 *  - 签名：SignatureHeader / signatureOfHmac / clientSuffix
 *  - 列目录：GET api.cloud.189.cn/listFiles.action（fileListAO 分页）
 *  - 直链：GET api.cloud.189.cn/getFileDownloadUrl.action → 302 CDN
 *  - 会话刷新：InvalidSessionKey 时用 accessToken 续期并重试一次
 * ============================================================
 */
class TianyiCloudDataSource(
    private val config: ServerConfig,
    private val client: OkHttpClient,
) : CloudDataSource {

    /** 不跟随重定向的客户端，用于逐级解析下载链接的 302 跳转。 */
    private val noRedirectClient: OkHttpClient =
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    // ---------- token 会话（推荐） ----------
    private var sessionKey = config.tianyiSessionKey
    private var sessionSecret = config.tianyiSessionSecret
    private var accessToken = config.tianyiAccessToken
    private var refreshToken = config.tianyiRefreshToken
    private val useToken: Boolean get() = sessionKey.isNotEmpty() && sessionSecret.isNotEmpty()

    // ---------- Cookie（legacy 回退） ----------
    private val cookie: String = sanitizeCookie(config.tianyiCookie)

    private fun ensureAuth() {
        if (!useToken && cookie.isBlank()) error("未配置登录态：请先扫码登录或粘贴 Cookie")
        if (!useToken && !containsCookieName(cookie, "COOKIE_LOGIN_USER")) {
            error("Cookie 不完整：缺少登录字段 COOKIE_LOGIN_USER，请重新登录")
        }
    }

    override suspend fun test(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            ensureAuth()
            if (useToken) listFilesSigned(config.tianyiRootFolderId, 1)
            else listFilesPage(config.tianyiRootFolderId, 1)
            Unit
        }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> = withContext(Dispatchers.IO) {
        ensureAuth()
        val folderId = dir?.id ?: config.tianyiRootFolderId
        if (useToken) listSigned(folderId) else listCookie(folderId)
    }

    override suspend fun resolveStream(file: CloudFile): StreamSource = withContext(Dispatchers.IO) {
        ensureAuth()
        if (useToken) resolveSignedStream(file) else resolveCookieStream(file)
    }

    override fun close() { /* 无需要释放的内部状态 */ }

    // ============================================================
    // A. token + 签名（OpenList 189pc）
    // ============================================================

    /** 带签名执行 GET，返回响应体文本；会话失效时自动续期并重试一次。 */
    private fun signedGet(apiPath: String, params: Map<String, String>): String {
        var attempt = 0
        while (true) {
            val fullUrl = "https://api.cloud.189.cn$apiPath"
            val date = TianyiAuth.httpDate()
            val sign = TianyiAuth.signature(sessionSecret, sessionKey, "GET", fullUrl, date)
            val httpUrl = fullUrl.toHttpUrl().newBuilder().apply {
                TianyiAuth.clientSuffix().forEach { (k, v) -> addQueryParameter(k, v) }
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val req = Request.Builder().url(httpUrl)
                .header("Date", date)
                .header("SessionKey", sessionKey)
                .header("X-Request-ID", TianyiAuth.uuid())
                .header("Signature", sign)
                .header("User-Agent", UA)
                .header("Accept", "application/json;charset=UTF-8")
                .get().build()
            val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            if (body.contains("InvalidSessionKey", ignoreCase = true) ||
                body.contains("userSessionBO is null", ignoreCase = true)
            ) {
                // 会话失效：用 accessToken 续期后重试一次
                if (attempt == 0 && refreshSession()) { attempt = 1; continue }
            }
            return body
        }
    }

    /** 续期会话：GET getSessionForPC.action?appId&accessToken → 更新 sessionKey/Secret。 */
    private fun refreshSession(): Boolean {
        return runCatching {
            val fullUrl = "https://api.cloud.189.cn/getSessionForPC.action"
            val httpUrl = fullUrl.toHttpUrl().newBuilder().apply {
                TianyiAuth.clientSuffix().forEach { (k, v) -> addQueryParameter(k, v) }
                TianyiAuth.deviceParams().forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter("appId", APP_ID)
                addQueryParameter("accessToken", accessToken)
            }.build()
            val req = Request.Builder().url(httpUrl)
                .header("X-Request-ID", TianyiAuth.uuid())
                .header("User-Agent", UA)
                .get().build()
            val json = client.newCall(req).execute().use {
                JSONObject(it.body?.string().orEmpty())
            }
            val newKey = json.optString("sessionKey")
            val newSecret = json.optString("sessionSecret")
            if (newKey.isEmpty() || newSecret.isEmpty()) {
                refreshToken()  // 会话已彻底失效，尝试用 refreshToken 续期
            } else {
                sessionKey = newKey
                sessionSecret = newSecret
                true
            }
        }.getOrDefault(false)
    }

    /** 用 refreshToken 换取新的 access/refresh token，再续期会话。 */
    private fun refreshToken(): Boolean {
        return runCatching {
            val form = okhttp3.FormBody.Builder()
                .add("clientId", APP_ID)
                .add("refreshToken", refreshToken)
                .add("grantType", "refresh_token")
                .add("format", "json")
                .build()
            val req = Request.Builder()
                .url("https://open.e.189.cn/api/oauth2/refreshToken.do")
                .header("User-Agent", UA)
                .post(form)
                .build()
            val json = client.newCall(req).execute().use {
                JSONObject(it.body?.string().orEmpty())
            }
            val newAccess = json.optString("accessToken")
            val newRefresh = json.optString("refreshToken")
            if (newAccess.isEmpty()) false
            else {
                accessToken = newAccess
                refreshToken = newRefresh.ifEmpty { refreshToken }
                refreshSession()  // 用新 accessToken 再续期 sessionKey
            }
        }.getOrDefault(false)
    }

    /** 签名列目录（分页拉取，pageSize=1000）。 */
    private fun listSigned(folderId: String): List<CloudFile> {
        val out = ArrayList<CloudFile>()
        var page = 1
        while (true) {
            val resp = listFilesSigned(folderId, page)
            val ao = resp.optJSONObject("fileListAO") ?: break
            val folders = ao.optJSONArray("folderList") ?: JSONObject.NULL
            val files = ao.optJSONArray("fileList") ?: JSONObject.NULL
            if (folders is org.json.JSONArray) {
                for (i in 0 until folders.length()) {
                    val o = folders.optJSONObject(i) ?: continue
                    out.add(CloudFile(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        path = folderId,
                        isDir = true,
                        size = 0L,
                    ))
                }
            }
            if (files is org.json.JSONArray) {
                for (i in 0 until files.length()) {
                    val o = files.optJSONObject(i) ?: continue
                    out.add(CloudFile(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        path = folderId,
                        isDir = false,
                        size = o.optLong("size", 0L),
                    ))
                }
            }
            val pageCount = (if (folders is org.json.JSONArray) folders.length() else 0) +
                (if (files is org.json.JSONArray) files.length() else 0)
            if (pageCount < PAGE_SIZE) break
            page++
        }
        return out
    }

    private fun listFilesSigned(folderId: String, pageNum: Int): JSONObject {
        val body = signedGet("/listFiles.action", mapOf(
            "folderId" to folderId,
            "fileType" to "0",
            "mediaAttr" to "0",
            "iconOption" to "5",
            "pageNum" to pageNum.toString(),
            "pageSize" to PAGE_SIZE.toString(),
            "recursive" to "0",
            "orderBy" to "filename",
            "descending" to "false",
        ))
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: error("列目录失败：非 JSON 响应")
        val resCode = json.opt("res_code")
        if (resCode != null && resCode.toString() != "0" && resCode.toString() != "") {
            error("列目录失败：${json.optString("res_message", body)}")
        }
        return json
    }

    /**
     * 签名取播放直链：getFileDownloadUrl.action → 一次 302 取 CDN Location 即返回。
     *
     * 注意：天翼 CDN 的一次性签名链接**首次 GET 即被消耗**，所以 302 后只取 Location 返回给
     * 播放器，绝不能再对结果做二次 GET（否则播放器拿到的链接已失效）。与 OpenList 189pc 一致。
     */
    private fun resolveSignedStream(file: CloudFile): StreamSource {
        val body = signedGet("/getFileDownloadUrl.action", mapOf(
            "fileId" to file.id,
            "dt" to "3",
            "flag" to "1",
        ))
        val json = runCatching { JSONObject(body) }.getOrNull() ?: error("获取下载地址失败：非 JSON 响应")
        var dl = json.optString("fileDownloadUrl")
        if (dl.isBlank()) dl = json.optString("downloadUrl")
        if (dl.isBlank()) error("下载地址为空：${body.take(200)}")
        val entryUrl = normalizeUrl(dl.replace("&amp;", "&"))
        val req = Request.Builder().url(entryUrl)
            .header("User-Agent", UA)
            .get().build()
        noRedirectClient.newCall(req).execute().use { resp ->
            if (resp.code in 300..399) {
                val loc = resp.header("Location") ?: error("解析下载地址失败：重定向缺少 Location")
                // 直接返回 302 目标，不再二次 GET（一次性 CDN 链接）
                return StreamSource(url = normalizeUrl(loc), headers = mapOf("User-Agent" to UA))
            } else if (resp.isSuccessful) {
                return StreamSource(url = entryUrl, headers = mapOf("User-Agent" to UA))
            } else {
                error("解析下载地址失败：HTTP ${resp.code} ${resp.body?.string().orEmpty().take(200)}")
            }
        }
    }

    // ============================================================
    // B. Cookie（legacy 回退，绑定 IP）
    // ============================================================

    private fun listCookie(folderId: String): List<CloudFile> {
        val json = listFilesPage(folderId, 1)
        if (json.optInt("res_code", 0) != 0) {
            error("列目录失败：${json.optString("res_message")}")
        }
        val data = json.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<CloudFile>(data.length())
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            out.add(CloudFile(
                id = o.optString("fileId"),
                name = o.optString("fileName"),
                path = folderId,
                isDir = o.optBoolean("isFolder", false),
                size = o.optLong("fileSize", 0L),
            ))
        }
        return out
    }

    private fun resolveCookieStream(file: CloudFile): StreamSource {
        val infoUrl = "https://cloud.189.cn/api/portal/getFileInfo.action" +
            "?fileId=${file.id}&noCache=${nonce()}"
        val infoReq = Request.Builder().url(infoUrl)
            .header("Cookie", cookie)
            .header("Accept", "application/json;charset=UTF-8")
            .header("Referer", "https://cloud.189.cn/")
            .header("User-Agent", UA)
            .get().build()
        var entryUrl: String
        client.newCall(infoReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (body.contains("InvalidSessionKey", ignoreCase = true)) {
                    error("登录态失效或 IP 校验失败：$body")
                }
                error("获取文件信息失败：HTTP ${resp.code}，$body")
            }
            val json = JSONObject(body)
            if (json.optInt("res_code", 0) != 0) {
                error("获取文件信息失败：${json.optString("res_message")}")
            }
            val dl = json.optString("downloadUrl").ifBlank { json.optString("fileDownloadUrl") }
            if (dl.isBlank()) error("下载地址为空")
            entryUrl = normalizeUrl(dl)
        }
        var url = entryUrl
        // 只做一次 302 取 Location，不再二次 GET（一次性 CDN 链接）
        val req = Request.Builder().url(url)
            .header("Cookie", cookie)
            .header("User-Agent", UA)
            .get().build()
        noRedirectClient.newCall(req).execute().use { resp ->
            if (resp.code in 300..399) {
                val loc = resp.header("Location") ?: error("解析下载地址失败：重定向缺少 Location")
                return StreamSource(url = normalizeUrl(loc), headers = mapOf("User-Agent" to UA))
            } else if (resp.isSuccessful) {
                return StreamSource(url = url, headers = mapOf("User-Agent" to UA))
            } else {
                error("解析下载地址失败：HTTP ${resp.code} ${resp.body?.string().orEmpty().take(200)}")
            }
        }
    }

    /** 请求文件列表（Cookie 版），返回原始 JSON。 */
    private fun listFilesPage(folderId: String, page: Int): JSONObject {
        val url = "https://cloud.189.cn/api/portal/listFiles.action" +
            "?fileId=$folderId&noCache=${nonce()}"
        val req = Request.Builder().url(url)
            .header("Cookie", cookie)
            .header("Accept", "application/json;charset=UTF-8")
            .header("Referer", "https://cloud.189.cn/web/main/file/folder/$folderId")
            .header("User-Agent", UA)
            .get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (body.contains("InvalidSessionKey", ignoreCase = true)) {
                    error("登录态失效或 IP 校验失败：${JSONObject(body).optString("errorMsg", body)}")
                }
                error("列目录失败：HTTP ${resp.code}，$body")
            }
            return JSONObject(body)
        }
    }

    // ---------- 工具 ----------

    private fun nonce(): String = UUID.randomUUID().toString().replace("-", "")

    private fun sanitizeCookie(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.trim()
        s = Regex("^[Cc]ookie\\s*[:：]\\s*").replace(s, "")
        s = s.replace("\r", "\n").replace("\n", ";")
        val seen = LinkedHashSet<String>()
        for (p in s.split(";")) {
            val item = p.trim()
            if (item.isBlank()) continue
            seen.add(item)
        }
        return joinToStringUnique(seen)
    }

    private fun joinToStringUnique(items: LinkedHashSet<String>): String {
        val names = HashSet<String>()
        val result = ArrayList<String>()
        for (item in items) {
            val name = item.substringBefore("=").trim().lowercase()
            if (names.add(name)) result.add(item)
        }
        return result.joinToString("; ")
    }

    private fun containsCookieName(cookieStr: String, name: String): Boolean {
        val target = name.lowercase()
        return cookieStr.split(";").any {
            it.trim().substringBefore("=").trim().lowercase() == target
        }
    }

    private fun normalizeUrl(u: String): String {
        var s = u.trim().replace("&amp;", "&")
        when {
            s.startsWith("http://", ignoreCase = true) ->
                s = "https://" + s.substring("http://".length)
            s.startsWith("https:", ignoreCase = true) -> {
                val rest = s.substring("https:".length)
                if (!rest.startsWith("//")) s = "https://$rest"
            }
            s.startsWith("//") -> s = "https:$s"
        }
        return s
    }

    private companion object {
        const val APP_ID = "9317140619"
        const val PAGE_SIZE = 1000
        const val MAX_HOPS = 8
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
