package com.example.watcher.ui.viewmodel

import com.example.watcher.data.local.TemplateDao
import com.example.watcher.data.model.CouncilExpertEntity
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.VideoTemplateEntity
import com.example.watcher.data.repository.CouncilExpertRepository
import com.example.watcher.data.repository.TemplateRepository
import com.example.watcher.data.repository.TemplateShareManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles template CRUD, import/export, and reset operations.
 * Extracted from IntentViewModel.
 */
internal class TemplateDelegate(
    private val scope: CoroutineScope,
    private val templateRepository: TemplateRepository,
    private val templateDao: TemplateDao,
    private val councilExpertRepository: CouncilExpertRepository
) {
    // ── Update ────────────────────────────────────────────────────
    fun updateMonitorTemplate(entity: MonitorTemplateEntity) {
        scope.launch { templateRepository.updateMonitorTemplate(entity) }
    }

    fun updateVideoTemplate(entity: VideoTemplateEntity) {
        scope.launch { templateRepository.updateVideoTemplate(entity) }
    }

    fun updateCouncilTemplate(entity: CouncilTemplateEntity) {
        scope.launch { templateRepository.updateCouncilTemplate(entity) }
    }

    // ── Reset ─────────────────────────────────────────────────────
    fun resetMonitorTemplate(templateId: String) {
        scope.launch { templateRepository.resetMonitorTemplate(templateId) }
    }

    fun resetVideoTemplate(templateId: String) {
        scope.launch { templateRepository.resetVideoTemplate(templateId) }
    }

    fun resetCouncilTemplate(templateId: String) {
        scope.launch { templateRepository.resetCouncilTemplate(templateId) }
    }

    // ── Delete ─────────────────────────────────────────────────────
    fun deleteMonitorTemplate(templateId: String) {
        scope.launch { templateDao.deleteMonitorTemplate(templateId) }
    }

    fun deleteVideoTemplate(templateId: String) {
        scope.launch { templateDao.deleteVideoTemplate(templateId) }
    }

    fun deleteCouncilTemplate(templateId: String) {
        scope.launch { templateDao.deleteCouncilTemplate(templateId) }
    }

    // ── Export ─────────────────────────────────────────────────────
    fun exportMonitorTemplate(template: MonitorTemplateEntity): String =
        TemplateShareManager.exportMonitorTemplate(template)

    fun exportVideoTemplate(template: VideoTemplateEntity): String =
        TemplateShareManager.exportVideoTemplate(template)

    fun exportCouncilTemplate(template: CouncilTemplateEntity): String =
        TemplateShareManager.exportCouncilTemplate(template)

    fun exportCouncilExpertTemplate(expert: CouncilExpertEntity): String =
        TemplateShareManager.exportCouncilExpertTemplate(expert)

    // ── Import ────────────────────────────────────────────────────
    fun importTemplate(text: String, onResult: (String) -> Unit) {
        scope.launch {
            TemplateShareManager.importTemplate(text)
                .onSuccess { result ->
                    when {
                        result.monitorTemplate != null -> {
                            templateDao.upsertMonitor(result.monitorTemplate)
                            onResult("监控模板「${result.label}」导入成功")
                        }
                        result.videoTemplate != null -> {
                            templateDao.upsertVideo(result.videoTemplate)
                            onResult("视频分析模板「${result.label}」导入成功")
                        }
                        result.councilTemplate != null -> {
                            templateDao.upsertCouncil(result.councilTemplate)
                            onResult("智囊团模板「${result.label}」导入成功")
                        }
                        result.councilExpert != null -> {
                            councilExpertRepository.importExpert(result.councilExpert)
                            onResult("智囊团专家模板「${result.label}」导入成功")
                        }
                    }
                }
                .onFailure { e ->
                    onResult("导入失败: ${e.message}")
                }
        }
    }
}
