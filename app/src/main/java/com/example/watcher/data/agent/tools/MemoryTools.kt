package com.example.watcher.data.agent.tools

import com.example.watcher.data.agent.core.AgentToolSchema
import com.example.watcher.data.agent.memory.AgentSessionMemory

/** Read this agent's session memory (analysis trajectory so far). */
class MemoryReadTool(
    private val sessionMemory: AgentSessionMemory
) : AgentTool {

    override val schema = AgentToolSchema(
        name = "read_memory",
        description = "读取你在本次直播会话中的分析轨迹记忆。",
        parameters = mapOf(
            "limit" to param("integer", "最大返回条数", default = 10)
        )
    )

    override suspend fun execute(agentId: String, arguments: Map<String, Any?>): Map<String, Any?> {
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 10
        return mapOf("entries" to sessionMemory.read(agentId, limit))
    }
}

/** Write an entry to this agent's session memory. */
class MemoryWriteTool(
    private val sessionMemory: AgentSessionMemory
) : AgentTool {

    override val schema = AgentToolSchema(
        name = "write_memory",
        description = "将一条观察或分析结论写入你的会话记忆，供后续轮次参考。",
        parameters = mapOf(
            "content" to param("string", "要记住的内容", required = true)
        )
    )

    override suspend fun execute(agentId: String, arguments: Map<String, Any?>): Map<String, Any?> {
        val content = arguments["content"] as? String ?: return mapOf("error" to "missing content")
        sessionMemory.write(agentId, content)
        return mapOf("status" to "saved")
    }
}
