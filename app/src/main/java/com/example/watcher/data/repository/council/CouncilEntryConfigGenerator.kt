package com.example.watcher.data.repository.council

import com.example.watcher.data.model.CouncilConfig
import com.example.watcher.data.model.CouncilEntryDraft
import com.example.watcher.data.model.CouncilEntryGeneratedConfig
import com.example.watcher.data.model.CouncilExpertEntity
import com.example.watcher.data.model.CouncilSceneType
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.toCouncilTemplate
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.google.gson.Gson
import java.util.Locale

internal class CouncilEntryConfigGenerator {
    private val gson = Gson()

    suspend fun generate(
        provider: OpenAiCompatibleProvider,
        draft: CouncilEntryDraft,
        templates: List<CouncilTemplateEntity>,
        availableExperts: List<CouncilExpertEntity> = emptyList()
    ): CouncilEntryGeneratedConfig {
        val systemPrompt = buildSystemPrompt(availableExperts)
        val messages = listOf(
            ChatMessage(
                role = "user",
                content = buildUserPrompt(draft, templates)
            )
        )
        val raw = provider.chat(systemPrompt = systemPrompt, messages = messages).trim()
        return parse(raw, draft, provider.displayName)
    }

    fun buildFallback(
        draft: CouncilEntryDraft,
        providerName: String = "本地回退"
    ): CouncilEntryGeneratedConfig {
        val sceneType = inferSceneType(draft.scene, draft.background, draft.userNeed)
        val title = draft.userNeed.ifBlank {
            draft.scene.ifBlank { "智囊团任务" }
        }.trim().take(32).ifBlank { "智囊团任务" }
        val summary = buildSummary(draft, sceneType)
        val config = CouncilConfig(
            sceneType = sceneType,
            objective = buildObjective(draft, sceneType),
            focus = buildFocus(draft, sceneType),
            speakerRole = draft.speakerRole,
            targetRole = draft.targetRole,
            background = draft.background
        )
        return CouncilEntryGeneratedConfig(
            title = title,
            summary = summary,
            config = config,
            suggestedExperts = defaultExpertsFor(sceneType),
            promptPreview = buildPromptPreview(draft, config),
            providerName = providerName
        )
    }

    fun fromTemplate(template: CouncilTemplateEntity): CouncilEntryGeneratedConfig {
        val preset = template.toCouncilTemplate()
        val config = preset.toConfig()
        return CouncilEntryGeneratedConfig(
            title = preset.label,
            summary = preset.description.ifBlank { "从已保存模板加载。" },
            config = config,
            suggestedExperts = defaultExpertsFor(config.sceneType),
            promptPreview = buildPromptPreview(
                draft = CouncilEntryDraft(
                    scene = config.sceneType.name,
                    userNeed = config.objective,
                    concern = config.focus,
                    sourceTemplateId = template.templateId
                ),
                config = config
            ),
            providerName = "已保存模板"
        )
    }

    private fun parse(
        raw: String,
        draft: CouncilEntryDraft,
        providerName: String
    ): CouncilEntryGeneratedConfig {
        val payload = runCatching {
            gson.fromJson(raw, CouncilEntryGenerationPayload::class.java)
        }.getOrNull()

        val sceneType = parseSceneType(payload?.sceneType) ?: inferSceneType(
            draft.scene,
            draft.background,
            draft.userNeed
        )
        val config = CouncilConfig(
            sceneType = sceneType,
            objective = payload?.objective.cleanOrFallback(buildObjective(draft, sceneType)),
            focus = payload?.focus.cleanOrFallback(buildFocus(draft, sceneType)),
            speakerRole = draft.speakerRole,
            targetRole = draft.targetRole,
            background = draft.background
        )
        return CouncilEntryGeneratedConfig(
            title = payload?.title.cleanOrFallback(
                draft.userNeed.ifBlank { draft.scene }.takeIf { !it.isNullOrBlank() } ?: "智囊团任务"
            ),
            summary = payload?.summary.cleanOrFallback(buildSummary(draft, sceneType)),
            config = config,
            suggestedExperts = payload?.suggestedExperts.cleanList().ifEmpty {
                defaultExpertsFor(sceneType)
            },
            promptPreview = buildPromptPreview(draft, config),
            providerName = providerName
        )
    }

