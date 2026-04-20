package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A single dimension of the user's digital portrait (KV-style, one row per dimension). */
@Entity(
    tableName = "portrait_dimensions",
    indices = [Index("dimensionKey", unique = true)]
)
data class PortraitDimension(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dimensionKey: String,
    val displayName: String,
    val content: String = "",
    val confidence: Float = 0f,
    val observationDays: Int = 0,
    val lastSourceDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Default portrait dimensions seeded on first run. */
        fun defaults(): List<PortraitDimension> = listOf(
            PortraitDimension(dimensionKey = "appearance", displayName = "外貌特征"),
            PortraitDimension(dimensionKey = "environment", displayName = "生活环境"),
            PortraitDimension(dimensionKey = "habits", displayName = "行为习惯"),
            PortraitDimension(dimensionKey = "schedule", displayName = "作息规律"),
            PortraitDimension(dimensionKey = "social", displayName = "社交关系"),
            PortraitDimension(dimensionKey = "personality", displayName = "性格特点"),
            PortraitDimension(dimensionKey = "interests", displayName = "兴趣偏好"),
            PortraitDimension(dimensionKey = "health", displayName = "健康状态"),
        )
    }
}
