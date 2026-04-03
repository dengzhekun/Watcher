package com.example.watcher.data.model

data class VideoTaskTemplate(
    val id: String,
    val label: String,
    val description: String,
    val taskCategory: String,
    val strategyReason: String = "",
    val userRequirement: String,
    val sceneContext: String,
    val segmentAnalysisPrompt: String,
    val finalSummaryPrompt: String,
    val recordingDurationSeconds: Int,
    val segmentDurationSeconds: Int,
    val captureIntervalSeconds: Int,
    val samplingFps: Int = VideoProcessTaskDraft.DEFAULT_SAMPLING_FPS,
    val autoStartStreamingOutput: Boolean = true,
    val finalSummaryEnabled: Boolean = true
) {
    fun toDraft(createdAt: Long = System.currentTimeMillis()): VideoProcessTaskDraft {
        return VideoProcessTaskDraft(
            templateId = id,
            templateLabel = label,
            taskCategory = taskCategory,
            strategyReason = strategyReason,
            title = label,
            userInput = label,
            userRequirement = userRequirement,
            sceneContext = sceneContext,
            segmentAnalysisPrompt = segmentAnalysisPrompt,
            finalSummaryPrompt = finalSummaryPrompt,
            plannedDurationSeconds = recordingDurationSeconds,
            plannedSamplingFps = samplingFps,
            plannedSegmentDurationSeconds = segmentDurationSeconds,
            captureIntervalSeconds = captureIntervalSeconds,
            autoStartStreamingOutput = autoStartStreamingOutput,
            finalSummaryEnabled = finalSummaryEnabled,
            confirmationNotes = description,
            createdAt = createdAt
        ).normalized()
    }

    fun toEntity(): VideoTemplateEntity {
        return VideoTemplateEntity(
            templateId = id,
            label = label,
            description = description,
            taskCategory = taskCategory,
            strategyReason = strategyReason,
            userRequirement = userRequirement,
            sceneContext = sceneContext,
            segmentAnalysisPrompt = segmentAnalysisPrompt,
            finalSummaryPrompt = finalSummaryPrompt,
            recordingDurationSeconds = recordingDurationSeconds,
            segmentDurationSeconds = segmentDurationSeconds,
            captureIntervalSeconds = captureIntervalSeconds,
            samplingFps = samplingFps,
            autoStartStreamingOutput = autoStartStreamingOutput,
            finalSummaryEnabled = finalSummaryEnabled,
            isDefault = true
        )
    }
}

fun VideoTemplateEntity.toVideoTaskTemplate(): VideoTaskTemplate {
    return VideoTaskTemplate(
        id = templateId,
        label = label,
        description = description,
        taskCategory = taskCategory,
        strategyReason = strategyReason,
        userRequirement = userRequirement,
        sceneContext = sceneContext,
        segmentAnalysisPrompt = segmentAnalysisPrompt,
        finalSummaryPrompt = finalSummaryPrompt,
        recordingDurationSeconds = recordingDurationSeconds,
        segmentDurationSeconds = segmentDurationSeconds,
        captureIntervalSeconds = captureIntervalSeconds,
        samplingFps = samplingFps,
        autoStartStreamingOutput = autoStartStreamingOutput,
        finalSummaryEnabled = finalSummaryEnabled
    )
}

