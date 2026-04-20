package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "scene_profiles",
    indices = [Index("lastVerifiedAt")]
)
data class SceneProfile(
    @PrimaryKey val sceneId: String = UUID.randomUUID().toString(),
    val label: String,
    val userLabel: String? = null,
    val summary: String,
    val anchorObjects: String = "",
    val layoutHints: String = "",
    val stableEntities: String = "",
    val placeClusterId: String = "",
    val placeType: String = "",
    val spaceType: String = "",
    val usageCount: Int = 0,
    val lastVerifiedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SceneProbeSnapshot(
    val spaceType: String,
    val summary: String,
    val fixedFeatures: String,
    val detailObjects: String,
    val placeClusterId: String = "",
    val placeType: String = ""
)

data class SceneRecallResult(
    val profile: SceneProfile,
    val confidence: Float,
    val probeSummary: String,
    val matchedAnchors: List<String>,
    val matchBreakdown: MatchBreakdown
)

data class MatchBreakdown(
    val placeMatch: Boolean,
    val placeTypeMatch: Boolean,
    val spaceTypeMatch: Boolean,
    val fixedOverlap: List<String>,
    val detailOverlap: List<String>,
    val totalScore: Float
)

data class SceneAnalysisResult(
    val probe: SceneProbeSnapshot,
    val recallResult: SceneRecallResult?
)
