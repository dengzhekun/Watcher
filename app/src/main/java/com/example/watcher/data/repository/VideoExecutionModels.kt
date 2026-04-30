package com.example.watcher.data.repository

import com.example.watcher.data.model.VideoAnalysisResult
import com.example.watcher.data.model.VideoProcessRun
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoRunStatus
import com.example.watcher.data.model.VideoSegmentFeedback
import com.example.watcher.data.model.VideoSegmentRun
import com.example.watcher.data.model.VideoTimelineEvent
import com.example.watcher.data.remote.DoubaoResponse
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.File

data class VideoExecutionStatusUpdate(
    val stage: VideoRunStatus,
    val runId: Long,
    val segmentIndex: Int,
    val segmentCount: Int,
    val message: String,
    val templateLabel: String? = null,
    val segmentDurationSeconds: Int = 0,
    val captureIntervalSeconds: Int = 0,
    val finalSummary: String = "",
    val finalConclusion: String = "",
    val timelineEvents: List<VideoTimelineEvent> = emptyList(),
    val streamingBuffer: String? = null,
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
    val errorMessage: String? = null
)

data class VideoExecutionResult(
    val task: VideoProcessTaskDraft,
    val run: VideoProcessRun,
    val finalResult: VideoAnalysisResult
)

internal data class RecordedSegment(
    val segment: VideoSegmentRun,
    val file: File,
    val segmentNumber: Int,
    val durationSeconds: Int,
    val startOffsetSeconds: Int
)

internal data class SegmentExecutionResult(
    val segment: VideoSegmentRun,
    val analysisResult: VideoAnalysisResult
)

internal class VideoProcessException(
    val stage: VideoRunStatus,
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)

internal fun DoubaoResponse.requireOutputText(operation: String): String {
    val text = output.orEmpty()
        .asReversed()
        .asSequence()
        .flatMap { item ->
            sequence {
                item.text?.trim()?.takeIf { it.isNotEmpty() }?.let { yield(it) }
                item.content.orEmpty().asReversed().forEach { content ->
                    content.text?.trim()?.takeIf { it.isNotEmpty() }?.let { yield(it) }
                }
            }
        }
        .firstOrNull()

    return text
        ?: throw IllegalStateException(
            "$operation returned no text. Response shape: ${describeOutputShapeForError()}"
        )
}

private fun DoubaoResponse.describeOutputShapeForError(): String {
    val items = output.orEmpty().joinToString(separator = "; ") { item ->
        val contentTypes = item.content.orEmpty()
            .map { it.type ?: "unknown" }
            .joinToString(separator = ",")
            .ifBlank { "-" }
        "type=${item.type ?: "unknown"},status=${item.status ?: "-"},content=$contentTypes"
    }
    return "id=${id ?: "-"}, model=${model ?: "-"}, output=${if (items.isBlank()) "empty" else items}"
}

internal fun Throwable.toUserMessage(defaultMessage: String): String {
    return when (this) {
        is VideoProcessException -> userMessage
        is CancellationException -> defaultMessage
        is HttpException -> {
            val detail = runCatching { response()?.errorBody()?.string() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            detail ?: "$defaultMessage (HTTP ${code()})"
        }

        else -> message?.takeIf { it.isNotBlank() } ?: defaultMessage
    }
}
