package com.example.watcher.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watcher.R
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AiAudienceLiveState
import com.example.watcher.data.model.AudienceAction
import com.example.watcher.data.model.GiftType
import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.LiveSpeechState
import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.CommentaryEntryStatus
import com.example.watcher.data.model.DanmakuItem
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.VideoStreamSettings
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.watcher.ui.components.ConnectionStatus
import com.example.watcher.ui.components.MjpegStreamUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LiveRoomScreen(
    streamState: MjpegStreamUiState,
    isPlaying: Boolean,
    settings: VideoStreamSettings,
    commentaryState: LiveCommentaryState,
    aiAudienceState: AiAudienceLiveState,
    danmakuFlow: SharedFlow<DanmakuItem>,
    speechState: LiveSpeechState,
    audiences: List<AiAudienceEntity>,
    onPlayingChange: (Boolean) -> Unit,
    onMicToggle: (Boolean) -> Unit,
    onResetLiveRoom: () -> Unit,
    onReconnectStream: () -> Unit,
    onCaptureSnapshot: (Bitmap) -> Unit,
    onSaveAudience: (AiAudienceEntity) -> Unit,
    onExitLiveRoom: () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAudienceConfig by remember { mutableStateOf(false) }
    var rightPanelExpanded by remember { mutableStateOf(true) }

    // Highlight message (醒目留言) state
    var highlightMsg by remember { mutableStateOf<DanmakuItem?>(null) }
    // NOTE: danmaku collection is done in DanmakuOverlay to avoid duplicate collectors

    LaunchedEffect(lastInteraction) {
        delay(4000)
        showOverlay = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    showOverlay = !showOverlay
                    lastInteraction = System.currentTimeMillis()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Video layer
        val frame = streamState.currentFrame
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = stringResource(R.string.stream_live_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = when (streamState.connectionStatus) {
                        ConnectionStatus.Connecting -> stringResource(R.string.stream_status_connecting)
                        is ConnectionStatus.Error -> (streamState.connectionStatus as ConnectionStatus.Error).message
                        else -> stringResource(R.string.live_room_no_signal)
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }

        // Leaderboard (top right)
        if (aiAudienceState.likeBoard.isNotEmpty() || aiAudienceState.giftBoard.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Like board
                if (aiAudienceState.likeBoard.isNotEmpty()) {
                    Text("❤️ 点赞榜", color = Color(0xFFFF6B6B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    aiAudienceState.likeBoard.forEachIndexed { i, e ->
                        val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "" }
                        Text(
                            "$medal${e.audienceName} ×${e.count}",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp
                        )
                    }
                    val liker = aiAudienceState.lastLiker
                    if (liker != null) {
                        Text("最新: $liker ❤️", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    }
                }
                // Gift board
                if (aiAudienceState.giftBoard.isNotEmpty()) {
                    Text("🎁 礼物榜", color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    aiAudienceState.giftBoard.forEachIndexed { i, e ->
                        val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "" }
                        Text(
                            "$medal${e.audienceName} ${e.totalSpent}币",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp
                        )
                    }
                    for (g in aiAudienceState.recentGifts.reversed()) {
                        Text(
                            "${g.gift.emoji} ${g.audienceName}",
                            color = Color(0xFFFFD700).copy(alpha = 0.7f), fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // Highlight message (醒目留言) — pinned center with countdown
        val currentHighlight = highlightMsg
        if (currentHighlight != null) {
            val nameColor = audienceColor(currentHighlight.audienceName)
            var remainSec by remember(currentHighlight.id) { mutableIntStateOf(30) }
            LaunchedEffect(currentHighlight.id) {
                while (remainSec > 0) {
                    delay(1000)
                    remainSec--
                }
                highlightMsg = null
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp)
            ) {
                // Glow border
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(1.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFFF6B35),
                                    Color(0xFFFFD700)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
                // Inner content
                Column(
                    modifier = Modifier
                        .padding(2.dp)
                        .background(
                            Color(0xFF0D0D1A).copy(alpha = 0.95f),
                            RoundedCornerShape(15.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: icon + label + countdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📢", fontSize = 14.sp)
                        Text(
                            text = "醒目留言",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${remainSec}s",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Content
                    Text(
                        text = currentHighlight.content,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    // Sender
                    Text(
                        text = "—— ${currentHighlight.audienceName}",
                        color = nameColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Danmaku overlay (floating text across video)
        DanmakuOverlay(
            danmakuFlow = danmakuFlow,
            onHighlight = { highlightMsg = it }
        )

        // Left panel: AI audience chat
        if (aiAudienceState.messages.isNotEmpty()) {
            AiAudienceChatPanel(
                messages = aiAudienceState.messages,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(280.dp)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )
        }

        // Right panel: memory status + commentary feed (collapsible)
        if (commentaryState.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                if (rightPanelExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(320.dp)
                            .padding(end = 8.dp)
                    ) {
                        MemoryStatusBar(
                            state = commentaryState,
                            modifier = Modifier.weight(0.45f)
                        )
                        if (commentaryState.entries.isNotEmpty()) {
                            CommentaryFeed(
                                entries = commentaryState.entries,
                                modifier = Modifier.weight(0.55f)
                            )
                        }
                    }
                }
                // Toggle button
                FilledTonalIconButton(
                    onClick = { rightPanelExpanded = !rightPanelExpanded },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = if (rightPanelExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                        contentDescription = if (rightPanelExpanded) "收起" else "展开",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Overlay controls
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (statusText, statusColor) = when (streamState.connectionStatus) {
                        ConnectionStatus.Connected -> stringResource(R.string.stream_connection_connected) to Color(0xFF4CAF50)
                        ConnectionStatus.Connecting -> stringResource(R.string.stream_connection_connecting) to Color(0xFFFFA726)
                        is ConnectionStatus.Error -> stringResource(R.string.stream_connection_error) to Color(0xFFEF5350)
                        ConnectionStatus.Disconnected -> stringResource(R.string.stream_connection_disconnected) to Color.White.copy(alpha = 0.5f)
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )

                    if (streamState.connectionStatus is ConnectionStatus.Connected) {
                        Text(
                            text = stringResource(R.string.stream_fps, streamState.fps),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // LIVE badge (top right)
                if (streamState.connectionStatus is ConnectionStatus.Connected) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(
                                Color(0xCCE53935),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            text = stringResource(R.string.live_room_live_badge),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            streamState.currentFrame?.let(onCaptureSnapshot)
                            lastInteraction = System.currentTimeMillis()
                        },
                        enabled = streamState.currentFrame != null,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.stream_capture_snapshot),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            onPlayingChange(!isPlaying)
                            lastInteraction = System.currentTimeMillis()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.stream_stop) else stringResource(R.string.stream_start),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (!isPlaying || streamState.connectionStatus is ConnectionStatus.Error || streamState.connectionStatus is ConnectionStatus.Disconnected) {
                        FilledTonalIconButton(
                            onClick = {
                                onReconnectStream()
                                lastInteraction = System.currentTimeMillis()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.live_room_reconnect),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Mic toggle button
                    FilledTonalIconButton(
                        onClick = {
                            onMicToggle(!speechState.isMicEnabled)
                            lastInteraction = System.currentTimeMillis()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (speechState.isMicEnabled && speechState.isActive)
                                Color(0xFF66BB6A).copy(alpha = 0.3f)
                            else
                                Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (speechState.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "语音识别开关",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset live room button
                    FilledTonalIconButton(
                        onClick = {
                            onResetLiveRoom()
                            lastInteraction = System.currentTimeMillis()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFEF5350).copy(alpha = 0.25f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "重置直播间",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // AI Audience config button
                    FilledTonalIconButton(
                        onClick = {
                            showAudienceConfig = true
                            lastInteraction = System.currentTimeMillis()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF42A5F5).copy(alpha = 0.3f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "AI 观众配置",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Exit live room button
                    FilledTonalIconButton(
                        onClick = { onExitLiveRoom() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFEF5350).copy(alpha = 0.4f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "退出直播",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Stream URL (bottom left)
                val displayUrl = streamState.activeStreamUrl ?: settings.streamUrl
                if (displayUrl.isNotBlank()) {
                    Text(
                        text = displayUrl,
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 28.dp)
                    )
                }
            }
        }

        // Speech transcript strip (center-bottom, prominent)
        SpeechTranscriptStrip(
            state = speechState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.6f)
                .padding(bottom = 76.dp)
        )

        // AI Audience quick panel (lightweight, landscape-friendly)
        if (showAudienceConfig) {
            AudienceQuickPanel(
                audiences = audiences,
                onToggle = { audience, enabled ->
                    onSaveAudience(audience.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
                },
                onDismiss = { showAudienceConfig = false }
            )
        }
    }
}

// --- Commentary Feed ---

@Composable
private fun CommentaryFeed(
    entries: List<CommentaryEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to top when new entries arrive
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
            CommentaryEntryCard(entry)
        }
    }
}

@Composable
private fun CommentaryEntryCard(entry: CommentaryEntry) {
    val displayText = when (entry.status) {
        CommentaryEntryStatus.Recording -> "录制中…"
        CommentaryEntryStatus.Uploading -> "上传中…"
        CommentaryEntryStatus.Processing -> "处理中…"
        CommentaryEntryStatus.Analyzing -> "分析中…"
        CommentaryEntryStatus.Streaming -> entry.streamingText.ifBlank { "…" }
        CommentaryEntryStatus.Completed -> entry.text
        CommentaryEntryStatus.Skipped -> entry.text.ifBlank { "已跳过（积压保护）" }
        CommentaryEntryStatus.Failed -> entry.text.ifBlank { "分析失败" }
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
                        .clip(CircleShape)
                        .background(Color(0xFFFFA726))
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
private fun MemoryStatusBar(state: LiveCommentaryState, modifier: Modifier = Modifier) {
    val hasAnything = state.memoryA.isNotBlank() || state.latestMemoryB.isNotBlank() ||
            state.sceneMemory.isNotBlank() || state.entityMemory.isNotBlank()

    if (!hasAnything) return

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Scene memory (three-layer)
        if (state.scenePhase.isNotBlank()) {
            item {
                MemoryTag("建设者", state.scenePhase, Color(0xFF66BB6A))
            }
        }
        if (state.sceneMemory.isNotBlank()) {
            item { MemoryRow("场景", state.sceneMemory, Color(0xFF66BB6A), 3) }
        }
        if (state.entityMemory.isNotBlank()) {
            item { MemoryRow("实体", state.entityMemory, Color(0xFF29B6F6), 4) }
        }
        if (state.actionSummary.isNotBlank()) {
            item { MemoryRow("动态", state.actionSummary, Color(0xFFFFCA28), 2) }
        }
        if (state.pendingAsks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ASK", color = Color(0xFFEF5350), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFFEF5350).copy(alpha = 0.2f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                    state.pendingAsks.forEach {
                        Text("? $it", color = Color(0xFFEF5350).copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // General memory (memoryA/B)
        if (state.memoryA.isNotBlank()) {
            item { MemoryRow("记忆A", state.memoryA, Color(0xFF42A5F5), 3) }
        }
        if (state.latestMemoryB.isNotBlank()) {
            item { MemoryRow("记忆B", state.latestMemoryB, Color(0xFFFFA726), 2) }
        }
    }
}

@Composable
private fun MemoryTag(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
        Text(value, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

@Composable
private fun MemoryRow(label: String, text: String, color: Color, maxLines: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
        Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp, lineHeight = 12.sp,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

// --- Speech Transcript Strip ---

@Composable
private fun SpeechTranscriptStrip(
    state: LiveSpeechState,
    modifier: Modifier = Modifier
) {
    if (!state.isActive) return

    val recent = state.transcripts.take(3)

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Listening indicator + error
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = if (state.isListening) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = state.errorMessage
                    ?: if (state.isListening) "🎤 聆听中…"
                    else if (!state.isMicEnabled) "🎤 已静音"
                    else "🎤 等待语音…",
                color = if (state.errorMessage != null) Color(0xFFEF5350) else Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        for (entry in recent) {
            Text(
                text = entry.text,
                color = Color.White.copy(alpha = if (entry.isFinal) 0.9f else 0.5f),
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

// --- Audience name color palette (bright, no dark colors) ---

private val audienceNameColors = listOf(
    Color(0xFF81D4FA), // light blue
    Color(0xFFFFAB91), // salmon
    Color(0xFFA5D6A7), // light green
    Color(0xFFCE93D8), // lavender
    Color(0xFFFFE082), // gold
    Color(0xFF80DEEA), // cyan
    Color(0xFFF48FB1), // pink
    Color(0xFFE6EE9C), // lime
    Color(0xFFB39DDB), // light purple
    Color(0xFFFFCC80), // peach
)

private fun audienceColor(name: String): Color {
    val index = (name.hashCode().and(0x7FFFFFFF)) % audienceNameColors.size
    return audienceNameColors[index]
}

// --- AI Audience Chat Panel (left side) ---

@Composable
private fun AiAudienceChatPanel(
    messages: List<AiAudienceMessageEntity>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(messages.firstOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items = messages, key = { it.id }) { msg ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg.audienceName,
                        color = audienceColor(msg.audienceName),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeFormat.format(Date(msg.timestamp)),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = msg.content,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Danmaku Overlay ---

private data class DanmakuLane(
    val item: DanmakuItem,
    val lane: Int,
    val startTime: Long
)

@Composable
private fun DanmakuOverlay(
    danmakuFlow: SharedFlow<DanmakuItem>,
    onHighlight: (DanmakuItem) -> Unit = {}
) {
    val activeDanmaku = remember { mutableStateListOf<DanmakuLane>() }
    val seenIds = remember { mutableSetOf<Long>() }
    val laneCount = 5
    val durationMs = 10000L

    // Single collector for all danmaku — handles display + highlight detection
    LaunchedEffect(Unit) {
        danmakuFlow.collect { item ->
            // ID-based dedup: skip if already displayed
            if (!seenIds.add(item.id)) return@collect

            // Detect highlight gift
            if (item.action is AudienceAction.Gift &&
                (item.action as AudienceAction.Gift).gift == GiftType.HIGHLIGHT
            ) {
                onHighlight(item)
            }

            val now = System.currentTimeMillis()
            val usedLanes = activeDanmaku
                .filter { now - it.startTime < durationMs / 2 }
                .map { it.lane }
                .toSet()
            val lane = (0 until laneCount).firstOrNull { it !in usedLanes }
                ?: (0 until laneCount).random()
            activeDanmaku.add(DanmakuLane(item, lane, now))
            activeDanmaku.removeAll { now - it.startTime > durationMs + 1000 }

            // Prevent seenIds from growing forever
            if (seenIds.size > 200) {
                val idsToKeep = activeDanmaku.map { it.item.id }.toSet()
                seenIds.retainAll(idsToKeep)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val now = System.currentTimeMillis()
            activeDanmaku.removeAll { now - it.startTime > durationMs + 1000 }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val laneHeightDp = maxHeight / laneCount

        for (danmaku in activeDanmaku.toList()) {
            DanmakuText(
                audienceName = danmaku.item.audienceName,
                content = danmaku.item.content,
                action = danmaku.item.action,
                lane = danmaku.lane,
                laneHeightDp = laneHeightDp,
                totalWidthPx = widthPx,
                durationMs = durationMs,
                startTime = danmaku.startTime,
                itemKey = danmaku.item.id
            )
        }
    }
}

@Composable
private fun DanmakuText(
    audienceName: String,
    content: String,
    action: AudienceAction,
    lane: Int,
    laneHeightDp: Dp,
    totalWidthPx: Float,
    durationMs: Long,
    startTime: Long,
    itemKey: Long
) {
    var offsetX by remember(itemKey) { mutableStateOf(totalWidthPx) }

    LaunchedEffect(itemKey) {
        val totalDistance = totalWidthPx * 2
        val stepMs = 16L
        var elapsed = System.currentTimeMillis() - startTime

        while (elapsed < durationMs) {
            val progress = elapsed.toFloat() / durationMs
            offsetX = totalWidthPx - (totalDistance * progress)
            delay(stepMs)
            elapsed = System.currentTimeMillis() - startTime
        }
        offsetX = -totalWidthPx
    }

    val nameColor = audienceColor(audienceName)
    val actionPrefix = when (action) {
        is AudienceAction.Like -> "❤️ "
        is AudienceAction.Gift -> "${action.gift.emoji} "
        else -> ""
    }
    val isGift = action is AudienceAction.Gift
    val textColor = if (isGift) Color(0xFFFFD700) else Color.White.copy(alpha = 0.85f)
    val fontSize = if (isGift) 16.sp else 14.sp

    val annotatedText = buildAnnotatedString {
        if (actionPrefix.isNotEmpty()) append(actionPrefix)
        withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
            append(audienceName)
        }
        append(": ")
        withStyle(SpanStyle(color = textColor)) {
            append(content)
        }
    }

    Text(
        text = annotatedText,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), 0) }
            .padding(top = laneHeightDp * lane)
    )
}

// --- Audience Quick Panel (landscape-friendly) ---

@Composable
private fun AudienceQuickPanel(
    audiences: List<AiAudienceEntity>,
    onToggle: (AiAudienceEntity, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .background(Color(0xFF1E1E2E).copy(alpha = 0.95f), RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "👥",
                fontSize = 18.sp
            )

            if (audiences.isEmpty()) {
                Text(
                    "暂无观众，请在竖屏管理中心添加",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            } else {
                audiences.forEach { audience ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = if (audience.enabled) 0.08f else 0.03f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = audienceColor(audience.name).let { color ->
                                audience.name
                            },
                            color = audienceColor(audience.name),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${audience.audienceType.label} · ${audience.heartbeatIntervalSeconds}s",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                        Switch(
                            checked = audience.enabled,
                            onCheckedChange = { onToggle(audience, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Color(0xFF42A5F5)
                            ),
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }
        }
    }
}
