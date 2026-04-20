package com.example.watcher.data.agent.core

/** Response from an agent backend — either tool calls or final answer. */
sealed interface AgentResponse {

    /** Agent wants to call tools before giving final answer. */
    data class ToolCalls(val calls: List<ToolCall>) : AgentResponse

    /** Agent's final analysis opinion. */
    data class FinalAnswer(val opinion: AgentOpinion) : AgentResponse
}

/** A single tool invocation requested by the agent. */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

/** Result of executing a tool call, fed back to the agent. */
data class ToolResult(
    val callId: String,
    val toolName: String,
    val result: Map<String, Any?>,
    val success: Boolean = true,
    val error: String? = null
)
