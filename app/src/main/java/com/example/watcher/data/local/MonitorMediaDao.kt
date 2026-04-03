package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.MonitorMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorMediaDao {
    @Query("SELECT * FROM monitor_media ORDER BY createdAt DESC, id DESC")
    fun observeAllMedia(): Flow<List<MonitorMediaEntity>>

    @Query("SELECT * FROM monitor_media WHERE runId = :runId ORDER BY createdAt DESC, id DESC")
    fun observeMediaForRun(runId: Long): Flow<List<MonitorMediaEntity>>

    @Query("SELECT * FROM monitor_media WHERE runId = :runId ORDER BY createdAt DESC, id DESC")
    suspend fun getMediaForRun(runId: Long): List<MonitorMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MonitorMediaEntity): Long

    @Query("DELETE FROM monitor_media WHERE runId = :runId")
    suspend fun deleteByRunId(runId: Long)
}
