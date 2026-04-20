package com.example.watcher.data.repository

import android.util.Log
import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.core.AgentToolParameter
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.agentframework.tools.AgentTool
import com.example.watcher.agentframework.tools.AgentToolContext
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.BehaviorReasoningLog
import com.example.watcher.data.model.ObservationGoal
import com.example.watcher.data.model.ObservationGoalStatuses
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CreateBehaviorClaimTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "create_behavior_claim",
        description = "新建一条场景行为 claim。观察信号中已包含已有 claim 信息，确认无匹配 claim 后直接调用。",
        parameters = listOf(
            AgentToolParameter("dimensionKey", "string", "行为维度 key，建议复用已有命名", true),
            AgentToolParameter("claimText", "string", "行为判断文本，建议 30 字以内", true),
            AgentToolParameter("status", "string", "状态: hypothesis/emerging/stable/stale/conflicted", true),
            AgentToolParameter("confidenceScore", "number", "0 到 1 的置信度", true),
            AgentToolParameter("evidenceSummary", "string", "简短证据摘要", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val dimensionKey = call.arguments["dimensionKey"]?.toString()
            ?: return fail(call, "Missing dimensionKey")
        if (dimensionKey.isBlank()) {
            return fail(call, "Empty dimensionKey")
        }
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")

        val claimText = call.arguments["claimText"]?.toString()?.trim()
            ?: return fail(call, "Missing claimText")
        if (claimText.isBlank()) return fail(call, "Empty claimText")

        val status = call.arguments["status"]?.toString() ?: BehaviorClaimStatuses.HYPOTHESIS
        if (status !in BehaviorClaimStatuses.ordered) {
            return fail(call, "Unsupported status: $status")
        }

        val confidenceScore = when (val raw = call.arguments["confidenceScore"]) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull() ?: return fail(call, "Invalid confidenceScore")
            else -> return fail(call, "Missing confidenceScore")
        }.coerceIn(0f, 1f)

        val evidenceSummary = call.arguments["evidenceSummary"]?.toString()?.trim().orEmpty()
        val now = System.currentTimeMillis()
        val normalizedDimensionKey = dimensionKey.trim()
        val existing = dao.getClaimBySceneDimensionAndText(sceneId, normalizedDimensionKey, claimText)
        if (existing != null) {
            return fail(call, "Exact claim already exists; use update_behavior_claim instead")
        }
        val warnings = modelingState.guardForDimension(context.session.sessionId, normalizedDimensionKey)
        recordWarnings(recordActivity, warnings, "create claim", normalizedDimensionKey)

        val created = BehaviorClaim(
            sceneId = sceneId,
            dimensionKey = normalizedDimensionKey,
            claimText = claimText,
            status = status,
            confidenceScore = confidenceScore,
            evidenceSummary = evidenceSummary,
            evidenceCount = 1,
            firstObservedAt = now,
            lastObservedAt = now,
            updatedAt = now
        )
        dao.upsertClaim(created)
        Log.d(PortraitCuratorAgent.TAG, "Behavior claim created: ${created.dimensionKey}/${created.status} ${created.claimText}")
        recordActivity(
            "write",
            "新建行为 claim",
            "scene=${sceneId.take(8)} ${created.dimensionKey}/${created.status}: ${created.claimText}",
            "success"
        )
        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf(
                "claimId" to created.claimId,
                "dimensionKey" to created.dimensionKey,
                "status" to created.status,
                "evidenceCount" to created.evidenceCount,
                "warnings" to warnings
            )
        )
    }
}

internal class UpdateBehaviorClaimTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "update_behavior_claim",
        description = "更新一条已有 claim。用于补强证据、调整 status、修正 dimensionKey 或 claimText。",
        parameters = listOf(
            AgentToolParameter("claimId", "string", "要更新的 claim ID", true),
            AgentToolParameter("dimensionKey", "string", "新的行为维度 key", false),
            AgentToolParameter("claimText", "string", "新的行为判断文本", false),
            AgentToolParameter("status", "string", "新的状态", false),
            AgentToolParameter("confidenceScore", "number", "新的置信度", false),
            AgentToolParameter("evidenceSummary", "string", "新增证据摘要", false),
            AgentToolParameter("incrementEvidenceCount", "integer", "额外增加的证据次数，默认 0", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val claimId = call.arguments["claimId"]?.toString() ?: return fail(call, "Missing claimId")
        val existing = dao.getClaimById(claimId) ?: return fail(call, "Unknown claimId: $claimId")
        if (existing.sceneId != sceneId) {
            return fail(call, "Claim does not belong to current scene")
        }

        val newDimensionKey = call.arguments["dimensionKey"]?.toString()?.trim()
            ?.takeIf { it.isNotBlank() } ?: existing.dimensionKey
        val newClaimText = call.arguments["claimText"]?.toString()?.trim()
            ?.takeIf { it.isNotBlank() } ?: existing.claimText
        val newStatus = call.arguments["status"]?.toString()
            ?.takeIf { it in BehaviorClaimStatuses.ordered } ?: existing.status
        val newConfidence = parseOptionalFloat(call.arguments["confidenceScore"])?.coerceIn(0f, 1f)
        val evidenceSummary = call.arguments["evidenceSummary"]?.toString()?.trim().orEmpty()
        val evidenceIncrement = parseOptionalInt(call.arguments["incrementEvidenceCount"]) ?: 0
        val target = dao.getClaimBySceneDimensionAndText(sceneId, newDimensionKey, newClaimText)
        if (target != null && target.claimId != existing.claimId) {
            return fail(call, "Another claim already uses the same dimensionKey + claimText; use merge_behavior_claims")
        }
        val warnings = modelingState.guardForDimension(context.session.sessionId, newDimensionKey)
        recordWarnings(recordActivity, warnings, "update claim", newDimensionKey)

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            dimensionKey = newDimensionKey,
            claimText = newClaimText,
            status = newStatus,
            confidenceScore = newConfidence?.let { maxOf(existing.confidenceScore, it) } ?: existing.confidenceScore,
            evidenceSummary = mergeEvidence(existing.evidenceSummary, evidenceSummary),
            evidenceCount = (existing.evidenceCount + evidenceIncrement.coerceAtLeast(0)).coerceAtLeast(existing.evidenceCount),
            lastObservedAt = now,
            updatedAt = now
        )
        dao.upsertClaim(updated)
        recordActivity(
            "write",
            "更新行为 claim",
            "scene=${sceneId.take(8)} ${updated.dimensionKey}/${updated.status}: ${updated.claimText}",
            "success"
        )
        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf(
                "claimId" to updated.claimId,
                "dimensionKey" to updated.dimensionKey,
                "status" to updated.status,
                "evidenceCount" to updated.evidenceCount,
                "warnings" to warnings
            )
        )
    }
}

internal class MergeBehaviorClaimsTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "merge_behavior_claims",
        description = "将当前场景中多个已有 claims 合并为一个 canonical claim。用于收束近义分支。",
        parameters = listOf(
            AgentToolParameter("sourceClaimIds", "array", "要合并的 claimId 列表", true),
            AgentToolParameter("canonicalDimensionKey", "string", "合并后的维度 key", true),
            AgentToolParameter("canonicalClaimText", "string", "合并后的 claim 文本", true),
            AgentToolParameter("canonicalStatus", "string", "合并后的状态", false),
            AgentToolParameter("canonicalConfidenceScore", "number", "合并后的置信度", false),
            AgentToolParameter("canonicalEvidenceSummary", "string", "合并后的证据摘要", false),
            AgentToolParameter("reason", "string", "合并原因", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val sourceClaimIds = parseStringList(call.arguments["sourceClaimIds"])
        if (sourceClaimIds.size < 2) return fail(call, "Need at least 2 sourceClaimIds")
        val sourceClaims = sourceClaimIds.mapNotNull { dao.getClaimById(it) }
        if (sourceClaims.size < 2) return fail(call, "Not enough valid source claims")
        if (sourceClaims.any { it.sceneId != sceneId }) {
            return fail(call, "All source claims must belong to current scene")
        }

        val canonicalDimensionKey = call.arguments["canonicalDimensionKey"]?.toString()?.trim()
            ?: return fail(call, "Missing canonicalDimensionKey")
        val canonicalClaimText = call.arguments["canonicalClaimText"]?.toString()?.trim()
            ?: return fail(call, "Missing canonicalClaimText")
        if (canonicalDimensionKey.isBlank() || canonicalClaimText.isBlank()) {
            return fail(call, "Empty canonicalDimensionKey or canonicalClaimText")
        }
        val plannedStatus = call.arguments["canonicalStatus"]?.toString().orEmpty()
        val plannedConfidence = parseOptionalFloat(call.arguments["canonicalConfidenceScore"]) ?: 0f
        val canonicalEvidenceSummary = call.arguments["canonicalEvidenceSummary"]?.toString()?.trim().orEmpty()
        val reason = call.arguments["reason"]?.toString()?.trim().orEmpty()
        val warnings = modelingState.guardForDimension(context.session.sessionId, canonicalDimensionKey)
        recordWarnings(recordActivity, warnings, "merge claims", canonicalDimensionKey)

        val primary = sourceClaims.maxWithOrNull(
            compareBy<BehaviorClaim>({ -it.evidenceCount }, { -it.confidenceScore }, { statusRank(it.status) })
        ) ?: return fail(call, "No primary claim")
        val canonicalExisting = dao.getClaimBySceneDimensionAndText(sceneId, canonicalDimensionKey, canonicalClaimText)
            ?.takeIf { it.claimId !in sourceClaimIds }
        val participatingClaims = buildList {
            addAll(sourceClaims)
            canonicalExisting?.let(::add)
        }
        val merged = (canonicalExisting ?: primary).copy(
            sceneId = sceneId,
            dimensionKey = canonicalDimensionKey,
            claimText = canonicalClaimText,
            status = mergeStatuses(participatingClaims, plannedStatus),
            confidenceScore = maxOf(
                plannedConfidence.coerceIn(0f, 1f),
                participatingClaims.maxOf { it.confidenceScore }
            ),
            evidenceSummary = mergeEvidence(
                listOf(canonicalEvidenceSummary, reason).joinToString("；").trim('；'),
                participatingClaims.joinToString("；") { it.evidenceSummary }
            ),
            evidenceCount = participatingClaims.sumOf { it.evidenceCount }.coerceAtLeast(1),
            firstObservedAt = participatingClaims.minOf { it.firstObservedAt },
            lastObservedAt = participatingClaims.maxOf { it.lastObservedAt },
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertClaim(merged)
        participatingClaims
            .map { it.claimId }
            .distinct()
            .filter { it != merged.claimId }
            .forEach { dao.deleteClaimById(it) }

        recordActivity(
            "write",
            "合并行为 claims",
            "scene=${sceneId.take(8)} merged=${sourceClaims.size} -> ${merged.claimText}",
            "success"
        )
        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf(
                "claimId" to merged.claimId,
                "mergedCount" to sourceClaims.size,
                "dimensionKey" to merged.dimensionKey,
                "status" to merged.status,
                "warnings" to warnings
            )
        )
    }
}

internal class DeleteBehaviorClaimTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "delete_behavior_claim",
        description = "删除一条当前场景中不再适合保留的 claim。删除前应先确认不会丢失关键长期事实。",
        parameters = listOf(
            AgentToolParameter("claimId", "string", "要删除的 claim ID", true),
            AgentToolParameter("reason", "string", "删除原因", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val claimId = call.arguments["claimId"]?.toString() ?: return fail(call, "Missing claimId")
        val existing = dao.getClaimById(claimId) ?: return fail(call, "Unknown claimId: $claimId")
        if (existing.sceneId != sceneId) {
            return fail(call, "Claim does not belong to current scene")
        }
        val warnings = modelingState.guardForDimension(context.session.sessionId, existing.dimensionKey)
        recordWarnings(recordActivity, warnings, "delete claim", existing.dimensionKey)
        dao.deleteClaimById(claimId)
        recordActivity(
            "write",
            "删除行为 claim",
            "scene=${sceneId.take(8)} ${existing.dimensionKey}: ${existing.claimText}",
            "success"
        )
        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf(
                "deletedClaimId" to claimId,
                "reason" to call.arguments["reason"]?.toString().orEmpty(),
                "warnings" to warnings
            )
        )
    }
}

