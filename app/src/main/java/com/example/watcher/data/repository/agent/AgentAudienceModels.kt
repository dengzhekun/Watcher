package com.example.watcher.data.repository.agent

import java.util.ArrayDeque

internal const val MAX_WORKING_MEMORY = 8
internal const val MAX_EPISODIC_MEMORY = 8
internal const val MAX_RELATIONS_IN_PROMPT = 6
internal const val MAX_SOCIAL_EVENTS = 8
internal const val MAX_RECENT_TARGETS = 5
internal const val PEER_LOOP_STREAK_LIMIT = 2

internal data class AgentSocialProfile(
    val archetype: String,
    val speakingStyle: String,
    val spendingStyle: String,
    val socialDrive: String
)

internal data class AgentSocialEvent(
    val source: String,
    val summary: String,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPromptString(): String = "[$eventType] $source: $summary"
}

internal data class AgentRelationState(
    var affinity: Int = 0,
    var familiarity: Int = 0,
    var tension: Int = 0,
    var note: String = ""
) {
    fun toPromptString(): String = buildString {
        append("好感$affinity")
        append(" 熟悉$familiarity")
        append(" 紧张$tension")
        if (note.isNotBlank()) append(" 备注:$note")
    }
}

internal data class AgentStateSnapshot(
    val emotion: String,
    val emotionIntensity: Int,
    val currentGoal: String,
    val focusTarget: String,
    val silenceStreak: Int,
    val socialProfile: AgentSocialProfile,
    val workingMemory: List<String>,
    val episodicMemory: List<String>,
    val socialInbox: List<AgentSocialEvent>,
    val relations: Map<String, AgentRelationState>
)

internal data class AgentRuntimeState(
    var emotion: String = "平静",
    var emotionIntensity: Int = 36,
    var currentGoal: String = "先观察直播间氛围",
    var focusTarget: String = "主播",
    var lastActionSummary: String = "尚未行动",
    var hasEntered: Boolean = false,
    var silenceStreak: Int = 0,
    var peerInteractionTarget: String = "",
    var peerInteractionStreak: Int = 0,
    val socialProfile: AgentSocialProfile = AgentSocialProfile(
        archetype = "普通观众",
        speakingStyle = "口语化、简短",
        spendingStyle = "谨慎消费",
        socialDrive = "先观察，再挑值得互动的对象"
    ),
    val workingMemory: ArrayDeque<String> = ArrayDeque(),
    val episodicMemory: ArrayDeque<String> = ArrayDeque(),
    val socialInbox: ArrayDeque<AgentSocialEvent> = ArrayDeque(),
    val recentInteractionTargets: ArrayDeque<String> = ArrayDeque(),
    val relations: MutableMap<String, AgentRelationState> = mutableMapOf(
        "主播" to AgentRelationState(affinity = 1, familiarity = 1, tension = 0, note = "直播间中心人物")
    )
) {
    fun rememberWorkingMemory(line: String) {
        if (line.isBlank()) return
        workingMemory.addLast(line.take(80))
        while (workingMemory.size > MAX_WORKING_MEMORY) {
            workingMemory.removeFirst()
        }
    }

    fun rememberEpisode(line: String) {
        if (line.isBlank()) return
        episodicMemory.addLast(line.take(100))
        while (episodicMemory.size > MAX_EPISODIC_MEMORY) {
            episodicMemory.removeFirst()
        }
    }

    fun pushSocialEvent(source: String, summary: String, eventType: String) {
        if (summary.isBlank()) return
        socialInbox.addLast(
            AgentSocialEvent(
                source = source.take(20),
                summary = summary.take(100),
                eventType = eventType.take(16)
            )
        )
        while (socialInbox.size > MAX_SOCIAL_EVENTS) {
            socialInbox.removeFirst()
        }
    }

    fun noteInteractionTarget(target: String) {
        if (target.isBlank()) return
        val normalized = target.take(20)
        recentInteractionTargets.remove(normalized)
        recentInteractionTargets.addLast(normalized)
        while (recentInteractionTargets.size > MAX_RECENT_TARGETS) {
            recentInteractionTargets.removeFirst()
        }
    }

    fun registerPeerInteraction(target: String) {
        val normalized = target.take(20)
        if (normalized.isBlank()) return
        if (peerInteractionTarget == normalized) {
            peerInteractionStreak = (peerInteractionStreak + 1).coerceAtMost(8)
        } else {
            peerInteractionTarget = normalized
            peerInteractionStreak = 1
        }
    }

    fun clearPeerInteractionLoop() {
        peerInteractionTarget = ""
        peerInteractionStreak = 0
    }

    fun isPeerLoopHot(target: String? = peerInteractionTarget): Boolean {
        val normalized = target?.takeIf { it.isNotBlank() } ?: return false
        return peerInteractionTarget == normalized && peerInteractionStreak >= PEER_LOOP_STREAK_LIMIT
    }

    fun ensureStreamerRelation() {
        relations.getOrPut("主播") {
            AgentRelationState(affinity = 1, familiarity = 1, tension = 0, note = "直播间中心人物")
        }
    }

    fun toSnapshot(): AgentStateSnapshot = AgentStateSnapshot(
        emotion = emotion,
        emotionIntensity = emotionIntensity,
        currentGoal = currentGoal,
        focusTarget = focusTarget,
        silenceStreak = silenceStreak,
        socialProfile = socialProfile,
        workingMemory = workingMemory.toList(),
        episodicMemory = episodicMemory.toList(),
        socialInbox = socialInbox.toList(),
        relations = relations.mapValues { (_, relation) -> relation.copy() }
    )
}

