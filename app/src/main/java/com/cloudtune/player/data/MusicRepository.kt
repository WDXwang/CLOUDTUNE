package com.cloudtune.player.data

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.StreamSource
import kotlinx.coroutines.flow.Flow

/**
 * 音乐仓库：对界面 / 播放器屏蔽后端差异，统一提供
 * 多存储管理、文件浏览、播放源解析、收藏（本地）能力。
 *
 * 多网盘聚合的核心约定：
 * - 每个 [StorageAccount] 对应一个独立的 [CloudDataSource]；
 * - 目录项 [CloudFile] 携带 `accountId`，播放 / 浏览时按该字段路由回对应数据源；
 * - 本地音乐不归属任何账号（`isLocal = true`）。
 */
interface MusicRepository {

    /** 全部已添加的存储（含停用），按添加顺序。 */
    val accounts: Flow<List<StorageAccount>>

    /** 添加一条存储（调用方应先 [testConnection] 校验）。 */
    suspend fun addAccount(account: StorageAccount)

    /** 更新存储（改配置 / 改名 / 启停），会重建对应数据源。 */
    suspend fun updateAccount(account: StorageAccount)

    /** 删除一条存储并释放其数据源。 */
    suspend fun removeAccount(id: String)

    /** 清除全部存储（退出登录）。 */
    suspend fun clearAccounts()

    /** 用给定配置做连通性测试（不影响已保存的存储）。 */
    suspend fun testConnection(config: ServerConfig): Result<Unit>

    /** 列出某存储某目录内容（文件夹 + 文件），dir 为 null 表示该存储根目录。 */
    suspend fun list(accountId: String, dir: CloudFile?): List<CloudFile>

    /** 在某存储内按文件名递归搜索（有深度 / 目录数上限，避免打爆接口）。 */
    suspend fun search(accountId: String, query: String): List<CloudFile>

    /** 递归扫描某存储内的全部音乐文件（用于按歌手自动生成歌单，有深度 / 目录数 / 条数上限）。 */
    suspend fun scanMusic(accountId: String): List<CloudFile>

    /** 扫描设备本地音乐（需先获得音频权限） */
    suspend fun listLocalMusic(): List<CloudFile>

    /** 解析音乐文件为可播放的流地址（按 file.accountId 路由） */
    suspend fun resolveStream(file: CloudFile): StreamSource

    /** 收藏列表（按添加时间倒序） */
    fun observeFavorites(): Flow<List<MusicTrack>>

    /** 某文件是否已收藏 */
    suspend fun isFavorite(id: String): Boolean

    /** 切换收藏状态 */
    suspend fun toggleFavorite(track: MusicTrack)

    // ---------- 歌单 ----------

    /** 歌单列表（概要，含曲数） */
    fun observePlaylists(): Flow<List<com.cloudtune.player.core.model.PlaylistInfo>>

    /** 歌单内歌曲 */
    fun observePlaylistTracks(playlistId: Long): Flow<List<MusicTrack>>

    /** 新建歌单，返回自增 id */
    suspend fun createPlaylist(name: String): Long

    /** 重命名歌单 */
    suspend fun renamePlaylist(id: Long, name: String)

    /** 删除歌单（连同内部歌曲） */
    suspend fun deletePlaylist(id: Long)

    /** 把一首歌加入歌单 */
    suspend fun addToPlaylist(playlistId: Long, track: MusicTrack)

    /** 从歌单移除一首歌 */
    suspend fun removeFromPlaylist(playlistId: Long, fileId: String)
}
