package com.cloudtune.player.core.provider

import android.content.Context
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.data.local.LocalMusicScanner

/**
 * 本地音乐 Provider — 通过 Android MediaStore 扫描本机音乐文件。
 *
 * 能力：Search（按文件路径过滤）、Playback（content:// URI 直接播放）、LocalScan
 */
class LocalProvider(
    private val context: Context,
    private val repository: MusicRepository,
) : MusicProvider, SearchCapability, PlaybackCapability, LocalScanCapability {

    override val source = MusicSource.Local
    override val displayName = "本地音乐"

    override val capabilities = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.LocalScan,
    )

    private var cachedTracks: List<ProviderTrack>? = null

    // ─────────────── LocalScanCapability ───────────────

    override suspend fun scan(): List<ProviderTrack> {
        val files = repository.listLocalMusic()
        return files.map { file ->
            // 文件名通常就是 content://media/external/audio/media/xxx
            val displayName = file.name
            ProviderTrack(
                id = "local:${file.id}",
                title = displayName,
                source = MusicSource.Local,
                durationMs = 0L,
                origin = TrackOrigin.LocalOrigin(file.id),
            )
        }.also { cachedTracks = it }
    }

    // ─────────────── SearchCapability ───────────────

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): List<ProviderTrack> {
        val all = cachedTracks ?: scan()
        val q = query.trim()
        if (q.isBlank()) return all.drop((page - 1) * pageSize).take(pageSize)
        return all.filter { it.title.contains(q, ignoreCase = true) }
            .drop((page - 1) * pageSize).take(pageSize)
    }

    // ─────────────── PlaybackCapability ───────────────

    override suspend fun resolvePlayback(track: ProviderTrack): PlaybackResolution {
        val origin = track.origin as? TrackOrigin.LocalOrigin
            ?: return PlaybackResolution.Failed("非本地来源")
        // content:// URI 直接可播，由 Media3 DefaultDataSource 读取
        return PlaybackResolution.Playable(url = origin.contentUri)
    }
}