    private fun buildSystemPrompt(availableExperts: List<CouncilExpertEntity>): String = buildString {
        appendLine("# 你的角色")
        appendLine("你是一个智囊团任务配置生成器。你的产出将直接驱动一组 AI 专家在直播中为用户提供实时分析。")
        appendLine()
        appendLine("# 智囊团运行机制（你必须理解这些，才能生成高质量配置）")
        appendLine("1. 用户即将进入一场直播（面试/会议/演讲/其他），需要 AI 帮忙看场、识别风险、给建议。")
        appendLine("2. 你生成的配置会被传递给 3-5 位 AI 专家，每位专家会独立分析直播画面和语音。")
        appendLine("3. 专家们会基于你设定的 objective 决定「分析什么」，基于 focus 决定「重点看什么」。")
        appendLine("4. 因此 objective 和 focus 越具体，专家的分析就越精准；越空泛，专家就越容易输出废话。")
        appendLine()
        appendLine("# 输出格式")
        appendLine("严格返回一个 JSON 对象，不要输出任何额外文字。")
        appendLine("所有字段的值必须使用简体中文（sceneType 除外）。")
        appendLine()
        appendLine("# 字段说明")
        appendLine("- title：简短有力的任务标题，让用户一眼知道智囊团在帮他做什么（如「面试防踩坑」「路演说服力诊断」）")
        appendLine("- summary：2-3 句话，告诉用户智囊团会怎么帮他，具体关注什么")
        appendLine("- sceneType：Speech | Meeting | Interview | General（枚举值，保持英文）")
        appendLine("- objective：智囊团的核心任务，必须回答「专家们需要帮用户判断什么、解决什么」。要具体到场景（如「识别面试官是否在用压力测试来压价」而非「帮助用户判断面试情况」）")
        appendLine("- focus：专家应该重点盯住的风险信号和观察维度，用分号分隔。必须具体到可观察的行为或话术（如「注意对方是否回避薪资区间；留意口头承诺但不写进 offer 的情况」）")
        appendLine("- suggestedExperts：3-5 个席位标签数组，从用户已配置的专家中选择最合适的组合")
        appendLine()

        // 动态席位库：从用户实际配置的专家列表中读取
        if (availableExperts.isNotEmpty()) {
            appendLine("# 用户已配置的专家席位（只能从这里选）")
            availableExperts.forEach { expert ->
                val desc = expert.description.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                appendLine("${expert.name}$desc")
            }
        } else {
            appendLine("# 席位建议")
            appendLine("用户尚未配置专家，请根据场景推荐 3-5 个席位名称（中文），例如：事实观察员、风险守门人、策略顾问、综合研判等。")
        }

        appendLine()
        appendLine("# 质量标准")
        appendLine("- objective 必须包含至少一个具体的判断目标，禁止使用「帮助用户了解情况」这类空话")
        appendLine("- focus 必须包含至少 3 个具体的观察维度，每个维度要落实到可观察行为")
        appendLine("- suggestedExperts 中的名称必须与上面的席位名称完全一致（如果有已配置席位）")
    }

