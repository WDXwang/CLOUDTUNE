package com.cloudtune.player.core.provider

import com.cloudtune.player.data.source.BuiltinSources
import com.cloudtune.player.data.source.SourceRepository
import com.cloudtune.player.data.source.SourceSong
import kotlinx.coroutines.flow.first

/**
 * 第三方音源 Provider — 包装 SourceRepository（BuiltinSources + QuickJS 脚本引擎）。
 *
 * 把已导入的全部第三方音源（网易云热歌榜、酷狗音乐榜、汽水、洛雪自定义源脚本）
 * 统一聚合为一个 Provider。
 *
 * 注意：Provider 的 source 统一标为 ThirdParty，但每首歌的 origin 里会保留 sourceKey，
 * 播放时路由回正确的子源。
 */
class SourceProvider(
    private val sourceRepository: SourceRepository,
) : MusicProvider, SearchCapability, PlaybackCapability, HomeFeedCapability, RankingsCapability {

    override val source = MusicSource.ThirdParty
    override val displayName = "第三方音源"

    override val capabilities = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.HomeFeed,
        MusicCapability.Rankings,
    )

    // ─────────────── SearchCapability ───────────────

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): List<ProviderTrack> {
        val sources = sourceRepository.sources.first()
        val results = sources.flatMap { src ->
            runCatching { sourceRepository.search(src, query).getOrNull()?.songs.orEmpty() }
                .getOrDefault(emptyList())
        }
        return results
            .distinctBy { "${it.sourceKey}:${it.id}" }
            .map { it.toProviderTrack() }
            .drop((page - 1) * pageSize)
            .take(pageSize)
    }

    // ─────────────── PlaybackCapability ───────────────

    override suspend fun resolvePlayback(track: ProviderTrack): PlaybackResolution {
        val origin = track.origin as? TrackOrigin.SourceSongOrigin
            ?: return PlaybackResolution.Failed("非第三方音源来源")
        val sources = sourceRepository.sources.first()
        val source = sources.firstOrNull { src ->
            BuiltinSources.nameForSourceKey(origin.sourceKey)?.let { it == src.name } == true
                || src.sources.contains(origin.sourceKey)
        } ?: sources.firstOrNull() ?: return PlaybackResolution.Failed("找不到对应音源")

        return runCatching {
            val url = sourceRepository.resolveUrl(source, SourceSong(
                id = track.id.substringAfterLast(':'),
                name = track.title,
                sourceKey = origin.sourceKey,
                raw = origin.rawJson,
            )).getOrNull()
            if (!url.isNullOrBlank()) PlaybackResolution.Playable(url = url)
            else PlaybackResolution.Failed("音源未返回播放地址")
        }.getOrElse { PlaybackResolution.Failed(it.message ?: "解析失败") }
    }

    // ─────────────── HomeFeedCapability ───────────────

    override suspend fun homeFeed(): ProviderHomeFeed {
        val sources = sourceRepository.sources.first()
        val firstSrc = sources.firstOrNull() ?: return ProviderHomeFeed()
        val boards = runCatching {
            sourceRepository.loadBoard(firstSrc, null).getOrNull()?.boards.orEmpty()
        }.getOrDefault(emptyList())
        val rankings = boards.take(4).map { b ->
            ProviderRanking(id = b.id, name = b.name, source = MusicSource.ThirdParty)
        }
        val firstBoard = boards.firstOrNull()
        val songs = firstBoard?.let { loadBoardSongs(it) }.orEmpty()
        return ProviderHomeFeed(
            recommendedSongs = songs.take(10),
            rankings = rankings,
        )
    }

    private suspend fun loadBoardSongs(board: com.cloudtune.player.data.source.SourceBoard): List<ProviderTrack> {
        val sources = sourceRepository.sources.first()
        val (sourceId, boardId) = board.id.split('|', limit = 2).let {
            if (it.size == 2) it[0] to it[1] else "" to it[0]
        }
        val source = sources.firstOrNull { it.id == sourceId } ?: sources.firstOrNull() ?: return emptyList()
        return runCatching {
            sourceRepository.loadBoard(source, boardId).getOrNull()?.songs.orEmpty()
        }.getOrDefault(emptyList())
            .map { it.toProviderTrack() }
    }

    // ─────────────── RankingsCapability ───────────────

    override suspend fun rankingList(): List<ProviderRanking> {
        val sources = sourceRepository.sources.first()
        return sources.flatMap { src ->
            runCatching {
                sourceRepository.loadBoard(src, null).getOrNull()?.boards.orEmpty()
                    .map { ProviderRanking(id = "${src.id}|${it.id}", name = it.name, source = MusicSource.ThirdParty) }
            }.getOrDefault(emptyList())
        }
    }

    override suspend fun rankingTracks(rankingId: String, page: Int, pageSize: Int): List<ProviderTrack> {
        val sources = sourceRepository.sources.first()
        val (sourceId, boardId) = rankingId.split('|', limit = 2).let {
            if (it.size == 2) it[0] to it[1] else "" to it[0]
        }
        val source = sources.firstOrNull { it.id == sourceId } ?: sources.firstOrNull() ?: return emptyList()
        return runCatching {
            sourceRepository.loadBoard(source, boardId).getOrNull()?.songs.orEmpty()
        }.getOrDefault(emptyList())
            .map { it.toProviderTrack() }
            .take(pageSize)
    }

    // ─────────────── 内部 ───────────────

    private fun SourceSong.toProviderTrack(): ProviderTrack = ProviderTrack(
        id = "${sourceKey.ifBlank { "third" }}:$id",
        title = name,
        artist = artist,
        album = album,
        durationMs = durationMs,
        source = sourceOf(sourceKey),
        origin = TrackOrigin.SourceSongOrigin(sourceKey, raw),
    )

    private fun sourceOf(sourceKey: String): MusicSource = when {
        sourceKey.equals("netease", true) -> MusicSource.Netease
        sourceKey.equals("kugou", true) -> MusicSource.Kugou
        else -> MusicSource.ThirdParty
    }

    private suspend fun sourceForSourceKey(key: String): List<com.cloudtune.player.data.source.MusicSourceInfo> {
        val sources = sourceRepository.sources.first()
        return sources.filter { src ->
            src.sources.contains(key) || src.sourceKeys().any { it.equals(key, true) }
        }
    }

    private fun com.cloudtune.player.data.source.MusicSourceInfo.sourceKeys() =
        sourceActions.keys + sources
}
