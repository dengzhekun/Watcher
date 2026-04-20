package com.example.watcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watcher.ui.screens.HubPage
import com.example.watcher.ui.theme.LocalWatcherExtendedColors
import kotlin.math.abs

@Composable
internal fun PageScaffold(
    page: HubPage,
    pageOffset: Float,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val focus = pageFocus(pageOffset)
    val accent = blendedPageAccent(selectionPosition(page, pageOffset))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.02f + focus * 0.06f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content
        )
    }
}

@Composable
internal fun BottomGlassScrim(
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalWatcherExtendedColors.current
    val surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val glassTint = extendedColors.glassOverlay.copy(alpha = 0.86f)
    val baseTint = extendedColors.surfaceContainerHigh.copy(alpha = 0.96f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(228.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glassTint.copy(alpha = 0.22f),
                            glassTint.copy(alpha = 0.48f),
                            surfaceTint.copy(alpha = 0.88f),
                            surfaceTint,
                            baseTint
                        )
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, 24.dp.toPx())
                )
            }
    )
}

@Composable
internal fun SharedWorkspaceHeader(
    modifier: Modifier = Modifier,
    pagerPosition: Float,
    onNavigate: (HubPage) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)
    ) {
        RouteSelectionStrip(
            pagerPosition = pagerPosition,
            onNavigate = onNavigate
        )
    }
}

@Composable
internal fun WatcherTopBar(
    eyebrow: String,
    title: String,
    subtitle: String,
    currentPage: HubPage,
    pageOffset: Float,
    onOpenSettings: () -> Unit,
    onOpenAgentConfig: (() -> Unit)? = null,
    onOpenWalletConfig: (() -> Unit)? = null
) {
    val extendedColors = LocalWatcherExtendedColors.current
    val accent = blendedPageAccent(selectionPosition(currentPage, pageOffset))

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
                text = eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onOpenWalletConfig != null) {
                Surface(
                    shape = CircleShape,
                    color = extendedColors.glassOverlay,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ) {
                    IconButton(onClick = onOpenWalletConfig) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Open API wallet",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (onOpenAgentConfig != null) {
                Surface(
                    shape = CircleShape,
                    color = extendedColors.glassOverlay,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ) {
                    IconButton(onClick = onOpenAgentConfig) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Open agent settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = extendedColors.glassOverlay,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwipeCoachmarkOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Swipe between workspaces",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Swipe left for realtime monitoring and swipe right for video analysis. Home keeps the latest task state in sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HubPage.entries.forEach { page ->
                    StatusPill(text = page.label, accent = pageAccent(page))
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(20.dp)) {
                    Text("Got it")
                }
            }
        }
    }
}

@Composable
private fun RouteSelectionStrip(
    pagerPosition: Float,
    onNavigate: (HubPage) -> Unit
) {
    val extendedColors = LocalWatcherExtendedColors.current
    val indicatorPosition = pagerPosition.coerceIn(0f, (HubPage.entries.size - 1).toFloat())
    val accent = blendedPageAccent(indicatorPosition)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = extendedColors.glassOverlay.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gap = 8.dp
                val slotWidth = (maxWidth - gap * (HubPage.entries.size - 1)) / HubPage.entries.size
                val offsetX = (slotWidth + gap) * indicatorPosition

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .offset(x = offsetX)
                            .width(slotWidth)
                            .height(44.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = accent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f))
                    ) {}

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        HubPage.entries.forEach { page ->
                            val emphasis = 1f - (abs(indicatorPosition - page.pageIndex).coerceIn(0f, 1f) * 0.55f)
                            val contentColor = lerp(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                accent,
                                emphasis
                            )

                            Box(
                                modifier = Modifier
                                    .width(slotWidth)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onNavigate(page) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = page.label,
                                    tint = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun pageAccent(page: HubPage): Color {
    return when (page) {
        HubPage.Monitor -> Color(0xFF0E8B65)
        HubPage.Hub -> Color(0xFF0058BE)
        HubPage.Analysis -> Color(0xFF9A5B00)
        HubPage.History -> Color(0xFF6A4CB0)
        HubPage.Templates -> Color(0xFF8B6914)
    }
}
