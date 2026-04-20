package com.example.watcher.agentframework.service

import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.core.AgentSessionStatus
import com.example.watcher.agentframework.knowledge.AgentKnowledgeEntry
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentSessionSnapshot

data class AgentProfileEvolutionResult(
    val updatedDefinition: AgentDefinition? = null,
    val updatedConfig: AgentRunConfig? = null,
    val knowledgeWrites: List<AgentKnowledgeEntry> = emptyList()
)

interface AgentProfileEvolutionStrategy {
    suspend fun evolve(
        profile: RegisteredAgentProfile,
        snapshot: AgentSessionSnapshot
    ): AgentProfileEvolutionResult?
}

class HeuristicAgentProfileEvolutionStrategy : AgentProfileEvolutionStrategy {
    override suspend fun evolve(
        profile: RegisteredAgentProfile,
        snapshot: AgentSessionSnapshot
    ): AgentProfileEvolutionResult {
        val definition = profile.definition
        val metadata = definition.metadata.toMutableMap()
        val runCount = metadata["runCount"]?.toIntOrNull() ?: 0
        val successCount = metadata["successCount"]?.toIntOrNull() ?: 0
        val failureCount = metadata["failureCount"]?.toIntOrNull() ?: 0
        val completed = snapshot.status == AgentSessionStatus.Completed

        metadata["runCount"] = (runCount + 1).toString()
        metadata["successCount"] = if (completed) {
            (successCount + 1).toString()
        } else {
            successCount.toString()
        }
        metadata["failureCount"] = if (completed) {
            failureCount.toString()
        } else {
            (failureCount + 1).toString()
        }
        metadata["lastStopReason"] = snapshot.stopReason?.name.orEmpty()
        metadata["lastRunAt"] = System.currentTimeMillis().toString()

        return AgentProfileEvolutionResult(
            updatedDefinition = definition.copy(metadata = metadata),
            updatedConfig = profile.config,
            // A single execution summary is runtime telemetry, not durable knowledge.
            // Business knowledge should only be written explicitly when it is reusable.
            knowledgeWrites = emptyList()
        )
    }
}
