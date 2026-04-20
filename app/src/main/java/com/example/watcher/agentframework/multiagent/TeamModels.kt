package com.example.watcher.agentframework.multiagent

import com.example.watcher.agentframework.autonomy.AutonomousLifecycleState
import com.example.watcher.agentframework.core.AgentDefinition
import java.util.UUID

enum class TeamRole {
    Coordinator,
    Planner,
    Executor,
    Reviewer,
    Specialist,
    Observer
}

enum class CollaborationMode {
    Blackboard,
    Hierarchical,
    Consensus,
    Pipeline
}

enum class TeamLifecycleState {
    Created,
    Initializing,
    Running,
    Suspended,
    Stopped,
    Failed,
    Destroyed
}

enum class TeamTaskStatus {
    Pending,
    Assigned,
    Running,
    Completed,
    Failed,
    Cancelled,
    Blocked
}

enum class TeamMessageKind {
    TaskAssignment,
    TaskResult,
    Broadcast,
    Proposal,
    Vote,
    Alert,
    Coordination
}

enum class BlackboardVisibility {
    Global,
    TeamOnly,
    AgentPrivate
}

data class TeamAgentSpec(
    val agentId: String,
    val definition: AgentDefinition,
    val role: TeamRole,
    val capabilities: Set<String> = emptySet(),
    val weight: Int = 1,
    val metadata: Map<String, String> = emptyMap()
)

data class TeamDefinition(
    val teamId: String,
    val name: String,
    val rootGoal: String,
    val collaborationMode: CollaborationMode = CollaborationMode.Blackboard,
    val members: List<TeamAgentSpec>,
    val metadata: Map<String, String> = emptyMap()
)

data class TeamTask(
    val taskId: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val status: TeamTaskStatus = TeamTaskStatus.Pending,
    val ownerAgentId: String? = null,
    val dependsOnTaskIds: List<String> = emptyList(),
    val priority: Int = 50,
    val kind: String = "generic",
    val attempts: Int = 0,
    val resultSummary: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BlackboardEntry(
    val entryId: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val authorAgentId: String,
    val taskId: String? = null,
    val visibility: BlackboardVisibility = BlackboardVisibility.TeamOnly,
    val tags: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis()
)

data class TeamMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val fromAgentId: String,
    val toAgentId: String? = null,
    val kind: TeamMessageKind,
    val subject: String,
    val content: String,
    val taskId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

data class TeamProposal(
    val proposalId: String = UUID.randomUUID().toString(),
    val proposerAgentId: String,
    val taskId: String? = null,
    val title: String,
    val content: String,
    val confidence: Int = 50,
    val metadata: Map<String, String> = emptyMap()
)

enum class VoteChoice {
    Approve,
    Reject,
    Abstain
}

data class TeamVote(
    val proposalId: String,
    val voterAgentId: String,
    val choice: VoteChoice,
    val weight: Int = 1,
    val rationale: String = ""
)

data class TeamConsensusOutcome(
    val accepted: Boolean,
    val winner: TeamProposal?,
    val votes: List<TeamVote>,
    val summary: String
)

data class TeamAgentRuntimeState(
    val agentId: String,
    val lifecycleState: AutonomousLifecycleState,
    val currentTaskId: String? = null,
    val lastOutput: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class TeamSnapshot(
    val teamId: String,
    val name: String,
    val rootGoal: String,
    val lifecycleState: TeamLifecycleState = TeamLifecycleState.Created,
    val round: Int = 0,
    val tasks: List<TeamTask> = emptyList(),
    val blackboardEntries: List<BlackboardEntry> = emptyList(),
    val messages: List<TeamMessage> = emptyList(),
    val proposals: List<TeamProposal> = emptyList(),
    val consensusOutcome: TeamConsensusOutcome? = null,
    val agentStates: Map<String, TeamAgentRuntimeState> = emptyMap(),
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed interface TeamEvent {
    val teamId: String
    val timestamp: Long

    data class LifecycleChanged(
        override val teamId: String,
        val state: TeamLifecycleState,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TeamEvent

    data class TaskStateChanged(
        override val teamId: String,
        val task: TeamTask,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TeamEvent

    data class MessagePublished(
        override val teamId: String,
        val message: TeamMessage,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TeamEvent

    data class BlackboardUpdated(
        override val teamId: String,
        val entry: BlackboardEntry,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TeamEvent

    data class ConsensusReached(
        override val teamId: String,
        val outcome: TeamConsensusOutcome,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TeamEvent

    data class AgentStateChanged(
        override val teamId: String,
        val state: TeamAgentRuntimeState,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TeamEvent
}
