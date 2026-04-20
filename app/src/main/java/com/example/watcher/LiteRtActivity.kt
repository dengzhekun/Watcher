package com.example.watcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.watcher.data.local.litert.DownloadProgress
import com.example.watcher.ui.screens.LiteRtScreen
import com.example.watcher.ui.theme.WatcherTheme
import com.example.watcher.ui.viewmodel.LiteRtViewModel

class LiteRtActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatcherTheme {
                LiteRtRoute(onClose = ::finish)
            }
        }
    }

    override fun finish() {
        setResult(Activity.RESULT_OK)
        super.finish()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LiteRtActivity::class.java)
        }
    }
}

@Composable
private fun LiteRtRoute(onClose: () -> Unit) {
    val viewModel: LiteRtViewModel = viewModel()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val savedConfig by viewModel.savedConfig.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val pendingImageUri by viewModel.pendingImageUri.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    LiteRtScreen(
        engineStatus = engineStatus,
        savedConfig = savedConfig,
        chatHistory = chatHistory,
        generating = generating,
        pendingImageUri = pendingImageUri,
        downloadProgress = downloadProgress,
        isModelDownloaded = viewModel.isModelDownloaded(),
        onLoadEngine = viewModel::loadEngine,
        onUnloadEngine = viewModel::unloadEngine,
        onSendMessage = viewModel::sendMessage,
        onAttachImage = viewModel::attachImage,
        onClearAttachment = viewModel::clearAttachment,
        onClearChat = viewModel::clearChat,
        onDownloadModel = viewModel::downloadModel,
        onClose = onClose
    )
}
