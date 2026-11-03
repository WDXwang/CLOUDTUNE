package com.cloudtune.player.data.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
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

    private const val TAG = "BuiltinSources"

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
        Log.d(TAG, "loadBoard boardId=$boardId")
        val result = if (boardId.isNullOrBlank()) loadBoardDir() else loadBoardSongs(boardId)
        Log.d(TAG, "loadBoard result: boards=${result.boards.size}, songs=${result.songs.size}")
        result
    }.onFailure { Log.e(TAG, "loadBoard failed", it) }

    /** 搜索歌曲 */
    suspend fun search(keyword: String): Result<SourcePayload> = runCatching {
        val url = "https://music.163.com/api/search/get?s=${java.net.URLEncoder.encode(keyword, "UTF-8")}&type=1&limit=30"
        val body = httpGet(url)
        Log.d(TAG, "search body len=${body.length}")
        val root = json.parseToJsonElement(body).jsonObject
        val result = root["result"]?.jsonObject
        val songsArr = result?.get("songs")
        val songs = songsArr?.let { parseSongs(it) }.orEmpty()
        Log.d(TAG, "search songs=${songs.size}")
        SourcePayload(songs = songs)
    }.onFailure { Log.e(TAG, "search failed", it) }

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
        Log.d(TAG, "loadBoardDir body len=${body.length}")
        val root = json.parseToJsonElement(body).jsonObject
        val listEl = root["list"]
        Log.d(TAG, "top-level keys=${root.keys.joinToString(",")}")
        val boards = listEl?.let { parseBoards(it) }.orEmpty()
        Log.d(TAG, "boards count=${boards.size}")
        return SourcePayload(boards = boards)
    }

    /** 榜单歌曲：api/playlist/detail?id=XXX */
    private suspend fun loadBoardSongs(boardId: String): SourcePayload {
        val body = httpGet("https://music.163.com/api/playlist/detail?id=$boardId")
        Log.d(TAG, "loadBoardSongs body len=${body.length}")
        val root = json.parseToJsonElement(body).jsonObject
        val result = root["result"]?.jsonObject ?: run {
            Log.w(TAG, "no result field, top keys=${root.keys.joinToString(",")}")
            return SourcePayload()
        }
        Log.d(TAG, "result keys=${result.keys.joinToString(",")}")
        // 兼容两种结构：result.tracks（热门榜单）和 result.playlist.tracks（普通歌单）
        val tracksEl: JsonElement? = when {
            result.containsKey("tracks") -> result["tracks"]
            result.containsKey("playlist") -> result["playlist"]?.jsonObject?.get("tracks")
            else -> null
        }
        if (tracksEl == null) {
            Log.w(TAG, "no tracks field found in result")
            return SourcePayload()
        }
        val tracks = parseSongs(tracksEl)
        Log.d(TAG, "tracks parsed=${tracks.size}")
        return SourcePayload(songs = tracks)
    }

    private fun parseBoards(element: JsonElement): List<SourceBoard> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "榜单"
            SourceBoard(id = id, name = name)
        }
    }

    /** 从 JsonElement（应该是 JsonArray）解析歌曲列表，内部吞异常避免整批丢失 */
    private fun parseSongs(element: JsonElement): List<SourceSong> {
        val arr = element as? JsonArray ?: run {
            Log.w(TAG, "parseSongs expected JsonArray, got ${element::class.simpleName}")
            return emptyList()
        }
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            runCatching {
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val singer = parseArtists(obj)
                val album = parseAlbum(obj)
                SourceSong(
                    id = id,
                    name = name,
                    artist = singer,
                    album = album,
                    sourceKey = "netease",
                )
            }.getOrNull()
        }
    }

    private fun parseAlbum(track: JsonObject): String {
        // 网易云 tracks[].album 是对象 { name, id, ... }， tracks[].al 也可能是对象
        val obj = (track["al"] as? JsonObject) ?: (track["album"] as? JsonObject)
        if (obj != null) {
            val name = obj["name"]?.jsonPrimitive?.contentOrNull
            if (!name.isNullOrBlank()) return name
        }
        // fallback: album 可能是字符串（极少数情况）
        val strName = track["album"]?.jsonPrimitive?.contentOrNull
        return strName ?: ""
    }

    private fun parseArtists(track: JsonObject): String {
        // tracks[].artists[] 是数组，每个元素是 { name, id, ... } 对象
        val artistsKey = if (track.containsKey("ar")) "ar" else "artists"
        val artArr = track[artistsKey] as? JsonArray ?: return ""
        return artArr.mapNotNull { a ->
            when (a) {
                is JsonObject -> a["name"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> a.contentOrNull
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("/")
    }
}
