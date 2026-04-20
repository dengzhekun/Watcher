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
import com.example.watcher.data.model.VideoTemplateEntity
import com.example.watcher.ui.components.ActionRow
import com.example.watcher.ui.components.EmptyHint
import com.example.watcher.ui.components.FormField
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun VideoTemplateListCard(
    templates: List<VideoTemplateEntity>,
    onUpdate: (VideoTemplateEntity) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (VideoTemplateEntity) -> String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val defaultCount = templates.count { it.isDefault }
    val summary = when {
        templates.isEmpty() -> "暂无视频分析模板。"
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
                    Text("视频分析模板", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "编辑模板参数后，工作台中的视频分析卡片会直接使用最新配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起视频分析模板" else "展开视频分析模板"
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
    var duration by remember(template.templateId, template.recordingDurationSeconds) {
        mutableStateOf(template.recordingDurationSeconds.toString())
    }
    var segmentDuration by remember(template.templateId, template.segmentDurationSeconds) {
        mutableStateOf(template.segmentDurationSeconds.toString())
    }
    var captureInterval by remember(template.templateId, template.captureIntervalSeconds) {
        mutableStateOf(template.captureIntervalSeconds.toString())
    }
    var fps by remember(template.templateId, template.samplingFps) {
        mutableStateOf(template.samplingFps.toString())
    }
    var segmentPrompt by remember(template.templateId, template.segmentAnalysisPrompt) {
        mutableStateOf(template.segmentAnalysisPrompt)
    }
    var summaryPrompt by remember(template.templateId, template.finalSummaryPrompt) {
        mutableStateOf(template.finalSummaryPrompt)
    }

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
                    FormField(label = "任务要求", value = requirement, onValueChange = { requirement = it })
                    FormField(label = "场景描述", value = sceneContext, onValueChange = { sceneContext = it })
                    FormField(
                        label = "录制时长（秒）",
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "分段时长（秒）",
                        value = segmentDuration,
                        onValueChange = { segmentDuration = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "采样间隔（秒）",
                        value = captureInterval,
                        onValueChange = { captureInterval = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "采样帧率 FPS",
                        value = fps,
                        onValueChange = { fps = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number
                    )
                    FormField(
                        label = "分段分析提示词",
                        value = segmentPrompt,
                        onValueChange = { segmentPrompt = it },
                        minLines = 4
                    )
                    FormField(
                        label = "最终总结提示词",
                        value = summaryPrompt,
                        onValueChange = { summaryPrompt = it },
                        minLines = 4
                    )

                    ActionRow(
                        primaryLabel = "保存",
                        onPrimaryClick = {
                            onSave(
                                template.copy(
                                    label = label,
                                    description = description,
                                    userRequirement = requirement,
                                    sceneContext = sceneContext,
                                    recordingDurationSeconds = duration.toIntOrNull()
                                        ?: template.recordingDurationSeconds,
                                    segmentDurationSeconds = segmentDuration.toIntOrNull()
                                        ?: template.segmentDurationSeconds,
                                    captureIntervalSeconds = captureInterval.toIntOrNull()
                                        ?: template.captureIntervalSeconds,
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
