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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.ui.components.ActionRow
import com.example.watcher.ui.components.EmptyHint
import com.example.watcher.ui.components.FormField
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun MonitorTemplateListCard(
    templates: List<MonitorTemplateEntity>,
    onUpdate: (MonitorTemplateEntity) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (MonitorTemplateEntity) -> String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val defaultCount = templates.count { it.isDefault }
    val summary = when {
        templates.isEmpty() -> "暂无监控模板。"
        else -> "${templates.size} 个模板 · 默认模板 $defaultCount 个"
    }

    WatcherCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("监控模板", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "编辑模板参数后，工作台中的监控卡片会直接使用最新配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起监控模板" else "展开监控模板"
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
    var sceneDescription by remember(template.templateId, template.originalSceneDescription) {
        mutableStateOf(template.originalSceneDescription)
    }
    var interval by remember(template.templateId, template.checkIntervalSeconds) {
        mutableStateOf(template.checkIntervalSeconds.toString())
    }
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
                    FormField(label = "监控目标", value = requirement, onValueChange = { requirement = it })
                    FormField(label = "场景描述", value = sceneDescription, onValueChange = { sceneDescription = it })
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
                            text = "监控模式：${template.monitorMode} | 基线来源：${template.baselineSource}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    ActionRow(
                        primaryLabel = "保存",
                        onPrimaryClick = {
                            onSave(
                                template.copy(
                                    label = label,
                                    description = description,
                                    userRequirement = requirement,
                                    originalSceneDescription = sceneDescription,
                                    checkIntervalSeconds = interval.toIntOrNull()
                                        ?: template.checkIntervalSeconds,
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
