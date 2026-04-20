package com.example.watcher.data.local.litert

import com.example.watcher.agentframework.core.AgentConversationItem
import com.example.watcher.agentframework.core.AgentMessageRole
import com.example.watcher.agentframework.runtime.AgentBrain
import com.example.watcher.agentframework.runtime.AgentModelGateway
import com.example.watcher.agentframework.runtime.JsonProtocolAgentBrain
import com.example.watcher.agentframework.service.AgentBrainConnectionTester
import com.example.watcher.agentframework.service.AgentBrainFactory
import com.example.watcher.agentframework.service.RegisteredAgentProfile
import com.example.watcher.data.remote.ChatMessage

const val LITERT_BRAIN_FACTORY_ID = "litert_local_brain"

class LiteRtAgentBrainFactory(
    private val liteRtProvider: LiteRtLlmProvider
) : AgentBrainFactory {
    override val factoryId: String = LITERT_BRAIN_FACTORY_ID

    override fun create(profile: RegisteredAgentProfile): AgentBrain {
        return JsonProtocolAgentBrain(LiteRtAgentModelGateway(liteRtProvider))
    }
}

private class LiteRtAgentModelGateway(
    private val provider: LiteRtLlmProvider
) : AgentModelGateway {
    override suspend fun generate(
        systemPrompt: String,
        messages: List<AgentConversationItem>
    ): String {
        return provider.chat(
            systemPrompt = systemPrompt,
            messages = messages.map { it.toChatMessage() }
        )
    }

    private fun AgentConversationItem.toChatMessage(): ChatMessage {
        val mappedRole = when (role) {
            AgentMessageRole.Assistant -> "assistant"
            AgentMessageRole.User -> "user"
            AgentMessageRole.System -> "user"
            AgentMessageRole.Tool -> "user"
            AgentMessageRole.Observation -> "user"
        }
        return ChatMessage(role = mappedRole, content = content)
    }
}

class LiteRtConnectionTester(
    private val liteRtProvider: LiteRtLlmProvider,
    private val engineManager: LiteRtEngineManager
) : AgentBrainConnectionTester {
    override suspend fun testConnection(
        factoryId: String,
        metadata: Map<String, String>
    ): String {
        require(factoryId == LITERT_BRAIN_FACTORY_ID) {
            "Unsupported brain factory for connection test: $factoryId"
        }
        check(engineManager.isReady()) { "Local model is not initialized" }
        return liteRtProvider.chat(
            systemPrompt = "You are a connectivity check. Reply briefly.",
            messages = listOf(ChatMessage(role = "user", content = "Connection test"))
        ).trim()
    }
}

class CompositeAgentBrainConnectionTester(
    private val testers: Map<String, AgentBrainConnectionTester>
) : AgentBrainConnectionTester {
    override suspend fun testConnection(
        factoryId: String,
        metadata: Map<String, String>
    ): String {
        val tester = testers[factoryId]
            ?: throw IllegalArgumentException("No connection tester for factory: $factoryId")
        return tester.testConnection(factoryId, metadata)
    }
}