internal class ResolveObservationGoalTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "resolve_observation_goal",
        description = "将 observation goal 标记为已解决，用于避免重复追问",
        parameters = listOf(
            AgentToolParameter("goalId", "string", "目标 ID", true),
            AgentToolParameter("resolutionNote", "string", "解决说明", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val goalId = call.arguments["goalId"]?.toString() ?: return fail(call, "Missing goalId")
        val existing = dao.getGoalById(goalId) ?: return fail(call, "Unknown goalId: $goalId")
        if (existing.sceneId != sceneId) {
            return fail(call, "Observation goal does not belong to current scene")
        }
        dao.upsertGoal(
            existing.copy(
                status = ObservationGoalStatuses.RESOLVED,
                resolutionNote = call.arguments["resolutionNote"]?.toString().orEmpty(),
                updatedAt = System.currentTimeMillis()
            )
        )
        recordActivity("write", "关闭 observation goal", "goalId=$goalId", "success")
        return AgentToolResult(call.id, definition.name, true, mapOf("resolved" to goalId))
    }
}

internal class ConsolidateBehaviorClaimsTool(
    private val consolidator: BehaviorClaimConsolidator,
    private val currentSceneIdProvider: () -> String?,
    private val currentSceneLabelProvider: () -> String,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "consolidate_behavior_claims",
        description = "对当前场景内语义相近的 claims 做归一和去重；只在存在明确重复时合并。",
        parameters = listOf(
            AgentToolParameter("maxClaims", "integer", "最多送审的 claim 数，默认 24", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val warnings = modelingState.guardForDimension(context.session.sessionId, "")
        recordWarnings(recordActivity, warnings, "consolidate claims", "")
        val result = consolidator.consolidateSceneClaims(
            sceneId = sceneId,
            sceneLabel = currentSceneLabelProvider(),
            maxClaims = parseMaxClaims(call.arguments["maxClaims"])
        )
        if (result.mergedCount == 0) {
            recordActivity(
                "write",
                if (result.reason == "not_enough_claims") "跳过 claim 归一" else "无需 claim 归一",
                "scene=${sceneId.take(8)} reason=${result.reason}",
                "success"
            )
            return AgentToolResult(
                call.id,
                definition.name,
                true,
                mapOf(
                    "mergedCount" to 0,
                    "reason" to result.reason,
                    "guidance" to when (result.reason) {
                        "not_enough_claims" -> "Claims 不足 2 条，无需归一。请直接写收敛总结并 Finish。"
                        "no_merge_candidates" -> "无可合并的候选 claim。请直接写收敛总结并 Finish。"
                        else -> "归一已完成，请继续后续步骤。"
                    },
                    "warnings" to warnings
                )
            )
        }

        recordActivity(
            "write",
            "完成 claim 归一",
            "scene=${sceneId.take(8)} merged=${result.mergedCount} ${result.summaries.joinToString("；").take(220)}",
            "success"
        )
        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf(
                "mergedCount" to result.mergedCount,
                "summaries" to result.summaries,
                "warnings" to warnings
            )
        )
    }

    private fun parseMaxClaims(raw: Any?): Int {
        val parsed = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
        return (parsed ?: 24).coerceIn(6, 40)
    }
}

private fun recordWarnings(
    recordActivity: (String, String, String, String) -> Unit,
    warnings: List<String>,
    action: String,
    dimensionKey: String
) {
    warnings.forEach { warning ->
        recordActivity(
            "guard",
            "建模上下文不足",
            "${action}${if (dimensionKey.isNotBlank()) " [$dimensionKey]" else ""}: $warning",
            "warning"
        )
    }
}

