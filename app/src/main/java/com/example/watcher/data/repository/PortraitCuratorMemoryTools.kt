package com.example.watcher.data.repository

import com.example.watcher.agentframework.core.AgentMemoryScope
import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.core.AgentToolParameter
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.agentframework.knowledge.AgentKnowledgeEntry
import com.example.watcher.agentframework.memory.AgentMemoryEntry
import com.example.watcher.agentframework.tools.AgentTool
import com.example.watcher.agentframework.tools.AgentToolContext

internal class CuratorReadMemoryTool(
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "curator_read_memory",
        description = "Read the current invocation memory for this agent.",
        parameters = listOf(
            AgentToolParameter("scope", "string", "Optional: working or episodic.", false),
            AgentToolParameter("limit", "integer", "Maximum number of entries to read.", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val scope = parseMemoryScope(call.arguments["scope"])
        val limit = parseLimit(call.arguments["limit"], default = 20)
        val entries = context.memoryStore.read(context.session.sessionId, scope, limit)
        maybeRecord(
            context = context,
            summary = "读取 session memory",
            detail = "scope=${scope?.name ?: "all"} count=${entries.size}"
        )
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf(
                "entries" to entries.map { it.toMap() },
                "count" to entries.size
            )
        )
    }

    private fun maybeRecord(context: AgentToolContext, summary: String, detail: String) {
        if (context.definition.agentId == PORTRAIT_CURATOR_AGENT_ID) {
            recordActivity("read", summary, detail, "success")
        }
    }
}

internal class CuratorWriteMemoryTool(
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "curator_write_memory",
        description = "Write a memory entry for the current invocation.",
        parameters = listOf(
            AgentToolParameter("scope", "string", "working or episodic.", true),
            AgentToolParameter("content", "string", "Memory content to store.", true),
            AgentToolParameter("tags", "array", "Optional memory tags.", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val content = call.arguments["content"] as? String
            ?: return AgentToolResult(call.id, definition.name, false, error = "Missing required argument: content")
        val scope = parseMemoryScope(call.arguments["scope"]) ?: AgentMemoryScope.Working
        val entry = AgentMemoryEntry(
            scope = scope,
            content = content,
            tags = parseStringSet(call.arguments["tags"])
        )
        context.memoryStore.write(context.session.sessionId, entry)
        if (context.definition.agentId == PORTRAIT_CURATOR_AGENT_ID) {
            recordActivity(
                "write",
                "写入 session memory",
                "scope=${scope.name.lowercase()} ${content.trim().replace("\n", " / ").take(180)}",
                "success"
            )
        }
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf("entry" to entry.toMap())
        )
    }
}

internal class CuratorReadKnowledgeTool(
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "curator_read_knowledge",
        description = "Read recent long-term knowledge for this agent.",
        parameters = listOf(
            AgentToolParameter("limit", "integer", "Maximum number of entries to read.", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val limit = parseLimit(call.arguments["limit"], default = 20)
        val entries = context.knowledgeStore.read(context.definition.agentId, limit)
        if (context.definition.agentId == PORTRAIT_CURATOR_AGENT_ID) {
            recordActivity("read", "读取长期 knowledge", "count=${entries.size}", "success")
        }
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf(
                "entries" to entries.map { it.toMap() },
                "count" to entries.size
            )
        )
    }
}

internal class CuratorQueryKnowledgeTool(
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "curator_query_knowledge",
        description = "Search long-term knowledge for this agent.",
        parameters = listOf(
            AgentToolParameter("query", "string", "Search text.", false),
            AgentToolParameter("tags", "array", "Optional tags that must all match.", false),
            AgentToolParameter("limit", "integer", "Maximum number of entries to read.", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val query = call.arguments["query"] as? String ?: ""
        val tags = parseStringSet(call.arguments["tags"])
        val limit = parseLimit(call.arguments["limit"], default = 10)
        val entries = context.knowledgeStore.query(context.definition.agentId, query, tags, limit)
        if (context.definition.agentId == PORTRAIT_CURATOR_AGENT_ID) {
            recordActivity(
                "read",
                "检索长期 knowledge",
                "query=${query.ifBlank { "-" }} tags=${tags.joinToString("|")} count=${entries.size}",
                "success"
            )
        }
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf(
                "entries" to entries.map { it.toMap() },
                "count" to entries.size
            )
        )
    }
}

