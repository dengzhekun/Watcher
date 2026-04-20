package com.example.watcher.data.repository

import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.local.BlackboardDao
import com.example.watcher.data.local.SceneProfileDao
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.MatchBreakdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class PortraitWorkspaceMemorySnapshot(
    val sceneId: String?,
    val sceneLabel: String,
    val sceneSummary: String,
    val entitySummary: String,
    val actionSummary: String,
    val scenePhase: String,
    val placeClusterId: String,
    val placeType: String,
    val spaceType: String,
    val claimCount: Int,
    val dimensions: List<Map<String, Any>>,
    val activeGoals: List<Map<String, Any>>,
    val recentReasoning: List<Map<String, Any>>,
    val blackboardSummary: Map<String, Any>,
    val lastMatchBreakdown: Map<String, Any>?
)

internal class PortraitWorkspaceSnapshotBuilder(
    private val blackboardDao: BlackboardDao,
    private val behaviorModelDao: BehaviorModelDao,
    private val sceneProfileDao: SceneProfileDao,
    private val sceneMemoryManager: SceneMemoryManager?,
    private val currentSceneIdProvider: () -> String?,
    private val currentSceneLabelProvider: () -> String,
    private val currentMatchBreakdownProvider: () -> MatchBreakdown?
) {
    suspend fun snapshot(): PortraitWorkspaceMemorySnapshot {
        val sceneId = currentSceneIdProvider()
        val sceneLabel = currentSceneLabelProvider()
        val profile = sceneId?.let { sceneProfileDao.getById(it) }
        val claims = sceneId?.let { behaviorModelDao.getClaimsByScene(it) }.orEmpty()
        val goals = sceneId?.let { behaviorModelDao.getGoalsByScene(it) }.orEmpty()
        val reasoning = sceneId?.let { behaviorModelDao.getReasoningByScene(it) }.orEmpty()
        val today = today()
        val blackboardDay = blackboardDao.getDay(today)
        val recentEntries = blackboardDao.getEntriesByDate(today).takeLast(5)
        val recentObservations = blackboardDao.getObservationItemsByDate(today).takeLast(8)
        val dimensions = claims
            .groupBy { it.dimensionKey.ifBlank { "unclassified" } }
            .toList()
            .sortedByDescending { (_, groupedClaims) -> groupedClaims.maxOfOrNull { it.updatedAt } ?: 0L }
            .take(8)
            .map { (dimensionKey, groupedClaims) ->
                mapOf(
                    "dimensionKey" to dimensionKey,
                    "claimCount" to groupedClaims.size,
                    "stableCount" to groupedClaims.count { it.status == "stable" },
                    "topClaims" to groupedClaims
                        .sortedWith(compareBy<BehaviorClaim>({ statusRank(it.status) }, { -it.evidenceCount }, { -it.updatedAt }))
                        .take(3)
                        .map { it.claimText }
                )
            }
        return PortraitWorkspaceMemorySnapshot(
            sceneId = sceneId,
            sceneLabel = sceneLabel,
            sceneSummary = sceneMemoryManager?.sceneMemory ?: profile?.summary.orEmpty(),
            entitySummary = sceneMemoryManager?.buildEntitySummary().orEmpty(),
            actionSummary = sceneMemoryManager?.actionSummary.orEmpty(),
            scenePhase = sceneMemoryManager?.phase?.displayName.orEmpty(),
            placeClusterId = profile?.placeClusterId.orEmpty(),
            placeType = profile?.placeType.orEmpty(),
            spaceType = profile?.spaceType.orEmpty(),
            claimCount = claims.size,
            dimensions = dimensions,
            activeGoals = goals.take(6).map { goal ->
                mapOf(
                    "goalId" to goal.goalId,
                    "dimensionKey" to goal.dimensionKey,
                    "question" to goal.question,
                    "priority" to goal.priority
                )
            },
            recentReasoning = reasoning.take(6).map { log ->
                mapOf(
                    "dimensionKey" to log.dimensionKey,
                    "content" to log.content,
                    "confidence" to log.confidence,
                    "createdAt" to log.createdAt
                )
            },
            blackboardSummary = mapOf(
                "dayDate" to today,
                "totalEntries" to (blackboardDay?.totalEntries ?: recentEntries.size),
                "recentEntries" to recentEntries.map { it.text.take(180) },
                "recentObservations" to recentObservations.map { item ->
                    mapOf(
                        "category" to item.category,
                        "dimensionHint" to item.dimensionHint,
                        "content" to item.content
                    )
                }
            ),
            lastMatchBreakdown = currentMatchBreakdownProvider()?.toMap()
        )
    }
}

