package com.example.watcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.ui.viewmodel.AgentConfigUiState
import com.example.watcher.ui.viewmodel.AgentEditorDraft
import com.example.watcher.ui.viewmodel.AgentListItemUiModel

@Composable
internal fun DesktopAgentConfigLayout(
    uiState: AgentConfigUiState,
    isDirty: Boolean,
    onStartCreate: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onDraftChange: (AgentEditorDraft) -> Unit,
    onSave: () -> Unit,
    onTestBrain: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AgentLibraryPane(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 360.dp)
                .fillMaxHeight(),
            uiState = uiState,
            compact = false,
            onStartCreate = onStartCreate,
            onDuplicateSelected = onDuplicateSelected,
            onSelectAgent = onSelectAgent
        )
        AgentEditorPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            uiState = uiState,
            isDirty = isDirty,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onTestBrain = onTestBrain,
            onReset = onReset,
            onDelete = onDelete
        )
    }
}

@Composable
internal fun CompactAgentConfigLayout(
    uiState: AgentConfigUiState,
    isDirty: Boolean,
    onStartCreate: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onDraftChange: (AgentEditorDraft) -> Unit,
    onSave: () -> Unit,
    onTestBrain: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AgentLibraryPane(
            modifier = Modifier.fillMaxWidth(),
            uiState = uiState,
            compact = true,
            onStartCreate = onStartCreate,
            onDuplicateSelected = onDuplicateSelected,
            onSelectAgent = onSelectAgent
        )
        AgentEditorPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            uiState = uiState,
            isDirty = isDirty,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onTestBrain = onTestBrain,
            onReset = onReset,
            onDelete = onDelete
        )
    }
}

@Composable
internal fun AgentLibraryPane(
    modifier: Modifier,
    uiState: AgentConfigUiState,
    compact: Boolean,
    onStartCreate: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onSelectAgent: (String) -> Unit
) {
    if (compact) {
        CompactAgentSwitcher(
            modifier = modifier,
            uiState = uiState,
            onStartCreate = onStartCreate,
            onDuplicateSelected = onDuplicateSelected,
            onSelectAgent = onSelectAgent
        )
        return
    }

    PanelSurface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Agent 库", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "选择一个已有 Agent 进行编辑，或新建一个草稿。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionButtonRow(
                primaryLabel = "新建 Agent",
                primaryIcon = Icons.Default.Add,
                onPrimaryClick = onStartCreate,
                secondaryLabel = "复制当前项",
                secondaryIcon = Icons.Default.ContentCopy,
                onSecondaryClick = onDuplicateSelected,
                secondaryEnabled = uiState.selectedAgentId != null || uiState.detail != null
            )
            if (uiState.agents.isEmpty()) {
                EmptyPanelMessage(
                    text = "还没有已配置的 Agent，可以从这里创建第一个。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.agents,
                        key = { it.agentId }
                    ) { agent ->
                        AgentListItemCard(
                            agent = agent,
                            selected = agent.agentId == uiState.selectedAgentId && !uiState.isCreatingNew,
                            onSelect = { onSelectAgent(agent.agentId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactAgentSwitcher(
    modifier: Modifier,
    uiState: AgentConfigUiState,
    onStartCreate: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onSelectAgent: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedAgent = uiState.agents.firstOrNull { it.agentId == uiState.selectedAgentId }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (uiState.isCreatingNew) "当前：新建 Agent 草稿" else "当前：已选 Agent",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = selectedAgent?.name ?: "选择已有 Agent",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = selectedAgent?.agentId ?: "点击切换到一个已保存的 Agent 配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                uiState.agents.forEach { agent ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(agent.name)
                                Text(
                                    text = agent.agentId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectAgent(agent.agentId)
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartCreate,
                modifier = Modifier.weight(1f)
            ) {
                Text("新建")
            }
            OutlinedButton(
                onClick = onDuplicateSelected,
                enabled = uiState.selectedAgentId != null || uiState.detail != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("复制")
            }
        }
    }
}

@Composable
internal fun AgentListItemCard(
    agent: AgentListItemUiModel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        )
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
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AgentConfigStatusPill(
                    text = if (agent.usesCustomBrain) "自定义大脑" else "默认大脑",
                    emphasized = agent.usesCustomBrain
                )
            }
            Text(
                text = agent.agentId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (agent.description.isNotBlank()) {
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "大脑：${agent.brainSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "运行时：${agent.latestRuntimeState ?: "从未启动"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "更新时间 ${formatTimestamp(agent.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun AgentSummaryStrip(agent: AgentListItemUiModel) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(agent.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "大脑：${agent.brainSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "运行时：${agent.latestRuntimeState ?: "从未启动"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun AgentEditorPane(
    modifier: Modifier,
    uiState: AgentConfigUiState,
    isDirty: Boolean,
    onDraftChange: (AgentEditorDraft) -> Unit,
    onSave: () -> Unit,
    onTestBrain: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = AgentConfigTab.entries[selectedTabIndex]

    PanelSurface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            EditorHeader(
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp),
                uiState = uiState,
                isDirty = isDirty
            )
            EditorActions(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                isCreatingNew = uiState.isCreatingNew,
                isSaving = uiState.isSaving,
                isDeleting = uiState.isDeleting,
                isTestingBrain = uiState.isTestingBrain,
                onSave = onSave,
                onTestBrain = onTestBrain,
                onReset = onReset,
                onDelete = onDelete
            )
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 18.dp
            ) {
                AgentConfigTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.label) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    AgentConfigTab.Basics -> basicsItems(uiState.draft, onDraftChange)
                    AgentConfigTab.Brain -> brainItems(uiState.draft, onDraftChange)
                    AgentConfigTab.Runtime -> runtimeItems(uiState.draft, onDraftChange)
                    AgentConfigTab.Records -> recordsItems(uiState)
                }
            }
        }
    }
}

