package com.example.watcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.BehaviorReasoningLog
import com.example.watcher.data.model.ObservationGoal
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun DlcObservationGoalsCard(
    goals: List<ObservationGoal>,
    currentSceneLabel: String
) {
    WatcherCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "待补证据",
                style = MaterialTheme.typography.titleLarge
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Icon(Icons.Default.TrackChanges, contentDescription = null)
            }
        }
        Text(
            text = "当前场景：$currentSceneLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (goals.isEmpty()) {
            Text(
                text = "当前没有待追问的问题。Agent 认为现有观察足够继续收敛模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            return@WatcherCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            goals
                .sortedWith(compareByDescending<ObservationGoal> { it.priority }.thenByDescending { it.updatedAt })
                .take(6)
                .forEach { goal ->
                    DlcObservationGoalRow(goal)
                }
        }
    }
}

@Composable
private fun DlcObservationGoalRow(goal: ObservationGoal) {
    val accent = dimensionAccent(goal.dimensionKey)

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
                    text = goal.dimensionKey.ifBlank { "未命名维度" },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Text(
                    text = "P${goal.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = goal.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun DlcBehaviorReasoningCard(reasoningLogs: List<BehaviorReasoningLog>) {
    WatcherCard {
        Text(
            text = "行为推理轨迹",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "这里显示 Agent 针对行为模型写下的中间推断，不属于 blackboard 观察事实。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (reasoningLogs.isEmpty()) {
            Text(
                text = "暂无推理轨迹。Agent 收到观察后会先记录中间判断，再决定是否提升为 claim 或发起补证据请求。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            return@WatcherCard
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reasoningLogs.take(10).forEach { log ->
                    DlcReasoningLogRow(log)
                }
            }
        }
    }
}

@Composable
private fun DlcReasoningLogRow(log: BehaviorReasoningLog) {
    val confidenceColor = when (log.confidence) {
        "high" -> MaterialTheme.colorScheme.primary
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(confidenceColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = log.dimensionKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = confidenceColor
                )
                Text(
                    text = log.confidence,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = log.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (log.basis.isNotBlank()) {
                Text(
                    text = log.basis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
