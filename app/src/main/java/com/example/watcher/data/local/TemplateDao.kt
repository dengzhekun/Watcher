package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.VideoTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM monitor_templates ORDER BY templateId")
    fun observeMonitorTemplates(): Flow<List<MonitorTemplateEntity>>

    @Query("SELECT * FROM monitor_templates WHERE templateId = :id")
    suspend fun getMonitorTemplate(id: String): MonitorTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonitor(template: MonitorTemplateEntity)

    @Query("DELETE FROM monitor_templates WHERE templateId = :id")
    suspend fun deleteMonitorTemplate(id: String)

    @Query("SELECT COUNT(*) FROM monitor_templates")
    suspend fun monitorTemplateCount(): Int

    @Query("SELECT * FROM video_templates ORDER BY templateId")
    fun observeVideoTemplates(): Flow<List<VideoTemplateEntity>>

    @Query("SELECT * FROM video_templates WHERE templateId = :id")
    suspend fun getVideoTemplate(id: String): VideoTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideo(template: VideoTemplateEntity)

    @Query("DELETE FROM video_templates WHERE templateId = :id")
    suspend fun deleteVideoTemplate(id: String)

    @Query("SELECT COUNT(*) FROM video_templates")
    suspend fun videoTemplateCount(): Int
}
