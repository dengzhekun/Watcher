package com.example.watcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.BehaviorReasoningLog
import com.example.watcher.data.model.MatchBreakdown
import com.example.watcher.data.model.ObservationGoal
import com.example.watcher.data.model.SceneProfile
import com.example.watcher.ui.components.StatusPill
import com.example.watcher.ui.components.WatcherCard
import com.example.watcher.ui.viewmodel.ClaimConsolidationUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DlcPortraitCard(
    sceneProfiles: List<SceneProfile>,
    claims: List<BehaviorClaim>,
    allGoals: List<ObservationGoal>,
    allReasoningLogs: List<BehaviorReasoningLog>,
    currentSceneId: String?,
    currentSceneLabel: String,
    lastMatchedSceneId: String?,
    lastMatchBreakdown: MatchBreakdown?,
    onRegenerate: (String) -> Unit,
    claimConsolidationUiState: ClaimConsolidationUiState,
    onRunClaimConsolidation: (String) -> Unit,
    onExportClaims: (String, String) -> Unit,
    onRenameScene: (String, String) -> Unit,
    onMergeScenes: (String, String) -> Unit
) {
    var selectedSceneId by remember(currentSceneId) { mutableStateOf(currentSceneId) }
    var showUniversal by remember(currentSceneId) { mutableStateOf(currentSceneId == null) }
    var renameTarget by remember { mutableStateOf<SceneProfile?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var mergeSource by remember { mutableStateOf<SceneProfile?>(null) }
    var mergeTargetId by remember { mutableStateOf<String?>(null) }
    val sortedScenes = sceneProfiles.sortedWith(
        compareByDescending<SceneProfile> { it.sceneId == currentSceneId }
            .thenByDescending { it.lastVerifiedAt }
            .thenByDescending { it.updatedAt }
    )
    val activeSceneId = when {
        showUniversal -> null
        selectedSceneId != null -> selectedSceneId
        currentSceneId != null -> currentSceneId
        else -> sortedScenes.firstOrNull()?.sceneId
    }
    val selectedScene = sortedScenes.firstOrNull { it.sceneId == activeSceneId }
    val visibleClaims = if (showUniversal) {
        claims.filter { it.sceneId == null }
    } else {
        claims.filter { it.sceneId == activeSceneId }
    }
    val visibleMatchBreakdown = if (!showUniversal && activeSceneId == lastMatchedSceneId) {
        lastMatchBreakdown
    } else {
        null
    }
    val visibleConsolidationState = if (!showUniversal && activeSceneId == claimConsolidationUiState.sceneId) {
        claimConsolidationUiState
    } else {
        null
    }
    val groupedClaims = visibleClaims
        .groupBy { it.dimensionKey.ifBlank { "未命名维度" } }
        .toList()
        .sortedByDescending { (_, dimensionClaims) -> dimensionClaims.maxOfOrNull { it.updatedAt } ?: 0L }

    WatcherCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "场景行为模型",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (showUniversal) {
                        "当前视图：跨场景通用层"
                    } else {
                        "当前视图：${selectedScene?.let(::sceneDisplayLabel).orEmpty().ifBlank { currentSceneLabel }}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!showUniversal && selectedScene != null && selectedScene.sceneId != currentSceneId) {
                    FilledTonalButton(
                        onClick = {
                            mergeSource = selectedScene
                            mergeTargetId = sortedScenes.firstOrNull { it.sceneId != selectedScene.sceneId }?.sceneId
                        }
                    ) {
                        Text("合并", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!showUniversal && selectedScene != null) {
                    FilledTonalButton(
                        onClick = { onRunClaimConsolidation(selectedScene.sceneId) },
                        enabled = !claimConsolidationUiState.isRunning
                    ) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = if (visibleConsolidationState?.isRunning == true) "归一中" else "归一",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        val exportTitle = if (showUniversal) {
                            "通用层 claims 导出"
                        } else {
                            "${selectedScene?.let(::sceneDisplayLabel).orEmpty().ifBlank { currentSceneLabel }} claims 导出"
                        }
                        onExportClaims(
                            exportTitle,
                            buildClaimExportPayload(
                                selectedScene = selectedScene,
                                currentSceneId = currentSceneId,
                                currentSceneLabel = currentSceneLabel,
                                visibleClaims = visibleClaims,
                                showUniversal = showUniversal
                            )
                        )
                    },
                    enabled = visibleClaims.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("导出", style = MaterialTheme.typography.labelMedium)
                }
                if (!showUniversal && selectedScene != null) {
                    FilledTonalIconButton(
                        onClick = {
                            renameTarget = selectedScene
                            renameDraft = sceneDisplayLabel(selectedScene)
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "重命名场景")
                    }
                }
                if (!showUniversal && selectedScene != null) {
                    FilledTonalButton(
                        onClick = { onRegenerate(selectedScene.sceneId) }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("重建模型", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (visibleMatchBreakdown != null && selectedScene != null) {
            Text(
                text = "命中依据：${formatMatchBreakdown(visibleMatchBreakdown, selectedScene)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (visibleConsolidationState != null) {
            Text(
                text = formatClaimConsolidationState(visibleConsolidationState),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    visibleConsolidationState.errorMessage != null -> MaterialTheme.colorScheme.error
                    visibleConsolidationState.isRunning -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DlcSceneChip(
                label = "通用",
                selected = showUniversal,
                isCurrent = false,
                onClick = {
                    showUniversal = true
                    selectedSceneId = null
                }
            )
            sortedScenes.forEach { scene ->
                DlcSceneChip(
                    label = sceneDisplayLabel(scene),
                    selected = !showUniversal && activeSceneId == scene.sceneId,
                    isCurrent = scene.sceneId == currentSceneId,
                    onClick = {
                        showUniversal = false
                        selectedSceneId = scene.sceneId
                    }
                )
            }
        }

        if (!showUniversal && selectedScene != null) {
            Text(
                text = buildString {
                    val tags = listOfNotNull(
                        selectedScene.placeType.takeIf { it.isNotBlank() }?.let { "地点类型 ${placeTypeLabel(it)}" },
                        selectedScene.spaceType.takeIf { it.isNotBlank() }?.let { "空间 ${it}" }
                    )
                    append(
                        if (tags.isEmpty()) {
                            "当前场景尚未补全地点或空间标签。"
                        } else {
                            tags.joinToString(" · ")
                        }
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (visibleClaims.isEmpty()) {
            Text(
                text = if (showUniversal) {
                    "尚无跨场景通用结论。只有同一行为在多个场景中稳定复现后，才会自动提升到通用层。"
                } else {
                    "当前场景尚无行为模型。开始观察后 Agent 会先形成 hypothesis，再逐步收敛为稳定结论。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groupedClaims.forEach { (dimensionKey, dimensionClaims) ->
                    DlcBehaviorDimensionSection(
                        dimensionKey = dimensionKey,
                        claims = dimensionClaims
                    )
                }
            }
        }

        if (!showUniversal) {
            val universalClaims = claims.filter { it.sceneId == null }
            if (universalClaims.isNotEmpty()) {
                DlcBehaviorDimensionSection(
                    dimensionKey = "跨场景通用",
                    claims = universalClaims.take(4).map { claim ->
                        claim.copy(
                            dimensionKey = "跨场景通用",
                            evidenceSummary = buildString {
                                append(claim.dimensionKey)
                                if (claim.evidenceSummary.isNotBlank()) {
                                    append(" · ")
                                    append(claim.evidenceSummary)
                                }
                            }
                        )
                    }
                )
            }
        }

        if (mergeSource != null) {
            val source = mergeSource ?: return@WatcherCard
            val mergeTargets = sortedScenes.filter { it.sceneId != source.sceneId }
            val sourceClaimCount = claims.count { it.sceneId == source.sceneId }
            val sourceGoalCount = allGoals.count { it.sceneId == source.sceneId }
            val sourceReasoningCount = allReasoningLogs.count { it.sceneId == source.sceneId }

            AlertDialog(
                onDismissRequest = {
                    mergeSource = null
                    mergeTargetId = null
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetId = mergeTargetId ?: return@TextButton
                            onMergeScenes(source.sceneId, targetId)
                            mergeSource = null
                            mergeTargetId = null
                        },
                        enabled = mergeTargetId != null
                    ) {
                        Text("确认合并")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            mergeSource = null
                            mergeTargetId = null
                        }
                    ) {
                        Text("取消")
                    }
                },
                title = { Text("合并场景") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "将「${sceneDisplayLabel(source)}」合并到：",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        mergeTargets.forEach { target ->
                            val targetClaimCount = claims.count { it.sceneId == target.sceneId }
                            val targetGoalCount = allGoals.count { it.sceneId == target.sceneId }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (mergeTargetId == target.sceneId) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { mergeTargetId = target.sceneId }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${sceneDisplayLabel(target)}（${placeTypeLabel(target.placeType)}）",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "$targetClaimCount 条 claim, $targetGoalCount 条 goal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            text = "预计迁移：$sourceClaimCount 条 claim, $sourceGoalCount 条 goal, $sourceReasoningCount 条推理记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "合并后源场景将被删除，数据不可恢复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }

        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = renameTarget ?: return@TextButton
                            onRenameScene(target.sceneId, renameDraft)
                            renameTarget = null
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text("取消")
                    }
                },
                title = { Text("重命名场景") },
                text = {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text("场景标签") },
                        singleLine = true
                    )
                }
            )
        }
    }
}

@Composable
private fun DlcBehaviorDimensionSection(
    dimensionKey: String,
    claims: List<BehaviorClaim>
) {
    val accent = dimensionAccent(dimensionKey)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = dimensionKey.ifBlank { "未命名维度" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (claims.isEmpty()) {
            Text(
                text = "暂无结论",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 14.dp)
            )
            return
        }

        claims
            .sortedWith(compareBy({ claimStatusRank(it.status) }, { -it.updatedAt }))
            .take(4)
            .forEach { claim ->
                DlcBehaviorClaimRow(claim = claim, accent = accent)
            }
    }
}

@Composable
private fun DlcBehaviorClaimRow(
    claim: BehaviorClaim,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                text = BehaviorClaimStatuses.displayNameOf(claim.status),
                accent = when (claim.status) {
                    BehaviorClaimStatuses.STABLE -> accent
                    BehaviorClaimStatuses.EMERGING -> MaterialTheme.colorScheme.secondary
                    BehaviorClaimStatuses.CONFLICTED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
            )
            Text(
                text = "证据 ${claim.evidenceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "${(claim.confidenceScore * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = claim.claimText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 14.dp)
        )
        if (claim.evidenceSummary.isNotBlank()) {
            Text(
                text = claim.evidenceSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}

@Composable
private fun DlcSceneChip(
    label: String,
    selected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isCurrent) {
                Text(
                    text = "当前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatClaimConsolidationState(state: ClaimConsolidationUiState): String {
    state.errorMessage?.let { return "归一失败：$it" }
    if (state.isRunning) {
        return "正在对当前场景 claims 做归一去重，请等待结果写回。"
    }
    return when (state.reason) {
        "merged" -> buildString {
            append("归一完成：已合并 ")
            append(state.mergedCount)
            append(" 组")
            if (state.summaries.isNotEmpty()) {
                append("（")
                append(state.summaries.joinToString("；").take(140))
                append("）")
            }
        }

        "not_enough_claims" -> "归一跳过：当前场景 claim 少于 2 条。"
        "no_merge_candidates" -> "归一完成：未发现可安全合并的重复 claim。"
        else -> "可手动触发 claim 归一测试。"
    }
}

private fun buildClaimExportPayload(
    selectedScene: SceneProfile?,
    currentSceneId: String?,
    currentSceneLabel: String,
    visibleClaims: List<BehaviorClaim>,
    showUniversal: Boolean
): String {
    val sceneLabel = if (showUniversal) {
        "跨场景通用层"
    } else {
        selectedScene?.let(::sceneDisplayLabel).orEmpty().ifBlank { currentSceneLabel }
    }
    val sceneId = if (showUniversal) "universal" else (selectedScene?.sceneId ?: currentSceneId.orEmpty())
    return buildString {
        appendLine("{")
        appendLine("  \"exportType\": \"behavior_claims\",")
        appendLine("  \"viewScope\": \"${if (showUniversal) "universal" else "scene"}\",")
        appendLine("  \"sceneLabel\": \"${jsonEscape(sceneLabel)}\",")
        appendLine("  \"sceneId\": \"${jsonEscape(sceneId)}\",")
        appendLine("  \"claimCount\": ${visibleClaims.size},")
        appendLine("  \"claims\": [")
        visibleClaims
            .sortedWith(compareBy({ claimStatusRank(it.status) }, { it.dimensionKey }, { -it.updatedAt }))
            .forEachIndexed { index, claim ->
                appendLine("    {")
                appendLine("      \"claimId\": \"${jsonEscape(claim.claimId)}\",")
                appendLine("      \"sceneId\": ${claim.sceneId?.let { "\"${jsonEscape(it)}\"" } ?: "null"},")
                appendLine("      \"dimensionKey\": \"${jsonEscape(claim.dimensionKey)}\",")
                appendLine("      \"claimText\": \"${jsonEscape(claim.claimText)}\",")
                appendLine("      \"status\": \"${jsonEscape(claim.status)}\",")
                appendLine("      \"confidenceScore\": ${"%.4f".format(Locale.US, claim.confidenceScore)},")
                appendLine("      \"evidenceCount\": ${claim.evidenceCount},")
                appendLine("      \"evidenceSummary\": \"${jsonEscape(claim.evidenceSummary)}\",")
                appendLine("      \"firstObservedAt\": ${claim.firstObservedAt},")
                appendLine("      \"lastObservedAt\": ${claim.lastObservedAt},")
                append("      \"updatedAt\": ${claim.updatedAt}")
                appendLine()
                append("    }")
                if (index != visibleClaims.lastIndex) append(",")
                appendLine()
            }
        appendLine("  ]")
        appendLine("}")
    }
}

private fun jsonEscape(raw: String): String {
    return raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
