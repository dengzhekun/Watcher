package com.example.watcher.data.repository

import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorClaimStatuses

internal const val PORTRAIT_CURATOR_AGENT_ID = "portrait_curator"

data class PortraitCuratorMemoryEntry(
    val scope: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L
)

data class PortraitCuratorMemoryDebugState(
    val runtimeId: String? = null,
    val workingEntries: List<PortraitCuratorMemoryEntry> = emptyList(),
    val episodicEntries: List<PortraitCuratorMemoryEntry> = emptyList(),
    val knowledgeEntries: List<PortraitCuratorMemoryEntry> = emptyList(),
    val structuredShortTermCount: Int = 0,
    val structuredWorkingCount: Int = 0,
    val structuredLongTermCount: Int = 0
)

internal fun fail(call: AgentToolCall, error: String) =
    AgentToolResult(call.id, call.name, false, error = error)

internal fun mergeEvidence(existing: String, incoming: String): String {
    return listOf(existing, incoming)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("；")
        .take(220)
}

internal fun parseOptionalFloat(raw: Any?): Float? {
    return when (raw) {
        is Number -> raw.toFloat()
        is String -> raw.toFloatOrNull()
        else -> null
    }
}

internal fun parseOptionalInt(raw: Any?): Int? {
    return when (raw) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    }
}

internal fun parseStringList(raw: Any?): List<String> {
    return when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }.distinct()
        is String -> raw
            .split("\n", "\\n", ",", "，")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        else -> emptyList()
    }
}

internal fun mergeStatuses(claims: List<BehaviorClaim>, plannedStatus: String): String {
    val candidates = buildList {
        addAll(claims.map { it.status })
        if (plannedStatus in BehaviorClaimStatuses.ordered) {
            add(plannedStatus)
        }
    }
    return candidates.minByOrNull(::statusRank) ?: BehaviorClaimStatuses.HYPOTHESIS
}

internal fun statusRank(status: String): Int {
    return when (status) {
        BehaviorClaimStatuses.STABLE -> 0
        BehaviorClaimStatuses.EMERGING -> 1
        BehaviorClaimStatuses.HYPOTHESIS -> 2
        BehaviorClaimStatuses.STALE -> 3
        BehaviorClaimStatuses.CONFLICTED -> 4
        else -> 99
    }
}
