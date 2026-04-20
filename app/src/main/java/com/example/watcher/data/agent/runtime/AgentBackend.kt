package com.example.watcher.data.agent.runtime

import com.example.watcher.data.agent.core.AgentMessage
import com.example.watcher.data.agent.core.AgentResponse
import com.example.watcher.data.agent.core.AgentToolSchema

/**
 * Pluggable backend — the "brain" of an agent. Both LLM and HTTP backends
 * implement the same interface. The runtime doesn't know or care what's behind it.
 */
interface AgentBackend {
    /**
     * Send messages to the backend and receive either tool calls or final answer.
     * @param systemPrompt The agent's system-level instructions
     * @param messages Conversation history (user context + previous tool results)
     * @param tools Available tool schemas the agent can call
     */
    suspend fun call(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentToolSchema>
    ): AgentResponse
}