internal class CuratorWriteKnowledgeTool(
    private val currentSceneLabelProvider: () -> String,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    companion object {
        private val ALLOWED_KNOWLEDGE_TAGS = setOf("modeling_rule", "lesson", "cross_scene_pattern")
    }

    override val definition = AgentToolDefinition(
        name = "curator_write_knowledge",
        description = "Write a long-term knowledge entry for this agent.",
        parameters = listOf(
            AgentToolParameter("content", "string", "Knowledge content to store.", true),
            AgentToolParameter("tags", "array", "Required: at least one of modeling_rule / lesson / cross_scene_pattern.", true),
            AgentToolParameter("metadata", "object", "Optional string metadata.", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val content = call.arguments["content"] as? String
            ?: return AgentToolResult(call.id, definition.name, false, error = "Missing required argument: content")
        val trimmedContent = content.trim()
        val tags = parseStringSet(call.arguments["tags"])
        val metadata = parseStringMap(call.arguments["metadata"])
        if (context.definition.agentId == PORTRAIT_CURATOR_AGENT_ID) {
            validateKnowledge(trimmedContent, currentSceneLabelProvider(), tags)?.let { error ->
                recordActivity("write", "拒绝写入长期 knowledge", error, "error")
                return AgentToolResult(call.id, definition.name, false, error = error)
            }
        }
        val entry = AgentKnowledgeEntry(
            content = trimmedContent,
            tags = tags,
            metadata = metadata
        )
        context.knowledgeStore.write(context.definition.agentId, entry)
        if (context.definition.agentId == PORTRAIT_CURATOR_AGENT_ID) {
            recordActivity(
                "write",
                "写入长期 knowledge",
                "${trimmedContent.replace("\n", " / ").take(180)}",
                "success"
            )
        }
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf("entry" to entry.toMap())
        )
    }

    private fun validateKnowledge(content: String, sceneLabel: String, tags: Set<String>): String? {
        val kindTags = tags.intersect(ALLOWED_KNOWLEDGE_TAGS)
        if (kindTags.isEmpty()) {
            return "Knowledge must include at least one tag in ${ALLOWED_KNOWLEDGE_TAGS.joinToString("/")}."
        }
        if (content.length < 18) {
            return "Knowledge content too short; keep only stable reusable lessons."
        }
        if (Regex("""\b\d{1,2}:\d{2}\b|[0-2]?\d点([0-5]\d分?)?""").containsMatchIn(content)) {
            return "Knowledge must not store scene-local time windows."
        }
        val localMarkers = listOf("当前场景", "本场景", "本次观察", "本轮观察", "这个工位", "这张桌子")
        if (localMarkers.any { content.contains(it) }) {
            return "Knowledge must be reusable across sessions, not scene-local observations."
        }
        if (sceneLabel.isNotBlank() && content.contains(sceneLabel)) {
            return "Knowledge must not mention the current scene label directly."
        }
        return null
    }
}

private fun parseMemoryScope(value: Any?): AgentMemoryScope? {
    return when ((value as? String)?.trim()?.lowercase()) {
        "working" -> AgentMemoryScope.Working
        "episodic" -> AgentMemoryScope.Episodic
        null, "" -> null
        else -> AgentMemoryScope.Working
    }
}

private fun parseLimit(value: Any?, default: Int): Int {
    val parsed = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
    return (parsed ?: default).coerceAtLeast(1)
}

private fun parseStringSet(value: Any?): Set<String> {
    val list = value as? List<*> ?: return emptySet()
    return list.mapNotNull { it as? String }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

private fun parseStringMap(value: Any?): Map<String, String> {
    val map = value as? Map<*, *> ?: return emptyMap()
    return map.entries.mapNotNull { entry ->
        val key = entry.key as? String ?: return@mapNotNull null
        val rawValue = entry.value ?: return@mapNotNull null
        key to rawValue.toString()
    }.toMap()
}

private fun AgentMemoryEntry.toMap(): Map<String, Any> {
    return mapOf(
        "scope" to scope.name.lowercase(),
        "content" to content,
        "tags" to tags.toList(),
        "createdAt" to createdAt
    )
}

private fun AgentKnowledgeEntry.toMap(): Map<String, Any> {
    return mapOf(
        "entryId" to entryId,
        "content" to content,
        "tags" to tags.toList(),
        "metadata" to metadata,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
