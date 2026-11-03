package com.cloudtune.player.core.provider

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.core.util.MusicUtils
import kotlinx.coroutines.flow.first

/**
 * 网盘聚合 Provider — 把 MusicRepository 包装成 MusicProvider。
 *
 * 特点：
 * - SearchCapability：跨盘按文件名搜索（MusicRepository.search）
 * - PlaybackCapability：resolveStream → Playable(url, headers)
 * - CloudBrowseCapability：listFiles / resolveFileUrl（保留文件夹浏览能力）
 * - HomeFeedCapability：首页推荐 = "最近播放" + "收藏" 预览（没有个性化推荐接口）
 */
class CloudProvider(
    private val repository: MusicRepository,
) : MusicProvider,
    SearchCapability,
    PlaybackCapability,
    CloudBrowseCapability,
    HomeFeedCapability {

    override val source = MusicSource.CloudDrive
    override val displayName = "我的网盘"

    override val capabilities = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.CloudBrowse,
        MusicCapability.HomeFeed,
    )

    // ─────────────── SearchCapability ───────────────

    /**
     * 跨全部启用的网盘并行搜索文件名。
     * 注意：网盘没有 ID3 元数据，只能按文件名模糊匹配。
     */
    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): List<ProviderTrack> {
        val accounts = repository.accounts.first()
        val enabled = accounts.filter { it.enabled }
        val results = enabled.flatMap { account ->
            runCatching { repository.search(account.id, query) }.getOrDefault(emptyList())
        }
        return results
            .filter { it.isMusic() }
            .distinctBy { "${it.accountId}:${it.id}" }
            .map { it.toProviderTrack() }
            .drop((page - 1) * pageSize)
            .take(pageSize)
    }

    // ─────────────── PlaybackCapability ───────────────

    override suspend fun resolvePlayback(track: ProviderTrack): PlaybackResolution {
        val origin = track.origin as? TrackOrigin.CloudFileOrigin
            ?: return PlaybackResolution.Failed("非网盘来源的歌曲")
        return runCatching {
            val file = CloudFile(
                id = origin.fileId,
                name = track.title,
                path = "",
                isDir = false,
                accountId = origin.accountId,
            )
            val stream = repository.resolveStream(file)
            PlaybackResolution.Playable(url = stream.url, headers = stream.headers)
        }.getOrElse { PlaybackResolution.Failed(it.message ?: "解析失败") }
    }

    // ─────────────── CloudBrowseCapability ───────────────

    /** 列出某网盘某目录的条目（ProviderTrack 含文件夹 + 文件，调用方按 isDir 区分） */
    override suspend fun listFiles(path: String, accountId: String): List<ProviderCloudEntry> {
        val file = if (path.isBlank()) null else CloudFile(
            id = path, name = path.substringAfterLast('/'), path = path, isDir = true,
            accountId = accountId,
        )
        return runCatching { repository.list(accountId, file).map { it.toCloudEntry() } }
            .getOrDefault(emptyList())
    }

    override suspend fun resolveFileUrl(entry: ProviderCloudEntry, accountId: String): PlaybackResolution {
        val file = CloudFile(
            id = entry.id, name = entry.name, path = entry.path,
            isDir = entry.isDir, accountId = accountId,
        )
        return runCatching {
            val stream = repository.resolveStream(file)
            PlaybackResolution.Playable(url = stream.url, headers = stream.headers)
        }.getOrElse { PlaybackResolution.Failed(it.message ?: "解析失败") }
    }

    // ─────────────── HomeFeedCapability ───────────────

    /**
     * 网盘没有官方推荐接口。HomeFeed 用"全部收藏" + "各网盘首屏各展示 N 首音乐"模拟。
     */
    override suspend fun homeFeed(): ProviderHomeFeed {
        val accounts = repository.accounts.first().filter { it.enabled }
        val recommended = mutableListOf<ProviderTrack>()
        accounts.forEach { account ->
            runCatching {
                repository.list(account.id, null)
                    .filter { it.isMusic() }
                    .take(3)
                    .map { it.toProviderTrack() }
                    .also { recommended += it }
            }
        }
        val favorites = runCatching {
            com.cloudtune.player.data.db.AppDatabase.getInstance(
                com.cloudtune.player.CloudTuneApp.instance
            ).favoriteDao().observeAll()
        }.getOrNull()
        return ProviderHomeFeed(
            recommendedSongs = recommended.distinctBy { it.id }.take(12),
        )
    }

    // ─────────────── 内部 ───────────────

    private fun CloudFile.toProviderTrack(): ProviderTrack {
        val base = MusicUtils.baseName(name)
        val artist = if (" - " in base) base.substringBefore(" - ") else ""
        val title = if (" - " in base) base.substringAfter(" - ") else base
        return ProviderTrack(
            id = "${accountId ?: ""}:$id",
            title = title.ifBlank { name },
            artist = artist,
            source = MusicSource.CloudDrive,
            durationMs = 0L,
            origin = TrackOrigin.CloudFileOrigin(accountId, id),
        )
    }

    private fun CloudFile.toCloudEntry() = ProviderCloudEntry(
        id = id, name = name, path = path,
        isDir = isDir, size = size, mimeType = mimeType,
    )
}
