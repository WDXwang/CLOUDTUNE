package com.cloudtune.player.data.repo

import android.content.Context
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import com.cloudtune.player.data.CloudDataSource
import com.cloudtune.player.data.CloudDataSourceFactory
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.data.db.FavoriteDao
import com.cloudtune.player.data.db.FavoriteEntity
import com.cloudtune.player.data.db.PlaylistDao
import com.cloudtune.player.data.db.PlaylistEntity
import com.cloudtune.player.data.db.PlaylistTrackEntity
import com.cloudtune.player.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * [MusicRepository] 默认实现：
 * - 配置读写委托给 [SettingsStore]；
 * - 持有当前后端数据源，配置变更时通过 [Mutex] 线程安全地重建；
 * - 收藏通过 [FavoriteDao] 本地落库，CloudFile 序列化进 fileJson。
 */
class MusicRepositoryImpl(
    private val context: Context,
    private val settings: SettingsStore,
    private val factory: CloudDataSourceFactory,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
) : MusicRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val mutex = Mutex()

    /** 当前使用的数据源；未配置时为 null。 */
    @Volatile
    private var current: CloudDataSource? = null

    override val config: Flow<ServerConfig?> get() = settings.config

    override suspend fun saveConfig(config: ServerConfig) {
        settings.saveConfig(config)
        rebuild(config)
    }

    override suspend fun clearConfig() {
        settings.clearConfig()
        mutex.withLock {
            current?.close()
            current = null
        }
    }

    /** 用给定配置做一次连通性测试，测完立即关闭，不影响当前已登录的数据源。 */
    override suspend fun testConnection(config: ServerConfig): Result<Unit> {
        val ds = factory.create(config)
        return try {
            ds.test()
        } finally {
            ds.close()
        }
    }

    override suspend fun list(dir: CloudFile?): List<CloudFile> =
        ensureSource().list(dir)

    /** 本地音乐扫描（IO 线程执行）。 */
    override suspend fun listLocalMusic(): List<CloudFile> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.cloudtune.player.data.local.LocalMusicScanner(context).scan()
        }

    override suspend fun resolveStream(file: CloudFile): StreamSource {
        // 本地文件：直接返回 content:// URI，由 DefaultDataSource 读取，不走网络
        if (file.isLocal) return StreamSource(url = file.id)
        return ensureSource().resolveStream(file)
    }

    override fun observeFavorites(): Flow<List<MusicTrack>> =
        favoriteDao.observeAll().map { rows -> rows.mapNotNull { it.toTrackOrNull(json) } }

    override suspend fun isFavorite(id: String): Boolean =
        favoriteDao.findById(id) != null

    override suspend fun toggleFavorite(track: MusicTrack) {
        if (favoriteDao.findById(track.file.id) != null) {
            favoriteDao.deleteById(track.file.id)
        } else {
            favoriteDao.insert(track.toEntity(json))
        }
    }

    // ---------- 歌单 ----------

    override fun observePlaylists(): Flow<List<com.cloudtune.player.core.model.PlaylistInfo>> =
        playlistDao.observeAll().map { list ->
            list.map { p ->
                // 并行查曲数：用 withContext(IO) 批量查询避免逐条阻塞主线程
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.cloudtune.player.core.model.PlaylistInfo(
                        id = p.id,
                        name = p.name,
                        trackCount = playlistDao.countTracks(p.id),
                    )
                }
            }
        }

    override fun observePlaylistTracks(playlistId: Long): Flow<List<MusicTrack>> =
        playlistDao.observeTracks(playlistId).map { rows ->
            rows.mapNotNull { it.toTrackOrNull(json) }
        }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(
            PlaylistEntity(
                name = name,
                createdAt = System.currentTimeMillis(),
            )
        )

    override suspend fun renamePlaylist(id: Long, name: String) =
        playlistDao.rename(id, name)

    override suspend fun deletePlaylist(id: Long) {
        playlistDao.clearTracks(id)
        playlistDao.deleteById(id)
    }

    override suspend fun addToPlaylist(playlistId: Long, track: MusicTrack) {
        playlistDao.insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                fileId = track.file.id,
                title = track.title,
                fileJson = json.encodeToString(CloudFile.serializer(), track.file),
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun removeFromPlaylist(playlistId: Long, fileId: String) =
        playlistDao.deleteTrack(playlistId, fileId)

    // ---------- 内部 ----------

    private suspend fun rebuild(c: ServerConfig) = mutex.withLock {
        current?.close()
        current = factory.create(c)
    }

    /**
     * 取当前数据源；若尚未初始化（例如进程重启后直接进入浏览页），
     * 尝试从已持久化的配置重建一次。仍无配置则抛带明确信息的异常。
     */
    private suspend fun ensureSource(): CloudDataSource {
        current?.let { return it }
        return mutex.withLock {
            current?.let { return it }
            val saved = settings.config.first()
                ?: throw IllegalStateException("尚未配置云盘服务器，请先在设置中登录 / 配置。")
            factory.create(saved).also { current = it }
        }
    }

    private fun FavoriteEntity.toTrackOrNull(json: Json): MusicTrack? =
        runCatching { json.decodeFromString(CloudFile.serializer(), fileJson) }
            .getOrNull()
            ?.let { file ->
                MusicTrack(
                    file = file,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                )
            }

    private fun MusicTrack.toEntity(json: Json): FavoriteEntity = FavoriteEntity(
        id = file.id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        fileJson = json.encodeToString(CloudFile.serializer(), file),
        addedAt = System.currentTimeMillis(),
    )

    private fun PlaylistTrackEntity.toTrackOrNull(json: Json): MusicTrack? =
        runCatching { json.decodeFromString(CloudFile.serializer(), fileJson) }
            .getOrNull()
            ?.let { file ->
                MusicTrack(
                    file = file,
                    title = title,
                    artist = null,
                    album = null,
                    durationMs = 0L,
                )
            }
}
