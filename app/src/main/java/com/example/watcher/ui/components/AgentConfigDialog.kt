package com.example.watcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.ui.viewmodel.AgentConfigUiState
import com.example.watcher.ui.viewmodel.AgentEditorDraft
import com.example.watcher.ui.viewmodel.toAgentEditorDraft

internal enum class AgentConfigTab(val label: String) {
    Basics("基础"),
    Brain("大脑"),
    Runtime("运行时"),
    Records("记录")
}

@Composable
fun AgentConfigScreen(
    uiState: AgentConfigUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartCreate: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onDraftChange: (AgentEditorDraft) -> Unit,
    onSave: () -> Unit,
    onTestBrain: () -> Unit,
    onTestAgent: () -> Unit,
    onTestContext: () -> Unit,
    onDeleteKnowledgeEntry: (String) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onBack)

    val baselineDraft = remember(uiState.isCreatingNew, uiState.detail) {
        if (uiState.isCreatingNew) {
            AgentEditorDraft()
        } else {
            uiState.detail?.profile?.toAgentEditorDraft() ?: AgentEditorDraft()
        }
    }
    val isDirty = uiState.draft != baselineDraft
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = AgentConfigTab.entries[selectedTabIndex]

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AgentConfigTopBar(
                agentCount = uiState.agentCount,
                isCreatingNew = uiState.isCreatingNew,
                selectedName = uiState.detail?.profile?.definition?.name,
                onRefresh = onRefresh,
                onBack = onBack
            )
            uiState.statusMessage?.let { message ->
                StatusBanner(text = message, isError = false)
            }
            uiState.errorMessage?.let { message ->
                StatusBanner(text = message, isError = true)
            }
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PanelSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CompactAgentSwitcher(
                            modifier = Modifier.fillMaxWidth(),
                            uiState = uiState,
                            onStartCreate = onStartCreate,
                            onDuplicateSelected = onDuplicateSelected,
                            onSelectAgent = onSelectAgent
                        )
                        CompactActionBar(
                            isCreatingNew = uiState.isCreatingNew,
                            isDirty = isDirty,
                            isSaving = uiState.isSaving,
                            isDeleting = uiState.isDeleting,
                            isTestingBrain = uiState.isTestingBrain,
                            isTestingAgent = uiState.isTestingAgent,
                            isTestingContext = uiState.isTestingContext,
                            onSave = onSave,
                            onTestBrain = onTestBrain,
                            onTestAgent = onTestAgent,
                            onTestContext = onTestContext,
                            onReset = onReset,
                            onDelete = onDelete
                        )
                    }
                }
                PanelSurface(modifier = Modifier.fillMaxWidth()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 18.dp
                    ) {
                        AgentConfigTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(tab.label) }
                            )
                        }
                    }
                }
                when (selectedTab) {
                    AgentConfigTab.Basics -> BasicsContent(uiState.draft, onDraftChange)
                    AgentConfigTab.Brain -> BrainContent(uiState, onDraftChange)
                    AgentConfigTab.Runtime -> RuntimeContent(uiState.draft, onDraftChange)
                    AgentConfigTab.Records -> RecordsContent(
                        uiState = uiState,
                        onDeleteKnowledgeEntry = onDeleteKnowledgeEntry
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentConfigTopBar(
    agentCount: Int,
    isCreatingNew: Boolean,
    selectedName: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("返回")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Agent 配置", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    isCreatingNew -> "新建草稿"
                    selectedName != null -> selectedName
                    else -> "$agentCount 个 Agent"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("刷新")
        }
    }
}