internal data class RelationUpdatePayload(
    val target: String? = null,
    val affinityDelta: Int = 0,
    val familiarityDelta: Int = 0,
    val tensionDelta: Int = 0,
    val note: String? = null
)

internal data class AgentResponsePayload(
    val speak: Boolean? = null,
    val content: String? = null,
    val emotion: String? = null,
    val emotionIntensity: Int? = null,
    val goal: String? = null,
    val focus: String? = null,
    val action: String? = null,
    val memory: String? = null,
    val relationUpdates: List<RelationUpdatePayload>? = null
)

internal data class AgentDecision(
    val speak: Boolean,
    val content: String?,
    val emotion: String,
    val emotionIntensity: Int,
    val goal: String,
    val focus: String,
    val action: String,
    val memory: String,
    val relationUpdates: List<RelationUpdatePayload>
)

internal data class PersistedAgentRelationState(
    val affinity: Int = 0,
    val familiarity: Int = 0,
    val tension: Int = 0,
    val note: String = ""
)

internal data class PersistedAgentSocialEvent(
    val source: String = "",
    val summary: String = "",
    val eventType: String = "",
    val timestamp: Long = 0L
)

internal data class PersistedAgentRuntimeState(
    val emotion: String = "平静",
    val emotionIntensity: Int = 36,
    val currentGoal: String = "先观察直播间氛围",
    val focusTarget: String = "主播",
    val lastActionSummary: String = "尚未行动",
    val hasEntered: Boolean = false,
    val silenceStreak: Int = 0,
    val peerInteractionTarget: String = "",
    val peerInteractionStreak: Int = 0,
    val socialProfile: AgentSocialProfile = AgentSocialProfile(
        archetype = "普通观众",
        speakingStyle = "口语化、简短",
        spendingStyle = "谨慎消费",
        socialDrive = "先观察，再挑值得互动的对象"
    ),
    val workingMemory: List<String> = emptyList(),
    val episodicMemory: List<String> = emptyList(),
    val socialInbox: List<PersistedAgentSocialEvent> = emptyList(),
    val recentInteractionTargets: List<String> = emptyList(),
    val relations: Map<String, PersistedAgentRelationState> = emptyMap()
)

