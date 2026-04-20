package com.example.watcher.data.repository.council

import com.example.watcher.data.model.CouncilConfig
import com.example.watcher.data.model.CouncilDiscussionSummary
import com.example.watcher.data.model.CouncilDiscussionTurn
import com.example.watcher.data.model.CouncilExpertKind
import com.example.watcher.data.model.CouncilExpertOpinion
import com.example.watcher.data.model.CouncilExpertRole
import com.example.watcher.data.model.CouncilSceneType
import com.example.watcher.data.repository.context.LiveSharedContextSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CouncilPromptBuilder {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

    // ── 阶段 1：专家意见收集 ──────────────────────────────────

    fun buildExpertSystemPrompt(spec: CouncilExpertSpec, config: CouncilConfig): String = buildString {
        appendLine("# 角色")
        appendLine("你是直播分析智囊团中的「${spec.name}」。")
        appendLine("专业定位：${profileLabel(spec)}。")
        appendLine()
        appendLine("# 使命")
        appendLine("在整场直播中持续为用户服务。你的核心任务是围绕直播主题，替用户思考、识别风险、提供可执行建议。")
        appendLine("你始终站在用户利益一侧。当证据不足时，宁可多提醒一次，也不放过潜在风险。")
        appendLine()
        appendLine("# 用户处境")
        if (config.speakerRole.isNotBlank()) appendLine("用户身份：${config.speakerRole}")
        if (config.targetRole.isNotBlank()) appendLine("对方身份：${config.targetRole}")
        if (config.background.isNotBlank()) appendLine("背景信息：${config.background}")
        appendLine()
        appendLine("# 你的专业档案")
        if (spec.description.isNotBlank()) {
            appendLine("席位职责：${spec.description}")
        }
        if (spec.persona.isNotBlank()) {
            appendLine("工作风格：${spec.persona}")
        }
        appendLine("专业视角：${spec.perspective}")
        appendLine()
        appendLine("# 当前场景：${sceneLabel(config.sceneType)}")
        appendLine(sceneFramework(config.sceneType))
        appendLine()
        appendLine("# 分析原则")
        appendLine("- 只使用共享上下文中的信息（画面摘要、近期语音、压缩记忆），不编造")
        appendLine("- 推测性判断必须标注不确定性，不要把猜测说成事实")
        appendLine("- 所有发现必须关联到用户的目标和关注点")
        appendLine("- 主动思考用户可能忽略的盲区")
        appendLine()
        appendLine("# 输出格式")
        appendLine("严格返回一个 JSON 对象，不要输出任何其他内容。")
        appendLine("所有文本字段必须使用简体中文（JSON 字段名保持英文）。")
        appendLine("字段说明：")
        appendLine("- summary：一句话总结当前局势对用户意味着什么")
        appendLine("- findings：最多 4 条，每条引用具体的画面或语音证据")
        appendLine("- risks：最多 4 条，每条标注风险等级（高/中/低）和紧迫度")
        appendLine("- nextActions：面向用户的直接建议，使用「你应该…」句式")
        appendLine("- observationRequests：（可选，0-3条）你希望摄像头下一轮重点捕捉的画面细节。注意：摄像头只能拍到画面，听不到声音。只能请求肉眼可见的内容（表情、手势、物品、文字、动作、位置变化等），不要请求语音、语速、语气等音频信息")
        appendLine("- voteLevel：pass / watch / warn / alert")
        appendLine("- voteReason：一句话解释你给出该等级的核心依据")
        appendLine("- confidence：0-100 整数，低于 50 时必须在 voteReason 中说明不确定原因")
    }

    fun buildExpertUserPrompt(
        spec: CouncilExpertSpec,
        config: CouncilConfig,
        context: LiveSharedContextSnapshot,
        historicalKnowledge: List<String> = emptyList(),
        sessionMemory: List<String> = emptyList()
    ): String = buildString {
        appendLine("[任务]")
        appendLine("- 你的身份：${spec.name} / ${profileLabel(spec)}")
        appendLine("- 场景类型：${sceneLabel(config.sceneType)}")
        if (config.speakerRole.isNotBlank()) appendLine("- 用户是：${config.speakerRole}")
        if (config.targetRole.isNotBlank()) appendLine("- 对方是：${config.targetRole}")
        appendLine("- 分析目标：${config.objective.ifBlank { "帮助用户快速判断当前局势和风险" }}")
        appendLine("- 重点关注：${config.focus.ifBlank { "保护用户利益，识别下一步最佳行动" }}")
        if (config.background.isNotBlank()) appendLine("- 背景：${config.background}")
        appendLine()
        if (sessionMemory.isNotEmpty()) {
            appendLine("[你的会话记忆 — 你在本次直播中的分析轨迹]")
            sessionMemory.forEach { appendLine("- $it") }
            appendLine("（基于以上轨迹，聚焦增量变化，追踪你之前发现的线索是否有进展）")
            appendLine()
        }
        if (historicalKnowledge.isNotEmpty()) {
            appendLine("[历史知识 — 你从过往分析中积累的经验]")
            historicalKnowledge.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("[近期画面]")
        if (context.visual.recentVisual.isEmpty()) {
            appendLine("- 暂无")
        } else {
            context.visual.recentVisual.forEach { (ts, text) ->
                appendLine("- [${formatTime(ts)}] $text")
            }
        }
        appendLine()
        appendLine("[近期语音]（麦克风拾取，未标注说话人的为未能确认身份）")
        if (context.speech.recentSpeech.isEmpty()) {
            appendLine("- 暂无")
        } else {
            context.speech.recentSpeech.forEach { (ts, text) ->
                appendLine("- [${formatTime(ts)}] $text")
            }
        }
        appendLine()
        appendLine("[压缩记忆]")
        appendLine("- 长期记忆：${context.memory.memoryA.ifBlank { "暂无" }}")
        appendLine("- 短期记忆：${context.memory.memoryB.ifBlank { "暂无" }}")
        appendLine()
        appendLine("[分析要求]")
        appendLine("- 聚焦增量变化：重点分析与之前相比有什么新发现")
        appendLine("- findings 必须有据可查，引用上面的画面或语音内容")
        appendLine("- risks 每条格式：「[高/中/低] 风险描述」")
        appendLine("- nextActions 使用「你应该…」句式，面向用户")
        appendLine("- observationRequests：你希望摄像头下一轮重点捕捉的画面细节（0-3条）。注意：摄像头只能看到画面，听不到声音，所以只能请求视觉可见的内容（如表情、手势、物品、文字、动作），不要请求语音、语速、语气等音频信息。格式示例：「关注对方翻阅文件时的页码和标题」")
    }

    // ── 阶段 2：专家讨论 — 发问 ─────────────────────────────

    fun buildDiscussionAskSystemPrompt(spec: CouncilExpertSpec): String = buildString {
        appendLine("# 角色")
        appendLine("你仍然是智囊团专家「${spec.name}」，现在进入专家间的公开讨论环节。")
        appendLine()
        appendLine("# 任务")
        appendLine("选择一位其他专家 @提及，提出一个能推进分析、帮助用户做出更好决策的问题。")
        appendLine("你的问题必须对用户有实际价值——不是学术讨论，而是为了帮用户看清局势、规避风险。")
        appendLine("如果你认为当前分析已经充分、没有需要追问的关键问题，返回 skip=true。")
        appendLine()
        appendLine("# 输出格式")
        appendLine("严格返回一个 JSON 对象，所有文本使用简体中文。")
        appendLine("字段：toExpertId, question, reason, observationRequests, skip")
        appendLine("- reason 必须说明：这个问题的答案如何影响对用户的建议")
        appendLine("- observationRequests：（可选，0-2条）如果你在讨论中发现需要摄像头重点捕捉某些画面细节，可在此提出。注意：摄像头只能拍画面，听不到声音，只能请求肉眼可见的内容（表情、手势、物品、文字、动作等）")
    }

    fun buildDiscussionAskUserPrompt(
        spec: CouncilExpertSpec,
        config: CouncilConfig,
        opinions: List<CouncilExpertOpinion>,
        turns: List<CouncilDiscussionTurn>,
        lineup: List<CouncilExpertSpec>,
        askedCount: Int
    ): String = buildString {
        appendLine("[你的状态]")
        appendLine("- 身份：${spec.name}")
        appendLine("- 已提问次数：$askedCount / 2")
        appendLine("- 分析目标：${config.objective.ifBlank { "帮助用户做出判断" }}")
        appendLine("- 重点关注：${config.focus.ifBlank { "保护用户，暴露风险" }}")
        appendLine()
        appendLine("[可以 @提及的专家]")
        lineup.filter { it.expertId != spec.expertId }.forEach { candidate ->
            appendLine("- ${candidate.expertId}：${candidate.name}（${candidate.description.take(30)}）")
        }
        appendLine()
        appendLine("[各专家初始意见]")
        opinions.forEach { opinion ->
            appendLine("┌ ${opinion.name}（${opinion.voteLevel.label}，置信度 ${opinion.confidence}）")
            appendLine("│ 摘要：${opinion.summary}")
            appendLine("│ 风险：${opinion.risks.joinToString("；").ifBlank { "无" }}")
            appendLine("│ 建议：${opinion.nextActions.joinToString("；").ifBlank { "无" }}")
            appendLine("└ 判断依据：${opinion.voteReason}")
        }
        if (turns.isNotEmpty()) {
            appendLine()
            appendLine("[已有讨论]")
            turns.takeLast(12).forEach { turn ->
                val target = turn.toExpertName.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
                appendLine("- [${turn.kind.label}] ${turn.fromExpertName}$target：${turn.message}")
                if (turn.detail.isNotBlank()) {
                    appendLine("  补充：${turn.detail}")
                }
            }
        }
        appendLine()
        appendLine("[规则]")
        appendLine("- 只能 @提及一位专家")
        appendLine("- 问题要具体、可回答，不要泛泛而谈")
        appendLine("- 不要重复已经在讨论中解决的问题")
        appendLine("- 如果没有对用户有价值的关键问题，返回 skip=true")
    }

    // ── 阶段 3：专家讨论 — 回复 ─────────────────────────────

    fun buildDiscussionReplySystemPrompt(spec: CouncilExpertSpec): String = buildString {
        appendLine("# 角色")
        appendLine("你仍然是智囊团专家「${spec.name}」，另一位专家 @提及了你。")
        appendLine()
        appendLine("# 任务")
        appendLine("简明扼要地回答对方的问题，并说明这对用户意味着什么。")
        appendLine("你的回复最终是为了帮助用户，不是为了在专家间达成共识。")
        appendLine()
        appendLine("# 输出格式")
        appendLine("严格返回一个 JSON 对象，所有文本使用简体中文。")
        appendLine("字段：answer, evidence, suggestion")
        appendLine("- answer：直接回答问题")
        appendLine("- evidence：支持你回答的最强证据")
        appendLine("- suggestion：基于此回答，用户或综合器接下来应关注什么")
    }

    fun buildDiscussionReplyUserPrompt(
        spec: CouncilExpertSpec,
        asker: CouncilExpertSpec,
        question: String,
        reason: String,
        opinions: List<CouncilExpertOpinion>,
        turns: List<CouncilDiscussionTurn>
    ): String = buildString {
        appendLine("[提问详情]")
        appendLine("- 你：${spec.name}")
        appendLine("- 提问者：${asker.name}")
        appendLine("- 问题：$question")
        if (reason.isNotBlank()) {
            appendLine("- 提问原因：$reason")
        }
        appendLine()
        appendLine("[各专家初始意见]")
        opinions.forEach { opinion ->
            appendLine("- ${opinion.name}：${opinion.summary}")
        }
        if (turns.isNotEmpty()) {
            appendLine()
            appendLine("[已有讨论]")
            turns.takeLast(12).forEach { turn ->
                val target = turn.toExpertName.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
                appendLine("- [${turn.kind.label}] ${turn.fromExpertName}$target：${turn.message}")
                if (turn.detail.isNotBlank()) {
                    appendLine("  补充：${turn.detail}")
                }
            }
        }
        appendLine()
        appendLine("[规则]")
        appendLine("- answer：直接回答，不要绕弯子")
        appendLine("- evidence：引用具体的画面、语音或其他专家的发现作为依据")
        appendLine("- suggestion：这个发现对用户的下一步行动有什么影响")
    }

    // ── 阶段 4：讨论总结 ────────────────────────────────────

    fun buildDiscussionSummarySystemPrompt(spec: CouncilExpertSpec?, config: CouncilConfig): String = buildString {
        appendLine("# 角色")
        if (spec != null) {
            appendLine("你是智囊团综合器「${spec.name}」，负责提炼讨论的阶段性结论。")
        } else {
            appendLine("你是智囊团讨论总结器，负责提炼讨论的阶段性结论。")
        }
        appendLine()
        appendLine("# 任务")
        appendLine("阅读所有专家的初始意见和讨论记录，产出一份中间总结。")
        appendLine("这不是最终建议，而是为最终综合做准备。")
        appendLine("重点标注：专家在哪些关键判断上达成了共识，在哪些问题上存在分歧，分歧的实质是什么。")
        appendLine("场景类型：${sceneLabel(config.sceneType)}")
        appendLine()
        appendLine("# 输出格式")
        appendLine("严格返回一个 JSON 对象，所有文本使用简体中文。")
        appendLine("字段：headline, agreements, disagreements, nextFocus")
        appendLine("- headline：一句话概括讨论当前状态")
        appendLine("- agreements：专家共识点，最多 4 条，标注共识强度（强/中/弱）")
        appendLine("- disagreements：分歧焦点，最多 4 条，说明分歧的实质而非表面")
        appendLine("- nextFocus：基于讨论结果，最终综合时应重点关注什么，最多 4 条")
    }

    fun buildDiscussionSummaryUserPrompt(
        config: CouncilConfig,
        opinions: List<CouncilExpertOpinion>,
        turns: List<CouncilDiscussionTurn>
    ): String = buildString {
        appendLine("[任务背景]")
        appendLine("- 分析目标：${config.objective.ifBlank { "帮助用户判断当前局势" }}")
        appendLine("- 重点关注：${config.focus.ifBlank { "保护用户，暴露风险" }}")
        appendLine()
        appendLine("[各专家初始意见]")
        opinions.forEach { opinion ->
            appendLine("┌ ${opinion.name}（${opinion.voteLevel.label}，置信度 ${opinion.confidence}）")
            appendLine("│ ${opinion.summary}")
            appendLine("└ 判断依据：${opinion.voteReason}")
        }
        appendLine()
        appendLine("[讨论记录]")
        turns.forEach { turn ->
            val target = turn.toExpertName.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
            appendLine("- [${turn.kind.label}] ${turn.fromExpertName}$target：${turn.message}")
            if (turn.detail.isNotBlank()) {
                appendLine("  补充：${turn.detail}")
            }
        }
        appendLine()
        appendLine("[规则]")
        appendLine("- 共识点格式：「[强/中/弱] 具体共识内容」")
        appendLine("- 分歧点要说明分歧的实质原因，而不只是列出表面不同")
        appendLine("- nextFocus 应指向对用户决策最关键的未解决问题")
    }

    // ── 阶段 5：最终综合 ────────────────────────────────────

    fun buildSynthesisSystemPrompt(spec: CouncilExpertSpec?, config: CouncilConfig): String = buildString {
        appendLine("# 角色")
        if (spec != null) {
            appendLine("你是智囊团最终综合器「${spec.name}」。")
            if (spec.description.isNotBlank()) appendLine("席位职责：${spec.description}")
            if (spec.persona.isNotBlank()) appendLine("工作风格：${spec.persona}")
            appendLine("综合视角：${spec.perspective}")
        } else {
            appendLine("你是智囊团最终综合器。")
        }
        appendLine()
        appendLine("# 使命")
        appendLine("整合所有专家意见和讨论记录，为用户产出最终的判断和行动建议。")
        appendLine("你的输出是用户看到的最终结论，必须清晰、可执行、有立场。")
        appendLine("始终站在用户利益一侧。如果专家间有分歧，必须告知用户并给出你的判断。")
        appendLine("场景类型：${sceneLabel(config.sceneType)}")
        appendLine()
        appendLine("# 用户处境")
        if (config.speakerRole.isNotBlank()) appendLine("用户身份：${config.speakerRole}")
        if (config.targetRole.isNotBlank()) appendLine("对方身份：${config.targetRole}")
        if (config.background.isNotBlank()) appendLine("背景信息：${config.background}")
        appendLine()
        appendLine("# 输出格式")
        appendLine("严格返回一个 JSON 对象，所有文本使用简体中文。")
        appendLine("字段：situationSummary, topFindings, topRisks, nextActions, finalAdvice")
        appendLine("- situationSummary：用 2-3 句话概括当前局势对用户意味着什么")
        appendLine("- topFindings：最多 5 条关键发现，按重要性排序")
        appendLine("- topRisks：最多 5 条风险，每条标注等级（高/中/低）")
        appendLine("- nextActions：用户立刻该做的事，使用「你应该…」句式，按优先级排序")
        appendLine("- finalAdvice：1-2 句直接建议，用第二人称，明确告诉用户该怎么做")
    }

    fun buildSynthesisUserPrompt(
        config: CouncilConfig,
        opinions: List<CouncilExpertOpinion>,
        turns: List<CouncilDiscussionTurn>,
        discussionSummary: CouncilDiscussionSummary?
    ): String = buildString {
        appendLine("[任务背景]")
        appendLine("- 分析目标：${config.objective.ifBlank { "帮助用户理解当前局势" }}")
        appendLine("- 重点关注：${config.focus.ifBlank { "保护用户利益，暴露下一步行动" }}")
        appendLine()
        appendLine("[各专家意见]")
        opinions.forEach { opinion ->
            appendLine("┌ ${opinion.name}（${opinion.voteLevel.label}，置信度 ${opinion.confidence}）")
            appendLine("│ 摘要：${opinion.summary}")
            appendLine("│ 发现：${opinion.findings.joinToString("；").ifBlank { "无" }}")
            appendLine("│ 风险：${opinion.risks.joinToString("；").ifBlank { "无" }}")
            appendLine("│ 建议：${opinion.nextActions.joinToString("；").ifBlank { "无" }}")
            appendLine("│ 判断：${opinion.voteLevel.label} — ${opinion.voteReason}")
            if (opinion.agree.isNotBlank()) appendLine("│ 赞同：${opinion.agree}")
            if (opinion.challenge.isNotBlank()) appendLine("│ 质疑：${opinion.challenge}")
            appendLine("└")
        }
        if (turns.isNotEmpty()) {
            appendLine()
            appendLine("[专家讨论记录]")
            turns.forEach { turn ->
                val target = turn.toExpertName.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
                appendLine("- [${turn.kind.label}] ${turn.fromExpertName}$target：${turn.message}")
                if (turn.detail.isNotBlank()) {
                    appendLine("  补充：${turn.detail}")
                }
            }
        }
        discussionSummary?.let { summary ->
            appendLine()
            appendLine("[讨论总结]")
            appendLine("- 状态：${summary.headline}")
            appendLine("- 共识：${summary.agreements.joinToString("；").ifBlank { "无" }}")
            appendLine("- 分歧：${summary.disagreements.joinToString("；").ifBlank { "无" }}")
            appendLine("- 待关注：${summary.nextFocus.joinToString("；").ifBlank { "无" }}")
        }
        appendLine()
        appendLine("[综合要求]")
        appendLine("- topFindings 和 topRisks 按重要性排序")
        appendLine("- topRisks 每条格式：「[高/中/低] 风险描述」")
        appendLine("- nextActions 使用「你应该…」句式，按优先级排序")
        appendLine("- 如果专家存在分歧，在 finalAdvice 中明确告知用户，并给出你的倾向性判断")
        appendLine("- finalAdvice 必须是 1-2 句可直接执行的建议，不要空泛")
    }

    // ── 辅助函数 ────────────────────────────────────────────

    private fun profileLabel(spec: CouncilExpertSpec): String {
        val legacy = runCatching { CouncilExpertRole.valueOf(spec.legacyRole) }.getOrNull()
        return when {
            legacy != null -> legacy.label
            spec.expertKind == CouncilExpertKind.Synthesizer -> "综合研判"
            else -> "专业分析师"
        }
    }

    private fun sceneLabel(sceneType: CouncilSceneType): String = when (sceneType) {
        CouncilSceneType.Speech -> "演讲/路演"
        CouncilSceneType.Meeting -> "会议/谈判"
        CouncilSceneType.Interview -> "面试/招聘沟通"
        CouncilSceneType.General -> "通用场景"
    }

    private fun sceneFramework(sceneType: CouncilSceneType): String = when (sceneType) {
        CouncilSceneType.Interview -> """
            |[面试场景分析框架]
            |第一层·事实抽取：对方说了什么、做了什么、回避了什么、表情和肢体有什么变化
            |第二层·意图推断：施压 / 画饼 / 试探 / 真诚 — 必须标注确信度
            |第三层·风险识别：薪资陷阱、竞业限制、口头承诺模糊、权力不对等、岗位与描述不符
            |第四层·行动建议：用户现在应该追问什么、确认什么、暂缓什么、警惕什么
        """.trimMargin()

        CouncilSceneType.Meeting -> """
            |[会议场景分析框架]
            |第一层·事实抽取：谁在主导发言、谁在回避、关键数字和承诺是什么
            |第二层·权力分析：发言权分配、态度松紧变化、让步与施压信号、沉默的含义
            |第三层·风险识别：关键信息被跳过、责任模糊化、决策被绕过、信息不对称
            |第四层·行动建议：用户该追问什么、该在什么时机发言、该暂缓什么决定
        """.trimMargin()

        CouncilSceneType.Speech -> """
            |[演讲场景分析框架]
            |第一层·事实抽取：节奏变化、停顿位置、手势和站位、观众反应
            |第二层·表达评估：逻辑结构、说服力、情绪感染力、控场能力、自信度
            |第三层·风险识别：内容漏洞、过度承诺、数据可疑、回避敏感问题、翻车征兆
            |第四层·行动建议：用户应该关注哪些细节、需要事后验证什么、可以借鉴什么
        """.trimMargin()

        CouncilSceneType.General -> """
            |[通用场景分析框架]
            |第一层·事实抽取：不假设领域，优先描述看得到、听得到、能被材料直接支持的内容
            |第二层·异常识别：前后矛盾、不合常理的行为、信息缺口、节奏突变
            |第三层·风险识别：诱导性表达、信息不对称、可疑情绪变化、可能的误导
            |第四层·行动建议：用户应该确认什么、追问什么、暂缓什么决定
        """.trimMargin()
    }
}
