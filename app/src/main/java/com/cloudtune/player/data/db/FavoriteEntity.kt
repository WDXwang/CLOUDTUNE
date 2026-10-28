package com.cloudtune.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地收藏表。主键使用云端文件 id（WebDAV 为相对路径，天翼为数字 id），
 * 保证同一首歌不会重复收藏；CloudFile 整体序列化进 [fileJson] 以便回放。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    /** CloudFile 的 JSON 序列化结果。 */
    val fileJson: String,
    /** 添加时间（epoch ms），用于倒序展示。 */
    val addedAt: Long,
)
