package com.example.watcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.watcher.WatcherXmaxImportStatus
import com.example.watcher.WatcherXmaxImportStatusSection
import com.example.watcher.ui.viewmodel.ApiWalletUiState

@Composable
internal fun WalletSummaryCard(uiState: ApiWalletUiState) {
    val defaultProvider = uiState.defaultProvider

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.Top
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
internal fun ExternalImportStatusCard(status: WatcherXmaxImportStatus) {
    val accent = if (status.hasImportedPayload) Color(0xFF18794E) else Color(0xFF9A6700)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "XMAX 外部导入状态",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = status.sourceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = status.lastImportedAt?.let { "最后导入 ${formatDateTime(it)}" }
                            ?: "尚未收到导入记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SummaryBadge(
                    text = if (status.hasImportedPayload) "已接收" else "待导入",
                    highlighted = status.hasImportedPayload
                )
            }

            Text(
                text = status.nextStepHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            status.sections.forEach { section ->
                ExternalImportSectionRow(section)
            }
        }
    }
}

@Composable
private fun ExternalImportSectionRow(section: WatcherXmaxImportStatusSection) {
    val label = when {
        !section.imported -> "未导入"
        section.enabled -> "已启用"
        else -> "已禁用"
    }
    val accent = when {
        !section.imported -> MaterialTheme.colorScheme.onSurfaceVariant
        section.enabled -> Color(0xFF18794E)
        else -> Color(0xFF9A6700)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = section.lastImportedAt?.let {
                        "来源 ${section.source} · ${formatDateTime(it)}"
                    } ?: "来源 ${section.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = section.nextStep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.12f)
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
}

@Composable
internal fun EmptyWalletCard(arkFallbackAvailable: Boolean) {
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
internal fun SectionTitle(
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
internal fun SummaryBadge(
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
