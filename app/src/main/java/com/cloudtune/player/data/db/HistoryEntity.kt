package com.cloudtune.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史播放记录。主键用文件 id（同收藏），同一首歌重复播放时只保留一条，
 * 刷新 [playedAt] 并累加 [playCount]；[fileJson] 存完整 CloudFile 以便回放。
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val fileJson: String,
    /** 最近一次播放时间（epoch ms），列表按此倒序。 */
    val playedAt: Long,
    /** 累计播放次数。 */
    val playCount: Int,
)
