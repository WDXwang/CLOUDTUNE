package com.cloudtune.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    /** 全部歌单（按创建时间倒序），UI 直接收集。 */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAll(): List<PlaylistEntity>

    /** 新建歌单，返回自增 id。 */
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    // ---- 歌单内歌曲 ----

    /** 歌单内歌曲（按加入时间升序，即添加顺序）。 */
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getTracks(playlistId: Long): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND fileId = :fileId")
    suspend fun deleteTrack(playlistId: Long, fileId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun countTracks(playlistId: Long): Int
}
