package com.cloudtune.player.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /** 历史记录（按最近播放时间倒序），UI 直接收集。 */
    @Query("SELECT * FROM history ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    /**
     * 记录一次播放：首次插入（playCount=1），已存在则刷新时间并把次数 +1。
     */
    @Query(
        "INSERT INTO history(id, title, artist, album, durationMs, fileJson, playedAt, playCount) " +
            "VALUES(:id, :title, :artist, :album, :durationMs, :fileJson, :playedAt, 1) " +
            "ON CONFLICT(id) DO UPDATE SET " +
            "playedAt = excluded.playedAt, " +
            "playCount = playCount + 1, " +
            "fileJson = excluded.fileJson, " +
            "title = excluded.title, " +
            "artist = excluded.artist, " +
            "album = excluded.album, " +
            "durationMs = excluded.durationMs"
    )
    suspend fun record(
        id: String,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long,
        fileJson: String,
        playedAt: Long,
    )

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}
