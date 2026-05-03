package com.example.watcher.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ImportResourceStatus {
    Imported,
    Pending,
    Failed,
    Disabled
}

data class ImportWorkbenchResource(
    val id: String,
    val name: String,
    val typeLabel: String,
    val sourceLabel: String,
    val status: ImportResourceStatus,
    val summary: String,
    val detailLines: List<String> = emptyList(),
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null
)

data class ImportWorkbenchUiState(
    val isLoading: Boolean = true,
    val title: String = "Import Workbench",
    val subtitle: String = "统一检查导入资源状态，快速处理失败项与待激活动作。",
    val resources: List<ImportWorkbenchResource> = emptyList(),
    val expandedResourceIds: Set<String> = emptySet(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val importedCount: Int
        get() = resources.count { it.status == ImportResourceStatus.Imported }
    val pendingCount: Int
        get() = resources.count { it.status == ImportResourceStatus.Pending }
    val failedCount: Int
        get() = resources.count { it.status == ImportResourceStatus.Failed }
}

interface ImportWorkbenchRepository {
    fun observeResources(): Flow<List<ImportWorkbenchResource>>
}

private class SampleImportWorkbenchRepository : ImportWorkbenchRepository {
    override fun observeResources(): Flow<List<ImportWorkbenchResource>> {
        return flowOf(
            listOf(
                ImportWorkbenchResource(
                    id = "provider_wallet",
                    name = "Provider Wallet",
                    typeLabel = "LLM",
                    sourceLabel = "xmax/provider.json",
                    status = ImportResourceStatus.Imported,
                    summary = "已导入 3 条 provider，默认项可用。",
                    detailLines = listOf(
                        "Last sync: 2026-05-03 18:35",
                        "Enabled providers: 2/3",
                        "Default provider is active"
                    ),
                    primaryActionLabel = "Recheck",
                    secondaryActionLabel = "Open"
                ),
                ImportWorkbenchResource(
                    id = "agent_profiles",
                    name = "Agent Profiles",
                    typeLabel = "Agent",
                    sourceLabel = "xmax/agent.json",
                    status = ImportResourceStatus.Pending,
                    summary = "检测到导入记录，但尚未应用到当前运行配置。",
                    detailLines = listOf(
                        "Imported definitions: 5",
                        "Pending activation: 2",
                        "Needs explicit apply"
                    ),
                    primaryActionLabel = "Apply",
                    secondaryActionLabel = "Inspect"
                ),
                ImportWorkbenchResource(
                    id = "audience_profiles",
                    name = "Audience Profiles",
                    typeLabel = "Audience",
                    sourceLabel = "xmax/audience.json",
                    status = ImportResourceStatus.Failed,
                    summary = "最新导入失败，解析字段不完整。",
                    detailLines = listOf(
                        "Last attempt: 2026-05-03 17:42",
                        "Error: missing `personaTone`",
                        "Fallback profile remains enabled"
                    ),
                    primaryActionLabel = "Retry",
                    secondaryActionLabel = "Details"
                )
            )
        )
    }
}

class ImportWorkbenchViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository: ImportWorkbenchRepository = SampleImportWorkbenchRepository()
    private var observeJob: Job? = null

    private val _uiState = MutableStateFlow(ImportWorkbenchUiState())
    val uiState: StateFlow<ImportWorkbenchUiState> = _uiState.asStateFlow()

    init {
        observeResources()
    }

    fun refresh() {
        observeResources()
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

    fun triggerPrimaryAction(resourceId: String) {
        val resourceName = _uiState.value.resources.firstOrNull { it.id == resourceId }?.name ?: resourceId
        _uiState.update {
            it.copy(
                statusMessage = "Primary action queued: $resourceName",
                errorMessage = null
            )
        }
    }

    fun triggerSecondaryAction(resourceId: String) {
        val resourceName = _uiState.value.resources.firstOrNull { it.id == resourceId }?.name ?: resourceId
        _uiState.update {
            it.copy(
                statusMessage = "Secondary action queued: $resourceName",
                errorMessage = null
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private fun observeResources() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeResources().collect { resources ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        resources = resources
                    )
                }
            }
        }
    }
}
