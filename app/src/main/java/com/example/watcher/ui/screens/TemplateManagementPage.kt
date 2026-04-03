package com.example.watcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.model.MemorySnapshot
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.watcher.data.model.AgentAudienceDebugSnapshot
import com.example.watcher.data.repository.TemplateShareManager
import com.example.watcher.ui.components.AiAudienceConfigDialog
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.VideoTemplateEntity
import com.example.watcher.ui.components.ActionRow
import com.example.watcher.ui.components.EmptyHint
import com.example.watcher.ui.components.FormField
import com.example.watcher.ui.components.MotionDepth
import com.example.watcher.ui.components.MotionStageSection
import com.example.watcher.ui.components.PageScaffold
import com.example.watcher.ui.components.WatcherCard
import com.example.watcher.ui.components.WatcherTopBar

@Composable
internal fun TemplateManagementPage(
    monitorTemplates: List<MonitorTemplateEntity>,
    videoTemplates: List<VideoTemplateEntity>,
    providers: List<LlmProviderEntity>,
    audiences: List<AiAudienceEntity>,
    onUpdateMonitorTemplate: (MonitorTemplateEntity) -> Unit,
    onUpdateVideoTemplate: (VideoTemplateEntity) -> Unit,
    onResetMonitorTemplate: (String) -> Unit,
    onResetVideoTemplate: (String) -> Unit,
    onDeleteMonitorTemplate: (String) -> Unit,
    onDeleteVideoTemplate: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSaveProvider: (LlmProviderEntity) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSaveAudience: (AiAudienceEntity) -> Unit,
    onDeleteAudience: (Long) -> Unit,
    getLastPost: (Long) -> String?,
    getLastResponse: (Long) -> String?,
    getAgentDebugSnapshot: (AiAudienceEntity) -> AgentAudienceDebugSnapshot?,
    getWallet: (Long) -> Int,
    setWallet: (Long, Int) -> Unit,
    getMemorySnapshot: () -> MemorySnapshot,
    commentaryState: LiveCommentaryState,
    onExportMonitor: (MonitorTemplateEntity) -> String,
    onExportVideo: (VideoTemplateEntity) -> String,
    onImportTemplate: (String, (String) -> Unit) -> Unit,
    currentPage: HubPage,
    pageOffset: Float
) {
    val header = workspaceHeaderFor(currentPage)

    PageScaffold(page = currentPage, pageOffset = pageOffset) {
        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Header) {
            WatcherTopBar(
                eyebrow = header.eyebrow,
                title = header.title,
                subtitle = header.subtitle,
                currentPage = currentPage,
                pageOffset = pageOffset,
                onOpenSettings = onOpenSettings
            )
        }

        // Template import card
        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Support) {
            TemplateImportCard(onImport = onImportTemplate)
        }

        // Live room management sections
        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Support) {
            AiAudienceManagementCard(
                providers = providers,
                audiences = audiences,
                onSaveProvider = onSaveProvider,
                onDeleteProvider = onDeleteProvider,
                onSaveAudience = onSaveAudience,
                onDeleteAudience = onDeleteAudience,
                getLastPost = getLastPost,
                getLastResponse = getLastResponse,
                getAgentDebugSnapshot = getAgentDebugSnapshot,
                getWallet = getWallet,
                setWallet = setWallet
            )
        }

        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Support) {
            MemorySystemCard(getMemorySnapshot = getMemorySnapshot)
        }

        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Support) {
            SceneMemoryCard(commentaryState = commentaryState)
        }

        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Support) {
            MonitorTemplateListCard(
                templates = monitorTemplates,
                onUpdate = onUpdateMonitorTemplate,
                onReset = onResetMonitorTemplate,
                onDelete = onDeleteMonitorTemplate,
                onExport = onExportMonitor
            )
        }

        MotionStageSection(pageOffset = pageOffset, depth = MotionDepth.Support) {
            VideoTemplateListCard(
                templates = videoTemplates,
                onUpdate = onUpdateVideoTemplate,
                onReset = onResetVideoTemplate,
                onDelete = onDeleteVideoTemplate,
                onExport = onExportVideo
            )
        }
    }
}

// --- AI Audience Management Card ---

