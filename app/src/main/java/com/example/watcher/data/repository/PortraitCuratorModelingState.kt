package com.example.watcher.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PortraitCuratorModelingState {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, SessionState>()

    suspend fun markPreloaded(sessionId: String) {
        mutate(sessionId) { it.copy(workspaceReadCount = 1) }
    }

    suspend fun markWorkspaceRead(sessionId: String) {
        mutate(sessionId) { it.copy(workspaceReadCount = it.workspaceReadCount + 1) }
    }

    suspend fun markDimensionRead(sessionId: String, dimensionKey: String) {
        val normalized = normalize(dimensionKey)
        if (normalized.isBlank()) return
        mutate(sessionId) { state ->
            state.copy(
                workspaceReadCount = state.workspaceReadCount + 1,
                dimensionReads = state.dimensionReads + normalized
            )
        }
    }

    suspend fun guardForDimension(sessionId: String, dimensionKey: String): List<String> {
        val normalized = normalize(dimensionKey)
        val state = snapshot(sessionId)
        return buildList {
            if (state.workspaceReadCount <= 0) {
                add("No workspace context was read in this session before claim mutation.")
            }
            if (normalized.isNotBlank() && normalized !in state.dimensionReads) {
                add("Dimension '$normalized' was not inspected via read_claims_by_dimension before mutation.")
            }
        }
    }

    suspend fun clear(sessionId: String) {
        mutex.withLock {
            sessions.remove(sessionId)
        }
    }

    private suspend fun snapshot(sessionId: String): SessionState {
        return mutex.withLock {
            sessions[sessionId] ?: SessionState()
        }
    }

    private suspend fun mutate(sessionId: String, transform: (SessionState) -> SessionState) {
        mutex.withLock {
            val current = sessions[sessionId] ?: SessionState()
            sessions[sessionId] = transform(current)
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase()

    private data class SessionState(
        val workspaceReadCount: Int = 0,
        val dimensionReads: Set<String> = emptySet()
    )
}
