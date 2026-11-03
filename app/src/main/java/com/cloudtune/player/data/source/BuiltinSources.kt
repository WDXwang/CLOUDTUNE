package com.cloudtune.player.data.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 内置官方音源（Kotlin 原生实现，绕开 JS 引擎以保证稳定）。
 *
 * 已内置：
 * - 网易云热歌榜（`netease`）：排行榜目录 / 榜单歌曲 / 搜索 / 播放直链
 * - 酷狗音乐榜（`kugou`）：27 个榜单 / 榜单歌曲 / 搜索 / 播放直链
 */
object BuiltinSources {

    private const val TAG = "BuiltinSources"

    /** 一个内置源的描述，用于写入音源元信息 */
    data class Builtin(
        val id: String,
        val name: String,
        val version: String,
        val sourceKeys: List<String>,
        val actions: List<String>,
    )

    const val NETEASE_HOT_NAME = "网易云热歌榜"
    const val KUGOU_RANK_NAME = "酷狗音乐榜"

    private const val ACTION_LEADERBOARD = "leaderboard"
    private const val ACTION_MUSIC_SEARCH = "musicSearch"
    private const val ACTION_MUSIC_URL = "musicUrl"

    private val NETEASE_ACTIONS = listOf(ACTION_LEADERBOARD, ACTION_MUSIC_SEARCH, ACTION_MUSIC_URL)
    private val KUGOU_ACTIONS = listOf(ACTION_LEADERBOARD, ACTION_MUSIC_SEARCH, ACTION_MUSIC_URL)

    /** 全部内置源 */
    val all: List<Builtin> = listOf(
        Builtin(
            id = "builtin_netease",
            name = NETEASE_HOT_NAME,
            version = "2.0.0",
            sourceKeys = listOf("netease"),
            actions = NETEASE_ACTIONS,
        ),
        Builtin(
            id = "builtin_kugou",
            name = KUGOU_RANK_NAME,
            version = "1.0.0",
            sourceKeys = listOf("kugou"),
            actions = KUGOU_ACTIONS,
        ),
    )

    /** 内置源没有 JS 脚本，落盘一个占位注释即可（调用时不会执行它） */
    const val PLACEHOLDER_SCRIPT = "// 内置音源：由 Kotlin 原生实现，无需脚本\n"

    /** 是否由内置源原生处理 */
    fun isBuiltin(name: String): Boolean = all.any { it.name == name }

    /** sourceKey → 内置源名称（用于把歌曲路由回正确的音源） */
    fun nameForSourceKey(sourceKey: String): String? = when (sourceKey) {
        "netease" -> NETEASE_HOT_NAME
        "kugou" -> KUGOU_RANK_NAME
        else -> null
    }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient = Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------- 对外入口 ----------

    /** 拉榜单：boardId=null → 榜单目录；否则 → 该榜单歌曲 */
    suspend fun loadBoard(sourceName: String, boardId: String?): Result<SourcePayload> = runCatching {
        when (sourceName) {
            NETEASE_HOT_NAME -> neteaseBoard(boardId)
            KUGOU_RANK_NAME -> kugouBoard(boardId)
            else -> error("未知内置音源：$sourceName")
        }
    }.onFailure { Log.e(TAG, "loadBoard($sourceName, $boardId) failed", it) }

    /** 搜索歌曲 */
    suspend fun search(sourceName: String, keyword: String): Result<SourcePayload> = runCatching {
        when (sourceName) {
            NETEASE_HOT_NAME -> neteaseSearch(keyword)
            KUGOU_RANK_NAME -> kugouSearch(keyword)
            else -> error("未知内置音源：$sourceName")
        }
    }.onFailure { Log.e(TAG, "search($sourceName, $keyword) failed", it) }

    /** 解析播放直链（songId：网易云是歌曲 id，酷狗是 hash） */
    suspend fun resolveUrl(sourceName: String, songId: String): Result<String> = runCatching {
        when (sourceName) {
            NETEASE_HOT_NAME -> "https://music.163.com/song/media/outer/url?id=$songId.mp3"
            KUGOU_RANK_NAME -> kugouPlayUrl(songId)
            else -> error("未知内置音源：$sourceName")
        }.takeIf { it.isNotBlank() } ?: error("音源未返回可播放地址")
    }.onFailure { Log.e(TAG, "resolveUrl($sourceName, $songId) failed", it) }

    // ---------- HTTP ----------

