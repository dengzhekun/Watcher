package com.example.watcher.data.repository.context

import android.graphics.Bitmap
import com.example.watcher.data.local.AiAudienceMessageDao
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.EntityStatus
import com.example.watcher.data.repository.CommentaryMemoryManager
import com.example.watcher.data.repository.SceneMemoryManager

class LiveSharedContextProvider(
    private val messageDao: AiAudienceMessageDao,
    private val memoryManager: CommentaryMemoryManager,
    private val sceneMemoryManager: SceneMemoryManager
) {
    private var frameProvider: (() -> Bitmap?)? = null
    private var speechProvider: (() -> List<Pair<Long, String>>)? = null

    fun updateProviders(
        frameProvider: (() -> Bitmap?)?,
        speechProvider: (() -> List<Pair<Long, String>>)?
    ) {
        this.frameProvider = frameProvider
        this.speechProvider = speechProvider
    }

    suspend fun getSnapshot(
        audience: AiAudienceEntity?,
        activeAudienceNames: List<String>,
        mentionSince: Long,
        profile: LiveSharedContextProfile,
        now: Long = System.currentTimeMillis()
    ): LiveSharedContextSnapshot {
        val recentMessages = if (profile.recentMessagesLimit > 0) {
            messageDao.getRecent(profile.recentMessagesLimit).reversed()
        } else {
            emptyList()
        }
        val pendingMentions = audience
            ?.takeIf { profile.mentionLimit > 0 }
            ?.let { messageDao.getPendingMentions(it.id, mentionSince) }
            .orEmpty()
            .takeLast(profile.mentionLimit)
        val recentSpeech = speechProvider?.invoke()
            ?.filter { (timestamp, text) ->
                timestamp >= now - profile.speechWindowMs && text.isNotBlank()
            }
            ?.take(profile.recentSpeechLimit)
            .orEmpty()
        val activeEntities = sceneMemoryManager.entities.values
            .filter { it.status == EntityStatus.ACTIVE }
            .sortedBy { it.name }

        return LiveSharedContextSnapshot(
            visual = SharedVisualContext(
                sceneMemory = sceneMemoryManager.sceneMemory,
                entities = activeEntities,
                actionSummary = sceneMemoryManager.actionSummary,
                recentVisual = memoryManager.recentVisual.takeLast(profile.recentVisualLimit),
                pendingRequests = sceneMemoryManager.getPendingRequests()
            ),
            speech = SharedSpeechContext(
                recentSpeech = recentSpeech,
                latestSpeechTimestamp = recentSpeech.maxOfOrNull { it.first }
            ),
            memory = SharedMemoryContext(
                memoryA = memoryManager.memoryA,
                memoryB = memoryManager.latestMemoryB,
                rawBufferSize = memoryManager.rawSinceLastB.size
            ),
            social = SharedSocialContext(
                recentMessages = recentMessages,
                pendingMentions = pendingMentions,
                activeAudienceNames = activeAudienceNames,
                latestMentionTimestamp = pendingMentions.maxOfOrNull { it.timestamp }
            ),
            runtime = SharedRuntimeContext(
                snapshotTime = now,
                hasFrame = frameProvider?.invoke() != null
            )
        )
    }
}
