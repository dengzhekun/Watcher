package com.example.watcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.watcher.importworkbench.WorkbenchResourceState
import com.example.watcher.ui.viewmodel.ImportWorkbenchResource
import com.example.watcher.ui.viewmodel.ImportWorkbenchUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWorkbenchScreen(
    uiState: ImportWorkbenchUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onPrimaryAction: (String) -> Unit,
    onSecondaryAction: (String) -> Unit,
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
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WorkbenchSummary(uiState = uiState)
            }
            if (uiState.resources.isEmpty() && !uiState.isLoading) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Text(
                            text = "暂无可展示的导入资源。",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(uiState.resources, key = { it.id }) { resource ->
                    ImportResourceCard(
                        resource = resource,
                        isExpanded = resource.id in uiState.expandedResourceIds,
                        onToggleExpanded = { onToggleExpanded(resource.id) },
                        onPrimaryAction = { onPrimaryAction(resource.id) },
                        onSecondaryAction = { onSecondaryAction(resource.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkbenchSummary(uiState: ImportWorkbenchUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = uiState.title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = uiState.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge("Applied ${uiState.importedCount}", WorkbenchResourceState.APPLIED)
            StatusBadge("Pending ${uiState.pendingCount}", WorkbenchResourceState.NEEDS_MANUAL_ACTION)
            StatusBadge("Failed ${uiState.failedCount}", WorkbenchResourceState.FAILED)
        }
    }
}

@Composable
private fun ImportResourceCard(
    resource: ImportWorkbenchResource,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(resource.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${resource.typeLabel} · ${resource.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(resource.statusDisplayLabel, resource.status)
            }

            Text(
                text = resource.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resource.detailLines.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onToggleExpanded,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isExpanded) "收起详情" else "展开详情")
                    }
                }
                resource.primaryActionLabel?.let {
                    FilledTonalButton(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(it)
                    }
                }
                resource.secondaryActionLabel?.let {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(it)
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    resource.detailLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    status: WorkbenchResourceState
) {
    val (container, content) = when (status) {
        WorkbenchResourceState.APPLIED -> Color(0xFF18794E).copy(alpha = 0.12f) to Color(0xFF18794E)
        WorkbenchResourceState.RECEIVED,
        WorkbenchResourceState.NEEDS_MANUAL_ACTION -> Color(0xFF9A6700).copy(alpha = 0.12f) to Color(0xFF9A6700)
        WorkbenchResourceState.FAILED -> Color(0xFFB42318).copy(alpha = 0.12f) to Color(0xFFB42318)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content
        )
    }
}

private val ImportWorkbenchResource.statusDisplayLabel: String
    get() = when (status) {
        WorkbenchResourceState.APPLIED -> "已应用"
        WorkbenchResourceState.RECEIVED -> "已接收"
        WorkbenchResourceState.NEEDS_MANUAL_ACTION -> "待处理"
        WorkbenchResourceState.FAILED -> "失败"
    }
