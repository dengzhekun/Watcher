package com.example.watcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.ui.viewmodel.AgentConfigUiState
import com.example.watcher.ui.viewmodel.AgentEditorDraft
import com.example.watcher.ui.viewmodel.SavedBrainUiModel
import com.example.watcher.ui.viewmodel.toDraft

@Composable
internal fun BasicsContent(
    draft: AgentEditorDraft,
    onDraftChange: (AgentEditorDraft) -> Unit
) {
    SectionCard(
        title = "基础资料",
        description = "配置 Agent 的身份、摘要、目标和核心系统提示词。"
    ) {
        FormField(
            label = "Agent ID",
            value = draft.agentId,
            supporting = "用于运行时查找和持久化的稳定唯一键。",
            onValueChange = { onDraftChange(draft.copy(agentId = it.trim())) },
            singleLine = true
        )
        FormField(
            label = "显示名称",
            value = draft.name,
            supporting = "显示在 Agent 列表和运行记录中的名称。",
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            singleLine = true
        )
        FormField(
            label = "描述",
            value = draft.description,
            supporting = "用于和其他 Agent 区分开的简短描述。",
            onValueChange = { onDraftChange(draft.copy(description = it)) },
            minLines = 2,
            maxLines = 4
        )
        FormField(
            label = "目标",
            value = draft.goal,
            supporting = "指导自主规划与执行的长期目标。",
            onValueChange = { onDraftChange(draft.copy(goal = it)) },
            minLines = 2,
            maxLines = 4
        )
        FormField(
            label = "系统提示词",
            value = draft.systemInstruction,
            supporting = "定义 Agent 的人设、边界和预期行为。",
            onValueChange = { onDraftChange(draft.copy(systemInstruction = it)) },
            minLines = 6,
            maxLines = 10
        )
        FormField(
            label = "标签",
            value = draft.tagsText,
            supporting = "用逗号分隔的标签，方便分组、筛选和后续迁移。",
            onValueChange = { onDraftChange(draft.copy(tagsText = it)) }
        )
    }
}

@Composable
internal fun BrainContent(
    uiState: AgentConfigUiState,
    onDraftChange: (AgentEditorDraft) -> Unit
) {
    val draft = uiState.draft
    SectionCard(
        title = "大脑连接",
        description = "Brain 独立于 Agent 保存。点击“测试大脑”会验证连接，并自动保存当前 Brain。"
    ) {
        SavedBrainSelector(
            savedBrains = uiState.savedBrains,
            selectedBrainId = draft.selectedBrainId,
            onUseDefault = {
                onDraftChange(
                    draft.copy(
                        selectedBrainId = "",
                        brainEndpoint = "",
                        brainApiKey = "",
                        brainModelName = "",
                        brainDisplayName = ""
                    )
                )
            },
            onSelectBrain = { savedBrain ->
                onDraftChange(savedBrain.toDraft(draft))
            }
        )
        FormField(
            label = "Brain 接口地址",
            value = draft.brainEndpoint,
            supporting = "只填写基础 API 地址，例如 .../v1 或 .../api/v3，不要包含 /responses 或 /chat/completions。",
            onValueChange = {
                onDraftChange(
                    draft.copy(
                        selectedBrainId = "",
                        brainEndpoint = it
                    )
                )
            },
            singleLine = true
        )
        OutlinedTextField(
            value = draft.brainApiKey,
            onValueChange = {
                onDraftChange(
                    draft.copy(
                        selectedBrainId = "",
                        brainApiKey = it
                    )
                )
            },
            label = { Text("Brain API 密钥") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                FieldHint("配置自定义接口时必填。界面中会隐藏显示，但仍会保存到 Agent 元数据中。")
            }
        )
        FormField(
            label = "Brain 模型",
            value = draft.brainModelName,
            supporting = "该 Agent 可选的模型名。如果留空，框架会回退到应用默认模型。",
            onValueChange = {
                onDraftChange(
                    draft.copy(
                        selectedBrainId = "",
                        brainModelName = it
                    )
                )
            },
            singleLine = true
        )
        FormField(
            label = "Brain 标识",
            value = draft.brainDisplayName,
            supporting = "显示在 Agent 列表中的可选名称，方便快速识别这个 Brain。",
            onValueChange = {
                onDraftChange(
                    draft.copy(
                        selectedBrainId = "",
                        brainDisplayName = it
                    )
                )
            },
            singleLine = true
        )
        InfoCallout(
            title = "连接测试",
            body = "使用上方“测试大脑”验证当前 URL 和 API 密钥。成功后会自动保存该 Brain，并可被其他 Agent 选用。"
        )
    }
}

