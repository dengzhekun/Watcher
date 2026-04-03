package com.example.watcher.data.local

import androidx.room.*
import com.example.watcher.data.model.VideoStreamSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoStreamSettingsDao {
    
    @Query("SELECT * FROM video_stream_settings WHERE id = 1")
    fun getSettings(): Flow<VideoStreamSettings?>
    
    @Query("SELECT * FROM video_stream_settings WHERE id = 1")
    suspend fun getSettingsSync(): VideoStreamSettings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: VideoStreamSettings)
    
    @Update
    suspend fun update(settings: VideoStreamSettings)
}