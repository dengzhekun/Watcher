package com.example.watcher.data.repository

import com.example.watcher.data.model.CouncilTemplates
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.local.TemplateDao
import com.example.watcher.data.model.MonitorTaskTemplates
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.VideoTaskTemplates
import com.example.watcher.data.model.VideoTemplateEntity
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val dao: TemplateDao) {

    val monitorTemplates: Flow<List<MonitorTemplateEntity>> = dao.observeMonitorTemplates()
    val videoTemplates: Flow<List<VideoTemplateEntity>> = dao.observeVideoTemplates()
    val councilTemplates: Flow<List<CouncilTemplateEntity>> = dao.observeCouncilTemplates()

    suspend fun updateMonitorTemplate(entity: MonitorTemplateEntity) {
        dao.upsertMonitor(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateVideoTemplate(entity: VideoTemplateEntity) {
        dao.upsertVideo(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateCouncilTemplate(entity: CouncilTemplateEntity) {
        dao.upsertCouncil(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun resetMonitorTemplate(templateId: String) {
        val default = MonitorTaskTemplates.findById(templateId)?.toEntity() ?: return
        dao.upsertMonitor(default)
    }

    suspend fun resetVideoTemplate(templateId: String) {
        val default = VideoTaskTemplates.findById(templateId)?.toEntity() ?: return
        dao.upsertVideo(default)
    }

    suspend fun resetCouncilTemplate(templateId: String) {
        val default = CouncilTemplates.findById(templateId)?.toEntity() ?: return
        dao.upsertCouncil(default)
    }

    suspend fun getMonitorTemplate(id: String): MonitorTemplateEntity? {
        return dao.getMonitorTemplate(id)
    }

    suspend fun getVideoTemplate(id: String): VideoTemplateEntity? {
        return dao.getVideoTemplate(id)
    }

    suspend fun getCouncilTemplate(id: String): CouncilTemplateEntity? {
        return dao.getCouncilTemplate(id)
    }

    suspend fun councilTemplatesFirstSnapshot(): List<CouncilTemplateEntity> {
        return dao.getAllCouncilTemplates()
    }
}
