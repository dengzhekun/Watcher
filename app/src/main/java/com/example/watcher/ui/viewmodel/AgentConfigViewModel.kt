package com.example.watcher.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watcher.data.repository.AgentConfigRepository
import com.example.watcher.watcherApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AgentConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.watcherApplication().agentFrameworkContainer
    private val repository = AgentConfigRepository(
        service = container.service,
        brainCatalog = container.brainCatalog,
        brainConnectionTester = container.brainConnectionTester,
        llmWalletRepository = container.llmWalletRepository
    )

    private val _uiState = MutableStateFlow(AgentConfigUiState(isLoading = true))
    val uiState: StateFlow<AgentConfigUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = previous.copy(
                isLoading = true,
                deletingKnowledgeEntryId = null,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.migrateLegacyBrainSecrets()
                repository.listAgentItems() to repository.listSavedBrains()
            }.onSuccess { (agents, savedBrains) ->
                val selectedId = when {
                    previous.isCreatingNew -> null
                    previous.selectedAgentId != null && agents.any { it.agentId == previous.selectedAgentId } ->
                        previous.selectedAgentId
                    else -> agents.firstOrNull()?.agentId
                }
                if (selectedId == null) {
                    _uiState.value = AgentConfigUiState(
                        isLoading = false,
                        isCreatingNew = true,
                        agentCount = agents.size,
                        agents = agents,
                        savedBrains = savedBrains,
                        draft = AgentEditorDraft()
                    )
                } else {
                    loadDetail(selectedId, agents, savedBrains, isRefresh = true)
                }
            }.onFailure { error ->
                _uiState.value = previous.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "加载 Agent 列表失败。"
                )
            }
        }
    }

    fun startCreateAgent() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isCreatingNew = true,
            selectedAgentId = null,
            detail = null,
            draft = AgentEditorDraft(),
            statusMessage = null,
            errorMessage = null,
            deletingKnowledgeEntryId = null
        )
    }

    fun duplicateSelectedAgent() {
        val current = _uiState.value
        val sourceDraft = current.detail?.profile?.toAgentEditorDraft() ?: current.draft
        _uiState.value = current.copy(
            isCreatingNew = true,
            selectedAgentId = null,
            detail = null,
            draft = sourceDraft.duplicate(),
            statusMessage = "已从当前选中的 Agent 复制出草稿。",
            errorMessage = null,
            deletingKnowledgeEntryId = null
        )
    }

    fun selectAgent(agentId: String) {
        viewModelScope.launch {
            loadDetail(agentId, _uiState.value.agents, _uiState.value.savedBrains)
        }
    }

    fun updateDraft(transform: (AgentEditorDraft) -> AgentEditorDraft) {
        _uiState.value = _uiState.value.copy(
            draft = transform(_uiState.value.draft),
            statusMessage = null,
            errorMessage = null,
            deletingKnowledgeEntryId = null
        )
    }

    fun resetDraft() {
        val current = _uiState.value
        val restored = current.detail?.profile?.toAgentEditorDraft() ?: AgentEditorDraft()
        _uiState.value = current.copy(
            draft = restored,
            statusMessage = null,
            errorMessage = null,
            deletingKnowledgeEntryId = null
        )
    }

    fun saveDraft() {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isSaving = true,
                isTestingAgent = false,
                isTestingContext = false,
                deletingKnowledgeEntryId = null,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.saveAgent(
                    draft = current.draft,
                    existingProfile = current.detail?.profile?.takeUnless { current.isCreatingNew }
                )
            }.onSuccess { saved ->
                val agents = repository.listAgentItems()
                val savedBrains = repository.listSavedBrains()
                loadDetail(
                    agentId = saved.agentId,
                    agents = agents,
                    savedBrains = savedBrains,
                    isRefresh = true,
                    statusMessage = "Agent 已保存。"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "保存 Agent 失败。"
                )
            }
        }
    }

    fun deleteSelectedAgent() {
        val selectedId = _uiState.value.selectedAgentId ?: return
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isDeleting = true,
                isTestingAgent = false,
                isTestingContext = false,
                deletingKnowledgeEntryId = null,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.deleteAgent(selectedId)
            }.onSuccess {
                val agents = repository.listAgentItems()
                val nextId = agents.firstOrNull()?.agentId
                if (nextId == null) {
                    _uiState.value = AgentConfigUiState(
                        isLoading = false,
                        isCreatingNew = true,
                        agentCount = agents.size,
                        agents = agents,
                        savedBrains = repository.listSavedBrains(),
                        draft = AgentEditorDraft(),
                        statusMessage = "Agent 已删除。"
                    )
                } else {
                    val savedBrains = repository.listSavedBrains()
                    loadDetail(
                        agentId = nextId,
                        agents = agents,
                        savedBrains = savedBrains,
                        isRefresh = true,
                        statusMessage = "Agent 已删除。"
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    errorMessage = error.message ?: "删除 Agent 失败。"
                )
            }
        }
    }

    fun testBrainConnection() {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isTestingBrain = true,
                isTestingAgent = false,
                isTestingContext = false,
                deletingKnowledgeEntryId = null,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.testBrainConnection(
                    draft = current.draft,
                    existingProfile = current.detail?.profile?.takeUnless { current.isCreatingNew }
                )
            }.onSuccess { response ->
                val savedBrain = repository.saveBrainProfile(current.draft)
                val savedBrains = repository.listSavedBrains()
                val preview = response.replace('\n', ' ').trim().take(120)
                _uiState.value = _uiState.value.copy(
                    isTestingBrain = false,
                    savedBrains = savedBrains,
                    draft = _uiState.value.draft.copy(
                        selectedBrainId = savedBrain.id,
                        brainEndpoint = savedBrain.endpoint,
                        brainApiKey = savedBrain.apiKey,
                        brainModelName = savedBrain.modelName,
                        brainDisplayName = savedBrain.name
                    ),
                    statusMessage = if (preview.isBlank()) {
                        "Brain 连接成功，已保存为 ${savedBrain.name}。"
                    } else {
                        "Brain 连接成功，已保存为 ${savedBrain.name}：$preview"
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isTestingBrain = false,
                    errorMessage = error.message ?: "Brain 连接测试失败。"
                )
            }
        }
    }

    fun testAgent() {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isTestingAgent = true,
                isTestingBrain = false,
                isTestingContext = false,
                deletingKnowledgeEntryId = null,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.testAgent(
                    draft = current.draft,
                    existingProfile = current.detail?.profile?.takeUnless { current.isCreatingNew }
                )
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isTestingAgent = false,
                    statusMessage = "Agent 测试输入：你好，你是谁\n$response"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isTestingAgent = false,
                    errorMessage = error.message ?: "Agent 测试失败。"
                )
            }
        }
    }

    fun testContext() {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isTestingContext = true,
                isTestingBrain = false,
                isTestingAgent = false,
                deletingKnowledgeEntryId = null,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.testContext(
                    draft = current.draft,
                    existingProfile = current.detail?.profile?.takeUnless { current.isCreatingNew }
                )
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isTestingContext = false,
                    statusMessage = response
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isTestingContext = false,
                    errorMessage = error.message ?: "上下文测试失败。"
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            statusMessage = null,
            errorMessage = null,
            deletingKnowledgeEntryId = null
        )
    }

    fun deleteKnowledgeEntry(entryId: String) {
        val selectedId = _uiState.value.selectedAgentId ?: return
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                deletingKnowledgeEntryId = entryId,
                errorMessage = null,
                statusMessage = null
            )
            runCatching {
                repository.deleteKnowledgeEntry(selectedId, entryId)
            }.onSuccess {
                loadDetail(
                    agentId = selectedId,
                    agents = _uiState.value.agents,
                    savedBrains = _uiState.value.savedBrains,
                    isRefresh = true,
                    statusMessage = "知识条目已删除。"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    deletingKnowledgeEntryId = null,
                    errorMessage = error.message ?: "删除知识条目失败。"
                )
            }
        }
    }

    private suspend fun loadDetail(
        agentId: String,
        agents: List<AgentListItemUiModel>,
        savedBrains: List<SavedBrainUiModel>,
        isRefresh: Boolean = false,
        statusMessage: String? = null
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = true,
            isSaving = if (isRefresh) false else current.isSaving,
            isDeleting = if (isRefresh) false else current.isDeleting,
            deletingKnowledgeEntryId = null,
            isTestingBrain = if (isRefresh) false else current.isTestingBrain,
            isTestingAgent = if (isRefresh) false else current.isTestingAgent,
            isTestingContext = if (isRefresh) false else current.isTestingContext,
            errorMessage = null,
            statusMessage = statusMessage
        )
        runCatching {
            repository.loadAgentDetail(agentId)
        }.onSuccess { detail ->
            if (detail == null) {
                _uiState.value = current.copy(
                    isLoading = false,
                    isSaving = false,
                    isDeleting = false,
                    deletingKnowledgeEntryId = null,
                    isTestingBrain = false,
                    isTestingAgent = false,
                    isTestingContext = false,
                    agentCount = agents.size,
                    agents = agents,
                    savedBrains = savedBrains,
                    errorMessage = "未找到 Agent：$agentId"
                )
                return
            }
            _uiState.value = AgentConfigUiState(
                isLoading = false,
                isSaving = false,
                isDeleting = false,
                deletingKnowledgeEntryId = null,
                isTestingBrain = false,
                isTestingAgent = false,
                isTestingContext = false,
                isCreatingNew = false,
                agentCount = agents.size,
                agents = agents,
                savedBrains = savedBrains,
                selectedAgentId = agentId,
                detail = detail,
                draft = detail.profile.toAgentEditorDraft(),
                statusMessage = statusMessage
            )
        }.onFailure { error ->
            _uiState.value = current.copy(
                isLoading = false,
                isSaving = false,
                isDeleting = false,
                deletingKnowledgeEntryId = null,
                isTestingBrain = false,
                isTestingAgent = false,
                isTestingContext = false,
                agentCount = agents.size,
                agents = agents,
                savedBrains = savedBrains,
                errorMessage = error.message ?: "加载 Agent 详情失败。"
            )
        }
    }
}
