package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.PortraitDimension
import kotlinx.coroutines.flow.Flow

@Dao
interface PortraitDao {

    @Query("SELECT * FROM portrait_dimensions ORDER BY dimensionKey ASC")
    fun observeAll(): Flow<List<PortraitDimension>>

    @Query("SELECT * FROM portrait_dimensions WHERE dimensionKey = :key")
    suspend fun getByKey(key: String): PortraitDimension?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dimension: PortraitDimension)

    @Query("SELECT COUNT(*) FROM portrait_dimensions")
    suspend fun count(): Int

    @Query("DELETE FROM portrait_dimensions")
    suspend fun deleteAll()
}
