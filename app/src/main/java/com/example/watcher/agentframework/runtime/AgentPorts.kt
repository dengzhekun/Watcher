package com.example.watcher.agentframework.runtime

import com.example.watcher.agentframework.core.AgentInput
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AgentOutput(
    val outputId: String = UUID.randomUUID().toString(),
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

interface AgentInputPort {
    suspend fun submit(input: AgentInput)
}

interface AgentOutputPort {
    suspend fun publish(output: AgentOutput)
    suspend fun readAll(): List<AgentOutput>
    suspend fun clear()
}

class InMemoryAgentOutputPort : AgentOutputPort {
    private val mutex = Mutex()
    private val outputs = mutableListOf<AgentOutput>()

    override suspend fun publish(output: AgentOutput) {
        mutex.withLock {
            outputs += output
        }
    }

    override suspend fun readAll(): List<AgentOutput> {
        return mutex.withLock { outputs.toList() }
    }

    override suspend fun clear() {
        mutex.withLock {
            outputs.clear()
        }
    }
}
