package com.cloudtune.player.data.pan123

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * ============================================================
 *  实验性 · 非官方接口，风险自担
 * ------------------------------------------------------------
 *  123 云盘数据源（Bearer token 方式，token 由 [Pan123Auth.login] 换取）。
 *
 *  - 列目录：GET /api/file/list/new（游标分页，data.Next 为下一页，-1 结束）
 *  - 取直链：GET /api/file/download_info（需列目录时缓存的 Etag / S3KeyFlag）
 * ============================================================
 */
class Pan123CloudDataSource(
    private val config: ServerConfig,
    private val client: OkHttpClient,
) : CloudDataSource {

    private val token: String = config.pan123Token.trim()

    /** 根目录 fileId，未填写时回落到 "0"。 */
    private val rootFolderId: String = config.pan123RootFolderId.ifBlank { "0" }

    /** 列目录时记录的下载参数，键为 fileId（直链接口需要）。 */
    private val fileMeta = ConcurrentHashMap<String, Pan123Meta>()

    override suspend fun test(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            ensureToken()
            val req = Request.Builder()
                .url(listUrl(rootFolderId, "0"))
                .authed()
                .get()
                .build()
            fetchJson(req)
            Unit
        }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> = withContext(Dispatchers.IO) {
        ensureToken()
        // dir == null 表示配置中的根目录；否则 dir.id 即 123 的 fileId
        val parentId = dir?.id ?: rootFolderId
        val out = ArrayList<CloudFile>()

        var next = "0"
        var pages = 0
        while (pages < MAX_PAGES) {
            val json = fetchJson(
                Request.Builder().url(listUrl(parentId, next)).authed().get().build()
            )
            val data = json.optJSONObject("data") ?: break
            val info = data.optJSONArray("InfoList")

            if (info != null) {
                for (i in 0 until info.length()) {
                    val o = info.optJSONObject(i) ?: continue
                    val fileId = o.optString("FileId")
                    if (fileId.isBlank()) continue
                    val name = o.optString("FileName")
                    val type = o.optString("Type", "0")
                    val size = o.optLong("Size", 0L)

                    fileMeta[fileId] = Pan123Meta(
                        etag = o.optString("Etag"),
                        s3KeyFlag = o.optString("S3KeyFlag"),
                        size = size,
                        category = o.optString("Category"),
                        type = type,
                    )

                    out.add(
                        CloudFile(
                            id = fileId,
                            name = name,
                            path = "/$name",
                            isDir = type == "1", // 0=文件，1=文件夹
                            size = size,
                            lastModified = parseTimestamp(o.optString("UpdateAt")),
                            mimeType = null,
                        )
                    )
                }
            }

            pages++
            val nextCursor = data.optString("Next").toLongOrNull() ?: -1L
            if (nextCursor <= 0) break // -1 表示没有下一页
            if ((info?.length() ?: 0) < PAGE_SIZE) break
            next = nextCursor.toString()
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

        val url = DOWNLOAD_INFO_URL.toHttpUrl().newBuilder()
            .addQueryParameter("driveId", "0")
            .addQueryParameter("etag", meta.etag)
            .addQueryParameter("fileId", file.id)
            .addQueryParameter("fileName", file.name)
            .addQueryParameter("s3keyflag", meta.s3KeyFlag)
            .addQueryParameter("size", meta.size.toString())
            .addQueryParameter("type", meta.type.ifBlank { meta.category })
            .build()

        val req = Request.Builder()
            .url(url)
            .authed()
            .header("Referer", REFERER)
            .get()
            .build()
        val json = fetchJson(req)
        val downloadUrl = json.optJSONObject("data")?.optString("DownloadUrl").orEmpty()
        if (downloadUrl.isBlank()) error("123 云盘未返回下载地址，请稍后重试")
        StreamSource(url = downloadUrl)
    }

    override fun close() {
        // client 由 Factory 管理，这里只清理本地缓存
        fileMeta.clear()
    }

    // ---------- 内部实现 ----------

    private fun ensureToken() {
        if (token.isBlank()) error("未配置 123 云盘登录 token，请先登录")
    }

    /** 统一附加 123 云盘认证头。 */
    private fun Request.Builder.authed(): Request.Builder = this
        .header("Authorization", "Bearer $token")
        .header("platform", "android")
        .header("app-version", "3")
        .header("Accept", "application/json")

    private fun listUrl(parentFileId: String, next: String): HttpUrl =
        "https://www.123pan.com/api/file/list/new".toHttpUrl().newBuilder()
            .addQueryParameter("driveId", "0")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("next", next)
            .addQueryParameter("orderBy", "file_name")
            .addQueryParameter("orderDirection", "asc")
            .addQueryParameter("parentFileId", parentFileId)
            .addQueryParameter("trashed", "false")
            .addQueryParameter("search", "")
            .build()

    /** 执行请求并校验业务码，失败时抛出可读异常。 */
    private fun fetchJson(req: Request): JSONObject {
        client.newCall(req).execute().use { resp ->
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
                if (code == 401 || message.contains("登录") || message.contains("token", ignoreCase = true)) {
                    error(ERR_AUTH)
                }
                error("123 云盘请求失败：$message")
            }
            return json
        }
    }

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
        val etag: String,
        val s3KeyFlag: String,
        val size: Long,
        val category: String,
        val type: String,
    )

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
        const val DOWNLOAD_INFO_URL = "https://www.123pan.com/api/file/download_info"
        const val REFERER = "https://www.123pan.com/"
        const val ERR_AUTH = "123 云盘登录已失效，请重新登录"
    }
}
