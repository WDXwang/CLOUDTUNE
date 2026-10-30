package com.cloudtune.player.data.repo

import android.content.Context
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StorageAccount
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * [MusicRepository] 默认实现：
 * - 多存储账号读写委托给 [SettingsStore]；
 * - 每条存储持有一个数据源（按 accountId 懒加载、配置变更时重建、删除时关闭）；
 * - 浏览结果统一盖上 `accountId / accountName`，播放时据此路由；
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

    /** accountId -> 数据源（按需创建，避免一次为所有网盘建立连接）。 */
    private val sources = mutableMapOf<String, CloudDataSource>()

    override val accounts: Flow<List<StorageAccount>> = settings.accounts

    override suspend fun addAccount(account: StorageAccount) {
        updateAccounts { it + account }
    }

    override suspend fun updateAccount(account: StorageAccount) {
        updateAccounts { list -> list.map { if (it.id == account.id) account else it } }
        // 配置可能变化，释放旧连接，下次访问时按新配置重建
        mutex.withLock {
            sources.remove(account.id)?.close()
        }
    }

    override suspend fun removeAccount(id: String) {
        updateAccounts { list -> list.filterNot { it.id == id } }
        mutex.withLock {
            sources.remove(id)?.close()
        }
    }

    override suspend fun clearAccounts() {
        settings.clearAccounts()
        mutex.withLock {
            sources.values.forEach { runCatching { it.close() } }
            sources.clear()
        }
    }

    /** 读 - 改 - 写 账号列表（并发下串行化，避免丢失更新）。 */
    private suspend fun updateAccounts(block: (List<StorageAccount>) -> List<StorageAccount>) {
        mutex.withLock {
            val current = settings.accounts.first()
            settings.saveAccounts(block(current))
        }
    }

    /** 用给定配置做一次连通性测试，测完立即关闭，不影响当前已登录的数据源。 */
    override suspend fun testConnection(config: ServerConfig): Result<Unit> {
        val ds = factory.create(config)
        return try {
            ds.test()
        } finally {
            runCatching { ds.close() }
        }
    }

    override suspend fun list(accountId: String, dir: CloudFile?): List<CloudFile> {
        val account = accountById(accountId)
            ?: throw IllegalStateException("存储不存在或已被删除，请刷新后重试。")
        return ensureSource(account).list(dir).map {
            it.copy(accountId = account.id, accountName = account.name)
        }
    }

    /**
     * 存储内递归搜索：广度优先，目录数上限 [MAX_SEARCH_FOLDERS]、层级上限 [MAX_SEARCH_DEPTH]，
     * 单个目录失败不影响整体（网络波动时尽量多返回结果）。
     */
    override suspend fun search(accountId: String, query: String): List<CloudFile> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val account = accountById(accountId)
            ?: throw IllegalStateException("存储不存在或已被删除，请刷新后重试。")
        val ds = ensureSource(account)

        val result = mutableListOf<CloudFile>()
        val queue = ArrayDeque<Pair<CloudFile?, Int>>()
        queue.addLast(null to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_SEARCH_FOLDERS && result.size < MAX_RESULTS) {
            val (dir, depth) = queue.removeFirst()
            visited++
            val children = try {
                ds.list(dir)
            } catch (_: Exception) {
                continue
            }
            for (child in children) {
                if (child.isDir) {
                    if (depth + 1 <= MAX_SEARCH_DEPTH) queue.addLast(child to (depth + 1))
                } else if (child.name.contains(q, ignoreCase = true)) {
                    result += child.copy(accountId = account.id, accountName = account.name)
                    if (result.size >= MAX_RESULTS) break
                }
            }
        }
        return result
    }

    /**
     * 递归收集某存储内的全部音乐文件（与 [search] 同一套 BFS 上限，
     * 只是不做名称过滤、条数上限放宽到 [MAX_SCAN_RESULTS]）。
     * 用于「按歌手自动生成歌单」这类全量扫描场景。
     */
    override suspend fun scanMusic(accountId: String): List<CloudFile> {
        val account = accountById(accountId)
            ?: throw IllegalStateException("存储不存在或已被删除，请刷新后重试。")
        val ds = ensureSource(account)

        val result = mutableListOf<CloudFile>()
        val queue = ArrayDeque<Pair<CloudFile?, Int>>()
        queue.addLast(null to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_SEARCH_FOLDERS && result.size < MAX_SCAN_RESULTS) {
            val (dir, depth) = queue.removeFirst()
            visited++
            val children = try {
                ds.list(dir)
            } catch (_: Exception) {
                continue
            }
            for (child in children) {
                if (child.isDir) {
                    if (depth + 1 <= MAX_SEARCH_DEPTH) queue.addLast(child to (depth + 1))
                } else if (child.isMusic()) {
                    result += child.copy(accountId = account.id, accountName = account.name)
                    if (result.size >= MAX_SCAN_RESULTS) break
                }
            }
        }
        return result
    }

    /** 本地音乐扫描（IO 线程执行）。 */
    override suspend fun listLocalMusic(): List<CloudFile> =
        withContext(Dispatchers.IO) {
            com.cloudtune.player.data.local.LocalMusicScanner(context).scan()
        }

    override suspend fun resolveStream(file: CloudFile): StreamSource {
        // 本地文件：直接返回 content:// URI，由 DefaultDataSource 读取，不走网络
        if (file.isLocal) return StreamSource(url = file.id)

        val accountId = file.accountId ?: run {
            // 兼容旧收藏 / 歌单：没有归属账号时，若仅添加了一条存储则自动落到该存储
            val all = settings.accounts.first()
            when (all.size) {
                0 -> throw IllegalStateException("尚未配置任何网盘，请先在设置中添加存储。")
                1 -> all.first().id
                else -> throw IllegalStateException("该文件未记录所属网盘，请在文件页重新打开播放。")
            }
        }
        val account = accountById(accountId)
            ?: throw IllegalStateException("存储不存在或已被删除。")
        return ensureSource(account).resolveStream(file)
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
                withContext(Dispatchers.IO) {
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

    private suspend fun accountById(id: String): StorageAccount? =
        settings.accounts.first().firstOrNull { it.id == id }

    /** 取（或懒创建）某存储的数据源。 */
    private suspend fun ensureSource(account: StorageAccount): CloudDataSource = mutex.withLock {
        sources[account.id]?.let { return@withLock it }
        factory.create(account.config).also { sources[account.id] = it }
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

    private companion object {
        const val MAX_SEARCH_DEPTH = 5
        const val MAX_SEARCH_FOLDERS = 300
        const val MAX_RESULTS = 200
        const val MAX_SCAN_RESULTS = 2000
    }
}
