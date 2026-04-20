package com.example.watcher.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.watcher.data.model.BlackboardDay
import com.example.watcher.data.model.BlackboardEntry
import com.example.watcher.ui.components.StatusPill
import com.example.watcher.ui.components.WatcherCard

@Composable
internal fun DlcBlackboardCard(
    days: List<BlackboardDay>,
    selectedDayEntries: List<BlackboardEntry>,
    onLoadDayEntries: (String) -> Unit
) {
    var expandedDate by remember { mutableStateOf<String?>(null) }

    WatcherCard {
        Text(
            text = "Blackboard 历史归档",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "这里只展示已经落库的历史快照，不承担当前会话里的信息流转职责。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "按天归档 — 点击日期查看详情",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (days.isEmpty()) {
            Text(
                text = "暂无历史记录。解说完成后将自动按天归档。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(days, key = { it.date }) { day ->
                        val isExpanded = expandedDate == day.date
                        DlcBlackboardDayRow(
                            day = day,
                            isExpanded = isExpanded,
                            entries = if (isExpanded) selectedDayEntries else emptyList(),
                            onClick = {
                                if (isExpanded) {
                                    expandedDate = null
                                } else {
                                    expandedDate = day.date
                                    onLoadDayEntries(day.date)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DlcBlackboardDayRow(
    day: BlackboardDay,
    isExpanded: Boolean,
    entries: List<BlackboardEntry>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = day.date,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            StatusPill(
                text = "${day.totalEntries} 条",
                accent = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = if (isExpanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (day.coreMemoryA.isNotBlank() && !isExpanded) {
            Text(
                text = day.coreMemoryA,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        if (isExpanded) {
            Spacer(Modifier.height(6.dp))
            if (day.sceneMemory.isNotBlank()) {
                ArchiveMemorySection(label = "场景", content = day.sceneMemory, accent = MaterialTheme.colorScheme.tertiary)
            }
            if (day.entityMemory.isNotBlank()) {
                ArchiveMemorySection(label = "实体", content = day.entityMemory, accent = MaterialTheme.colorScheme.primary)
            }
            if (day.coreMemoryA.isNotBlank()) {
                ArchiveMemorySection(label = "核心记忆", content = day.coreMemoryA, accent = MaterialTheme.colorScheme.error)
            }

            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "原始观察条目 (${entries.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "#${entry.segmentIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = entry.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ArchiveMemorySection(
    label: String,
    content: String,
    accent: androidx.compose.ui.graphics.Color
) {
    if (content.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
