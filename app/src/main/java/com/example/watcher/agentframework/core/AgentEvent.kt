package com.example.watcher.agentframework.core

sealed interface AgentEvent {
    val sessionId: String
    val timestamp: Long

    data class SessionStarted(
        override val sessionId: String,
        val agentId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class StepStarted(
        override val sessionId: String,
        val step: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class DecisionProduced(
        override val sessionId: String,
        val step: Int,
        val decision: AgentDecision,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class ReplyGenerated(
        override val sessionId: String,
        val step: Int,
        val reply: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class ToolCallStarted(
        override val sessionId: String,
        val step: Int,
        val call: AgentToolCall,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class ToolCallCompleted(
        override val sessionId: String,
        val step: Int,
        val result: AgentToolResult,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class Waiting(
        override val sessionId: String,
        val step: Int,
        val reason: String,
        val resumeAfterMillis: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class SessionFinished(
        override val sessionId: String,
        val status: AgentSessionStatus,
        val stopReason: AgentStopReason,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent

    data class SessionErrored(
        override val sessionId: String,
        val step: Int,
        val message: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent
}
