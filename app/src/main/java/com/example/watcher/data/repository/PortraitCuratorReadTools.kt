package com.example.watcher.data.repository

import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.core.AgentToolParameter
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.agentframework.tools.AgentTool
import com.example.watcher.agentframework.tools.AgentToolContext
import com.example.watcher.data.local.BehaviorModelDao
import com.example.watcher.data.local.BlackboardDao
import com.example.watcher.data.local.SceneProfileDao
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.MatchBreakdown
import java.util.Locale

internal class ReadBlackboardTool(
    private val dao: BlackboardDao,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_blackboard",
        description = "读取指定日期的 blackboard 观察日志，包括日摘要、原始观察条目和结构化观察",
        parameters = listOf(
            AgentToolParameter("date", "string", "日期，格式 yyyy-MM-dd", true)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val date = call.arguments["date"]?.toString() ?: return fail(call, "Missing date")
        val day = dao.getDay(date)
        val entries = dao.getEntriesByDate(date)
        val observationItems = dao.getObservationItemsByDate(date)
        val text = buildString {
            if (day != null) {
                appendLine("日期：$date，总条目：${day.totalEntries}")
                if (day.sceneMemory.isNotBlank()) appendLine("场景：${day.sceneMemory}")
                if (day.entityMemory.isNotBlank()) appendLine("实体：${day.entityMemory}")
                if (day.coreMemoryA.isNotBlank()) appendLine("核心记忆：${day.coreMemoryA}")
            } else {
                appendLine("该日期无数据")
            }
            if (entries.isNotEmpty()) {
                appendLine("观察条目(${entries.size})：")
                entries.takeLast(20).forEachIndexed { index, entry ->
                    appendLine("${index + 1}. ${entry.text}")
                }
            }
            if (observationItems.isNotEmpty()) {
                appendLine("结构化观察(${observationItems.size})：")
                observationItems.takeLast(30).forEachIndexed { index, item ->
                    appendLine(
                        "${index + 1}. [${item.category}/${item.dimensionHint.ifBlank { "-" }}] ${item.content}"
                    )
                }
            }
        }
        recordActivity(
            "read",
            "读取 Blackboard",
            "date=$date, entries=${entries.size}, observations=${observationItems.size}",
            "success"
        )
        return AgentToolResult(call.id, definition.name, true, mapOf("data" to text))
    }
}

internal class ReadBehaviorModelTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val currentSceneLabelProvider: () -> String,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_behavior_model",
        description = "读取当前行为模型中的 claims 和 observation goals",
        parameters = emptyList()
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val currentSceneId = currentSceneIdProvider()
        modelingState.markWorkspaceRead(context.session.sessionId)
        val currentSceneClaims = currentSceneId?.let { dao.getClaimsByScene(it) }.orEmpty()
        val universalClaims = dao.getUniversalClaims()
        val activeGoals = currentSceneId?.let { dao.getGoalsByScene(it) }.orEmpty()

        val payload = buildString {
            appendLine("当前场景：${currentSceneLabelProvider()}")
            appendLine("当前场景 sceneId：${currentSceneId ?: "未绑定"}")
            appendLine()
            appendLine("当前场景 claims：")
            if (currentSceneClaims.isEmpty()) {
                appendLine("- 暂无")
            } else {
                currentSceneClaims
                    .sortedWith(compareBy<BehaviorClaim>({ it.dimensionKey }, { it.status }, { -it.updatedAt }))
                    .forEach { claim ->
                        appendLine(
                            "- [${claim.claimId}] ${claim.dimensionKey}/${claim.status} " +
                                "conf=${"%.2f".format(Locale.US, claim.confidenceScore)} " +
                                "evidence=${claim.evidenceCount}: ${claim.claimText}"
                        )
                        if (claim.evidenceSummary.isNotBlank()) {
                            appendLine("  basis: ${claim.evidenceSummary}")
                        }
                    }
            }
            appendLine()
            appendLine("跨场景通用 claims：")
            if (universalClaims.isEmpty()) {
                appendLine("- 暂无")
            } else {
                universalClaims
                    .sortedWith(compareBy<BehaviorClaim>({ it.dimensionKey }, { it.status }, { -it.updatedAt }))
                    .forEach { claim ->
                        appendLine(
                            "- [${claim.claimId}] ${claim.dimensionKey}/${claim.status} " +
                                "conf=${"%.2f".format(Locale.US, claim.confidenceScore)} " +
                                "evidence=${claim.evidenceCount}: ${claim.claimText}"
                        )
                        if (claim.evidenceSummary.isNotBlank()) {
                            appendLine("  basis: ${claim.evidenceSummary}")
                        }
                    }
            }
            appendLine()
            appendLine("Observation goals：")
            if (activeGoals.isEmpty()) {
                appendLine("- 暂无")
            } else {
                activeGoals.forEach { goal ->
                    appendLine("- [${goal.goalId}] ${goal.dimensionKey}/P${goal.priority} ${goal.question}")
                }
            }
        }
        recordActivity(
            "read",
            "读取行为模型",
            "sceneClaims=${currentSceneClaims.size}, universalClaims=${universalClaims.size}, activeGoals=${activeGoals.size}",
            "success"
        )
        return AgentToolResult(call.id, definition.name, true, mapOf("model" to payload))
    }
}

