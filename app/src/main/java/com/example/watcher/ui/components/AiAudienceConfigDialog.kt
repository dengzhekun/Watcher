package com.example.watcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.LlmProviderEntity

private val darkFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White.copy(alpha = 0.8f),
        focusedBorderColor = Color(0xFF42A5F5),
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedLabelColor = Color(0xFF42A5F5),
        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
        cursorColor = Color(0xFF42A5F5)
    )

@Composable
fun AiAudienceConfigDialog(
    providers: List<LlmProviderEntity>,
    audiences: List<AiAudienceEntity>,
    onSaveProvider: (LlmProviderEntity) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSaveAudience: (AiAudienceEntity) -> Unit,
    onDeleteAudience: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onDismiss)
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.9f)
                    .width(520.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
            ) {
                var selectedTab by remember { mutableIntStateOf(0) }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI 观众配置", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF42A5F5)
                            )
                        }
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("模型接口", modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                        }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("观众角色", modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                        }
                    }

                    when (selectedTab) {
                        0 -> ProviderTab(providers, onSaveProvider, onDeleteProvider)
                        1 -> AudienceTab(audiences, providers, onSaveAudience, onDeleteAudience)
                    }
                }
            }
        }
    }
}

// --- Provider Tab ---

@Composable
private fun ProviderTab(
    providers: List<LlmProviderEntity>,
    onSave: (LlmProviderEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var editingProvider by remember { mutableStateOf<LlmProviderEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (showForm || editingProvider != null) {
            ProviderForm(
                initial = editingProvider,
                onSave = {
                    onSave(it)
                    editingProvider = null
                    showForm = false
                },
                onCancel = {
                    editingProvider = null
                    showForm = false
                }
            )
        } else {
            Button(
                onClick = { showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = 4.dp))
                Text("添加模型接口")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(providers, key = { it.id }) { provider ->
                    ProviderCard(provider,
                        onEdit = { editingProvider = provider },
                        onDelete = { onDelete(provider.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: LlmProviderEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${provider.modelName} · ${provider.endpoint.take(40)}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "编辑", tint = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFEF5350).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ProviderForm(
    initial: LlmProviderEntity?,
    onSave: (LlmProviderEntity) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var endpoint by remember { mutableStateOf(initial?.endpoint ?: "https://") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var modelName by remember { mutableStateOf(initial?.modelName ?: "") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                if (initial != null) "编辑模型接口" else "添加模型接口",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
        }
        item {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("例如：DeepSeek") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
            )
        }
        item {
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it },
                label = { Text("API Endpoint") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
            )
        }
        item {
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors,
                visualTransformation = PasswordVisualTransformation()
            )
        }
        item {
            OutlinedTextField(
                value = modelName, onValueChange = { modelName = it },
                label = { Text("模型名称") },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消", color = Color.White.copy(alpha = 0.7f))
                }
                Button(
                    onClick = {
                        if (name.isNotBlank() && endpoint.isNotBlank() && modelName.isNotBlank()) {
                            val id = initial?.id ?: name.lowercase().replace(" ", "_") + "_" + System.currentTimeMillis()
                            onSave(LlmProviderEntity(
                                id = id, name = name, endpoint = endpoint,
                                apiKey = apiKey, modelName = modelName,
                                enabled = true,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            ))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}

// --- Audience Tab ---

@Composable
private fun AudienceTab(
    audiences: List<AiAudienceEntity>,
    providers: List<LlmProviderEntity>,
    onSave: (AiAudienceEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editingAudience by remember { mutableStateOf<AiAudienceEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (showForm || editingAudience != null) {
            AudienceForm(
                initial = editingAudience,
                providers = providers,
                onSave = {
                    onSave(it)
                    editingAudience = null
                    showForm = false
                },
                onCancel = {
                    editingAudience = null
                    showForm = false
                }
            )
        } else {
            if (providers.isEmpty()) {
                Text("请先添加模型接口", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            } else {
                Button(
                    onClick = { showForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = 4.dp))
                    Text("添加观众")
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(audiences, key = { it.id }) { audience ->
                    AudienceCard(audience, providers,
                        onEdit = { editingAudience = audience },
                        onDelete = { onDelete(audience.id) },
                        onToggle = { onSave(audience.copy(enabled = it, updatedAt = System.currentTimeMillis())) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudienceCard(
    audience: AiAudienceEntity,
    providers: List<LlmProviderEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val providerName = providers.find { it.id == audience.providerId }?.name ?: audience.providerId

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = if (audience.enabled) 0.08f else 0.03f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(audience.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${audience.audienceType.label} · $providerName · ${audience.heartbeatIntervalSeconds}s",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                )
                if (audience.audienceType == AudienceEngineType.Agent && audience.socialArchetype.isNotBlank()) {
                    Text(
                        audience.socialArchetype,
                        color = Color(0xFF42A5F5).copy(alpha = 0.75f),
                        fontSize = 10.sp
                    )
                }
                Text(
                    audience.persona.take(50) + if (audience.persona.length > 50) "…" else "",
                    color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, maxLines = 1
                )
            }
            Switch(
                checked = audience.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF42A5F5))
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "编辑", tint = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFEF5350).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun AudienceForm(
    initial: AiAudienceEntity?,
    providers: List<LlmProviderEntity>,
    onSave: (AiAudienceEntity) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var audienceType by remember { mutableStateOf(initial?.audienceType ?: AudienceEngineType.Agent) }
    var persona by remember { mutableStateOf(initial?.persona ?: "你是一个热情的直播观众，喜欢对画面内容发表简短有趣的评论。") }
    var socialArchetype by remember { mutableStateOf(initial?.socialArchetype ?: "") }
    var speakingStyle by remember { mutableStateOf(initial?.speakingStyle ?: "") }
    var spendingStyle by remember { mutableStateOf(initial?.spendingStyle ?: "") }
    var socialDrive by remember { mutableStateOf(initial?.socialDrive ?: "") }
    var selectedProviderId by remember { mutableStateOf(initial?.providerId ?: providers.firstOrNull()?.id ?: "") }
    var heartbeat by remember { mutableStateOf((initial?.heartbeatIntervalSeconds ?: 15).toString()) }
    var includeFrame by remember { mutableStateOf(initial?.includeFrame ?: false) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                if (initial != null) "编辑观众" else "添加观众",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
        }
        item {
            Text("观众引擎", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AudienceEngineType.values().forEach { type ->
                    val isSelected = type == audienceType
                    OutlinedButton(
                        onClick = { audienceType = type },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF42A5F5).copy(alpha = 0.2f) else Color.Transparent
                        )
                    ) {
                        Text(
                            type.label,
                            color = if (isSelected) Color(0xFF42A5F5) else Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("例如：小蓝") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
            )
        }
        item {
            OutlinedTextField(
                value = persona, onValueChange = { persona = it },
                label = { Text("人格设定") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = darkFieldColors, maxLines = 5
            )
        }
        if (audienceType == AudienceEngineType.Agent) {
            item {
                OutlinedTextField(
                    value = socialArchetype, onValueChange = { socialArchetype = it },
                    label = { Text("社交定位") },
                    placeholder = { Text("例如：乐子人 / 守护型 / 理中客") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
                )
            }
            item {
                OutlinedTextField(
                    value = speakingStyle, onValueChange = { speakingStyle = it },
                    label = { Text("说话风格") },
                    placeholder = { Text("例如：短句、爱玩梗、略阴阳") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
                )
            }
            item {
                OutlinedTextField(
                    value = spendingStyle, onValueChange = { spendingStyle = it },
                    label = { Text("消费风格") },
                    placeholder = { Text("例如：谨慎消费 / 上头就刷") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
                )
            }
            item {
                OutlinedTextField(
                    value = socialDrive, onValueChange = { socialDrive = it },
                    label = { Text("社交驱动力") },
                    placeholder = { Text("例如：想被主播注意 / 喜欢和观众抬杠") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors
                )
            }
        }
        item {
            // Provider selector as simple text buttons
            Text("选择模型接口", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.forEach { p ->
                    val isSelected = p.id == selectedProviderId
                    OutlinedButton(
                        onClick = { selectedProviderId = p.id },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF42A5F5).copy(alpha = 0.2f) else Color.Transparent
                        )
                    ) {
                        Text(p.name, color = if (isSelected) Color(0xFF42A5F5) else Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = heartbeat, onValueChange = { heartbeat = it.filter { c -> c.isDigit() } },
                label = { Text("心跳间隔（秒）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkFieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("附带当前画面帧", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = includeFrame, onCheckedChange = { includeFrame = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF42A5F5)))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF42A5F5)))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消", color = Color.White.copy(alpha = 0.7f))
                }
                Button(
                    onClick = {
                        if (name.isNotBlank() && selectedProviderId.isNotBlank()) {
                            onSave(AiAudienceEntity(
                                id = initial?.id ?: 0,
                                name = name,
                                audienceType = audienceType,
                                persona = persona,
                                socialArchetype = socialArchetype,
                                speakingStyle = speakingStyle,
                                spendingStyle = spendingStyle,
                                socialDrive = socialDrive,
                                providerId = selectedProviderId,
                                enabled = enabled,
                                heartbeatIntervalSeconds = heartbeat.toIntOrNull()?.coerceAtLeast(5) ?: 15,
                                includeFrame = includeFrame,
                                personalMemory = initial?.personalMemory ?: "",
                                agentStateJson = initial?.agentStateJson ?: "",
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            ))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}