    private fun buildUserPrompt(
        draft: CouncilEntryDraft,
        templates: List<CouncilTemplateEntity>
    ): String = buildString {
        appendLine("[用户简报]")
        appendLine("场景：${draft.scene.ifBlank { "未指定" }}")
        appendLine("我的角色：${draft.speakerRole.ifBlank { "未指定" }}")
        appendLine("对方是谁：${draft.targetRole.ifBlank { "未指定" }}")
        appendLine("需要什么帮助：${draft.userNeed.ifBlank { "未指定" }}")
        appendLine("最担心什么：${draft.concern.ifBlank { "未指定" }}")
        appendLine("补充背景：${draft.background.ifBlank { "无" }}")
        if (templates.isNotEmpty()) {
            appendLine()
            appendLine("[参考模板 — 可借鉴但不要照搬，根据用户简报调整]")
            templates.take(4).forEach { template ->
                appendLine("- ${template.label}：")
                appendLine("  目标：${template.objective}")
                appendLine("  关注：${template.focus}")
            }
        }
        appendLine()
        appendLine("[生成要求]")
        appendLine("1. 仔细阅读用户的角色、对方、需求和担忧，理解用户在这场直播中的处境")
        appendLine("2. objective 要回答：如果你是坐在用户旁边的顾问，你会帮他盯住什么？")
        appendLine("3. focus 要回答：哪些具体的话术、表情、行为、信号需要专家重点观察？")
        appendLine("4. suggestedExperts 根据场景选最合适的组合，最后一个必须是「综合研判」")
        appendLine("5. 所有输出使用简体中文")
    }

    private fun buildSummary(
        draft: CouncilEntryDraft,
        sceneType: CouncilSceneType
    ): String {
        val speaker = draft.speakerRole.ifBlank { "用户" }
        val target = draft.targetRole.ifBlank { "对方" }
        val sceneName = sceneTypeToChinese(sceneType)
        return when {
            draft.userNeed.isNotBlank() && draft.concern.isNotBlank() ->
                "智囊团将在${sceneName}场景中协助${speaker}：${draft.userNeed}。重点防范：${draft.concern}。"
            draft.userNeed.isNotBlank() ->
                "智囊团将在${sceneName}场景中协助${speaker}：${draft.userNeed}，同时持续监控${target}的行为信号。"
            else ->
                "智囊团将在${sceneName}场景中为${speaker}提供全程分析，帮助识别${target}的意图和潜在风险。"
        }
    }

    private fun buildObjective(
        draft: CouncilEntryDraft,
        sceneType: CouncilSceneType
    ): String {
        val speaker = draft.speakerRole.ifBlank { "用户" }
        val target = draft.targetRole.ifBlank { "对方" }
        if (draft.userNeed.isNotBlank()) {
            return "帮助${speaker}在与${target}的互动中：${draft.userNeed}。识别对${speaker}不利的风险信号，提供可执行的应对建议。"
        }
        return when (sceneType) {
            CouncilSceneType.Speech ->
                "评估演讲者的说服力和可信度；识别内容漏洞、过度承诺和数据可疑之处；帮助${speaker}判断哪些信息值得信赖。"
            CouncilSceneType.Meeting ->
                "识别会议中的权力博弈和信息操控；判断谁在主导、谁在回避；帮助${speaker}看清下一步该追问什么。"
            CouncilSceneType.Interview ->
                "识别${target}的施压话术和模糊承诺；判断岗位真实情况与描述是否一致；保护${speaker}的谈判利益。"
            CouncilSceneType.General ->
                "在信息不完整时优先站在${speaker}利益一侧；识别异常行为和误导信号；帮助${speaker}做出安全决策。"
        }
    }

    private fun buildFocus(
        draft: CouncilEntryDraft,
        sceneType: CouncilSceneType
    ): String {
        if (draft.concern.isNotBlank()) {
            val sceneFocus = when (sceneType) {
                CouncilSceneType.Speech -> "表达节奏变化；内容与承诺的一致性"
                CouncilSceneType.Meeting -> "发言权分配；关键信息是否被绕过"
                CouncilSceneType.Interview -> "薪资谈判中的模糊表述；口头承诺的可信度"
                CouncilSceneType.General -> "前后矛盾的表述；异常的情绪变化"
            }
            return "${draft.concern}；${sceneFocus}"
        }
        return when (sceneType) {
            CouncilSceneType.Speech ->
                "节奏突变和异常停顿；肢体语言与内容是否一致；数据引用的准确性；过度承诺或回避敏感问题；观众反应的变化"
            CouncilSceneType.Meeting ->
                "谁在主导发言谁在回避；态度松紧的切换时机；关键数字和承诺是否被模糊处理；信息不对称和责任转移"
            CouncilSceneType.Interview ->
                "是否使用压力测试来压价；画饼话术（如「未来可期」「弹性空间」）；岗位描述与实际是否有出入；竞业限制和薪资结构的模糊之处"
            CouncilSceneType.General ->
                "前后矛盾的陈述；诱导性提问或表达；异常的沉默或话题转移；信息缺口和刻意回避"
        }
    }

