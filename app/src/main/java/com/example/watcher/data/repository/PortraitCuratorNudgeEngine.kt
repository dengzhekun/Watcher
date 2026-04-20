package com.example.watcher.data.repository

import android.util.Log
import com.example.watcher.agentframework.autonomy.SignalChannel
import com.example.watcher.agentframework.service.AgentFrameworkService
import com.example.watcher.agentframework.service.AgentSignalSeed
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.model.BehaviorClaimStatuses

/**
 * Periodic self-reflection nudge engine for the Portrait Curator Agent.
 *
 * Tracks observation count and elapsed time, injecting Agent-channel signals
 * at configurable intervals to prompt the LLM to step back and evaluate its
 * modeling quality, adjust strategy, or consolidate under time pressure.
 */
class PortraitCuratorNudgeEngine {
    private var observationCount = 0
    private var sessionStartTime = 0L
    private var lastProgressNudgeAt = 0
    private var lastStrategyNudgeAt = 0
    private var timePressureNudgeSent = false

    fun reset(startTime: Long) {
        observationCount = 0
        sessionStartTime = startTime
        lastProgressNudgeAt = 0
        lastStrategyNudgeAt = 0
        timePressureNudgeSent = false
    }

    suspend fun onObservation(
        runtimeId: String,
        service: AgentFrameworkService,
        behaviorModelDao: BehaviorModelDao,
        sceneId: String?,
        maxRuntimeMillis: Long,
        recordActivity: (String, String, String, String) -> Unit
    ) {
        observationCount++

        // Time pressure nudge: >70% runtime elapsed
        if (!timePressureNudgeSent && sessionStartTime > 0) {
            val elapsed = System.currentTimeMillis() - sessionStartTime
            if (elapsed > maxRuntimeMillis * 0.7) {
                timePressureNudgeSent = true
                val signal = buildTimePressureNudge(elapsed, maxRuntimeMillis)
                submitNudge(runtimeId, service, signal, recordActivity, "时间压力提醒")
                return
            }
        }

        // Strategy evaluation nudge: every 10 observations
        if (observationCount - lastStrategyNudgeAt >= 10) {
            lastStrategyNudgeAt = observationCount
            lastProgressNudgeAt = observationCount // also counts as progress nudge
            val stats = computeNudgeStats(behaviorModelDao, sceneId)
            val signal = buildStrategyNudge(stats, observationCount)
            submitNudge(runtimeId, service, signal, recordActivity, "策略评估提醒")
            return
        }

        // Progress checkpoint nudge: every 5 observations
        if (observationCount - lastProgressNudgeAt >= 5) {
            lastProgressNudgeAt = observationCount
            val stats = computeNudgeStats(behaviorModelDao, sceneId)
            val signal = buildProgressNudge(stats, observationCount)
            submitNudge(runtimeId, service, signal, recordActivity, "进展检查点")
        }
    }

    private suspend fun submitNudge(
        runtimeId: String,
        service: AgentFrameworkService,
        signal: String,
        recordActivity: (String, String, String, String) -> Unit,
        summary: String
    ) {
        try {
            service.submitAutonomousSignal(
                runtimeId,
                AgentSignalSeed(
                    channel = SignalChannel.Agent,
                    content = signal
                )
            )
            recordActivity("nudge", summary, signal.take(200), "info")
        } catch (e: Exception) {
            Log.w(PortraitCuratorAgent.TAG, "Failed to submit nudge signal", e)
        }
    }

