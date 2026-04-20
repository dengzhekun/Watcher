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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.watcher.data.model.CouncilSceneType
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.ui.components.ActionRow
import com.example.watcher.ui.components.EmptyHint
import com.example.watcher.ui.components.FormField
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun CouncilTemplateListCard(
    templates: List<CouncilTemplateEntity>,
    onUpdate: (CouncilTemplateEntity) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (CouncilTemplateEntity) -> String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val defaultCount = templates.count { it.isDefault }
    val summary = when {
        templates.isEmpty() -> "暂无智囊团模板。"
        else -> "${templates.size} 个模板 · 默认模板 $defaultCount 个"
    }

    WatcherCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("智囊团模板", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "这些模板决定智囊团模式的场景定位、目标和关注重点。可以直接编辑、恢复默认或分享给别人导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起智囊团模板" else "展开智囊团模板"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (templates.isEmpty()) {
                        EmptyHint(text = "暂无智囊团模板。")
                    } else {
                        templates.forEach { template ->
                            CouncilTemplateItem(
                                template = template,
                                onSave = onUpdate,
                                onReset = { onReset(template.templateId) },
                                onDelete = { onDelete(template.templateId) },
                                onExport = { onExport(template) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouncilTemplateItem(
    template: CouncilTemplateEntity,
    onSave: (CouncilTemplateEntity) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> String
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember(template.templateId) { mutableStateOf(false) }
    var label by remember(template.templateId, template.label) { mutableStateOf(template.label) }
    var description by remember(template.templateId, template.description) { mutableStateOf(template.description) }
    var objective by remember(template.templateId, template.objective) { mutableStateOf(template.objective) }
    var focus by remember(template.templateId, template.focus) { mutableStateOf(template.focus) }
    var sceneType by remember(template.templateId, template.sceneType) { mutableStateOf(template.sceneType) }

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
                    clipboardManager.setText(AnnotatedString(onExport()))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "分享")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.Edit, contentDescription = if (expanded) "收起" else "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(label = "模板名称", value = label, onValueChange = { label = it })
                    FormField(label = "模板描述", value = description, onValueChange = { description = it })
                    FormField(
                        label = "场景类型（Speech / Meeting / Interview / General）",
                        value = sceneType,
                        onValueChange = { sceneType = it }
                    )
                    FormField(
                        label = "用户目标",
                        value = objective,
                        onValueChange = { objective = it },
                        minLines = 3
                    )
                    FormField(
                        label = "关注重点",
                        value = focus,
                        onValueChange = { focus = it },
                        minLines = 3
                    )

                    ActionRow(
                        primaryLabel = "保存修改",
                        onPrimaryClick = {
                            onSave(
                                template.copy(
                                    label = label,
                                    description = description,
                                    sceneType = sceneType.toSceneTypeName(),
                                    objective = objective,
                                    focus = focus
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

private fun String.toSceneTypeName(): String {
    return runCatching { CouncilSceneType.valueOf(trim()) }
        .getOrDefault(CouncilSceneType.General)
        .name
}
