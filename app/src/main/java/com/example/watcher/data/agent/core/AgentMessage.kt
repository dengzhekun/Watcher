package com.example.watcher.data.agent.core

/** A single message in the agent conversation (system/user/assistant/tool_result). */
data class AgentMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL_RESULT = "tool_result"

        fun system(content: String) = AgentMessage(ROLE_SYSTEM, content)
        fun user(content: String) = AgentMessage(ROLE_USER, content)
        fun assistant(content: String) = AgentMessage(ROLE_ASSISTANT, content)
        fun toolResult(callId: String, content: String) = AgentMessage(ROLE_TOOL_RESULT, content, callId)
    }
}
