package com.example.watcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.watcher.data.model.CouncilConfig
import com.example.watcher.data.model.CouncilEntryDraft
import com.example.watcher.data.model.CouncilEntryUiState
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.toConfig

private const val STEP_MODE = 0
private const val STEP_BRIEF = 1
private const val STEP_GENERATE = 2
private const val STEP_CONFIRM = 3

@Composable
internal fun LandscapeModeEntryDialog(
    councilTemplates: List<CouncilTemplateEntity>,
    entryState: CouncilEntryUiState,
    onGenerate: (CouncilEntryDraft) -> Unit,
    onSaveGeneratedTemplate: (String) -> Unit,
    onDismiss: () -> Unit,
    onStartLive: () -> Unit,
    onStartCouncil: (CouncilConfig) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(STEP_MODE) }
    var saveAsTemplate by rememberSaveable { mutableStateOf(false) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var localDraft by remember(entryState.draft) { mutableStateOf(entryState.draft) }
    val fieldColors = councilEntryFieldColors()

    LaunchedEffect(entryState.generated?.generatedAt) {
        val generated = entryState.generated ?: return@LaunchedEffect
        templateName = generated.title
        if (step == STEP_GENERATE && !entryState.isGenerating) {
            step = STEP_CONFIRM
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // consume clicks to prevent dismissal
                    ),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF171821)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    WizardHeader(
                        step = step,
                        onClose = onDismiss
                    )

                    when (step) {
                        STEP_MODE -> {
                            ModeChoiceStep(
                                templates = councilTemplates,
                                onStartLive = onStartLive,
                                onChooseCouncil = { step = STEP_BRIEF },
                                onPickTemplate = { template ->
                                    onStartCouncil(template.toConfig())
                                }
                            )
                        }

                        STEP_BRIEF -> {
                            BriefStep(
                                draft = localDraft,
                                fieldColors = fieldColors,
                                errorMessage = entryState.errorMessage,
                                onDraftChange = { localDraft = it },
                                onBack = { step = STEP_MODE },
                                onNext = {
                                    onGenerate(localDraft)
                                    step = STEP_GENERATE
                                }
                            )
                        }

                        STEP_GENERATE -> {
                            GenerateStep(
                                state = entryState,
                                canGenerate = localDraft.canGenerate(),
                                onBack = { step = STEP_BRIEF },
                                onRegenerate = { onGenerate(localDraft) },
                                onContinue = { step = STEP_CONFIRM }
                            )
                        }

                        else -> {
                            ConfirmStep(
                                state = entryState,
                                fieldColors = fieldColors,
                                saveAsTemplate = saveAsTemplate,
                                templateName = templateName,
                                onSaveAsTemplateChange = { saveAsTemplate = it },
                                onTemplateNameChange = { templateName = it },
                                onBack = { step = STEP_GENERATE },
                                onSaveGeneratedTemplate = onSaveGeneratedTemplate,
                                onStartCouncil = onStartCouncil
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(
    step: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = when (step) {
                    STEP_MODE -> "进入横屏模式"
                    STEP_BRIEF -> "第 1 步 · 填写智囊团简报"
                    STEP_GENERATE -> "第 2 步 · 生成结构化模板"
                    else -> "第 3 步 · 确认并启动"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                text = when (step) {
                    STEP_MODE -> "请先选择直播模式或智囊团模式。"
                    STEP_BRIEF -> "描述场景和你需要的帮助。"
                    STEP_GENERATE -> "让 AI 将简报转化为智囊团配置。"
                    else -> "确认生成的配置后进入智囊团模式。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun ModeChoiceStep(
    templates: List<CouncilTemplateEntity>,
    onStartLive: () -> Unit,
    onChooseCouncil: () -> Unit,
    onPickTemplate: (CouncilTemplateEntity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ModeCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LiveTv,
            title = "直播模式",
            body = "直接进入直播间。",
            action = "进入直播",
            onClick = onStartLive
        )
        ModeCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AutoAwesome,
            title = "智囊团模式",
            body = "填写简报，AI 生成智囊团配置。",
            action = "新建智囊团",
            onClick = onChooseCouncil
        )
        if (templates.isNotEmpty()) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF2A2D3A)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = Color(0xFF81D4FA),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "从模板开始",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        templates.take(4).forEach { template ->
                            Surface(
                                onClick = { onPickTemplate(template) },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = template.label,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = template.description,
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
private fun RowScope.ModeCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF2A2D3A)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF81D4FA),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(action)
            }
        }
    }
}

private data class BriefQuestion(
    val prompt: String,
    val placeholder: String,
    val required: Boolean = false,
    val multiLine: Boolean = false,
    val getValue: (CouncilEntryDraft) -> String,
    val setValue: (CouncilEntryDraft, String) -> CouncilEntryDraft
)

private val briefQuestions = listOf(
    BriefQuestion(
        prompt = "这是什么场景？",
        placeholder = "面试、会议、销售、直播……",
        required = true,
        getValue = { it.scene },
        setValue = { d, v -> d.copy(scene = v) }
    ),
    BriefQuestion(
        prompt = "你在场景中的角色是？",
        placeholder = "候选人、卖方、主持人、经理……",
        required = true,
        getValue = { it.speakerRole },
        setValue = { d, v -> d.copy(speakerRole = v) }
    ),
    BriefQuestion(
        prompt = "对方是谁？",
        placeholder = "面试官、客户、老板、观众……",
        getValue = { it.targetRole },
        setValue = { d, v -> d.copy(targetRole = v) }
    ),
    BriefQuestion(
        prompt = "你需要智囊团提供什么帮助？",
        placeholder = "希望智囊团判断或协助什么？",
        required = true,
        multiLine = true,
        getValue = { it.userNeed },
        setValue = { d, v -> d.copy(userNeed = v) }
    ),
    BriefQuestion(
        prompt = "你最担心什么？",
        placeholder = "压力、误导信号、失控、隐藏风险……",
        multiLine = true,
        getValue = { it.concern },
        setValue = { d, v -> d.copy(concern = v) }
    ),
    BriefQuestion(
        prompt = "补充背景信息",
        placeholder = "进入智囊团模式前的额外上下文（可选）",
        multiLine = true,
        getValue = { it.background },
        setValue = { d, v -> d.copy(background = v) }
    )
)

@Composable
private fun BriefStep(
    draft: CouncilEntryDraft,
    fieldColors: TextFieldColors,
    errorMessage: String?,
    onDraftChange: (CouncilEntryDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var qi by rememberSaveable { mutableIntStateOf(0) }
    val q = briefQuestions[qi]
    val value = q.getValue(draft)
    val total = briefQuestions.size
    val isLast = qi == briefQuestions.lastIndex
    val canAdvance = !q.required || value.isNotBlank()

    WizardCard {
        // Top: progress dots + step counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            briefQuestions.forEachIndexed { idx, bq ->
                val filled = bq.getValue(draft).isNotBlank()
                val current = idx == qi
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (current) 10.dp else 8.dp)
                        .background(
                            color = when {
                                current -> Color(0xFF64B5F6)
                                filled -> Color(0xFF81C784)
                                else -> Color.White.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clickable { qi = idx }
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                "${qi + 1} / $total",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Middle: left question, right input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: question + quick options
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = q.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                if (!q.required) {
                    Text("选填 · 可直接跳过", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
            }

            // Right: input field
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onDraftChange(q.setValue(draft, it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(q.placeholder) },
                    singleLine = !q.multiLine,
                    minLines = if (q.multiLine) 3 else 1,
                    maxLines = if (q.multiLine) 5 else 1,
                    colors = fieldColors
                )
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // Bottom bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { if (qi > 0) qi-- else onBack() }) {
                Text(if (qi > 0) "上一步" else "返回")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.canGenerate()) {
                    OutlinedButton(onClick = onNext) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("生成模板")
                    }
                }
                FilledTonalButton(
                    onClick = { if (isLast) onNext() else qi++ },
                    enabled = canAdvance
                ) {
                    Text(if (isLast) "生成模板" else "下一步")
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GenerateStep(
    state: CouncilEntryUiState,
    canGenerate: Boolean,
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()

    WizardCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "AI 正在生成结构化智囊团任务模板……",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            when {
                state.generated != null -> {
                    InfoBlock("生成器", state.generated.providerName)
                    InfoBlock("任务标题", state.generated.title)
                    InfoBlock("场景", state.generated.config.sceneType.name)
                    InfoBlock("目标", state.generated.config.objective)
                    InfoBlock("关注点", state.generated.config.focus)
                    InfoBlock("建议席位", state.generated.suggestedExperts.joinToString(", "))
                    PromptPreviewBlock(state.generated.promptPreview)
                }

                state.errorMessage != null -> {
                    InfoBlock("生成出错", state.errorMessage)
                }

                else -> {
                    InfoBlock(
                        "等待中",
                        "请先执行生成。此步骤将展示根据简报生成的结构化智囊团模板。"
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = canGenerate && !state.isGenerating
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("重新生成")
                }
                FilledTonalButton(
                    onClick = onContinue,
                    enabled = state.generated != null && !state.isGenerating
                ) {
                    Text("下一步")
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    state: CouncilEntryUiState,
    fieldColors: TextFieldColors,
    saveAsTemplate: Boolean,
    templateName: String,
    onSaveAsTemplateChange: (Boolean) -> Unit,
    onTemplateNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onSaveGeneratedTemplate: (String) -> Unit,
    onStartCouncil: (CouncilConfig) -> Unit
) {
    val generated = state.generated
    val scrollState = rememberScrollState()

    WizardCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (generated == null) {
                InfoBlock(
                    "缺少配置",
                    "尚未生成智囊团配置，请返回上一步先执行生成。"
                )
            } else {
                InfoBlock("场景", generated.config.sceneType.name)
                InfoBlock("目标", generated.config.objective)
                InfoBlock("关注点", generated.config.focus)
                InfoBlock("摘要", generated.summary)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = saveAsTemplate,
                        onCheckedChange = onSaveAsTemplateChange
                    )
                    Text(
                        text = "进入智囊团前保存为模板",
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }

                if (saveAsTemplate) {
                    CouncilEntryField(
                        value = templateName,
                        onValueChange = onTemplateNameChange,
                        label = "模板名称",
                        placeholder = "智囊团模板名称",
                        colors = fieldColors
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (generated != null) onSaveGeneratedTemplate(templateName)
                    },
                    enabled = generated != null
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("保存模板")
                }
                FilledTonalButton(
                    onClick = {
                        if (generated != null) {
                            if (saveAsTemplate) onSaveGeneratedTemplate(templateName)
                            onStartCouncil(generated.config)
                        }
                    },
                    enabled = generated != null
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("启动智囊团")
                }
            }
        }
    }
}

@Composable
private fun WizardCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun CouncilEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    colors: TextFieldColors,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = maxLines == 1,
        minLines = minLines,
        maxLines = maxLines,
        colors = colors
    )
}

@Composable
private fun InfoBlock(
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PromptPreviewBlock(prompt: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2230)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "提示词预览",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = prompt,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun councilEntryFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White.copy(alpha = 0.92f),
        focusedBorderColor = Color(0xFF64B5F6),
        unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
        focusedLabelColor = Color(0xFF64B5F6),
        unfocusedLabelColor = Color.White.copy(alpha = 0.62f),
        cursorColor = Color(0xFF64B5F6),
        focusedPlaceholderColor = Color.White.copy(alpha = 0.36f),
        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.28f),
        focusedContainerColor = Color.White.copy(alpha = 0.03f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
    )
}