@Composable
private fun AiAudienceManagementCard(
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
    val clipboardManager = LocalClipboardManager.current

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
                Text("👥 AI 观众", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showConfigDialog = true }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.padding(end = 4.dp))
                    Text("配置")
                }
            }

            if (audiences.isEmpty()) {
                EmptyHint("暂无 AI 观众")
            } else {
                audiences.forEach { audience ->
                    var expanded by remember { mutableStateOf(false) }
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
                                    Text(
                                        audience.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "${audience.audienceType.label} · 💰 ${wallet}币 · ❤️ ${if (audience.enabled) "启用" else "禁用"} · ${audience.heartbeatIntervalSeconds}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { expanded = !expanded }) {
                                        Text(if (expanded) "收起" else "详情")
                                    }
                                }
                            }

                            AnimatedVisibility(visible = expanded) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Persona
                                    Text("人设", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        audience.persona.take(200),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Personal memory
                                    Text("个人记忆", style = MaterialTheme.typography.labelMedium)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        tonalElevation = 4.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = audience.personalMemory.ifBlank { "暂无记忆（直播结束后自动生成）" },
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp),
                                            color = if (audience.personalMemory.isNotBlank())
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (audience.audienceType == AudienceEngineType.Agent) {
                                        Text("Agent Runtime", style = MaterialTheme.typography.labelMedium)
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

                                    // Last post
                                    Text("最近一次完整 Prompt", style = MaterialTheme.typography.labelMedium)
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
                                                Text("Copy")
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
                                            color = if (post != null)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Last response
                                    Text("最近一次 AI 返回", style = MaterialTheme.typography.labelMedium)
                                    val resp = getLastResponse(audience.id)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        tonalElevation = 4.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = resp ?: "暂无数据",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp),
                                            color = if (resp != null)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
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

private fun formatAgentDebugSnapshot(snapshot: AgentAudienceDebugSnapshot?): String {
    if (snapshot == null) return "No runtime state yet. Trigger the agent once in live mode to populate it."

    val inboxText = snapshot.socialInbox.take(4).joinToString("\n") { "- $it" }.ifBlank { "None" }
    val relationText = snapshot.relationSummaries.take(4).joinToString("\n") { "- $it" }.ifBlank { "None" }
    val targetText = snapshot.recentInteractionTargets.joinToString(", ").ifBlank { "None" }

    return buildString {
        appendLine("Emotion: ${snapshot.emotion} (${snapshot.emotionIntensity}/100)")
        appendLine("Goal: ${snapshot.currentGoal}")
        appendLine("Focus: ${snapshot.focusTarget}")
        appendLine("Last action: ${snapshot.lastActionSummary}")
        appendLine("Entered: ${if (snapshot.hasEntered) "Yes" else "No"}")
        appendLine("Silence streak: ${snapshot.silenceStreak}")
        appendLine("Archetype: ${snapshot.socialArchetype}")
        appendLine("Speaking style: ${snapshot.speakingStyle}")
        appendLine("Spending style: ${snapshot.spendingStyle}")
        appendLine("Social drive: ${snapshot.socialDrive}")
        appendLine("Recent targets: $targetText")
        appendLine("Social inbox:")
        appendLine(inboxText)
        appendLine("Relations:")
        append(relationText)
    }
}

// --- Memory System Card ---

@Composable
private fun MemorySystemCard(getMemorySnapshot: () -> MemorySnapshot) {
    var expanded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<MemorySnapshot?>(null) }

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🧠 记忆系统", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { snapshot = getMemorySnapshot() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    TextButton(onClick = {
                        if (snapshot == null) snapshot = getMemorySnapshot()
                        expanded = !expanded
                    }) { Text(if (expanded) "收起" else "查看") }
                }
            }

            AnimatedVisibility(visible = expanded && snapshot != null) {
                val s = snapshot ?: return@AnimatedVisibility
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MemoryBlock("核心记忆 A", s.memoryA.ifBlank { "（空）" })
                    MemoryBlock("近期摘要 B", s.memoryB.ifBlank { "（空）" })
                    MemoryBlock(
                        "最近画面解说 (${s.recentVisual.size} 条)",
                        s.recentVisual.joinToString("\n") { (_, text) -> "· $text" }.ifBlank { "（空）" }
                    )
                    Text(
                        "待压缩缓冲：${s.rawBufferSize} 条（满 10 条触发 B 压缩）",
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

// --- Template Import Card ---

@Composable
private fun TemplateImportCard(onImport: (String, (String) -> Unit) -> Unit) {
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
                Text("📥 导入模板", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("粘贴分享文本") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        placeholder = { Text("我向你分享了一个模板skill~,...") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val clip = clipboardManager.getText()?.text ?: ""
                            importText = clip
                        }) {
                            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.padding(end = 4.dp))
                            Text("从剪贴板粘贴")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (importText.isNotBlank()) {
                                    onImport(importText) { msg ->
                                        resultMessage = msg
                                        if (msg.contains("成功")) importText = ""
                                    }
                                }
                            },
                            enabled = importText.isNotBlank() && TemplateShareManager.canImport(importText)
                        ) {
                            Text("导入")
                        }
                    }
                    resultMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.contains("成功")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// --- Scene Memory Card (three-layer) ---

