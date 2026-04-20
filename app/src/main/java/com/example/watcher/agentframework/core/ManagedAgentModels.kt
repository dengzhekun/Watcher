package com.example.watcher.agentframework.core

enum class AgentInputKind {
    UserMessage,
    Observation,
    GoalUpdate,
    SystemDirective
}

data class AgentInput(
    val kind: AgentInputKind,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class ManagedAgentLifecycleState {
    Created,
    Running,
    Waiting,
    Idle,
    Stopped,
    Failed
}

data class ManagedAgentSnapshot(
    val agentId: String,
    val sessionId: String,
    val definition: AgentDefinition,
    val config: AgentRunConfig,
    val lifecycleState: ManagedAgentLifecycleState = ManagedAgentLifecycleState.Created,
    val activeGoal: String = definition.goal,
    val cycleCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastReply: String? = null,
    val lastDecision: AgentDecision? = null,
    val pendingInputs: List<AgentInput> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