object VideoTaskTemplates {
    val ThirdPersonRecord = VideoTaskTemplate(
        id = "third_person_record",
        label = "第三视角记录",
        description = "每 60 秒录制 2 秒，适合长时间低频采样，并在结束后汇总整体变化。",
        taskCategory = VideoTaskCategory.LongHorizonSummary.value,
        strategyReason = "适合固定视角下的长时间回顾，优先保留趋势变化，减少冗余片段。",
        userRequirement = "周期性记录场景中的关键变化、人物动作与潜在异常，并在结束后给出整体总结。",
        sceneContext = "默认以固定第三视角观察同一场景，关注持续变化、异常动作和长时间趋势。",
        segmentAnalysisPrompt = buildString {
            append("请只分析当前采样片段中的关键变化、人物动作和异常情况。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用“高”“中”“低”。")
            append("timestampSeconds 使用当前片段内的相对秒数。")
        },
        finalSummaryPrompt = buildString {
            append("请基于全部分片分析结果，总结整个观察周期中的变化趋势、关键事件和异常。")
            append("需要合并重复事件，按完整时间线输出。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用“高”“中”“低”。")
            append("timestampSeconds 使用整个任务时间线上的绝对秒数。")
        },
        recordingDurationSeconds = 21_600,
        segmentDurationSeconds = 2,
        captureIntervalSeconds = 60,
        samplingFps = 1,
        autoStartStreamingOutput = true,
        finalSummaryEnabled = true
    )

