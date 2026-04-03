package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.watcher.data.model.VideoProcessTask
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProcessTaskDao {
    @Query("SELECT * FROM video_process_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<VideoProcessTask>>

    @Query("SELECT * FROM video_process_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): VideoProcessTask?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: VideoProcessTask): Long

    @Update
    suspend fun update(task: VideoProcessTask)

    @Transaction
    suspend fun upsert(task: VideoProcessTask): Long {
        return if (task.id == 0L) {
            insert(task)
        } else {
            update(task)
            task.id
        }
    }

    @Query("DELETE FROM video_process_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
