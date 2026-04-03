package com.example.watcher.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.ceil

enum class VideoTaskCategory(val value: String) {
    LongHorizonSummary("long_horizon_summary"),
    ContinuousWatch("continuous_watch"),
    ShortBurstDense("short_burst_dense");

    companion object {
        fun fromValue(value: String?): VideoTaskCategory? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

@Entity(tableName = "video_process_tasks")
data class VideoProcessTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: String? = null,
    val templateLabel: String? = null,
    val taskCategory: String? = null,
    val strategyReason: String = "",
    val title: String,
    val userInput: String,
    val userRequirement: String,
    val sceneContext: String,
    @ColumnInfo(name = "analysisPrompt")
    val segmentAnalysisPrompt: String,
    val finalSummaryPrompt: String,
    val plannedDurationSeconds: Int,
    val plannedSamplingFps: Int,
    val plannedSegmentDurationSeconds: Int,
    val captureIntervalSeconds: Int,
    val plannedSegmentCount: Int,
    val autoStartStreamingOutput: Boolean = false,
    val finalSummaryEnabled: Boolean = true,
    val confirmationNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val runCount: Int = 0
)

@Entity(
    tableName = "video_process_runs",
    indices = [Index("taskId")]
)
data class VideoProcessRun(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val templateId: String? = null,
    val templateLabel: String? = null,
    val taskTitle: String = "",
    val taskRequirement: String = "",
    val status: VideoRunStatus = VideoRunStatus.Idle,
    val recordingStartedAt: Long? = null,
    val recordingEndedAt: Long? = null,
    val totalDurationSeconds: Int = 0,
    val segmentDurationSeconds: Int = 0,
    val captureIntervalSeconds: Int = 0,
    val segmentCount: Int = 0,
    val finalSummary: String = "",
    val finalConclusion: String = "",
    val rawModelSummary: String = "",
    val mergedVideoPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class VideoRunStatus {
    Idle,
    Planning,
    AwaitingConfirmation,
    Recording,
    Uploading,
    Preprocessing,
    Analyzing,
    Summarizing,
    Completed,
    Failed,
    Cancelled
}

@Entity(
    tableName = "video_segment_runs",
    foreignKeys = [
        ForeignKey(
            entity = VideoProcessRun::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId")]
)
data class VideoSegmentRun(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: Long,
    val segmentIndex: Int,
    val status: VideoRunStatus = VideoRunStatus.Idle,
    val durationSeconds: Int,
    val localFilePath: String? = null,
    val arkFileId: String? = null,
    val summary: String = "",
    val conclusion: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = VideoProcessRun::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId"), Index("segmentRunId")]
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: Long,
    val segmentRunId: Long? = null,
    val timestampSeconds: Int,
    val title: String,
    val detail: String,
    val confidence: Float? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class VideoProcessTaskDraft(
    val taskId: Long? = null,
    val templateId: String? = null,
    val templateLabel: String? = null,
    val taskCategory: String? = null,
    val strategyReason: String = "",
    val title: String = "",
    val userInput: String = "",
    val userRequirement: String = "",
    val sceneContext: String = "",
    val segmentAnalysisPrompt: String = "",
    val finalSummaryPrompt: String = "",
    val plannedDurationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val plannedSamplingFps: Int = DEFAULT_SAMPLING_FPS,
    val plannedSegmentDurationSeconds: Int = DEFAULT_SEGMENT_DURATION_SECONDS,
    val captureIntervalSeconds: Int = DEFAULT_CAPTURE_INTERVAL_SECONDS,
    val plannedSegmentCount: Int = 1,
    val autoStartStreamingOutput: Boolean = false,
    val finalSummaryEnabled: Boolean = true,
    val confirmationNotes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun normalized(): VideoProcessTaskDraft {
        val safeRequirement = userRequirement.ifBlank { userInput.ifBlank { DEFAULT_REQUIREMENT } }
        val safeTitle = title.ifBlank { safeRequirement.take(MAX_TITLE_LENGTH) }
            .take(MAX_TITLE_LENGTH)
        val safeDuration = plannedDurationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        val safeSamplingFps = plannedSamplingFps.coerceIn(MIN_SAMPLING_FPS, MAX_SAMPLING_FPS)
        val safeSegmentDuration = plannedSegmentDurationSeconds
            .coerceIn(MIN_SEGMENT_DURATION_SECONDS, MAX_SEGMENT_DURATION_SECONDS)
            .coerceAtMost(safeDuration)
        val safeCaptureInterval = captureIntervalSeconds
            .coerceIn(MIN_CAPTURE_INTERVAL_SECONDS, MAX_CAPTURE_INTERVAL_SECONDS)
            .coerceAtLeast(safeSegmentDuration)
        val safeSegmentCount = ceil(safeDuration / safeCaptureInterval.toDouble()).toInt()
            .coerceAtLeast(1)
        val safeSceneContext = sceneContext.ifBlank { DEFAULT_SCENE_CONTEXT }
        val safeSegmentPrompt = segmentAnalysisPrompt.ifBlank {
            buildFallbackSegmentAnalysisPrompt(
                userRequirement = safeRequirement,
                sceneContext = safeSceneContext
            )
        }
        val safeFinalSummaryPrompt = finalSummaryPrompt.ifBlank {
            buildFallbackFinalSummaryPrompt(
                userRequirement = safeRequirement,
                sceneContext = safeSceneContext
            )
        }
        val safeCategory = VideoTaskCategory.fromValue(taskCategory)?.value

        return copy(
            taskCategory = safeCategory,
            strategyReason = strategyReason.trim(),
            title = safeTitle,
            userRequirement = safeRequirement,
            sceneContext = safeSceneContext,
            segmentAnalysisPrompt = safeSegmentPrompt,
            finalSummaryPrompt = safeFinalSummaryPrompt,
            plannedDurationSeconds = safeDuration,
            plannedSamplingFps = safeSamplingFps,
            plannedSegmentDurationSeconds = safeSegmentDuration,
            captureIntervalSeconds = safeCaptureInterval,
            plannedSegmentCount = safeSegmentCount,
            confirmationNotes = confirmationNotes.trim()
        )
    }

    fun toEntity(existing: VideoProcessTask? = null): VideoProcessTask {
        val normalized = normalized()
        val now = System.currentTimeMillis()
        return VideoProcessTask(
            id = normalized.taskId ?: 0L,
            templateId = normalized.templateId,
            templateLabel = normalized.templateLabel,
            taskCategory = normalized.taskCategory,
            strategyReason = normalized.strategyReason,
            title = normalized.title,
            userInput = normalized.userInput,
            userRequirement = normalized.userRequirement,
            sceneContext = normalized.sceneContext,
            segmentAnalysisPrompt = normalized.segmentAnalysisPrompt,
            finalSummaryPrompt = normalized.finalSummaryPrompt,
            plannedDurationSeconds = normalized.plannedDurationSeconds,
            plannedSamplingFps = normalized.plannedSamplingFps,
            plannedSegmentDurationSeconds = normalized.plannedSegmentDurationSeconds,
            captureIntervalSeconds = normalized.captureIntervalSeconds,
            plannedSegmentCount = normalized.plannedSegmentCount,
            autoStartStreamingOutput = normalized.autoStartStreamingOutput,
            finalSummaryEnabled = normalized.finalSummaryEnabled,
            confirmationNotes = normalized.confirmationNotes,
            createdAt = existing?.createdAt ?: normalized.createdAt,
            updatedAt = now,
            lastUsedAt = existing?.lastUsedAt,
            runCount = existing?.runCount ?: 0
        )
    }

    companion object {
        const val DEFAULT_DURATION_SECONDS = 30
        const val DEFAULT_SEGMENT_DURATION_SECONDS = 10
        const val DEFAULT_CAPTURE_INTERVAL_SECONDS = 10
        const val DEFAULT_SAMPLING_FPS = 1
        const val MIN_DURATION_SECONDS = 5
        const val MAX_DURATION_SECONDS = 21_600
        const val MIN_SEGMENT_DURATION_SECONDS = 2
        const val MAX_SEGMENT_DURATION_SECONDS = 300
        const val MIN_CAPTURE_INTERVAL_SECONDS = 2
        const val MAX_CAPTURE_INTERVAL_SECONDS = 3_600
        const val MIN_SAMPLING_FPS = 1
        const val MAX_SAMPLING_FPS = 8
        const val MAX_TITLE_LENGTH = 48
        private const val DEFAULT_REQUIREMENT = "请总结当前视频中的关键信息。"
        private const val DEFAULT_SCENE_CONTEXT = "当前画面可作为本次视频分析任务的场景参考。"

        fun fromEntity(task: VideoProcessTask): VideoProcessTaskDraft {
            return VideoProcessTaskDraft(
                taskId = task.id,
                templateId = task.templateId,
                templateLabel = task.templateLabel,
                taskCategory = task.taskCategory,
                strategyReason = task.strategyReason,
                title = task.title,
                userInput = task.userInput,
                userRequirement = task.userRequirement,
                sceneContext = task.sceneContext,
                segmentAnalysisPrompt = task.segmentAnalysisPrompt,
                finalSummaryPrompt = task.finalSummaryPrompt,
                plannedDurationSeconds = task.plannedDurationSeconds,
                plannedSamplingFps = task.plannedSamplingFps,
                plannedSegmentDurationSeconds = task.plannedSegmentDurationSeconds,
                captureIntervalSeconds = task.captureIntervalSeconds,
                plannedSegmentCount = task.plannedSegmentCount,
                autoStartStreamingOutput = task.autoStartStreamingOutput,
                finalSummaryEnabled = task.finalSummaryEnabled,
                confirmationNotes = task.confirmationNotes,
                createdAt = task.createdAt
            ).normalized()
        }

        fun buildFallbackSegmentAnalysisPrompt(
            userRequirement: String,
            sceneContext: String
        ): String {
            return buildString {
                append("请只分析当前上传的视频片段，围绕任务目标“")
                append(userRequirement)
                append("”。")
                append("结合场景参考：")
                append(sceneContext)
                append("。")
                append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
                append("timelineEvents 中每一项必须包含 timestampSeconds、title、detail、confidence。")
                append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
                append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用“高”“中”“低”。")
                append("timestampSeconds 使用当前片段内的相对秒数，不要推断片段外内容。")
            }
        }

        fun buildFallbackFinalSummaryPrompt(
            userRequirement: String,
            sceneContext: String
        ): String {
            return buildString {
                append("请基于全部分片分析结果，汇总任务目标“")
                append(userRequirement)
                append("”的最终结论。")
                append("场景参考：")
                append(sceneContext)
                append("。")
                append("需要串联完整时序，合并重复事件，并区分明确观察与谨慎推断。")
                append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
                append("timelineEvents 中每一项必须包含 timestampSeconds、title、detail、confidence。")
                append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
                append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用“高”“中”“低”。")
                append("timestampSeconds 使用整个任务时间线上的绝对秒数。")
            }
        }
    }
}

data class VideoTaskPlan(
    val templateId: String? = null,
    val templateLabel: String? = null,
    val taskCategory: String? = null,
    val strategyReason: String = "",
    val title: String,
    val userRequirement: String,
    val sceneContext: String,
    val recordingDurationSeconds: Int,
    val samplingFps: Int,
    val segmentDurationSeconds: Int,
    val captureIntervalSeconds: Int,
    val segmentCount: Int,
    val segmentAnalysisPrompt: String,
    val finalSummaryPrompt: String,
    val autoStartStreamingOutput: Boolean = false,
    val finalSummaryEnabled: Boolean = true,
    val confirmationNotes: String = ""
) {
    fun toDraft(
        userInput: String,
        taskId: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ): VideoProcessTaskDraft {
        return VideoProcessTaskDraft(
            taskId = taskId,
            templateId = templateId,
            templateLabel = templateLabel,
            taskCategory = taskCategory,
            strategyReason = strategyReason,
            title = title,
            userInput = userInput,
            userRequirement = userRequirement,
            sceneContext = sceneContext,
            segmentAnalysisPrompt = segmentAnalysisPrompt,
            finalSummaryPrompt = finalSummaryPrompt,
            plannedDurationSeconds = recordingDurationSeconds,
            plannedSamplingFps = samplingFps,
            plannedSegmentDurationSeconds = segmentDurationSeconds,
            captureIntervalSeconds = captureIntervalSeconds,
            plannedSegmentCount = segmentCount,
            autoStartStreamingOutput = autoStartStreamingOutput,
            finalSummaryEnabled = finalSummaryEnabled,
            confirmationNotes = confirmationNotes,
            createdAt = createdAt
        ).normalized()
    }
}

data class VideoTimelineEvent(
    val timestampSeconds: Int,
    val title: String,
    val detail: String,
    val confidence: Float? = null
)

data class VideoAnalysisResult(
    val summary: String,
    val conclusion: String,
    val timelineEvents: List<VideoTimelineEvent>,
    val rawResponse: String = ""
)

data class VideoSegmentFeedback(
    val segmentIndex: Int,
    val summary: String,
    val conclusion: String,
    val status: VideoRunStatus = VideoRunStatus.Completed
)

data class VideoProcessingStatus(
    val stage: VideoRunStatus = VideoRunStatus.Idle,
    val activeTask: VideoProcessTaskDraft? = null,
    val activeRunId: Long? = null,
    val templateLabel: String? = null,
    val currentSegmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val segmentDurationSeconds: Int = 0,
    val captureIntervalSeconds: Int = 0,
    val message: String = "",
    val finalSummary: String = "",
    val finalConclusion: String = "",
    val timelineEvents: List<VideoTimelineEvent> = emptyList(),
    val streamingBuffer: String = "",
    val streamingEnabled: Boolean = false,
    val isStreamingActive: Boolean = false,
    val isRecordingActive: Boolean = false,
    val isAnalysisActive: Boolean = false,
    val recordingSegmentIndex: Int = 0,
    val activeStreamingSegmentIndex: Int = 0,
    val recordedSegmentCount: Int = 0,
    val analyzedSegmentCount: Int = 0,
    val pendingSegmentCount: Int = 0,
    val recordedDurationSeconds: Int = 0,
    val remainingDurationSeconds: Int = 0,
    val nextCaptureInSeconds: Int? = null,
    val stopRequested: Boolean = false,
    val segmentFeedbacks: List<VideoSegmentFeedback> = emptyList(),
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)