internal data class PersistedAgentState(
    val emotion: String = "平静",
    val emotionIntensity: Int = 36,
    val currentGoal: String = "先观察直播间氛围",
    val focusTarget: String = "主播",
    val lastActionSummary: String = "尚未行动",
    val hasEntered: Boolean = false,
    val silenceStreak: Int = 0,
    val peerInteractionTarget: String = "",
    val peerInteractionStreak: Int = 0,
    val socialProfile: AgentSocialProfile = AgentSocialProfile(
        archetype = "普通观众",
        speakingStyle = "口语化、简短",
        spendingStyle = "谨慎消费",
        socialDrive = "先观察，再挑值得互动的对象"
    ),
    val workingMemory: List<String> = emptyList(),
    val episodicMemory: List<String> = emptyList(),
    val socialInbox: List<AgentSocialEvent> = emptyList(),
    val recentInteractionTargets: List<String> = emptyList(),
    val relations: Map<String, AgentRelationState> = mapOf(
        "主播" to AgentRelationState(affinity = 1, familiarity = 1, tension = 0, note = "直播间中心人物")
    )
)

internal fun AgentRuntimeState.toPersistedState(): PersistedAgentState = PersistedAgentState(
    emotion = emotion,
    emotionIntensity = emotionIntensity,
    currentGoal = currentGoal,
    focusTarget = focusTarget,
    lastActionSummary = lastActionSummary,
    hasEntered = hasEntered,
    silenceStreak = silenceStreak,
    peerInteractionTarget = peerInteractionTarget,
    peerInteractionStreak = peerInteractionStreak,
    socialProfile = socialProfile,
    workingMemory = workingMemory.toList(),
    episodicMemory = episodicMemory.toList(),
    socialInbox = socialInbox.toList(),
    recentInteractionTargets = recentInteractionTargets.toList(),
    relations = relations.mapValues { (_, relation) -> relation.copy() }
)

internal fun PersistedAgentRuntimeState.toRuntimeState(): AgentRuntimeState = AgentRuntimeState(
    emotion = emotion,
    emotionIntensity = emotionIntensity,
    currentGoal = currentGoal,
    focusTarget = focusTarget,
    lastActionSummary = lastActionSummary,
    hasEntered = hasEntered,
    silenceStreak = silenceStreak,
    peerInteractionTarget = peerInteractionTarget,
    peerInteractionStreak = peerInteractionStreak,
    socialProfile = socialProfile,
    workingMemory = ArrayDeque(workingMemory),
    episodicMemory = ArrayDeque(episodicMemory),
    socialInbox = ArrayDeque(socialInbox.map { it.toRuntimeEvent() }),
    recentInteractionTargets = ArrayDeque(recentInteractionTargets),
    relations = relations.mapValues { (_, relation) -> relation.toRuntimeState() }.toMutableMap()
).also {
    it.ensureStreamerRelation()
}

internal fun PersistedAgentState.toRuntimeState(): AgentRuntimeState = AgentRuntimeState(
    emotion = emotion,
    emotionIntensity = emotionIntensity,
    currentGoal = currentGoal,
    focusTarget = focusTarget,
    lastActionSummary = lastActionSummary,
    hasEntered = hasEntered,
    silenceStreak = silenceStreak,
    peerInteractionTarget = peerInteractionTarget,
    peerInteractionStreak = peerInteractionStreak,
    socialProfile = socialProfile,
    workingMemory = ArrayDeque(workingMemory),
    episodicMemory = ArrayDeque(episodicMemory),
    socialInbox = ArrayDeque(socialInbox),
    recentInteractionTargets = ArrayDeque(recentInteractionTargets),
    relations = relations.toMutableMap()
).also {
    it.ensureStreamerRelation()
}

private fun PersistedAgentSocialEvent.toRuntimeEvent(): AgentSocialEvent = AgentSocialEvent(
    source = source,
    summary = summary,
    eventType = eventType,
    timestamp = timestamp
)

private fun PersistedAgentRelationState.toRuntimeState(): AgentRelationState = AgentRelationState(
    affinity = affinity,
    familiarity = familiarity,
    tension = tension,
    note = note
)
