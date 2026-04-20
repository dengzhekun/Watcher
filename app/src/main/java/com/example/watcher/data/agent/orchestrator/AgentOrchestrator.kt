package com.example.watcher.data.agent.orchestrator

import android.util.Log
import com.example.watcher.data.agent.core.*
import com.example.watcher.data.agent.memory.AgentKnowledgeStore
import com.example.watcher.data.agent.memory.AgentSessionMemory
import com.example.watcher.data.agent.runtime.AgentBackend
import com.example.watcher.data.agent.runtime.AgentRuntime
import com.example.watcher.data.agent.tools.*
import com.example.watcher.data.repository.SceneMemoryManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Orchestrates multi-agent analysis. Replaces the scheduling logic
 * previously in CouncilManager's analyze() method.
 *
 * The orchestrator does NOT know or care whether agents use LLM or HTTP backends.
 * It only interacts with agents through AgentRuntime.
 */
class AgentOrchestrator(
    private val sessionMemory: AgentSessionMemory,
    private val knowledgeStore: AgentKnowledgeStore,
    private val sceneMemoryManager: SceneMemoryManager
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
        private const val PARALLELISM = 2
    }

    /** A registered agent with its runtime and profile. */
    data class RegisteredAgent(
        val id: String,
        val profile: AgentProfile,
        val runtime: AgentRuntime,
        val sortOrder: Int = 0
    )

    /**
     * Run the Gathering phase: all agents observe in parallel and return opinions.
     */
    suspend fun runGathering(
        agents: List<RegisteredAgent>,
        context: AgentContext,
        sessionId: String
    ): List<Pair<String, AgentOpinion>> = coroutineScope {
        val semaphore = Semaphore(PARALLELISM)

        agents.map { agent ->
            async {
                semaphore.withPermit {
                    Log.d(TAG, "Gathering: agent ${agent.id} starting")
                    val request = AgentRequest(
                        type = AgentRequestType.OBSERVE,
                        sessionId = sessionId,
                        roundNumber = context.roundNumber,
                        context = context,
                        profile = agent.profile,
                        availableTools = agent.runtime.let {
                            // Tools are resolved by the runtime's ToolExecutor
                            buildToolSchemas()
                        }
                    )
                    val opinion = agent.runtime.execute(agent.id, request)
                    Log.d(TAG, "Gathering: agent ${agent.id} done (vote=${opinion.voteLevel}, confidence=${opinion.confidence})")
                    agent.id to opinion
                }
            }
        }.awaitAll()
    }

    /**
     * Run a single discussion round: pick the most alarmed agent to ask, get a reply.
     * Returns the ask+reply turns, or empty if no agent wants to ask.
     */
    suspend fun runDiscussionRound(
        agents: List<RegisteredAgent>,
        opinions: Map<String, AgentOpinion>,
        previousTurns: List<DiscussionTurn>,
        context: AgentContext,
        sessionId: String
    ): List<DiscussionTurn> {
        // Sort by vote severity descending
        val sorted = agents.sortedByDescending { a ->
            val op = opinions[a.id] ?: return@sortedByDescending 0
            voteSeverity(op.voteLevel) * 100 + op.confidence
        }

        for (asker in sorted) {
            val targets = agents.filter { it.id != asker.id }
            if (targets.isEmpty()) continue

            val askRequest = AgentRequest(
                type = AgentRequestType.DISCUSS_ASK,
                sessionId = sessionId,
                roundNumber = context.roundNumber,
                context = context,
                profile = asker.profile,
                availableTools = buildToolSchemas(),
                discussionContext = DiscussionContext(
                    allOpinions = opinions.values.toList(),
                    previousTurns = previousTurns,
                    targetAgents = targets.map { it.profile }
                )
            )

            val askOpinion = asker.runtime.execute(asker.id, askRequest)
            // The "ask" is encoded in the opinion's nextActions or summary
            // For simplicity, treat the summary as the question
            val question = askOpinion.summary
            if (question.isBlank()) continue

            // Find target — use the first finding as target hint, or pick highest-disagreement
            val targetId = targets.first().id
            val target = agents.first { it.id == targetId }

            val replyRequest = AgentRequest(
                type = AgentRequestType.DISCUSS_REPLY,
                sessionId = sessionId,
                roundNumber = context.roundNumber,
                context = context,
                profile = target.profile,
                availableTools = buildToolSchemas(),
                discussionContext = DiscussionContext(
                    allOpinions = opinions.values.toList(),
                    previousTurns = previousTurns,
                    targetAgents = emptyList(),
                    questionFrom = asker.profile.name,
                    question = question,
                    questionReason = askOpinion.voteReason
                )
            )

            val replyOpinion = target.runtime.execute(target.id, replyRequest)

            return listOf(
                DiscussionTurn(
                    fromAgent = asker.profile.name,
                    toAgent = target.profile.name,
                    kind = "ask",
                    message = question,
                    detail = askOpinion.voteReason
                ),
                DiscussionTurn(
                    fromAgent = target.profile.name,
                    toAgent = asker.profile.name,
                    kind = "reply",
                    message = replyOpinion.summary,
                    detail = replyOpinion.findings.firstOrNull() ?: ""
                )
            )
        }

        return emptyList() // No agent wanted to ask
    }

    private fun buildToolSchemas(): List<AgentToolSchema> {
        // Return schemas for all available tools
        return listOf(
            AgentToolSchema("request_observation", "请求摄像头重点关注指定画面细节（仅视觉内容）", mapOf(
                "request" to ToolParamSchema("string", "需要关注的画面细节", required = true),
                "reason" to ToolParamSchema("string", "请求原因")
            )),
            AgentToolSchema("query_knowledge", "从你的知识库检索相关经验", mapOf(
                "query" to ToolParamSchema("string", "检索关键词", required = true),
                "limit" to ToolParamSchema("integer", "最大返回条数", default = 5)
            )),
            AgentToolSchema("write_knowledge", "将值得长期记住的知识写入你的知识库", mapOf(
                "category" to ToolParamSchema("string", "类别: expert_calibration 或 user_profile", required = true),
                "content" to ToolParamSchema("string", "知识内容", required = true)
            )),
            AgentToolSchema("read_memory", "读取你在本次会话中的分析轨迹", mapOf(
                "limit" to ToolParamSchema("integer", "最大条数", default = 10)
            )),
            AgentToolSchema("write_memory", "将观察结论写入会话记忆", mapOf(
                "content" to ToolParamSchema("string", "要记住的内容", required = true)
            ))
        )
    }

    private fun voteSeverity(level: String): Int = when (level.lowercase()) {
        "alert" -> 3; "warn" -> 2; "watch" -> 1; else -> 0
    }
}
