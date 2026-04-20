package com.example.watcher.agentframework.memory

import com.example.watcher.agentframework.core.AgentMemoryScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AgentMemoryEntry(
    val scope: AgentMemoryScope,
    val content: String,
    val tags: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis()
)

data class AgentMemorySnapshot(
    val working: List<AgentMemoryEntry>,
    val episodic: List<AgentMemoryEntry>
)

interface AgentMemoryStore {
    suspend fun write(sessionId: String, entry: AgentMemoryEntry)
    suspend fun read(
        sessionId: String,
        scope: AgentMemoryScope? = null,
        limit: Int = 20
    ): List<AgentMemoryEntry>
    suspend fun clear(sessionId: String)
}

class InMemoryAgentMemoryStore(
    private val maxEntriesPerSession: Int = 1000
) : AgentMemoryStore {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, MutableList<AgentMemoryEntry>>()

    override suspend fun write(sessionId: String, entry: AgentMemoryEntry) {
        mutex.withLock {
            val entries = store.getOrPut(sessionId) { mutableListOf() }
            entries += entry
            if (entries.size > maxEntriesPerSession) {
                val excess = entries.size - maxEntriesPerSession
                repeat(excess) { entries.removeAt(0) }
            }
        }
    }

    override suspend fun read(
        sessionId: String,
        scope: AgentMemoryScope?,
        limit: Int
    ): List<AgentMemoryEntry> {
        return mutex.withLock {
            val entries = store[sessionId].orEmpty()
            val filtered = if (scope == null) {
                entries
            } else {
                entries.filter { it.scope == scope }
            }
            filtered.takeLast(limit)
        }
    }

    override suspend fun clear(sessionId: String) {
        mutex.withLock {
            store.remove(sessionId)
        }
    }
}
