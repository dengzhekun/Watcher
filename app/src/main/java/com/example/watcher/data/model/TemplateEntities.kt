package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitor_templates")
data class MonitorTemplateEntity(
    @PrimaryKey val templateId: String,
    val label: String,
    val description: String,
    val userRequirement: String,
    val originalSceneDescription: String,
    val checkIntervalSeconds: Int,
    val promptTemplate: String,
    val monitorMode: String,
    val targetTrigger: String,
    val baselineSource: String,
    val isDefault: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "video_templates")
data class VideoTemplateEntity(
    @PrimaryKey val templateId: String,
    val label: String,
    val description: String,
    val taskCategory: String,
    val strategyReason: String,
    val userRequirement: String,
    val sceneContext: String,
    val segmentAnalysisPrompt: String,
    val finalSummaryPrompt: String,
    val recordingDurationSeconds: Int,
    val segmentDurationSeconds: Int,
    val captureIntervalSeconds: Int,
    val samplingFps: Int,
    val autoStartStreamingOutput: Boolean,
    val finalSummaryEnabled: Boolean,
    val isDefault: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "council_templates")
data class CouncilTemplateEntity(
    @PrimaryKey val templateId: String,
    val label: String,
    val description: String,
    val sceneType: String,
    val objective: String,
    val focus: String,
    val speakerRole: String = "",
    val targetRole: String = "",
    val background: String = "",
    val isDefault: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
