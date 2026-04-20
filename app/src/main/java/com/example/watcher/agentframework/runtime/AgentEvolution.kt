package com.example.watcher.agentframework.runtime

import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentDecision
import com.example.watcher.agentframework.core.AgentMemoryWrite
import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.core.ManagedAgentSnapshot
import com.example.watcher.agentframework.knowledge.AgentKnowledgeEntry
import com.example.watcher.agentframework.knowledge.AgentKnowledgeSnapshot
import com.example.watcher.agentframework.memory.AgentMemorySnapshot

data class AgentEvolutionRequest(
    val definition: AgentDefinition,
    val config: AgentRunConfig,
    val snapshot: ManagedAgentSnapshot,
    val latestDecision: AgentDecision,
    val memory: AgentMemorySnapshot,
    val knowledge: AgentKnowledgeSnapshot
)

data class AgentEvolutionResult(
    val updatedDefinition: AgentDefinition? = null,
    val updatedConfig: AgentRunConfig? = null,
    val knowledgeWrites: List<AgentKnowledgeEntry> = emptyList(),
    val memoryWrites: List<AgentMemoryWrite> = emptyList()
)

interface AgentEvolutionEngine {
    suspend fun evolve(request: AgentEvolutionRequest): AgentEvolutionResult?
}

class NoOpAgentEvolutionEngine : AgentEvolutionEngine {
    override suspend fun evolve(request: AgentEvolutionRequest): AgentEvolutionResult? = null
}
