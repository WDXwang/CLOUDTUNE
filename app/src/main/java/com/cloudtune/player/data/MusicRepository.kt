package com.cloudtune.player.data

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource
import kotlinx.coroutines.flow.Flow

/**
 * 音乐仓库：对界面 / 播放器屏蔽后端差异，统一提供
 * 配置、文件浏览、播放源解析、收藏（本地）能力。
 */
interface MusicRepository {

    /** 当前保存的服务器配置，未配置时为 null */
    val config: Flow<ServerConfig?>

    /** 持久化配置（登录 / 保存） */
    suspend fun saveConfig(config: ServerConfig)

    /** 清除配置（退出登录） */
    suspend fun clearConfig()

    /** 用给定配置做连通性测试（不影响已保存配置） */
    suspend fun testConnection(config: ServerConfig): Result<Unit>

    /** 列出目录内容（文件夹 + 文件），dir 为 null 表示根目录 */
    suspend fun list(dir: CloudFile?): List<CloudFile>

    /** 扫描设备本地音乐（需先获得音频权限） */
    suspend fun listLocalMusic(): List<CloudFile>

    /** 解析音乐文件为可播放的流地址 */
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