@Composable
internal fun SavedBrainSelector(
    savedBrains: List<SavedBrainUiModel>,
    selectedBrainId: String,
    onUseDefault: () -> Unit,
    onSelectBrain: (SavedBrainUiModel) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedBrain = savedBrains.firstOrNull { it.id == selectedBrainId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "已保存的 Brain",
            style = MaterialTheme.typography.titleSmall
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
                        text = selectedBrain?.name ?: "自定义或默认 Brain",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = selectedBrain?.let { "${it.endpoint} 路 ${it.modelName}" }
                            ?: "选择一个已保存的 Brain，或继续保持自定义 Brain 配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("使用自定义/默认 Brain") },
                    onClick = {
                        expanded = false
                        onUseDefault()
                    }
                )
                savedBrains.forEach { savedBrain ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(savedBrain.name)
                                Text(
                                    text = "${savedBrain.endpoint} 路 ${savedBrain.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectBrain(savedBrain)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun RuntimeContent(
    draft: AgentEditorDraft,
    onDraftChange: (AgentEditorDraft) -> Unit
) {
    SectionCard(
        title = "运行时保护参数",
        description = "这些字段控制 Agent 最多可以运行多久、允许失败多少次，以及在停滞多久后由框架终止。"
    ) {
        NumberField(
            label = "maxSteps",
            value = draft.maxSteps,
            hint = "单次自主运行允许的主循环最大次数。",
            onValueChange = { onDraftChange(draft.copy(maxSteps = it)) }
        )
        NumberField(
            label = "maxConsecutiveFailures",
            value = draft.maxConsecutiveFailures,
            hint = "连续失败达到多少次后终止本次运行。",
            onValueChange = { onDraftChange(draft.copy(maxConsecutiveFailures = it)) }
        )
        NumberField(
            label = "maxIdleTurns",
            value = draft.maxIdleTurns,
            hint = "允许多少次无进展回合后判定为停滞。",
            onValueChange = { onDraftChange(draft.copy(maxIdleTurns = it)) }
        )
        NumberField(
            label = "maxRuntimeMillis",
            value = draft.maxRuntimeMillis,
            hint = "单次运行的硬性时长上限，单位毫秒。",
            onValueChange = { onDraftChange(draft.copy(maxRuntimeMillis = it)) }
        )
        NumberField(
            label = "toolTimeoutMillis",
            value = draft.toolTimeoutMillis,
            hint = "单次工具调用允许的最长时长，单位毫秒。",
            onValueChange = { onDraftChange(draft.copy(toolTimeoutMillis = it)) }
        )
    }
}

@Composable
internal fun RecordsContent(
    uiState: AgentConfigUiState,
    onDeleteKnowledgeEntry: (String) -> Unit
) {
    SectionCard(
        title = "当前提示词",
        description = "下方内容就是当前草稿中的系统提示词原文。"
    ) {
        if (uiState.draft.systemInstruction.isBlank()) {
            EmptyPanelMessage("系统提示词为空。")
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = uiState.draft.systemInstruction,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    RecordListSection(
        title = "知识",
        emptyText = "这个 Agent 还没有知识条目。",
        records = uiState.detail?.knowledge.orEmpty(),
        description = "这些知识条目会参与后续推理，可在这里删除不再需要的内容。",
        deletingRecordId = uiState.deletingKnowledgeEntryId,
        onDeleteRecord = onDeleteKnowledgeEntry
    )
    RecordListSection(
        title = "工作记忆",
        emptyText = "最近一次运行还没有写入工作记忆。",
        records = uiState.detail?.workingMemory.orEmpty()
    )
    RecordListSection(
        title = "情节记忆",
        emptyText = "最近一次运行还没有写入情节记忆。",
        records = uiState.detail?.episodicMemory.orEmpty()
    )
    RunsSection(runs = uiState.detail?.runs.orEmpty())
    ActivitySection(activities = uiState.detail?.activities.orEmpty())
}

internal fun LazyListScope.basicsItems(
    draft: AgentEditorDraft,
    onDraftChange: (AgentEditorDraft) -> Unit
) {
    item {
        BasicsContent(draft = draft, onDraftChange = onDraftChange)
    }
}

internal fun LazyListScope.brainItems(
    draft: AgentEditorDraft,
    onDraftChange: (AgentEditorDraft) -> Unit
) {
    item {
        SectionCard(
            title = "大脑连接",
            description = "为当前 Agent 配置独立的 OpenAI 兼容接口。留空时会使用应用默认 Brain。"
        ) {
            FormField(
                label = "Brain 接口地址",
                value = draft.brainEndpoint,
                supporting = "当前 Agent 使用的 OpenAI 兼容接口基础地址。",
                onValueChange = { onDraftChange(draft.copy(brainEndpoint = it)) },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.brainApiKey,
                onValueChange = { onDraftChange(draft.copy(brainApiKey = it)) },
                label = { Text("Brain API 密钥") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    FieldHint("配置自定义接口时必填。界面中会隐藏显示，但仍会保存到 Agent 元数据中。")
                }
            )
            FormField(
                label = "Brain 模型",
                value = draft.brainModelName,
                supporting = "该 Agent 可选的模型名。如果留空，框架会回退到应用默认模型。",
                onValueChange = { onDraftChange(draft.copy(brainModelName = it)) },
                singleLine = true
            )
            FormField(
                label = "Brain 标识",
                value = draft.brainDisplayName,
                supporting = "显示在 Agent 列表中的可选名称，方便快速识别这个 Brain。",
                onValueChange = { onDraftChange(draft.copy(brainDisplayName = it)) },
                singleLine = true
            )
            InfoCallout(
                title = "连接测试",
                body = "保存草稿前，可使用上方“测试大脑”验证当前 URL 和 API 密钥。"
            )
        }
    }
}

internal fun LazyListScope.runtimeItems(
    draft: AgentEditorDraft,
    onDraftChange: (AgentEditorDraft) -> Unit
) {
    item {
        RuntimeContent(draft = draft, onDraftChange = onDraftChange)
    }
}

internal fun LazyListScope.recordsItems(
    uiState: AgentConfigUiState,
    onDeleteKnowledgeEntry: (String) -> Unit = {}
) {
    item {
        SectionCard(
            title = "当前提示词",
            description = "下方内容就是当前草稿中的系统提示词原文。"
        ) {
            if (uiState.draft.systemInstruction.isBlank()) {
                EmptyPanelMessage("系统提示词为空。")
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = uiState.draft.systemInstruction,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    item {
        RecordListSection(
            title = "知识",
            emptyText = "这个 Agent 还没有知识条目。",
            records = uiState.detail?.knowledge.orEmpty(),
            description = "这些知识条目会参与后续推理，可在这里删除不再需要的内容。",
            deletingRecordId = uiState.deletingKnowledgeEntryId,
            onDeleteRecord = onDeleteKnowledgeEntry
        )
    }
    item {
        RecordListSection(
            title = "工作记忆",
            emptyText = "最近一次运行还没有写入工作记忆。",
            records = uiState.detail?.workingMemory.orEmpty()
        )
    }
    item {
        RecordListSection(
            title = "情节记忆",
            emptyText = "最近一次运行还没有写入情节记忆。",
            records = uiState.detail?.episodicMemory.orEmpty()
        )
    }
    item {
        RunsSection(runs = uiState.detail?.runs.orEmpty())
    }
    item {
        ActivitySection(activities = uiState.detail?.activities.orEmpty())
    }
}
