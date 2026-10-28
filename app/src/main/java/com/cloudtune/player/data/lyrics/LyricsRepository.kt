package com.cloudtune.player.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 从网易云音乐公开接口获取歌词。
 *
 * 流程：先用「标题 + 艺术家」搜索出歌曲 id，再按 id 拉取 LRC 文本。
 * 接口为公开 Web 接口（无需登录 / Cookie），但属第三方逆向接口，随时可能失效；
 * 歌曲若无歌词会返回空，由上层按"无歌词"处理。
 */
class LyricsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * 按标题与艺术家抓取 LRC 歌词文本；无歌词 / 失败返回 null。
     */
    suspend fun fetchLyrics(title: String, artist: String?): String? = withContext(Dispatchers.IO) {
        val query = listOf(title, artist).filter { !it.isNullOrBlank() }.joinToString(" ").trim()
        if (query.isBlank()) return@withContext null
        val songId = searchSong(query) ?: return@withContext null
        fetchLrc(songId)
    }

    /** 网易云搜索，返回命中第一首歌的 id（歌曲、无结果返回 null）。 */
    private fun searchSong(query: String): Long? {
        val url = "https://music.163.com/api/search/get"
            .toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "1")
            .addQueryParameter("offset", "0")
            .build()
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://music.163.com/")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val result = json.optJSONObject("result") ?: return null
            val songs = result.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null
            val id = songs.getJSONObject(0).optLong("id", -1L)
            return if (id > 0) id else null
        }
    }

    /** 按歌曲 id 拉取 LRC 歌词文本；无歌词返回 null。 */
    private fun fetchLrc(songId: Long): String? {
        val url = "https://music.163.com/api/song/lyric"
            .toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("lv", "-1")
            .addQueryParameter("kv", "-1")
            .addQueryParameter("tv", "-1")
            .build()
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://music.163.com/")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val lrc = json.optJSONObject("lrc") ?: return null
            val lyric = lrc.optString("lyric", "")
            return lyric.ifBlank { null }
        }
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