    val BehaviorHealthAssessment = VideoTaskTemplate(
        id = "behavior_health_assessment",
        label = "用户行为健康评估",
        description = "每 30 秒录制 2 秒，评估用户坐姿、用眼距离、活动频率等健康指标。",
        taskCategory = VideoTaskCategory.ContinuousWatch.value,
        strategyReason = "持续观察用户体态与习惯，需要稳定采样以发现不良姿势和久坐问题。",
        userRequirement = "定期采样画面，评估用户的坐姿是否端正、用眼距离是否过近、是否长时间保持同一姿势，并给出健康建议。",
        sceneContext = "固定视角观察用户在桌前的工作或学习状态，关注体态、距离屏幕远近和活动频率。",
        segmentAnalysisPrompt = buildString {
            append("请分析当前片段中用户的体态和行为健康状况。重点关注：")
            append("1）坐姿是否端正（驼背、歪头、趴桌）；2）眼睛距离屏幕/书本是否过近；")
            append("3）是否长时间保持同一姿势未活动；4）是否有揉眼、伸懒腰等疲劳信号。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用\u201C高\u201D\u201C中\u201D\u201C低\u201D。")
            append("timestampSeconds 使用当前片段内的相对秒数。")
        },
        finalSummaryPrompt = buildString {
            append("请基于全部分片结果，综合评估用户在整个观察期内的行为健康状况。")
            append("包括：坐姿评分、用眼习惯评估、活动频率评估，并给出改善建议。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("timestampSeconds 使用整个任务时间线上的绝对秒数。")
        },
        recordingDurationSeconds = 3600,
        segmentDurationSeconds = 2,
        captureIntervalSeconds = 30,
        samplingFps = 1
    )

    val TenMinuteRecap = VideoTaskTemplate(
        id = "ten_minute_recap",
        label = "十分钟内都发生了什么",
        description = "每 15 秒录制 2 秒，高密度记录十分钟内的所有活动并汇总。",
        taskCategory = VideoTaskCategory.ShortBurstDense.value,
        strategyReason = "短时间高频采样，尽可能捕捉十分钟内的每个关键动作和事件。",
        userRequirement = "高频采样记录最近十分钟内发生的所有事情，包括人物动作、物品变化、进出情况，结束后按时间线完整汇总。",
        sceneContext = "固定视角观察场景，关注十分钟内发生的一切变化和事件。",
        segmentAnalysisPrompt = buildString {
            append("请详细记录当前片段中发生的所有事件和变化。包括：")
            append("人物的进出和动作、物品的移动和变化、任何值得注意的细节。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用\u201C高\u201D\u201C中\u201D\u201C低\u201D。")
            append("timestampSeconds 使用当前片段内的相对秒数。")
        },
        finalSummaryPrompt = buildString {
            append("请基于全部分片结果，按时间线汇总十分钟内发生的所有事件。")
            append("合并重复事件，突出关键节点，给出完整的事件回顾。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("timestampSeconds 使用整个任务时间线上的绝对秒数。")
        },
        recordingDurationSeconds = 600,
        segmentDurationSeconds = 2,
        captureIntervalSeconds = 15,
        samplingFps = 1
    )

    val FocusAnalysis = VideoTaskTemplate(
        id = "focus_analysis",
        label = "用户专注度分析",
        description = "每 20 秒录制 2 秒，分析用户注意力集中程度和分心情况。",
        taskCategory = VideoTaskCategory.ContinuousWatch.value,
        strategyReason = "需要持续采样以判断用户的注意力变化趋势和分心频率。",
        userRequirement = "定期采样分析用户的专注程度，检测是否出现走神、玩手机、频繁转头、发呆等分心行为，并统计专注时长占比。",
        sceneContext = "固定视角观察用户在桌前的工作或学习状态，关注面部朝向、手部动作和注意力焦点。",
        segmentAnalysisPrompt = buildString {
            append("请分析当前片段中用户的专注状态。重点关注：")
            append("1）用户视线是否集中在工作/学习区域；2）是否在使用手机或其他无关设备；")
            append("3）是否频繁转头、东张西望；4）是否出现发呆、打哈欠等走神迹象；")
            append("5）手部是否在进行与任务相关的操作。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用\u201C高\u201D\u201C中\u201D\u201C低\u201D。")
            append("timestampSeconds 使用当前片段内的相对秒数。")
        },
        finalSummaryPrompt = buildString {
            append("请基于全部分片结果，综合评估用户在整个观察期内的专注度。")
            append("包括：专注时长占比、分心次数和原因分析、专注度变化趋势，并给出提升建议。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("timestampSeconds 使用整个任务时间线上的绝对秒数。")
        },
        recordingDurationSeconds = 1800,
        segmentDurationSeconds = 2,
        captureIntervalSeconds = 20,
        samplingFps = 1
    )

    val BehaviorSuggestion = VideoTaskTemplate(
        id = "behavior_suggestion",
        label = "用户行为建议",
        description = "每 45 秒录制 2 秒，观察用户日常行为习惯并给出优化建议。",
        taskCategory = VideoTaskCategory.LongHorizonSummary.value,
        strategyReason = "中低频采样观察用户的行为模式和习惯，积累足够样本后给出综合建议。",
        userRequirement = "长期观察用户的行为习惯，包括工作节奏、休息频率、物品整理、空间利用等方面，结束后给出行为优化建议。",
        sceneContext = "固定视角观察用户的日常活动场景，关注行为模式、习惯和效率。",
        segmentAnalysisPrompt = buildString {
            append("请观察当前片段中用户的行为习惯。记录：")
            append("1）当前在做什么活动；2）工作/活动节奏是否合理；")
            append("3）桌面和周围环境的整理状态；4）是否有低效或可改善的行为模式。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用\u201C高\u201D\u201C中\u201D\u201C低\u201D。")
            append("timestampSeconds 使用当前片段内的相对秒数。")
        },
        finalSummaryPrompt = buildString {
            append("请基于全部分片结果，分析用户的行为模式和习惯，给出具体可行的优化建议。")
            append("涵盖：时间管理、工作节奏、环境整理、效率提升等方面。")
            append("只返回 JSON，字段为 summary、conclusion、timelineEvents。")
            append("timelineEvents 每一项必须包含 timestampSeconds、title、detail、confidence。")
            append("JSON 字段名保持英文，字段值与说明文字请使用简体中文。")
            append("timestampSeconds 使用整个任务时间线上的绝对秒数。")
        },
        recordingDurationSeconds = 7200,
        segmentDurationSeconds = 2,
        captureIntervalSeconds = 45,
        samplingFps = 1
    )

    val all: List<VideoTaskTemplate> = listOf(
        ThirdPersonRecord,
        BehaviorHealthAssessment,
        TenMinuteRecap,
        FocusAnalysis,
        BehaviorSuggestion
    )

    fun findById(id: String?): VideoTaskTemplate? = all.firstOrNull { it.id == id }

    fun defaultEntities(): List<VideoTemplateEntity> = all.map { it.toEntity() }
}
