package com.example.watcher.data.agent.tools

import com.example.watcher.data.agent.core.AgentToolSchema
import com.example.watcher.data.agent.memory.AgentKnowledgeStore

/** Query this agent's persistent knowledge base. */
class KnowledgeQueryTool(
    private val knowledgeStore: AgentKnowledgeStore
) : AgentTool {

    override val schema = AgentToolSchema(
        name = "query_knowledge",
        description = "从你的持久知识库中检索与当前场景相关的经验和知识。",
        parameters = mapOf(
            "query" to param("string", "检索关键词或描述", required = true),
            "limit" to param("integer", "最大返回条数", default = 5)
        )
    )

    override suspend fun execute(agentId: String, arguments: Map<String, Any?>): Map<String, Any?> {
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 5
        val entries = knowledgeStore.query(agentId, limit)
        return mapOf("entries" to entries.map { mapOf("content" to it.content, "category" to it.category, "relevance" to it.relevance) })
    }
}

/** Write a new entry to this agent's persistent knowledge base. */
class KnowledgeWriteTool(
    private val knowledgeStore: AgentKnowledgeStore
) : AgentTool {

    override val schema = AgentToolSchema(
        name = "write_knowledge",
        description = "将一条值得跨会话记住的知识写入你的持久知识库。只写入有价值的经验、模式和事实，不要写入临时信息。",
        parameters = mapOf(
            "category" to param("string", "知识类别: expert_calibration(分析经验) 或 user_profile(用户画像)", required = true),
            "content" to param("string", "知识内容", required = true)
        )
    )

    override suspend fun execute(agentId: String, arguments: Map<String, Any?>): Map<String, Any?> {
        val category = arguments["category"] as? String ?: "expert_calibration"
        val content = arguments["content"] as? String ?: return mapOf("error" to "missing content")
        knowledgeStore.write(agentId, category, content)
        return mapOf("status" to "saved")
    }
}
