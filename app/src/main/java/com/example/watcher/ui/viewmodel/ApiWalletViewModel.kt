package com.example.watcher.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.example.watcher.data.repository.ArkConfig
import com.example.watcher.data.repository.LlmWalletRepository
import com.example.watcher.data.repository.ProviderConnectivitySnapshot
import com.example.watcher.data.repository.ProviderConnectivityStatus
import com.example.watcher.watcherApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class ApiWalletDraft(
    val id: String? = null,
    val name: String = "",
    val endpoint: String = "https://",
    val apiKey: String = "",
    val modelName: String = "",
    val enabled: Boolean = true
)

data class ApiWalletUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val testingProviderId: String? = null,
    val providers: List<LlmProviderEntity> = emptyList(),
    val providerConnectivity: Map<String, ProviderConnectivitySnapshot> = emptyMap(),
    val defaultProviderId: String? = null,
    val isEditing: Boolean = false,
    val draft: ApiWalletDraft = ApiWalletDraft(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val arkFallbackAvailable: Boolean = false
) {
    val defaultProvider: LlmProviderEntity?
        get() = providers.firstOrNull { it.id == defaultProviderId }
}

class ApiWalletViewModel(application: Application) : AndroidViewModel(application) {
    private val walletRepository: LlmWalletRepository =
        application.watcherApplication().agentFrameworkContainer.llmWalletRepository

    private val _uiState = MutableStateFlow(
        ApiWalletUiState(arkFallbackAvailable = ArkConfig.apiKey.isNotBlank())
    )
    val uiState: StateFlow<ApiWalletUiState> = _uiState.asStateFlow()

    init {
        observeProviders()
    }

