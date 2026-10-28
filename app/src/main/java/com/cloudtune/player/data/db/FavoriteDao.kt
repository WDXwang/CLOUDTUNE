package com.cloudtune.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    /** 收藏列表（按添加时间倒序），UI 可直接收集此 Flow。 */
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun findById(id: String): FavoriteEntity?

    /** 冲突即覆盖（重复收藏同一首歌时刷新元数据与时间）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String)
}
