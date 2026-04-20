package com.example.watcher.data.agent.tools

import com.example.watcher.data.agent.core.AgentToolSchema
import com.example.watcher.data.agent.core.ToolParamSchema

/** Interface for all agent tools. Each tool defines its schema and execution logic. */
interface AgentTool {
    val schema: AgentToolSchema
    suspend fun execute(agentId: String, arguments: Map<String, Any?>): Map<String, Any?>
}

/** Convenience builder for tool parameters. */
fun param(type: String, description: String, required: Boolean = false, default: Any? = null) =
    ToolParamSchema(type, description, required, default)
