package com.example.watcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.repository.PortraitCuratorActivityEntry
import com.example.watcher.data.repository.PortraitCuratorMemoryDebugState
import com.example.watcher.data.repository.PortraitCuratorStatus
import com.example.watcher.ui.components.StatusPill
import com.example.watcher.ui.components.WatcherCard
import com.example.watcher.ui.viewmodel.BlackboardDebugObservation
import com.example.watcher.ui.viewmodel.BlackboardDebugUiState

@Composable
internal fun DlcAgentStatusCard(status: PortraitCuratorStatus) {
    WatcherCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Blackboard 协作 Agent",
                style = MaterialTheme.typography.titleMedium
            )
            when (status) {
                is PortraitCuratorStatus.Idle -> StatusPill(
                    text = "待机",
                    accent = MaterialTheme.colorScheme.outline
                )
                is PortraitCuratorStatus.Running -> StatusPill(
                    text = status.lifecycleState,
                    accent = if (status.isTerminal || status.stopReason != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        when (status) {
            is PortraitCuratorStatus.Idle -> {
                Text(
                    text = "开始观察后 Agent 将自动启动，持续把观察流沉淀成行为 claim，并主动提出待补证据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is PortraitCuratorStatus.Running -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = "信号 ${status.signalCount}",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                    StatusPill(
                        text = "输出 ${status.outputCount}",
                        accent = MaterialTheme.colorScheme.secondary
                    )
                    StatusPill(
                        text = "周期 ${status.cycle}",
                        accent = MaterialTheme.colorScheme.primary
                    )
                    StatusPill(
                        text = "空转 ${status.idleCount}",
                        accent = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "最近活跃: ${formatRelativeRuntimeTime(status.updatedAt)}  |  runtime ${status.runtimeId.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status.lastValidationStatus?.let { validation ->
                    Text(
                        text = "最近校验: $validation${status.lastValidationFeedback?.let { " / $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                status.lastOutputPreview?.takeIf { it.isNotBlank() }?.let { output ->
                    Text(
                        text = "最近输出: $output",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (status.stopReason != null) {
                    Text(
                        text = "停止原因: ${status.stopReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (status.error != null) {
                    Text(
                        text = "错误: ${status.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun DlcAgentActivityCard(
    entries: List<PortraitCuratorActivityEntry>,
    onExport: (List<PortraitCuratorActivityEntry>) -> Unit = {}
) {
    WatcherCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Agent 活动轨迹",
                style = MaterialTheme.typography.titleLarge
            )
            if (entries.isNotEmpty()) {
                IconButton(onClick = { onExport(entries) }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "导出日志",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = "直接查看 Agent 最近读取了什么、写入了什么、提交了哪些请求。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (entries.isEmpty()) {
            Text(
                text = "暂无活动记录。启动 Agent 后，这里会显示 signal 提交、工具读取、claim 写入和补证据请求。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            return@WatcherCard
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DlcAgentActivityRow(entry)
                }
            }
        }
    }
}

@Composable
internal fun DlcAgentMemoryCard(state: PortraitCuratorMemoryDebugState) {
    WatcherCard {
        Text(
            text = "Agent Memory",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "直接查看 curator 当前 working / episodic / knowledge 的最近条目，判断它是否真的在利用记忆。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = "working ${state.workingEntries.size}",
                accent = MaterialTheme.colorScheme.primary
            )
            StatusPill(
                text = "episodic ${state.episodicEntries.size}",
                accent = MaterialTheme.colorScheme.tertiary
            )
            StatusPill(
                text = "knowledge ${state.knowledgeEntries.size}",
                accent = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = "structured memory: short=${state.structuredShortTermCount}, working=${state.structuredWorkingCount}, long=${state.structuredLongTermCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (
            state.workingEntries.isEmpty() &&
            state.episodicEntries.isEmpty() &&
            state.knowledgeEntries.isEmpty()
        ) {
            Text(
                text = "暂无 memory 调试数据。启动 Agent 并运行至少一轮后，这里会显示最近的记忆条目。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            return@WatcherCard
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemoryEntrySection(
                    title = "Working",
                    entries = state.workingEntries,
                    accent = MaterialTheme.colorScheme.primary
                )
                MemoryEntrySection(
                    title = "Episodic",
                    entries = state.episodicEntries,
                    accent = MaterialTheme.colorScheme.tertiary
                )
                MemoryEntrySection(
                    title = "Knowledge",
                    entries = state.knowledgeEntries,
                    accent = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun DlcAgentActivityRow(entry: PortraitCuratorActivityEntry) {
    val accent = when (entry.status) {
        "error" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.secondary
        "success" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatDebugTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = entry.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemoryEntrySection(
    title: String,
    entries: List<com.example.watcher.data.repository.PortraitCuratorMemoryEntry>,
    accent: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (entries.isEmpty()) {
            Text(
                text = "暂无",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            return@Column
        }
        entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        text = formatDebugTimestamp(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entry.tags.isNotEmpty()) {
                        Text(
                            text = entry.tags.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        }
    }
}

@Composable
internal fun DlcMemoryStatusCard(state: LiveCommentaryState) {
    WatcherCard {
        Text(
            text = "Blackboard 共享状态",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "当前会话中沉淀下来的场景、实体、动态和压缩记忆，供 Agent 持续读取与更新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.scenePhase.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "场景阶段",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = state.scenePhase,
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        if (state.sessionId.isNotBlank()) {
            Text(
                text = "当前 session: ${state.sessionId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (state.isDraining) {
            StatusPill(
                text = "观察流排空中",
                accent = MaterialTheme.colorScheme.secondary
            )
        }

        MemorySection(label = "场景记忆", content = state.sceneMemory, accent = MaterialTheme.colorScheme.tertiary)
        MemorySection(label = "实体记忆", content = state.entityMemory, accent = MaterialTheme.colorScheme.primary)
        MemorySection(label = "动态摘要", content = state.actionSummary, accent = MaterialTheme.colorScheme.secondary)

        if (state.pendingAsks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "建设者请求",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.pendingAsks.forEach { ask ->
                    Text(
                        text = "- $ask",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        MemorySection(label = "核心记忆 A", content = state.memoryA, accent = MaterialTheme.colorScheme.error)
        MemorySection(label = "近期记忆 B", content = state.latestMemoryB, accent = MaterialTheme.colorScheme.secondary)

        if (state.sceneMemory.isBlank() && state.memoryA.isBlank() && state.entityMemory.isBlank()) {
            Text(
                text = "尚无记忆数据。开始观察后将逐步构建场景记忆与压缩记忆。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
internal fun DlcBlackboardDebugCard(state: BlackboardDebugUiState) {
    WatcherCard {
        Text(
            text = "Blackboard 调试面板",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "展开查看当前会话里有哪些信息正在 Blackboard 中流转，以及最近写入了哪些客观观察。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.sessionId.isNotBlank()) {
                StatusPill(
                    text = "session ${state.sessionId.takeLast(6)}",
                    accent = MaterialTheme.colorScheme.outline
                )
            }
            StatusPill(
                text = "observation ${state.observationItemCount}",
                accent = MaterialTheme.colorScheme.primary
            )
            StatusPill(
                text = "已持久化 ${state.persistedSegmentCount}",
                accent = MaterialTheme.colorScheme.tertiary
            )
            StatusPill(
                text = "已喂 Agent ${state.fedSignalCount}",
                accent = MaterialTheme.colorScheme.secondary
            )
        }

        if (state.lastPersistedSegmentIndex != null || state.lastFedSignalSummary.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.lastPersistedSegmentIndex != null) {
                    Text(
                        text = "最近一次持久化片段: #${state.lastPersistedSegmentIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.lastFedSignalSummary.isNotBlank()) {
                    Text(
                        text = "最近一次喂给 Agent 的信号: ${state.lastFedSignalSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (state.sharedKeys.isNotEmpty()) {
            Text(
                text = "当前共享字段",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.sharedKeys.forEach { key ->
                    StatusPill(
                        text = key,
                        accent = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        if (state.sharedFields.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.sharedFields.forEach { field ->
                    MemorySection(
                        label = field.key,
                        content = field.content,
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        if (state.categoryCounts.isNotEmpty()) {
            Text(
                text = "Observation 分类计数",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categoryCounts.forEach { item ->
                    StatusPill(
                        text = "${item.category} ${item.count}",
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        if (state.recentObservations.isNotEmpty()) {
            Text(
                text = "最近写入 Blackboard 的 observation",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.recentObservations, key = { "${it.segmentIndex}-${it.category}-${it.content}" }) { item ->
                        DlcBlackboardObservationRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun DlcBlackboardObservationRow(item: BlackboardDebugObservation) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "#${item.segmentIndex}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (item.dimensionHint.isNotBlank()) {
                    Text(
                        text = item.dimensionHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MemorySection(
    label: String,
    content: String,
    accent: androidx.compose.ui.graphics.Color
) {
    if (content.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun formatDebugTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun formatRelativeRuntimeTime(timestamp: Long): String {
    val deltaSeconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
    return when {
        deltaSeconds < 5L -> "刚刚"
        deltaSeconds < 60L -> "${deltaSeconds}s 前"
        deltaSeconds < 3600L -> "${deltaSeconds / 60L}m 前"
        else -> "${deltaSeconds / 3600L}h 前"
    }
}

internal fun formatActivityLogForExport(entries: List<PortraitCuratorActivityEntry>): String = buildString {
    appendLine("=== Agent Activity Log (${entries.size} entries) ===")
    appendLine()
    entries.forEach { entry ->
        append("[${formatDebugTimestamp(entry.timestamp)}] ")
        append("${entry.type} | ${entry.summary}")
        if (entry.status != "info") append(" [${entry.status}]")
        appendLine()
        if (entry.detail.isNotBlank()) {
            entry.detail.lines().forEach { line ->
                appendLine("  $line")
            }
        }
    }
}
