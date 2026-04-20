package com.example.watcher.agentframework.tools

import com.example.watcher.agentframework.core.AgentMemoryScope
import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.core.AgentToolParameter
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.agentframework.knowledge.AgentKnowledgeEntry
import com.example.watcher.agentframework.memory.AgentMemoryEntry

class ReadMemoryTool : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_memory",
        description = "Read the current invocation memory for this agent.",
        parameters = listOf(
            AgentToolParameter(
                name = "scope",
                type = "string",
                description = "Optional: working or episodic.",
                required = false
            ),
            AgentToolParameter(
                name = "limit",
                type = "integer",
                description = "Maximum number of entries to read.",
                required = false
            )
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val scope = parseScope(call.arguments["scope"])
        val limit = parseLimit(call.arguments["limit"], default = 20)
        val entries = context.memoryStore.read(context.session.sessionId, scope, limit)
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

class WriteMemoryTool : AgentTool {
    override val definition = AgentToolDefinition(
        name = "write_memory",
        description = "Write a memory entry for the current invocation.",
        parameters = listOf(
            AgentToolParameter(
                name = "scope",
                type = "string",
                description = "working or episodic.",
                required = true
            ),
            AgentToolParameter(
                name = "content",
                type = "string",
                description = "Memory content to store.",
                required = true
            ),
            AgentToolParameter(
                name = "tags",
                type = "array",
                description = "Optional memory tags.",
                required = false
            )
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val content = call.arguments["content"] as? String
            ?: return AgentToolResult(
                callId = call.id,
                toolName = definition.name,
                success = false,
                error = "Missing required argument: content"
            )
        val entry = AgentMemoryEntry(
            scope = parseScope(call.arguments["scope"]) ?: AgentMemoryScope.Working,
            content = content,
            tags = parseStringSet(call.arguments["tags"])
        )
        context.memoryStore.write(context.session.sessionId, entry)
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf("entry" to entry.toMap())
        )
    }
}

class ReadKnowledgeTool : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_knowledge",
        description = "Read recent long-term knowledge for this agent.",
        parameters = listOf(
            AgentToolParameter(
                name = "limit",
                type = "integer",
                description = "Maximum number of entries to read.",
                required = false
            )
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val limit = parseLimit(call.arguments["limit"], default = 20)
        val entries = context.knowledgeStore.read(context.definition.agentId, limit)
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

class QueryKnowledgeTool : AgentTool {
    override val definition = AgentToolDefinition(
        name = "query_knowledge",
        description = "Search long-term knowledge for this agent.",
        parameters = listOf(
            AgentToolParameter(
                name = "query",
                type = "string",
                description = "Search text.",
                required = false
            ),
            AgentToolParameter(
                name = "tags",
                type = "array",
                description = "Optional tags that must all match.",
                required = false
            ),
            AgentToolParameter(
                name = "limit",
                type = "integer",
                description = "Maximum number of entries to read.",
                required = false
            )
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val query = call.arguments["query"] as? String ?: ""
        val tags = parseStringSet(call.arguments["tags"])
        val limit = parseLimit(call.arguments["limit"], default = 10)
        val entries = context.knowledgeStore.query(context.definition.agentId, query, tags, limit)
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

class WriteKnowledgeTool : AgentTool {
    override val definition = AgentToolDefinition(
        name = "write_knowledge",
        description = "Write a long-term knowledge entry for this agent.",
        parameters = listOf(
            AgentToolParameter(
                name = "content",
                type = "string",
                description = "Knowledge content to store.",
                required = true
            ),
            AgentToolParameter(
                name = "tags",
                type = "array",
                description = "Optional knowledge tags.",
                required = false
            ),
            AgentToolParameter(
                name = "metadata",
                type = "object",
                description = "Optional string metadata.",
                required = false
            )
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val content = call.arguments["content"] as? String
            ?: return AgentToolResult(
                callId = call.id,
                toolName = definition.name,
                success = false,
                error = "Missing required argument: content"
            )
        val entry = AgentKnowledgeEntry(
            content = content,
            tags = parseStringSet(call.arguments["tags"]),
            metadata = parseStringMap(call.arguments["metadata"])
        )
        context.knowledgeStore.write(context.definition.agentId, entry)
        return AgentToolResult(
            callId = call.id,
            toolName = definition.name,
            success = true,
            output = mapOf("entry" to entry.toMap())
        )
    }
}

fun AgentToolRegistry.registerDefaultContextTools(): AgentToolRegistry {
    return register(ReadMemoryTool())
        .register(WriteMemoryTool())
        .register(ReadKnowledgeTool())
        .register(QueryKnowledgeTool())
        .register(WriteKnowledgeTool())
}

private fun parseScope(value: Any?): AgentMemoryScope? {
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
