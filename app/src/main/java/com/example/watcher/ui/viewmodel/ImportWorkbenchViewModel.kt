package com.example.watcher.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.watcher.importworkbench.ImportActionTarget
import com.example.watcher.importworkbench.ImportWorkbenchRepository
import com.example.watcher.importworkbench.WorkbenchResourceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportWorkbenchResource(
    val id: String,
    val name: String,
    val typeLabel: String,
    val sourceLabel: String,
    val status: WorkbenchResourceState,
    val summary: String,
    val detailLines: List<String> = emptyList(),
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null,
    val actionTarget: ImportActionTarget? = null
)

data class ImportWorkbenchUiState(
    val isLoading: Boolean = true,
    val title: String = "通用导入工作台",
    val subtitle: String = "统一检查导入资源状态，快速处理失败项与待激活动作。",
    val resources: List<ImportWorkbenchResource> = emptyList(),
    val expandedResourceIds: Set<String> = emptySet(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val importedCount: Int
        get() = resources.count { it.status == WorkbenchResourceState.APPLIED }

    val pendingCount: Int
        get() = resources.count {
            it.status == WorkbenchResourceState.RECEIVED ||
                it.status == WorkbenchResourceState.NEEDS_MANUAL_ACTION
        }

    val failedCount: Int
        get() = resources.count { it.status == WorkbenchResourceState.FAILED }
}

class ImportWorkbenchViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = ImportWorkbenchRepository.fromContext(application)

    private val _uiState = MutableStateFlow(ImportWorkbenchUiState())
    val uiState: StateFlow<ImportWorkbenchUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val batch = repository.loadBatch()
        val resources = repository.loadCards().mapIndexed { index, card ->
            ImportWorkbenchResource(
                id = buildResourceId(
                    importedAt = batch?.importedAt,
                    index = index,
                    card = card
                ),
                name = card.title,
                typeLabel = card.resourceType.toUiTypeLabel(),
                sourceLabel = batch?.sourceLabel ?: "通用导入",
                status = card.state,
                summary = card.summary,
                detailLines = card.detailLines,
                primaryActionLabel = card.actionTarget.displayLabel.takeIf { it.isNotBlank() },
                actionTarget = card.actionTarget
            )
        }
        _uiState.value = ImportWorkbenchUiState(
            isLoading = false,
            title = "通用导入工作台",
            subtitle = batch?.let {
                "最近一次导入来自 ${it.sourceLabel} · ${formatTimestamp(it.importedAt)}"
            } ?: "统一检查导入资源状态，快速处理失败项与待激活动作。",
            resources = resources
        )
    }

    fun toggleExpanded(resourceId: String) {
        _uiState.update { current ->
            val expanded = current.expandedResourceIds.toMutableSet()
            if (!expanded.add(resourceId)) {
                expanded.remove(resourceId)
            }
            current.copy(expandedResourceIds = expanded)
        }
    }

    fun resolvePrimaryAction(resourceId: String): ImportActionTarget? {
        return _uiState.value.resources.firstOrNull { it.id == resourceId }?.actionTarget
    }

    fun triggerSecondaryAction(resourceId: String) {
        val resourceName = _uiState.value.resources.firstOrNull { it.id == resourceId }?.name
            ?: resourceId
        _uiState.update {
            it.copy(
                statusMessage = "当前资源：$resourceName",
                errorMessage = null
            )
        }
    }

    fun reportUnsupportedPrimaryAction(resourceId: String) {
        val resourceName = _uiState.value.resources.firstOrNull { it.id == resourceId }?.name
            ?: resourceId
        _uiState.update {
            it.copy(
                statusMessage = null,
                errorMessage = "当前资源暂不支持直接跳转：$resourceName"
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}

private fun buildResourceId(
    importedAt: Long?,
    index: Int,
    card: com.example.watcher.importworkbench.WorkbenchEntryCard
): String {
    return listOf(
        importedAt?.toString() ?: "no_batch",
        index.toString(),
        card.resourceType,
        card.resourceKey
    ).joinToString(":")
}

private fun String.toUiTypeLabel(): String {
    return when (this) {
        "provider" -> "Provider"
        "agent" -> "Agent"
        "audience_group" -> "AI 观众"
        "expert_council" -> "专家团"
        else -> this
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
