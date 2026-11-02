package com.cloudtune.player.data.source

import kotlinx.serialization.Serializable

/**
 * 已导入的音源脚本（兼容洛雪音乐自定义源协议）。
 *
 * 元信息来自脚本初始化时 `lx.send(EVENT_NAMES.inited, { sourceInfo })` 回传的内容，
 * 脚本本体以 `.js` 文件保存在应用私有目录。
 */
@Serializable
data class MusicSourceInfo(
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "",
    val sources: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val importedAt: Long = 0L,
)

/** 第三方源返回的一首歌曲（榜单 / 搜索通用）。 */
@Serializable
data class SourceSong(
    val id: String,
    val name: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val sourceKey: String = "",
    /** 原始 JSON（取播放地址时原样回传给音源脚本） */
    val raw: String = "",
)

/** 榜单条目：既可以是榜单列表，也可以直接带歌曲列表。 */
@Serializable
data class SourceBoard(
    val id: String,
    val name: String,
    val songs: List<SourceSong> = emptyList(),
)
