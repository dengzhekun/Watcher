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
import com.example.watcher.ui.screens.ImportWorkbenchScreen
import com.example.watcher.ui.theme.WatcherTheme
import com.example.watcher.ui.viewmodel.ImportWorkbenchViewModel

class ImportWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatcherTheme {
                ImportWorkbenchRoute(onClose = ::finish)
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ImportWorkbenchActivity::class.java)
        }
    }
}

@Composable
private fun ImportWorkbenchRoute(
    onClose: () -> Unit,
    viewModel: ImportWorkbenchViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    ImportWorkbenchScreen(
        uiState = uiState,
        onBack = onClose,
        onRefresh = viewModel::refresh,
        onToggleExpanded = viewModel::toggleExpanded,
        onPrimaryAction = viewModel::triggerPrimaryAction,
        onSecondaryAction = viewModel::triggerSecondaryAction,
        onClearMessage = viewModel::clearMessage
    )
}
