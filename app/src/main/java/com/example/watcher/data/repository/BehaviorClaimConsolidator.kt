package com.example.watcher.data.repository

import androidx.room.withTransaction
import com.example.watcher.data.local.AppDatabase
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.BehaviorReasoningLog
import com.example.watcher.data.remote.ContentItem
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.Message
import com.example.watcher.data.remote.extractOutputText
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BehaviorClaimConsolidator(
    private val database: AppDatabase,
    private val apiService: DoubaoApiService,
    private val llmWalletRepository: LlmWalletRepository
) {
    private val dao: BehaviorModelDao = database.behaviorModelDao()
    private val gson = Gson()

    suspend fun consolidateSceneClaims(
        sceneId: String,
        sceneLabel: String,
        maxClaims: Int = 24
    ): ConsolidationResult {
        val sceneClaims = dao.getClaimsByScene(sceneId)
        if (sceneClaims.size < 2) {
            return ConsolidationResult(
                mergedCount = 0,
                summaries = emptyList(),
                reason = "not_enough_claims"
            )
        }

        val candidateClaims = sceneClaims
            .sortedWith(
                compareBy<BehaviorClaim>({ statusRank(it.status) }, { -it.evidenceCount }, { -it.confidenceScore })
            )
            .take(maxClaims.coerceIn(6, 40))

        val plan = requestRewritePlan(sceneLabel, candidateClaims)
        val currentClaimsById = sceneClaims.associateBy { it.claimId }
        val validPlans = plan?.normalizedClaims
            ?.mapNotNull { candidate -> candidate.toValidatedPlan(currentClaimsById) }
            .orEmpty()
            .dedupeBySourceClaims()

        if (validPlans.isEmpty()) {
            return ConsolidationResult(
                mergedCount = 0,
                summaries = emptyList(),
                reason = "no_merge_candidates"
            )
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return database.withTransaction {
            val claimsById = dao.getClaimsByScene(sceneId).associateBy { it.claimId }.toMutableMap()
            var rewrittenGroups = 0
            val rewriteSummaries = mutableListOf<String>()

            validPlans.forEach { planItem ->
                val sourceClaims = planItem.sourceClaimIds.mapNotNull { claimsById[it] }
                if (sourceClaims.size < 2) return@forEach

                val preferredPrimary = choosePrimaryClaim(sourceClaims) ?: return@forEach
                val canonicalExisting = dao.getClaimBySceneDimensionAndText(
                    sceneId,
                    planItem.canonicalDimensionKey,
                    planItem.canonicalClaimText
                )?.takeIf { it.claimId !in planItem.sourceClaimIds }

                val participatingClaims = buildList {
                    addAll(sourceClaims)
                    canonicalExisting?.let(::add)
                }
                val primary = canonicalExisting ?: preferredPrimary
                val rewrittenClaim = primary.copy(
                    sceneId = sceneId,
                    dimensionKey = planItem.canonicalDimensionKey,
                    claimText = planItem.canonicalClaimText,
                    status = mergeStatuses(participatingClaims, planItem.canonicalStatus),
                    confidenceScore = maxOf(
                        planItem.canonicalConfidenceScore.coerceIn(0f, 1f),
                        participatingClaims.maxOf { it.confidenceScore }
                    ),
                    evidenceSummary = mergeText(
                        values = participatingClaims.map { it.evidenceSummary } +
                            planItem.canonicalEvidenceSummary +
                            planItem.reason,
                        limit = 220
                    ),
                    evidenceCount = participatingClaims.sumOf { it.evidenceCount }.coerceAtLeast(1),
                    firstObservedAt = participatingClaims.minOf { it.firstObservedAt },
                    lastObservedAt = participatingClaims.maxOf { it.lastObservedAt },
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertClaim(rewrittenClaim)

                participatingClaims
                    .map { it.claimId }
                    .distinct()
                    .filter { it != rewrittenClaim.claimId }
                    .forEach { claimId ->
                        dao.deleteClaimById(claimId)
                        claimsById.remove(claimId)
                    }
                claimsById[rewrittenClaim.claimId] = rewrittenClaim

                dao.insertReasoningLog(
                    BehaviorReasoningLog(
                        sceneId = sceneId,
                        dayDate = today,
                        dimensionKey = rewrittenClaim.dimensionKey,
                        content = "Claim 重整：${sourceClaims.joinToString(" / ") { it.claimText }} -> ${rewrittenClaim.claimText}",
                        confidence = "high",
                        basis = planItem.reason.ifBlank { "同场景重复或近义 claim 已按全量重整计划收敛" }
                    )
                )
                rewrittenGroups += 1
                rewriteSummaries += "${sourceClaims.size}→1: ${rewrittenClaim.claimText}"
            }

            ConsolidationResult(
                mergedCount = rewrittenGroups,
                summaries = rewriteSummaries,
                reason = if (rewrittenGroups > 0) "merged" else "no_merge_candidates"
            )
        }
    }

    private suspend fun requestRewritePlan(
        sceneLabel: String,
        claims: List<BehaviorClaim>
    ): ClaimRewritePlan? {
        val llmConfig = llmWalletRepository.resolveArkResponsesConfig(ArkConfig.intentModel)
        val response = apiService.analyzeIntent(
            authorization = llmConfig.bearerToken(),
            request = DoubaoRequest(
                model = llmConfig.modelName,
                input = listOf(
                    Message(
                        role = "system",
                        content = listOf(
                            ContentItem(
                                type = "input_text",
                                text = """
                                    你是行为 claim 重整器。你的任务是基于同一场景下的一批原始 claims，输出一套更干净、更稳定、更少重复的规范化 claim 集。

                                    工作目标：
                                    1. 合并明显表达同一可观察事实的重复 claims。
                                    2. 允许同时重整 dimensionKey 和 claimText，使结果更稳定、更统一。
                                    3. 保留有价值的独立事实；不要为了简洁而删除其实不同的行为事实。
                                    4. 没把握就不要合并。

                                    强约束：
                                    1. 瞬时状态 与 长期习惯 不能合并。
                                    2. 人的动作习惯 与 环境事实/物品摆放 不能合并。
                                    3. 相反或冲突含义不能合并。
                                    4. 每个 normalized claim 必须引用至少 2 个 sourceClaimIds。
                                    5. 不要输出 markdown，不要解释性文字，只输出 JSON。
                                    6. 只输出你确信需要重整的项；未输出的原始 claims 将被系统原样保留。

                                    输出格式：
                                    {
                                      "normalizedClaims": [
                                        {
                                          "sourceClaimIds": ["id1", "id2"],
                                          "canonicalDimensionKey": "working_posture_pattern",
                                          "canonicalClaimText": "久坐工作时常以单手托腮",
                                          "canonicalStatus": "emerging",
                                          "canonicalConfidenceScore": 0.84,
                                          "canonicalEvidenceSummary": "多次观察到面对屏幕久坐时用单手支撑下巴",
                                          "reason": "同一工作姿势习惯的不同表述"
                                        }
                                      ]
                                    }

                                    如果没有需要重整的项，返回 {"normalizedClaims":[]}
                                """.trimIndent()
                            )
                        )
                    ),
                    Message(
                        role = "user",
                        content = listOf(
                            ContentItem(
                                type = "input_text",
                                text = buildString {
                                    appendLine("当前场景：$sceneLabel")
                                    appendLine("请基于以下原始 claims 做全量重整：")
                                    claims.forEachIndexed { index, claim ->
                                        appendLine(
                                            "${index + 1}. claimId=${claim.claimId}, " +
                                                "dimensionKey=${claim.dimensionKey}, " +
                                                "status=${claim.status}, " +
                                                "confidence=${"%.2f".format(Locale.US, claim.confidenceScore)}, " +
                                                "evidenceCount=${claim.evidenceCount}"
                                        )
                                        appendLine("claimText=${claim.claimText}")
                                        if (claim.evidenceSummary.isNotBlank()) {
                                            appendLine("evidenceSummary=${claim.evidenceSummary}")
                                        }
                                        appendLine()
                                    }
                                }
                            )
                        )
                    )
                )
            )
        )
        val raw = response.extractOutputText()?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            gson.fromJson(ModelOutputParser.extractJson(raw), ClaimRewritePlan::class.java)
        }.getOrNull()
    }

    private fun choosePrimaryClaim(claims: List<BehaviorClaim>): BehaviorClaim? {
        return claims.maxWithOrNull(
            compareBy<BehaviorClaim>({ -it.evidenceCount }, { -it.confidenceScore }, { statusRank(it.status) })
        )
    }

    private fun mergeStatuses(claims: List<BehaviorClaim>, plannedStatus: String): String {
        val candidates = buildList {
            addAll(claims.map { it.status })
            if (plannedStatus in BehaviorClaimStatuses.ordered) {
                add(plannedStatus)
            }
        }
        return candidates.minByOrNull(::statusRank) ?: BehaviorClaimStatuses.HYPOTHESIS
    }

    private fun mergeText(values: List<String>, limit: Int): String {
        return values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("；")
            .take(limit)
    }

    private fun statusRank(status: String): Int {
        return when (status) {
            BehaviorClaimStatuses.STABLE -> 0
            BehaviorClaimStatuses.EMERGING -> 1
            BehaviorClaimStatuses.HYPOTHESIS -> 2
            BehaviorClaimStatuses.STALE -> 3
            BehaviorClaimStatuses.CONFLICTED -> 4
            else -> 99
        }
    }
}

data class ConsolidationResult(
    val mergedCount: Int,
    val summaries: List<String>,
    val reason: String
)

private data class ClaimRewritePlan(
    val normalizedClaims: List<ClaimRewriteCandidate> = emptyList()
)

private data class ClaimRewriteCandidate(
    val sourceClaimIds: List<String> = emptyList(),
    val canonicalDimensionKey: String = "",
    val canonicalClaimText: String = "",
    val canonicalStatus: String = "",
    val canonicalConfidenceScore: Float = 0f,
    val canonicalEvidenceSummary: String = "",
    val reason: String = ""
)

private data class ValidatedRewritePlan(
    val sourceClaimIds: List<String>,
    val canonicalDimensionKey: String,
    val canonicalClaimText: String,
    val canonicalStatus: String,
    val canonicalConfidenceScore: Float,
    val canonicalEvidenceSummary: String,
    val reason: String
)

private fun ClaimRewriteCandidate.toValidatedPlan(
    claimsById: Map<String, BehaviorClaim>
): ValidatedRewritePlan? {
    val sourceIds = sourceClaimIds
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .filter { claimsById.containsKey(it) }
    if (sourceIds.size < 2) return null

    val canonicalDimensionKey = canonicalDimensionKey.trim()
    val canonicalClaimText = canonicalClaimText.trim()
    if (canonicalDimensionKey.isBlank() || canonicalClaimText.isBlank()) return null

    val canonicalStatus = canonicalStatus
        .takeIf { it in BehaviorClaimStatuses.ordered }
        ?: BehaviorClaimStatuses.HYPOTHESIS

    return ValidatedRewritePlan(
        sourceClaimIds = sourceIds,
        canonicalDimensionKey = canonicalDimensionKey,
        canonicalClaimText = canonicalClaimText,
        canonicalStatus = canonicalStatus,
        canonicalConfidenceScore = canonicalConfidenceScore.coerceIn(0f, 1f),
        canonicalEvidenceSummary = canonicalEvidenceSummary.trim(),
        reason = reason.trim()
    )
}

private fun List<ValidatedRewritePlan>.dedupeBySourceClaims(): List<ValidatedRewritePlan> {
    val usedClaimIds = mutableSetOf<String>()
    val accepted = mutableListOf<ValidatedRewritePlan>()
    for (item in this.sortedByDescending { it.sourceClaimIds.size }) {
        if (item.sourceClaimIds.any { it in usedClaimIds }) continue
        accepted += item
        usedClaimIds += item.sourceClaimIds
    }
    return accepted
}
