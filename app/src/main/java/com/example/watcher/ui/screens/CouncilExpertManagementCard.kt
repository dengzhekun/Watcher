package com.example.watcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.CouncilExpertDefaults
import com.example.watcher.data.model.CouncilExpertEntity
import com.example.watcher.data.model.CouncilExpertKind
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.model.toLegacyCouncilExpertRoleOrNull
import com.example.watcher.ui.components.ActionRow
import com.example.watcher.ui.components.EmptyHint
import com.example.watcher.ui.components.FormField
import com.example.watcher.ui.components.WatcherCard

/* ──────────────────────────────────────────────────────────────────────
 *  Top-level card: header + expert list
 * ────────────────────────────────────────────────────────────────────── */

@Composable
internal fun CouncilExpertManagementCard(
    experts: List<CouncilExpertEntity>,
    providers: List<LlmProviderEntity>,
    onCreateExpert: () -> Unit,
    onSaveExpert: (CouncilExpertEntity) -> Unit,
    onDuplicateExpert: (CouncilExpertEntity) -> Unit,
    onResetExpert: (String) -> Unit,
    onDeleteExpert: (String) -> Unit,
    onRestoreMissingExperts: () -> Unit,
    getLastPrompt: (String) -> String?,
    getLastResponse: (String) -> String?,
    getSessionMemory: (String) -> List<String>,
    getExpertKnowledge: suspend (String) -> List<com.example.watcher.data.model.CouncilKnowledgeEntity>,
    onDeleteKnowledge: (Long) -> Unit,
    onExportExpert: (CouncilExpertEntity) -> String
) {
    val activeCount = experts.count { it.enabled && it.selectedForCouncil }
    val synthCount = experts.count {
        it.enabled && it.selectedForCouncil && it.expertKind == CouncilExpertKind.Synthesizer
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val defaultProvider = providers.firstOrNull { it.enabled } ?: providers.firstOrNull()
    val defaultProviderLabel = defaultProvider?.name ?: "未配置默认接口"
    val missingCount = CouncilExpertDefaults.all.count { p -> experts.none { it.expertId == p.expertId } }
    val summary = when {
        experts.isEmpty() -> "暂无专家配置。"
        else -> buildString {
            append("${experts.size} 位专家")
            append(" · 已上场 $activeCount 位")
            append(" · 综合器 $synthCount 位")
            if (missingCount > 0) {
                append(" · 缺失预设 $missingCount 位")
            }
        }
    }

    WatcherCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            /* ── header ── */
            CardHeader(
                summary = summary,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
                activeCount = activeCount,
                synthCount = synthCount,
                defaultProviderLabel = defaultProviderLabel,
                missingCount = missingCount,
                onRestore = onRestoreMissingExperts,
                onCreate = onCreateExpert
            )

            /* ── expert list ── */
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (experts.isEmpty()) {
                        EmptyHint("暂无智囊团专家配置。")
                    } else {
                        experts.forEach { expert ->
                            key(expert.expertId) {
                                ExpertCard(
                                    expert = expert,
                                    providers = providers,
                                    activeCount = activeCount,
                                    defaultProviderLabel = defaultProviderLabel,
                                    onSave = onSaveExpert,
                                    onDuplicate = { onDuplicateExpert(expert) },
                                    onReset = { onResetExpert(expert.expertId) },
                                    onDelete = { onDeleteExpert(expert.expertId) },
                                    lastPrompt = getLastPrompt(expert.expertId),
                                    lastResponse = getLastResponse(expert.expertId),
                                    sessionMemory = getSessionMemory(expert.expertId),
                                    getExpertKnowledge = getExpertKnowledge,
                                    onDeleteKnowledge = onDeleteKnowledge,
                                    onExport = { onExportExpert(expert) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Card header: title, description, action buttons
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun CardHeader(
    summary: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    activeCount: Int,
    synthCount: Int,
    defaultProviderLabel: String,
    missingCount: Int,
    onRestore: () -> Unit,
    onCreate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("智囊团专家库", style = MaterialTheme.typography.titleLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "统一使用上场状态管理专家。开启即启用并加入阵容，关闭即停用并移出阵容。当前已上场 $activeCount/5 位。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "当前运行时默认接口：$defaultProviderLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (synthCount == 0) {
                Text(
                    "当前未选择综合器，智囊团仍可运行，但不会有专门的最终整合席位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (synthCount > 1) {
                Text(
                    "当前有多位综合器处于上场状态，运行时只会使用排序最靠前的一位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起专家库卡片" else "展开专家库卡片"
                )
            }
            if (missingCount > 0) {
                FilledTonalButton(onClick = onRestore) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("恢复缺失预设($missingCount)")
                }
            }
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("新建专家")
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Single expert card (collapsed / expanded)
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun ExpertCard(
    expert: CouncilExpertEntity,
    providers: List<LlmProviderEntity>,
    activeCount: Int,
    defaultProviderLabel: String,
    onSave: (CouncilExpertEntity) -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    lastPrompt: String?,
    lastResponse: String?,
    sessionMemory: List<String>,
    getExpertKnowledge: suspend (String) -> List<com.example.watcher.data.model.CouncilKnowledgeEntity>,
    onDeleteKnowledge: (Long) -> Unit,
    onExport: () -> String
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(expert.expertId) { mutableStateOf(false) }
    var showDeleteDialog by remember(expert.expertId) { mutableStateOf(false) }

    // Editable draft fields — keyed on entity so they reset when DB changes
    var name by remember(expert.expertId, expert.name) { mutableStateOf(expert.name) }
    var description by remember(expert.expertId, expert.description) { mutableStateOf(expert.description) }
    var persona by remember(expert.expertId, expert.promptPersona) { mutableStateOf(expert.promptPersona) }
    var perspective by remember(expert.expertId, expert.perspective) { mutableStateOf(expert.perspective) }
    var providerId by remember(expert.expertId, expert.providerId) { mutableStateOf(expert.providerId) }
    var expertKind by remember(expert.expertId, expert.expertKind) { mutableStateOf(expert.expertKind) }
    var sortOrder by remember(expert.expertId, expert.sortOrder) { mutableStateOf(expert.sortOrder.toString()) }
    var active by remember(expert.expertId, expert.enabled, expert.selectedForCouncil) {
        mutableStateOf(expert.enabled && expert.selectedForCouncil)
    }

    val resolvedProvider = providers.firstOrNull { it.id == providerId }
    val providerLabel = when {
        providerId.isBlank() -> defaultProviderLabel
        resolvedProvider != null -> resolvedProvider.name
        else -> "$providerId（接口不存在）"
    }
    val legacyLabel = expert.toLegacyCouncilExpertRoleOrNull()?.label
    val summaryText = buildString {
        append("#${sortOrder.toIntOrNull() ?: expert.sortOrder}")
        append(" · "); append(legacyLabel ?: expertKind.label)
        append(" · "); append(providerLabel)
        append(" · "); append(if (active) "已上场" else "未上场")
    }

    fun currentDraft() = expert.copy(
        name = name,
        description = description,
        promptPersona = persona,
        perspective = perspective,
        providerId = providerId,
        expertKind = expertKind,
        enabled = active,
        selectedForCouncil = active,
        sortOrder = sortOrder.toIntOrNull() ?: expert.sortOrder
    )

    /* ── delete confirmation dialog ── */
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除专家") },
            text = {
                Text(
                    if (expert.isSystemPreset)
                        "将删除专家\u201C${expert.name}\u201D。这是系统预设专家，删除后可通过顶部\u201C恢复缺失预设\u201D找回。"
                    else
                        "将删除专家\u201C${expert.name}\u201D。删除后该专家的配置将从专家库中移除。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    /* ── card surface ── */
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            /* ── collapsed header — always visible ── */
            CollapsedHeader(
                name = name,
                summary = summaryText,
                description = description,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onDuplicate = onDuplicate,
                onDelete = { showDeleteDialog = true }
            )

            /* ── expanded body ── */
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    /* fields */
                    FormField(label = "专家名称", value = name, onValueChange = { name = it })
                    FormField(label = "专家说明", value = description, onValueChange = { description = it }, minLines = 2)
                    FormField(label = "专家风格 / 人设", value = persona, onValueChange = { persona = it }, minLines = 3)
                    FormField(label = "专业关注点", value = perspective, onValueChange = { perspective = it }, minLines = 3)
                    FormField(
                        label = "排序序号",
                        value = sortOrder,
                        onValueChange = { sortOrder = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )

                    /* kind picker */
                    KindPicker(selected = expertKind, onSelect = { expertKind = it })

                    /* provider picker */
                    ProviderPicker(
                        providers = providers,
                        selectedId = providerId,
                        defaultLabel = defaultProviderLabel,
                        onSelect = { providerId = it }
                    )

                    /* roster switch */
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        if (active) "当前状态：已启用并加入阵容" else "当前状态：已停用并移出阵容",
                        style = MaterialTheme.typography.bodySmall
                    )
                    RosterSwitch(
                        active = active,
                        enabled = active || activeCount < 5,
                        onToggle = { next ->
                            if (!next || activeCount < 5) {
                                active = next
                                onSave(currentDraft().copy(enabled = next, selectedForCouncil = next))
                            }
                        }
                    )
                    if (!active && activeCount >= 5) {
                        Text(
                            "当前阵容已满 5 位。若要让此专家上场，请先关闭一位已上场专家。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    /* debug log section — only if data exists */
                    if (lastPrompt != null || lastResponse != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Text("运行记录", style = MaterialTheme.typography.labelLarge)
                        if (lastPrompt != null) {
                            CollapsibleTextBlock(
                                label = "最近一次完整提示词",
                                text = lastPrompt,
                                onCopy = { clipboard.setText(AnnotatedString(lastPrompt)) }
                            )
                        }
                        if (lastResponse != null) {
                            CollapsibleTextBlock(
                                label = "最近一次模型回复",
                                text = lastResponse,
                                onCopy = { clipboard.setText(AnnotatedString(lastResponse)) }
                            )
                        }
                    }

                    /* session memory section */
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Text("会话记忆", style = MaterialTheme.typography.labelLarge)
                    if (sessionMemory.isEmpty()) {
                        Text(
                            "暂无 — 启动智囊团直播后，该专家的分析轨迹将在此显示。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            sessionMemory.forEach { mem ->
                                Text(
                                    text = mem,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    /* knowledge section */
                    ExpertKnowledgeSection(
                        expertId = expert.expertId,
                        getExpertKnowledge = getExpertKnowledge,
                        onDelete = onDeleteKnowledge
                    )

                    /* bottom actions */
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(onExport())) }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("分享专家模板")
                    }
                    ActionRow(
                        primaryLabel = "保存文本与类型修改",
                        onPrimaryClick = { onSave(currentDraft()) },
                        primaryEnabled = true,
                        secondaryLabel = "恢复默认",
                        onSecondaryClick = onReset,
                        secondaryEnabled = expert.isSystemPreset,
                        secondaryIcon = Icons.Default.Refresh
                    )
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Collapsed header row
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun CollapsedHeader(
    name: String,
    summary: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name.ifBlank { "未命名专家" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.CopyAll, contentDescription = "复制专家")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除专家")
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "详情"
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Kind picker (Specialist / Synthesizer)
 * ────────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindPicker(
    selected: CouncilExpertKind,
    onSelect: (CouncilExpertKind) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("专家类型", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onSelect(CouncilExpertKind.Specialist) }) {
                Text(if (selected == CouncilExpertKind.Specialist) "专家 · 已选" else "专家")
            }
            FilledTonalButton(onClick = { onSelect(CouncilExpertKind.Synthesizer) }) {
                Text(if (selected == CouncilExpertKind.Synthesizer) "综合器 · 已选" else "综合器")
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Provider picker
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun ProviderPicker(
    providers: List<LlmProviderEntity>,
    selectedId: String,
    defaultLabel: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("模型接口", style = MaterialTheme.typography.labelMedium)
        Text(
            "当前默认接口：$defaultLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (providers.isEmpty()) {
            Text(
                "当前没有可选模型接口，只有在后续配置接口后才能为专家指定独立模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            providers.forEach { provider ->
                val sel = provider.id == selectedId
                FilledTonalButton(onClick = { onSelect(provider.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sel) "${provider.name} · 已选中" else provider.name)
                }
            }
        }
        TextButton(onClick = { onSelect("") }) {
            Text(
                if (selectedId.isBlank()) "跟随当前默认接口：$defaultLabel"
                else "切换为当前默认接口：$defaultLabel"
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Roster switch (on-field toggle)
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun RosterSwitch(
    active: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("上场状态", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (active) "开启时，此专家会被启用，并自动加入智囊团阵容。"
                    else "关闭时，此专家会被停用，并自动从智囊团阵容中移出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = active,
                onCheckedChange = onToggle,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Collapsible text block for debug prompt / response
 *
 *  Collapsed: shows first ~120 chars, max 3 lines, with "展开" action.
 *  Expanded:  scrollable text up to 280dp tall, with "收起" + "复制".
 * ────────────────────────────────────────────────────────────────────── */

private const val PREVIEW_CHARS = 120
private const val PREVIEW_LINES = 3

@Composable
private fun CollapsibleTextBlock(
    label: String,
    text: String,
    onCopy: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    // Increment to force scroll-state reset on collapse/expand
    var generation by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy) { Text("复制") }
                TextButton(onClick = { open = !open; generation++ }) {
                    Text(if (open) "收起" else "展开")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (open) {
                val scrollState = remember(generation) { androidx.compose.foundation.ScrollState(0) }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState)
                        .padding(10.dp)
                )
            } else {
                val preview = if (text.length > PREVIEW_CHARS) text.take(PREVIEW_CHARS) + "…" else text
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Expert knowledge section — shows persisted knowledge entries
 * ────────────────────────────────────────────────────────────────────── */

private fun knowledgeCategoryLabel(category: String): String = when (category) {
    "session_fact" -> "会话事实"
    "expert_calibration" -> "专家校准"
    "user_profile" -> "用户画像"
    // Legacy categories
    "scene_pattern" -> "场景模式"
    "key_finding" -> "关键发现"
    else -> category
}

private fun knowledgeCategoryColor(category: String): androidx.compose.ui.graphics.Color = when (category) {
    "session_fact" -> androidx.compose.ui.graphics.Color(0xFF42A5F5)
    "expert_calibration" -> androidx.compose.ui.graphics.Color(0xFFAB47BC)
    "user_profile" -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
    else -> androidx.compose.ui.graphics.Color(0xFF78909C)
}

@Composable
private fun ExpertKnowledgeSection(
    expertId: String,
    getExpertKnowledge: suspend (String) -> List<com.example.watcher.data.model.CouncilKnowledgeEntity>,
    onDelete: (Long) -> Unit
) {
    var entries by remember(expertId) {
        mutableStateOf<List<com.example.watcher.data.model.CouncilKnowledgeEntity>>(emptyList())
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(expertId, refreshKey) {
        entries = getExpertKnowledge(expertId)
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
    Text("知识沉淀（${entries.size}条）", style = MaterialTheme.typography.labelLarge)
    if (entries.isEmpty()) {
        Text(
            "暂无 — 完成一场智囊团直播并退出后，系统将从分析结果中萃取知识。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry ->
            val catColor = knowledgeCategoryColor(entry.category)
            Surface(
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = knowledgeCategoryLabel(entry.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = catColor,
                            modifier = Modifier
                                .background(catColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "相关度 ${(entry.relevance * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { onDelete(entry.id); refreshKey++ },
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.height(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
