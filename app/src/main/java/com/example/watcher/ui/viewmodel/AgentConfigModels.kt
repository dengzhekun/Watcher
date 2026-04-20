package com.example.watcher.ui.viewmodel

import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig
import com.example.watcher.agentframework.integration.readAppAgentBrainProfileConfig
import com.example.watcher.agentframework.service.RegisteredAgentProfile
import com.example.watcher.data.model.LlmProviderEntity

data class AgentListItemUiModel(
    val agentId: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val updatedAt: Long,
    val brainSummary: String,
    val usesCustomBrain: Boolean,
    val latestRuntimeState: String? = null,
    val latestRuntimeAt: Long? = null
)

data class AgentTextRecordUiModel(
    val id: String,
    val title: String,
    val content: String,
    val supporting: String,
    val createdAt: Long,
    val tags: List<String> = emptyList()
)

data class AgentRunUiModel(
    val runtimeId: String,
    val title: String,
    val lifecycleState: String,
    val stopReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val outputs: List<String>,
    val errorMessage: String? = null
)

data class AgentActivityItemUiModel(
    val id: String,
    val title: String,
    val detail: String,
    val timestamp: Long
)

data class AgentDetailUiModel(
    val profile: RegisteredAgentProfile,
    val knowledge: List<AgentTextRecordUiModel>,
    val workingMemory: List<AgentTextRecordUiModel>,
    val episodicMemory: List<AgentTextRecordUiModel>,
    val runs: List<AgentRunUiModel>,
    val activities: List<AgentActivityItemUiModel>,
    val latestRuntimeId: String? = null
)

data class SavedBrainUiModel(
    val id: String,
    val name: String,
    val endpoint: String,
    val apiKey: String,
    val modelName: String
)

data class AgentEditorDraft(
    val agentId: String = "",
    val name: String = "",
    val description: String = "",
    val goal: String = "",
    val systemInstruction: String = "",
    val tagsText: String = "",
    val selectedBrainId: String = "",
    val brainEndpoint: String = "",
    val brainApiKey: String = "",
    val brainModelName: String = "",
    val brainDisplayName: String = "",
    val maxSteps: String = AgentRunConfig().maxSteps.toString(),
    val maxConsecutiveFailures: String = AgentRunConfig().maxConsecutiveFailures.toString(),
    val maxIdleTurns: String = AgentRunConfig().maxIdleTurns.toString(),
    val maxRuntimeMillis: String = AgentRunConfig().maxRuntimeMillis.toString(),
    val toolTimeoutMillis: String = AgentRunConfig().toolTimeoutMillis.toString()
)

data class AgentConfigUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val deletingKnowledgeEntryId: String? = null,
    val isTestingBrain: Boolean = false,
    val isTestingAgent: Boolean = false,
    val isTestingContext: Boolean = false,
    val isCreatingNew: Boolean = true,
    val agentCount: Int = 0,
    val agents: List<AgentListItemUiModel> = emptyList(),
    val savedBrains: List<SavedBrainUiModel> = emptyList(),
    val selectedAgentId: String? = null,
    val detail: AgentDetailUiModel? = null,
    val draft: AgentEditorDraft = AgentEditorDraft(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

internal fun RegisteredAgentProfile.toAgentEditorDraft(): AgentEditorDraft {
    return AgentEditorDraft(
        agentId = agentId,
        name = definition.name,
        description = definition.description,
        goal = definition.goal,
        systemInstruction = definition.systemInstruction,
        tagsText = tags.joinToString(", "),
        selectedBrainId = readAppAgentBrainProfileConfig().providerId,
        brainEndpoint = readAppAgentBrainProfileConfig().endpoint,
        brainApiKey = readAppAgentBrainProfileConfig().apiKey,
        brainModelName = readAppAgentBrainProfileConfig().modelName,
        brainDisplayName = readAppAgentBrainProfileConfig().displayName,
        maxSteps = config.maxSteps.toString(),
        maxConsecutiveFailures = config.maxConsecutiveFailures.toString(),
        maxIdleTurns = config.maxIdleTurns.toString(),
        maxRuntimeMillis = config.maxRuntimeMillis.toString(),
        toolTimeoutMillis = config.toolTimeoutMillis.toString()
    )
}

internal fun AgentEditorDraft.toAppAgentBrainProfileConfig(): AppAgentBrainProfileConfig {
    return AppAgentBrainProfileConfig(
        providerId = selectedBrainId.trim(),
        endpoint = brainEndpoint.trim(),
        apiKey = brainApiKey.trim(),
        modelName = brainModelName.trim(),
        displayName = brainDisplayName.trim()
    )
}

internal fun SavedBrainUiModel.toDraft(current: AgentEditorDraft): AgentEditorDraft {
    return current.copy(
        selectedBrainId = id,
        brainEndpoint = endpoint,
        brainApiKey = apiKey,
        brainModelName = modelName,
        brainDisplayName = name
    )
}

internal fun LlmProviderEntity.toSavedBrainUiModel(): SavedBrainUiModel {
    return SavedBrainUiModel(
        id = id,
        name = name,
        endpoint = endpoint,
        apiKey = apiKey,
        modelName = modelName
    )
}

internal fun AgentEditorDraft.duplicate(): AgentEditorDraft {
    val baseId = agentId.ifBlank { "agent" }
    val nextId = if (baseId.endsWith("_copy")) "${baseId}_2" else "${baseId}_copy"
    return copy(
        agentId = nextId,
        name = if (name.isBlank()) "Agent Copy" else "$name Copy"
    )
}
