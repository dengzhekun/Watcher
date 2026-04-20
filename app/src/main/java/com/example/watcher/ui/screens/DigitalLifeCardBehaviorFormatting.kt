package com.example.watcher.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.watcher.data.model.BehaviorClaimStatuses
import com.example.watcher.data.model.MatchBreakdown
import com.example.watcher.data.model.SceneProfile

internal fun claimStatusRank(status: String): Int = when (status) {
    BehaviorClaimStatuses.STABLE -> 0
    BehaviorClaimStatuses.EMERGING -> 1
    BehaviorClaimStatuses.HYPOTHESIS -> 2
    BehaviorClaimStatuses.STALE -> 3
    BehaviorClaimStatuses.CONFLICTED -> 4
    else -> 5
}

@Composable
internal fun dimensionAccent(dimensionKey: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.82f)
    )
    val index = (dimensionKey.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

internal fun sceneDisplayLabel(scene: SceneProfile): String {
    return scene.userLabel?.trim().takeUnless { it.isNullOrBlank() } ?: scene.label
}

internal fun formatMatchBreakdown(
    breakdown: MatchBreakdown,
    scene: SceneProfile
): String {
    val parts = buildList {
        if (breakdown.placeMatch) {
            add("地点一致")
        } else if (breakdown.placeTypeMatch && scene.placeType.isNotBlank()) {
            add("地点类型「${placeTypeLabel(scene.placeType)}」一致")
        }
        if (breakdown.spaceTypeMatch && scene.spaceType.isNotBlank()) {
            add("空间类型「${scene.spaceType}」一致")
        }
        if (breakdown.fixedOverlap.isNotEmpty()) {
            add("固定物件命中 ${breakdown.fixedOverlap.size} 个（${breakdown.fixedOverlap.take(3).joinToString("、")}）")
        }
        if (breakdown.detailOverlap.isNotEmpty()) {
            add("细节命中 ${breakdown.detailOverlap.size} 个")
        }
        add("总分 ${"%.1f".format(breakdown.totalScore)}")
    }
    return parts.joinToString(" + ")
}

internal fun placeTypeLabel(raw: String): String = when (raw) {
    "home" -> "居家"
    "office" -> "办公"
    "third_place" -> "第三地点"
    "unknown" -> "未标注"
    else -> raw.ifBlank { "未标注" }
}
