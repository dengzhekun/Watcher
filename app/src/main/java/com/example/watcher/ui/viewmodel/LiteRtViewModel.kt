package com.example.watcher.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watcher.data.local.litert.DownloadProgress
import com.example.watcher.data.local.litert.LiteRtConfigStore
import com.example.watcher.data.local.litert.LiteRtEngineManager
import com.example.watcher.data.local.litert.LiteRtEngineStatus
import com.example.watcher.data.local.litert.LiteRtLlmProvider
import com.example.watcher.data.local.litert.LiteRtModelConfig
import com.example.watcher.data.local.litert.LiteRtModelDownloader
import com.example.watcher.data.local.litert.LiteRtModelLocator
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.watcherApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatEntry(
    val role: String,
    val content: String,
    val imageUri: String? = null,
    val imageFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class LiteRtViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.watcherApplication().agentFrameworkContainer
    private val engineManager: LiteRtEngineManager = container.liteRtEngineManager
    private val configStore: LiteRtConfigStore = container.liteRtConfigStore
    private val provider: LiteRtLlmProvider = container.liteRtProvider
    private val downloader: LiteRtModelDownloader = container.liteRtModelDownloader
    private val modelLocator: LiteRtModelLocator = container.liteRtModelLocator

    val engineStatus: StateFlow<LiteRtEngineStatus> = engineManager.status

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    init {
        // Collect download progress from downloader
        viewModelScope.launch {
            downloader.progress.collect { _downloadProgress.value = it }
        }
    }

    fun isModelDownloaded(): Boolean = modelLocator.hasAvailableModel(_savedConfig.value?.modelPath)

    fun downloadModel() {
        viewModelScope.launch {
            val result = downloader.downloadModel()
            result.onSuccess { modelPath ->
                val config = modelLocator.defaultConfig(modelPath)
                configStore.saveConfig(config)
                _savedConfig.value = config
                engineManager.initialize(config)
            }
        }
    }

    private val _savedConfig = MutableStateFlow(resolveSavedConfig())
    val savedConfig: StateFlow<LiteRtModelConfig?> = _savedConfig.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatEntry>>(emptyList())
    val chatHistory: StateFlow<List<ChatEntry>> = _chatHistory.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

    fun loadEngine(config: LiteRtModelConfig) {
        viewModelScope.launch {
            val resolvedConfig = modelLocator.resolveConfig(config) ?: config
            configStore.saveConfig(resolvedConfig)
            _savedConfig.value = resolvedConfig
            engineManager.initialize(resolvedConfig)
        }
    }

    fun scanExistingModel() {
        viewModelScope.launch {
            val storedConfig = configStore.loadConfig()
            val resolvedConfig = modelLocator.resolveConfig(storedConfig)
            if (resolvedConfig != null) {
                configStore.saveConfig(resolvedConfig)
                _savedConfig.value = resolvedConfig
            } else {
                _savedConfig.value = storedConfig
            }
        }
    }

    fun unloadEngine() {
        viewModelScope.launch {
            engineManager.close()
            configStore.clearConfig()
            _savedConfig.value = null
        }
    }

    fun attachImage(uri: Uri) {
        _pendingImageUri.value = uri
    }

    fun clearAttachment() {
        _pendingImageUri.value = null
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _generating.value) return
        val imageUri = _pendingImageUri.value
        // Save image as temp JPEG file immediately
        val imageFilePath = imageUri?.let { saveImageToTempFile(it) }

        val userEntry = ChatEntry(
            role = "user",
            content = text,
            imageUri = imageUri?.toString(),       // content:// URI for display
            imageFilePath = imageFilePath           // file path for LiteRT-LM
        )
        _chatHistory.value = _chatHistory.value + userEntry
        _pendingImageUri.value = null

        viewModelScope.launch {
            _generating.value = true
            val assistantEntry = ChatEntry(role = "assistant", content = "")
            _chatHistory.value = _chatHistory.value + assistantEntry

            try {
                val messages = _chatHistory.value
                    .filter { (it.role == "user" || it.role == "assistant") && it.content.isNotEmpty() }
                    .map { ChatMessage(role = it.role, content = it.content) }

                var accumulated = ""
                provider.chatStream(
                    systemPrompt = "You are a helpful assistant. Reply concisely in the user's language.",
                    messages = messages,
                    imageFilePath = imageFilePath
                ).collect { chunk ->
                    accumulated += chunk
                    val history = _chatHistory.value.toMutableList()
                    history[history.lastIndex] = history.last().copy(content = accumulated)
                    _chatHistory.value = history
                }

                if (accumulated.isBlank()) {
                    val history = _chatHistory.value.toMutableList()
                    history[history.lastIndex] = history.last().copy(content = "(空响应)")
                    _chatHistory.value = history
                }
            } catch (e: Exception) {
                val history = _chatHistory.value.toMutableList()
                history[history.lastIndex] = history.last().copy(content = "Error: ${e.message}")
                _chatHistory.value = history
            } finally {
                _generating.value = false
            }
        }
    }

    fun clearChat() {
        _chatHistory.value = emptyList()
    }

    private fun saveImageToTempFile(uri: Uri): String? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val tempFile = java.io.File(context.cacheDir, "litert_img_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            tempFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveSavedConfig(): LiteRtModelConfig? {
        val storedConfig = configStore.loadConfig()
        val resolvedConfig = modelLocator.resolveConfig(storedConfig)
        return if (resolvedConfig != null) {
            if (resolvedConfig != storedConfig) {
                configStore.saveConfig(resolvedConfig)
            }
            resolvedConfig
        } else {
            storedConfig
        }
    }
}