internal class ReadClaimSchemaTool(
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_claim_schema",
        description = "读取场景行为 claim 的字段定义、状态规则和建模原则；在创建或修改 claim 前应先读取一次。",
        parameters = emptyList()
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val payload = mapOf(
            "entity" to "BehaviorClaim",
            "fields" to listOf(
                mapOf("name" to "claimId", "type" to "string", "description" to "claim 唯一 ID"),
                mapOf("name" to "sceneId", "type" to "string|null", "description" to "所属场景，null 表示通用层"),
                mapOf("name" to "dimensionKey", "type" to "string", "description" to "行为维度 key，应尽量复用"),
                mapOf("name" to "claimText", "type" to "string", "description" to "稳定、可复用的行为或场景事实"),
                mapOf("name" to "status", "type" to "string", "description" to "hypothesis/emerging/stable/stale/conflicted"),
                mapOf("name" to "confidenceScore", "type" to "number", "description" to "0 到 1 的置信度"),
                mapOf("name" to "evidenceSummary", "type" to "string", "description" to "证据摘要，写观察依据，不写归一理由"),
                mapOf("name" to "evidenceCount", "type" to "integer", "description" to "累计证据次数"),
                mapOf("name" to "firstObservedAt", "type" to "long", "description" to "首次观察时间"),
                mapOf("name" to "lastObservedAt", "type" to "long", "description" to "最近观察时间")
            ),
            "statusRules" to listOf(
                mapOf("status" to BehaviorClaimStatuses.HYPOTHESIS, "meaning" to "初步猜测，证据刚出现"),
                mapOf("status" to BehaviorClaimStatuses.EMERGING, "meaning" to "重复出现，正在形成"),
                mapOf("status" to BehaviorClaimStatuses.STABLE, "meaning" to "多次稳定复现的长期模式"),
                mapOf("status" to BehaviorClaimStatuses.STALE, "meaning" to "历史成立，但近期需要刷新"),
                mapOf("status" to BehaviorClaimStatuses.CONFLICTED, "meaning" to "存在相反证据或明显冲突")
            ),
            "modelingRules" to listOf(
                "优先维护已有 claim 主干，而不是为每条 observation 生成新分支。",
                "不适合进入长期场景模型的内容应写入 reasoning log，而不是 claim。",
                "claimText 应短、稳、可复用，避免带过细时间窗或一次性描述。",
                "需要合并多个已有 claims 时使用 merge_behavior_claims，而不是新建一条近义 claim。",
                "knowledge 只写跨 session 可复用的方法经验或跨场景稳定规律。",
                "调用 curator_write_knowledge 时必须带上 modeling_rule / lesson / cross_scene_pattern 之一作为标签。",
                "一个阶段收敛完成后，用 episodic memory 记录阶段总结，避免后续重复推理。"
            )
        )
        recordActivity("read", "读取 claim schema", "返回字段定义与建模规则", "success")
        return AgentToolResult(call.id, definition.name, true, payload)
    }
}

internal class ReadWorkspaceMemoryTool(
    private val blackboardDao: BlackboardDao,
    private val behaviorModelDao: BehaviorModelDao,
    private val sceneProfileDao: SceneProfileDao,
    private val sceneMemoryManager: SceneMemoryManager?,
    private val currentSceneIdProvider: () -> String?,
    private val currentSceneLabelProvider: () -> String,
    private val currentMatchBreakdownProvider: () -> MatchBreakdown?,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "curator_read_workspace_memory",
        description = "读取当前场景工作区的统一摘要，包括 scene、blackboard、claim 维度概览、goals 和 recent reasoning。",
        parameters = emptyList()
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val snapshot = PortraitWorkspaceSnapshotBuilder(
            blackboardDao = blackboardDao,
            behaviorModelDao = behaviorModelDao,
            sceneProfileDao = sceneProfileDao,
            sceneMemoryManager = sceneMemoryManager,
            currentSceneIdProvider = currentSceneIdProvider,
            currentSceneLabelProvider = currentSceneLabelProvider,
            currentMatchBreakdownProvider = currentMatchBreakdownProvider
        ).snapshot()
        modelingState.markWorkspaceRead(context.session.sessionId)
        recordActivity(
            "read",
            "读取 workspace memory",
            "scene=${snapshot.sceneLabel} claims=${snapshot.claimCount} dimensions=${snapshot.dimensions.size} goals=${snapshot.activeGoals.size}",
            "success"
        )
        return AgentToolResult(call.id, definition.name, true, snapshot.toToolPayload())
    }
}

