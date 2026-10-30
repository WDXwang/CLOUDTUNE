package com.cloudtune.player.data.quark

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================
 *  实验性 · 非官方接口，风险自担
 * ------------------------------------------------------------
 *  夸克网盘数据源（粘贴浏览器 Cookie 方式）。
 *
 *  - 列目录：GET /1/clouddrive/file/sort（_page/_size 分页，metadata._total 为总数）
 *  - 取直链：POST /1/clouddrive/file/download（body {fids:[...]} → data[0].download_url）
 * ============================================================
 */
class QuarkCloudDataSource(
    private val config: ServerConfig,
    private val client: OkHttpClient,
) : CloudDataSource {

    private val cookie: String = config.quarkCookie.trim()

    /** 根目录 fid，未填写时回落到 "0"。 */
    private val rootFid: String = config.quarkRootFid.ifBlank { "0" }

    override suspend fun test(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            ensureCookie()
            fetchPage(rootFid, 1)
            Unit
        }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> = withContext(Dispatchers.IO) {
        ensureCookie()
        // dir == null 表示配置中的根目录；否则 dir.id 即夸克 fid
        val parentFid = dir?.id ?: rootFid
        val out = ArrayList<CloudFile>()

        var page = 1
        var total = 0
        while (page <= MAX_PAGES) {
            val json = fetchPage(parentFid, page)
            val data = json.optJSONObject("data")
            val list = data?.optJSONArray("list")
            if (list == null || list.length() == 0) break

            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val fid = o.optString("fid")
                if (fid.isBlank()) continue
                val name = o.optString("file_name")
                out.add(
                    CloudFile(
                        id = fid,
                        name = name,
                        path = "/$name",
                        isDir = o.optBoolean("dir", false),
                        size = o.optLong("size", 0L),
                        lastModified = o.optLong("updated_at", 0L), // 毫秒
                        mimeType = null,
                    )
                )
            }

            if (total <= 0) total = json.optJSONObject("metadata")?.optInt("_total", 0) ?: 0
            if (list.length() < PAGE_SIZE) break
            if (total > 0 && out.size >= total) break
            page++
        }
        out
    }

    override suspend fun resolveStream(file: CloudFile): StreamSource = withContext(Dispatchers.IO) {
        ensureCookie()
        if (file.isDir) error("夸克网盘：文件夹无法解析为播放链接")

        val body = JSONObject().put("fids", JSONArray().put(file.id)).toString()
        val req = Request.Builder()
            .url("$API_BASE/1/clouddrive/file/download?pr=ucpro&fr=pc")
            .quarkHeaders()
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val json = fetchJson(req, "获取下载地址")
        val first = json.optJSONArray("data")?.optJSONObject(0)
            ?: error("夸克网盘未返回下载地址，请稍后重试")
        val downloadUrl = first.optString("download_url")
        if (downloadUrl.isBlank()) error("夸克网盘未返回下载地址，请稍后重试")

        StreamSource(
            url = downloadUrl,
            headers = mapOf(
                "Cookie" to cookie,
                "Referer" to REFERER,
                "User-Agent" to UA,
            ),
        )
    }

    override fun close() {
        // client 由 Factory 管理，这里没有需要清理的本地缓存
    }

    // ---------- 内部实现 ----------

    private fun ensureCookie() {
        if (cookie.isBlank()) error("未配置夸克 Cookie，请先粘贴浏览器 Cookie")
    }

    private fun Request.Builder.quarkHeaders(): Request.Builder = this
        .header("Cookie", cookie)
        .header("Referer", REFERER)
        .header("User-Agent", UA)
        .header("Accept", "application/json, text/plain, */*")

    /** 请求某一页目录（page 从 1 开始），返回原始 JSON。 */
    private fun fetchPage(parentFid: String, page: Int): JSONObject {
        val url = "$API_BASE/1/clouddrive/file/sort".toHttpUrl().newBuilder()
            .addQueryParameter("pr", "ucpro")
            .addQueryParameter("fr", "pc")
            .addQueryParameter("uc_param_str", "")
            .addQueryParameter("pdir_fid", parentFid)
            .addQueryParameter("_page", page.toString())
            .addQueryParameter("_size", PAGE_SIZE.toString())
            .addQueryParameter("_fetch_total", "1")
            .addQueryParameter("_fetch_all", "0")
            .addQueryParameter("_sort", "file_type:asc,updated_at:desc")
            .build()
        val req = Request.Builder().url(url).quarkHeaders().get().build()
        return fetchJson(req, "列目录")
    }

    /** 执行请求并校验业务码（code == 0 或 status == 200 视为成功）。 */
    private fun fetchJson(req: Request, action: String): JSONObject {
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 401) error(ERR_AUTH)
                error("夸克网盘$action 失败：HTTP ${resp.code}")
            }
            val json = runCatching { JSONObject(body) }
                .getOrElse { error("夸克网盘$action 失败：返回非 JSON 数据") }

            val code = json.optInt("code", 0)
            val status = json.optInt("status", 0)
            if (code != 0 && status != 200) {
                val message = json.optString("message").ifBlank { "code=$code" }
                if (code == 31001 || code == 41004 || code == 401 || message.contains("登录")) {
                    error(ERR_AUTH)
                }
                error("夸克网盘$action 失败：$message")
            }
            return json
        }
    }

    private companion object {
        const val API_BASE = "https://drive-pc.quark.cn"
        const val PAGE_SIZE = 50
        const val MAX_PAGES = 5
        const val REFERER = "https://pan.quark.cn/"
        const val ERR_AUTH = "夸克 Cookie 已失效，请重新获取"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