    private suspend fun httpGet(url: String, referer: String = ""): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (referer.isNotBlank()) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { resp -> resp.body?.string() ?: "" }
    }

    private suspend fun getJson(url: String, referer: String = ""): JsonObject? {
        val body = httpGet(url, referer)
        if (body.isBlank()) return null
        return runCatching { json.parseToJsonElement(body).jsonObject }
            .onFailure { Log.e(TAG, "json parse failed: ${body.take(120)}", it) }
            .getOrNull()
    }

    // ---------- 网易云 ----------

    private suspend fun neteaseBoard(boardId: String?): SourcePayload {
        if (boardId.isNullOrBlank()) {
            val root = getJson("https://music.163.com/api/toplist", "https://music.163.com/")
            val boards = parseBoards(root?.get("list"), sourceKey = "netease")
            Log.d(TAG, "netease boards=${boards.size}")
            return SourcePayload(boards = boards)
        }
        val root = getJson("https://music.163.com/api/playlist/detail?id=$boardId", "https://music.163.com/")
        val result = root?.get("result")?.jsonObject
        val tracksEl = when {
            result == null -> null
            result.containsKey("tracks") -> result["tracks"]
            result.containsKey("playlist") -> result["playlist"]?.jsonObject?.get("tracks")
            else -> null
        }
        val songs = parseNeteaseSongs(tracksEl)
        Log.d(TAG, "netease board $boardId songs=${songs.size}")
        return SourcePayload(songs = songs)
    }

    private suspend fun neteaseSearch(keyword: String): SourcePayload {
        val url = "https://music.163.com/api/search/get?s=${encodeUrl(keyword)}&type=1&limit=30"
        val root = getJson(url, "https://music.163.com/")
        val songs = parseNeteaseSongs(root?.get("result")?.jsonObject?.get("songs"))
        Log.d(TAG, "netease search '$keyword' songs=${songs.size}")
        return SourcePayload(songs = songs)
    }

    /** 网易云：track 里 artists/al 是对象数组；榜单新接口可能是 ar/al */
    private fun parseNeteaseSongs(element: JsonElement?): List<SourceSong> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            runCatching {
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                SourceSong(
                    id = id,
                    name = name,
                    artist = joinNames(obj["ar"] ?: obj["artists"]),
                    album = (obj["al"] ?: obj["album"])?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    durationMs = (obj["dt"] ?: obj["duration"])?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    sourceKey = "netease",
                )
            }.getOrNull()
        }
    }

    // ---------- 酷狗 ----------

    private suspend fun kugouBoard(boardId: String?): SourcePayload {
        if (boardId.isNullOrBlank()) {
            val root = getJson("http://mobilecdn.kugou.com/api/v3/rank/list?plat=0&withsong=0&page=1&pagesize=100")
            val boards = parseBoards(root?.get("data")?.jsonObject?.get("info"), sourceKey = "kugou")
            Log.d(TAG, "kugou boards=${boards.size}")
            return SourcePayload(boards = boards)
        }
        val root = getJson("http://mobilecdn.kugou.com/api/v3/rank/song?plat=0&rankid=$boardId&page=1&pagesize=30")
        val songs = parseKugouSongs(root?.get("data")?.jsonObject?.get("info"))
        Log.d(TAG, "kugou board $boardId songs=${songs.size}")
        return SourcePayload(songs = songs)
    }

    private suspend fun kugouSearch(keyword: String): SourcePayload {
        val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${encodeUrl(keyword)}&page=1&pagesize=30&showtype=1"
        val root = getJson(url)
        val songs = parseKugouSongs(root?.get("data")?.jsonObject?.get("info"))
        Log.d(TAG, "kugou search '$keyword' songs=${songs.size}")
        return SourcePayload(songs = songs)
    }

    /** 酷狗播放直链：hash → getSongInfo 接口直接返回 url */
    private suspend fun kugouPlayUrl(hash: String): String {
        val url = "http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=$hash"
        val root = getJson(url) ?: return ""
        val direct = root["url"]?.jsonPrimitive?.contentOrNull
        if (!direct.isNullOrBlank()) return direct
        // 个别歌曲主链为空时尝试备用链
        val backup = root["backup_url"] as? JsonArray
        return backup?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun parseKugouSongs(element: JsonElement?): List<SourceSong> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            runCatching {
                val hash = obj["hash"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val name = obj["songname"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val singer = obj["singername"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: joinNames(obj["authors"])
                    ?: obj["filename"]?.jsonPrimitive?.contentOrNull
                        ?.substringBefore(" - ", "").orEmpty()
                SourceSong(
                    id = hash,
                    name = name,
                    artist = singer,
                    album = obj["album_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    durationMs = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.times(1000) ?: 0L,
                    sourceKey = "kugou",
                )
            }.getOrNull()
        }
    }

    // ---------- 通用解析 ----------

    /** 榜单目录：兼容 `{id, name}` / `{rankid, rankname}` 两种字段 */
    private fun parseBoards(element: JsonElement?, sourceKey: String): List<SourceBoard> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] ?: obj["rankid"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = (obj["name"] ?: obj["rankname"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SourceBoard(id = id, name = name)
        }
    }

    /** 把 `[{name:...}]` 或 `[{author_name:...}]` 拼成歌手串 */
    private fun joinNames(element: JsonElement?): String {
        val arr = element as? JsonArray ?: return ""
        return arr.mapNotNull { a ->
            when (a) {
                is JsonObject -> (a["name"] ?: a["author_name"])?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> a.contentOrNull
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("/")
    }

    private fun encodeUrl(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
