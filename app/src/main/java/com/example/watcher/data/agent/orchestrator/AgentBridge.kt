package com.example.watcher.data.agent.orchestrator

import com.example.watcher.data.agent.core.AgentContext
import com.example.watcher.data.agent.core.AgentOpinion
import com.example.watcher.data.agent.core.AgentProfile
import com.example.watcher.data.agent.core.DiscussionTurn
import com.example.watcher.data.agent.memory.AgentKnowledgeStore
import com.example.watcher.data.agent.memory.AgentSessionMemory
import com.example.watcher.data.agent.runtime.AgentRuntime
import com.example.watcher.data.agent.runtime.LlmBackend
import com.example.watcher.data.agent.tools.*
import com.example.watcher.data.model.CouncilConfig
import com.example.watcher.data.model.CouncilDiscussionTurn
import com.example.watcher.data.model.CouncilExpertKind
import com.example.watcher.data.model.CouncilExpertOpinion
import com.example.watcher.data.model.CouncilVoteLevel
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.example.watcher.data.repository.SceneMemoryManager
import com.example.watcher.data.repository.context.LiveSharedContextSnapshot
import com.example.watcher.data.repository.council.CouncilExpertSpec

/**
 * Bridges between the agent framework types and the existing council types.
 * Used during migration — CouncilManager calls orchestrator via bridge.
 */
object AgentBridge {

    fun specToProfile(spec: CouncilExpertSpec) = AgentProfile(
        name = spec.name,
        description = spec.description,
        persona = spec.persona,
        perspective = spec.perspective,
        expertKind = spec.expertKind.name.lowercase()
    )

    fun specToRegisteredAgent(
        spec: CouncilExpertSpec,
        provider: OpenAiCompatibleProvider,
        sessionMemory: AgentSessionMemory,
        knowledgeStore: AgentKnowledgeStore,
        sceneMemoryManager: SceneMemoryManager
    ): AgentOrchestrator.RegisteredAgent {
        val toolExecutor = ToolExecutor().apply {
            register(ObservationTool(sceneMemoryManager))
            register(KnowledgeQueryTool(knowledgeStore))
            register(KnowledgeWriteTool(knowledgeStore))
            register(MemoryReadTool(sessionMemory))
            register(MemoryWriteTool(sessionMemory))
        }
        val backend = LlmBackend(provider)
        val runtime = AgentRuntime(backend, toolExecutor)
        return AgentOrchestrator.RegisteredAgent(
            id = spec.expertId,
            profile = specToProfile(spec),
            runtime = runtime,
            sortOrder = spec.sortOrder
        )
    }

    fun contextFromSnapshot(
        snapshot: LiveSharedContextSnapshot,
        config: CouncilConfig,
        roundNumber: Int
    ) = AgentContext(
        sceneType = config.sceneType.name,
        objective = config.objective,
        focus = config.focus,
        speakerRole = config.speakerRole,
        targetRole = config.targetRole,
        background = config.background,
        recentVisual = snapshot.visual.recentVisual,
        recentSpeech = snapshot.speech.recentSpeech,
        memoryA = snapshot.memory.memoryA,
        memoryB = snapshot.memory.memoryB,
        roundNumber = roundNumber
    )

    fun agentOpinionToCouncil(
        agentId: String,
        spec: CouncilExpertSpec,
        opinion: AgentOpinion
    ) = CouncilExpertOpinion(
        expertId = agentId,
        name = spec.name,
        expertKind = spec.expertKind,
        legacyRole = spec.legacyRole,
        summary = opinion.summary,
        findings = opinion.findings,
        risks = opinion.risks,
        nextActions = opinion.nextActions,
        observationRequests = emptyList(), // Handled via tools now
        voteLevel = CouncilVoteLevel.fromRaw(opinion.voteLevel),
        voteReason = opinion.voteReason,
        confidence = opinion.confidence
    )

    fun councilTurnToAgent(turn: CouncilDiscussionTurn) = DiscussionTurn(
        fromAgent = turn.fromExpertName,
        toAgent = turn.toExpertName,
        kind = turn.kind.name.lowercase(),
        message = turn.message,
        detail = turn.detail
    )
}