    private suspend fun computeNudgeStats(
        behaviorModelDao: BehaviorModelDao,
        sceneId: String?
    ): NudgeStats {
        val sid = sceneId ?: return NudgeStats()
        val claims = behaviorModelDao.getClaimsByScene(sid)
        val goals = behaviorModelDao.getGoalsByScene(sid)
        val dimensions = claims.map { it.dimensionKey }.distinct()
        val statusCounts = claims.groupBy { it.status }.mapValues { it.value.size }
        return NudgeStats(
            totalClaims = claims.size,
            dimensionCount = dimensions.size,
            hypothesisCount = statusCounts[BehaviorClaimStatuses.HYPOTHESIS] ?: 0,
            emergingCount = statusCounts[BehaviorClaimStatuses.EMERGING] ?: 0,
            stableCount = statusCounts[BehaviorClaimStatuses.STABLE] ?: 0,
            avgEvidence = if (claims.isEmpty()) 0f else claims.map { it.evidenceCount }.average().toFloat(),
            openGoalCount = goals.count { it.status != "resolved" }
        )
    }

    private fun buildProgressNudge(stats: NudgeStats, obsCount: Int): String = buildString {
        appendLine("[自反思] 进展检查点（已处理 $obsCount 条观察）")
        appendLine("当前模型：${stats.totalClaims} 条 claim，${stats.dimensionCount} 个维度")
        appendLine("状态分布：hypothesis=${stats.hypothesisCount} emerging=${stats.emergingCount} stable=${stats.stableCount}")
        appendLine("平均证据数：${"%.1f".format(stats.avgEvidence)}")
        if (stats.totalClaims == 0) {
            appendLine("⚠ 你尚未创建任何 claim。请立即分析已收到的观察并创建 claim。")
        } else if (stats.hypothesisCount > stats.emergingCount + stats.stableCount) {
            appendLine("建议：hypothesis 比例过高，优先用 update_behavior_claim 提升已有 hypothesis 的 evidenceCount 和 status。")
        }
        append("请用 assess_model_quality 工具获取详细质量报告，然后调整建模策略。")
    }

    private fun buildStrategyNudge(stats: NudgeStats, obsCount: Int): String = buildString {
        appendLine("[自反思] 策略评估（已处理 $obsCount 条观察）")
        appendLine("当前模型：${stats.totalClaims} 条 claim，${stats.dimensionCount} 个维度")
        appendLine("状态分布：hypothesis=${stats.hypothesisCount} emerging=${stats.emergingCount} stable=${stats.stableCount}")
        if (stats.dimensionCount <= 1 && stats.totalClaims >= 3) {
            appendLine("⚠ 维度单一（仅 ${stats.dimensionCount} 个），考虑从观察中发现新的行为维度。")
        }
        if (stats.avgEvidence < 2f && stats.totalClaims > 0) {
            appendLine("⚠ 证据密度低（平均 ${"%.1f".format(stats.avgEvidence)}），优先为已有 claim 补充证据。")
        }
        if (stats.openGoalCount > 3) {
            appendLine("⚠ 有 ${stats.openGoalCount} 个未解决的观察目标，考虑关闭已有答案的目标。")
        }
        appendLine("调用 assess_model_quality 获取质量评分，根据 advice 调整方向。")
        append("如果发现了通用建模规则，用 curator_write_knowledge(tags=[modeling_rule]) 保存。")
    }

    private fun buildTimePressureNudge(elapsedMillis: Long, maxRuntimeMillis: Long): String = buildString {
        val remainingMinutes = ((maxRuntimeMillis - elapsedMillis) / 60_000L).coerceAtLeast(0)
        appendLine("[自反思] 时间压力提醒（剩余约 ${remainingMinutes} 分钟）")
        appendLine("runtime 接近上限。请立即：")
        appendLine("1. 停止创建新的 hypothesis claim")
        appendLine("2. 用 update_behavior_claim 巩固证据充分的 claim（提升 status 到 emerging/stable）")
        appendLine("3. 用 curator_write_memory(scope=working) 记录当前建模进展摘要")
        append("4. 如有通用建模规则要保存，立即用 curator_write_knowledge 写入")
    }

    private data class NudgeStats(
        val totalClaims: Int = 0,
        val dimensionCount: Int = 0,
        val hypothesisCount: Int = 0,
        val emergingCount: Int = 0,
        val stableCount: Int = 0,
        val avgEvidence: Float = 0f,
        val openGoalCount: Int = 0
    )
}
