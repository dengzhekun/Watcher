package com.example.watcher.data.repository.context

import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.SceneEntity

data class SharedVisualContext(
    val sceneMemory: String,
    val entities: List<SceneEntity>,
    val actionSummary: String,
    val recentVisual: List<Pair<Long, String>>,
    val pendingRequests: List<String>
)

data class SharedSpeechContext(
    val recentSpeech: List<Pair<Long, String>>,
    val latestSpeechTimestamp: Long?
)

data class SharedMemoryContext(
    val memoryA: String,
    val memoryB: String,
    val rawBufferSize: Int
)

data class SharedSocialContext(
    val recentMessages: List<AiAudienceMessageEntity>,
    val pendingMentions: List<AiAudienceMessageEntity>,
    val activeAudienceNames: List<String>,
    val latestMentionTimestamp: Long?
)

data class SharedRuntimeContext(
    val snapshotTime: Long,
    val hasFrame: Boolean
)

data class LiveSharedContextSnapshot(
    val visual: SharedVisualContext,
    val speech: SharedSpeechContext,
    val memory: SharedMemoryContext,
    val social: SharedSocialContext,
    val runtime: SharedRuntimeContext
)
