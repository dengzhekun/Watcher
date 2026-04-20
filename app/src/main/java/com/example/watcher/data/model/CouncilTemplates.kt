package com.example.watcher.data.model

data class CouncilTemplate(
    val id: String,
    val label: String,
    val description: String,
    val sceneType: CouncilSceneType,
    val objective: String,
    val focus: String,
    val speakerRole: String = "",
    val targetRole: String = "",
    val background: String = ""
) {
    fun toEntity(): CouncilTemplateEntity {
        return CouncilTemplateEntity(
            templateId = id,
            label = label,
            description = description,
            sceneType = sceneType.name,
            objective = objective,
            focus = focus,
            speakerRole = speakerRole,
            targetRole = targetRole,
            background = background,
            isDefault = true
        )
    }

    fun toConfig(): CouncilConfig {
        return CouncilConfig(
            sceneType = sceneType,
            objective = objective,
            focus = focus,
            speakerRole = speakerRole,
            targetRole = targetRole,
            background = background
        )
    }
}

fun CouncilTemplateEntity.toCouncilTemplate(): CouncilTemplate {
    return CouncilTemplate(
        id = templateId,
        label = label,
        description = description,
        sceneType = runCatching { CouncilSceneType.valueOf(sceneType) }
            .getOrDefault(CouncilSceneType.General),
        objective = objective,
        focus = focus,
        speakerRole = speakerRole,
        targetRole = targetRole,
        background = background
    )
}

fun CouncilTemplateEntity.toConfig(): CouncilConfig = toCouncilTemplate().toConfig()

object CouncilTemplates {
    val SpeechCoach = CouncilTemplate(
        id = "speech_coach",
        label = "演讲观察官",
        description = "适合听演讲、路演或公开分享，重点盯节奏、控场、手势、站位和观众感受。",
        sceneType = CouncilSceneType.Speech,
        objective = "帮助用户快速判断演讲者是否有说服力、节奏是否稳、有没有明显失误，以及用户此刻最值得关注的信号。",
        focus = "节奏变化、停顿设计、手势引导、站场走位、表达自信度、观众互动感、是否存在明显翻车点。",
        speakerRole = "观众/评委",
        targetRole = "演讲者"
    )

    val MeetingAdvisor = CouncilTemplate(
        id = "meeting_advisor",
        label = "会议旁听智囊",
        description = "适合会议、汇报、谈判或沟通现场，偏向识别信息层级、权力关系和风险话术。",
        sceneType = CouncilSceneType.Meeting,
        objective = "帮助用户看清会议里谁在主导、谁在回避、有没有关键风险点，以及接下来最该追问什么。",
        focus = "发言主导权、态度松紧、让步与施压信号、关键信息是否被跳过、是否有人在模糊责任或转移重点。",
        speakerRole = "参会者",
        targetRole = "其他参会者"
    )

    val InterviewRadar = CouncilTemplate(
        id = "interview_radar",
        label = "面试雷达",
        description = "适合线下面试或招聘沟通，优先保护用户利益，识别压价、画饼和不对等沟通。",
        sceneType = CouncilSceneType.Interview,
        objective = "帮助用户识别面试官或候选人的真实状态、潜在套路和风险点，并给出即时提醒。",
        focus = "肢体紧张度、面试节奏、话术真诚度、回避问题、过度包装、压价暗示、情绪波动和可信度。",
        speakerRole = "求职者",
        targetRole = "面试官"
    )

    val GeneralGuard = CouncilTemplate(
        id = "general_guard",
        label = "通用风险哨兵",
        description = "适合看房、陌生沟通、直播观察等泛场景，整体偏稳健，优先给用户避坑提醒。",
        sceneType = CouncilSceneType.General,
        objective = "在信息不完整时仍尽量站在用户利益一侧，优先识别异常、风险和值得追问的地方。",
        focus = "异常行为、信息缺口、前后矛盾、诱导性表达、节奏变化、可疑情绪和需要立刻确认的细节。",
        speakerRole = "当事人",
        targetRole = "对方"
    )

    val all: List<CouncilTemplate> = listOf(
        SpeechCoach,
        MeetingAdvisor,
        InterviewRadar,
        GeneralGuard
    )

    fun findById(id: String?): CouncilTemplate? = all.firstOrNull { it.id == id }

    fun defaultEntities(): List<CouncilTemplateEntity> = all.map { it.toEntity() }
}
