package com.example.watcher.agentframework.knowledge

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AgentKnowledgeEntry(
    val entryId: String = UUID.randomUUID().toString(),
    val content: String,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AgentKnowledgeSnapshot(
    val entries: List<AgentKnowledgeEntry>
)

interface AgentKnowledgeStore {
    suspend fun write(agentId: String, entry: AgentKnowledgeEntry)
    suspend fun read(agentId: String, limit: Int = 20): List<AgentKnowledgeEntry>
    suspend fun query(
        agentId: String,
        query: String,
        tags: Set<String> = emptySet(),
        limit: Int = 10
    ): List<AgentKnowledgeEntry>
    suspend fun remove(agentId: String, entryId: String): Boolean
    suspend fun clear(agentId: String)
}

class InMemoryAgentKnowledgeStore(
    private val maxEntriesPerAgent: Int = 500
) : AgentKnowledgeStore {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, MutableList<AgentKnowledgeEntry>>()

    override suspend fun write(agentId: String, entry: AgentKnowledgeEntry) {
        mutex.withLock {
            val entries = store.getOrPut(agentId) { mutableListOf() }
            entries += entry
            if (entries.size > maxEntriesPerAgent) {
                val excess = entries.size - maxEntriesPerAgent
                repeat(excess) { entries.removeAt(0) }
            }
        }
    }

    override suspend fun read(agentId: String, limit: Int): List<AgentKnowledgeEntry> {
        return mutex.withLock {
            store[agentId].orEmpty().takeLast(limit)
        }
    }

    override suspend fun query(
        agentId: String,
        query: String,
        tags: Set<String>,
        limit: Int
    ): List<AgentKnowledgeEntry> {
        val normalizedQuery = query.trim().lowercase()
        return mutex.withLock {
            store[agentId].orEmpty()
                .asSequence()
                .filter { entry ->
                    val tagMatch = tags.isEmpty() || tags.all { it in entry.tags }
                    val queryMatch = normalizedQuery.isBlank() ||
                        entry.content.lowercase().contains(normalizedQuery) ||
                        entry.metadata.values.any { it.lowercase().contains(normalizedQuery) }
                    tagMatch && queryMatch
                }
                .take(limit)
                .toList()
        }
    }

    override suspend fun remove(agentId: String, entryId: String): Boolean {
        return mutex.withLock {
            val entries = store[agentId] ?: return@withLock false
            val removed = entries.removeAll { it.entryId == entryId }
            if (entries.isEmpty()) {
                store.remove(agentId)
            }
            removed
        }
    }

    override suspend fun clear(agentId: String) {
        mutex.withLock {
            store.remove(agentId)
        }
    }
}
