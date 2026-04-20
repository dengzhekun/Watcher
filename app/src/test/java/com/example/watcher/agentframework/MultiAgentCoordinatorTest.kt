package com.example.watcher.agentframework

import com.example.watcher.agentframework.autonomy.AutonomousLifecycleState
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.multiagent.CollaborationMode
import com.example.watcher.agentframework.multiagent.CollaborativeAgentFactory
import com.example.watcher.agentframework.multiagent.CollaborativeAgentHandle
import com.example.watcher.agentframework.multiagent.TeamAgentContext
import com.example.watcher.agentframework.multiagent.TeamAgentOutput
import com.example.watcher.agentframework.multiagent.TeamAgentRuntimeState
import com.example.watcher.agentframework.multiagent.TeamAgentSpec
import com.example.watcher.agentframework.multiagent.TeamDefinition
import com.example.watcher.agentframework.multiagent.TeamLifecycleState
import com.example.watcher.agentframework.multiagent.TeamMessage
import com.example.watcher.agentframework.multiagent.TeamRole
import com.example.watcher.agentframework.multiagent.TeamTask
import com.example.watcher.agentframework.multiagent.TeamTaskStatus
import com.example.watcher.agentframework.multiagent.TeamRuntimeConfig
import com.example.watcher.agentframework.multiagent.MultiAgentCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAgentCoordinatorTest {

    @Test
    fun coordinatorCompletesTasksAndBuildsConsensus() = runBlocking {
        val members = listOf(
            TeamAgentSpec(
                agentId = "planner",
                definition = AgentDefinition(
                    agentId = "planner",
                    name = "Planner",
                    systemInstruction = "Plan work",
                    goal = "Plan the team work"
                ),
                role = TeamRole.Planner
            ),
            TeamAgentSpec(
                agentId = "executor",
                definition = AgentDefinition(
                    agentId = "executor",
                    name = "Executor",
                    systemInstruction = "Execute work",
                    goal = "Execute the team work"
                ),
                role = TeamRole.Executor
            )
        )

        val coordinator = MultiAgentCoordinator(
            team = TeamDefinition(
                teamId = "team_1",
                name = "Test Team",
                rootGoal = "Solve the shared task",
                collaborationMode = CollaborationMode.Blackboard,
                members = members
            ),
            agentFactory = FakeCollaborativeAgentFactory(),
            config = TeamRuntimeConfig(
                maxRounds = 6,
                loopDelayMillis = 1L
            ),
            parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        coordinator.start()
        val finalSnapshot = coordinator.awaitCompletion()

        assertEquals(TeamLifecycleState.Stopped, finalSnapshot.lifecycleState)
        assertTrue(finalSnapshot.tasks.isNotEmpty())
        assertTrue(finalSnapshot.tasks.all { it.status == TeamTaskStatus.Completed })
        assertTrue(finalSnapshot.blackboardEntries.isNotEmpty())
        assertNotNull(finalSnapshot.consensusOutcome)
        assertTrue(finalSnapshot.consensusOutcome!!.winner != null)
    }

    private class FakeCollaborativeAgentFactory : CollaborativeAgentFactory {
        override fun create(
            spec: TeamAgentSpec,
            context: TeamAgentContext
        ): CollaborativeAgentHandle {
            return FakeCollaborativeAgentHandle(spec)
        }
    }

    private class FakeCollaborativeAgentHandle(
        override val spec: TeamAgentSpec
    ) : CollaborativeAgentHandle {
        private var started = false
        private var currentTask: TeamTask? = null
        private var pendingOutputs = mutableListOf<String>()

        override suspend fun start() {
            started = true
        }

        override suspend fun assignTask(task: TeamTask) {
            currentTask = task
            pendingOutputs += "${spec.agentId} completed ${task.title}"
        }

        override suspend fun deliver(message: TeamMessage) = Unit

        override suspend fun collectOutputs(): TeamAgentOutput {
            val outputs = pendingOutputs.toList()
            pendingOutputs.clear()
            return TeamAgentOutput(
                agentId = spec.agentId,
                outputs = outputs,
                isTerminal = outputs.isNotEmpty(),
                isFailed = false
            )
        }

        override fun currentState(currentTaskId: String?): TeamAgentRuntimeState {
            return TeamAgentRuntimeState(
                agentId = spec.agentId,
                lifecycleState = if (started) AutonomousLifecycleState.Running else AutonomousLifecycleState.Created,
                currentTaskId = currentTaskId,
                lastOutput = pendingOutputs.lastOrNull()
            )
        }

        override suspend fun stop() {
            started = false
            currentTask = null
        }
    }
}
