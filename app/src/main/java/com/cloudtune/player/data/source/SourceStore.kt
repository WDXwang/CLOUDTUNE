package com.cloudtune.player.data.source

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 音源脚本本地存储：脚本本体放在 `filesDir/music_sources/<id>.js`，
 * 元信息统一写在 `sources.json`。
 */
class SourceStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(MusicSourceInfo.serializer())
    private val mutex = Mutex()

    private val dir: File
        get() = File(context.filesDir, "music_sources").apply { if (!exists()) mkdirs() }

    private val metaFile: File
        get() = File(dir, "sources.json")

    suspend fun list(): List<MusicSourceInfo> = withContext(Dispatchers.IO) {
        mutex.withLock { readMeta() }
    }

    /** 保存脚本并返回带 id 的元信息（同 id 时覆盖）。 */
    suspend fun save(script: String, info: MusicSourceInfo): MusicSourceInfo = withContext(Dispatchers.IO) {
        mutex.withLock {
            val merged = info.copy(
                id = info.id.ifBlank { "src_${System.currentTimeMillis()}" },
                importedAt = System.currentTimeMillis(),
            )
            File(dir, "${merged.id}.js").writeText(script, Charsets.UTF_8)
            val all = readMeta().filterNot { it.id == merged.id } + merged
            writeMeta(all)
            merged
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { File(dir, "$id.js").delete() }
            writeMeta(readMeta().filterNot { it.id == id })
        }
    }

    suspend fun script(id: String): String? = withContext(Dispatchers.IO) {
        runCatching { File(dir, "$id.js").readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun readMeta(): List<MusicSourceInfo> {
        val raw = runCatching { metaFile.readText(Charsets.UTF_8) }.getOrNull()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun writeMeta(list: List<MusicSourceInfo>) {
        runCatching { metaFile.writeText(json.encodeToString(listSerializer, list), Charsets.UTF_8) }
    }
}
