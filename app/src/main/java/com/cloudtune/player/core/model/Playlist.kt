package com.cloudtune.player.core.model

/**
 * 歌单的概要信息（列表页展示）。
 *
 * @param id 歌单本地主键
 * @param name 歌单名称
 * @param trackCount 歌单内歌曲数
 */
data class PlaylistInfo(
    val id: Long,
    val name: String,
    val trackCount: Int,
)
