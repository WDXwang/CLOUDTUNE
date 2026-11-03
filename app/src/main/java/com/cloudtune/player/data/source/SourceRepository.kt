package com.cloudtune.player.data.source

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 一次音源调用的结果：可能返回榜单列表，也可能直接返回歌曲列表。 */
data class SourcePayload(
    val boards: List<SourceBoard> = emptyList(),
    val songs: List<SourceSong> = emptyList(),
)

/**
 * 第三方音源仓库：负责脚本导入、榜单拉取与播放地址解析。
 *
 * 榜单协议（扩展洛雪自定义源协议）：
 * - `leaderboard` + `info={}` → 返回 `{ boards: [{id,name}] }`
 * - `leaderboard` + `info={boardId}` → 返回 `{ songs: [...] }`
 * 若脚本未实现 `leaderboard`，会向界面返回「不支持」错误。
 */
class SourceRepository(context: Context) {

    private val store = SourceStore(context)
    private val engine = LxScriptEngine()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _sources = MutableStateFlow<List<MusicSourceInfo>>(emptyList())
    val sources: StateFlow<List<MusicSourceInfo>> = _sources.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            _sources.value = store.list()
        }
    }

    /** 确保内置官方源已导入（应用首次启动时调用；已导入的旧版本会替换为新版本）。 */
    suspend fun ensureBuiltinSources(context: Context) {
        val assetFiles = listOf(
            "sources/netease_hot.js" to "网易云热歌榜",
        )
        for ((asset, builtinName) in assetFiles) {
            runCatching {
                val scriptText = context.assets.open(asset)
                    .bufferedReader()
                    .use { it.readText() }
                // 删除已有同名源（允许脚本升级）
                _sources.value.firstOrNull { it.name == builtinName }?.let { existing ->
                    store.delete(existing.id)
                }
                import(scriptText)
            }
        }
    }

    suspend fun refresh() {
        _sources.value = store.list()
    }

    /** 导入脚本：先执行一次拿到元信息，再落盘。 */
    suspend fun import(scriptText: String): Result<MusicSourceInfo> = runCatching {
        require(scriptText.isNotBlank()) { "脚本内容为空" }
        val meta = engine.inspect(scriptText).getOrThrow()
        store.save(
            scriptText,
            MusicSourceInfo(
                id = "",
                name = meta.name,
                author = meta.author,
                version = meta.version,
                sources = meta.sources,
                actions = meta.actions,
                sourceActions = meta.sourceActions,
            ),
        ).also { _sources.value = store.list() }
    }

    suspend fun delete(id: String) {
        store.delete(id)
        _sources.value = store.list()
    }

    /** 拉取榜单：boardId 为空时取榜单目录，否则取该榜单歌曲。 */
    suspend fun loadBoard(info: MusicSourceInfo, boardId: String?): Result<SourcePayload> = runCatching {
        // 内置源直接走原生实现，绕开 JS 引擎
        if (BuiltinSources.isBuiltin(info.name)) {
            BuiltinSources.loadBoard(boardId).getOrThrow()
        } else {
            val script = store.script(info.id) ?: error("音源脚本文件不存在，请重新导入")
            val sourceKey = info.sources.firstOrNull().orEmpty()
            val infoJson = if (boardId.isNullOrBlank()) "{}" else buildString {
                append("{\"boardId\":"); append(quoted(boardId))
                append(",\"id\":"); append(quoted(boardId))
                append(",\"board\":"); append(quoted(boardId))
                append("}")
            }
            val raw = engine.call(script, ACTION_LEADERBOARD, sourceKey, infoJson).getOrThrow()
            parsePayload(raw)
        }
    }

    /** 用音源脚本搜索歌曲（洛雪扩展 action：`musicSearch` / `search`）。 */
    suspend fun search(info: MusicSourceInfo, keyword: String): Result<SourcePayload> = runCatching {
        if (BuiltinSources.isBuiltin(info.name)) {
            BuiltinSources.search(keyword).getOrThrow()
        } else {
            val script = store.script(info.id) ?: error("音源脚本文件不存在，请重新导入")
            val action = when {
                info.supports(ACTION_MUSIC_SEARCH) -> ACTION_MUSIC_SEARCH
                info.supports(ACTION_SEARCH) -> ACTION_SEARCH
                else -> error("该音源未提供搜索接口（仅支持解析播放地址），无法搜索歌曲")
            }
            val sourceKey = info.sourceFor(action)
            val infoJson = buildString {
                append("{\"keyword\":").append(quoted(keyword))
                append(",\"text\":").append(quoted(keyword))
                append(",\"page\":1,\"limit\":30}")
            }
            val raw = engine.call(script, action, sourceKey, infoJson).getOrThrow()
            parsePayload(raw)
        }
    }

    /** 解析某首歌的播放直链。 */
    suspend fun resolveUrl(info: MusicSourceInfo, song: SourceSong): Result<String> = runCatching {
        if (BuiltinSources.isBuiltin(info.name)) {
            BuiltinSources.resolveUrl(song.id)
        } else {
            val script = store.script(info.id) ?: error("音源脚本文件不存在，请重新导入")
            val sourceKey = song.sourceKey.ifBlank { info.sources.firstOrNull().orEmpty() }
            val infoJson = buildString {
                append("{\"musicInfo\":")
                append(song.raw.ifBlank {
                    buildString {
                        append("{\"songmid\":").append(quoted(song.id))
                        append(",\"name\":").append(quoted(song.name))
                        append(",\"singer\":").append(quoted(song.artist))
                        append("}")
                    }
                })
                append(",\"type\":\"128k\"}")
            }
            val raw = engine.call(script, ACTION_MUSIC_URL, sourceKey, infoJson).getOrThrow()
            val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            val url = when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element.str("url", "musicUrl", "src", "playUrl", "link")
                else -> null
            }
            url?.takeIf { it.isNotBlank() } ?: error("音源未返回可播放地址")
        }
    }

    // ---------- JSON 解析 ----------

    private fun parsePayload(raw: String): SourcePayload {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return SourcePayload()
        return when (root) {
            is JsonArray -> {
                val firstObj = root.firstOrNull() as? JsonObject
                if (firstObj != null && looksLikeSong(firstObj)) {
                    SourcePayload(songs = root.mapNotNull { (it as? JsonObject)?.let(::parseSong) })
                } else {
                    SourcePayload(boards = root.mapNotNull { (it as? JsonObject)?.let(::parseBoard) })
                }
            }
            is JsonObject -> {
                val boardArr = root.arrayOf("boards", "boardList")
                val songArr = root.arrayOf("songs", "tracks", "songList")
                    ?: root.arrayOf("data", "list", "result")
                val boards = boardArr?.mapNotNull { (it as? JsonObject)?.let(::parseBoard) }.orEmpty()
                val songs = songArr?.mapNotNull { (it as? JsonObject)?.let(::parseSong) }.orEmpty()
                if (boards.isEmpty() && songs.isEmpty()) {
                    // 兼容 { result: { list: [...] } } / { data: { ... } } 这类一层包裹
                    val nested = listOf("result", "data").firstNotNullOfOrNull { key ->
                        root[key] as? JsonObject
                    }
                    if (nested != null) parsePayload(nested.toString())
                    else parseSong(root)?.let { SourcePayload(songs = listOf(it)) } ?: SourcePayload()
                } else {
                    SourcePayload(boards, songs)
                }
            }
            else -> SourcePayload()
        }
    }

    private fun looksLikeSong(o: JsonObject): Boolean =
        o.containsKey("singer") || o.containsKey("artist") || o.containsKey("songmid") ||
            o.containsKey("songId") || o.containsKey("songmid") || o.containsKey("singerName")

    private fun parseBoard(o: JsonObject): SourceBoard? {
        val id = o.str("id", "boardId", "key", "topId", "code")
        val name = o.str("name", "title", "boardName")
        if (id.isBlank() && name.isBlank()) return null
        val songs = o.arrayOf("songs", "list", "tracks", "data")
            ?.mapNotNull { (it as? JsonObject)?.let(::parseSong) }
            .orEmpty()
        return SourceBoard(id = id.ifBlank { name }, name = name.ifBlank { id }, songs = songs)
    }

    private fun parseSong(o: JsonObject): SourceSong? {
        val name = o.str("name", "title", "songname", "songName", "song")
        if (name.isBlank()) return null
        val id = o.str("id", "songId", "songmid", "songMid", "rid", "mid", "hash", "songid")
        val artist = artistOf(o)
        val durRaw = o.num("interval", "duration", "dt", "time")
        val durationMs = if (durRaw in 1..9999) durRaw * 1000 else durRaw
        return SourceSong(
            id = id.ifBlank { "$name|$artist" },
            name = name,
            artist = artist,
            album = o.str("album", "albumName", "alname", "albumTitle"),
            durationMs = durationMs,
            coverUrl = o.str("pic", "cover", "coverUrl", "img", "albumPic", "picUrl").ifBlank { null },
            sourceKey = o.str("source", "sourceKey"),
            raw = o.toString(),
        )
    }

    private fun artistOf(o: JsonObject): String {
        for (key in listOf("artist", "singer", "singers", "artists", "author", "singerName")) {
            when (val value = o[key]) {
                is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                is JsonArray -> {
                    val names = value.mapNotNull { item ->
                        when (item) {
                            is JsonPrimitive -> item.contentOrNull
                            is JsonObject -> item.str("name", "artist", "title")
                            else -> null
                        }
                    }.filter { it.isNotBlank() }
                    if (names.isNotEmpty()) return names.joinToString("/")
                }
                is JsonObject -> value.str("name", "title").takeIf { it.isNotBlank() }?.let { return it }
                else -> null
            }
        }
        return ""
    }

    private fun JsonObject.str(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun JsonObject.num(vararg keys: String): Long =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        }?.toLong() ?: 0L

    private fun JsonObject.arrayOf(vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonArray)?.takeIf { it.isNotEmpty() }
        }

    private fun quoted(value: String): String =
        Json.encodeToString(String.serializer(), value)

    private companion object {
        const val ACTION_LEADERBOARD = "leaderboard"
        const val ACTION_MUSIC_URL = "musicUrl"
        const val ACTION_MUSIC_SEARCH = "musicSearch"
        const val ACTION_SEARCH = "search"
    }
}