@Composable
internal fun EditorHeader(
    modifier: Modifier,
    uiState: AgentConfigUiState,
    isDirty: Boolean
) {
    val title = when {
        uiState.isCreatingNew && uiState.draft.name.isNotBlank() -> uiState.draft.name
        uiState.isCreatingNew -> "新建 Agent 草稿"
        uiState.detail != null -> uiState.detail.profile.definition.name
        else -> "Agent 编辑器"
    }
    val subtitle = when {
        uiState.isCreatingNew -> "先配置身份、大脑和运行时保护参数，再进行保存。"
        uiState.detail != null -> "正在编辑已选 Agent，修改内容会在点击保存前仅保留在本地。"
        else -> "请选择一个 Agent，或新建一个草稿。"
    }
    val detail = uiState.detail

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentConfigStatusPill(
                text = if (uiState.isCreatingNew) "新建模式" else "编辑模式",
                emphasized = true
            )
            if (isDirty) {
                AgentConfigStatusPill(text = "未保存")
            }
            if (detail != null) {
                AgentConfigStatusPill(text = "知识 ${detail.knowledge.size}")
                AgentConfigStatusPill(text = "运行 ${detail.runs.size}")
                AgentConfigStatusPill(text = "活动 ${detail.activities.size}")
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun EditorActions(
    modifier: Modifier,
    isCreatingNew: Boolean,
    isSaving: Boolean,
    isDeleting: Boolean,
    isTestingBrain: Boolean,
    onSave: () -> Unit,
    onTestBrain: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = !isSaving && !isDeleting && !isTestingBrain,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaving) "保存中..." else "保存")
            }
            OutlinedButton(
                onClick = onTestBrain,
                enabled = !isSaving && !isDeleting && !isTestingBrain,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTestingBrain) "测试中..." else "测试大脑")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                enabled = !isSaving && !isDeleting && !isTestingBrain,
                modifier = Modifier.weight(1f)
            ) {
                Text("重置")
            }
            TextButton(
                onClick = onDelete,
                enabled = !isCreatingNew && !isSaving && !isDeleting && !isTestingBrain,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isDeleting) "删除中..." else "删除")
            }
        }
    }
}

@Composable
internal fun CompactActionBar(
    isCreatingNew: Boolean,
    isDirty: Boolean,
    isSaving: Boolean,
    isDeleting: Boolean,
    isTestingBrain: Boolean,
    isTestingAgent: Boolean,
    isTestingContext: Boolean,
    onSave: () -> Unit,
    onTestBrain: () -> Unit,
    onTestAgent: () -> Unit,
    onTestContext: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isCreatingNew) "新建 Agent 草稿" else if (isDirty) "存在未保存修改" else "Agent 已就绪",
                style = MaterialTheme.typography.titleSmall
            )
            if (isDirty) {
                Text(
                    text = "未保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = !isSaving && !isDeleting && !isTestingBrain && !isTestingAgent && !isTestingContext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaving) "保存中..." else "保存")
            }
            OutlinedButton(
                onClick = onTestBrain,
                enabled = !isSaving && !isDeleting && !isTestingBrain && !isTestingAgent && !isTestingContext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTestingBrain) "测试中..." else "测试大脑")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onTestAgent,
                enabled = !isSaving && !isDeleting && !isTestingBrain && !isTestingAgent && !isTestingContext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTestingAgent) "测试中..." else "测试 Agent")
            }
            OutlinedButton(
                onClick = onTestContext,
                enabled = !isSaving && !isDeleting && !isTestingBrain && !isTestingAgent && !isTestingContext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTestingContext) "测试中..." else "测试上下文")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                enabled = !isSaving && !isDeleting && !isTestingBrain && !isTestingAgent && !isTestingContext,
                modifier = Modifier.weight(1f)
            ) {
                Text("重置")
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = !isCreatingNew && !isSaving && !isDeleting && !isTestingBrain && !isTestingAgent && !isTestingContext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isDeleting) "删除中..." else "删除")
            }
        }
    }
}
