package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.MonitorEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorEventDao {
    @Query("SELECT * FROM monitor_events ORDER BY timestamp ASC, id ASC")
    fun observeAllEvents(): Flow<List<MonitorEventEntity>>

    @Query("SELECT * FROM monitor_events WHERE runId = :runId ORDER BY timestamp ASC, id ASC")
    fun observeEventsForRun(runId: Long): Flow<List<MonitorEventEntity>>

    @Query("SELECT * FROM monitor_events WHERE runId = :runId ORDER BY timestamp ASC, id ASC")
    suspend fun getEventsForRun(runId: Long): List<MonitorEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: MonitorEventEntity): Long

    @Query("DELETE FROM monitor_events WHERE runId = :runId")
    suspend fun deleteByRunId(runId: Long)
}
