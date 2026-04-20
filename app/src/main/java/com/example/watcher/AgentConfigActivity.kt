package com.example.watcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.watcher.ui.components.AgentConfigScreen
import com.example.watcher.ui.theme.WatcherTheme
import com.example.watcher.ui.viewmodel.AgentConfigViewModel

class AgentConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatcherTheme {
                AgentConfigRoute(onClose = ::finish)
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, AgentConfigActivity::class.java)
        }
    }
}

@Composable
private fun AgentConfigRoute(
    onClose: () -> Unit,
    viewModel: AgentConfigViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    AgentConfigScreen(
        uiState = uiState,
        onBack = {
            viewModel.clearFeedback()
            onClose()
        },
        onRefresh = viewModel::refresh,
        onStartCreate = viewModel::startCreateAgent,
        onDuplicateSelected = viewModel::duplicateSelectedAgent,
        onSelectAgent = viewModel::selectAgent,
        onDraftChange = { draft -> viewModel.updateDraft { draft } },
        onSave = viewModel::saveDraft,
        onTestBrain = viewModel::testBrainConnection,
        onTestAgent = viewModel::testAgent,
        onTestContext = viewModel::testContext,
        onDeleteKnowledgeEntry = viewModel::deleteKnowledgeEntry,
        onReset = viewModel::resetDraft,
        onDelete = viewModel::deleteSelectedAgent
    )
}
