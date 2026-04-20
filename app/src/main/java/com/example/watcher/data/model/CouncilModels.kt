package com.example.watcher.data.model

enum class InteractionMode(val label: String) {
    Off("未开启"),
    Live("直播模式"),
    Council("智囊团模式")
}

enum class CouncilSceneType(val label: String) {
    Speech("演讲"),
    Meeting("会议"),
    Interview("面试"),
    General("通用")
}

enum class CouncilVoteLevel(val label: String) {
    Pass("通过"),
    Watch("注意"),
    Warn("警惕"),
    Alert("立即留意");

    companion object {
        fun fromRaw(raw: String?): CouncilVoteLevel {
            return when (raw?.trim()?.lowercase()) {
                "alert", "立即留意" -> Alert
                "warn", "警惕" -> Warn
                "watch", "注意" -> Watch
                else -> Pass
            }
        }
    }
}

enum class CouncilExpertRole(val label: String) {
    Observer("事实锚点"),
    Delivery("表达与控场专家"),
    Psychology("意图解码"),
    Risk("风险雷达"),
    Strategy("策略引擎"),
    Synthesizer("综合研判")
}

enum class CouncilExpertKind(val label: String) {
    Specialist("专家"),
    Synthesizer("综合器")
}

data class CouncilConfig(
    val sceneType: CouncilSceneType = CouncilSceneType.General,
    val objective: String = "",
    val focus: String = "",
    val speakerRole: String = "",
    val targetRole: String = "",
    val background: String = ""
)

enum class CouncilAnalysisPhase(val label: String) {
    Idle("待命"),
    Gathering("收集线索"),
    Discussing("专家讨论"),
    Reviewing("交叉复核"),
    Synthesizing("整合意见"),
    Complete("完成")
}

enum class CouncilExpertStage(val label: String) {
    Standby("待命"),
    Observing("观察中"),
    Speaking("发言中"),
    Discussing("讨论中"),
    Reviewing("复核中"),
    Voted("已表决"),
    Synthesizing("整合中"),
    WaitingContext("等待上下文"),
    Blocked("受阻")
}

enum class CouncilDiscussionKind(val label: String) {
    Ask("@提问"),
    Reply("回应"),
    Summary("阶段结论")
}

data class CouncilExpertConsoleState(
    val expertId: String,
    val name: String,
    val expertKind: CouncilExpertKind = CouncilExpertKind.Specialist,
    val legacyRole: String = "",
    val stage: CouncilExpertStage = CouncilExpertStage.Standby,
    val headline: String = "",
    val note: String = "",
    val voteLevel: CouncilVoteLevel? = null,
    val confidence: Int? = null,
    val isLead: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CouncilExpertOpinion(
    val expertId: String,
    val name: String,
    val expertKind: CouncilExpertKind = CouncilExpertKind.Specialist,
    val legacyRole: String = "",
    val summary: String,
    val findings: List<String>,
    val risks: List<String>,
    val nextActions: List<String>,
    val observationRequests: List<String> = emptyList(),
    val voteLevel: CouncilVoteLevel,
    val voteReason: String,
    val confidence: Int,
    val agree: String = "",
    val challenge: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class CouncilAlert(
    val level: CouncilVoteLevel,
    val message: String,
    val triggeredBy: List<String>,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CouncilSynthesis(
    val situationSummary: String,
    val topFindings: List<String>,
    val topRisks: List<String>,
    val nextActions: List<String>,
    val finalAdvice: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CouncilDiscussionTurn(
    val id: String,
    val round: Int,
    val fromExpertId: String,
    val fromExpertName: String,
    val toExpertId: String = "",
    val toExpertName: String = "",
    val kind: CouncilDiscussionKind,
    val message: String,
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class CouncilDiscussionSummary(
    val headline: String,
    val agreements: List<String>,
    val disagreements: List<String>,
    val nextFocus: List<String>,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CouncilUiState(
    val isActive: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisPhase: CouncilAnalysisPhase = CouncilAnalysisPhase.Idle,
    val config: CouncilConfig = CouncilConfig(),
    val activeProviderName: String? = null,
    val lastTrigger: String = "",
    val console: List<CouncilExpertConsoleState> = emptyList(),
    val experts: List<CouncilExpertOpinion> = emptyList(),
    val discussionTurns: List<CouncilDiscussionTurn> = emptyList(),
    val discussionSummary: CouncilDiscussionSummary? = null,
    val discussionRound: Int = 0,
    val latestAlert: CouncilAlert? = null,
    val synthesis: CouncilSynthesis? = null,
    val errorMessage: String? = null
)
