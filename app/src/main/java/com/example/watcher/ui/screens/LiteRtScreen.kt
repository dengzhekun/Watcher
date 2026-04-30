package com.example.watcher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.watcher.data.local.litert.DownloadProgress
import com.example.watcher.data.local.litert.DownloadState
import com.example.watcher.data.local.litert.LiteRtBackendType
import com.example.watcher.data.local.litert.LiteRtEngineState
import com.example.watcher.data.local.litert.LiteRtEngineStatus
import com.example.watcher.data.local.litert.LiteRtModelConfig
import com.example.watcher.ui.viewmodel.ChatEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiteRtScreen(
    engineStatus: LiteRtEngineStatus,
    savedConfig: LiteRtModelConfig?,
    chatHistory: List<ChatEntry>,
    generating: Boolean,
    pendingImageUri: Uri?,
    downloadProgress: DownloadProgress,
    isModelDownloaded: Boolean,
    onLoadEngine: (LiteRtModelConfig) -> Unit,
    onUnloadEngine: () -> Unit,
    onScanForModel: () -> Unit,
    onSendMessage: (String) -> Unit,
    onAttachImage: (Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onClearChat: () -> Unit,
    onDownloadModel: () -> Unit,
    onClose: () -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }
    val isReady = engineStatus.state == LiteRtEngineState.Ready

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(onAttachImage) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地模型对话") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (chatHistory.isNotEmpty()) {
                        IconButton(onClick = onClearChat) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空对话")
                        }
                    }
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "模型配置")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Compact status bar
            EngineStatusBar(engineStatus)

            // Chat messages
            val listState = rememberLazyListState()
            LaunchedEffect(chatHistory.size) {
                if (chatHistory.isNotEmpty()) {
                    listState.animateScrollToItem(chatHistory.size - 1)
                }
            }

            if (!isReady && chatHistory.isEmpty()) {
                // Show download/setup prompt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ModelSetupPrompt(
                        downloadProgress = downloadProgress,
                        isModelDownloaded = isModelDownloaded,
                        onDownload = onDownloadModel,
                        onScanForModel = onScanForModel,
                        onOpenConfig = { showConfigDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(chatHistory) { entry ->
                        ChatBubble(entry)
                    }
                    if (generating) {
                        item { GeneratingIndicator() }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            // Image preview
            if (pendingImageUri != null) {
                ImagePreviewBar(uri = pendingImageUri, onRemove = onClearAttachment)
            }

            // Input bar
            ChatInputBar(
                enabled = isReady && !generating,
                onSend = onSendMessage,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }

    if (showConfigDialog) {
        ModelConfigDialog(
            engineStatus = engineStatus,
            savedConfig = savedConfig,
            onLoadEngine = onLoadEngine,
            onUnloadEngine = onUnloadEngine,
            onDismiss = { showConfigDialog = false }
        )
    }
}

@Composable
private fun EngineStatusBar(status: LiteRtEngineStatus) {
    val stateColor = when (status.state) {
        LiteRtEngineState.Ready -> MaterialTheme.colorScheme.primary
        LiteRtEngineState.Initializing -> MaterialTheme.colorScheme.tertiary
        LiteRtEngineState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (status.state) {
        LiteRtEngineState.NotConfigured -> "未配置模型 · 点击右上角齿轮配置"
        LiteRtEngineState.Idle -> "空闲"
        LiteRtEngineState.Initializing -> "加载中..."
        LiteRtEngineState.Ready -> {
            val name = status.modelConfig?.displayName?.ifBlank {
                status.modelConfig.modelPath.substringAfterLast('/')
            } ?: "模型"
            val backend = status.modelConfig?.backend?.name ?: ""
            "$name · $backend · 就绪"
        }
        LiteRtEngineState.Error -> "错误: ${status.errorMessage ?: "未知"}"
        LiteRtEngineState.Closing -> "关闭中..."
    }

    Surface(
        color = stateColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = stateColor
        )
    }
}

@Composable
private fun ModelSetupPrompt(
    downloadProgress: DownloadProgress,
    isModelDownloaded: Boolean,
    onDownload: () -> Unit,
    onScanForModel: () -> Unit,
    onOpenConfig: () -> Unit
) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        when (downloadProgress.state) {
            DownloadState.Downloading -> {
                Text(
                    text = "正在下载模型...",
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = { downloadProgress.progressPercent },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = downloadProgress.progressMB,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DownloadState.Failed -> {
                Text(
                    text = "下载失败",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = downloadProgress.errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onDownload) { Text("重试下载") }
            }
            else -> {
                if (isModelDownloaded) {
                    Text(
                        text = "已检测到本地模型",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "升级后会优先扫描已有模型；如果路径没恢复，可以手动重新扫描。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onScanForModel) { Text("扫描已有模型") }
                        OutlinedButton(onClick = onOpenConfig) { Text("打开配置") }
                    }
                } else {
                    Text(
                        text = "未检测到可用模型",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Gemma 4 E2B (2.58 GB)\n支持文本 + 图片多模态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    OutlinedButton(onClick = onScanForModel) { Text("扫描已有模型") }
                    Button(onClick = onDownload) { Text("下载模型") }
                    TextButton(onClick = onOpenConfig) { Text("手动配置路径") }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    val isUser = entry.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                entry.imageUri?.let { uriStr ->
                    UriImage(
                        uri = Uri.parse(uriStr),
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun GeneratingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "思考中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewBar(uri: Uri, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UriImage(
                uri = uri,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "已附加图片",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    enabled: Boolean,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPickImage, enabled = enabled) {
                Icon(Icons.Default.AttachFile, contentDescription = "附加图片")
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (enabled) "输入消息..." else "请先配置模型") },
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    val text = inputText
                    inputText = ""
                    keyboardController?.hide()
                    onSend(text)
                },
                enabled = enabled && inputText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun ModelConfigDialog(
    engineStatus: LiteRtEngineStatus,
    savedConfig: LiteRtModelConfig?,
    onLoadEngine: (LiteRtModelConfig) -> Unit,
    onUnloadEngine: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialConfig = savedConfig ?: engineStatus.modelConfig
    var modelPath by rememberSaveable { mutableStateOf(initialConfig?.modelPath ?: "") }
    var displayName by rememberSaveable { mutableStateOf(initialConfig?.displayName ?: "") }
    var backend by remember { mutableStateOf(initialConfig?.backend ?: LiteRtBackendType.GPU) }
    var visionBackend by remember { mutableStateOf(initialConfig?.visionBackend) }
    var audioBackend by remember { mutableStateOf(initialConfig?.audioBackend) }
    var temperature by remember { mutableFloatStateOf(initialConfig?.temperature ?: 0.7f) }
    var topK by remember { mutableIntStateOf(initialConfig?.topK ?: 40) }
    var topP by remember { mutableFloatStateOf(initialConfig?.topP ?: 0.95f) }

    val isLoading = engineStatus.state == LiteRtEngineState.Initializing
    val isReady = engineStatus.state == LiteRtEngineState.Ready

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型配置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = modelPath,
                    onValueChange = { modelPath = it },
                    label = { Text("模型文件路径") },
                    placeholder = { Text("/data/local/tmp/model.litertlm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称 (可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                Text("推理后端", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiteRtBackendType.entries.forEach { type ->
                        FilledTonalButton(
                            onClick = { backend = type },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = type.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (backend == type) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Multimodal backends
                Text("多模态 (需模型支持)", style = MaterialTheme.typography.labelLarge)
                BackendSelector(
                    label = "视觉后端",
                    selected = visionBackend,
                    onSelect = { visionBackend = it },
                    enabled = !isLoading
                )
                BackendSelector(
                    label = "音频后端",
                    selected = audioBackend,
                    onSelect = { audioBackend = it },
                    enabled = !isLoading
                )

                Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f, enabled = !isLoading)

                Text("Top-K: $topK", style = MaterialTheme.typography.labelMedium)
                Slider(value = topK.toFloat(), onValueChange = { topK = it.toInt() }, valueRange = 1f..100f, enabled = !isLoading)

                Text("Top-P: ${"%.2f".format(topP)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = topP, onValueChange = { topP = it }, valueRange = 0f..1f, enabled = !isLoading)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isReady) {
                    TextButton(onClick = { onUnloadEngine(); onDismiss() }) { Text("卸载") }
                }
                Button(
                    onClick = {
                        onLoadEngine(
                            LiteRtModelConfig(
                                modelPath = modelPath.trim(),
                                displayName = displayName.trim(),
                                backend = backend,
                                visionBackend = visionBackend,
                                audioBackend = audioBackend,
                                temperature = temperature,
                                topK = topK,
                                topP = topP
                            )
                        )
                        onDismiss()
                    },
                    enabled = modelPath.isNotBlank() && !isLoading
                ) {
                    Text(if (isReady) "重新加载" else "加载模型")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun BackendSelector(
    label: String,
    selected: LiteRtBackendType?,
    onSelect: (LiteRtBackendType?) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val options = listOf<LiteRtBackendType?>(null) + LiteRtBackendType.entries
            options.forEach { type ->
                FilledTonalButton(
                    onClick = { onSelect(type) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = type?.name ?: "关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected == type) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UriImage(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    }
}
