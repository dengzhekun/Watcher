package com.example.watcher.agentframework.multiagent

import com.example.watcher.agentframework.autonomy.AutonomousAgentRuntime
import com.example.watcher.agentframework.autonomy.AutonomousAgentConfig
import com.example.watcher.agentframework.autonomy.AutonomousAgentModules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TeamRuntimeConfig(
    val maxRounds: Int = 12,
    val loopDelayMillis: Long = 200L,
    val maxRuntimeMillis: Long = 180_000L,
    val maxTaskRetries: Int = 1
)

class AutonomousCollaborativeAgentFactory(
    private val agentConfig: AutonomousAgentConfig = AutonomousAgentConfig(),
    private val modulesFactory: (TeamAgentSpec, TeamAgentContext) -> AutonomousAgentModules
) : CollaborativeAgentFactory {
    override fun create(spec: TeamAgentSpec, context: TeamAgentContext): CollaborativeAgentHandle {
        val runtime = AutonomousAgentRuntime(
            definition = spec.definition,
            config = agentConfig,
            modules = modulesFactory(spec, context),
            parentScope = context.parentScope
        )
        return AutonomousCollaborativeAgentHandle(spec, runtime)
    }
}

class MultiAgentCoordinator(
    private val team: TeamDefinition,
    private val agentFactory: CollaborativeAgentFactory,
    private val taskPlanner: TeamTaskPlanner = DefaultTeamTaskPlanner(),
    private val assignmentStrategy: TeamTaskAssignmentStrategy = CapabilityBasedAssignmentStrategy(),
    private val consensusStrategy: ConsensusStrategy = MajorityConsensusStrategy(),
    private val blackboard: SharedBlackboard = InMemorySharedBlackboard(),
    private val messageBus: TeamMessageBus = InMemoryTeamMessageBus(),
    private val config: TeamRuntimeConfig = TeamRuntimeConfig(),
    parentScope: CoroutineScope
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private val completion = CompletableDeferred<TeamSnapshot>()
    private val registry = TeamAgentRegistry()
    private val assignedTaskByAgent = mutableMapOf<String, String>()
    private val _events = MutableSharedFlow<TeamEvent>(extraBufferCapacity = 128)
    private val _snapshot = MutableStateFlow(
        TeamSnapshot(
            teamId = team.teamId,
            name = team.name,
            rootGoal = team.rootGoal
        )
    )

    private var job: Job? = null
    @Volatile
    private var stopRequested = false

    val snapshot: StateFlow<TeamSnapshot> = _snapshot.asStateFlow()
    val events: SharedFlow<TeamEvent> = _events.asSharedFlow()

    suspend fun initialize() {
        if (snapshot.value.lifecycleState != TeamLifecycleState.Created) return
        setLifecycle(TeamLifecycleState.Initializing)
        val context = TeamAgentContext(
            team = team,
            blackboard = blackboard,
            messageBus = messageBus,
            parentScope = scope
        )
        for (spec in team.members) {
            messageBus.registerAgent(spec.agentId)
            registry.register(agentFactory.create(spec, context))
        }
        val initialTasks = taskPlanner.createInitialTasks(team)
        mutateSnapshot { state ->
            state.copy(
                tasks = initialTasks,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun start() {
        mutex.withLock {
            if (job?.isActive == true) return
            stopRequested = false
            job = scope.launch(Dispatchers.Default) {
                runLoop()
            }
        }
    }

    fun stop() {
        stopRequested = true
    }

    suspend fun submitMessage(message: TeamMessage) {
        publishMessage(message)
    }

    suspend fun submitTask(task: TeamTask) {
        mutateSnapshot { state ->
            state.copy(
                tasks = state.tasks + task,
                updatedAt = System.currentTimeMillis()
            )
        }
        _events.emit(TeamEvent.TaskStateChanged(team.teamId, task))
    }

    suspend fun awaitCompletion(): TeamSnapshot {
        job?.join()
        return if (completion.isCompleted) completion.await() else snapshot.value
    }

    suspend fun destroy() {
        stopRequested = true
        job?.cancel()
        job?.join()
        registry.stopAll()
        blackboard.clear()
        messageBus.clear()
        setLifecycle(TeamLifecycleState.Destroyed)
        completeIfNeeded(snapshot.value)
    }

    private suspend fun runLoop() {
        if (snapshot.value.lifecycleState == TeamLifecycleState.Created) {
            initialize()
        }
        registry.startAll()
        setLifecycle(TeamLifecycleState.Running)
        val startedAt = System.currentTimeMillis()

        try {
            while (!stopRequested) {
                val current = snapshot.value
                if (current.round >= config.maxRounds || System.currentTimeMillis() - startedAt >= config.maxRuntimeMillis) {
                    setLifecycle(TeamLifecycleState.Stopped)
                    finalizeConsensus()
                    registry.stopAll()
                    completeIfNeeded(snapshot.value)
                    return
                }

                val round = current.round + 1
                deliverQueuedMessages()
                assignReadyTasks()
                collectAgentOutputs()
                addFollowUpTasks()
                refreshAgentStates()
                val blackboardEntries = blackboard.all()
                val messages = messageBus.history()
                mutateSnapshot { state ->
                    state.copy(
                        round = round,
                        blackboardEntries = blackboardEntries,
                        messages = messages,
                        updatedAt = System.currentTimeMillis()
                    )
                }

                if (shouldFinalize(snapshot.value)) {
                    finalizeConsensus()
                    setLifecycle(
                        if (snapshot.value.tasks.any { it.status == TeamTaskStatus.Failed } &&
                            snapshot.value.tasks.none { it.status == TeamTaskStatus.Completed }
                        ) {
                            TeamLifecycleState.Failed
                        } else {
                            TeamLifecycleState.Stopped
                        }
                    )
                    registry.stopAll()
                    completeIfNeeded(snapshot.value)
                    return
                }

                delay(config.loopDelayMillis)
            }
            finalizeConsensus()
            setLifecycle(TeamLifecycleState.Stopped)
            registry.stopAll()
            completeIfNeeded(snapshot.value)
        } catch (_: CancellationException) {
            setLifecycle(TeamLifecycleState.Destroyed)
            registry.stopAll()
            completeIfNeeded(snapshot.value)
        }
    }

    private suspend fun assignReadyTasks() {
        val current = snapshot.value
        val updatedTasks = mutableListOf<TeamTask>()
        for (task in current.tasks) {
            if (task.status != TeamTaskStatus.Pending && task.status != TeamTaskStatus.Blocked) {
                updatedTasks += task
            } else if (task.dependsOnTaskIds.any { depId ->
                    current.tasks.none { it.taskId == depId && it.status == TeamTaskStatus.Completed }
                }) {
                updatedTasks += task.copy(status = TeamTaskStatus.Blocked, updatedAt = System.currentTimeMillis())
            } else {
                val owner = assignmentStrategy.assign(task, team, current)
                if (owner == null || assignedTaskByAgent.containsKey(owner)) {
                    updatedTasks += task.copy(status = TeamTaskStatus.Pending, updatedAt = System.currentTimeMillis())
                } else {
                    registry.get(owner)?.assignTask(task)
                    assignedTaskByAgent[owner] = task.taskId
                    publishMessage(
                        TeamMessage(
                            fromAgentId = "coordinator",
                            toAgentId = owner,
                            kind = TeamMessageKind.TaskAssignment,
                            subject = task.title,
                            content = task.description,
                            taskId = task.taskId
                        )
                    )
                    updatedTasks += task.copy(
                        ownerAgentId = owner,
                        status = TeamTaskStatus.Running,
                        attempts = task.attempts + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }
        updateTasks(updatedTasks)
    }

    private suspend fun collectAgentOutputs() {
        for (handle in registry.all()) {
            val currentTaskId = assignedTaskByAgent[handle.spec.agentId]
            val output = handle.collectOutputs()
            if (output.outputs.isNotEmpty()) {
                for (text in output.outputs) {
                    val entry = BlackboardEntry(
                        key = buildString {
                            append("agent/")
                            append(handle.spec.agentId)
                            currentTaskId?.let { append("/task/$it") }
                        },
                        value = text,
                        authorAgentId = handle.spec.agentId,
                        taskId = currentTaskId,
                        visibility = BlackboardVisibility.TeamOnly,
                        tags = setOf("agent_output", handle.spec.role.name.lowercase())
                    )
                    blackboard.publish(entry)
                    _events.emit(TeamEvent.BlackboardUpdated(team.teamId, entry))
                    publishMessage(
                        TeamMessage(
                            fromAgentId = handle.spec.agentId,
                            kind = TeamMessageKind.TaskResult,
                            subject = "Task output",
                            content = text,
                            taskId = currentTaskId
                        )
                    )
                }
            }

            if (currentTaskId != null) {
                val agentState = handle.currentState(currentTaskId)
                _events.emit(TeamEvent.AgentStateChanged(team.teamId, agentState))
                val task = snapshot.value.tasks.firstOrNull { it.taskId == currentTaskId } ?: continue
                if (output.isFailed) {
                    assignedTaskByAgent.remove(handle.spec.agentId)
                    updateSingleTask(
                        task.copy(
                            status = if (task.attempts <= config.maxTaskRetries) TeamTaskStatus.Pending else TeamTaskStatus.Failed,
                            resultSummary = output.outputs.lastOrNull(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else if (output.isTerminal && output.outputs.isNotEmpty()) {
                    assignedTaskByAgent.remove(handle.spec.agentId)
                    updateSingleTask(
                        task.copy(
                            status = TeamTaskStatus.Completed,
                            resultSummary = output.outputs.last(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private suspend fun addFollowUpTasks() {
        val newTasks = taskPlanner.createFollowUpTasks(team, snapshot.value)
            .filter { candidate -> snapshot.value.tasks.none { it.kind == candidate.kind && it.title == candidate.title } }
        if (newTasks.isEmpty()) return
        mutateSnapshot { state ->
            state.copy(
                tasks = state.tasks + newTasks,
                updatedAt = System.currentTimeMillis()
            )
        }
        for (task in newTasks) {
            _events.emit(TeamEvent.TaskStateChanged(team.teamId, task))
        }
    }

    private suspend fun deliverQueuedMessages() {
        for (handle in registry.all()) {
            val messages = messageBus.drain(handle.spec.agentId)
            for (message in messages) {
                handle.deliver(message)
            }
        }
    }

    private suspend fun refreshAgentStates() {
        val states = registry.all().associate { handle ->
            val currentTaskId = assignedTaskByAgent[handle.spec.agentId]
            handle.spec.agentId to handle.currentState(currentTaskId)
        }
        mutateSnapshot { state ->
            state.copy(
                agentStates = states,
                updatedAt = System.currentTimeMillis()
            )
        }
        for (state in states.values) {
            _events.emit(TeamEvent.AgentStateChanged(team.teamId, state))
        }
    }

    private suspend fun finalizeConsensus() {
        val proposals = snapshot.value.tasks
            .filter { it.status == TeamTaskStatus.Completed && !it.resultSummary.isNullOrBlank() }
            .mapNotNull { task ->
                val owner = task.ownerAgentId ?: return@mapNotNull null
                TeamProposal(
                    proposerAgentId = owner,
                    taskId = task.taskId,
                    title = task.title,
                    content = task.resultSummary.orEmpty(),
                    confidence = 70,
                    metadata = mapOf("kind" to task.kind)
                )
        }
        if (proposals.isEmpty()) return
        val outcome = consensusStrategy.decide(team, proposals)
        val winner = outcome.winner
        if (winner != null) {
            val entry = BlackboardEntry(
                key = "team/final_consensus",
                value = winner.content,
                authorAgentId = winner.proposerAgentId,
                taskId = winner.taskId,
                visibility = BlackboardVisibility.Global,
                tags = setOf("consensus", "final")
            )
            blackboard.publish(entry)
            _events.emit(TeamEvent.BlackboardUpdated(team.teamId, entry))
        }
        val blackboardEntries = blackboard.all()
        mutateSnapshot { state ->
            state.copy(
                proposals = proposals,
                consensusOutcome = outcome,
                blackboardEntries = blackboardEntries,
                updatedAt = System.currentTimeMillis()
            )
        }
        _events.emit(TeamEvent.ConsensusReached(team.teamId, outcome))
    }

    private fun shouldFinalize(snapshot: TeamSnapshot): Boolean {
        val terminalStates = setOf(
            TeamTaskStatus.Completed,
            TeamTaskStatus.Failed,
            TeamTaskStatus.Cancelled
        )
        return snapshot.tasks.isNotEmpty() &&
            snapshot.tasks.all { it.status in terminalStates } &&
            assignedTaskByAgent.isEmpty()
    }

    private suspend fun publishMessage(message: TeamMessage) {
        if (message.toAgentId == null) {
            messageBus.broadcast(message)
        } else {
            messageBus.send(message)
        }
        mutateSnapshot { state ->
            state.copy(
                messages = state.messages + message,
                updatedAt = System.currentTimeMillis()
            )
        }
        _events.emit(TeamEvent.MessagePublished(team.teamId, message))
    }

    private suspend fun updateTasks(tasks: List<TeamTask>) {
        mutateSnapshot { state ->
            state.copy(
                tasks = tasks,
                updatedAt = System.currentTimeMillis()
            )
        }
        for (task in tasks) {
            _events.emit(TeamEvent.TaskStateChanged(team.teamId, task))
        }
    }

    private suspend fun updateSingleTask(task: TeamTask) {
        val tasks = snapshot.value.tasks.map {
            if (it.taskId == task.taskId) task else it
        }
        updateTasks(tasks)
    }

    private suspend fun setLifecycle(state: TeamLifecycleState) {
        mutateSnapshot { current ->
            current.copy(
                lifecycleState = state,
                updatedAt = System.currentTimeMillis()
            )
        }
        _events.emit(TeamEvent.LifecycleChanged(team.teamId, state))
    }

    private suspend fun completeIfNeeded(finalSnapshot: TeamSnapshot) {
        if (!completion.isCompleted) {
            completion.complete(finalSnapshot)
        }
    }

    private suspend fun mutateSnapshot(transform: (TeamSnapshot) -> TeamSnapshot) {
        mutex.withLock {
            _snapshot.value = transform(_snapshot.value)
        }
    }
}
