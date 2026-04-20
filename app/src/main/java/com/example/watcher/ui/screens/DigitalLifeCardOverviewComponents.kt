package com.example.watcher.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.CommentaryEntryStatus
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.VideoStreamSettings
import com.example.watcher.ui.components.CameraPreviewCard
import com.example.watcher.ui.components.MjpegStreamUiState
import com.example.watcher.ui.components.StatusPill
import com.example.watcher.ui.components.WatcherCard
import com.example.watcher.ui.theme.LocalWatcherExtendedColors
import com.example.watcher.ui.viewmodel.ObservationControlPhase
import com.example.watcher.ui.viewmodel.ObservationControlState

@Composable
internal fun DlcSectionIntro(
    eyebrow: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DlcCommentaryCard(
    state: LiveCommentaryState,
    controlState: ObservationControlState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStopAgent: () -> Unit,
    onReset: () -> Unit
) {
    WatcherCard {
        Text(
            text = "实时观察流",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Producer 录制片段，B/C 生成客观观察，结果持续写入 Blackboard 当前会话。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = when (controlState.phase) {
                    ObservationControlPhase.Idle -> "未启动"
                    ObservationControlPhase.StartingAgent -> "启动 Agent"
                    ObservationControlPhase.StartingObservation -> "接入观察流"
                    ObservationControlPhase.Running -> "运行中"
                    ObservationControlPhase.StoppingObservation -> "停止观察"
                    ObservationControlPhase.AwaitingDrain -> "等待收尾"
                    ObservationControlPhase.Consolidating -> "Agent 收敛"
                    ObservationControlPhase.StoppingAgent -> "停止 Agent"
                    ObservationControlPhase.Error -> "启动异常"
                },
                accent = when (controlState.phase) {
                    ObservationControlPhase.Running -> MaterialTheme.colorScheme.primary
                    ObservationControlPhase.Consolidating -> MaterialTheme.colorScheme.secondary
                    ObservationControlPhase.Error -> MaterialTheme.colorScheme.error
                    ObservationControlPhase.Idle -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )
            Text(
                text = controlState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (
                state.isActive ||
                controlState.phase == ObservationControlPhase.StoppingObservation ||
                controlState.phase == ObservationControlPhase.AwaitingDrain
            ) {
                Button(
                    onClick = onStop,
                    enabled = controlState.phase != ObservationControlPhase.StoppingObservation &&
                        controlState.phase != ObservationControlPhase.AwaitingDrain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(18.dp))
                    Spacer(androidx.compose.ui.Modifier.size(6.dp))
                    Text(
                        if (controlState.phase == ObservationControlPhase.StoppingObservation) {
                            "停止中"
                        } else if (controlState.phase == ObservationControlPhase.AwaitingDrain) {
                            "收尾中"
                        } else {
                            "停止观察"
                        }
                    )
                }
            } else if (
                controlState.phase == ObservationControlPhase.Consolidating ||
                controlState.phase == ObservationControlPhase.StoppingAgent
            ) {
                Button(
                    onClick = onStopAgent,
                    enabled = controlState.phase != ObservationControlPhase.StoppingAgent,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(18.dp))
                    Spacer(androidx.compose.ui.Modifier.size(6.dp))
                    Text(
                        if (controlState.phase == ObservationControlPhase.StoppingAgent) {
                            "停止中"
                        } else {
                            "停止 Agent"
                        }
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = !controlState.isBusy
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(18.dp))
                    Spacer(androidx.compose.ui.Modifier.size(6.dp))
                    Text(
                        when (controlState.phase) {
                            ObservationControlPhase.StartingAgent -> "正在启动 Agent"
                            ObservationControlPhase.StartingObservation -> "正在接入观察流"
                            ObservationControlPhase.AwaitingDrain -> "正在等待收尾"
                            ObservationControlPhase.Consolidating -> "Agent 收敛中"
                            ObservationControlPhase.StoppingAgent -> "正在停止 Agent"
                            else -> "开始观察"
                        }
                    )
                }
            }
            FilledTonalButton(
                onClick = onReset,
                enabled = !state.isActive && !controlState.isBusy
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(18.dp))
                Spacer(androidx.compose.ui.Modifier.size(6.dp))
                Text("重置")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = "录制 ${state.recordedSegmentCount}",
                accent = MaterialTheme.colorScheme.primary
            )
            StatusPill(
                text = "分析 ${state.analyzedSegmentCount}",
                accent = MaterialTheme.colorScheme.tertiary
            )
            if (state.sessionId.isNotBlank()) {
                StatusPill(
                    text = "session ${state.sessionId.takeLast(6)}",
                    accent = MaterialTheme.colorScheme.outline
                )
            }
            if (state.isActive) {
                StatusPill(
                    text = "运行中",
                    accent = MaterialTheme.colorScheme.secondary
                )
            } else if (state.isDraining || controlState.phase == ObservationControlPhase.AwaitingDrain) {
                StatusPill(
                    text = "排空中",
                    accent = MaterialTheme.colorScheme.tertiary
                )
            } else if (controlState.phase == ObservationControlPhase.Consolidating) {
                StatusPill(
                    text = "收敛中",
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        if (state.entries.isNotEmpty()) {
            Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                LazyColumn(
                    modifier = androidx.compose.ui.Modifier
                        .heightIn(max = 400.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.entries, key = { "${it.sessionId}-${it.segmentIndex}" }) { entry ->
                        DlcCommentaryEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DlcCommentaryEntryRow(entry: CommentaryEntry) {
    val statusColor = when (entry.status) {
        CommentaryEntryStatus.Completed -> MaterialTheme.colorScheme.primary
        CommentaryEntryStatus.Streaming -> MaterialTheme.colorScheme.tertiary
        CommentaryEntryStatus.Failed -> MaterialTheme.colorScheme.error
        CommentaryEntryStatus.Skipped -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusLabel = when (entry.status) {
        CommentaryEntryStatus.Recording -> "录制中"
        CommentaryEntryStatus.Uploading -> "上传中"
        CommentaryEntryStatus.Processing -> "处理中"
        CommentaryEntryStatus.Analyzing -> "分析中"
        CommentaryEntryStatus.Streaming -> "流式输出"
        CommentaryEntryStatus.Completed -> "完成"
        CommentaryEntryStatus.Skipped -> "跳过"
        CommentaryEntryStatus.Failed -> "失败"
    }

    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .size(8.dp)
                    .background(
                        color = statusColor,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            Text(
                text = entry.displayTimestamp,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "#${entry.segmentIndex}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }

        val displayText = when {
            entry.status == CommentaryEntryStatus.Streaming && entry.streamingText.isNotBlank() ->
                entry.streamingText
            entry.text.isNotBlank() -> entry.text
            else -> null
        }
        if (displayText != null) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }
    }
}

@Composable
internal fun DigitalLifeMissionCard(
    currentSceneLabel: String,
    universalClaimCount: Int
) {
    WatcherCard {
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "模块目标",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "让 Blackboard 先稳定承接观察事实和共享上下文，再以场景为顶层索引沉淀行为模型，并在多个场景复现后提升为通用模式。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = LocalWatcherExtendedColors.current.surfaceContainer
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(text = currentSceneLabel, accent = MaterialTheme.colorScheme.primary)
            StatusPill(text = "通用 $universalClaimCount", accent = MaterialTheme.colorScheme.tertiary)
            StatusPill(text = "主动补证据", accent = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun DigitalLifeVideoAnchorCard(
    settings: VideoStreamSettings,
    streamState: MjpegStreamUiState,
    isStreamPlaying: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onReconnectStream: () -> Unit,
    onCaptureSnapshot: (Bitmap) -> Unit,
    onOpenSettings: () -> Unit
) {
    WatcherCard {
        Text(
            text = "实时视频锚点",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "后续观察、解说、行为建模和补证据请求都会围绕这个视频窗口展开。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CameraPreviewCard(
            title = "Digital Life Stream",
            subtitle = settings.streamDisplayUrl,
            streamState = streamState,
            isPlaying = isStreamPlaying,
            onPlayingChange = onPlayingChange,
            onReconnect = onReconnectStream,
            onCaptureSnapshot = onCaptureSnapshot,
            onOpenSettings = onOpenSettings,
            compact = true,
            showAiBadge = true
        )
    }
}

@Composable
internal fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}
