package com.example.watcher.data.repository

import android.graphics.Bitmap
import com.example.watcher.data.local.TimelineEventDao
import com.example.watcher.data.local.VideoProcessRunDao
import com.example.watcher.data.local.VideoProcessTaskDao
import com.example.watcher.data.model.TimelineEventEntity
import com.example.watcher.data.model.VideoAnalysisResult
import com.example.watcher.data.model.VideoProcessRun
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoRunStatus
import com.example.watcher.data.model.VideoSegmentFeedback
import com.example.watcher.data.model.VideoTimelineEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

internal class VideoExecutionOrchestrator(
    private val taskDao: VideoProcessTaskDao,
    private val runDao: VideoProcessRunDao,
    private val timelineEventDao: TimelineEventDao,
    private val saveTask: suspend (VideoProcessTaskDraft) -> VideoProcessTaskDraft,
    private val segmentProcessor: VideoSegmentProcessor
) {
    suspend fun executeTask(
        draft: VideoProcessTaskDraft,
        streamingOutputEnabled: Boolean,
        latestFrameProvider: () -> Bitmap?,
        outputRoot: File,
        shouldStopRequested: () -> Boolean = { false },
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): VideoExecutionResult = coroutineScope {
        segmentProcessor.requireApiKey()
        val task = saveTask(draft)
        val taskEntity = task.taskId?.let { taskDao.getTaskById(it) }
            ?: error("Failed to save the video task before execution.")
        val scheduledSegmentCount = task.plannedSegmentCount
        val now = System.currentTimeMillis()
        var run = VideoProcessRun(
            taskId = taskEntity.id,
            templateId = task.templateId,
            templateLabel = task.templateLabel,
            taskTitle = taskEntity.title,
            taskRequirement = taskEntity.userRequirement,
            status = VideoRunStatus.Recording,
            recordingStartedAt = now,
            totalDurationSeconds = task.plannedDurationSeconds,
            segmentDurationSeconds = task.plannedSegmentDurationSeconds,
            captureIntervalSeconds = task.captureIntervalSeconds,
            segmentCount = scheduledSegmentCount
        )
        val runId = runDao.upsert(run)
        run = run.copy(id = runId)

        taskDao.upsert(
            taskEntity.copy(
                updatedAt = now,
                lastUsedAt = now,
                runCount = taskEntity.runCount + 1
            )
        )

        onStatus(
            VideoExecutionStatusUpdate(
                stage = VideoRunStatus.Recording,
                runId = runId,
                segmentIndex = 0,
                segmentCount = scheduledSegmentCount,
                message = "Preparing video analysis",
                templateLabel = task.templateLabel,
                segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                captureIntervalSeconds = task.captureIntervalSeconds,
                streamingEnabled = streamingOutputEnabled,
                streamingBuffer = "",
                isRecordingActive = true,
                isAnalysisActive = false,
                recordedSegmentCount = 0,
                analyzedSegmentCount = 0,
                pendingSegmentCount = 0,
                recordedDurationSeconds = 0,
                remainingDurationSeconds = task.plannedDurationSeconds,
                nextCaptureInSeconds = 0,
                recordingSegmentIndex = 1
            )
        )

        val recordedSegmentCount = AtomicInteger(0)
        val analyzedSegmentCount = AtomicInteger(0)
        val recordedDurationSeconds = AtomicInteger(0)
        val nextCaptureOffsetSeconds = AtomicInteger(0)
        val recordedSegments = Channel<RecordedSegment>(Channel.UNLIMITED)
        val partialTimelineEvents = java.util.Collections.synchronizedList(mutableListOf<VideoTimelineEvent>())
        val segmentFeedbacks = java.util.Collections.synchronizedList(mutableListOf<VideoSegmentFeedback>())

        val analyzer = async {
            val results = mutableListOf<SegmentExecutionResult>()
            for (recordedSegment in recordedSegments) {
                ensureActive()
                val result = segmentProcessor.analyzeRecordedSegment(
                    recordedSegment = recordedSegment,
                    task = task,
                    segmentCount = scheduledSegmentCount,
                    runId = runId,
                    streamingOutputEnabled = streamingOutputEnabled,
                    recordedSegmentCount = recordedSegmentCount,
                    analyzedSegmentCount = analyzedSegmentCount,
                    recordedDurationSeconds = recordedDurationSeconds,
                    onStatus = onStatus
                )
                results += result

                val offsetEvents = result.analysisResult.timelineEvents.map { event ->
                    event.copy(timestampSeconds = event.timestampSeconds + recordedSegment.startOffsetSeconds)
                }
                partialTimelineEvents += offsetEvents
                val completedCount = analyzedSegmentCount.incrementAndGet()
                segmentFeedbacks += VideoSegmentFeedback(
                    segmentIndex = recordedSegment.segmentNumber,
                    summary = result.analysisResult.summary,
                    conclusion = result.analysisResult.conclusion
                )

                onStatus(
                    VideoExecutionStatusUpdate(
                        stage = VideoRunStatus.Analyzing,
                        runId = runId,
                        segmentIndex = recordedSegment.segmentNumber,
                        segmentCount = scheduledSegmentCount,
                        message = if (
                            completedCount < recordedSegmentCount.get() ||
                            (recordedSegmentCount.get() < scheduledSegmentCount && !shouldStopRequested())
                        ) {
                            "第 ${recordedSegment.segmentNumber}/$scheduledSegmentCount 段分析完成，继续处理后续片段"
                        } else {
                            "All recorded segments analyzed. Preparing final summary"
                        },
                        templateLabel = task.templateLabel,
                        segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                        captureIntervalSeconds = task.captureIntervalSeconds,
                        timelineEvents = partialTimelineEvents.sortedBy { it.timestampSeconds },
                        streamingEnabled = streamingOutputEnabled,
                        isStreamingActive = false,
                        isRecordingActive = recordedSegmentCount.get() < scheduledSegmentCount &&
                            !shouldStopRequested(),
                        isAnalysisActive = completedCount < recordedSegmentCount.get(),
                        recordedSegmentCount = recordedSegmentCount.get(),
                        analyzedSegmentCount = completedCount,
                        pendingSegmentCount = (recordedSegmentCount.get() - completedCount).coerceAtLeast(0),
                        recordedDurationSeconds = recordedDurationSeconds.get(),
                        remainingDurationSeconds = (task.plannedDurationSeconds - nextCaptureOffsetSeconds.get())
                            .coerceAtLeast(0),
                        stopRequested = shouldStopRequested(),
                        activeStreamingSegmentIndex = 0,
                        segmentFeedbacks = segmentFeedbacks.toList()
                    )
                )
            }
            results
        }

        val runStartedAt = System.currentTimeMillis()
        var producedSegments = 0
        try {
            while (nextCaptureOffsetSeconds.get() < task.plannedDurationSeconds) {
                ensureActive()
                if (shouldStopRequested() && producedSegments > 0) {
                    break
                }

                val scheduledOffsetSeconds = nextCaptureOffsetSeconds.get()
                val scheduledStartAt = runStartedAt + (scheduledOffsetSeconds * 1_000L)
                val waitMs = (scheduledStartAt - System.currentTimeMillis()).coerceAtLeast(0L)
                if (waitMs > 0L) {
                    onStatus(
                        VideoExecutionStatusUpdate(
                            stage = VideoRunStatus.Recording,
                            runId = runId,
                            segmentIndex = producedSegments,
                            segmentCount = scheduledSegmentCount,
                            message = "Waiting for next capture window",
                            templateLabel = task.templateLabel,
                            segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                            captureIntervalSeconds = task.captureIntervalSeconds,
                            streamingEnabled = streamingOutputEnabled,
                            isRecordingActive = false,
                            isAnalysisActive = recordedSegmentCount.get() > analyzedSegmentCount.get(),
                            recordedSegmentCount = recordedSegmentCount.get(),
                            analyzedSegmentCount = analyzedSegmentCount.get(),
                            pendingSegmentCount = (recordedSegmentCount.get() - analyzedSegmentCount.get())
                                .coerceAtLeast(0),
                            recordedDurationSeconds = recordedDurationSeconds.get(),
                            remainingDurationSeconds = (task.plannedDurationSeconds - scheduledOffsetSeconds)
                                .coerceAtLeast(0),
                            nextCaptureInSeconds = ceil(waitMs / 1000.0).toInt(),
                            recordingSegmentIndex = (producedSegments + 1).coerceAtMost(scheduledSegmentCount),
                            stopRequested = shouldStopRequested()
                        )
                    )
                    delay(waitMs)
                }

                ensureActive()
                if (shouldStopRequested() && producedSegments > 0) {
                    break
                }

                val remainingDuration = task.plannedDurationSeconds - scheduledOffsetSeconds
                if (remainingDuration <= 0) {
                    break
                }

                val segmentNumber = producedSegments + 1
                val actualDuration = minOf(task.plannedSegmentDurationSeconds, remainingDuration)
                    .coerceAtLeast(1)

                val recordedSegment = segmentProcessor.recordSegmentClip(
                    runId = runId,
                    task = task,
                    segmentNumber = segmentNumber,
                    segmentCount = scheduledSegmentCount,
                    actualDuration = actualDuration,
                    outputRoot = outputRoot,
                    latestFrameProvider = latestFrameProvider,
                    startOffsetSeconds = scheduledOffsetSeconds,
                    streamingOutputEnabled = streamingOutputEnabled,
                    recordedSegmentCount = recordedSegmentCount,
                    analyzedSegmentCount = analyzedSegmentCount,
                    recordedDurationSeconds = recordedDurationSeconds,
                    onStatus = onStatus
                )

                producedSegments = segmentNumber
                val producedCount = recordedSegmentCount.incrementAndGet()
                val totalRecordedDuration = recordedDurationSeconds.addAndGet(actualDuration)
                val nextOffset = scheduledOffsetSeconds + task.captureIntervalSeconds
                nextCaptureOffsetSeconds.set(nextOffset)
                recordedSegments.send(recordedSegment)

                val hasMoreScheduledSegments =
                    nextOffset < task.plannedDurationSeconds && !shouldStopRequested()
                onStatus(
                    VideoExecutionStatusUpdate(
                        stage = VideoRunStatus.Recording,
                        runId = runId,
                        segmentIndex = segmentNumber,
                        segmentCount = scheduledSegmentCount,
                        message = when {
                            shouldStopRequested() -> "Stop requested. Waiting for pending segment analysis"
                            hasMoreScheduledSegments -> "Segment $segmentNumber/$scheduledSegmentCount recorded and queued"
                            else -> "Capture finished. Waiting for remaining segment analysis"
                        },
                        templateLabel = task.templateLabel,
                        segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                        captureIntervalSeconds = task.captureIntervalSeconds,
                        streamingEnabled = streamingOutputEnabled,
                        isRecordingActive = hasMoreScheduledSegments,
                        isAnalysisActive = producedCount > analyzedSegmentCount.get(),
                        recordedSegmentCount = producedCount,
                        analyzedSegmentCount = analyzedSegmentCount.get(),
                        pendingSegmentCount = (producedCount - analyzedSegmentCount.get()).coerceAtLeast(0),
                        recordedDurationSeconds = totalRecordedDuration,
                        remainingDurationSeconds = (task.plannedDurationSeconds - nextOffset).coerceAtLeast(0),
                        nextCaptureInSeconds = if (hasMoreScheduledSegments) {
                            task.captureIntervalSeconds
                        } else {
                            null
                        },
                        recordingSegmentIndex = (segmentNumber + 1).coerceAtMost(scheduledSegmentCount),
                        stopRequested = shouldStopRequested()
                    )
                )
            }
        } finally {
            recordedSegments.close()
        }

        val segmentResults = analyzer.await()
        if (segmentResults.isEmpty()) {
            run = run.copy(
                status = VideoRunStatus.Cancelled,
                recordingEndedAt = System.currentTimeMillis(),
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            runDao.upsert(run)
            onStatus(
                VideoExecutionStatusUpdate(
                    stage = VideoRunStatus.Cancelled,
                    runId = runId,
                    segmentIndex = 0,
                    segmentCount = scheduledSegmentCount,
                    message = "Task stopped before any valid segment was produced",
                    templateLabel = task.templateLabel,
                    segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                    captureIntervalSeconds = task.captureIntervalSeconds,
                    streamingEnabled = streamingOutputEnabled,
                    recordedSegmentCount = 0,
                    analyzedSegmentCount = 0,
                    pendingSegmentCount = 0,
                    recordedDurationSeconds = 0,
                    remainingDurationSeconds = task.plannedDurationSeconds,
                    stopRequested = shouldStopRequested()
                )
            )
            return@coroutineScope VideoExecutionResult(
                task = task,
                run = run,
                finalResult = VideoAnalysisResult(
                    summary = "",
                    conclusion = "",
                    timelineEvents = emptyList()
                )
            )
        }

        val allTimelineEvents = partialTimelineEvents.sortedBy { it.timestampSeconds }
        val shouldSummarize = task.finalSummaryEnabled && segmentResults.size > 1

        onStatus(
            VideoExecutionStatusUpdate(
                stage = VideoRunStatus.Summarizing,
                runId = runId,
                segmentIndex = recordedSegmentCount.get(),
                segmentCount = scheduledSegmentCount,
                message = if (shouldSummarize) {
                    "Generating final summary"
                } else {
                    "Compiling completed segment results"
                },
                templateLabel = task.templateLabel,
                segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                captureIntervalSeconds = task.captureIntervalSeconds,
                streamingEnabled = streamingOutputEnabled,
                streamingBuffer = "",
                isRecordingActive = false,
                isAnalysisActive = shouldSummarize,
                recordedSegmentCount = recordedSegmentCount.get(),
                analyzedSegmentCount = analyzedSegmentCount.get(),
                pendingSegmentCount = 0,
                recordedDurationSeconds = recordedDurationSeconds.get(),
                remainingDurationSeconds = (task.plannedDurationSeconds - nextCaptureOffsetSeconds.get())
                    .coerceAtLeast(0),
                stopRequested = shouldStopRequested(),
                segmentFeedbacks = segmentFeedbacks.toList()
            )
        )

        val finalResult = try {
            when {
                segmentResults.size == 1 -> {
                    segmentResults.single().analysisResult.copy(timelineEvents = allTimelineEvents)
                }

                !task.finalSummaryEnabled -> segmentProcessor.combineSegmentResults(segmentResults, allTimelineEvents)

                else -> {
                    segmentProcessor.summarizeSegments(
                        task = task,
                        results = segmentResults,
                        runId = runId,
                        segmentCount = scheduledSegmentCount,
                        streamingOutputEnabled = streamingOutputEnabled,
                        recordedSegmentCount = recordedSegmentCount.get(),
                        analyzedSegmentCount = analyzedSegmentCount.get(),
                        recordedDurationSeconds = recordedDurationSeconds.get(),
                        segmentFeedbacks = segmentFeedbacks.toList(),
                        onStatus = onStatus
                    ).copy(timelineEvents = allTimelineEvents)
                }
            }
        } catch (error: Exception) {
            throw VideoProcessException(
                stage = VideoRunStatus.Summarizing,
                userMessage = "Failed to summarize segment results: ${error.toUserMessage("Check the model output.")}",
                cause = error
            )
        }

        val mergedVideoPath = try {
            onStatus(
                VideoExecutionStatusUpdate(
                    stage = VideoRunStatus.Summarizing,
                    runId = runId,
                    segmentIndex = recordedSegmentCount.get(),
                    segmentCount = scheduledSegmentCount,
                    message = "Generating merged video",
                    templateLabel = task.templateLabel,
                    segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                    captureIntervalSeconds = task.captureIntervalSeconds,
                    finalSummary = finalResult.summary,
                    finalConclusion = finalResult.conclusion,
                    timelineEvents = finalResult.timelineEvents,
                    streamingEnabled = streamingOutputEnabled,
                    streamingBuffer = finalResult.rawResponse,
                    isRecordingActive = false,
                    isAnalysisActive = false,
                    recordedSegmentCount = recordedSegmentCount.get(),
                    analyzedSegmentCount = analyzedSegmentCount.get(),
                    pendingSegmentCount = 0,
                    recordedDurationSeconds = recordedDurationSeconds.get(),
                    remainingDurationSeconds = (task.plannedDurationSeconds - nextCaptureOffsetSeconds.get())
                        .coerceAtLeast(0),
                    stopRequested = shouldStopRequested(),
                    segmentFeedbacks = segmentFeedbacks.toList()
                )
            )
            segmentProcessor.mergeSegmentVideos(
                runId = runId,
                task = task,
                results = segmentResults,
                outputRoot = outputRoot
            )
        } catch (_: Exception) {
            null
        }

        try {
            run = run.copy(
                status = VideoRunStatus.Completed,
                recordingEndedAt = System.currentTimeMillis(),
                finalSummary = finalResult.summary,
                finalConclusion = finalResult.conclusion,
                rawModelSummary = finalResult.rawResponse,
                mergedVideoPath = mergedVideoPath,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            runDao.upsert(run)
            timelineEventDao.deleteByRunId(runId)
            timelineEventDao.insertAll(
                finalResult.timelineEvents.map { event ->
                    TimelineEventEntity(
                        runId = runId,
                        timestampSeconds = event.timestampSeconds,
                        title = event.title,
                        detail = event.detail,
                        confidence = event.confidence
                    )
                }
            )
        } catch (error: Exception) {
            throw VideoProcessException(
                stage = VideoRunStatus.Completed,
                userMessage = "Failed to save the final video result: ${error.toUserMessage("Check the local database state.")}",
                cause = error
            )
        }

        onStatus(
            VideoExecutionStatusUpdate(
                stage = VideoRunStatus.Completed,
                runId = runId,
                segmentIndex = recordedSegmentCount.get(),
                segmentCount = scheduledSegmentCount,
                message = if (shouldStopRequested() && recordedSegmentCount.get() < scheduledSegmentCount) {
                    "Stopped manually, partial summary completed"
                } else {
                    "视频处理完成"
                },
                templateLabel = task.templateLabel,
                segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                captureIntervalSeconds = task.captureIntervalSeconds,
                finalSummary = finalResult.summary,
                finalConclusion = finalResult.conclusion,
                timelineEvents = finalResult.timelineEvents,
                streamingEnabled = streamingOutputEnabled,
                streamingBuffer = finalResult.rawResponse,
                isRecordingActive = false,
                isAnalysisActive = false,
                recordedSegmentCount = recordedSegmentCount.get(),
                analyzedSegmentCount = analyzedSegmentCount.get(),
                pendingSegmentCount = 0,
                recordedDurationSeconds = recordedDurationSeconds.get(),
                remainingDurationSeconds = (task.plannedDurationSeconds - nextCaptureOffsetSeconds.get())
                    .coerceAtLeast(0),
                stopRequested = shouldStopRequested(),
                segmentFeedbacks = segmentFeedbacks.toList()
            )
        )

        VideoExecutionResult(
            task = task,
            run = run,
            finalResult = finalResult
        )
    }

    suspend fun markRunFailed(
        runId: Long,
        segmentIndex: Int,
        segmentCount: Int,
        streamingEnabled: Boolean,
        error: Throwable,
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ) {
        val existingRun = runDao.getRunById(runId) ?: return
        if (existingRun.status == VideoRunStatus.Cancelled || existingRun.status == VideoRunStatus.Completed) {
            return
        }

        val failureMessage = error.toUserMessage("Execution failed.")
        runDao.upsert(
            existingRun.copy(
                status = VideoRunStatus.Failed,
                errorMessage = failureMessage,
                recordingEndedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        onStatus(
            VideoExecutionStatusUpdate(
                stage = VideoRunStatus.Failed,
                runId = runId,
                segmentIndex = segmentIndex,
                segmentCount = segmentCount,
                message = failureMessage,
                templateLabel = existingRun.templateLabel,
                segmentDurationSeconds = existingRun.segmentDurationSeconds,
                captureIntervalSeconds = existingRun.captureIntervalSeconds,
                streamingEnabled = streamingEnabled,
                errorMessage = failureMessage,
                isRecordingActive = false,
                isAnalysisActive = false,
                activeStreamingSegmentIndex = 0
            )
        )
    }

    suspend fun markRunCancelled(
        runId: Long,
        segmentIndex: Int,
        segmentCount: Int,
        streamingEnabled: Boolean,
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ) {
        val existingRun = runDao.getRunById(runId) ?: return
        if (existingRun.status == VideoRunStatus.Completed || existingRun.status == VideoRunStatus.Cancelled) {
            return
        }

        runDao.upsert(
            existingRun.copy(
                status = VideoRunStatus.Cancelled,
                errorMessage = null,
                recordingEndedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        onStatus(
            VideoExecutionStatusUpdate(
                stage = VideoRunStatus.Cancelled,
                runId = runId,
                segmentIndex = segmentIndex,
                segmentCount = segmentCount,
                message = "Current video processing task cancelled",
                templateLabel = existingRun.templateLabel,
                segmentDurationSeconds = existingRun.segmentDurationSeconds,
                captureIntervalSeconds = existingRun.captureIntervalSeconds,
                streamingEnabled = streamingEnabled,
                isRecordingActive = false,
                isAnalysisActive = false,
                activeStreamingSegmentIndex = 0
            )
        )
    }
}
