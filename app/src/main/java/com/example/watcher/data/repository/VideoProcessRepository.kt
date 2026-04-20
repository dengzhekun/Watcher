package com.example.watcher.data.repository

import android.graphics.Bitmap
import com.example.watcher.data.local.TimelineEventDao
import com.example.watcher.data.local.VideoProcessRunDao
import com.example.watcher.data.local.VideoProcessTaskDao
import com.example.watcher.data.local.VideoSegmentRunDao
import com.example.watcher.data.model.TimelineEventEntity
import com.example.watcher.data.model.VideoAnalysisResult
import com.example.watcher.data.model.VideoProcessRun
import com.example.watcher.data.model.VideoProcessTask
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoRunStatus
import com.example.watcher.data.model.VideoSegmentFeedback
import com.example.watcher.data.model.VideoSegmentRun
import com.example.watcher.data.model.VideoTaskPlan
import com.example.watcher.data.model.VideoTimelineEvent
import com.example.watcher.data.remote.ArkResponseStreamEvent
import com.example.watcher.data.remote.ArkStreamingClient
import com.example.watcher.data.remote.ContentItem
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoImageRequest
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.DoubaoResponse
import com.example.watcher.data.remote.DoubaoVideoRequest
import com.example.watcher.data.remote.ImageContentItem
import com.example.watcher.data.remote.ImageMessage
import com.example.watcher.data.remote.Message
import com.example.watcher.data.remote.VideoContentItem
import com.example.watcher.data.remote.VideoMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

