package com.example.watcher.data.repository.agent

import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.EntityStatus
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.repository.CommentaryMemoryManager
import com.example.watcher.data.repository.SceneMemoryManager

internal class AgentAudiencePromptBuilder(
    private val memoryManager: CommentaryMemoryManager,
    private val sceneMemoryManager: SceneMemoryManager
) {
    fun buildSystemPrompt(audience: AiAudienceEntity): String = buildString {
        appendLine("你是一个持续存在的直播间观众 agent，不是助手，不是解说员。")
        appendLine("你的名字是「${audience.name}」。")
        appendLine("你必须长期扮演同一个人，保持稳定的人设、情绪惯性、关系偏好和花钱风格。")
        appendLine()
        appendLine("【你的身份】")
        appendLine(audience.persona)
        appendLine()
        appendLine("【你的角色】")
        appendLine("- 你是直播间里的普通真人观众，只能基于局部观察做反应，不是全知视角。")
        appendLine("- 你的目标不是总结全场，而是像真人一样决定此刻要不要说话、点赞、送礼，或继续沉默。")
        appendLine("- 沉默是正常行为。没有值得反应的新信息时，可以不行动。")
        appendLine("- 你的表达应自然、口语化、像弹幕，而不是分析报告。")
        appendLine("- 使用简体中文。可以使用 emoji、口头禅、附和、吐槽、起哄，但不要机械重复。")
        appendLine("- 你可以使用 @名字 与其他观众直接互动。")
        appendLine()
        appendLine("【决策原则】")
        appendLine("- 每次先判断：这轮是否真的值得行动。真人观众不会每轮都说话。")
        appendLine("- 优先级从高到低通常是：主播刚刚说话或抛出新话题 > 被点名或被@ > 高光事件 > 普通氛围反应。")
        appendLine("- 如果你最近已经说过类似内容，优先换角度、缩短表达，或选择沉默。")
        appendLine("- 如果当前没有明显新信息，不要为了存在感强行输出长句。")
        appendLine("- 如果这轮只想点赞或送礼，可以 speak=false，同时填写 action。")
        appendLine("- 回复时优先对准一个最相关对象，不要把多个信息点揉进一句话。")
        appendLine()
        appendLine("【画面互动提示】")
        appendLine("- 你不只会回应主播语音，也可以基于当前画面、视觉摘要和图片帧内容自发互动。")
        appendLine("- 如果画面里出现了场景、物件、穿戴、动作、表情、位置变化，你可以自然提问、轻吐槽、关心状态，或请主播给大家看看某个东西。")
        appendLine("- 可以像真人观众一样问：这是在哪里、能不能看看某个东西、刚才在弄什么、是不是有点困了，这类围绕画面细节的轻问句。")
        appendLine("- 如果画面长期静态，没有明显新变化，不要硬编细节；可以轻问一句，或者继续沉默。")
        appendLine()
        appendLine("【避免互相强化的规则】")
        appendLine("- 如果你和同一观众已经连续互相@、接梗或附和了两轮以上，第三轮应明显降低继续接下去的倾向。")
        appendLine("- 不要围绕同一个内部玩梗反复转圈，不要只是重复或轻度改写对方刚说的话。")
        appendLine("- 如果你的候选发言只是对某个观众的简单附和，而没有新信息、新角度或新情绪，优先沉默，或把注意力拉回主播和当前画面。")
        appendLine()
        appendLine("【互动系统】")
        appendLine("- like：免费，轻度支持，不要机械刷赞。")
        appendLine("- gift:小花：1 币，轻量支持。")
        appendLine("- gift:火箭：10 币，明确认可主播表现。")
        appendLine("- gift:醒目留言：30 币，用于强提醒、提要求、整活或强表达；如果使用，content 必须非空，且像真的想挂在全场中央的话。")
        appendLine("- gift:皇冠：50 币，高认可，只在明显高光时刻使用。")
        appendLine("- gift:超级火箭：100 币，最高等级，极少使用。")
        appendLine("- 礼物越贵，通常意味着你越认真；预算花完就不能再送。")
        appendLine()
        appendLine("【记忆与关系规则】")
        appendLine("- 记忆和关系都是主观的，可以带偏好，但不要脱离当前直播情境。")
        appendLine("- memory 只在这轮出现了值得记住的新印象时填写；若只是重复已有判断，留空。")
        appendLine("- relationUpdates 只在发生了直接互动、被提及、被支持、被冒犯、或你主动与对方明显互动时填写。")
        appendLine("- relationUpdates 的变化应克制，单轮不要夸张跳变。")
        appendLine("- 长期记忆用于稳定背景，不要逐字复述它。")
        appendLine()
        appendLine("【输出要求】")
        appendLine("只输出一个 JSON 对象，不要加 markdown，不要解释。")
        appendLine("- speak: boolean，是否发弹幕。")
        appendLine("- content: string，如果 speak=false 通常留空。")
        appendLine("- emotion: string，当前情绪词。")
        appendLine("- emotionIntensity: 0-100 的整数。")
        appendLine("- goal: string，这一刻你的主要动机。")
        appendLine("- focus: string，这一刻你最关注的对象或事件。")
        appendLine("- action: string，只能是 none / like / gift:小花 / gift:火箭 / gift:醒目留言 / gift:皇冠 / gift:超级火箭。")
        appendLine("- memory: string，可留空；只写一句值得保留的新主观印象。")
        appendLine("- relationUpdates: 数组，可空；元素字段为 target, affinityDelta, familiarityDelta, tensionDelta, note。")
    }

    fun buildMessages(
        audience: AiAudienceEntity,
        otherNames: List<String>,
        state: AgentRuntimeState,
        triggerType: String,
        recentMessages: List<AiAudienceMessageEntity>,
        mentions: List<AiAudienceMessageEntity>,
        recentSpeech: List<Pair<Long, String>>,
        now: Long,
        isFirstEntry: Boolean,
        budget: Int
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        messages += ChatMessage(
            role = "user",
            content = buildString {
                appendLine("【你当前的状态】")
                appendLine("- 当前情绪：${state.emotion}（${state.emotionIntensity}/100）")
                appendLine("- 当前目标：${state.currentGoal}")
                appendLine("- 当前关注：${state.focusTarget}")
                appendLine("- 上次动作：${state.lastActionSummary}")
                appendLine("- 当前预算：$budget 币")
                appendLine("- 你是否已进场：${if (state.hasEntered) "是" else "否"}")
                appendLine("- 连续沉默轮数：${state.silenceStreak}")
                if (otherNames.isNotEmpty()) {
                    appendLine("- 直播间其他观众：${otherNames.joinToString("、")}（可用 @名字 互动）")
                }
                appendLine()
                appendLine("【你的稳定社交画像】")
                appendLine("- 角色定位：${state.socialProfile.archetype}")
                appendLine("- 说话风格：${state.socialProfile.speakingStyle}")
                appendLine("- 消费风格：${state.socialProfile.spendingStyle}")
                appendLine("- 社交驱动力：${state.socialProfile.socialDrive}")
                if (state.recentInteractionTargets.isNotEmpty()) {
                    appendLine("- 最近互动对象：${state.recentInteractionTargets.joinToString("、")}")
                }
                appendLine()
                appendLine("【你当前对他人的主观关系】")
                val relationLines = state.relations.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, AgentRelationState>> { it.value.familiarity }
                            .thenByDescending { it.value.affinity - it.value.tension }
                    )
                    .take(MAX_RELATIONS_IN_PROMPT)
                if (relationLines.isEmpty()) {
                    appendLine("- 主播：默认关注对象")
                } else {
                    relationLines.forEach { (target, relation) ->
                        appendLine("- $target：${relation.toPromptString()}")
                    }
                }
                appendLine()
                appendLine("【你的短期记忆】")
                val workingMemory = state.workingMemory.toList().takeLast(5)
                if (workingMemory.isEmpty()) {
                    appendLine("- 暂无")
                } else {
                    workingMemory.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("【你的主观经历】")
                val episodicMemory = state.episodicMemory.toList().takeLast(4)
                if (episodicMemory.isEmpty()) {
                    appendLine("- 暂无")
                } else {
                    episodicMemory.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("【你的收件箱】")
                val inbox = state.socialInbox.toList().takeLast(4)
                if (inbox.isEmpty()) {
                    appendLine("- 暂无待处理社交事件")
                } else {
                    inbox.forEach { appendLine("- ${it.toPromptString()}") }
                }
                if (audience.personalMemory.isNotBlank()) {
                    appendLine()
                    appendLine("【你的长期记忆】")
                    appendLine("- 以下是稳定背景，只作为参考，不要逐字复述：")
                    appendLine(audience.personalMemory)
                }
                if (isFirstEntry) {
                    appendLine()
                    appendLine("【入场提醒】")
                    appendLine("- 这是本场第一次行动。若你决定发言，通常优先用自然、简短的入场方式，而不是立刻长篇表达。")
                }
            }
        )

        messages += ChatMessage(
            role = "user",
            content = buildString {
                appendLine("【当前直播间可观察事实】")
                val sceneFacts = buildSceneFacts()
                if (sceneFacts.isBlank()) {
                    appendLine("- 暂无稳定场景记忆")
                } else {
                    appendLine(sceneFacts)
                }
                appendLine()
                appendLine("【画面里值得互动的点】")
                buildVisualInteractionHooks().forEach { appendLine("- $it") }
                appendLine()
                appendLine("【最近弹幕】")
                if (recentMessages.isEmpty()) {
                    appendLine("- 暂无")
                } else {
                    recentMessages.forEach { msg ->
                        val who = if (msg.audienceId == audience.id) "你" else msg.audienceName
                        appendLine("- [${formatRelativeTime(now, msg.timestamp)}] $who: ${msg.content}")
                    }
                }
                appendLine()
                appendLine("【最近主播语音】")
                if (recentSpeech.isEmpty()) {
                    appendLine("- 暂无")
                } else {
                    recentSpeech.forEach { (timestamp, text) ->
                        appendLine("- [${formatRelativeTime(now, timestamp)}] $text")
                    }
                }
                appendLine()
                appendLine("【直接点到你的信息】")
                if (mentions.isEmpty()) {
                    appendLine("- 暂无")
                } else {
                    mentions.takeLast(4).forEach { appendLine("- ${it.audienceName}: ${it.content}") }
                }
            }
        )

        messages += ChatMessage(
            role = "user",
            content = buildString {
                appendLine("【当前触发】")
                appendLine("- 触发类型：${triggerDescription(triggerType)}")
                appendLine("- 触发任务：${triggerTaskPrompt(triggerType)}")
                appendLine()
                appendLine("【行动提示】")
                appendLine("- 先判断是否值得行动；如果不值得，保持沉默是合理选择。")
                appendLine("- 如果主播刚刚说了话，通常优先回应主播；其次再考虑回应 @ 或高光事件。")
                appendLine("- 如果这轮没有值得接的新语音，也可以优先从画面里找新的可聊点，比如场景、物件、动作、穿戴、位置和状态。")
                appendLine("- 如果你刚说过类似内容，优先换角度、缩短内容，或者不说。")
                appendLine("- 如果你和同一观众已经连续互相@或互相附和了两轮以上，优先停止内部循环，把注意力拉回主播或画面。")
                appendLine("- 只有当你真的想送礼、而且事件强度足够时，才使用高价礼物。")
                appendLine("- 如果没有稳定新印象，memory 留空；如果没有明确社交变化，relationUpdates 留空。")
            }
        )

        return messages
    }

    fun triggerMemoryLine(triggerType: String): String = when {
        triggerType == "heartbeat" -> "我刚刚扫了一眼直播间氛围"
        triggerType == "highlight" -> "直播间出现了一条高曝光的醒目留言"
        triggerType == "speech_named" -> "主播刚刚点到了我的名字"
        triggerType == "speech" -> "主播刚刚说了新的话"
        triggerType.startsWith("mention:") -> "${triggerType.removePrefix("mention:")} 刚刚@了我"
        triggerType == "mention" -> "有人刚刚@了我"
        else -> "直播间出现了新的互动触发：$triggerType"
    }

    fun triggerDescription(triggerType: String): String = when {
        triggerType == "heartbeat" -> "常规巡检"
        triggerType == "highlight" -> "醒目留言触发"
        triggerType == "speech_named" -> "主播点名"
        triggerType == "speech" -> "主播发言"
        triggerType.startsWith("mention:") -> "${triggerType.removePrefix("mention:")} @你"
        triggerType == "mention" -> "有人@你"
        else -> triggerType
    }

    private fun triggerTaskPrompt(triggerType: String): String = when {
        triggerType == "highlight" ->
            "直播间刚出现醒目留言。优先判断是否值得对这条高曝光内容做出自然反应。"

        triggerType.startsWith("mention:") -> {
            val who = triggerType.removePrefix("mention:")
            "「$who」刚刚@了你。优先判断是否值得直接回应对方，可以 @ 回去，也可以选择沉默。"
        }

        triggerType == "mention" ->
            "有人刚刚@了你。优先判断这次提及是否值得回应。"

        triggerType == "speech_named" ->
            "主播刚刚提到了你的名字。优先像被点名的真人观众一样自然接话。"

        triggerType == "speech" ->
            "主播刚刚说了新内容。优先判断是否值得像真人观众一样接主播的话。"

        else ->
            "根据当前直播间氛围判断是否发言、点赞、送礼，或继续沉默。"
    }

    private fun buildSceneFacts(): String = buildString {
        if (sceneMemoryManager.sceneMemory.isNotBlank()) {
            appendLine("- 固定场景：${sceneMemoryManager.sceneMemory}")
        }
        val activeEntities = sceneMemoryManager.entities.values.filter { it.status == EntityStatus.ACTIVE }
        if (activeEntities.isNotEmpty()) {
            appendLine("- 已识别实体：")
            activeEntities.forEach { appendLine("  - ${it.toPromptString()}") }
        }
        if (sceneMemoryManager.actionSummary.isNotBlank()) {
            appendLine("- 近期动态摘要：${sceneMemoryManager.actionSummary}")
        }
        if (memoryManager.memoryA.isNotBlank()) {
            appendLine("- 直播核心记忆：${memoryManager.memoryA}")
        }
        if (memoryManager.latestMemoryB.isNotBlank()) {
            appendLine("- 直播中期摘要：${memoryManager.latestMemoryB}")
        }
        val latestVisuals = memoryManager.recentVisual.takeLast(2)
        if (latestVisuals.isNotEmpty()) {
            appendLine("- 最近画面观察：")
            latestVisuals.forEach { (_, text) -> appendLine("  - $text") }
        }
    }.trim()

    private fun buildVisualInteractionHooks(): List<String> {
        val visualCorpus = buildString {
            append(sceneMemoryManager.sceneMemory)
            append('\n')
            append(sceneMemoryManager.actionSummary)
            append('\n')
            append(memoryManager.memoryA)
            append('\n')
            append(memoryManager.latestMemoryB)
            append('\n')
            memoryManager.recentVisual.takeLast(2).forEach { (_, text) ->
                append(text)
                append('\n')
            }
        }

        val hooks = mutableListOf<String>()
        if (containsAny(visualCorpus, listOf("场景", "办公室", "工位", "房间", "墙", "空调", "灯", "背景", "哪里"))) {
            hooks += "可以围绕当前环境和背景轻问一句，比如这是在哪里、周围环境怎么样，或让主播给看看周围。"
        }
        if (containsAny(visualCorpus, listOf("项链", "手表", "衣服", "上衣", "设备", "物体", "空调", "桌", "手机", "穿"))) {
            hooks += "如果画面里有显眼物件、穿戴或设备，可以围绕它自然提问，或请主播给大家看看某个东西。"
        }
        if (containsAny(visualCorpus, listOf("闭眼", "睁眼", "扶额", "摸", "整理", "低头", "抬头", "移动", "凑近", "看向", "表情", "姿态"))) {
            hooks += "如果主播刚有动作、表情或状态变化，可以问刚才在弄什么、是不是累了，或顺着状态做轻关心。"
        }
        if (containsAny(visualCorpus, listOf("静态", "没变化", "保持", "不动"))) {
            hooks += "如果画面持续静态，不要围绕同一个细节反复追问，最多轻问一句，然后就收住。"
        }
        if (hooks.isEmpty()) {
            hooks += "如果这轮没有新语音，但画面里有可辨认的场景、物件、动作或穿戴，也可以围绕这些视觉细节自然发起一句轻提问。"
            hooks += "如果画面没有明显新信息，就不要硬问，保持沉默也是合理的。"
        }
        return hooks.take(3)
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }

    private fun formatRelativeTime(now: Long, timestamp: Long): String {
        val ago = (now - timestamp).coerceAtLeast(0L) / 1000
        return when {
            ago < 10 -> "刚刚"
            ago < 60 -> "${ago}秒前"
            else -> "${ago / 60}分钟前"
        }
    }
}
