package com.cloudtune.player.data.webdav

import android.util.Xml
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * WebDAV 后端（默认，对接自建 AList 或任意标准 WebDAV 服务）。
 *
 * - 列目录使用 PROPFIND（Depth: 1），请求体为标准 propfind XML。
 * - href 可能是 URL 编码的，先解码再使用；[CloudFile.id] 存相对 davPath 的路径。
 */
class WebDavCloudDataSource(
    private val config: ServerConfig,
    private val client: OkHttpClient,
) : CloudDataSource {

    private val authHeader: String by lazy {
        Credentials.basic(config.username, config.password, Charsets.UTF_8)
    }

    override suspend fun test(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val url = collectionUrl(config.rootPath)
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Depth", "0") // 探活只需 Depth 0
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(MEDIA_XML))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("WebDAV 探测失败：HTTP ${resp.code}")
            }
        }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> = withContext(Dispatchers.IO) {
        // dir == null 表示配置中的根目录；否则用 dir.id（相对路径）
        val targetRel = dir?.id ?: config.rootPath
        val selfHref = normalizeRel(targetRel)
        val url = collectionUrl(targetRel)

        val req = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("Depth", "1")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(MEDIA_XML))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("列目录失败：HTTP ${resp.code}（$url）")
            val body = resp.body?.string().orEmpty()
            parseMultistatus(body, selfHref, parentRel = selfHref)
        }
    }

    override suspend fun resolveStream(file: CloudFile): StreamSource = withContext(Dispatchers.IO) {
        // 直链即 baseUrl + davPath + file.id，播放时带 Basic 凭据
        StreamSource(
            url = joinSegments(config.baseUrl, config.davPath, file.id),
            headers = mapOf("Authorization" to authHeader),
        )
    }

    override fun close() {
        // client 由 Factory 统一管理，这里不做处理。
    }

    // ---------- 内部实现 ----------

    /** 拼接集合 URL：baseUrl + davPath + 相对路径，末尾补 '/'（WebDAV 集合惯例）。 */
    private fun collectionUrl(relative: String): String {
        var base = joinSegments(config.baseUrl, config.davPath, relative)
        if (!base.endsWith("/")) base += "/"
        return base
    }

    /**
     * 解析 WebDAV multistatus XML。
     * @param selfHref 当前正在列的集合的相对路径（已去首尾斜杠），用于跳过代表集合自身的 response。
     * @param parentRel 父目录相对路径，写入每个条目的 [CloudFile.path]。
     */
    private fun parseMultistatus(
        xml: String,
        selfHref: String,
        parentRel: String,
    ): List<CloudFile> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        val out = mutableListOf<CloudFile>()
        var inResponse = false
        var inProp = false
        var href = ""
        var displayName = ""
        var contentLength = ""
        var lastModified = ""
        var isCollection = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "response" -> {
                            inResponse = true
                            inProp = false
                            href = ""; displayName = ""; contentLength = ""; lastModified = ""
                            isCollection = false
                        }
                        "prop" -> if (inResponse) inProp = true
                        "href" -> if (inResponse && !inProp) {
                            val t = parser.nextText()
                            if (t.isNotBlank()) href = t
                        }
                        "displayname" -> if (inResponse && inProp) {
                            val t = parser.nextText()
                            if (t.isNotBlank()) displayName = t
                        }
                        "getcontentlength" -> if (inResponse && inProp) {
                            val t = parser.nextText()
                            if (t.isNotBlank()) contentLength = t
                        }
                        "getlastmodified" -> if (inResponse && inProp) {
                            val t = parser.nextText()
                            if (t.isNotBlank()) lastModified = t
                        }
                        // resourcetype 下的 collection 子标签出现即表示这是目录
                        "collection" -> if (inResponse && inProp) isCollection = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "prop" -> inProp = false
                        "response" -> {
                            inResponse = false
                            val file = buildEntry(href, displayName, contentLength, lastModified, isCollection, selfHref, parentRel)
                            if (file != null) out.add(file)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun buildEntry(
        rawHref: String,
        displayName: String,
        contentLength: String,
        lastModified: String,
        isCollection: Boolean,
        selfHref: String,
        parentRel: String,
    ): CloudFile? {
        if (rawHref.isBlank()) return null
        // href 可能 URL 编码，先解码
        val decoded = runCatching { URLDecoder.decode(rawHref, "UTF-8") }.getOrDefault(rawHref)
        val rel = hrefToRelative(decoded)
        // 跳过代表目录自身的 response
        if (normalizeRel(rel) == selfHref) return null

        // 目录条目通常以 '/' 结尾，去掉尾斜杠作为 id
        val id = rel.trimEnd('/')
        val name = displayName.ifBlank { basename(id) }.ifBlank { basename(rawHref) }

        return CloudFile(
            id = if (id.startsWith("/")) id else "/$id",
            name = name,
            path = "/$parentRel",
            isDir = isCollection,
            size = contentLength.trim().toLongOrNull() ?: 0L,
            lastModified = parseHttpDate(lastModified),
            mimeType = if (isCollection) null else "application/octet-stream",
        )
    }

    /** href -> 相对 davPath 的路径。处理两种形式：绝对 URL（含 host）或服务器路径。 */
    private fun hrefToRelative(href: String): String {
        var h = href
        // 若为 http(s)://host/dav/...，截到 host 之后
        val schemeIdx = h.indexOf("://")
        if (schemeIdx >= 0) {
            val afterScheme = h.substring(schemeIdx + 3)
            val slashIdx = afterScheme.indexOf('/')
            if (slashIdx >= 0) h = afterScheme.substring(slashIdx)
        }
        // 去掉 davPath 前缀，得到相对路径
        val dav = config.davPath.trim('/')
        if (h.startsWith("/$dav", ignoreCase = true)) {
            h = h.substring(dav.length + 1)
        }
        if (!h.startsWith("/")) h = "/$h"
        return h
    }

    /** 规范化相对路径：去首尾斜杠，便于比较。 */
    private fun normalizeRel(rel: String): String = rel.trim('/')

    private fun basename(path: String): String {
        val p = path.trimEnd('/')
        val idx = p.lastIndexOf('/')
        return if (idx >= 0) p.substring(idx + 1) else p
    }

    private fun parseHttpDate(s: String): Long {
        if (s.isBlank()) return 0L
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }.parse(s)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private companion object {
        private val MEDIA_XML = "application/xml; charset=utf-8".toMediaType()

        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8" ?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:resourcetype/>
  </D:prop>
</D:propfind>"""

        /** 拼接路径段，避免重复斜杠；第一段（baseUrl）保留 host:port。 */
        fun joinSegments(vararg parts: String): String {
            val sb = StringBuilder()
            parts.forEachIndexed { i, part ->
                if (part.isBlank()) return@forEachIndexed
                if (i == 0) {
                    sb.append(part.trimEnd('/'))
                } else {
                    sb.append('/').append(part.trim('/'))
                }
            }
            return sb.toString()
        }
    }
}
