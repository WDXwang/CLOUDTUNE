package com.cloudtune.player.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 内置官方音源（原生实现，绕开 JS 引擎以保证稳定）。
 * 目前内置：网易云热歌榜（排行榜目录、榜单歌曲、搜索、播放直链）。
 */
object BuiltinSources {

    /** 内置源的唯一标识（匹配 MusicSourceInfo.name） */
    const val NETEASE_HOT_NAME = "网易云热歌榜"

    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val REFERER = "https://music.163.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 是否由内置源原生处理 */
    fun isBuiltin(name: String): Boolean = name == NETEASE_HOT_NAME

    /** 拉榜单：boardId=null → 榜单目录；否则 → 该榜单歌曲 */
    suspend fun loadBoard(boardId: String?): Result<SourcePayload> = runCatching {
        if (boardId.isNullOrBlank()) loadBoardDir() else loadBoardSongs(boardId)
    }

    /** 搜索歌曲 */
    suspend fun search(keyword: String): Result<SourcePayload> = runCatching {
        val url = "https://music.163.com/api/search/get?s=${java.net.URLEncoder.encode(keyword, "UTF-8")}&type=1&limit=30"
        val body = httpGet(url)
        val root = json.parseToJsonElement(body).jsonObject
        val result = root["result"]?.jsonObject
        val songs = result?.get("songs")?.let { arr ->
            arr.toString().let { parseSongs(it) }
        }.orEmpty()
        SourcePayload(songs = songs)
    }

    /** 解析播放直链（返回可直接播放的 URL） */
    fun resolveUrl(songId: String): String {
        return "https://music.163.com/song/media/outer/url?id=$songId.mp3"
    }

    // ---------- 内部实现 ----------

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", REFERER)
            .build()
        client.newCall(request).execute().use { resp ->
            resp.body?.string() ?: ""
        }
    }

    /** 榜单目录：api/toplist → {list: [{id,name,...}]} */
    private suspend fun loadBoardDir(): SourcePayload {
        val body = httpGet("https://music.163.com/api/toplist")
        val root = json.parseToJsonElement(body).jsonObject
        val list = root["list"]?.let { arr -> arr.toString() }
        val boards = parseBoards(list)
        return SourcePayload(boards = boards)
    }

    /** 榜单歌曲：api/playlist/detail?id=XXX → {result: {playlist: {tracks: [...]}} 或 {result: {tracks: [...]}}} */
    private suspend fun loadBoardSongs(boardId: String): SourcePayload {
        val body = httpGet("https://music.163.com/api/playlist/detail?id=$boardId")
        val root = json.parseToJsonElement(body).jsonObject
        val result = root["result"]?.jsonObject ?: return SourcePayload()
        val tracksJson = when {
            result.containsKey("tracks") -> result["tracks"]!!.toString()
            result.containsKey("playlist") -> result["playlist"]?.jsonObject?.get("tracks")?.toString()
            else -> null
        } ?: return SourcePayload()
        return SourcePayload(songs = parseSongs(tracksJson))
    }

    private fun parseBoards(jsonArray: String?): List<SourceBoard> {
        if (jsonArray.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(jsonArray).let { element ->
                if (element is kotlinx.serialization.json.JsonArray) element else return@runCatching emptyList()
                element
            }
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "榜单"
                SourceBoard(id = id, name = name)
            }
        }.getOrDefault(emptyList())
    }

    private fun parseSongs(jsonArray: String): List<SourceSong> {
        if (jsonArray.isBlank()) return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(jsonArray).let { element ->
                if (element is kotlinx.serialization.json.JsonArray) element else return@runCatching emptyList()
                element
            }
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val singer = parseArtists(obj)
                val album = obj["al"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: obj["album"]?.jsonPrimitive?.contentOrNull ?: ""
                SourceSong(
                    id = id,
                    name = name,
                    artist = singer,
                    album = album,
                    sourceKey = "netease",
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseArtists(track: JsonObject): String {
        // tracks[].ar[] 或 tracks[].artists[]
        val artistsKey = if (track.containsKey("ar")) "ar" else "artists"
        val art = track[artistsKey] as? kotlinx.serialization.json.JsonArray ?: return ""
        return art.mapNotNull { a ->
            when (a) {
                is JsonObject -> a["name"]?.jsonPrimitive?.contentOrNull
                is kotlinx.serialization.json.JsonPrimitive -> a.contentOrNull
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("/")
    }
}
