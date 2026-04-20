package com.example.watcher.agentframework.runtime

import com.example.watcher.agentframework.core.AgentInput
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentDecision
import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.core.AgentSessionSnapshot
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.knowledge.AgentKnowledgeSnapshot
import com.example.watcher.agentframework.memory.AgentMemorySnapshot

data class AgentBrainRequest(
    val definition: AgentDefinition,
    val config: AgentRunConfig,
    val session: AgentSessionSnapshot,
    val memory: AgentMemorySnapshot,
    val knowledge: AgentKnowledgeSnapshot,
    val recentInputs: List<AgentInput>,
    val availableTools: List<AgentToolDefinition>
)

interface AgentBrain {
    suspend fun decide(request: AgentBrainRequest): AgentDecision
}
