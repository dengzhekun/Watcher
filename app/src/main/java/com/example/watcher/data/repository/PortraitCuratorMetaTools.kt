package com.example.watcher.data.repository

import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.agentframework.tools.AgentTool
import com.example.watcher.agentframework.tools.AgentToolContext
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.model.BehaviorClaimStatuses

/**
 * Self-assessment tool: lets the agent quantitatively evaluate its own
 * behavior model quality — claim coverage, evidence depth, maturity, etc.
 */
internal class AssessModelQualityTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "assess_model_quality",
        description = "评估当前场景的行为模型质量，返回质量评分和建议。用于自我评估和策略调整。",
        parameters = emptyList()
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider()
            ?: return fail(call, "Missing current scene context")
        val claims = dao.getClaimsByScene(sceneId)
        val goals = dao.getGoalsByScene(sceneId)

        val totalClaims = claims.size
        val dimensions = claims.map { it.dimensionKey }.distinct()
        val dimensionCount = dimensions.size
        val statusDistribution = claims.groupBy { it.status }.mapValues { it.value.size }
        val hypothesisCount = statusDistribution[BehaviorClaimStatuses.HYPOTHESIS] ?: 0
        val emergingCount = statusDistribution[BehaviorClaimStatuses.EMERGING] ?: 0
        val stableCount = statusDistribution[BehaviorClaimStatuses.STABLE] ?: 0
        val avgEvidenceCount = if (totalClaims == 0) 0f else claims.map { it.evidenceCount }.average().toFloat()
        val avgConfidence = if (totalClaims == 0) 0f else claims.map { it.confidenceScore }.average().toFloat()

        val weakDimensions = dimensions.filter { dim ->
            val dimClaims = claims.filter { it.dimensionKey == dim }
            dimClaims.size <= 1 || dimClaims.all { it.status == BehaviorClaimStatuses.HYPOTHESIS }
        }
        val strongDimensions = dimensions.filter { dim ->
            val dimClaims = claims.filter { it.dimensionKey == dim }
            dimClaims.any { it.status == BehaviorClaimStatuses.STABLE || it.status == BehaviorClaimStatuses.EMERGING } &&
                dimClaims.any { it.evidenceCount >= 3 }
        }

        val openGoals = goals.filter { it.status != "resolved" }
        val goalDimensions = openGoals.map { it.dimensionKey }.distinct()
        val coverageGaps = goalDimensions.filter { gd ->
            claims.none { it.dimensionKey == gd }
        }

        // Quality score (0-100):
        // - Diversity (25): dimensionCount / max(5, dimensionCount)
        // - Depth (25): avgEvidenceCount / 5, capped at 1
        // - Maturity (25): (emerging + stable) / max(totalClaims, 1)
        // - Coverage (25): 1 - coverageGaps / max(goalDimensions.size, 1)
        val diversityScore = if (totalClaims == 0) 0f else (dimensionCount.toFloat() / maxOf(5, dimensionCount)).coerceAtMost(1f)
        val depthScore = (avgEvidenceCount / 5f).coerceAtMost(1f)
        val maturityScore = if (totalClaims == 0) 0f else ((emergingCount + stableCount).toFloat() / totalClaims)
        val coverageScore = if (goalDimensions.isEmpty()) 1f else (1f - coverageGaps.size.toFloat() / goalDimensions.size)
        val qualityScore = ((diversityScore + depthScore + maturityScore + coverageScore) * 25).toInt().coerceIn(0, 100)

        val advice = buildAdvice(totalClaims, hypothesisCount, emergingCount, stableCount, avgEvidenceCount, weakDimensions, coverageGaps)

        recordActivity(
            "meta",
            "模型质量评估",
            "quality=$qualityScore claims=$totalClaims dims=$dimensionCount",
            "info"
        )

        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf(
                "totalClaims" to totalClaims,
                "dimensionCount" to dimensionCount,
                "statusDistribution" to statusDistribution,
                "avgEvidenceCount" to "%.1f".format(avgEvidenceCount),
                "avgConfidence" to "%.2f".format(avgConfidence),
                "weakDimensions" to weakDimensions,
                "strongDimensions" to strongDimensions,
                "coverageGaps" to coverageGaps,
                "qualityScore" to qualityScore,
                "advice" to advice
            )
        )
    }

    private fun buildAdvice(
        totalClaims: Int,
        hypothesisCount: Int,
        emergingCount: Int,
        stableCount: Int,
        avgEvidence: Float,
        weakDimensions: List<String>,
        coverageGaps: List<String>
    ): String {
        if (totalClaims == 0) {
            return "尚无 claim，请立即分析已收到的观察并创建 claim。"
        }
        val issues = mutableListOf<String>()
        if (hypothesisCount > emergingCount + stableCount) {
            val pct = (hypothesisCount * 100) / totalClaims
            issues.add("hypothesis 比例过高(${pct}%)，优先提升已有 hypothesis 到 emerging")
        }
        if (avgEvidence < 2f) {
            issues.add("证据密度低(平均${"%.1f".format(avgEvidence)})，优先为已有 claim 补充证据")
        }
        if (weakDimensions.isNotEmpty()) {
            issues.add("弱维度：${weakDimensions.joinToString("、")}，需要更多观察或新建 claim")
        }
        if (coverageGaps.isNotEmpty()) {
            issues.add("覆盖缺口：${coverageGaps.joinToString("、")}有目标但无 claim")
        }
        if (issues.isEmpty()) {
            return "模型质量良好。继续巩固已有 claim 并关注新的行为模式。"
        }
        return issues.joinToString("；")
    }
}
