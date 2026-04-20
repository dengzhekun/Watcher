package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Daily summary record on the blackboard. One row per calendar day. */
@Entity(tableName = "blackboard_days")
data class BlackboardDay(
    @PrimaryKey val date: String,            // "2026-04-14"
    val sceneMemory: String = "",
    val entityMemory: String = "",
    val actionSummary: String = "",
    val coreMemoryA: String = "",
    val latestMemoryB: String = "",
    val dailyDigest: String = "",
    val totalEntries: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Individual commentary observation entry, linked to a day. */
@Entity(
    tableName = "blackboard_entries",
    foreignKeys = [ForeignKey(
        entity = BlackboardDay::class,
        parentColumns = ["date"],
        childColumns = ["dayDate"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("dayDate"), Index("timestamp")]
)
data class BlackboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayDate: String,
    val segmentIndex: Int,
    val timestamp: Long,
    val text: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

object BlackboardObservationCategories {
    const val SCENE = "scene"
    const val USER = "user"
    const val INTERACTION = "interaction"
    const val TIME = "time"
    const val UNKNOWN = "unknown"
}

/** Parsed observation atoms extracted from a commentary entry. */
@Entity(
    tableName = "blackboard_observation_items",
    foreignKeys = [ForeignKey(
        entity = BlackboardEntry::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entryId"), Index("dayDate"), Index("category"), Index("timestamp")]
)
data class BlackboardObservationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val dayDate: String,
    val segmentIndex: Int,
    val timestamp: Long,
    val category: String,
    val content: String,
    val dimensionHint: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
