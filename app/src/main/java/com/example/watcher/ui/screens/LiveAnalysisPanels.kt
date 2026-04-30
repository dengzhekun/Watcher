package com.example.watcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.CommentaryEntryStatus
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.LiveSpeechState

internal data class CommentaryTextScheme(
    val recording: String,
    val uploading: String,
    val processing: String,
    val analyzing: String,
    val streamingPlaceholder: String,
    val skippedFallback: String,
    val failedFallback: String
)

internal data class MemoryLabelScheme(
    val builderTag: String,
    val sceneLabel: String,
    val entitiesLabel: String,
    val actionLabel: String,
    val asksLabel: String,
    val askPrefix: String,
    val expertRequestsLabel: String? = null,
    val expertRequestPrefix: String = "> ",
    val memoryALabel: String,
    val memoryBLabel: String
)

internal data class TranscriptTextScheme(
    val listeningText: String,
    val waitingText: String,
    val mutedText: String? = null,
    val showMicIcon: Boolean = true
)

@Composable
internal fun CommentaryFeedPanel(
    entries: List<CommentaryEntry>,
    modifier: Modifier = Modifier,
    textScheme: CommentaryTextScheme
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.firstOrNull()?.segmentIndex) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.45f),
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = entries,
            key = { it.segmentIndex }
        ) { entry ->
            CommentaryEntryCardPanel(entry = entry, textScheme = textScheme)
        }
    }
}

@Composable
internal fun CommentaryEntryCardPanel(
    entry: CommentaryEntry,
    textScheme: CommentaryTextScheme
) {
    val displayText = when (entry.status) {
        CommentaryEntryStatus.Recording -> textScheme.recording
        CommentaryEntryStatus.Uploading -> textScheme.uploading
        CommentaryEntryStatus.Processing -> textScheme.processing
        CommentaryEntryStatus.Analyzing -> textScheme.analyzing
        CommentaryEntryStatus.Streaming -> entry.streamingText.ifBlank { textScheme.streamingPlaceholder }
        CommentaryEntryStatus.Completed -> entry.text
        CommentaryEntryStatus.Skipped -> entry.text.ifBlank { textScheme.skippedFallback }
        CommentaryEntryStatus.Failed -> entry.text.ifBlank { textScheme.failedFallback }
    }

    val isActive = entry.status in setOf(
        CommentaryEntryStatus.Recording,
        CommentaryEntryStatus.Uploading,
        CommentaryEntryStatus.Processing,
        CommentaryEntryStatus.Analyzing,
        CommentaryEntryStatus.Streaming
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when (entry.status) {
                    CommentaryEntryStatus.Failed -> Color.Red.copy(alpha = 0.15f)
                    CommentaryEntryStatus.Skipped -> Color(0xFFFFB300).copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.displayTimestamp,
                color = Color(0xFF4CAF50),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (entry.consumerId > 0) {
                Text(
                    text = "#${entry.consumerId}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFFFFA726), CircleShape)
                )
            }
        }

        Text(
            text = displayText,
            color = Color.White.copy(alpha = if (isActive) 0.7f else 0.95f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun MemoryStatusPanel(
    state: LiveCommentaryState,
    modifier: Modifier = Modifier,
    labels: MemoryLabelScheme
) {
    val hasAnything = state.memoryA.isNotBlank() ||
        state.latestMemoryB.isNotBlank() ||
        state.sceneMemory.isNotBlank() ||
        state.entityMemory.isNotBlank() ||
        state.pendingAsks.isNotEmpty() ||
        (labels.expertRequestsLabel != null && state.expertRequests.isNotEmpty())

    if (!hasAnything) return

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state.scenePhase.isNotBlank()) {
            item {
                MemoryTagPanel(labels.builderTag, state.scenePhase, Color(0xFF66BB6A))
            }
        }
        if (state.sceneMemory.isNotBlank()) {
            item { MemoryRowPanel(labels.sceneLabel, state.sceneMemory, Color(0xFF66BB6A), 3) }
        }
        if (state.entityMemory.isNotBlank()) {
            item { MemoryRowPanel(labels.entitiesLabel, state.entityMemory, Color(0xFF29B6F6), 4) }
        }
        if (state.actionSummary.isNotBlank()) {
            item { MemoryRowPanel(labels.actionLabel, state.actionSummary, Color(0xFFFFCA28), 2) }
        }
        if (state.pendingAsks.isNotEmpty()) {
            item {
                MemoryListSection(
                    label = labels.asksLabel,
                    color = Color(0xFFEF5350),
                    items = state.pendingAsks,
                    itemPrefix = labels.askPrefix
                )
            }
        }
        if (labels.expertRequestsLabel != null && state.expertRequests.isNotEmpty()) {
            item {
                MemoryListSection(
                    label = labels.expertRequestsLabel,
                    color = Color(0xFFAB47BC),
                    items = state.expertRequests,
                    itemPrefix = labels.expertRequestPrefix
                )
            }
        }
        if (state.memoryA.isNotBlank()) {
            item { MemoryRowPanel(labels.memoryALabel, state.memoryA, Color(0xFF42A5F5), 3) }
        }
        if (state.latestMemoryB.isNotBlank()) {
            item { MemoryRowPanel(labels.memoryBLabel, state.latestMemoryB, Color(0xFFFFA726), 2) }
        }
    }
}

@Composable
private fun MemoryTagPanel(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        Text(value, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

@Composable
private fun MemoryRowPanel(label: String, text: String, color: Color, maxLines: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryListSection(
    label: String,
    color: Color,
    items: List<String>,
    itemPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        items.forEach { item ->
            Text(
                text = "$itemPrefix$item",
                color = color.copy(alpha = 0.8f),
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SpeechTranscriptStripPanel(
    state: LiveSpeechState,
    modifier: Modifier = Modifier,
    textScheme: TranscriptTextScheme
) {
    if (!state.isActive) return

    val recent = state.transcripts.take(3)

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (textScheme.showMicIcon) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (textScheme.showMicIcon) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (state.isListening) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = state.errorMessage
                    ?: if (state.isListening) {
                        textScheme.listeningText
                    } else if (!state.isMicEnabled && textScheme.mutedText != null) {
                        textScheme.mutedText
                    } else {
                        textScheme.waitingText
                    },
                color = if (state.errorMessage != null) Color(0xFFEF5350) else Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }

        recent.forEach { entry ->
            Text(
                text = entry.text,
                color = Color.White.copy(alpha = if (entry.isFinal) 0.9f else 0.55f),
                fontSize = 13.sp,
                fontWeight = if (entry.isFinal) FontWeight.Normal else FontWeight.Light,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
