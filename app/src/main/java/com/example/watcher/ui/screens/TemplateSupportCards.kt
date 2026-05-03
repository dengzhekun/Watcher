package com.example.watcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.model.MemorySnapshot
import com.example.watcher.data.repository.PendingAudienceImport
import com.example.watcher.data.repository.PendingCouncilImport
import com.example.watcher.data.repository.TemplateShareManager
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun MemorySystemCard(getMemorySnapshot: () -> MemorySnapshot) {
    var expanded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<MemorySnapshot?>(null) }

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("记忆系统", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { snapshot = getMemorySnapshot() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    TextButton(onClick = {
                        if (snapshot == null) snapshot = getMemorySnapshot()
                        expanded = !expanded
                    }) {
                        Text(if (expanded) "收起" else "查看")
                    }
                }
            }

            AnimatedVisibility(visible = expanded && snapshot != null) {
                val currentSnapshot = snapshot ?: return@AnimatedVisibility
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MemoryBlock("核心记忆 A", currentSnapshot.memoryA.ifBlank { "（空）" })
                    MemoryBlock("近期摘要 B", currentSnapshot.memoryB.ifBlank { "（空）" })
                    MemoryBlock(
                        title = "近期视觉记录（${currentSnapshot.recentVisual.size}）",
                        content = currentSnapshot.recentVisual
                            .joinToString("\n") { (_, text) -> "• $text" }
                            .ifBlank { "（空）" }
                    )
                    Text(
                        text = "待压缩缓冲区：${currentSnapshot.rawBufferSize} 条（达到 10 条后开始压缩到 B 层）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryBlock(title: String, content: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Surface(
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
internal fun TemplateImportCard(onImport: (String, (String) -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("导入模板", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "支持导入监控模板、视频分析模板、智囊团模板和专家配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("分享文本") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        placeholder = { Text("将分享得到的模板文本粘贴到这里") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            importText = clipboardManager.getText()?.text.orEmpty()
                        }) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("粘贴")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (importText.isNotBlank()) {
                                    onImport(importText) { message ->
                                        resultMessage = message
                                        val isSuccess =
                                            message.contains("success", ignoreCase = true) ||
                                                message.contains("\u6210\u529f")
                                        if (isSuccess) importText = ""
                                    }
                                }
                            },
                            enabled = importText.isNotBlank() && TemplateShareManager.canImport(importText)
                        ) {
                            Text("导入")
                        }
                    }
                    resultMessage?.let { message ->
                        val isSuccess =
                            message.contains("success", ignoreCase = true) ||
                                message.contains("\u6210\u529f")
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PendingHiddenWorkbenchImportsCard(
    pendingAudienceImport: PendingAudienceImport?,
    pendingCouncilImport: PendingCouncilImport?,
    providers: List<LlmProviderEntity>,
    onApplyAudienceImport: ((String) -> Unit) -> Unit,
    onApplyCouncilImport: ((String) -> Unit) -> Unit
) {
    if (pendingAudienceImport == null && pendingCouncilImport == null) {
        return
    }

    var expanded by remember { mutableStateOf(true) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val preferredProvider = providers.firstOrNull { it.enabled } ?: providers.firstOrNull()
    val audienceProvider = pendingAudienceImport?.providerIdHint
        ?.let { providerId -> providers.firstOrNull { it.id == providerId } }
        ?: preferredProvider

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("导入暂存", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "承接 XMAX 已导入但还没真正落库的 AI 观众和专家团配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    pendingAudienceImport?.let { audience ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("AI 观众导入草稿", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${audience.roomName} · ${audience.sourceLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("关注点：${audience.focusPrompt}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "风格：${audience.responseStyle.ifBlank { "未提供" }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = audienceProvider?.let { "将使用 Provider：${it.name}" }
                                        ?: pendingAudienceImport.providerIdHint?.let {
                                            "暂存 Provider ID：$it；应用后会先停用，补接口后再启用。"
                                        }
                                        ?: "当前没有可用 Provider；应用后会先停用，补接口后再启用。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = { onApplyAudienceImport { resultMessage = it } }
                                ) {
                                    Text("应用为 AI 观众")
                                }
                            }
                        }
                    }

                    pendingCouncilImport?.let { council ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("专家团导入草稿", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${council.topic} · ${council.sourceLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "角色：${council.memberRoles.joinToString("、").ifBlank { "未提供" }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "流程：${council.workflow.ifBlank { "未提供" }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FilledTonalButton(
                                    onClick = { onApplyCouncilImport { resultMessage = it } }
                                ) {
                                    Text("应用为智囊团模板")
                                }
                            }
                        }
                    }

                    resultMessage?.let { message ->
                        val isSuccess = message.contains("成功") ||
                            message.contains("已导入") ||
                            message.contains("已更新")
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SceneMemoryCard(commentaryState: LiveCommentaryState) {
    var expanded by remember { mutableStateOf(false) }

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("场景记忆（3 层）", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (commentaryState.scenePhase.isNotBlank()) {
                        Text(
                            text = commentaryState.scenePhase,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "查看")
                    }
                }
            }

            val entityCount = commentaryState.entityMemory.lines().count { it.isNotBlank() }
            val askCount = commentaryState.pendingAsks.size
            Text(
                text = "场景${if (commentaryState.sceneMemory.isNotBlank()) "已就绪" else "为空"} | " +
                    "实体 $entityCount | ASK 请求 $askCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MemoryBlock(
                        title = "第 1 层 | 固定场景",
                        content = commentaryState.sceneMemory.ifBlank { "（空）" }
                    )
                    MemoryBlock(
                        title = "第 2 层 | 实体（$entityCount）",
                        content = commentaryState.entityMemory.ifBlank { "（空）" }
                    )
                    MemoryBlock(
                        title = "第 3 层 | 动态摘要",
                        content = commentaryState.actionSummary.ifBlank { "（空）" }
                    )
                    if (commentaryState.pendingAsks.isNotEmpty()) {
                        Column {
                            Text("ASK 请求", style = MaterialTheme.typography.labelMedium)
                            commentaryState.pendingAsks.forEach { ask ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    tonalElevation = 4.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "• $ask",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
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
