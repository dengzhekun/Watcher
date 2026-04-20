package com.example.watcher.agentframework.integration

import com.example.watcher.agentframework.core.AgentConversationItem
import com.example.watcher.agentframework.core.AgentMessageRole
import com.example.watcher.agentframework.runtime.AgentBrain
import com.example.watcher.agentframework.runtime.AgentModelGateway
import com.example.watcher.agentframework.runtime.JsonProtocolAgentBrain
import com.example.watcher.agentframework.service.AgentBrainFactory
import com.example.watcher.agentframework.service.AgentBrainConnectionTester
import com.example.watcher.agentframework.service.RegisteredAgentProfile
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.example.watcher.data.repository.ArkConfig
import com.example.watcher.data.repository.LlmWalletRepository

const val APP_DEFAULT_LLM_BRAIN_FACTORY_ID = "app_default_llm_brain"

class AppDefaultAgentBrainFactory(
    private val llmWalletRepository: LlmWalletRepository
) : AgentBrainFactory {
    override val factoryId: String = APP_DEFAULT_LLM_BRAIN_FACTORY_ID

    override fun create(profile: RegisteredAgentProfile): AgentBrain = newBrain(
        profile.readAppAgentBrainProfileConfig()
    )

    fun newBrain(
        config: AppAgentBrainProfileConfig = AppAgentBrainProfileConfig()
    ): AgentBrain {
        return JsonProtocolAgentBrain(DefaultAgentModelGateway(llmWalletRepository, config))
    }

    suspend fun testConnection(
        config: AppAgentBrainProfileConfig = AppAgentBrainProfileConfig()
    ): String {
        val provider = resolveProvider(llmWalletRepository, config)
        return provider.chat(
            systemPrompt = "You are a connectivity check. Reply with a short acknowledgement.",
            messages = listOf(ChatMessage(role = "user", content = "Connection test"))
        ).trim()
    }
}

private class DefaultAgentModelGateway(
    private val llmWalletRepository: LlmWalletRepository,
    private val config: AppAgentBrainProfileConfig
) : AgentModelGateway {
    override suspend fun generate(
        systemPrompt: String,
        messages: List<AgentConversationItem>
    ): String {
        val provider = resolveProvider(llmWalletRepository, config)
        return provider.chat(
            systemPrompt = systemPrompt,
            messages = messages.map { it.toChatMessage() }
        )
    }
}

class AppAgentBrainConnectionTester(
    private val defaultBrainFactory: AppDefaultAgentBrainFactory
) : AgentBrainConnectionTester {
    override suspend fun testConnection(factoryId: String, metadata: Map<String, String>): String {
        require(factoryId == APP_DEFAULT_LLM_BRAIN_FACTORY_ID) {
            "Unsupported brain factory for connection test: $factoryId"
        }
        return defaultBrainFactory.testConnection(metadata.toAppAgentBrainProfileConfig())
    }
}

private suspend fun resolveProvider(
    llmWalletRepository: LlmWalletRepository,
    config: AppAgentBrainProfileConfig
): OpenAiCompatibleProvider {
    config.validate()
    if (config.providerId.isNotBlank()) {
        val savedProvider = llmWalletRepository.getProviderById(config.providerId)
        check(savedProvider != null) {
            "Saved brain not found: ${config.providerId}"
        }
        return savedProvider.toLlm()
    }
    if (config.hasCustomConnection()) {
        val fallbackModel = llmWalletRepository.resolvePreferredModel(fallbackModel = ArkConfig.intentModel)
        val resolvedModel = config.modelName.ifBlank { fallbackModel }
        check(resolvedModel.isNotBlank()) {
            "Brain model is required when no default model is available."
        }
        return OpenAiCompatibleProvider(
            id = "agent_custom_provider",
            displayName = config.displayName.ifBlank { "Agent Custom Brain" },
            endpoint = config.endpoint.trim(),
            apiKey = config.apiKey.trim(),
            modelName = resolvedModel
        )
    }
    return llmWalletRepository.resolveOpenAiProvider(fallbackModel = ArkConfig.intentModel)
}

private fun LlmProviderEntity.toLlm(): OpenAiCompatibleProvider {
    return OpenAiCompatibleProvider(
        id = id,
        displayName = name,
        endpoint = endpoint,
        apiKey = apiKey,
        modelName = modelName
    )
}

private fun AgentConversationItem.toChatMessage(): ChatMessage {
    val role = when (role) {
        AgentMessageRole.Assistant -> "assistant"
        AgentMessageRole.User -> "user"
        AgentMessageRole.System -> "user"
        AgentMessageRole.Tool -> "user"
        AgentMessageRole.Observation -> "user"
    }
    return ChatMessage(role = role, content = content)
}
