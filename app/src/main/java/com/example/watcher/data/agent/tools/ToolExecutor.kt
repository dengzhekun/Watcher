package com.example.watcher.data.agent.tools

import com.example.watcher.data.agent.core.AgentToolSchema
import com.example.watcher.data.agent.core.ToolCall
import com.example.watcher.data.agent.core.ToolResult

/** Registers and executes agent tools. */
class ToolExecutor {

    private val tools = mutableMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.schema.name] = tool
    }

    fun availableSchemas(): List<AgentToolSchema> = tools.values.map { it.schema }

    suspend fun execute(agentId: String, call: ToolCall): ToolResult {
        val tool = tools[call.name]
            ?: return ToolResult(call.id, call.name, emptyMap(), success = false, error = "Unknown tool: ${call.name}")
        return try {
            val result = tool.execute(agentId, call.arguments)
            ToolResult(call.id, call.name, result)
        } catch (e: Exception) {
            ToolResult(call.id, call.name, emptyMap(), success = false, error = e.message ?: "Tool execution failed")
        }
    }
}
