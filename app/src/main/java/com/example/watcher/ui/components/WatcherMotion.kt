package com.example.watcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.watcher.ui.screens.HubPage
import kotlin.math.abs
import kotlin.math.max

internal enum class MotionDepth(
    val horizontalTravel: Dp,
    val verticalTravel: Dp,
    val minScale: Float,
    val minAlpha: Float,
    val settleDelayMillis: Int
) {
    Header(10.dp, 6.dp, 0.988f, 0.93f, 0),
    Hero(14.dp, 8.dp, 0.992f, 0.95f, 40),
    Support(20.dp, 12.dp, 0.98f, 0.9f, 90),
    Focus(28.dp, 16.dp, 0.968f, 0.84f, 140),
    Footer(16.dp, 10.dp, 0.984f, 0.92f, 180)
}

internal fun calculatePagerPosition(currentPage: Int, currentPageOffsetFraction: Float): Float {
    val maxPage = (HubPage.entries.size - 1).coerceAtLeast(0)
    return (currentPage + currentPageOffsetFraction).coerceIn(0f, maxPage.toFloat())
}

internal fun calculatePageOffset(pagerPosition: Float, page: Int): Float {
    return pagerPosition - page
}

internal fun pageFocus(pageOffset: Float): Float {
    return 1f - abs(pageOffset).coerceIn(0f, 1f)
}

internal fun blendedPageAccent(position: Float): Color {
    val maxPage = (HubPage.entries.size - 1).coerceAtLeast(0)
    val clamped = position.coerceIn(0f, maxPage.toFloat())
    val startIndex = clamped.toInt()
    val endIndex = (startIndex + 1).coerceAtMost(maxPage)
    val fraction = clamped - startIndex
    return lerp(
        pageAccent(HubPage.fromPage(startIndex)),
        pageAccent(HubPage.fromPage(endIndex)),
        fraction
    )
}

internal fun selectionPosition(currentPage: HubPage, pageOffset: Float): Float {
    return (currentPage.pageIndex + pageOffset)
        .coerceIn(0f, (HubPage.entries.size - 1).toFloat())
}

@Composable
internal fun WorkspaceBackdrop(
    pagerPosition: Float,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.background
    val primaryGlow = blendedPageAccent(pagerPosition)
    val secondaryGlow = blendedPageAccent((pagerPosition + 0.72f).coerceAtMost((HubPage.entries.size - 1).toFloat()))

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val width = size.width
                val height = size.height
                val radius = max(width, height)
                val normalized = if (HubPage.entries.size <= 1) {
                    0.5f
                } else {
                    pagerPosition / (HubPage.entries.size - 1).toFloat()
                }

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.18f),
                            secondaryGlow.copy(alpha = 0.1f),
                            background,
                            background
                        )
                    )
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = width * (0.2f + normalized * 0.55f),
                            y = height * 0.16f
                        ),
                        radius = radius * 0.72f
                    )
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryGlow.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = width * (0.82f - normalized * 0.24f),
                            y = height * 0.38f
                        ),
                        radius = radius * 0.64f
                    )
                )
            }
    )
}

@Composable
internal fun MotionStageSection(
    pageOffset: Float,
    depth: MotionDepth,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val focus = pageFocus(pageOffset)
    val settleScaleProgress by animateFloatAsState(
        targetValue = if (focus > 0.985f) 1f else 0f,
        animationSpec = tween(
            durationMillis = 220,
            delayMillis = depth.settleDelayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "page-settle-scale"
    )
    val density = LocalDensity.current
    val horizontalTravel = with(density) { depth.horizontalTravel.toPx() }
    val verticalTravel = with(density) { depth.verticalTravel.toPx() }
    val dragAmount = pageOffset.coerceIn(-1f, 1f)
    val translationX = -dragAmount * horizontalTravel
    val translationY = verticalTravel * (1f - focus)
    val scale = lerpFloat(depth.minScale, 1f, focus) + settleScaleProgress * 0.004f
    val alpha = lerpFloat(depth.minAlpha, 1f, focus)

    Box(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            this.alpha = alpha
            this.translationX = translationX
            this.translationY = translationY
            this.scaleX = scale
            this.scaleY = scale
        }
    ) {
        content()
    }
}

internal fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
