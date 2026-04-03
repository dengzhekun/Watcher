package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitor_tasks")
data class MonitorTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val userInput: String,
    val userRequirement: String,
    val originalSceneDescription: String,
    val checkInterval: Int,
    val promptTemplate: String,
    val baseFrameBase64: String? = null,
    val baselineImagePath: String? = null,
    val monitorMode: MonitorMode = MonitorMode.SceneBaseline,
    val targetTrigger: TargetTrigger = TargetTrigger.OnAppear,
    val baselineSource: BaselineSource = BaselineSource.CapturedFrame,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val runCount: Int = 0,
    val lastStatus: String? = null,
    val lastSummary: String? = null
)
