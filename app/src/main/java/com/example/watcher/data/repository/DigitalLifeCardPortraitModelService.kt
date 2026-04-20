package com.example.watcher.data.repository

import android.util.Log
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.local.BlackboardDao
import com.example.watcher.data.local.SceneProfileDao
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.SceneProbeSnapshot
import com.example.watcher.data.model.SceneProfile
import java.util.Locale
import java.util.UUID

class DigitalLifeCardPortraitModelService(
    private val blackboardDao: BlackboardDao,
    private val behaviorModelDao: BehaviorModelDao,
    private val sceneProfileDao: SceneProfileDao,
    private val sceneProfileRepository: SceneProfileRepository,
    private val behaviorClaimConsolidator: BehaviorClaimConsolidator
) {
    companion object {
        private const val TAG = "DlcPortraitModelSvc"
    }

    suspend fun persistSceneProfileSnapshot(
        currentSceneId: String?,
        currentSceneProfileId: String?,
        commentaryState: LiveCommentaryState,
        date: String,
        probe: SceneProbeSnapshot?
    ): SceneProfile? {
        Log.d(TAG, "persistSceneProfileSnapshot start")
        val sceneObservations = blackboardDao
            .getObservationItemsByDate(date)
            .filter { it.category == com.example.watcher.data.model.BlackboardObservationCategories.SCENE }
            .map { it.content }
            .distinct()
        val updated = sceneProfileRepository.saveOrUpdateProfile(
            existingSceneId = currentSceneId ?: currentSceneProfileId,
            sceneMemory = commentaryState.sceneMemory,
            entitySummary = commentaryState.entityMemory,
            sceneObservations = sceneObservations,
            probe = probe
        )
        Log.d(TAG, "persistSceneProfileSnapshot end, sceneId=${updated?.sceneId}")
        return updated
    }

    suspend fun finalizeSceneSession(sceneId: String) {
        mergeSessionClaims(sceneId)
    }

    suspend fun clearSceneBehaviorModel(sceneId: String) {
        behaviorModelDao.deleteClaimsByScene(sceneId)
        behaviorModelDao.deleteGoalsByScene(sceneId)
        behaviorModelDao.deleteReasoningByScene(sceneId)
    }

    suspend fun runManualClaimConsolidation(
        sceneId: String,
        currentSceneId: String?,
        currentSceneLabel: String
    ): ConsolidationExecutionResult {
        val sceneLabel = sceneProfileDao.getById(sceneId)?.let(::displaySceneLabel)
            ?: if (currentSceneId == sceneId) currentSceneLabel else "未命名场景"
        val result = behaviorClaimConsolidator.consolidateSceneClaims(
            sceneId = sceneId,
            sceneLabel = sceneLabel
        )
        return ConsolidationExecutionResult(
            sceneLabel = sceneLabel,
            result = result
        )
    }

    suspend fun renameScene(sceneId: String, newLabel: String) {
        sceneProfileRepository.renameScene(sceneId, newLabel)
    }

    suspend fun mergeScenes(sourceSceneId: String, targetSceneId: String): SceneProfile? {
        return sceneProfileRepository.mergeScenes(sourceSceneId, targetSceneId)
    }

    suspend fun getSceneById(sceneId: String): SceneProfile? = sceneProfileDao.getById(sceneId)

    suspend fun resolveSceneLabel(sceneId: String, fallbackLabel: String = "未命名场景"): String {
        return sceneProfileDao.getById(sceneId)?.let(::displaySceneLabel) ?: fallbackLabel
    }

    fun displaySceneLabel(profile: SceneProfile): String {
        return profile.userLabel?.trim().takeUnless { it.isNullOrBlank() } ?: profile.label
    }

    private suspend fun mergeSessionClaims(sceneId: String) {
        Log.d(TAG, "mergeSessionClaims start, sceneId=$sceneId")
        val sceneClaims = behaviorModelDao.getClaimsByScene(sceneId)
        sceneClaims
            .filter { it.status == BehaviorClaimStatuses.STABLE }
            .forEach { claim ->
                val normalizedDimension = normalizeDimensionKey(claim.dimensionKey)
                val crossSceneClaims = behaviorModelDao.getStableClaimsByText(claim.claimText)
                    .filter { other ->
                        other.sceneId != null &&
                            other.sceneId != sceneId &&
                            normalizeDimensionKey(other.dimensionKey) == normalizedDimension
                    }
                val crossSceneIds = crossSceneClaims.mapNotNull { it.sceneId }.toSet()
                if (crossSceneIds.isEmpty()) return@forEach

                val universal = behaviorModelDao.getUniversalClaimByDimensionAndText(
                    dimensionKey = claim.dimensionKey,
                    claimText = claim.claimText
                )
                val totalSceneCount = crossSceneIds.size + 1
                val universalSummary = "跨场景通用：在 $totalSceneCount 个场景中稳定观察到"

                if (universal == null) {
                    behaviorModelDao.upsertClaim(
                        claim.copy(
                            claimId = UUID.randomUUID().toString(),
                            sceneId = null,
                            evidenceSummary = universalSummary,
                            evidenceCount = maxOf(claim.evidenceCount, totalSceneCount),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    behaviorModelDao.upsertClaim(
                        universal.copy(
                            status = BehaviorClaimStatuses.STABLE,
                            confidenceScore = maxOf(universal.confidenceScore, claim.confidenceScore),
                            evidenceSummary = universalSummary,
                            evidenceCount = maxOf(universal.evidenceCount, totalSceneCount),
                            lastObservedAt = maxOf(universal.lastObservedAt, claim.lastObservedAt),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        Log.d(
            TAG,
            "mergeSessionClaims end, sceneId=$sceneId, stableCount=${sceneClaims.count { it.status == BehaviorClaimStatuses.STABLE }}"
        )
    }

    private fun normalizeDimensionKey(raw: String): String {
        return raw.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
    }
}

data class ConsolidationExecutionResult(
    val sceneLabel: String,
    val result: ConsolidationResult
)
