package com.example.watcher.agentframework.service

import com.example.watcher.agentframework.runtime.AgentBrain

interface AgentBrainFactory {
    val factoryId: String
    fun create(profile: RegisteredAgentProfile): AgentBrain
}

interface AgentBrainCatalog {
    val defaultFactoryId: String

    fun factories(): List<AgentBrainFactory>

    fun contains(factoryId: String): Boolean {
        return factories().any { it.factoryId == factoryId }
    }
}

class StaticAgentBrainCatalog(
    override val defaultFactoryId: String,
    private val registeredFactories: List<AgentBrainFactory>
) : AgentBrainCatalog {
    override fun factories(): List<AgentBrainFactory> = registeredFactories
}

interface AgentBrainConnectionTester {
    suspend fun testConnection(
        factoryId: String,
        metadata: Map<String, String> = emptyMap()
    ): String
}

class StaticAgentBrainFactory(
    override val factoryId: String,
    private val brain: AgentBrain
) : AgentBrainFactory {
    override fun create(profile: RegisteredAgentProfile): AgentBrain = brain
}
