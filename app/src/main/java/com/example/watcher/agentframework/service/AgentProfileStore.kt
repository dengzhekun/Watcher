package com.example.watcher.agentframework.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AgentProfileStore {
    suspend fun upsert(profile: RegisteredAgentProfile)
    suspend fun get(agentId: String): RegisteredAgentProfile?
    suspend fun list(): List<RegisteredAgentProfile>
    suspend fun remove(agentId: String)
}

class InMemoryAgentProfileStore : AgentProfileStore {
    private val mutex = Mutex()
    private val profiles = linkedMapOf<String, RegisteredAgentProfile>()

    override suspend fun upsert(profile: RegisteredAgentProfile) {
        mutex.withLock {
            profiles[profile.agentId] = profile
        }
    }

    override suspend fun get(agentId: String): RegisteredAgentProfile? {
        return mutex.withLock { profiles[agentId] }
    }

    override suspend fun list(): List<RegisteredAgentProfile> {
        return mutex.withLock { profiles.values.toList() }
    }

    override suspend fun remove(agentId: String) {
        mutex.withLock {
            profiles.remove(agentId)
        }
    }
}

