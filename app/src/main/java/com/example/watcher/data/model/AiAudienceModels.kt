package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "llm_providers")
data class LlmProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val endpoint: String,
    val apiKey: String,
    val modelName: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_audiences")
data class AiAudienceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val audienceType: AudienceEngineType = AudienceEngineType.Agent,
    val persona: String,
    val socialArchetype: String = "",
    val speakingStyle: String = "",
    val spendingStyle: String = "",
    val socialDrive: String = "",
    val providerId: String,
    val enabled: Boolean = true,
    val heartbeatIntervalSeconds: Int = 15,
    val includeFrame: Boolean = false,
    val personalMemory: String = "",
    val agentStateJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AudienceEngineType(val label: String) {
    Classic("经典 AI"),
    Agent("Agent")
}

@Entity(
    tableName = "ai_audience_messages",
    foreignKeys = [ForeignKey(
        entity = AiAudienceEntity::class,
        parentColumns = ["id"],
        childColumns = ["audienceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("audienceId"), Index("timestamp")]
)
data class AiAudienceMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audienceId: Long,
    val audienceName: String,
    val content: String,
    val mentionedAudienceId: Long? = null,
    val mentionedAudienceName: String? = null,
    val triggerType: String = "heartbeat",
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

// In-memory UI state for live room
data class AiAudienceLiveState(
    val messages: List<AiAudienceMessageEntity> = emptyList(),
    val activeAudienceCount: Int = 0,
    val likeBoard: List<LikeRankEntry> = emptyList(), // top likers
    val lastLiker: String? = null,                     // most recent liker name
    val giftBoard: List<GiftRankEntry> = emptyList(),  // top gifters
    val recentGifts: List<GiftEvent> = emptyList()     // last 3 gift events
)

data class LikeRankEntry(val audienceName: String, val count: Int)
data class GiftRankEntry(val audienceName: String, val totalSpent: Int)

// Debug/management data per audience
data class AudienceDebugInfo(
    val audienceId: Long,
    val audienceName: String,
    val emotion: String?,
    val wallet: Int,
    val likeCount: Int,
    val lastPostContent: String?,   // full prompt sent to LLM last time
    val lastPostTime: Long?,
    val hasEntered: Boolean
)

data class AgentAudienceDebugSnapshot(
    val emotion: String,
    val emotionIntensity: Int,
    val currentGoal: String,
    val focusTarget: String,
    val lastActionSummary: String,
    val silenceStreak: Int,
    val hasEntered: Boolean,
    val socialArchetype: String,
    val speakingStyle: String,
    val spendingStyle: String,
    val socialDrive: String,
    val recentInteractionTargets: List<String>,
    val socialInbox: List<String>,
    val relationSummaries: List<String>
)

// Memory system snapshot
data class MemorySnapshot(
    val memoryA: String,
    val memoryB: String,
    val recentVisual: List<Pair<Long, String>>,
    val rawBufferSize: Int   // items pending B compression
)

// Danmaku item for floating overlay
data class DanmakuItem(
    val id: Long,
    val audienceName: String,
    val content: String,
    val timestamp: Long,
    val action: AudienceAction = AudienceAction.None
)

// Actions an AI audience can take
sealed class AudienceAction {
    data object None : AudienceAction()
    data object Like : AudienceAction()
    data class Gift(val gift: GiftType) : AudienceAction()
}

enum class GiftType(val displayName: String, val emoji: String, val cost: Int) {
    FLOWER("小花", "🌸", 1),
    ROCKET("火箭", "🚀", 10),
    HIGHLIGHT("醒目留言", "📢", 30),
    CROWN("皇冠", "👑", 50),
    SUPER_ROCKET("超级火箭", "🎆", 100);

    companion object {
        fun fromName(name: String): GiftType? = entries.find { name.contains(it.displayName) }
    }
}

data class GiftEvent(
    val audienceName: String,
    val gift: GiftType,
    val timestamp: Long
)
