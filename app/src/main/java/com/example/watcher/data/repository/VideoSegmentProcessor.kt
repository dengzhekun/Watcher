package com.example.watcher.data.repository

import android.graphics.Bitmap
import com.example.watcher.data.local.VideoSegmentRunDao
import com.example.watcher.data.model.VideoAnalysisResult
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoRunStatus
import com.example.watcher.data.model.VideoSegmentFeedback
import com.example.watcher.data.model.VideoSegmentRun
import com.example.watcher.data.model.VideoTimelineEvent
import com.example.watcher.data.remote.ArkResponseStreamEvent
import com.example.watcher.data.remote.ArkStreamingClient
import com.example.watcher.data.remote.ContentItem
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.DoubaoVideoRequest
import com.example.watcher.data.remote.Message
import com.example.watcher.data.remote.VideoContentItem
import com.example.watcher.data.remote.VideoMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

internal class VideoSegmentProcessor(
    private val apiService: DoubaoApiService,
    private val segmentRunDao: VideoSegmentRunDao,
    private val recorder: MjpegVideoRecorder,
    private val segmentMerger: VideoSegmentMerger,
    private val streamingClient: ArkStreamingClient,
    private val planningModel: String,
    private val videoModel: String,
    private val apiKey: String
) {
    fun requireApiKey() {
        check(apiKey.isNotBlank()) {
            "API_KEY is missing. Set it in local.properties first."
        }
    }

    suspend fun recordSegmentClip(
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

    suspend fun analyzeRecordedSegment(
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

    suspend fun summarizeSegments(
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

    suspend fun mergeSegmentVideos(
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

    fun combineSegmentResults(
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

    private fun bearerToken(): String = "Bearer $apiKey"

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

    private companion object {
        private const val FILE_POLL_ATTEMPTS = 30
        private const val FILE_POLL_INTERVAL_MS = 2_000L
    }
}
