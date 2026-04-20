package com.example.watcher.data.agent.core

/** Request types sent to an agent. */
enum class AgentRequestType {
    OBSERVE,
    DISCUSS_ASK,
    DISCUSS_REPLY,
    REFLECT
}

/** Full request payload from orchestrator to agent runtime. */
data class AgentRequest(
    val type: AgentRequestType,
    val sessionId: String,
    val roundNumber: Int,
    val context: AgentContext,
    val profile: AgentProfile,
    val availableTools: List<AgentToolSchema>,
    val discussionContext: DiscussionContext? = null
)

/** Extra context for discussion phases. */
data class DiscussionContext(
    val allOpinions: List<AgentOpinion>,
    val previousTurns: List<DiscussionTurn>,
    val targetAgents: List<AgentProfile>,
    // For DISCUSS_REPLY
    val questionFrom: String? = null,
    val question: String? = null,
    val questionReason: String? = null
)

data class DiscussionTurn(
    val fromAgent: String,
    val toAgent: String,
    val kind: String,
    val message: String,
    val detail: String = ""
)

/** Tool schema exposed to the agent so it knows what tools are available. */
data class AgentToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParamSchema>
)

data class ToolParamSchema(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: Any? = null
)
