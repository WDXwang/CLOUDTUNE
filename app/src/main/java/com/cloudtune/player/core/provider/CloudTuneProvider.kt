package com.cloudtune.player.core.provider

/**
 * CloudTune 音乐提供者统一抽象。
 *
 * 借鉴 Mixin 接口模式：每个 Provider 按需声明自己实现哪些能力，
 * 核心接口 [MusicProvider] 只负责标识和能力查询，不强迫实现任何业务。
 *
 * Provider 体系让 CloudTune 能统一处理：
 * - 商业平台（网易云、QQ、酷狗等第三方音源脚本 / 原生源）
 * - 网盘聚合（WebDAV / 天翼 / 夸克 / 123盘）
 * - 本地扫描（MediaStore）
 */

/** 音源类型 — 决定 Provider 的身份和可用能力 */
enum class MusicSource(val displayName: String) {
    Netease("网易云音乐"),
    Kugou("酷狗音乐"),
    Qishui("汽水音乐"),
    CloudDrive("我的网盘"),
    Local("本地音乐"),
    ThirdParty("第三方音源"),
}

/** Provider 可声明的能力 */
enum class MusicCapability {
    Search,          // 歌曲搜索
    Playback,        // 解析播放地址
    Lyrics,          // 获取歌词
    HomeFeed,        // 首页推荐（榜单/新歌/歌单）
    Rankings,        // 排行榜
    Playlists,       // 歌单浏览
    LocalScan,       // 本地扫描
    CloudBrowse,     // 网盘浏览（文件夹结构）
}

/** 所有 Provider 的基础标识接口 */
interface MusicProvider {
    /** 音源身份 */
    val source: MusicSource

    /** Provider 的展示名称 */
    val displayName: String

    /** 支持的能力集合 */
    val capabilities: Set<MusicCapability>

    fun supports(capability: MusicCapability): Boolean = capability in capabilities
}

// ──────────────────── Mixin 能力接口 ────────────────────

/** 歌曲搜索能力 */
interface SearchCapability {
    suspend fun searchSongs(query: String, page: Int = 1, pageSize: Int = 30): List<ProviderTrack>
}

/** 播放地址解析能力 */
interface PlaybackCapability {
    suspend fun resolvePlayback(track: ProviderTrack): PlaybackResolution
}

/** 歌词获取能力 */
interface LyricsCapability {
    suspend fun fetchLyrics(track: ProviderTrack): ByteArray?
}

/** 首页 Feed（推荐歌单 + 榜单 + 新歌预览） */
interface HomeFeedCapability {
    suspend fun homeFeed(): ProviderHomeFeed
}

/** 排行榜能力 */
interface RankingsCapability {
    suspend fun rankingList(): List<ProviderRanking>
    suspend fun rankingTracks(rankingId: String, page: Int = 1, pageSize: Int = 30): List<ProviderTrack>
}

/** 歌单能力 */
interface PlaylistsCapability {
    suspend fun playlistDetail(playlistId: String): ProviderPlaylistDetail
}

/** 网盘文件夹浏览能力（CloudDrive 专用） */
interface CloudBrowseCapability {
    suspend fun listFiles(path: String, accountId: String): List<ProviderCloudEntry>
    suspend fun resolveFileUrl(entry: ProviderCloudEntry, accountId: String): PlaybackResolution
}

/** 本地扫描能力 */
interface LocalScanCapability {
    suspend fun scan(): List<ProviderTrack>
}

// ──────────────────── 统一数据模型 ────────────────────

/**
 * Provider 返回的通用歌曲模型。
 *
 * 注意：每个 Provider 都有自己的 Track 来源（CloudFile、source song、MediaStore URI），
 * 这里用 [origin] 密封接口承载来源特定的引用，播放时再路由回对应 Provider。
 */
data class ProviderTrack(
    /** Provider 内部唯一 ID（不同 Provider 间不保证全局唯一） */
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val source: MusicSource,
    /** Provider 内部引用，播放时用它路由回正确的数据源 */
    val origin: TrackOrigin,
)

/** Track 来源的密封接口 */
sealed interface TrackOrigin {
    /** 来自网盘文件（accountId + fileId 用于路由回正确的 CloudDataSource） */
    data class CloudFileOrigin(val accountId: String?, val fileId: String) : TrackOrigin

    /** 来自第三方音源脚本 / 原生源的歌曲 JSON（播放时需 sourceKey 路由） */
    data class SourceSongOrigin(val sourceKey: String, val rawJson: String) : TrackOrigin

    /** 来自本地 MediaStore 的 content URI */
    data class LocalOrigin(val contentUri: String) : TrackOrigin

    /** 直接播放 URL（已解析好的直链，如网易云 public API） */
    data class DirectUrlOrigin(val url: String, val headers: Map<String, String> = emptyMap()) : TrackOrigin
}

/** 播放解析结果 */
sealed interface PlaybackResolution {
    /** 可直接播放：URL + 可选 headers */
    data class Playable(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val mimeType: String? = null,
    ) : PlaybackResolution

    /** 需要走 CloudFile 路由（网盘直链需实时解析） */
    data class NeedsCloudResolve(
        val accountId: String,
        val fileId: String,
    ) : PlaybackResolution

    /** 获取失败 */
    data class Failed(val reason: String) : PlaybackResolution
}

/** 首页 Feed：每个 Provider 返回自己的推荐块 */
data class ProviderHomeFeed(
    /** 推荐歌曲（横向滚动） */
    val recommendedSongs: List<ProviderTrack> = emptyList(),
    /** 榜单预览（横向卡片） */
    val rankings: List<ProviderRanking> = emptyList(),
    /** 新歌速递 */
    val newSongs: List<ProviderTrack> = emptyList(),
)

/** 排行榜 */
data class ProviderRanking(
    val id: String,
    val name: String,
    val source: MusicSource,
)

/** 歌单详情 */
data class ProviderPlaylistDetail(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<ProviderTrack> = emptyList(),
)

/** 网盘浏览条目（文件夹 / 文件） */
data class ProviderCloudEntry(
    val id: String,
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val mimeType: String? = null,
)
