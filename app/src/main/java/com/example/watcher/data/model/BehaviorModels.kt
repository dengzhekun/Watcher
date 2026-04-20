package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object BehaviorClaimStatuses {
    const val HYPOTHESIS = "hypothesis"
    const val EMERGING = "emerging"
    const val STABLE = "stable"
    const val STALE = "stale"
    const val CONFLICTED = "conflicted"

    val ordered = listOf(STABLE, EMERGING, HYPOTHESIS, STALE, CONFLICTED)

    fun displayNameOf(status: String): String = when (status) {
        STABLE -> "稳定"
        EMERGING -> "形成中"
        HYPOTHESIS -> "假设"
        STALE -> "待刷新"
        CONFLICTED -> "冲突"
        else -> status
    }
}

object ObservationGoalStatuses {
    const val ACTIVE = "active"
    const val RESOLVED = "resolved"
    const val EXPIRED = "expired"

    fun displayNameOf(status: String): String = when (status) {
        ACTIVE -> "进行中"
        RESOLVED -> "已解决"
        EXPIRED -> "已过期"
        else -> status
    }
}

@Entity(
    tableName = "behavior_claims",
    indices = [
        Index("sceneId"),
        Index("dimensionKey"),
        Index("status"),
        Index(value = ["sceneId", "dimensionKey", "claimText"], unique = true)
    ]
)
data class BehaviorClaim(
    @PrimaryKey val claimId: String = UUID.randomUUID().toString(),
    val sceneId: String? = null,
    val dimensionKey: String,
    val claimText: String,
    val status: String = BehaviorClaimStatuses.HYPOTHESIS,
    val confidenceScore: Float = 0.3f,
    val evidenceSummary: String = "",
    val evidenceCount: Int = 1,
    val firstObservedAt: Long = System.currentTimeMillis(),
    val lastObservedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "observation_goals",
    indices = [
        Index("sceneId"),
        Index("dimensionKey"),
        Index("status")
    ]
)
data class ObservationGoal(
    @PrimaryKey val goalId: String = UUID.randomUUID().toString(),
    val sceneId: String? = null,
    val dimensionKey: String,
    val question: String,
    val priority: Int = 1,
    val status: String = ObservationGoalStatuses.ACTIVE,
    val resolutionNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "behavior_reasoning_logs",
    indices = [
        Index("sceneId"),
        Index("dayDate"),
        Index("dimensionKey"),
        Index("createdAt")
    ]
)
data class BehaviorReasoningLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sceneId: String? = null,
    val dayDate: String,
    val dimensionKey: String,
    val content: String,
    val confidence: String,
    val basis: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
