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
import com.example.watcher.importworkbench.ImportActionTarget
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
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    ImportWorkbenchScreen(
        uiState = uiState,
        onBack = onClose,
        onRefresh = viewModel::refresh,
        onToggleExpanded = viewModel::toggleExpanded,
        onPrimaryAction = { resourceId ->
            val target = viewModel.resolvePrimaryAction(resourceId)
            when {
                target == null -> viewModel.triggerSecondaryAction(resourceId)
                else -> {
                    val intent = target.toIntentOrNull(context)
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        viewModel.reportUnsupportedPrimaryAction(resourceId)
                    }
                }
            }
        },
        onSecondaryAction = viewModel::triggerSecondaryAction,
        onClearMessage = viewModel::clearMessage
    )
}

internal enum class ImportWorkbenchDestination {
    ApiWallet,
    AgentConfig,
    TemplateManagement
}

internal fun ImportActionTarget.resolveDestination(): ImportWorkbenchDestination? {
    return when (route) {
        "api_wallet" -> ImportWorkbenchDestination.ApiWallet
        "agent_config" -> ImportWorkbenchDestination.AgentConfig
        "template_management" -> ImportWorkbenchDestination.TemplateManagement
        else -> null
    }
}

private fun ImportActionTarget.toIntentOrNull(context: Context): Intent? {
    return when (resolveDestination()) {
        ImportWorkbenchDestination.ApiWallet -> ApiWalletActivity.createIntent(context)
        ImportWorkbenchDestination.AgentConfig -> AgentConfigActivity.createIntent(context)
        ImportWorkbenchDestination.TemplateManagement -> MainActivity.createIntent(
            context = context,
            startPage = MainActivity.StartPage.Templates
        )
        null -> null
    }
}
