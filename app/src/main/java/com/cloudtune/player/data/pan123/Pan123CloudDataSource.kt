package com.cloudtune.player.data.pan123

import android.util.Base64
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.CRC32

/**
 * ============================================================
 *  实验性 · 非官方接口，风险自担
 * ------------------------------------------------------------
 *  123 云盘数据源（Bearer token 方式，token 由 [Pan123Auth.login] 换取）。
 *
 *  - 列目录：GET https://yun.123pan.cn/b/api/file/list/new（需签名头，Next=-1 为末页）
 *  - 取直链：POST https://yun.123pan.cn/api/file/download_info（注意没有 /b/）
 *  - 签名：auth-key = crc32(替换表映射后的 UTC 分钟)；auth-value = ts-rand-crc32(ts|rand|path|web|3|key)
 * ============================================================
 */
class Pan123CloudDataSource(
    private val config: ServerConfig,
    private val client: OkHttpClient,
) : CloudDataSource {

    private val token: String = config.pan123Token.trim()

    /** 设备标识：老配置里没有时临时生成，仅影响风控，不影响签名。 */
    private val loginUuid: String = config.pan123LoginUuid.trim()
        .ifBlank { Pan123Auth.randomLoginUuid() }

    /** 根目录 fileId，未填写时回落到 "0"。 */
    private val rootFolderId: String = config.pan123RootFolderId.ifBlank { "0" }

    /** 列目录时记录的下载参数，键为 fileId（直链接口需要）。 */
    private val fileMeta = ConcurrentHashMap<String, Pan123Meta>()

    override suspend fun test(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            ensureToken()
            fetchPage(rootFolderId, "0")
            Unit
        }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> = withContext(Dispatchers.IO) {
        ensureToken()
        // dir == null 表示配置中的根目录；否则 dir.id 即 123 的 fileId
        val parentId = dir?.id ?: rootFolderId
        val out = ArrayList<CloudFile>()
        val seen = HashSet<String>()

        var next = "0"
        var pages = 0
        while (pages < MAX_PAGES) {
            val (files, cursor) = fetchPage(parentId, next) ?: break
            for (f in files) {
                if (seen.add(f.id)) out.add(f)
            }
            // Next == "-1" 表示末页；空串 / 数字表示还有下一页
            next = cursor ?: break
            pages++
        }
        out
    }

    override suspend fun resolveStream(file: CloudFile): StreamSource = withContext(Dispatchers.IO) {
        ensureToken()
        if (file.isDir) error("123 云盘：文件夹无法解析为播放链接")
        val meta = fileMeta[file.id]
            ?: error("缺少文件下载信息，请重新打开该目录后再试")
        if (meta.etag.isBlank() || meta.s3KeyFlag.isBlank()) {
            error("缺少文件下载信息（Etag / S3KeyFlag），请重新打开该目录后再试")
        }

        val body = JSONObject().apply {
            put("driveId", 0)
            put("etag", meta.etag)
            put("fileId", file.id.toLongOrNull() ?: 0L)
            put("s3keyFlag", meta.s3KeyFlag)
            put("type", 0)
            put("fileName", file.name)
            put("size", meta.size)
        }.toString()

        val json = execute(
            authedPost(DOWNLOAD_INFO_URL, DOWNLOAD_INFO_PATH, body)
        )
        val data = json.optJSONObject("data")
        val raw = data?.optString("DownloadUrl").orEmpty()
        val decoded = decodeDownloadUrl(raw)
        if (decoded.isBlank()) error("123 云盘未返回下载地址，请稍后重试")
        // 直链带 auto_redirect=0 时 CDN 返回 JSON 中转页，需逐跳跟随到真实文件地址
        val downloadUrl = followRedirect(decoded)
        StreamSource(
            url = downloadUrl,
            headers = mapOf("Referer" to REFERER, "User-Agent" to UA),
        )
    }

    /**
     * 逐跳跟随 123 CDN 的 redirect_url（最多 5 跳）。
     * 仅当响应体很小（≤8KB 且形如 JSON）时才视为中转页，大响应直接视为文件流。
     */
    private fun followRedirect(initialUrl: String): String {
        var url = initialUrl
        repeat(MAX_REDIRECT_HOPS) {
            val nextUrl = probeJsonRedirect(url) ?: return url
            url = nextUrl
        }
        return url
    }

    private fun probeJsonRedirect(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("Referer", REFERER)
            .header("User-Agent", UA)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            if (len >= 0 && len <= 8192) {
                val body = resp.body?.string().orEmpty()
                val parsed = runCatching { JSONObject(body) }.getOrNull()
                parsed?.optJSONObject("data")?.optString("redirect_url")
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }.getOrNull()

    override fun close() {
        // client 由 Factory 管理，这里只清理本地缓存
        fileMeta.clear()
    }

    // ---------- 内部实现 ----------

    private fun ensureToken() {
        if (token.isBlank()) error("未配置 123 云盘登录 token，请先登录")
    }

    /** 拉取一页目录：返回 (文件列表, 下一页游标 or null=末页)。 */
    private fun fetchPage(parentFileId: String, next: String): Pair<List<CloudFile>, String?>? {
        val url = listUrl(parentFileId, next)
        val json = execute(authedGet(url.toString(), LIST_PATH))
        val data = json.optJSONObject("data") ?: return null
        val arr = data.optJSONArray("InfoList") ?: return emptyList<CloudFile>() to null

        val files = ArrayList<CloudFile>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fileId = o.optString("FileId")
            if (fileId.isBlank()) continue
            val name = o.optString("FileName")
            val type = o.optInt("Type", 0)
            val size = o.optLong("Size", 0L)

            fileMeta[fileId] = Pan123Meta(
                s3KeyFlag = o.optString("S3KeyFlag"),
                etag = o.optString("Etag"),
                size = size,
            )

            files.add(
                CloudFile(
                    id = fileId,
                    name = name,
                    path = "/$name",
                    isDir = type == 1, // 0=文件，1=文件夹
                    size = size,
                    lastModified = parseTimestamp(o.optString("UpdateAt")),
                    mimeType = null,
                )
            )
        }

        // Next == "-1" 表示末页；空串 / 数字表示还有下一页
        val cursor = data.optString("Next").takeIf { it != "-1" }
        return files to cursor
    }

    private fun listUrl(parentFileId: String, next: String): HttpUrl =
        FILE_LIST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("driveId", "0")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("next", next)
            .addQueryParameter("orderBy", "update_time")
            .addQueryParameter("orderDirection", "desc")
            .addQueryParameter("parentFileId", parentFileId)
            .addQueryParameter("trashed", "false")
            .addQueryParameter("SearchData", "")
            .addQueryParameter("Page", "1")
            .addQueryParameter("OnlyLookAbnormalFile", "0")
            .addQueryParameter("event", "homeListFile")
            .addQueryParameter("operateType", "1")
            .addQueryParameter("inDirectSpace", "false")
            .build()

    /** 签名头 + GET。path 形如 /b/api/file/list/new（含 /b，不含 host 与 query）。 */
    private fun authedGet(url: String, path: String): Request {
        val (authKey, authValue) = makeSign(path)
        return Request.Builder()
            .url(url)
            .header("platform", PLATFORM_WEB)
            .header("app-version", APP_VERSION_WEB)
            .header("authorization", "Bearer $token")
            .header("loginuuid", loginUuid)
            .header("auth-key", authKey)
            .header("auth-value", authValue)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .get()
            .build()
    }

    /** 签名头 + POST。 */
    private fun authedPost(url: String, path: String, body: String): Request {
        val (authKey, authValue) = makeSign(path)
        return Request.Builder()
            .url(url)
            .header("platform", PLATFORM_WEB)
            .header("app-version", APP_VERSION_WEB)
            .header("authorization", "Bearer $token")
            .header("loginuuid", loginUuid)
            .header("auth-key", authKey)
            .header("auth-value", authValue)
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
    }

    /**
     * 生成签名头对（auth-key, auth-value）。
     * @param path 含 /b 前缀的路径
     */
    private fun makeSign(path: String, ts: Long = System.currentTimeMillis() / 1000): Pair<String, String> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = (ts + SIGN_OFFSET_SECONDS) * 1000L
        }
        val minute = String.format(
            "%04d%02d%02d%02d%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
        )
        val substituted = minute.map { SIGN_TABLE[it - '0'] }.joinToString("")
        val authKey = crc32Hex(substituted)

        val random = ThreadLocalRandom.current().nextInt(0, 10_000_000)
        val data = "$ts|$random|$path|$SIGN_OS|$SIGN_VER|$authKey"
        return authKey to "$ts-$random-${crc32Hex(data)}"
    }

    private fun crc32Hex(s: String): String {
        val crc = CRC32()
        crc.update(s.toByteArray(Charsets.UTF_8))
        return java.lang.Long.toHexString(crc.value and 0xFFFFFFFFL)
    }

    /** 执行请求并校验业务码，失败时抛出可读异常。 */
    private fun execute(req: Request): JSONObject {
        client.newCall(req).execute().use { resp: Response ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 401) error(ERR_AUTH)
                error("123 云盘请求失败：HTTP ${resp.code}")
            }
            val json = runCatching { JSONObject(body) }
                .getOrElse { error("123 云盘返回非 JSON 数据") }

            val code = json.optInt("code", 0)
            if (code != 0) {
                val message = json.optString("message").ifBlank { "code=$code" }
                if (code == 401 || code == 5111 || message.contains("登录") ||
                    message.contains("token", ignoreCase = true)
                ) {
                    error(ERR_AUTH)
                }
                error("123 云盘请求失败：$message")
            }
            return json
        }
    }

    /**
     * 直链兼容两种形态：
     * 1) 整段 base64（不含协议头）→ 解码后须以 http 开头；
     * 2) download-v2?params=<base64> → 解码 params。
     * 中转页 URL 同样以 http 开头，因此不能用 startsWith("http") 短路。
     */
    private fun decodeDownloadUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (!trimmed.contains("://")) {
            return runCatching {
                String(Base64.decode(normalizeBase64(trimmed), Base64.DEFAULT), Charsets.UTF_8)
                    .takeIf { it.startsWith("http", ignoreCase = true) }
            }.getOrNull().orEmpty()
        }
        val idx = trimmed.indexOf("params=")
        if (idx < 0) return trimmed
        val payload = trimmed.substring(idx + "params=".length).substringBefore("&")
        return runCatching {
            String(Base64.decode(normalizeBase64(payload), Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull().orEmpty().ifBlank { trimmed }
    }

    private fun normalizeBase64(s: String): String = s.replace('-', '+').replace('_', '/')

    /** UpdateAt 可能是毫秒时间戳或 ISO 时间字符串，容错解析。 */
    private fun parseTimestamp(raw: String): Long {
        if (raw.isBlank()) return 0L
        raw.trim().toLongOrNull()?.let { return it }
        return runCatching {
            OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(raw.trim().replace(' ', 'T'))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    /** 列目录时缓存的下载参数。 */
    private data class Pan123Meta(
        val s3KeyFlag: String,
        val etag: String,
        val size: Long,
    )

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
        const val MAX_REDIRECT_HOPS = 5
        const val FILE_LIST_URL = "https://yun.123pan.cn/b/api/file/list/new"
        const val LIST_PATH = "/b/api/file/list/new"
        const val DOWNLOAD_INFO_URL = "https://yun.123pan.cn/api/file/download_info"
        const val DOWNLOAD_INFO_PATH = "/api/file/download_info"
        const val REFERER = "https://yun.123pan.cn/"
        const val PLATFORM_WEB = "web"
        const val APP_VERSION_WEB = "3"
        const val SIGN_OFFSET_SECONDS = 57_600L
        const val SIGN_TABLE = "adefghlmyijnopkqrstubcvwsz"
        const val SIGN_OS = "web"
        const val SIGN_VER = "3"
        const val ERR_AUTH = "123 云盘登录已失效，请重新登录"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
        val JSON_MEDIA = "application/json;charset=UTF-8".toMediaType()
    }
}
