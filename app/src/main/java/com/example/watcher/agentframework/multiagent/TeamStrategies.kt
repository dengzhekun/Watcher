package com.example.watcher.agentframework.multiagent

interface TeamTaskPlanner {
    suspend fun createInitialTasks(team: TeamDefinition): List<TeamTask>
    suspend fun createFollowUpTasks(
        team: TeamDefinition,
        snapshot: TeamSnapshot
    ): List<TeamTask>
}

interface TeamTaskAssignmentStrategy {
    suspend fun assign(
        task: TeamTask,
        team: TeamDefinition,
        snapshot: TeamSnapshot
    ): String?
}

interface ConsensusStrategy {
    suspend fun decide(
        team: TeamDefinition,
        proposals: List<TeamProposal>
    ): TeamConsensusOutcome
}

class DefaultTeamTaskPlanner : TeamTaskPlanner {
    override suspend fun createInitialTasks(team: TeamDefinition): List<TeamTask> {
        val coordinatorIds = team.members.filter { it.role == TeamRole.Coordinator }.map { it.agentId }.toSet()
        val planner = team.members.firstOrNull { it.role == TeamRole.Planner }
        val workers = team.members.filterNot { it.agentId in coordinatorIds }

        val baseTasks = mutableListOf<TeamTask>()

        planner?.let {
            baseTasks += TeamTask(
                title = "Plan team strategy",
                description = "Decompose the root goal and propose the best collaborative path for: ${team.rootGoal}",
                ownerAgentId = it.agentId,
                priority = 100,
                kind = "planning"
            )
        }

        workers
            .filter { it.role != TeamRole.Planner }
            .forEach { member ->
                baseTasks += TeamTask(
                    title = when (member.role) {
                        TeamRole.Executor -> "Execute assigned objective"
                        TeamRole.Reviewer -> "Review team quality and risks"
                        TeamRole.Specialist -> "Provide specialist analysis"
                        TeamRole.Observer -> "Monitor state and surface anomalies"
                        TeamRole.Coordinator -> "Coordinate dependencies"
                        TeamRole.Planner -> "Refine the plan"
                    },
                    description = buildString {
                        append("Work on the team root goal from your role perspective: ${team.rootGoal}.")
                        if (member.capabilities.isNotEmpty()) {
                            append(" Use these capabilities when relevant: ${member.capabilities.joinToString(", ")}.")
                        }
                    },
                    ownerAgentId = member.agentId,
                    priority = when (member.role) {
                        TeamRole.Executor -> 90
                        TeamRole.Specialist -> 85
                        TeamRole.Reviewer -> 70
                        TeamRole.Observer -> 60
                        else -> 50
                    },
                    kind = member.role.name.lowercase()
                )
            }

        return baseTasks.sortedByDescending { it.priority }
    }

    override suspend fun createFollowUpTasks(
        team: TeamDefinition,
        snapshot: TeamSnapshot
    ): List<TeamTask> {
        val completedKinds = snapshot.tasks
            .filter { it.status == TeamTaskStatus.Completed }
            .map { it.kind }
            .toSet()
        val hasSynthesis = snapshot.tasks.any { it.kind == "synthesis" }
        val synthesisOwner = team.members.firstOrNull {
            it.role == TeamRole.Coordinator || it.role == TeamRole.Planner || it.role == TeamRole.Reviewer
        } ?: return emptyList()

        val shouldCreateSynthesis = !hasSynthesis &&
            completedKinds.any { it == "executor" || it == "specialist" || it == "planning" }

        return if (shouldCreateSynthesis) {
            listOf(
                TeamTask(
                    title = "Synthesize team result",
                    description = "Collect completed work on the blackboard and produce a final team answer for: ${team.rootGoal}",
                    ownerAgentId = synthesisOwner.agentId,
                    priority = 95,
                    kind = "synthesis"
                )
            )
        } else {
            emptyList()
        }
    }
}

class CapabilityBasedAssignmentStrategy : TeamTaskAssignmentStrategy {
    override suspend fun assign(
        task: TeamTask,
        team: TeamDefinition,
        snapshot: TeamSnapshot
    ): String? {
        task.ownerAgentId?.let { return it }

        val freeAgents = team.members.filter { member ->
            snapshot.tasks.none { it.ownerAgentId == member.agentId && it.status == TeamTaskStatus.Running }
        }
        if (freeAgents.isEmpty()) return null

        val byKind = freeAgents.firstOrNull { task.kind in it.capabilities.map(String::lowercase) }
        if (byKind != null) return byKind.agentId

        return when (task.kind) {
            "planning" -> freeAgents.firstOrNull { it.role == TeamRole.Planner }?.agentId
            "reviewer" -> freeAgents.firstOrNull { it.role == TeamRole.Reviewer }?.agentId
            "synthesis" -> freeAgents.firstOrNull { it.role == TeamRole.Coordinator || it.role == TeamRole.Planner }?.agentId
            "executor" -> freeAgents.firstOrNull { it.role == TeamRole.Executor }?.agentId
            else -> freeAgents.maxByOrNull { it.weight }?.agentId
        }
    }
}

class MajorityConsensusStrategy : ConsensusStrategy {
    override suspend fun decide(
        team: TeamDefinition,
        proposals: List<TeamProposal>
    ): TeamConsensusOutcome {
        if (proposals.isEmpty()) {
            return TeamConsensusOutcome(
                accepted = false,
                winner = null,
                votes = emptyList(),
                summary = "No proposals available for consensus"
            )
        }

        val votes = mutableListOf<TeamVote>()
        proposals.forEach { proposal ->
            team.members.forEach { member ->
                val approve = proposal.proposerAgentId == member.agentId || proposal.confidence >= 60
                votes += TeamVote(
                    proposalId = proposal.proposalId,
                    voterAgentId = member.agentId,
                    choice = if (approve) VoteChoice.Approve else VoteChoice.Abstain,
                    weight = member.weight,
                    rationale = if (approve) "Confidence and role alignment accepted" else "Insufficient confidence"
                )
            }
        }

        val scoreByProposal = proposals.associateWith { proposal ->
            votes.filter { it.proposalId == proposal.proposalId && it.choice == VoteChoice.Approve }
                .sumOf { it.weight }
        }
        val winner = scoreByProposal.maxByOrNull { it.value }?.key
        val accepted = winner != null && scoreByProposal[winner]!! > team.members.sumOf { it.weight } / 2

        return TeamConsensusOutcome(
            accepted = accepted,
            winner = winner,
            votes = votes,
            summary = if (accepted && winner != null) {
                "Consensus accepted proposal from ${winner.proposerAgentId}"
            } else {
                "Consensus failed to reach a majority"
            }
        )
    }
}