    private fun sceneTypeToChinese(sceneType: CouncilSceneType): String = when (sceneType) {
        CouncilSceneType.Speech -> "演讲/路演"
        CouncilSceneType.Meeting -> "会议/谈判"
        CouncilSceneType.Interview -> "面试/招聘沟通"
        CouncilSceneType.General -> "通用"
    }

    private fun buildPromptPreview(
        draft: CouncilEntryDraft,
        config: CouncilConfig
    ): String = buildString {
        appendLine("【场景】${sceneTypeToChinese(config.sceneType)}")
        if (draft.speakerRole.isNotBlank()) appendLine("【我的角色】${draft.speakerRole}")
        if (draft.targetRole.isNotBlank()) appendLine("【对方】${draft.targetRole}")
        appendLine("【智囊团目标】${config.objective}")
        appendLine("【重点观察】${config.focus}")
        if (draft.background.isNotBlank()) appendLine("【背景】${draft.background.take(180)}")
    }.trim()

    private fun defaultExpertsFor(sceneType: CouncilSceneType): List<String> {
        return when (sceneType) {
            CouncilSceneType.Speech -> listOf("事实观察员", "表达分析师", "风险守门人", "策略顾问", "综合研判")
            CouncilSceneType.Meeting -> listOf("事实观察员", "心理分析师", "风险守门人", "策略顾问", "综合研判")
            CouncilSceneType.Interview -> listOf("事实观察员", "心理分析师", "风险守门人", "策略顾问", "综合研判")
            CouncilSceneType.General -> listOf("事实观察员", "风险守门人", "策略顾问", "综合研判")
        }
    }

    private fun inferSceneType(
        scene: String,
        background: String,
        userNeed: String
    ): CouncilSceneType {
        val merged = listOf(scene, background, userNeed).joinToString(" ").lowercase(Locale.US)
        return when {
            merged.contains("interview") || merged.contains("candidate") || merged.contains("hr")
                || merged.contains("面试") || merged.contains("候选") ->
                CouncilSceneType.Interview
            merged.contains("meeting") || merged.contains("report") || merged.contains("boss")
                || merged.contains("会议") || merged.contains("汇报") ->
                CouncilSceneType.Meeting
            merged.contains("speech") || merged.contains("presentation") || merged.contains("host")
                || merged.contains("演讲") || merged.contains("演示") || merged.contains("主持") ->
                CouncilSceneType.Speech
            else -> CouncilSceneType.General
        }
    }

    private fun parseSceneType(raw: String?): CouncilSceneType? {
        return when (raw?.trim()?.lowercase(Locale.US)) {
            "speech" -> CouncilSceneType.Speech
            "meeting" -> CouncilSceneType.Meeting
            "interview" -> CouncilSceneType.Interview
            "general" -> CouncilSceneType.General
            else -> null
        }
    }

    private fun String?.cleanOrFallback(fallback: String): String {
        return this?.trim()?.takeIf { it.isNotBlank() }?.take(240) ?: fallback
    }

    private fun List<String>?.cleanList(): List<String> {
        return this.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
    }
}

private data class CouncilEntryGenerationPayload(
    val title: String? = null,
    val summary: String? = null,
    val sceneType: String? = null,
    val objective: String? = null,
    val focus: String? = null,
    val suggestedExperts: List<String>? = null
)
