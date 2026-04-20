package com.example.watcher.data.repository

import android.graphics.Bitmap
import android.util.Log
import androidx.room.withTransaction
import com.example.watcher.data.local.AppDatabase
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.local.SceneProfileDao
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.MatchBreakdown
import com.example.watcher.data.model.SceneAnalysisResult
import com.example.watcher.data.model.SceneProbeSnapshot
import com.example.watcher.data.model.SceneProfile
import com.example.watcher.data.model.SceneRecallResult
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoImageRequest
import com.example.watcher.data.remote.ImageContentItem
import com.example.watcher.data.remote.ImageMessage
import com.example.watcher.data.remote.extractOutputText
import java.util.Locale

class SceneProfileRepository(
    private val database: AppDatabase,
    private val sceneProfileDao: SceneProfileDao,
    private val behaviorModelDao: BehaviorModelDao,
    private val apiService: DoubaoApiService,
    private val llmWalletRepository: LlmWalletRepository
) {
    companion object {
        private const val TAG = "SceneProfileRepo"
        private const val MIN_SCENE_MATCH_SCORE = 2f
    }

    suspend fun analyzeScene(
        frame: Bitmap?,
        placeSnapshot: PlaceClusterManager.PlaceSnapshot?
    ): SceneAnalysisResult? {
        val bitmap = frame ?: return null
        val probe = summarizeScene(bitmap)?.copy(
            placeClusterId = placeSnapshot?.clusterId.orEmpty(),
            placeType = placeSnapshot?.placeType.orEmpty()
        ) ?: return null

        val profiles = sceneProfileDao.getAll()
        if (profiles.isEmpty()) {
            return SceneAnalysisResult(
                probe = probe,
                recallResult = null
            )
        }

        val scored = profiles.mapNotNull { profile ->
            val (score, breakdown) = scoreProfile(probe, profile)
            val meetsHardGate = breakdown.fixedOverlap.isNotEmpty() ||
                (breakdown.placeMatch && breakdown.spaceTypeMatch && score >= MIN_SCENE_MATCH_SCORE)
            if (!meetsHardGate || score < MIN_SCENE_MATCH_SCORE) return@mapNotNull null

            SceneRecallResult(
                profile = profile,
                confidence = (score / 6.5f).coerceIn(0f, 1f),
                probeSummary = probe.summary,
                matchedAnchors = breakdown.fixedOverlap,
                matchBreakdown = breakdown
            ) to score
        }

        val best = scored.maxByOrNull { it.second }?.first
        return SceneAnalysisResult(
            probe = probe,
            recallResult = best
        )
    }

    suspend fun saveOrUpdateProfile(
        existingSceneId: String?,
        sceneMemory: String,
        entitySummary: String,
        sceneObservations: List<String>,
        probe: SceneProbeSnapshot? = null
    ): SceneProfile? {
        val summary = sceneMemory.trim()
            .ifBlank { probe?.summary?.trim().orEmpty() }
            .ifBlank { return null }
        val anchors = probe?.fixedFeatures?.trim().orEmpty()
            .ifBlank { buildAnchorObjects(entitySummary, sceneObservations) }
        val layoutHints = buildLayoutHints(
            detailObjects = probe?.detailObjects.orEmpty(),
            sceneObservations = sceneObservations
        )
        val now = System.currentTimeMillis()
        val existing = if (existingSceneId.isNullOrBlank()) {
            null
        } else {
            sceneProfileDao.getById(existingSceneId)
        }

        val profile = if (existing != null) {
            existing.copy(
                summary = summary,
                anchorObjects = anchors.ifBlank { existing.anchorObjects },
                layoutHints = layoutHints.ifBlank { existing.layoutHints },
                stableEntities = entitySummary.take(300),
                placeClusterId = existing.placeClusterId.ifBlank { probe?.placeClusterId.orEmpty() },
                placeType = existing.placeType.ifBlank { probe?.placeType.orEmpty() },
                spaceType = probe?.spaceType?.trim().orEmpty().ifBlank { existing.spaceType },
                usageCount = existing.usageCount + 1,
                lastVerifiedAt = now,
                updatedAt = now
            )
        } else {
            SceneProfile(
                label = inferLabel(summary, anchors, probe?.spaceType.orEmpty()),
                summary = summary,
                anchorObjects = anchors,
                layoutHints = layoutHints,
                stableEntities = entitySummary.take(300),
                placeClusterId = probe?.placeClusterId.orEmpty(),
                placeType = probe?.placeType.orEmpty(),
                spaceType = probe?.spaceType.orEmpty(),
                usageCount = 1,
                lastVerifiedAt = now,
                updatedAt = now
            )
        }
        sceneProfileDao.upsert(profile)
        return profile
    }

    suspend fun createPlaceholderScene(probe: SceneProbeSnapshot? = null): SceneProfile {
        val now = System.currentTimeMillis()
        val profile = SceneProfile(
            label = probe?.spaceType?.trim().takeUnless { it.isNullOrBlank() } ?: "新场景",
            summary = probe?.summary?.trim().takeUnless { it.isNullOrBlank() } ?: "待补全的新场景",
            anchorObjects = probe?.fixedFeatures.orEmpty(),
            layoutHints = probe?.detailObjects.orEmpty(),
            stableEntities = "",
            placeClusterId = probe?.placeClusterId.orEmpty(),
            placeType = probe?.placeType.orEmpty(),
            spaceType = probe?.spaceType.orEmpty(),
            usageCount = 0,
            lastVerifiedAt = now,
            createdAt = now,
            updatedAt = now
        )
        sceneProfileDao.upsert(profile)
        return profile
    }

    suspend fun renameScene(sceneId: String, newLabel: String) {
        sceneProfileDao.updateUserLabel(
            sceneId = sceneId,
            userLabel = newLabel.trim().ifBlank { null },
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun mergeScenes(sourceSceneId: String, targetSceneId: String): SceneProfile? {
        if (sourceSceneId == targetSceneId) {
            return sceneProfileDao.getById(targetSceneId)
        }

        return database.withTransaction {
            val source = sceneProfileDao.getById(sourceSceneId) ?: return@withTransaction null
            val target = sceneProfileDao.getById(targetSceneId) ?: return@withTransaction null

            val affectedClaimKeys = linkedSetOf<Pair<String, String>>()

            val targetClaimsByKey = behaviorModelDao.getClaimsByScene(targetSceneId)
                .associateByTo(linkedMapOf()) { claimMergeKey(it.dimensionKey, it.claimText) }
            behaviorModelDao.getClaimsByScene(sourceSceneId).forEach { claim ->
                affectedClaimKeys += normalizeDimensionKey(claim.dimensionKey) to normalizeText(claim.claimText)
                val key = claimMergeKey(claim.dimensionKey, claim.claimText)
                val existing = targetClaimsByKey[key]
                if (existing != null) {
                    val merged = existing.copy(
                        status = higherStatus(existing.status, claim.status),
                        confidenceScore = maxOf(existing.confidenceScore, claim.confidenceScore),
                        evidenceSummary = mergeText(existing.evidenceSummary, claim.evidenceSummary, 220),
                        evidenceCount = existing.evidenceCount + claim.evidenceCount,
                        firstObservedAt = minOf(existing.firstObservedAt, claim.firstObservedAt),
                        lastObservedAt = maxOf(existing.lastObservedAt, claim.lastObservedAt),
                        updatedAt = System.currentTimeMillis()
                    )
                    behaviorModelDao.upsertClaim(merged)
                    behaviorModelDao.deleteClaimById(claim.claimId)
                    targetClaimsByKey[key] = merged
                }
            }
            behaviorModelDao.reassignClaimsScene(sourceSceneId, targetSceneId)

            val targetGoalsByKey = behaviorModelDao.getGoalsByScene(targetSceneId)
                .associateByTo(linkedMapOf()) { normalizeText(it.question) }
            behaviorModelDao.getGoalsByScene(sourceSceneId).forEach { goal ->
                val existing = targetGoalsByKey[normalizeText(goal.question)]
                if (existing != null) {
                    behaviorModelDao.upsertGoal(
                        existing.copy(
                            priority = maxOf(existing.priority, goal.priority),
                            dimensionKey = existing.dimensionKey.ifBlank { goal.dimensionKey },
                            resolutionNote = mergeText(existing.resolutionNote, goal.resolutionNote, 220),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    behaviorModelDao.deleteGoalById(goal.goalId)
                }
            }
            behaviorModelDao.reassignGoalsScene(sourceSceneId, targetSceneId)

            behaviorModelDao.reassignReasoningScene(sourceSceneId, targetSceneId)

            sceneProfileDao.upsert(
                target.copy(
                    summary = preferPrimary(target.summary, source.summary),
                    anchorObjects = mergeDelimited(target.anchorObjects, source.anchorObjects),
                    layoutHints = mergeDelimited(target.layoutHints, source.layoutHints),
                    stableEntities = mergeText(target.stableEntities, source.stableEntities, 300),
                    placeClusterId = target.placeClusterId.ifBlank { source.placeClusterId },
                    placeType = target.placeType.ifBlank { source.placeType },
                    spaceType = target.spaceType.ifBlank { source.spaceType },
                    usageCount = target.usageCount + source.usageCount,
                    lastVerifiedAt = maxOf(target.lastVerifiedAt, source.lastVerifiedAt),
                    updatedAt = System.currentTimeMillis()
                )
            )
            sceneProfileDao.deleteById(sourceSceneId)

            recalibrateUniversalClaims(affectedClaimKeys)

            sceneProfileDao.getById(targetSceneId)
        }
    }

    private suspend fun summarizeScene(bitmap: Bitmap): SceneProbeSnapshot? {
        return try {
            val llmConfig = llmWalletRepository.resolveArkResponsesConfig(ArkConfig.intentModel)
            val response = apiService.analyzeImage(
                authorization = llmConfig.bearerToken(),
                request = DoubaoImageRequest(
                    model = llmConfig.modelName,
                    input = listOf(
                        ImageMessage(
                            role = "system",
                            content = listOf(
                                ImageContentItem(
                                    type = "input_text",
                                    text = """
                                        你是场景识别助手。请只根据图片识别当前所处的空间。
                                        输出四行纯文本：
                                        SPACE: 空间类型短标签（如：办公工位、卧室、客厅、厨房、会议室、实验室、咖啡厅等）
                                        SUMMARY: 40字以内场景整体摘要
                                        FIXED: 3-5个固定不动的大件特征（家具、电器、墙面装饰等长期不变的物体），用中文顿号分隔
                                        DETAIL: 2-3个当前可见的桌面或手边小物品，用中文顿号分隔

                                        注意：FIXED 应选择不会因拍摄角度或日常整理而消失的物体，而非随手放置的杯子、文具。
                                    """.trimIndent()
                                )
                            )
                        ),
                        ImageMessage(
                            role = "user",
                            content = listOf(
                                ImageContentItem(type = "input_text", text = "当前画面："),
                                ImageContentItem(type = "input_image", imageUrl = BitmapEncoding.toDataUri(bitmap))
                            )
                        )
                    )
                )
            )
            parseProbe(response.extractOutputText()?.trim().orEmpty())
        } catch (error: Exception) {
            Log.w(TAG, "Failed to summarize scene: ${error.message}")
            null
        }
    }

    private fun parseProbe(raw: String): SceneProbeSnapshot? {
        if (raw.isBlank()) return null
        val lines = raw.lineSequence().toList()
        val spaceType = lines.firstOrNull { it.startsWith("SPACE:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()
        val label = lines.firstOrNull { it.startsWith("LABEL:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()
        val summary = lines.firstOrNull { it.startsWith("SUMMARY:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()
        val fixed = lines.firstOrNull { it.startsWith("FIXED:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()
        val detail = lines.firstOrNull { it.startsWith("DETAIL:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()
        val anchors = lines.firstOrNull { it.startsWith("ANCHORS:", ignoreCase = true) }
            ?.substringAfter(":")?.trim().orEmpty()

        val resolvedFixed = fixed.ifBlank { anchors }
        val resolvedSpaceType = spaceType.ifBlank { label.ifBlank { summary.take(8) } }
        if (summary.isBlank() && resolvedFixed.isBlank() && detail.isBlank()) return null

        return SceneProbeSnapshot(
            spaceType = resolvedSpaceType,
            summary = summary.ifBlank { raw.take(80) },
            fixedFeatures = resolvedFixed,
            detailObjects = detail
        )
    }

    private fun scoreProfile(
        probe: SceneProbeSnapshot,
        profile: SceneProfile
    ): Pair<Float, MatchBreakdown> {
        var score = 0f

        val placeMatch = probe.placeClusterId.isNotBlank() &&
            profile.placeClusterId.isNotBlank() &&
            probe.placeClusterId == profile.placeClusterId
        if (placeMatch) {
            score += 2.5f
        }

        val placeTypeMatch = probe.placeType.isNotBlank() &&
            profile.placeType.isNotBlank() &&
            probe.placeType == profile.placeType
        if (placeTypeMatch && !placeMatch) {
            score += 1.0f
        }

        val spaceTypeMatch = probe.spaceType.isNotBlank() &&
            profile.spaceType.isNotBlank() &&
            (
                probe.spaceType.contains(profile.spaceType, ignoreCase = true) ||
                    profile.spaceType.contains(probe.spaceType, ignoreCase = true)
                )
        if (spaceTypeMatch) {
            score += 1.5f
        }

        val probeFixed = tokenize(probe.fixedFeatures)
        val storedFixed = tokenize(profile.anchorObjects)
        val fixedOverlap = probeFixed.intersect(storedFixed.toSet()).toList()
        score += fixedOverlap.size * 1.0f

        val probeDetail = tokenize(probe.detailObjects)
        val storedAll = tokenize(profile.anchorObjects + " " + profile.layoutHints)
        val detailOverlap = probeDetail.intersect(storedAll.toSet()).toList()
        score += detailOverlap.size * 0.3f

        val labelBonus = if (
            profile.label.contains(probe.spaceType, ignoreCase = true) ||
            probe.spaceType.contains(profile.label, ignoreCase = true)
        ) {
            0.5f
        } else {
            0f
        }
        score += labelBonus

        return score to MatchBreakdown(
            placeMatch = placeMatch,
            placeTypeMatch = placeTypeMatch,
            spaceTypeMatch = spaceTypeMatch,
            fixedOverlap = fixedOverlap,
            detailOverlap = detailOverlap,
            totalScore = score
        )
    }

    private suspend fun recalibrateUniversalClaims(affectedClaimKeys: Set<Pair<String, String>>) {
        if (affectedClaimKeys.isEmpty()) return
        val universals = behaviorModelDao.getUniversalClaims().filter { claim ->
            normalizeDimensionKey(claim.dimensionKey) to normalizeText(claim.claimText) in affectedClaimKeys
        }
        universals.forEach { universal ->
            val normalizedDimension = normalizeDimensionKey(universal.dimensionKey)
            val sceneVersions = behaviorModelDao.getStableClaimsByText(universal.claimText)
                .filter { claim ->
                    claim.sceneId != null &&
                        normalizeDimensionKey(claim.dimensionKey) == normalizedDimension &&
                        claim.status == BehaviorClaimStatuses.STABLE
                }
            val distinctSceneIds = sceneVersions.mapNotNull { it.sceneId }.toSet()
            if (distinctSceneIds.size < 2) {
                behaviorModelDao.deleteClaimById(universal.claimId)
            } else {
                behaviorModelDao.upsertClaim(
                    universal.copy(
                        status = BehaviorClaimStatuses.STABLE,
                        confidenceScore = sceneVersions.maxOfOrNull { it.confidenceScore } ?: universal.confidenceScore,
                        evidenceSummary = "跨场景通用：在 ${distinctSceneIds.size} 个场景中稳定观察到",
                        evidenceCount = maxOf(universal.evidenceCount, distinctSceneIds.size),
                        lastObservedAt = sceneVersions.maxOfOrNull { it.lastObservedAt } ?: universal.lastObservedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun tokenize(raw: String): List<String> {
        return raw
            .lowercase(Locale.getDefault())
            .split('、', '，', ',', '；', ';', ' ', '\n', '\t', '。', ':', '：')
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
    }

    private fun buildAnchorObjects(entitySummary: String, sceneObservations: List<String>): String {
        val fromEntities = entitySummary
            .lineSequence()
            .map { it.substringBefore("：").substringBefore("|").trim() }
            .filter { it.length in 2..12 }
            .take(4)
            .toList()
        val fromObservations = sceneObservations
            .flatMap { observation ->
                observation.split('、', '，', ',', '；', ';', ' ')
                    .map { it.trim() }
                    .filter { token -> token.length in 2..8 }
            }
            .distinct()
            .take(6)
        return (fromEntities + fromObservations).distinct().take(8).joinToString("、")
    }

    private fun buildLayoutHints(detailObjects: String, sceneObservations: List<String>): String {
        val detailTokens = detailObjects.split('、', '，', ',', '；', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val observationTokens = sceneObservations
            .take(4)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return (detailTokens + observationTokens).distinct().joinToString("；").take(220)
    }

    private fun inferLabel(summary: String, anchors: String, spaceType: String): String {
        return spaceType.takeIf { it.isNotBlank() }
            ?: anchors.split('、').firstOrNull { it.isNotBlank() }
            ?: summary.take(8).ifBlank { "常用场景" }
    }

    private fun claimMergeKey(dimensionKey: String, claimText: String): String {
        return "${normalizeDimensionKey(dimensionKey)}|${normalizeText(claimText)}"
    }

    private fun normalizeDimensionKey(raw: String): String {
        return raw.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
    }

    private fun normalizeText(raw: String): String {
        return raw.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
    }

    private fun higherStatus(first: String, second: String): String {
        val rank = mapOf(
            BehaviorClaimStatuses.STABLE to 0,
            BehaviorClaimStatuses.EMERGING to 1,
            BehaviorClaimStatuses.HYPOTHESIS to 2,
            BehaviorClaimStatuses.STALE to 3,
            BehaviorClaimStatuses.CONFLICTED to 4
        )
        return if ((rank[first] ?: 99) <= (rank[second] ?: 99)) first else second
    }

    private fun mergeDelimited(first: String, second: String): String {
        return (first.split('、', '；') + second.split('、', '；'))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .joinToString("、")
    }

    private fun mergeText(first: String, second: String, limit: Int): String {
        return listOf(first, second)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("；")
            .take(limit)
    }

    private fun preferPrimary(primary: String, fallback: String): String {
        return primary.trim().ifBlank { fallback.trim() }
    }
}
