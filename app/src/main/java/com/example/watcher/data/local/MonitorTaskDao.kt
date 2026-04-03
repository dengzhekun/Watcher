package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.MonitorTask
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorTaskDao {
    @Query("SELECT * FROM monitor_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<MonitorTask>>

    @Query("SELECT * FROM monitor_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): MonitorTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: MonitorTask): Long

    @Query("DELETE FROM monitor_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