class VideoProcessRepository(
    private val apiService: DoubaoApiService,
    private val taskDao: VideoProcessTaskDao,
    private val runDao: VideoProcessRunDao,
    private val segmentRunDao: VideoSegmentRunDao,
    private val timelineEventDao: TimelineEventDao,
    private val llmWalletRepository: LlmWalletRepository,
    private val recorder: MjpegVideoRecorder = MjpegVideoRecorder(),
    private val segmentMerger: VideoSegmentMerger = VideoSegmentMerger(),
    private val streamingClient: ArkStreamingClient = ArkStreamingClient()
) {
    private val planningModel = ArkConfig.videoPlanningModel
    private val videoModel = ArkConfig.videoAnalysisModel
    private val apiKey = ArkConfig.apiKey

    fun observeTasks(): Flow<List<VideoProcessTask>> = taskDao.observeTasks()

    fun observeRecentRuns(): Flow<List<VideoProcessRun>> = runDao.observeRecentRuns()

    fun observeTimelineForRun(runId: Long) = timelineEventDao.observeEventsForRun(runId)

    suspend fun saveTask(draft: VideoProcessTaskDraft): VideoProcessTaskDraft {
        val normalized = draft.normalized()
        val existing = normalized.taskId?.let { taskDao.getTaskById(it) }
        val entity = normalized.toEntity(existing)
        val taskId = taskDao.upsert(entity)
        return VideoProcessTaskDraft.fromEntity(
            entity.copy(id = if (entity.id == 0L) taskId else entity.id)
        )
    }

    suspend fun deleteTask(id: Long) {
        taskDao.deleteById(id)
    }

    suspend fun planVideoTask(userInput: String, frame: Bitmap?): Result<VideoTaskPlan> {
        return runCatching {
            requireApiKey()
            val response = requestPlanningResponse(userInput, frame)
            val content = response.requireOutputText("video task planning")
            ModelOutputParser.parseVideoTaskPlan(content, userInput)
        }
    }

    suspend fun executeTask(
        draft: VideoProcessTaskDraft,
        streamingOutputEnabled: Boolean,
        latestFrameProvider: () -> Bitmap?,
        outputRoot: File,
        shouldStopRequested: () -> Boolean = { false },
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): VideoExecutionResult = coroutineScope {
        requireApiKey()

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
        val partialTimelineEvents = mutableListOf<VideoTimelineEvent>()
        val segmentFeedbacks = mutableListOf<VideoSegmentFeedback>()

        val analyzer = async {
            val results = mutableListOf<SegmentExecutionResult>()
            for (recordedSegment in recordedSegments) {
                ensureActive()
                val result = analyzeRecordedSegment(
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

                val recordedSegment = recordSegmentClip(
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

                !task.finalSummaryEnabled -> combineSegmentResults(segmentResults, allTimelineEvents)

                else -> {
                    summarizeSegments(
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
            mergeSegmentVideos(
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

    private suspend fun recordSegmentClip(
        runId: Long,
        task: VideoProcessTaskDraft,
        segmentNumber: Int,
        segmentCount: Int,
        actualDuration: Int,
        outputRoot: File,
        latestFrameProvider: () -> Bitmap?,
        startOffsetSeconds: Int,
        streamingOutputEnabled: Boolean,
        recordedSegmentCount: AtomicInteger,
        analyzedSegmentCount: AtomicInteger,
        recordedDurationSeconds: AtomicInteger,
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): RecordedSegment {
        var segment = VideoSegmentRun(
            runId = runId,
            segmentIndex = segmentNumber,
            status = VideoRunStatus.Recording,
            durationSeconds = actualDuration
        )
        val segmentId = segmentRunDao.upsert(segment)
        segment = segment.copy(id = segmentId)

        try {
            onStatus(
                VideoExecutionStatusUpdate(
                    stage = VideoRunStatus.Recording,
                    runId = runId,
                    segmentIndex = segmentNumber,
                    segmentCount = segmentCount,
                    message = "Recording segment $segmentNumber/$segmentCount",
                    templateLabel = task.templateLabel,
                    segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                    captureIntervalSeconds = task.captureIntervalSeconds,
                    streamingEnabled = streamingOutputEnabled,
                    isRecordingActive = true,
                    isAnalysisActive = recordedSegmentCount.get() > analyzedSegmentCount.get(),
                    recordedSegmentCount = recordedSegmentCount.get(),
                    analyzedSegmentCount = analyzedSegmentCount.get(),
                    pendingSegmentCount = (recordedSegmentCount.get() - analyzedSegmentCount.get())
                        .coerceAtLeast(0),
                    recordedDurationSeconds = recordedDurationSeconds.get(),
                    remainingDurationSeconds = (task.plannedDurationSeconds - startOffsetSeconds)
                        .coerceAtLeast(0),
                    recordingSegmentIndex = segmentNumber
                )
            )

            val outputFile = File(outputRoot, "video_runs/run_${runId}_segment_${segmentNumber}.mp4")
            val recording = recorder.recordSegment(
                outputFile = outputFile,
                durationSeconds = actualDuration,
                samplingFps = task.plannedSamplingFps,
                frameProvider = latestFrameProvider
            )

            segment = segment.copy(
                localFilePath = recording.file.absolutePath,
                updatedAt = System.currentTimeMillis()
            )
            segmentRunDao.upsert(segment)

            return RecordedSegment(
                segment = segment,
                file = recording.file,
                segmentNumber = segmentNumber,
                durationSeconds = actualDuration,
                startOffsetSeconds = startOffsetSeconds
            )
        } catch (cancelled: CancellationException) {
            persistSegmentCancelled(segment)
            throw cancelled
        } catch (error: Exception) {
            persistSegmentFailure(segment, error)
            throw VideoProcessException(
                stage = VideoRunStatus.Recording,
                userMessage = "Failed to record the video segment: ${error.toUserMessage("Check the live stream state.")}",
                cause = error
            )
        }
    }

    private suspend fun analyzeRecordedSegment(
        recordedSegment: RecordedSegment,
        task: VideoProcessTaskDraft,
        segmentCount: Int,
        runId: Long,
        streamingOutputEnabled: Boolean,
        recordedSegmentCount: AtomicInteger,
        analyzedSegmentCount: AtomicInteger,
        recordedDurationSeconds: AtomicInteger,
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): SegmentExecutionResult {
        var segment = recordedSegment.segment

        try {
            onStatus(
                VideoExecutionStatusUpdate(
                    stage = VideoRunStatus.Uploading,
                    runId = runId,
                    segmentIndex = recordedSegment.segmentNumber,
                    segmentCount = segmentCount,
                    message = "Uploading segment ${recordedSegment.segmentNumber}/$segmentCount",
                    templateLabel = task.templateLabel,
                    segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                    captureIntervalSeconds = task.captureIntervalSeconds,
                    streamingEnabled = streamingOutputEnabled,
                    isRecordingActive = recordedSegmentCount.get() < segmentCount,
                    isAnalysisActive = true,
                    recordedSegmentCount = recordedSegmentCount.get(),
                    analyzedSegmentCount = analyzedSegmentCount.get(),
                    pendingSegmentCount = (recordedSegmentCount.get() - analyzedSegmentCount.get())
                        .coerceAtLeast(0),
                    recordedDurationSeconds = recordedDurationSeconds.get(),
                    remainingDurationSeconds = (task.plannedDurationSeconds - recordedSegment.startOffsetSeconds)
                        .coerceAtLeast(0),
                    activeStreamingSegmentIndex = recordedSegment.segmentNumber
                )
            )

            segment = segment.copy(
                status = VideoRunStatus.Uploading,
                updatedAt = System.currentTimeMillis()
            )
            segmentRunDao.upsert(segment)

            val fileId = try {
                uploadVideoFile(recordedSegment.file, task.plannedSamplingFps)
            } catch (error: Exception) {
                throw VideoProcessException(
                    stage = VideoRunStatus.Uploading,
                    userMessage = "Failed to upload the video segment: ${
                        error.toUserMessage("Check network access or API permissions.")
                    }",
                    cause = error
                )
            }

            segment = segment.copy(
                status = VideoRunStatus.Preprocessing,
                arkFileId = fileId,
                updatedAt = System.currentTimeMillis()
            )
            segmentRunDao.upsert(segment)

            onStatus(
                VideoExecutionStatusUpdate(
                    stage = VideoRunStatus.Preprocessing,
                    runId = runId,
                    segmentIndex = recordedSegment.segmentNumber,
                    segmentCount = segmentCount,
                    message = "Waiting for segment ${recordedSegment.segmentNumber}/$segmentCount preprocessing",
                    templateLabel = task.templateLabel,
                    segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                    captureIntervalSeconds = task.captureIntervalSeconds,
                    streamingEnabled = streamingOutputEnabled,
                    isRecordingActive = recordedSegmentCount.get() < segmentCount,
                    isAnalysisActive = true,
                    recordedSegmentCount = recordedSegmentCount.get(),
                    analyzedSegmentCount = analyzedSegmentCount.get(),
                    pendingSegmentCount = (recordedSegmentCount.get() - analyzedSegmentCount.get())
                        .coerceAtLeast(0),
                    recordedDurationSeconds = recordedDurationSeconds.get(),
                    remainingDurationSeconds = (task.plannedDurationSeconds - recordedSegment.startOffsetSeconds)
                        .coerceAtLeast(0),
                    activeStreamingSegmentIndex = recordedSegment.segmentNumber
                )
            )

            try {
                waitForFileReady(fileId)
            } catch (error: Exception) {
                throw VideoProcessException(
                    stage = VideoRunStatus.Preprocessing,
                    userMessage = "Failed to preprocess the video segment: ${
                        error.toUserMessage("Check the remote file status.")
                    }",
                    cause = error
                )
            }

            onStatus(
                VideoExecutionStatusUpdate(
                    stage = VideoRunStatus.Analyzing,
                    runId = runId,
                    segmentIndex = recordedSegment.segmentNumber,
                    segmentCount = segmentCount,
                    message = "Analyzing segment ${recordedSegment.segmentNumber}/$segmentCount",
                    templateLabel = task.templateLabel,
                    segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                    captureIntervalSeconds = task.captureIntervalSeconds,
                    streamingEnabled = streamingOutputEnabled,
                    streamingBuffer = "",
                    isStreamingActive = streamingOutputEnabled,
                    isRecordingActive = recordedSegmentCount.get() < segmentCount,
                    isAnalysisActive = true,
                    recordedSegmentCount = recordedSegmentCount.get(),
                    analyzedSegmentCount = analyzedSegmentCount.get(),
                    pendingSegmentCount = (recordedSegmentCount.get() - analyzedSegmentCount.get())
                        .coerceAtLeast(0),
                    recordedDurationSeconds = recordedDurationSeconds.get(),
                    remainingDurationSeconds = (task.plannedDurationSeconds - recordedSegment.startOffsetSeconds)
                        .coerceAtLeast(0),
                    activeStreamingSegmentIndex = recordedSegment.segmentNumber
                )
            )
            val analysisResult = try {
                analyzeVideoSegment(
                    fileId = fileId,
                    task = task,
                    segmentNumber = recordedSegment.segmentNumber,
                    segmentCount = segmentCount,
                    runId = runId,
                    streamingOutputEnabled = streamingOutputEnabled,
                    recordedSegmentCount = recordedSegmentCount.get(),
                    analyzedSegmentCount = analyzedSegmentCount.get(),
                    recordedDurationSeconds = recordedDurationSeconds.get(),
                    onStatus = onStatus
                )
            } catch (error: Exception) {
                throw VideoProcessException(
                    stage = VideoRunStatus.Analyzing,
                    userMessage = "Failed to analyze the video segment: ${error.toUserMessage("Check the model configuration or output.")}",
                    cause = error
                )
            }

            try {
                segment = segment.copy(
                    status = VideoRunStatus.Completed,
                    summary = analysisResult.summary,
                    conclusion = analysisResult.conclusion,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                segmentRunDao.upsert(segment)
            } catch (error: Exception) {
                throw VideoProcessException(
                    stage = VideoRunStatus.Completed,
                    userMessage = "Failed to save the segment result: ${error.toUserMessage("Check the local database state.")}",
                    cause = error
                )
            }

            return SegmentExecutionResult(segment, analysisResult)
        } catch (cancelled: CancellationException) {
            persistSegmentCancelled(segment)
            throw cancelled
        } catch (error: Exception) {
            persistSegmentFailure(segment, error)
            throw error
        }
    }

    private suspend fun requestPlanningResponse(userInput: String, frame: Bitmap?): DoubaoResponse {
        return if (frame != null) {
            apiService.analyzeImage(
                authorization = bearerToken(),
                request = DoubaoImageRequest(
                    model = planningModel,
                    input = listOf(
                        ImageMessage(
                            role = "system",
                            content = listOf(
                                ImageContentItem(
                                    type = "input_text",
                                    text = buildStructuredPlanningPrompt()
                                )
                            )
                        ),
                        ImageMessage(
                            role = "user",
                            content = buildPlanningContent(userInput, frame)
                        )
                    )
                )
            )
        } else {
            apiService.analyzeIntent(
                authorization = bearerToken(),
                request = DoubaoRequest(
                    model = planningModel,
                    input = listOf(
                        Message(
                            role = "system",
                            content = listOf(
                                ContentItem(
                                    type = "input_text",
                                    text = buildStructuredPlanningPrompt()
                                )
                            )
                        ),
                        Message(
                            role = "user",
                            content = listOf(
                                ContentItem(
                                    type = "input_text",
                                    text = userInput
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    private fun requireApiKey() {
        check(apiKey.isNotBlank()) {
            "API_KEY is missing. Set it in local.properties first."
        }
    }

    private fun bearerToken(): String = "Bearer $apiKey"

    private fun buildPlanningContent(userInput: String, frame: Bitmap?): List<ImageContentItem> {
        val items = mutableListOf<ImageContentItem>()
        if (frame != null) {
            items += ImageContentItem(
                type = "input_image",
                imageUrl = "data:image/jpeg;base64,${BitmapEncoding.toBase64(frame)}"
            )
        }
        items += ImageContentItem(type = "input_text", text = userInput)
        return items
    }

    private suspend fun uploadVideoFile(file: File, samplingFps: Int): String {
        val uploadSamplingFps = samplingFps.coerceIn(1, 5)
        val response = apiService.uploadFile(
            authorization = bearerToken(),
            purpose = "user_data".toRequestBody("text/plain".toMediaType()),
            preprocessConfigs = mapOf(
                "preprocess_configs[video][fps]" to uploadSamplingFps
                    .toString()
                    .toRequestBody("text/plain".toMediaType())
            ),
            file = MultipartBody.Part.createFormData(
                name = "file",
                filename = file.name,
                body = file.asRequestBody("video/mp4".toMediaType())
            )
        )
        return response.resolvedId()
            ?: error("File upload succeeded but file_id was missing.")
    }

    private suspend fun waitForFileReady(fileId: String) {
        repeat(FILE_POLL_ATTEMPTS) { attempt ->
            val file = apiService.getFile(bearerToken(), fileId)
            val status = file.status?.lowercase()
            if (
                status == null ||
                status == "active" ||
                status == "processed" ||
                status == "ready" ||
                status == "succeeded"
            ) {
                return
            }
            if (status == "failed") {
                error("Ark file preprocessing failed.")
            }
            delay(FILE_POLL_INTERVAL_MS)
            if (attempt == FILE_POLL_ATTEMPTS - 1) {
                error("Ark file preprocessing timed out.")
            }
        }
    }

    private suspend fun analyzeVideoSegment(
        fileId: String,
        task: VideoProcessTaskDraft,
        segmentNumber: Int,
        segmentCount: Int,
        runId: Long,
        streamingOutputEnabled: Boolean,
        recordedSegmentCount: Int,
        analyzedSegmentCount: Int,
        recordedDurationSeconds: Int,
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): VideoAnalysisResult {
        val request = DoubaoVideoRequest(
            model = videoModel,
            input = listOf(
                VideoMessage(
                    role = "user",
                    content = listOf(
                        VideoContentItem(
                            type = "input_video",
                            fileId = fileId
                        ),
                        VideoContentItem(
                            type = "input_text",
                            text = buildSegmentAnalysisPrompt(
                                task = task,
                                segmentNumber = segmentNumber,
                                segmentCount = segmentCount
                            )
                        )
                    )
                )
            )
        )

        val rawText = if (streamingOutputEnabled) {
            streamModelText(
                requestPayload = request.copy(stream = true),
                stage = VideoRunStatus.Analyzing,
                runId = runId,
                segmentIndex = segmentNumber,
                segmentCount = segmentCount,
                loadingMessage = "Analyzing segment $segmentNumber/$segmentCount",
                templateLabel = task.templateLabel,
                segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                captureIntervalSeconds = task.captureIntervalSeconds,
                streamingEnabled = true,
                isRecordingActive = recordedSegmentCount < segmentCount,
                isAnalysisActive = true,
                recordedSegmentCount = recordedSegmentCount,
                analyzedSegmentCount = analyzedSegmentCount,
                pendingSegmentCount = (recordedSegmentCount - analyzedSegmentCount).coerceAtLeast(0),
                recordedDurationSeconds = recordedDurationSeconds,
                activeStreamingSegmentIndex = segmentNumber,
                onStatus = onStatus
            )
        } else {
            val response = apiService.analyzeVideo(
                authorization = bearerToken(),
                request = request
            )
            response.requireOutputText("video segment analysis")
        }

        return ModelOutputParser.parseVideoAnalysis(rawText)
    }

    private suspend fun summarizeSegments(
        task: VideoProcessTaskDraft,
        results: List<SegmentExecutionResult>,
        runId: Long,
        segmentCount: Int,
        streamingOutputEnabled: Boolean,
        recordedSegmentCount: Int,
        analyzedSegmentCount: Int,
        recordedDurationSeconds: Int,
        segmentFeedbacks: List<VideoSegmentFeedback>,
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): VideoAnalysisResult {
        val summaryPayload = buildString {
            appendLine("你正在为一次分片视频分析任务生成最终汇总。")
            appendLine("任务目标：${task.userRequirement}")
            appendLine("场景参考：${task.sceneContext}")
            appendLine("汇总提示词：${task.finalSummaryPrompt}")
            appendLine("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            appendLine("timelineEvents 中每一项必须包含 timestampSeconds、title、detail、confidence。")
            appendLine("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            appendLine("confidence 优先返回 0 到 1 之间的数字；如果无法量化，也允许返回“高”“中”“低”。")
            results.forEach { result ->
                appendLine("第 ${result.segment.segmentIndex} 段摘要：${result.analysisResult.summary}")
                appendLine("第 ${result.segment.segmentIndex} 段结论：${result.analysisResult.conclusion}")
                result.analysisResult.timelineEvents.forEach { event ->
                    appendLine(
                        "事件 ${result.segment.segmentIndex}@${event.timestampSeconds}s: " +
                            "${event.title} | ${event.detail}"
                    )
                }
            }
        }

        val request = DoubaoRequest(
            model = planningModel,
            input = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        ContentItem(
                            type = "input_text",
                            text = summaryPayload
                        )
                    )
                )
            )
        )

        val rawText = if (streamingOutputEnabled) {
            streamModelText(
                requestPayload = request.copy(stream = true),
                stage = VideoRunStatus.Summarizing,
                runId = runId,
                segmentIndex = segmentCount,
                segmentCount = segmentCount,
                loadingMessage = "Generating final summary",
                templateLabel = task.templateLabel,
                segmentDurationSeconds = task.plannedSegmentDurationSeconds,
                captureIntervalSeconds = task.captureIntervalSeconds,
                streamingEnabled = true,
                isRecordingActive = false,
                isAnalysisActive = true,
                recordedSegmentCount = recordedSegmentCount,
                analyzedSegmentCount = analyzedSegmentCount,
                pendingSegmentCount = 0,
                recordedDurationSeconds = recordedDurationSeconds,
                activeStreamingSegmentIndex = segmentCount,
                segmentFeedbacks = segmentFeedbacks,
                onStatus = onStatus
            )
        } else {
            val response = apiService.analyzeIntent(
                authorization = bearerToken(),
                request = request
            )
            response.requireOutputText("video summary")
        }

        return ModelOutputParser.parseVideoAnalysis(rawText)
    }

    private suspend fun streamModelText(
        requestPayload: Any,
        stage: VideoRunStatus,
        runId: Long,
        segmentIndex: Int,
        segmentCount: Int,
        loadingMessage: String,
        templateLabel: String?,
        segmentDurationSeconds: Int,
        captureIntervalSeconds: Int,
        streamingEnabled: Boolean,
        isRecordingActive: Boolean,
        isAnalysisActive: Boolean,
        recordedSegmentCount: Int,
        analyzedSegmentCount: Int,
        pendingSegmentCount: Int,
        recordedDurationSeconds: Int,
        activeStreamingSegmentIndex: Int,
        segmentFeedbacks: List<VideoSegmentFeedback> = emptyList(),
        onStatus: suspend (VideoExecutionStatusUpdate) -> Unit
    ): String {
        var streamedText = ""
        val finalText = streamingClient.streamResponse(
            authorization = bearerToken(),
            requestPayload = requestPayload
        ) { event ->
            when (event) {
                is ArkResponseStreamEvent.OutputTextDelta -> {
                    streamedText = event.fullText
                    onStatus(
                        VideoExecutionStatusUpdate(
                            stage = stage,
                            runId = runId,
                            segmentIndex = segmentIndex,
                            segmentCount = segmentCount,
                            message = loadingMessage,
                            templateLabel = templateLabel,
                            segmentDurationSeconds = segmentDurationSeconds,
                            captureIntervalSeconds = captureIntervalSeconds,
                            streamingBuffer = event.fullText,
                            streamingEnabled = streamingEnabled,
                            isStreamingActive = true,
                            isRecordingActive = isRecordingActive,
                            isAnalysisActive = isAnalysisActive,
                            recordedSegmentCount = recordedSegmentCount,
                            analyzedSegmentCount = analyzedSegmentCount,
                            pendingSegmentCount = pendingSegmentCount,
                            recordedDurationSeconds = recordedDurationSeconds,
                            activeStreamingSegmentIndex = activeStreamingSegmentIndex,
                            segmentFeedbacks = segmentFeedbacks
                        )
                    )
                }

                is ArkResponseStreamEvent.OutputTextDone -> {
                    streamedText = event.fullText
                    onStatus(
                        VideoExecutionStatusUpdate(
                            stage = stage,
                            runId = runId,
                            segmentIndex = segmentIndex,
                            segmentCount = segmentCount,
                            message = loadingMessage,
                            templateLabel = templateLabel,
                            segmentDurationSeconds = segmentDurationSeconds,
                            captureIntervalSeconds = captureIntervalSeconds,
                            streamingBuffer = event.fullText,
                            streamingEnabled = streamingEnabled,
                            isStreamingActive = false,
                            isRecordingActive = isRecordingActive,
                            isAnalysisActive = isAnalysisActive,
                            recordedSegmentCount = recordedSegmentCount,
                            analyzedSegmentCount = analyzedSegmentCount,
                            pendingSegmentCount = pendingSegmentCount,
                            recordedDurationSeconds = recordedDurationSeconds,
                            activeStreamingSegmentIndex = activeStreamingSegmentIndex,
                            segmentFeedbacks = segmentFeedbacks
                        )
                    )
                }

                is ArkResponseStreamEvent.Completed -> {
                    streamedText = event.fullText
                }
            }
        }
        return finalText.ifBlank { streamedText }
    }

    private suspend fun mergeSegmentVideos(
        runId: Long,
        task: VideoProcessTaskDraft,
        results: List<SegmentExecutionResult>,
        outputRoot: File
    ): String {
        val segmentFiles = results
            .sortedBy { it.segment.segmentIndex }
            .mapNotNull { result ->
                result.segment.localFilePath
                    ?.takeIf(String::isNotBlank)
                    ?.let(::File)
                    ?.takeIf(File::exists)
            }
        if (segmentFiles.isEmpty()) {
            throw IllegalStateException("No local segment files are available for merging.")
        }

        val outputFile = File(outputRoot, "video_runs/run_${runId}_merged.mp4")
        return segmentMerger.mergeSegments(
            segmentFiles = segmentFiles,
            outputFile = outputFile,
            samplingFps = task.plannedSamplingFps
        ).absolutePath
    }

    private suspend fun persistSegmentFailure(segment: VideoSegmentRun, error: Throwable) {
        runCatching {
            segmentRunDao.upsert(
                segment.copy(
                    status = VideoRunStatus.Failed,
                    errorMessage = error.toUserMessage("Execution failed."),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun persistSegmentCancelled(segment: VideoSegmentRun) {
        runCatching {
            segmentRunDao.upsert(
                segment.copy(
                    status = VideoRunStatus.Cancelled,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun combineSegmentResults(
        results: List<SegmentExecutionResult>,
        timelineEvents: List<VideoTimelineEvent>
    ): VideoAnalysisResult {
        val summary = results.joinToString("\n") { result ->
            "第 ${result.segment.segmentIndex} 段：${result.analysisResult.summary}"
        }
        val conclusion = results.joinToString("\n") { result ->
            "第 ${result.segment.segmentIndex} 段结论：${result.analysisResult.conclusion}"
        }
        val rawResponse = results.joinToString("\n\n") { result ->
            result.analysisResult.rawResponse
        }
        return VideoAnalysisResult(
            summary = summary,
            conclusion = conclusion,
            timelineEvents = timelineEvents,
            rawResponse = rawResponse
        )
    }

    private fun DoubaoResponse.requireOutputText(operation: String): String {
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

    private fun Throwable.toUserMessage(defaultMessage: String): String {
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

    private fun buildSegmentAnalysisPrompt(
        task: VideoProcessTaskDraft,
        segmentNumber: Int,
        segmentCount: Int
    ): String {
        return buildString {
            appendLine("你是一名视频理解助手。")
            appendLine("任务目标：${task.userRequirement}")
            appendLine("场景参考：${task.sceneContext}")
            appendLine("当前片段：${segmentNumber}/${segmentCount}")
            appendLine("片段时长：${task.plannedSegmentDurationSeconds} 秒")
            appendLine("采样间隔：${task.captureIntervalSeconds} 秒")
            appendLine("片段分析提示词：${task.segmentAnalysisPrompt}")
            appendLine("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            appendLine("timelineEvents 中每一项必须包含 timestampSeconds、title、detail、confidence。")
            appendLine("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            appendLine("confidence 优先返回 0 到 1 之间的数字；如果无法量化，也允许返回“高”“中”“低”。")
            appendLine("timestampSeconds 必须仅使用当前片段内的相对秒数。")
        }
    }

    private fun buildStructuredPlanningPrompt(): String {
        return """
            你需要将用户的视频分析意图转换为结构化执行计划。
            只返回 JSON。
            JSON 必须且只能包含以下字段：
            {
              "taskCategory": string,
              "strategyReason": string,
              "title": string,
              "userRequirement": string,
              "sceneContext": string,
              "recordingDurationSeconds": integer,
              "samplingFps": integer,
              "segmentDurationSeconds": integer,
              "captureIntervalSeconds": integer,
              "segmentCount": integer,
              "segmentAnalysisPrompt": string,
              "finalSummaryPrompt": string,
              "confirmationNotes": string,
              "autoStartStreamingOutput": boolean,
              "finalSummaryEnabled": boolean
            }
            约束要求：
            - taskCategory 只能是 long_horizon_summary、continuous_watch、short_burst_dense 之一。
            - recordingDurationSeconds 表示总观察时长。
            - samplingFps 表示模型采样密度，不是摄像头录制帧率。
            - segmentDurationSeconds 表示每次录制片段的时长。
            - captureIntervalSeconds 表示两次录制开始之间的间隔。
            - segmentCount 必须与 recordingDurationSeconds 和 captureIntervalSeconds 保持一致。
            - sceneContext 只描述稳定、可观察的场景事实。
            - segmentAnalysisPrompt 必须是给用户可直接编辑的简体中文提示词，用于指导单个片段分析。
            - finalSummaryPrompt 必须是给用户可直接编辑的简体中文提示词，用于指导全局汇总。
            - 两个提示词都要明确：JSON 字段名保持英文，字段值与说明文字使用简体中文。
            - confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用“高”“中”“低”。
            - timelineEvents 中的每一项都必须包含 timestampSeconds、title、detail、confidence。
            - 这些参数只是推荐值，用户后续还可以手动调整。
        """.trimIndent()
    }

    companion object {
        private const val FILE_POLL_ATTEMPTS = 30
        private const val FILE_POLL_INTERVAL_MS = 2_000L
    }
}

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

private data class RecordedSegment(
    val segment: VideoSegmentRun,
    val file: File,
    val segmentNumber: Int,
    val durationSeconds: Int,
    val startOffsetSeconds: Int
)

private data class SegmentExecutionResult(
    val segment: VideoSegmentRun,
    val analysisResult: VideoAnalysisResult
)

private class VideoProcessException(
    val stage: VideoRunStatus,
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)