internal fun PortraitWorkspaceMemorySnapshot.toToolPayload(): Map<String, Any?> {
    return mapOf(
        "sceneId" to sceneId,
        "sceneLabel" to sceneLabel,
        "sceneSummary" to sceneSummary,
        "entitySummary" to entitySummary,
        "actionSummary" to actionSummary,
        "scenePhase" to scenePhase,
        "placeClusterId" to placeClusterId,
        "placeType" to placeType,
        "spaceType" to spaceType,
        "claimCount" to claimCount,
        "dimensions" to dimensions,
        "activeGoals" to activeGoals,
        "recentReasoning" to recentReasoning,
        "blackboardSummary" to blackboardSummary,
        "lastMatchBreakdown" to lastMatchBreakdown
    )
}

internal fun PortraitWorkspaceMemorySnapshot.toWorkingMemoryText(): String {
    return buildString {
        appendLine("你正在为场景「${sceneLabel.ifBlank { "未命名场景" }}」构建行为模型。")
        sceneId?.let { appendLine("场景 ID: $it") }
        if (sceneSummary.isNotBlank()) appendLine("场景描述: $sceneSummary")
        if (scenePhase.isNotBlank()) appendLine("当前建模阶段: $scenePhase")
        appendLine()
        if (claimCount == 0) {
            appendLine("当前尚无已建立的行为 claim。你需要从观察中提取并创建 claim。")
        } else {
            appendLine("已建立 ${claimCount} 条行为 claim，覆盖 ${dimensions.size} 个维度：")
            dimensions.take(5).forEach { dimension ->
                val topClaims = dimension["topClaims"] as? List<*>
                append("  - ${dimension["dimensionKey"]} (${dimension["claimCount"]} 条)")
                if (!topClaims.isNullOrEmpty()) {
                    append("：${topClaims.joinToString("、")}")
                }
                appendLine()
            }
        }
        if (activeGoals.isNotEmpty()) {
            appendLine()
            appendLine("待验证的观察目标：")
            activeGoals.take(4).forEach { goal ->
                appendLine("  - ${goal["dimensionKey"]}: ${goal["question"]}")
            }
        }
        val entryTexts = blackboardSummary["recentEntries"] as? List<*>
        if (!entryTexts.isNullOrEmpty()) {
            appendLine()
            appendLine("最近的观察记录（${entryTexts.size} 条）：")
            entryTexts.take(3).forEach { appendLine("  - ${it.toString().take(120)}") }
        }
    }.trim()
}

private fun MatchBreakdown.toMap(): Map<String, Any> {
    return mapOf(
        "placeMatch" to placeMatch,
        "placeTypeMatch" to placeTypeMatch,
        "spaceTypeMatch" to spaceTypeMatch,
        "fixedOverlap" to fixedOverlap,
        "detailOverlap" to detailOverlap,
        "totalScore" to totalScore
    )
}

internal suspend fun buildClaimContextForSignal(
    behaviorModelDao: BehaviorModelDao,
    sceneId: String?
): String {
    val sid = sceneId ?: return "\n---\n已有行为 claim：无。请用 create_behavior_claim 新建。"
    val claims = behaviorModelDao.getClaimsByScene(sid)
    if (claims.isEmpty()) return "\n---\n已有行为 claim：无。请用 create_behavior_claim 新建。"
    val sorted = claims
        .sortedWith(compareBy<BehaviorClaim>({ statusRank(it.status) }, { -it.evidenceCount }, { -it.updatedAt }))
        .take(12)
    return buildString {
        appendLine()
        appendLine("---")
        appendLine("已有行为 claim（可直接 update）：")
        sorted.forEachIndexed { i, claim ->
            appendLine("  [${i + 1}] claimId=${claim.claimId} dimension=${claim.dimensionKey} status=${claim.status} evidence=${claim.evidenceCount}")
            appendLine("      text: ${claim.claimText}")
        }
        appendLine("如需更新已有 claim，直接用 update_behavior_claim(claimId=xxx, incrementEvidenceCount=1)。")
        append("如需新建维度，用 create_behavior_claim。")
    }.trimEnd()
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
