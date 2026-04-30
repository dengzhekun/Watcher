package com.example.watcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.AudienceAction
import com.example.watcher.data.model.DanmakuItem
import com.example.watcher.data.model.GiftType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val audienceNameColors = listOf(
    Color(0xFF81D4FA),
    Color(0xFFFFAB91),
    Color(0xFFA5D6A7),
    Color(0xFFCE93D8),
    Color(0xFFFFE082),
    Color(0xFF80DEEA),
    Color(0xFFF48FB1),
    Color(0xFFE6EE9C),
    Color(0xFFB39DDB),
    Color(0xFFFFCC80),
)

internal fun audienceColor(name: String): Color {
    val index = (name.hashCode().and(0x7FFFFFFF)) % audienceNameColors.size
    return audienceNameColors[index]
}

@Composable
internal fun AiAudienceChatPanel(
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

private data class DanmakuLane(
    val item: DanmakuItem,
    val lane: Int,
    val startTime: Long
)

@Composable
internal fun DanmakuOverlay(
    danmakuFlow: SharedFlow<DanmakuItem>,
    onHighlight: (DanmakuItem) -> Unit = {}
) {
    val activeDanmaku = remember { mutableStateListOf<DanmakuLane>() }
    val seenIds = remember { mutableSetOf<Long>() }
    val laneCount = 5
    val durationMs = 10000L

    LaunchedEffect(Unit) {
        danmakuFlow.collect { item ->
            if (!seenIds.add(item.id)) return@collect

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

@Composable
internal fun AudienceQuickPanel(
    audiences: List<AiAudienceEntity>,
    onToggle: (AiAudienceEntity, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val enabledCount = audiences.count { it.enabled }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
            ) {
                val panelWidth = when {
                    maxWidth >= 1200.dp -> maxWidth * 0.56f
                    maxWidth >= 900.dp -> maxWidth * 0.66f
                    else -> maxWidth
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(panelWidth)
                        .widthIn(max = 920.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF131722).copy(alpha = 0.98f),
                    tonalElevation = 0.dp,
                    shadowElevation = 18.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "AI 观众面板",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (audiences.isEmpty()) {
                                        "当前还没有可用观众，去竖屏管理中心添加后再回来。"
                                    } else {
                                        "已启用 $enabledCount / ${audiences.size} 个观众。横屏下改为卡片网格，避免一排挤满。"
                                    },
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }

                            Text(
                                text = "关闭",
                                color = Color(0xFF8AB4FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(onClick = onDismiss)
                            )
                        }

                        if (audiences.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.04f),
                                        RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无观众，请在竖屏管理中心添加",
                                    color = Color.White.copy(alpha = 0.52f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 220.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                items(
                                    items = audiences,
                                    key = { it.id }
                                ) { audience ->
                                    AudienceQuickCard(
                                        audience = audience,
                                        onToggle = { enabled -> onToggle(audience, enabled) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudienceQuickCard(
    audience: AiAudienceEntity,
    onToggle: (Boolean) -> Unit
) {
    val accent = audienceColor(audience.name)
    val statusText = if (audience.enabled) "运行中" else "已暂停"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.34f)
            .background(
                if (audience.enabled) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.035f),
                RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.dp,
                color = if (audience.enabled) accent.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = audience.name,
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        color = if (audience.enabled) Color(0xFF81C784) else Color.White.copy(alpha = 0.42f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Switch(
                    checked = audience.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.18f)
                    )
                )
            }

            if (audience.persona.isNotBlank()) {
                Text(
                    text = audience.persona,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "这个观众还没有设定 persona。",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = audience.audienceType.label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp
        )
    }
}
