package com.example.watcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.AgentAudienceDebugSnapshot
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.ui.components.AiAudienceConfigDialog
import com.example.watcher.ui.components.EmptyHint
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun AiAudienceManagementCard(
    providers: List<LlmProviderEntity>,
    audiences: List<AiAudienceEntity>,
    onSaveProvider: (LlmProviderEntity) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSaveAudience: (AiAudienceEntity) -> Unit,
    onDeleteAudience: (Long) -> Unit,
    getLastPost: (Long) -> String?,
    getLastResponse: (Long) -> String?,
    getAgentDebugSnapshot: (AiAudienceEntity) -> AgentAudienceDebugSnapshot?,
    getWallet: (Long) -> Int,
    setWallet: (Long, Int) -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val enabledCount = audiences.count { it.enabled }
    val agentCount = audiences.count { it.audienceType == AudienceEngineType.Agent }
    val classicCount = audiences.size - agentCount
    val summary = when {
        audiences.isEmpty() -> "暂未配置 AI 观众。"
        else -> buildString {
            append("${audiences.size} 位观众")
            append(" · 已启用 $enabledCount 位")
            append(" · Agent $agentCount 位")
            if (classicCount > 0) {
                append(" · 经典 $classicCount 位")
            }
        }
    }

    if (showConfigDialog) {
        AiAudienceConfigDialog(
            providers = providers,
            audiences = audiences,
            onSaveProvider = onSaveProvider,
            onDeleteProvider = onDeleteProvider,
            onSaveAudience = onSaveAudience,
            onDeleteAudience = onDeleteAudience,
            onDismiss = { showConfigDialog = false }
        )
    }

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("AI 观众", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起 AI 观众卡片" else "展开 AI 观众卡片"
                        )
                    }
                    FilledTonalButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("配置")
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (audiences.isEmpty()) {
                        EmptyHint("暂无 AI 观众")
                    } else {
                        audiences.forEach { audience ->
                            var audienceExpanded by remember(audience.id) { mutableStateOf(false) }
                            val wallet = getWallet(audience.id)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(audience.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "${audience.audienceType.label} | 钱包 $wallet | ${
                                                    if (audience.enabled) "已启用" else "已停用"
                                                } | ${audience.heartbeatIntervalSeconds} 秒",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { audienceExpanded = !audienceExpanded }) {
                                                Text(if (audienceExpanded) "收起" else "详情")
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = audienceExpanded) {
                                        Column(
                                            modifier = Modifier.padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("人设", style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                text = audience.persona.take(200),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Text("个人记忆", style = MaterialTheme.typography.labelMedium)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                tonalElevation = 4.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = audience.personalMemory.ifBlank {
                                                        "暂无记忆，直播模式结束后会自动生成。"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    color = if (audience.personalMemory.isNotBlank()) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }

                                            if (audience.audienceType == AudienceEngineType.Agent) {
                                                Text("Agent 运行状态", style = MaterialTheme.typography.labelMedium)
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    tonalElevation = 4.dp,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = formatAgentDebugSnapshot(getAgentDebugSnapshot(audience)),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(8.dp),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }

                                            Text("最近一次完整提示词", style = MaterialTheme.typography.labelMedium)
                                            val post = getLastPost(audience.id)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (post != null) {
                                                    TextButton(onClick = {
                                                        clipboardManager.setText(AnnotatedString(post))
                                                    }) {
                                                        Text("复制")
                                                    }
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                tonalElevation = 4.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = post ?: "暂无数据",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    color = if (post != null) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }

                                            Text("最近一次 AI 回复", style = MaterialTheme.typography.labelMedium)
                                            val response = getLastResponse(audience.id)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                tonalElevation = 4.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = response ?: "暂无数据",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    color = if (response != null) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatAgentDebugSnapshot(snapshot: AgentAudienceDebugSnapshot?): String {
    if (snapshot == null) {
        return "暂无运行状态，请先在直播模式中触发一次该 Agent。"
    }

    val inboxText = snapshot.socialInbox.take(4).joinToString("\n") { "- $it" }.ifBlank { "无" }
    val relationText = snapshot.relationSummaries.take(4).joinToString("\n") { "- $it" }.ifBlank { "无" }
    val targetText = snapshot.recentInteractionTargets.joinToString("、").ifBlank { "无" }

    return buildString {
        appendLine("情绪：${snapshot.emotion}（${snapshot.emotionIntensity}/100）")
        appendLine("目标：${snapshot.currentGoal}")
        appendLine("关注点：${snapshot.focusTarget}")
        appendLine("最近动作：${snapshot.lastActionSummary}")
        appendLine("是否已入场：${if (snapshot.hasEntered) "是" else "否"}")
        appendLine("沉默连击：${snapshot.silenceStreak}")
        appendLine("社交原型：${snapshot.socialArchetype}")
        appendLine("说话风格：${snapshot.speakingStyle}")
        appendLine("消费风格：${snapshot.spendingStyle}")
        appendLine("社交驱动力：${snapshot.socialDrive}")
        appendLine("最近互动对象：$targetText")
        appendLine("社交收件箱：")
        appendLine(inboxText)
        appendLine("关系概览：")
        append(relationText)
    }
}
