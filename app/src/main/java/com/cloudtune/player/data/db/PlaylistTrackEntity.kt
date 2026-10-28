package com.cloudtune.player.data.db

import androidx.room.Entity

/**
 * 歌单内歌曲表。组合主键 (playlistId, fileId) 保证同一首歌不会重复加入同一歌单。
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "fileId"],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    /** 云端文件 id（本地音乐为 content:// URI） */
    val fileId: String,
    val title: String,
    /** CloudFile 的 JSON 序列化结果，用于回放 */
    val fileJson: String,
    /** 加入时间（epoch ms），用于排序 */
    val addedAt: Long,
)
