package com.example.watcher.agentframework.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AgentInvocationStore {
    suspend fun upsert(record: AgentInvocationRecord)
    suspend fun get(invocationId: String): AgentInvocationRecord?
    suspend fun list(): List<AgentInvocationRecord>
    suspend fun remove(invocationId: String)
}

interface AutonomousRuntimeRecordStore {
    suspend fun upsert(record: AutonomousAgentRuntimeRecord)
    suspend fun get(runtimeId: String): AutonomousAgentRuntimeRecord?
    suspend fun list(): List<AutonomousAgentRuntimeRecord>
    suspend fun remove(runtimeId: String)
}

class InMemoryAgentInvocationStore : AgentInvocationStore {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, AgentInvocationRecord>()

    override suspend fun upsert(record: AgentInvocationRecord) {
        mutex.withLock {
            records[record.invocationId] = record
        }
    }

    override suspend fun get(invocationId: String): AgentInvocationRecord? {
        return mutex.withLock { records[invocationId] }
    }

    override suspend fun list(): List<AgentInvocationRecord> {
        return mutex.withLock { records.values.toList() }
    }

    override suspend fun remove(invocationId: String) {
        mutex.withLock {
            records.remove(invocationId)
        }
    }
}

class InMemoryAutonomousRuntimeRecordStore : AutonomousRuntimeRecordStore {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, AutonomousAgentRuntimeRecord>()

    override suspend fun upsert(record: AutonomousAgentRuntimeRecord) {
        mutex.withLock {
            records[record.runtimeId] = record
        }
    }

    override suspend fun get(runtimeId: String): AutonomousAgentRuntimeRecord? {
        return mutex.withLock { records[runtimeId] }
    }

    override suspend fun list(): List<AutonomousAgentRuntimeRecord> {
        return mutex.withLock { records.values.toList() }
    }

    override suspend fun remove(runtimeId: String) {
        mutex.withLock {
            records.remove(runtimeId)
        }
    }
}