internal class ReadClaimDimensionsTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val currentSceneLabelProvider: () -> String,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_claim_dimensions",
        description = "读取当前场景 claim 的维度概览。先了解已建模方向，再决定钻取哪个 dimension。",
        parameters = emptyList()
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val claims = dao.getClaimsByScene(sceneId)
        modelingState.markWorkspaceRead(context.session.sessionId)
        val dimensions = claims
            .groupBy { it.dimensionKey.ifBlank { "unclassified" } }
            .toList()
            .sortedByDescending { (_, groupedClaims) -> groupedClaims.maxOfOrNull { it.updatedAt } ?: 0L }
        val payload = mapOf(
            "sceneId" to sceneId,
            "sceneLabel" to currentSceneLabelProvider(),
            "claimCount" to claims.size,
            "dimensionCount" to dimensions.size,
            "dimensions" to dimensions.map { (dimensionKey, groupedClaims) ->
                mapOf(
                    "dimensionKey" to dimensionKey,
                    "claimCount" to groupedClaims.size,
                    "statusBreakdown" to groupedClaims.groupingBy { it.status }.eachCount().toSortedMap(),
                    "topClaims" to groupedClaims
                        .sortedWith(compareBy<BehaviorClaim>({ statusRank(it.status) }, { -it.evidenceCount }, { -it.updatedAt }))
                        .take(3)
                        .map { it.claimText },
                    "lastUpdatedAt" to (groupedClaims.maxOfOrNull { it.updatedAt } ?: 0L)
                )
            }
        )
        recordActivity(
            "read",
            "读取 claim 维度概览",
            "scene=${sceneId.take(8)} dimensions=${dimensions.size}, claims=${claims.size}",
            "success"
        )
        return AgentToolResult(call.id, definition.name, true, payload)
    }
}

internal class ReadClaimsByDimensionTool(
    private val dao: BehaviorModelDao,
    private val currentSceneIdProvider: () -> String?,
    private val currentSceneLabelProvider: () -> String,
    private val modelingState: PortraitCuratorModelingState,
    private val recordActivity: (String, String, String, String) -> Unit
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "read_claims_by_dimension",
        description = "读取当前场景某个 dimensionKey 下的 claim 详情，理解该方向已有哪些分支。",
        parameters = listOf(
            AgentToolParameter("dimensionKey", "string", "要查看的行为维度 key", true)
        )
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val sceneId = currentSceneIdProvider() ?: return fail(call, "Missing current scene context")
        val dimensionKey = call.arguments["dimensionKey"]?.toString()?.trim()
            ?: return fail(call, "Missing dimensionKey")
        if (dimensionKey.isBlank()) return fail(call, "Empty dimensionKey")
        modelingState.markDimensionRead(context.session.sessionId, dimensionKey)

        val claims = dao.getClaimsByScene(sceneId)
            .filter { it.dimensionKey == dimensionKey }
            .sortedWith(compareBy<BehaviorClaim>({ statusRank(it.status) }, { -it.evidenceCount }, { -it.updatedAt }))
        val payload = mapOf(
            "sceneId" to sceneId,
            "sceneLabel" to currentSceneLabelProvider(),
            "dimensionKey" to dimensionKey,
            "claimCount" to claims.size,
            "claims" to claims.map { claim ->
                mapOf(
                    "claimId" to claim.claimId,
                    "sceneId" to claim.sceneId,
                    "dimensionKey" to claim.dimensionKey,
                    "claimText" to claim.claimText,
                    "status" to claim.status,
                    "confidenceScore" to claim.confidenceScore,
                    "evidenceCount" to claim.evidenceCount,
                    "evidenceSummary" to claim.evidenceSummary,
                    "firstObservedAt" to claim.firstObservedAt,
                    "lastObservedAt" to claim.lastObservedAt,
                    "updatedAt" to claim.updatedAt
                )
            }
        )
        recordActivity(
            "read",
            "按维度读取 claims",
            "scene=${sceneId.take(8)} dimension=$dimensionKey claims=${claims.size}",
            "success"
        )
        return AgentToolResult(call.id, definition.name, true, payload)
    }
}
