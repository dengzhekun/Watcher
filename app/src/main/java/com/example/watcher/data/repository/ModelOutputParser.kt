package com.example.watcher.data.repository

import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.model.CheckResult
import com.example.watcher.data.model.IntentResult
import com.example.watcher.data.model.MonitorDecision
import com.example.watcher.data.model.MonitorMode
import com.example.watcher.data.model.TargetTrigger
import com.example.watcher.data.model.VideoAnalysisResult
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoTaskCategory
import com.example.watcher.data.model.VideoTaskPlan
import com.example.watcher.data.model.VideoTimelineEvent
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import kotlin.math.roundToInt

object ModelOutputParser {
    private val gson = Gson()
    private val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
    private val arabicDurationRegex = Regex("""(\d+(?:\.\d+)?)\s*(小时|分钟|分|秒)""")
    private val chineseDurationRegex = Regex("""([零一二两三四五六七八九十百半]+)\s*(小时|分钟|分|秒)""")

    fun extractJson(text: String): String {
        codeBlockRegex.find(text)?.let { return it.groupValues[1].trim() }

        val objectStart = text.indexOf('{')
        val objectEnd = text.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1).trim()
        }

        return text.trim()
    }

    fun parseIntentResult(
        rawText: String,
        userInput: String,
        baseFrameBase64: String?,
        baselineSource: BaselineSource,
        hasImage: Boolean
    ): IntentResult {
        val payload = tryParse<IntentPayload>(rawText)

        val requirement = payload?.userRequirement?.trim().takeUnless { it.isNullOrBlank() }
            ?: userInput.trim().ifBlank { "监看当前画面" }
        val sceneDescription = payload?.originalSceneDescription?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: "暂无场景描述。"
        val interval = (payload?.checkIntervalSeconds ?: IntentResult.DEFAULT_INTERVAL_SECONDS)
            .coerceIn(IntentResult.MIN_INTERVAL_SECONDS, IntentResult.MAX_INTERVAL_SECONDS)
        val title = payload?.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: requirement.take(IntentResult.MAX_TITLE_LENGTH)
        val monitorMode = payload?.monitorMode.toMonitorMode(
            baselineSource = baselineSource,
            userInput = userInput,
            hasImage = hasImage
        )
        val targetTrigger = payload?.targetTrigger.toTargetTrigger(
            userInput = userInput,
            monitorMode = monitorMode
        )
        val effectiveBaselineSource = payload?.baselineSource.toBaselineSource(
            default = baselineSource,
            hasImage = hasImage
        )
        val promptTemplate = payload?.promptTemplate?.trim().takeUnless { it.isNullOrBlank() }
            ?: IntentResult.buildFallbackPrompt(
                requirement = requirement,
                sceneDescription = sceneDescription,
                monitorMode = monitorMode,
                targetTrigger = targetTrigger
            )

        return IntentResult(
            title = title,
            userInput = userInput,
            userRequirement = requirement,
            originalSceneDescription = sceneDescription,
            checkInterval = interval,
            promptTemplate = promptTemplate,
            baseFrameBase64 = baseFrameBase64,
            monitorMode = monitorMode,
            targetTrigger = targetTrigger,
            baselineSource = effectiveBaselineSource
        ).normalized()
    }

    fun parseMonitorDecision(rawText: String): MonitorDecision {
        val payload = tryParse<DetectionPayload>(rawText)

        if (payload != null) {
            val result = payload.status.toCheckResult()
            val summary = payload.summary?.trim().takeUnless { it.isNullOrBlank() }
                ?: payload.reason?.trim().takeUnless { it.isNullOrBlank() }
                ?: "模型返回结果：${result.toDisplayLabel()}"

            return MonitorDecision(
                result = result,
                summary = summary,
                reason = payload.reason?.trim().orEmpty(),
                confidence = parseConfidenceValue(payload.confidence),
                rawResponse = rawText
            )
        }

        val fallbackResult = when {
            rawText.contains("alert", ignoreCase = true) -> CheckResult.ALERT
            rawText.contains("warning", ignoreCase = true) -> CheckResult.WARNING
            rawText.contains("normal", ignoreCase = true) -> CheckResult.NORMAL
            else -> CheckResult.UNKNOWN
        }

        return MonitorDecision(
            result = fallbackResult,
            summary = rawText.trim().take(120).ifBlank { "模型响应无法解析" },
            reason = if (fallbackResult == CheckResult.UNKNOWN) {
                "响应内容不符合要求的 JSON 结构。"
            } else {
                ""
            },
            rawResponse = rawText
        )
    }

    fun parseVideoTaskPlan(rawText: String, userInput: String): VideoTaskPlan {
        val payload = tryParse<VideoPlanPayload>(rawText)
        val explicitDurationSeconds = extractRequestedDurationSeconds(userInput)
        val modelCategory = VideoTaskCategory.fromValue(payload?.taskCategory)
        val effectiveCategory = modelCategory ?: inferVideoTaskCategory(
            userInput = userInput,
            durationSeconds = explicitDurationSeconds ?: payload?.recordingDurationSeconds
        )
        val baseline = baselineFor(
            category = effectiveCategory,
            durationSeconds = explicitDurationSeconds ?: payload?.recordingDurationSeconds
        )
        val durationSeconds = explicitDurationSeconds
            ?: payload?.recordingDurationSeconds
            ?: baseline.recordingDurationSeconds
        val segmentDurationSeconds = payload?.segmentDurationSeconds ?: baseline.segmentDurationSeconds
        val captureIntervalSeconds = payload?.captureIntervalSeconds ?: baseline.captureIntervalSeconds
        val samplingFps = payload?.samplingFps ?: baseline.samplingFps

        val draft = VideoProcessTaskDraft(
            taskCategory = effectiveCategory.value,
            strategyReason = payload?.strategyReason?.trim().orEmpty().ifBlank {
                defaultStrategyReason(
                    category = effectiveCategory,
                    durationSeconds = durationSeconds,
                    segmentDurationSeconds = segmentDurationSeconds,
                    captureIntervalSeconds = captureIntervalSeconds
                )
            },
            title = payload?.title.orEmpty(),
            userInput = userInput,
            userRequirement = payload?.userRequirement.orEmpty(),
            sceneContext = payload?.sceneContext.orEmpty(),
            segmentAnalysisPrompt = payload?.segmentAnalysisPrompt.orEmpty(),
            finalSummaryPrompt = payload?.finalSummaryPrompt.orEmpty(),
            plannedDurationSeconds = durationSeconds,
            plannedSamplingFps = samplingFps,
            plannedSegmentDurationSeconds = segmentDurationSeconds,
            captureIntervalSeconds = captureIntervalSeconds,
            plannedSegmentCount = payload?.segmentCount ?: 0,
            autoStartStreamingOutput = payload?.autoStartStreamingOutput ?: false,
            finalSummaryEnabled = payload?.finalSummaryEnabled ?: true,
            confirmationNotes = payload?.confirmationNotes?.trim().orEmpty().ifBlank {
                defaultConfirmationNotes(
                    category = effectiveCategory,
                    durationSeconds = durationSeconds,
                    segmentDurationSeconds = segmentDurationSeconds,
                    captureIntervalSeconds = captureIntervalSeconds
                )
            }
        ).normalized()

        return VideoTaskPlan(
            templateId = draft.templateId,
            templateLabel = draft.templateLabel,
            taskCategory = draft.taskCategory,
            strategyReason = draft.strategyReason,
            title = draft.title,
            userRequirement = draft.userRequirement,
            sceneContext = draft.sceneContext,
            recordingDurationSeconds = draft.plannedDurationSeconds,
            samplingFps = draft.plannedSamplingFps,
            segmentDurationSeconds = draft.plannedSegmentDurationSeconds,
            captureIntervalSeconds = draft.captureIntervalSeconds,
            segmentCount = draft.plannedSegmentCount,
            segmentAnalysisPrompt = draft.segmentAnalysisPrompt,
            finalSummaryPrompt = draft.finalSummaryPrompt,
            autoStartStreamingOutput = draft.autoStartStreamingOutput,
            finalSummaryEnabled = draft.finalSummaryEnabled,
            confirmationNotes = draft.confirmationNotes
        )
    }

    fun parseVideoAnalysis(rawText: String): VideoAnalysisResult {
        val payload = tryParse<VideoAnalysisPayload>(rawText)

        if (payload != null) {
            val summary = payload.summary?.trim().takeUnless { it.isNullOrBlank() }
                ?: "已完成视频分析。"
            val conclusion = payload.conclusion?.trim().takeUnless { it.isNullOrBlank() }
                ?: summary
            val timeline = payload.timelineEvents.orEmpty()
                .mapNotNull { event ->
                    val title = event.title?.trim().takeUnless { it.isNullOrBlank() }
                        ?: return@mapNotNull null
                    VideoTimelineEvent(
                        timestampSeconds = (event.timestampSeconds ?: 0).coerceAtLeast(0),
                        title = title,
                        detail = event.detail?.trim().orEmpty(),
                        confidence = parseConfidenceValue(event.confidence)
                    )
                }
                .sortedBy { it.timestampSeconds }

            return VideoAnalysisResult(
                summary = summary,
                conclusion = conclusion,
                timelineEvents = timeline,
                rawResponse = rawText
            )
        }

        return VideoAnalysisResult(
            summary = rawText.trim().take(120).ifBlank { "模型响应无法解析" },
            conclusion = "视频结果未能按约定结构返回。",
            timelineEvents = emptyList(),
            rawResponse = rawText
        )
    }

    fun fractionToPercent(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 100f).roundToInt()
    }

    private fun parseConfidenceValue(raw: JsonElement?): Float? {
        if (raw == null || raw.isJsonNull) {
            return null
        }

        val text = runCatching { raw.asString }.getOrNull()?.trim().orEmpty()
        if (text.isBlank()) {
            return null
        }

        text.toDoubleOrNull()?.let { numeric ->
            return normalizeConfidenceValue(numeric)
        }

        if (text.endsWith("%")) {
            text.removeSuffix("%").trim().toDoubleOrNull()?.let { numeric ->
                return normalizeConfidenceValue(numeric / 100.0)
            }
        }

        val normalized = text.lowercase()
        return when {
            "high" in normalized || "高" in text -> 0.85f
            "medium" in normalized || "中" in text -> 0.60f
            "low" in normalized || "低" in text -> 0.30f
            else -> null
        }
    }

    private fun normalizeConfidenceValue(value: Double): Float? {
        if (!value.isFinite() || value < 0.0) {
            return null
        }

        val normalized = when {
            value <= 1.0 -> value
            value <= 100.0 -> value / 100.0
            else -> return null
        }
        return normalized.toFloat().coerceIn(0f, 1f)
    }

    private inline fun <reified T> tryParse(rawText: String): T? {
        return try {
            gson.fromJson(extractJson(rawText), T::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun extractRequestedDurationSeconds(userInput: String): Int? {
        val candidates = mutableListOf<Int>()

        arabicDurationRegex.findAll(userInput).forEach { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            convertToSeconds(value, match.groupValues[2])?.let(candidates::add)
        }
        chineseDurationRegex.findAll(userInput).forEach { match ->
            val value = parseChineseNumber(match.groupValues[1]) ?: return@forEach
            convertToSeconds(value, match.groupValues[2])?.let(candidates::add)
        }

        return candidates.maxOrNull()
    }

    private fun inferVideoTaskCategory(
        userInput: String,
        durationSeconds: Int?
    ): VideoTaskCategory {
        val hasSummaryIntent = SUMMARY_KEYWORDS.any(userInput::contains)
        val hasAlertIntent = ALERT_KEYWORDS.any(userInput::contains)

        return when {
            durationSeconds != null && durationSeconds <= 90 -> VideoTaskCategory.ShortBurstDense
            durationSeconds != null && durationSeconds >= 3_600 -> {
                if (hasAlertIntent) {
                    VideoTaskCategory.ContinuousWatch
                } else {
                    VideoTaskCategory.LongHorizonSummary
                }
            }

            hasSummaryIntent && (durationSeconds ?: 0) >= 1_800 -> VideoTaskCategory.LongHorizonSummary
            hasAlertIntent -> VideoTaskCategory.ContinuousWatch
            durationSeconds != null && durationSeconds <= 600 -> VideoTaskCategory.ContinuousWatch
            else -> VideoTaskCategory.LongHorizonSummary
        }
    }

    private fun baselineFor(
        category: VideoTaskCategory,
        durationSeconds: Int?
    ): VideoPlanningBaseline {
        val safeDuration = durationSeconds ?: VideoProcessTaskDraft.DEFAULT_DURATION_SECONDS
        return when (category) {
            VideoTaskCategory.ShortBurstDense -> {
                val targetSegments = 10
                val segmentDuration = (safeDuration.toDouble() / targetSegments.toDouble())
                    .roundToInt()
                    .coerceIn(VideoProcessTaskDraft.MIN_SEGMENT_DURATION_SECONDS, 12)
                VideoPlanningBaseline(
                    recordingDurationSeconds = safeDuration,
                    samplingFps = 2,
                    segmentDurationSeconds = segmentDuration,
                    captureIntervalSeconds = segmentDuration
                )
            }

            VideoTaskCategory.ContinuousWatch -> VideoPlanningBaseline(
                recordingDurationSeconds = safeDuration,
                samplingFps = if (safeDuration <= 300) 2 else 1,
                segmentDurationSeconds = 10,
                captureIntervalSeconds = 10
            )

            VideoTaskCategory.LongHorizonSummary -> VideoPlanningBaseline(
                recordingDurationSeconds = safeDuration,
                samplingFps = 1,
                segmentDurationSeconds = 10,
                captureIntervalSeconds = when {
                    safeDuration >= 7_200 -> 60
                    safeDuration >= 3_600 -> 45
                    safeDuration >= 1_800 -> 30
                    else -> 20
                }
            )
        }
    }

    private fun defaultStrategyReason(
        category: VideoTaskCategory,
        durationSeconds: Int,
        segmentDurationSeconds: Int,
        captureIntervalSeconds: Int
    ): String {
        val rhythm = "每隔 ${captureIntervalSeconds} 秒录制 ${segmentDurationSeconds} 秒"
        return when (category) {
            VideoTaskCategory.LongHorizonSummary ->
                "这是长时段回顾任务，建议用较低频率采样保留趋势变化，当前节奏为 $rhythm。"

            VideoTaskCategory.ContinuousWatch ->
                "这是连续观察任务，建议尽量覆盖全过程，当前节奏为 $rhythm。"

            VideoTaskCategory.ShortBurstDense ->
                "这是短时高密度观察任务，建议提高切片密度以尽快给出反馈，当前节奏为 $rhythm。"
        } + " 总观察时长约 ${durationSeconds} 秒。"
    }

    private fun defaultConfirmationNotes(
        category: VideoTaskCategory,
        durationSeconds: Int,
        segmentDurationSeconds: Int,
        captureIntervalSeconds: Int
    ): String {
        val categoryLabel = when (category) {
            VideoTaskCategory.LongHorizonSummary -> "长时段回顾"
            VideoTaskCategory.ContinuousWatch -> "连续观察"
            VideoTaskCategory.ShortBurstDense -> "短时高密度观察"
        }
        return "$categoryLabel：总时长 ${durationSeconds} 秒，每隔 ${captureIntervalSeconds} 秒录制 ${segmentDurationSeconds} 秒。"
    }

    private fun parseChineseNumber(raw: String): Double? {
        if (raw == "半") {
            return 0.5
        }

        var text = raw
        var suffixHalf = false
        if (text.endsWith("半")) {
            suffixHalf = true
            text = text.dropLast(1)
        }

        CHINESE_DIGIT_MAP[text]?.let { direct ->
            return direct + if (suffixHalf) 0.5 else 0.0
        }

        var result = 0
        var current = 0
        text.forEach { char ->
            when (char) {
                '十' -> {
                    result += (if (current == 0) 1 else current) * 10
                    current = 0
                }

                '百' -> {
                    result += (if (current == 0) 1 else current) * 100
                    current = 0
                }

                else -> {
                    val digit = CHINESE_DIGIT_MAP[char.toString()]?.toInt() ?: return null
                    current = digit
                }
            }
        }
        result += current

        if (result == 0 && text.isNotBlank()) {
            return null
        }
        return result + if (suffixHalf) 0.5 else 0.0
    }

    private fun convertToSeconds(value: Double, unit: String): Int? {
        val seconds = when (unit) {
            "小时" -> value * 3_600
            "分钟", "分" -> value * 60
            "秒" -> value
            else -> return null
        }
        return seconds.roundToInt().takeIf { it > 0 }
    }

    private fun String?.toCheckResult(): CheckResult {
        return when (this?.trim()?.uppercase()) {
            "ALERT" -> CheckResult.ALERT
            "WARNING" -> CheckResult.WARNING
            "NORMAL" -> CheckResult.NORMAL
            "UNKNOWN" -> CheckResult.UNKNOWN
            else -> CheckResult.UNKNOWN
        }
    }

    private fun CheckResult.toDisplayLabel(): String {
        return when (this) {
            CheckResult.NONE -> "未开始"
            CheckResult.ALERT -> "告警"
            CheckResult.WARNING -> "预警"
            CheckResult.NORMAL -> "正常"
            CheckResult.UNKNOWN -> "未知"
        }
    }

    private fun String?.toMonitorMode(
        baselineSource: BaselineSource,
        userInput: String,
        hasImage: Boolean
    ): MonitorMode {
        if (!hasImage || baselineSource == BaselineSource.CapturedFrame) {
            return MonitorMode.SceneBaseline
        }
        return when (this?.trim()?.lowercase()) {
            "referencetarget", "reference_target", "target", "target_presence", "目标检测", "参考目标检测" ->
                MonitorMode.ReferenceTarget
            "scenebaseline", "scene_baseline", "baseline", "场景基线", "场景基线比较" ->
                MonitorMode.SceneBaseline
            else -> inferMonitorModeFromText(userInput)
        }
    }

    private fun String?.toTargetTrigger(
        userInput: String,
        monitorMode: MonitorMode
    ): TargetTrigger {
        if (monitorMode != MonitorMode.ReferenceTarget) {
            return TargetTrigger.OnAppear
        }
        return when (this?.trim()?.lowercase()) {
            "ondisappear", "on_disappear", "disappear", "absence", "missing" ->
                TargetTrigger.OnDisappear
            "onappear", "on_appear", "appear", "presence" ->
                TargetTrigger.OnAppear
            else -> inferTargetTriggerFromText(userInput)
        }
    }

    private fun String?.toBaselineSource(default: BaselineSource, hasImage: Boolean): BaselineSource {
        if (!hasImage) {
            return BaselineSource.CapturedFrame
        }
        return when (this?.trim()?.lowercase()) {
            "uploadedimage", "uploaded_image", "upload" -> BaselineSource.UploadedImage
            "capturedframe", "captured_frame", "capture" -> BaselineSource.CapturedFrame
            else -> default
        }
    }

    private fun inferMonitorModeFromText(userInput: String): MonitorMode {
        val normalized = userInput.lowercase()
        return if (
            listOf(
                "this person",
                "this object",
                "this item",
                "look for",
                "recognize",
                "identify",
                "person in the photo",
                "object in the image"
            ).any(normalized::contains)
        ) {
            MonitorMode.ReferenceTarget
        } else {
            MonitorMode.SceneBaseline
        }
    }

    private fun inferTargetTriggerFromText(userInput: String): TargetTrigger {
        val normalized = userInput.lowercase()
        return if (
            listOf("leave", "missing", "gone", "disappear", "not appear", "absent")
                .any(normalized::contains)
        ) {
            TargetTrigger.OnDisappear
        } else {
            TargetTrigger.OnAppear
        }
    }

    private data class IntentPayload(
        @SerializedName(value = "title", alternate = ["taskTitle", "任务标题"])
        val title: String? = null,
        @SerializedName(value = "userRequirement", alternate = ["requirement", "用户需求"])
        val userRequirement: String? = null,
        @SerializedName(
            value = "originalSceneDescription",
            alternate = ["sceneDescription", "原始场景描述"]
        )
        val originalSceneDescription: String? = null,
        @SerializedName(
            value = "checkIntervalSeconds",
            alternate = ["checkInterval", "检查间隔秒数", "打点频率"]
        )
        val checkIntervalSeconds: Int? = null,
        @SerializedName(
            value = "promptTemplate",
            alternate = ["monitorPrompt", "提示词模板", "每次提示词"]
        )
        val promptTemplate: String? = null,
        @SerializedName(
            value = "monitorMode",
            alternate = ["mode", "baselineInterpretation", "monitor_type"]
        )
        val monitorMode: String? = null,
        @SerializedName(
            value = "targetTrigger",
            alternate = ["trigger", "referenceTrigger", "triggerMode"]
        )
        val targetTrigger: String? = null,
        @SerializedName(
            value = "baselineSource",
            alternate = ["imageSource", "referenceImageSource"]
        )
        val baselineSource: String? = null
    )

    private data class DetectionPayload(
        @SerializedName(value = "status", alternate = ["result", "状态"])
        val status: String? = null,
        @SerializedName(value = "summary", alternate = ["message", "摘要"])
        val summary: String? = null,
        @SerializedName(value = "reason", alternate = ["detail", "原因"])
        val reason: String? = null,
        val confidence: JsonElement? = null
    )

    private data class VideoPlanPayload(
        @SerializedName(value = "taskCategory", alternate = ["observationMode", "任务类别"])
        val taskCategory: String? = null,
        @SerializedName(value = "strategyReason", alternate = ["reason", "策略原因"])
        val strategyReason: String? = null,
        @SerializedName(value = "title", alternate = ["taskTitle", "任务标题"])
        val title: String? = null,
        @SerializedName(value = "userRequirement", alternate = ["requirement", "用户需求"])
        val userRequirement: String? = null,
        @SerializedName(value = "sceneContext", alternate = ["sceneDescription", "场景参考"])
        val sceneContext: String? = null,
        @SerializedName(
            value = "recordingDurationSeconds",
            alternate = ["durationSeconds", "录制时长秒数"]
        )
        val recordingDurationSeconds: Int? = null,
        @SerializedName(value = "samplingFps", alternate = ["sampleFps", "抽帧密度"])
        val samplingFps: Int? = null,
        @SerializedName(
            value = "segmentDurationSeconds",
            alternate = ["perSegmentDurationSeconds", "片段时长秒数"]
        )
        val segmentDurationSeconds: Int? = null,
        @SerializedName(
            value = "captureIntervalSeconds",
            alternate = ["intervalSeconds", "采样间隔秒数"]
        )
        val captureIntervalSeconds: Int? = null,
        @SerializedName(value = "segmentCount", alternate = ["segments", "片段数"])
        val segmentCount: Int? = null,
        @SerializedName(
            value = "segmentAnalysisPrompt",
            alternate = [
                "analysisPrompt",
                "segmentPrompt",
                "promptTemplate",
                "分片分析提示词",
                "分析提示词"
            ]
        )
        val segmentAnalysisPrompt: String? = null,
        @SerializedName(
            value = "finalSummaryPrompt",
            alternate = [
                "summaryPrompt",
                "finalPrompt",
                "分片汇总提示词",
                "最终汇总提示词"
            ]
        )
        val finalSummaryPrompt: String? = null,
        @SerializedName(value = "confirmationNotes", alternate = ["notes", "确认说明"])
        val confirmationNotes: String? = null,
        @SerializedName(value = "autoStartStreamingOutput", alternate = ["streamingEnabled"])
        val autoStartStreamingOutput: Boolean? = null,
        @SerializedName(value = "finalSummaryEnabled")
        val finalSummaryEnabled: Boolean? = null
    )

    private data class VideoAnalysisPayload(
        @SerializedName(value = "summary", alternate = ["摘要"])
        val summary: String? = null,
        @SerializedName(value = "conclusion", alternate = ["结论"])
        val conclusion: String? = null,
        @SerializedName(value = "timelineEvents", alternate = ["时间线事件"])
        val timelineEvents: List<VideoAnalysisEventPayload>? = null
    )

    private data class VideoAnalysisEventPayload(
        @SerializedName(value = "timestampSeconds", alternate = ["time", "时间戳秒数"])
        val timestampSeconds: Int? = null,
        @SerializedName(value = "title", alternate = ["事件"])
        val title: String? = null,
        @SerializedName(value = "detail", alternate = ["说明", "detailText"])
        val detail: String? = null,
        @SerializedName(value = "confidence", alternate = ["置信度"])
        val confidence: JsonElement? = null
    )

    private data class VideoPlanningBaseline(
        val recordingDurationSeconds: Int,
        val samplingFps: Int,
        val segmentDurationSeconds: Int,
        val captureIntervalSeconds: Int
    )

    private val SUMMARY_KEYWORDS = listOf("干了什么", "都做了什么", "总结", "回顾", "复盘")
    private val ALERT_KEYWORDS = listOf("异常", "危险", "告警", "报警", "问题", "摔倒")
    private val CHINESE_DIGIT_MAP = mapOf(
        "零" to 0.0,
        "一" to 1.0,
        "二" to 2.0,
        "两" to 2.0,
        "三" to 3.0,
        "四" to 4.0,
        "五" to 5.0,
        "六" to 6.0,
        "七" to 7.0,
        "八" to 8.0,
        "九" to 9.0
    )
}
