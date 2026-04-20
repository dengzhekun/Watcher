package com.example.watcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.repository.ProviderConnectivitySnapshot
import com.example.watcher.data.repository.ProviderConnectivityStatus
import com.example.watcher.ui.viewmodel.ApiWalletDraft
import com.example.watcher.ui.viewmodel.ApiWalletUiState

private enum class ProviderUsageState {
    Active,
    Selectable,
    Fallback,
    NotIntegrated
}

private data class ProviderUsageProbe(
    val feature: String,
    val state: ProviderUsageState,
    val detail: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiWalletScreen(
    uiState: ApiWalletUiState,
    onBack: () -> Unit,
    onStartCreate: () -> Unit,
    onEditProvider: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onDraftChange: ((ApiWalletDraft) -> ApiWalletDraft) -> Unit,
    onSaveDraft: () -> Unit,
    onTestProvider: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onClearMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val message = uiState.errorMessage ?: uiState.statusMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onClearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("API 钱包") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onStartCreate) {
                        Icon(Icons.Default.Add, contentDescription = "新增供应商")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                WalletSummaryCard(uiState)
            }

            if (uiState.isEditing) {
                item {
                    ProviderEditorCard(
                        draft = uiState.draft,
                        isSaving = uiState.isSaving,
                        onDraftChange = onDraftChange,
                        onSave = onSaveDraft,
                        onCancel = onCancelEditing
                    )
                }
            } else {
                item {
                    FilledTonalButton(
                        onClick = onStartCreate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("新增供应商", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item {
                SectionTitle(
                    title = "已保存的供应商",
                    subtitle = "每张卡片都会显示使用足迹，方便你判断该供应商当前在哪些功能中生效、可选或回退。"
                )
            }

            if (uiState.providers.isEmpty()) {
                item {
                    EmptyWalletCard(uiState.arkFallbackAvailable)
                }
            } else {
                items(uiState.providers, key = { it.id }) { provider ->
                    ProviderItemCard(
                        provider = provider,
                        isDefault = provider.id == uiState.defaultProviderId,
                        isTesting = provider.id == uiState.testingProviderId,
                        connectivity = uiState.providerConnectivity[provider.id]
                            ?: ProviderConnectivitySnapshot(),
                        onEdit = { onEditProvider(provider.id) },
                        onTest = { onTestProvider(provider.id) },
                        onDelete = { onDeleteProvider(provider.id) },
                        onSetDefault = { onSetDefault(provider.id) },
                        onToggleEnabled = { enabled -> onToggleEnabled(provider.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletSummaryCard(uiState: ApiWalletUiState) {
    val defaultProvider = uiState.defaultProvider

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "当前全局供应商",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = defaultProvider?.name ?: "尚未选择默认供应商",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (defaultProvider != null) {
                        "${defaultProvider.modelName} / ${defaultProvider.endpoint}"
                    } else if (uiState.arkFallbackAvailable) {
                        "当前未选中已保存的钱包项，应用仍可回退到本地 API_KEY。"
                    } else {
                        "当前既没有选中已保存的钱包项，也没有可用的本地回退配置。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "供 Agent、Brain 和其他全局 LLM 请求统一使用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = buildString {
                append("已保存 ${uiState.providers.size} 个")
                append(" · ")
                append("已启用 ${uiState.providers.count { it.enabled }} 个")
                if (uiState.arkFallbackAvailable) {
                    append(" · ")
                    append("本地回退可用")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProviderEditorCard(
    draft: ApiWalletDraft,
    isSaving: Boolean,
    onDraftChange: ((ApiWalletDraft) -> ApiWalletDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (draft.id == null) "新增供应商" else "编辑供应商",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "保存后，这个钱包条目就会对应用其他模块可用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onDraftChange { it.copy(name = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("供应商名称") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.endpoint,
                onValueChange = { value -> onDraftChange { it.copy(endpoint = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { value -> onDraftChange { it.copy(apiKey = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 密钥") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            OutlinedTextField(
                value = draft.modelName,
                onValueChange = { value -> onDraftChange { it.copy(modelName = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("启用", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "禁用后仍会保留该供应商，但不会被自动选中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { checked -> onDraftChange { it.copy(enabled = checked) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (isSaving) "保存中..." else "保存")
                }
            }
        }
    }
}

@Composable
private fun ProviderItemCard(
    provider: LlmProviderEntity,
    isDefault: Boolean,
    isTesting: Boolean,
    connectivity: ProviderConnectivitySnapshot,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val defaultAccent = Color(0xFFB7791F)
    val defaultContainer = Color(0xFFFFF6E5)
    val usageProbes = remember(provider.id, provider.enabled, provider.endpoint, isDefault) {
        buildUsageProbes(provider = provider, isDefault = isDefault)
    }
    var usageExpanded by remember(provider.id) { mutableStateOf(false) }
    val effectiveStatus = if (isTesting) {
        ProviderConnectivityStatus.Untested
    } else {
        connectivity.status
    }
    val statusLabel = when {
        isTesting -> "检测中"
        connectivity.status == ProviderConnectivityStatus.Verified -> "已验证"
        connectivity.status == ProviderConnectivityStatus.Failed -> "失败"
        else -> "未检测"
    }
    val statusAccent = when {
        isTesting -> MaterialTheme.colorScheme.primary
        connectivity.status == ProviderConnectivityStatus.Verified -> Color(0xFF18794E)
        connectivity.status == ProviderConnectivityStatus.Failed -> MaterialTheme.colorScheme.error
        else -> Color(0xFF9A6700)
    }
    val statusContainer = when {
        isTesting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        connectivity.status == ProviderConnectivityStatus.Verified -> Color(0xFF18794E).copy(alpha = 0.12f)
        connectivity.status == ProviderConnectivityStatus.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> Color(0xFF9A6700).copy(alpha = 0.12f)
    }
    val statusMessage = when {
        isTesting -> "正在用当前接口地址和 API 密钥测试连通性。"
        connectivity.status == ProviderConnectivityStatus.Verified -> buildString {
            append("连通性验证成功")
            connectivity.lastTestedAt?.let {
                append(" · ")
                append(formatDateTime(it))
            }
            connectivity.message?.takeIf { it.isNotBlank() }?.let {
                append("\n")
                append(it)
            }
        }
        connectivity.status == ProviderConnectivityStatus.Failed -> buildString {
            append("最近一次测试失败")
            connectivity.lastTestedAt?.let {
                append(" · ")
                append(formatDateTime(it))
            }
            connectivity.message?.takeIf { it.isNotBlank() }?.let {
                append("\n")
                append(it)
            }
        }
        else -> "建议先测试该供应商，再将其设为全局默认。"
    }
    val testButtonLabel = when {
        isTesting -> "测试中..."
        effectiveStatus == ProviderConnectivityStatus.Verified -> "重新测试"
        effectiveStatus == ProviderConnectivityStatus.Failed -> "重试测试"
        else -> "立即测试"
    }
    val usePrimaryTestButton = effectiveStatus != ProviderConnectivityStatus.Verified
    val cardContainerColor = if (isDefault) {
        defaultContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (isDefault) {
        BorderStroke(1.dp, defaultAccent.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isDefault) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = defaultAccent.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = defaultAccent
                            )
                            Text(
                                text = "默认",
                                style = MaterialTheme.typography.titleSmall,
                                color = defaultAccent
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = provider.name, style = MaterialTheme.typography.titleMedium)
                        if (!provider.enabled) {
                            SummaryBadge(text = "已禁用")
                        }
                    }
                    Text(
                        text = provider.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = provider.endpoint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = maskApiKey(provider.apiKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusContainer
                    ) {
                        Text(
                            text = statusLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusAccent
                        )
                    }
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑供应商")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除供应商",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            UsageFootprintCard(
                probes = usageProbes,
                providerEnabled = provider.enabled,
                expanded = usageExpanded,
                onToggleExpanded = { usageExpanded = !usageExpanded }
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (usePrimaryTestButton) {
                        Button(
                            onClick = onTest,
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (effectiveStatus == ProviderConnectivityStatus.Failed) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        ) {
                            Text(testButtonLabel)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onTest,
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(testButtonLabel)
                        }
                    }
                    if (!isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("☆", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("设为默认", modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            color = defaultAccent.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = defaultAccent
                                )
                                Text(
                                    text = "默认",
                                    modifier = Modifier.padding(start = 8.dp),
                                    color = defaultAccent
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (provider.enabled) "可参与全局解析" else "已排除全局解析",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (provider.enabled) {
                                    "这个钱包可被选为应用级默认供应商。"
                                } else {
                                    "禁用后仍会保留该钱包，但不会被自动选中。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = onToggleEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageFootprintCard(
    probes: List<ProviderUsageProbe>,
    providerEnabled: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val activeCount = probes.count { it.state == ProviderUsageState.Active }
    val selectableCount = probes.count { it.state == ProviderUsageState.Selectable }
    val fallbackCount = probes.count { it.state == ProviderUsageState.Fallback }
    val summary = buildString {
        append("已生效 $activeCount 项")
        if (selectableCount > 0) append(" · 可切换 $selectableCount 项")
        if (fallbackCount > 0) append(" · 回退 $fallbackCount 项")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "使用足迹",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (providerEnabled) {
                            "当前覆盖范围：$summary"
                        } else {
                            "当前已禁用全局解析。$summary"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "收起详情" else "查看详情")
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            if (expanded) {
                Text(
                    text = if (providerEnabled) {
                        "该探针反映的是应用当前的接线情况。显示为“已生效”表示该功能现在就会使用这个供应商。"
                    } else {
                        "该供应商已禁用全局解析；在支持已保存 Brain 绑定的功能中，仍可能被手动选中。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                probes.forEach { probe ->
                    UsageProbeRow(probe)
                }
            }
        }
    }
}

@Composable
private fun UsageProbeRow(probe: ProviderUsageProbe) {
    val (accent, container, label) = when (probe.state) {
        ProviderUsageState.Active -> Triple(
            Color(0xFF18794E),
            Color(0xFF18794E).copy(alpha = 0.12f),
            "当前生效"
        )

        ProviderUsageState.Selectable -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            "可切换"
        )

        ProviderUsageState.Fallback -> Triple(
            Color(0xFF9A6700),
            Color(0xFF9A6700).copy(alpha = 0.12f),
            "回退"
        )

        ProviderUsageState.NotIntegrated -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surface,
            "未接入"
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = probe.feature,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = probe.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = container
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

@Composable
private fun EmptyWalletCard(arkFallbackAvailable: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("还没有已保存的供应商", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (arkFallbackAvailable) {
                    "你仍可依赖本地 API_KEY 回退运行，但保存钱包条目后，全局 LLM 来源会更明确且可复用。"
                } else {
                    "请至少创建一个供应商，让应用拥有可用的全局 LLM 钱包条目。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryBadge(
    text: String,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun maskApiKey(apiKey: String): String {
    if (apiKey.isBlank()) return "API 密钥为空"
    if (apiKey.length <= 8) return "密钥：${"*".repeat(apiKey.length)}"
    return "密钥：${apiKey.take(4)}****${apiKey.takeLast(4)}"
}

private fun buildUsageProbes(
    provider: LlmProviderEntity,
    isDefault: Boolean
): List<ProviderUsageProbe> {
    val enabledDefault = provider.enabled && isDefault
    val arkCompatible = provider.isArkResponsesCompatible()

    fun openAiDefaultProbe(feature: String): ProviderUsageProbe {
        return when {
            enabledDefault -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Active,
                detail = "这个功能会在调用时解析当前钱包默认项，因此现在就会使用这个供应商。"
            )

            provider.enabled -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Selectable,
                detail = "将这个供应商设为默认后，该功能就会通过它的接口地址、API 密钥和模型发起请求。"
            )

            else -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "禁用的供应商会被全局钱包解析跳过，因此该功能不会自动选中它。"
            )
        }
    }

    fun arkDefaultProbe(feature: String): ProviderUsageProbe {
        return when {
            enabledDefault && arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Active,
                detail = "这个兼容 Ark 的供应商当前就是默认项，因此该功能现在可以直接使用它。"
            )

            enabledDefault && !arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "这个功能要求接口兼容 Ark Responses，因此会回退到本地 API_KEY。"
            )

            provider.enabled && arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Selectable,
                detail = "将这个供应商设为默认后，这个兼容 Ark 的功能就可以走它。"
            )

            provider.enabled && !arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "即便设为默认，这个功能仍会回退，因为该接口不兼容 Ark Responses。"
            )

            else -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "这个供应商已禁用全局解析，因此该功能无法自动选中它。"
            )
        }
    }

    return listOf(
        ProviderUsageProbe(
            feature = "已保存 Brain 绑定",
            state = ProviderUsageState.Selectable,
            detail = "Agent Config 可以把已保存的 Brain 直接绑定到这个供应商，而不必把它设为全局默认。"
        ),
        openAiDefaultProbe("默认 Agent 与 Brain 测试"),
        openAiDefaultProbe("智囊团配置生成器"),
        arkDefaultProbe("意图解析"),
        arkDefaultProbe("实时监控"),
        arkDefaultProbe("直播解说视频"),
        arkDefaultProbe("解说与场景记忆"),
        ProviderUsageProbe(
            feature = "视频流程",
            state = ProviderUsageState.NotIntegrated,
            detail = "这条流程目前仍直接读取本地 API_KEY 和固定 Ark 模型，尚未接入 ApiWallet。"
        )
    )
}

private fun LlmProviderEntity.isArkResponsesCompatible(): Boolean {
    val normalized = endpoint
        .trim()
        .lowercase()
        .removeSuffix("/")
    return normalized.isBlank() ||
        normalized.contains("ark.cn-beijing.volces.com") ||
        normalized.endsWith("/api/v3") ||
        normalized.endsWith("/api/v3/responses")
}
