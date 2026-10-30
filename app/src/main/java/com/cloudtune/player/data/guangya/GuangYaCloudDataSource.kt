package com.cloudtune.player.data.guangya

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ThreadLocalRandom

/**
 * ============================================================
 *  实验性 · 非官方接口，风险自担
 * ------------------------------------------------------------
 *  光鸭云盘数据源（access token 由 [GuangYaAuth.login] 换取）。
 *
 *  - 列目录：POST https://api.guangyapan.com/userres/v1/file/get_file_list
 *  - 取直链：POST https://api.guangyapan.com/userres/v1/get_res_download_url
 *  - 业务接口必须带 did / dt / traceparent 头；token 过期时用 refresh token 续期
 * ============================================================
 */
class GuangYaCloudDataSource(
    private val config: ServerConfig,
    private val client: OkHttpClient,
) : CloudDataSource {

    private var accessToken: String = config.guangYaAccessToken.trim()
    private var refreshToken: String = config.guangYaRefreshToken.trim()
    private val device: GuangYaAuth.Device =
        GuangYaAuth.deviceOf(config.guangYaDeviceId, config.guangYaDeviceSign)

    /** 根目录 id，未填写时为空串（光鸭根目录就是空串）。 */
    private val rootFolderId: String = config.guangYaRootFolderId.trim()

    override suspend fun test(): Result<Unit> = runCatching {
        list(null).let { Unit }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> = withContext(Dispatchers.IO) {
        val parentId = dir?.id ?: rootFolderId
        val out = ArrayList<CloudFile>()

        var page = 0
        var total = 0L
        while (page < MAX_PAGES) {
            val body = JSONObject().apply {
                put("parentId", parentId)
                put("pageSize", PAGE_SIZE)
                put("orderBy", 0)
                put("sortType", 0)
                put("page", page)
            }.toString()
            val data = call(FILE_LIST, body, "获取文件列表失败")
            val arr = listArray(data) ?: break
            if (arr.length() == 0) break

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val fileId = firstNonBlank(
                    o.optString("fileId"), o.optString("file_id"), o.optString("id"),
                    o.optString("resId"), o.optString("resourceId"),
                )
                if (fileId.isBlank()) continue
                val name = firstNonBlank(
                    o.optString("fileName"), o.optString("name"), o.optString("filename"),
                    o.optString("dirName"), o.optString("title"),
                )
                val size = firstNonBlank(
                    o.optString("fileSize"), o.optString("size"), o.optString("file_size"),
                ).toLongOrNull() ?: 0L

                out.add(
                    CloudFile(
                        id = fileId,
                        name = name,
                        path = "/$name",
                        isDir = isDir(o, name),
                        size = size,
                        lastModified = 0L,
                        mimeType = null,
                    )
                )
            }

            total = data.optString("total").toLongOrNull() ?: data.optLong("total", 0L)
            if (total > 0 && out.size >= total) break
            if (arr.length() < PAGE_SIZE) break
            page++
        }
        out.distinctBy { it.id }
    }

    override suspend fun resolveStream(file: CloudFile): StreamSource = withContext(Dispatchers.IO) {
        if (file.isDir) error("光鸭云盘：文件夹无法解析为播放链接")
        val body = JSONObject().put("fileId", file.id).toString()
        val data = call(RES_DOWNLOAD, body, "获取下载地址失败")

        val url = firstNonBlank(
            data.optString("signedURL"), data.optString("signedUrl"),
            data.optString("downloadUrl"), data.optString("download_url"),
            data.optString("url"), data.optString("cdnUrl"),
        )
        if (url.isBlank()) error("光鸭未返回有效下载地址，请确认该文件的下载权限")
        StreamSource(
            url = url,
            headers = mapOf("Referer" to DOWNLOAD_REFERER, "User-Agent" to UA),
        )
    }

    override fun close() {
        // client 由 Factory 管理，这里无本地缓存需要清理
    }

    // ---------- 内部实现 ----------

    /** 业务 POST，返回 data 节点；遇到鉴权失败自动续期一次。 */
    private suspend fun call(url: String, body: String, fallback: String, retried: Boolean = false): JSONObject {
        val json = execute(post(url, body))
        if (isSuccess(json)) return json.optJSONObject("data") ?: JSONObject()

        val code = json.optString("code").ifBlank { json.optString("error") }
        val authFailed = json.optInt("status", 0) == 401 || code == "401" ||
            code == "unauthenticated" || code == "unauthorized" || code == "invalid_token"

        if (authFailed) {
            if (retried || !refreshAccessToken()) error(ERR_AUTH)
            return call(url, body, fallback, retried = true)
        }

        val serverMsg = firstNonBlank(json.optString("msg"), json.optString("message"), json.optString("error_description"))
        error("$fallback：${serverMsg.ifBlank { "code=$code" }}")
    }

    private suspend fun refreshAccessToken(): Boolean {
        if (refreshToken.isBlank()) return false
        val session = GuangYaAuth.refresh(refreshToken, client, device) ?: return false
        accessToken = session.accessToken
        if (session.refreshToken.isNotBlank()) refreshToken = session.refreshToken
        return true
    }

    private fun post(url: String, body: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json, text/plain, */*")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA)
        .header("Origin", WEB_SITE)
        .header("Referer", DOWNLOAD_REFERER)
        .header("dt", "4")
        .header("traceparent", newTraceparent())
        .header("did", device.deviceId)
        .header("Authorization", "Bearer $accessToken")
        .post(body.toRequestBody(JSON_MEDIA))
        .build()

    private fun execute(req: Request): JSONObject {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) error("光鸭接口返回为空（HTTP ${resp.code}）")
            return runCatching { JSONObject(text) }
                .getOrElse { error("光鸭接口返回非 JSON 数据（HTTP ${resp.code}）") }
        }
    }

    private fun isSuccess(json: JSONObject): Boolean {
        if (!json.optBoolean("success", true)) return false
        if (json.optString("error").isNotBlank()) return false
        val code = json.optString("code")
        return code.isBlank() || code == "0"
    }

    private fun listArray(data: JSONObject) =
        data.optJSONArray("list")
            ?: data.optJSONArray("items")
            ?: data.optJSONArray("files")
            ?: data.optJSONArray("records")
            ?: data.optJSONArray("content")

    private fun isDir(item: JSONObject, name: String): Boolean = when (item.optString("resType")) {
        "2" -> true
        "1" -> false
        else ->
            item.optBoolean("isDir") || item.optBoolean("isdir") || item.optBoolean("dir") ||
                item.optBoolean("isFolder") || item.optBoolean("folder") ||
                item.optString("dirName").isNotBlank() ||
                item.optString("type").lowercase() in setOf("folder", "dir", "directory") ||
                (item.optString("dirType") == "1" && !name.contains('.'))
    }

    private fun newTraceparent(): String {
        val rnd = ThreadLocalRandom.current()
        fun hex(n: Int) = buildString(n) { repeat(n) { append("0123456789abcdef"[rnd.nextInt(16)]) } }
        return "00-${hex(32)}-${hex(16)}-01"
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private companion object {
        const val API_BASE = "https://api.guangyapan.com"
        const val WEB_SITE = "https://www.guangyapan.com"
        const val DOWNLOAD_REFERER = "$WEB_SITE/"
        const val FILE_LIST = "$API_BASE/userres/v1/file/get_file_list"
        const val RES_DOWNLOAD = "$API_BASE/userres/v1/get_res_download_url"
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
        const val ERR_AUTH = "光鸭登录已失效，请重新登录"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val JSON_MEDIA = "application/json;charset=UTF-8".toMediaType()
    }
}