internal class RequestObservationTool(
    private val dao: BehaviorModelDao,
    private val sceneMemoryManager: SceneMemoryManager?,
    private val currentSceneIdProvider: () -> String?,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "request_observation",
        description = "创建观察目标并通知视频解说员在接下来的视频分析中重点关注这些问题",
        parameters = listOf(
            AgentToolParameter("dimensionKey", "string", "行为维度 key，建议复用已有命名", true),
            AgentToolParameter("requests", "string", "观察请求列表，用换行分隔", true),
            AgentToolParameter("priority", "integer", "优先级 1-3，默认 2", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val dimensionKey = call.arguments["dimensionKey"]?.toString()
            ?: return fail(call, "Missing dimensionKey")
        if (dimensionKey.isBlank()) {
            return fail(call, "Empty dimensionKey")
        }
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")

        val raw = call.arguments["requests"]?.toString() ?: return fail(call, "Missing requests")
        val requests = raw
            .split("\n", "\\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
        if (requests.isEmpty()) return fail(call, "Empty requests")

        val priority = when (val rawPriority = call.arguments["priority"]) {
            is Number -> rawPriority.toInt()
            is String -> rawPriority.toIntOrNull() ?: 2
            else -> 2
        }.coerceIn(1, 3)

        val existingQuestions = dao.getGoalsByScene(sceneId)
            .filter { it.dimensionKey == dimensionKey.trim() }
            .map { it.question }
            .toSet()

        val newQuestions = requests.filter { it !in existingQuestions }
        newQuestions.forEach { question ->
            dao.upsertGoal(
                ObservationGoal(
                    sceneId = sceneId,
                    dimensionKey = dimensionKey.trim(),
                    question = question,
                    priority = priority
                )
            )
        }
        if (newQuestions.isNotEmpty()) {
            sceneMemoryManager?.appendExpertRequests(newQuestions)
        }

        Log.d(PortraitCuratorAgent.TAG, "Observation requests submitted: ${newQuestions.size} items for $dimensionKey")
        recordActivity(
            "request",
            "提交补证据请求",
            "scene=${sceneId.take(8)} ${dimensionKey.trim()} -> ${newQuestions.joinToString(" / ").take(200)}",
            "success"
        )
        return AgentToolResult(
            call.id,
            definition.name,
            true,
            mapOf("submitted" to newQuestions.size, "dimensionKey" to dimensionKey)
        )
    }
}

internal class WriteInferenceTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "write_inference",
        description = "记录一条中间推断，用于保留行为模型形成过程中的推理轨迹",
        parameters = listOf(
            AgentToolParameter("dimensionKey", "string", "关联维度 key", true),
            AgentToolParameter("content", "string", "推断内容", true),
            AgentToolParameter("confidence", "string", "置信度: low/medium/high", true),
            AgentToolParameter("basis", "string", "推断依据", false)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val key = call.arguments["dimensionKey"]?.toString() ?: return fail(call, "Missing dimensionKey")
        if (key.isBlank()) return fail(call, "Empty dimensionKey")
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val content = call.arguments["content"]?.toString() ?: return fail(call, "Missing content")
        val confidence = call.arguments["confidence"]?.toString() ?: "low"
        val basis = call.arguments["basis"]?.toString().orEmpty()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dao.insertReasoningLog(
            BehaviorReasoningLog(
                sceneId = sceneId,
                dayDate = today,
                dimensionKey = key.trim(),
                content = content,
                confidence = confidence,
                basis = basis
            )
        )
        Log.d(PortraitCuratorAgent.TAG, "Inference [$key/$confidence]: ${content.take(50)}...")
        recordActivity(
            "write",
            "记录行为推理",
            "scene=${sceneId.take(8)} ${key.trim()}/$confidence: ${content.take(120)}",
            "success"
        )
        return AgentToolResult(call.id, definition.name, true, mapOf("recorded" to key.trim()))
    }
}
