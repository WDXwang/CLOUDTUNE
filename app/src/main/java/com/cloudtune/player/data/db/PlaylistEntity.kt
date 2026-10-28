package com.cloudtune.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地歌单表。
 *
 * @param id 自增主键
 * @param name 歌单名称
 * @param createdAt 创建时间（epoch ms），用于排序
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)
