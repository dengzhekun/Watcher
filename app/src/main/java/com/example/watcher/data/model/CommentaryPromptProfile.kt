package com.example.watcher.data.model

/**
 * Configurable prompt templates for the 3-consumer commentary system.
 *
 * Each profile controls:
 * - Consumer B/C: commentator role + observation rules
 * - Consumer A:   builder role + phase-specific hints
 * - Memory compression: role descriptions + char limits for A/B tiers
 */
data class CommentaryPromptProfile(
    // Consumer B/C (commentator)
    val commentatorRole: String,
    val commentatorRules: List<String>,
    val maxSentences: Int = 6,
    // Consumer A (builder)
    val builderRole: String,
    val builderBootstrapHint: String,
    val builderBuildingHint: String,
    val builderSteadyHint: String,
    // Memory B compression
    val memoryBRole: String,
    val memoryBMaxChars: Int = 100,
    // Memory A compression
    val memoryARole: String,
    val memoryAMaxChars: Int = 200,
) {
    companion object {
        /** Original LiveRoom prompts — default, preserves existing behavior exactly. */
        fun liveRoom() = CommentaryPromptProfile(
            commentatorRole = "你是一位直播实况解说员。观看这段视频片段，描述画面中正在发生的事情。",
            commentatorRules = listOf(
                "固定场景和已识别实体不要重复描述，专注于新发生的动作和变化。",
                "只描述画面中实际可见的内容，严禁推测或想象接下来会发生什么。",
                "如果发现新的人物或物体，详细描述其外观特征以便后续识别。",
                "使用现在时，简洁有力。",
                "如果场景静态无变化，尽可能详细描述场景中的物体、环境、布局、颜色等细节。",
                "使用简体中文。",
                "只返回解说文本，不要返回 JSON 或其他格式。",
            ),
            maxSentences = 6,
            builderRole = "你是直播场景分析建设者。根据解说员的画面描述，维护结构化的场景记忆。",
            builderBootstrapHint = "【冷启动】重点：建立场景 + 识别所有实体 + 对模糊信息发 ASK",
            builderBuildingHint = "【补全中】重点：补充实体属性 + 识别新实体 + 校对已有信息 + 压缩动态",
            builderSteadyHint = "【稳态】重点：检查场景变化 + 追踪实体增减 + 压缩动态",
            memoryBRole = "你是直播内容记忆压缩助手。以下内容包含[画面]解说和[语音]转写两种来源。请提取关键信息，压缩为一段简洁摘要（100字以内）。只返回摘要文本。",
            memoryBMaxChars = 100,
            memoryARole = "你是直播核心记忆管理助手。根据当前核心记忆和最近的中期摘要，更新核心记忆。保留最重要的人物、场景、事件信息（200字以内）。只返回更新后的核心记忆文本。",
            memoryAMaxChars = 200,
        )

        /** Digital Life Card — user portrait observation profile. */
        fun digitalLifeCard() = CommentaryPromptProfile(
            commentatorRole = "你是用户行为观察员。观看这段视频片段，客观记录当前场景下可支持行为建模的画面事实，不预设固定维度。",
            commentatorRules = listOf(
                "已记录的固定场景和已识别实体不要重复描述，优先补充新出现的行为变化。",
                "只描述画面中实际可见的内容，严禁推测用户的想法、性格和意图。",
                "重点关注：姿势习惯、重复动作、物品使用方式、工作或休闲节奏、饮食与停留方式等可复证线索。",
                "如果场景静态无变化，优先补充桌面布局、空间特征、用户是否持续停留在工作区等稳定事实。",
                "尽量给出可复证的细节，如“右手拿杯子”“22点后仍在桌前”“桌面左侧有台灯”。",
                "优先使用标签行输出： [SCENE] 场景事实 / [USER] 用户自身状态或动作 / [INTERACTION] 用户与物品或空间的互动 / [TIME] 时段与活跃度线索。",
                "一行只写一个事实，能分开写就不要混在同一行。",
                "使用现在时，客观简洁。",
                "使用简体中文。",
                "只返回观察文本，不要返回 JSON 或其他格式。",
            ),
            maxSentences = 6,
            builderRole = "你是场景行为模型建设者。根据观察员的描述，维护当前场景的环境、实体和动态变化结构化记忆，为场景优先的行为模型服务。",
            builderBootstrapHint = "【冷启动】重点：建立固定环境、识别用户与常驻物品、对模糊但重要的行为规律发 ASK",
            builderBuildingHint = "【补全中】重点：补充环境细节、记录重复动作线索、校对实体信息、压缩动态",
            builderSteadyHint = "【稳态】重点：追踪场景中的环境变化、重复动作和时段差异，只保留对行为模型有价值的变化",
            memoryBRole = "你是场景行为模型摘要助手。从以下观察中提取当前场景下近期稳定出现的行为线索。可以自由归纳维度，但不要预设固定三分类。100字以内。",
            memoryBMaxChars = 100,
            memoryARole = "你是场景行为模型管理助手。将当前稳定判断和近期观察融合，更新该场景下的核心行为模型。允许自由组织维度，但要复用已有命名，避免同义词泛滥。保留跨观察周期稳定的模式，弱化一次性事件。200字以内。",
            memoryAMaxChars = 200,
        )
    }
}