    fun startCreate() {
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            draft = ApiWalletDraft(),
            statusMessage = null,
            errorMessage = null
        )
    }

    fun startEdit(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            draft = provider.toDraft(),
            statusMessage = null,
            errorMessage = null
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            isEditing = false,
            draft = ApiWalletDraft(),
            statusMessage = null,
            errorMessage = null
        )
    }

    fun updateDraft(transform: (ApiWalletDraft) -> ApiWalletDraft) {
        _uiState.value = _uiState.value.copy(
            draft = transform(_uiState.value.draft),
            statusMessage = null,
            errorMessage = null
        )
    }

    fun saveDraft() {
        val current = _uiState.value
        val draft = current.draft
        if (draft.name.isBlank() || draft.endpoint.isBlank() || draft.modelName.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Name, endpoint and model are required.")
            return
        }

        val existing = current.providers.firstOrNull { it.id == draft.id }
        val provider = LlmProviderEntity(
            id = draft.id ?: buildProviderId(draft.name),
            name = draft.name.trim(),
            endpoint = draft.endpoint.trim(),
            apiKey = draft.apiKey.trim(),
            modelName = draft.modelName.trim(),
            enabled = draft.enabled,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        _uiState.value = current.copy(isSaving = true, statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val connectionChanged = existing?.let {
                    it.endpoint != provider.endpoint ||
                        it.apiKey != provider.apiKey ||
                        it.modelName != provider.modelName
                } ?: false
                walletRepository.upsertProvider(
                    provider = provider,
                    makeDefault = current.defaultProviderId.isNullOrBlank() ||
                        current.defaultProviderId == provider.id
                )
                if (connectionChanged) {
                    walletRepository.clearProviderConnectivitySnapshot(provider.id)
                }
                if (!provider.enabled && current.defaultProviderId == provider.id) {
                    val replacementId = current.providers
                        .firstOrNull { it.id != provider.id && it.enabled }
                        ?.id
                    walletRepository.setDefaultProviderId(replacementId)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isEditing = false,
                    draft = ApiWalletDraft(),
                    statusMessage = "Provider saved."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Failed to save provider."
                )
            }
        }
    }

    fun testProvider(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Provider not found.")
            return
        }

        _uiState.value = _uiState.value.copy(
            testingProviderId = providerId,
            statusMessage = null,
            errorMessage = null
        )
        viewModelScope.launch {
            runCatching {
                OpenAiCompatibleProvider(
                    id = provider.id,
                    displayName = provider.name,
                    endpoint = provider.endpoint,
                    apiKey = provider.apiKey,
                    modelName = provider.modelName
                ).chat(
                    systemPrompt = "You are a connectivity check. Reply with a short acknowledgement.",
                    messages = listOf(ChatMessage(role = "user", content = "你好，你是谁"))
                ).trim()
            }.onSuccess { response ->
                val preview = response.replace('\n', ' ').trim().take(160)
                walletRepository.setProviderConnectivitySnapshot(
                    providerId = provider.id,
                    status = ProviderConnectivityStatus.Verified,
                    message = preview.ifBlank { "Connection succeeded." }
                )
                _uiState.value = _uiState.value.copy(
                    testingProviderId = null,
                    providerConnectivity = _uiState.value.providerConnectivity + (
                        provider.id to walletRepository.getProviderConnectivitySnapshot(provider.id)
                    ),
                    statusMessage = if (preview.isBlank()) {
                        "Provider test passed."
                    } else {
                        "Provider test passed: $preview"
                    }
                )
            }.onFailure { error ->
                walletRepository.setProviderConnectivitySnapshot(
                    providerId = provider.id,
                    status = ProviderConnectivityStatus.Failed,
                    message = error.message ?: "Provider test failed."
                )
                _uiState.value = _uiState.value.copy(
                    testingProviderId = null,
                    providerConnectivity = _uiState.value.providerConnectivity + (
                        provider.id to walletRepository.getProviderConnectivitySnapshot(provider.id)
                    ),
                    errorMessage = error.message ?: "Provider test failed."
                )
            }
        }
    }

    fun setDefaultProvider(providerId: String) {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            val provider = walletRepository.getProviderById(providerId)
            if (provider == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Provider not found.")
                return@launch
            }
            if (!provider.enabled) {
                walletRepository.upsertProvider(
                    provider.copy(enabled = true, updatedAt = System.currentTimeMillis()),
                    makeDefault = false
                )
            }
            walletRepository.setDefaultProviderId(providerId)
            _uiState.value = _uiState.value.copy(
                defaultProviderId = providerId,
                statusMessage = "Default wallet switched."
            )
        }
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            val provider = walletRepository.getProviderById(providerId)
            if (provider == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Provider not found.")
                return@launch
            }

            runCatching {
                walletRepository.upsertProvider(
                    provider.copy(enabled = enabled, updatedAt = System.currentTimeMillis()),
                    makeDefault = false
                )
                if (!enabled && _uiState.value.defaultProviderId == providerId) {
                    val replacementId = _uiState.value.providers
                        .firstOrNull { it.id != providerId && it.enabled }
                        ?.id
                    walletRepository.setDefaultProviderId(replacementId)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    statusMessage = if (enabled) "Provider enabled." else "Provider disabled."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Failed to update provider."
                )
            }
        }
    }

    fun deleteProvider(providerId: String) {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                walletRepository.deleteProvider(providerId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(statusMessage = "Provider deleted.")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Failed to delete provider."
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
    }

    private fun observeProviders() {
        viewModelScope.launch {
            walletRepository.observeProviders().collect { providers ->
                _uiState.value = _uiState.value.copy(
                    testingProviderId = null,
                    isLoading = false,
                    providers = providers,
                    providerConnectivity = providers.associate { provider ->
                        provider.id to walletRepository.getProviderConnectivitySnapshot(provider.id)
                    },
                    defaultProviderId = resolveEffectiveDefaultId(
                        providers = providers,
                        storedDefaultId = walletRepository.getDefaultProviderId()
                    ),
                    arkFallbackAvailable = ArkConfig.apiKey.isNotBlank()
                )
            }
        }
    }

    private fun resolveEffectiveDefaultId(
        providers: List<LlmProviderEntity>,
        storedDefaultId: String?
    ): String? {
        val stored = providers.firstOrNull { it.id == storedDefaultId && it.enabled }
        return stored?.id ?: providers.firstOrNull { it.enabled }?.id
    }

    private fun buildProviderId(name: String): String {
        return buildString {
            append(
                name.trim()
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifBlank { "provider" }
            )
            append("_")
            append(System.currentTimeMillis())
        }
    }
}

private fun LlmProviderEntity.toDraft(): ApiWalletDraft {
    return ApiWalletDraft(
        id = id,
        name = name,
        endpoint = endpoint,
        apiKey = apiKey,
        modelName = modelName,
        enabled = enabled
    )
}