@Composable
private fun SceneMemoryCard(commentaryState: LiveCommentaryState) {
    var expanded by remember { mutableStateOf(false) }

    WatcherCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎬 场景记忆（三层）", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (commentaryState.scenePhase.isNotBlank()) {
                        Text(
                            commentaryState.scenePhase,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "查看")
                    }
                }
            }

            // Summary line
            val entityCount = commentaryState.entityMemory.lines().filter { it.isNotBlank() }.size
            val askCount = commentaryState.pendingAsks.size
            Text(
                "场景${if (commentaryState.sceneMemory.isNotBlank()) "✓" else "✗"} · " +
                        "实体${entityCount}个 · " +
                        "ASK${askCount}条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MemoryBlock(
                        "Layer 1 · 固定场景",
                        commentaryState.sceneMemory.ifBlank { "（空）" }
                    )
                    MemoryBlock(
                        "Layer 2 · 实体 ($entityCount)",
                        commentaryState.entityMemory.ifBlank { "（空）" }
                    )
                    MemoryBlock(
                        "Layer 3 · 动态摘要",
                        commentaryState.actionSummary.ifBlank { "（空）" }
                    )
                    if (commentaryState.pendingAsks.isNotEmpty()) {
                        Column {
                            Text("ASK 请求", style = MaterialTheme.typography.labelMedium)
                            commentaryState.pendingAsks.forEach { ask ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    tonalElevation = 4.dp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "❓ $ask",
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

@Composable
private fun MonitorTemplateListCard(
    templates: List<MonitorTemplateEntity>,
    onUpdate: (MonitorTemplateEntity) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (MonitorTemplateEntity) -> String
) {
    WatcherCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("监控模板", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "编辑模板参数后，工作台的模板卡片将使用更新后的配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (templates.isEmpty()) {
                EmptyHint(text = "暂无监控模板。")
            } else {
                templates.forEach { template ->
                    MonitorTemplateItem(
                        template = template,
                        onSave = onUpdate,
                        onReset = { onReset(template.templateId) },
                        onExport = { onExport(template) },
                        onDelete = { onDelete(template.templateId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorTemplateItem(
    template: MonitorTemplateEntity,
    onSave: (MonitorTemplateEntity) -> Unit,
    onReset: () -> Unit,
    onExport: () -> String,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember(template.templateId) { mutableStateOf(false) }
    var label by remember(template.templateId, template.label) { mutableStateOf(template.label) }
    var description by remember(template.templateId, template.description) { mutableStateOf(template.description) }
    var requirement by remember(template.templateId, template.userRequirement) { mutableStateOf(template.userRequirement) }
    var sceneDesc by remember(template.templateId, template.originalSceneDescription) { mutableStateOf(template.originalSceneDescription) }
    var interval by remember(template.templateId, template.checkIntervalSeconds) { mutableStateOf(template.checkIntervalSeconds.toString()) }
    var prompt by remember(template.templateId, template.promptTemplate) { mutableStateOf(template.promptTemplate) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    val text = onExport()
                    clipboardManager.setText(AnnotatedString(text))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "分享")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.Edit, contentDescription = if (expanded) "收起" else "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(label = "标签名", value = label, onValueChange = { label = it })
                    FormField(label = "描述", value = description, onValueChange = { description = it })
                    FormField(label = "监控目标", value = requirement, onValueChange = { requirement = it })
                    FormField(label = "场景描述", value = sceneDesc, onValueChange = { sceneDesc = it })
                    FormField(
                        label = "巡检间隔（秒）",
                        value = interval,
                        onValueChange = { interval = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(label = "提示词", value = prompt, onValueChange = { prompt = it }, minLines = 4)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "监控模式：${template.monitorMode} · 基准来源：${template.baselineSource}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    ActionRow(
                        primaryLabel = "保存修改",
                        onPrimaryClick = {
                            onSave(
                                template.copy(
                                    label = label,
                                    description = description,
                                    userRequirement = requirement,
                                    originalSceneDescription = sceneDesc,
                                    checkIntervalSeconds = interval.toIntOrNull() ?: template.checkIntervalSeconds,
                                    promptTemplate = prompt
                                )
                            )
                        },
                        primaryEnabled = true,
                        secondaryLabel = "恢复默认",
                        onSecondaryClick = onReset,
                        secondaryEnabled = template.isDefault,
                        secondaryIcon = Icons.Default.Refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoTemplateListCard(
    templates: List<VideoTemplateEntity>,
    onUpdate: (VideoTemplateEntity) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (VideoTemplateEntity) -> String
) {
    WatcherCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("视频分析模板", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "编辑模板参数后，工作台的模板卡片将使用更新后的配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (templates.isEmpty()) {
                EmptyHint(text = "暂无视频分析模板。")
            } else {
                templates.forEach { template ->
                    VideoTemplateItem(
                        template = template,
                        onSave = onUpdate,
                        onReset = { onReset(template.templateId) },
                        onExport = { onExport(template) },
                        onDelete = { onDelete(template.templateId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoTemplateItem(
    template: VideoTemplateEntity,
    onSave: (VideoTemplateEntity) -> Unit,
    onReset: () -> Unit,
    onExport: () -> String,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember(template.templateId) { mutableStateOf(false) }
    var label by remember(template.templateId, template.label) { mutableStateOf(template.label) }
    var description by remember(template.templateId, template.description) { mutableStateOf(template.description) }
    var requirement by remember(template.templateId, template.userRequirement) { mutableStateOf(template.userRequirement) }
    var sceneContext by remember(template.templateId, template.sceneContext) { mutableStateOf(template.sceneContext) }
    var duration by remember(template.templateId, template.recordingDurationSeconds) { mutableStateOf(template.recordingDurationSeconds.toString()) }
    var segmentDuration by remember(template.templateId, template.segmentDurationSeconds) { mutableStateOf(template.segmentDurationSeconds.toString()) }
    var captureInterval by remember(template.templateId, template.captureIntervalSeconds) { mutableStateOf(template.captureIntervalSeconds.toString()) }
    var fps by remember(template.templateId, template.samplingFps) { mutableStateOf(template.samplingFps.toString()) }
    var segmentPrompt by remember(template.templateId, template.segmentAnalysisPrompt) { mutableStateOf(template.segmentAnalysisPrompt) }
    var summaryPrompt by remember(template.templateId, template.finalSummaryPrompt) { mutableStateOf(template.finalSummaryPrompt) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    val text = onExport()
                    clipboardManager.setText(AnnotatedString(text))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "分享")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.Edit, contentDescription = if (expanded) "收起" else "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(label = "标签名", value = label, onValueChange = { label = it })
                    FormField(label = "描述", value = description, onValueChange = { description = it })
                    FormField(label = "任务需求", value = requirement, onValueChange = { requirement = it })
                    FormField(label = "场景描述", value = sceneContext, onValueChange = { sceneContext = it })
                    FormField(
                        label = "录制总时长（秒）",
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "片段时长（秒）",
                        value = segmentDuration,
                        onValueChange = { segmentDuration = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "采集间隔（秒）",
                        value = captureInterval,
                        onValueChange = { captureInterval = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "采样帧率（FPS）",
                        value = fps,
                        onValueChange = { fps = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(label = "片段分析提示词", value = segmentPrompt, onValueChange = { segmentPrompt = it }, minLines = 4)
                    FormField(label = "汇总提示词", value = summaryPrompt, onValueChange = { summaryPrompt = it }, minLines = 4)

                    ActionRow(
                        primaryLabel = "保存修改",
                        onPrimaryClick = {
                            onSave(
                                template.copy(
                                    label = label,
                                    description = description,
                                    userRequirement = requirement,
                                    sceneContext = sceneContext,
                                    recordingDurationSeconds = duration.toIntOrNull() ?: template.recordingDurationSeconds,
                                    segmentDurationSeconds = segmentDuration.toIntOrNull() ?: template.segmentDurationSeconds,
                                    captureIntervalSeconds = captureInterval.toIntOrNull() ?: template.captureIntervalSeconds,
                                    samplingFps = fps.toIntOrNull() ?: template.samplingFps,
                                    segmentAnalysisPrompt = segmentPrompt,
                                    finalSummaryPrompt = summaryPrompt
                                )
                            )
                        },
                        primaryEnabled = true,
                        secondaryLabel = "恢复默认",
                        onSecondaryClick = onReset,
                        secondaryEnabled = template.isDefault,
                        secondaryIcon = Icons.Default.Refresh
                    )
                }
            }
        }
    }
}
