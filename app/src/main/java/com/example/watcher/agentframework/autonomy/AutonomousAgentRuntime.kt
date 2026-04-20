package com.example.watcher.agentframework.autonomy

import com.example.watcher.agentframework.core.AgentDefinition
import java.util.UUID
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

class AutonomousAgentRuntime(
    private val definition: AgentDefinition,
    private val config: AutonomousAgentConfig,
    private val modules: AutonomousAgentModules,
    parentScope: CoroutineScope,
    sessionId: String = UUID.randomUUID().toString()
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private val completion = CompletableDeferred<AutonomousAgentSnapshot>()
    private val _events = MutableSharedFlow<AutonomousAgentEvent>(
        replay = 32,
        extraBufferCapacity = 128
    )
    private val _snapshot = MutableStateFlow(
        AutonomousAgentSnapshot(
            sessionId = sessionId,
            definition = definition
        )
    )

    private var job: Job? = null
    @Volatile
    private var stopRequested = false

    val snapshot: StateFlow<AutonomousAgentSnapshot> = _snapshot.asStateFlow()
    val events: SharedFlow<AutonomousAgentEvent> = _events.asSharedFlow()

    suspend fun initialize() {
        if (snapshot.value.lifecycleState != AutonomousLifecycleState.Created) return
        setLifecycle(AutonomousLifecycleState.Initialized)
    }

    suspend fun start() {
        mutex.withLock {
            if (job?.isActive == true) return
            if (snapshot.value.lifecycleState.isTerminal) return
            stopRequested = false
            job = scope.launch(Dispatchers.Default) {
                runLoop()
            }
        }
    }

    suspend fun submitSignal(signal: AgentSignal) {
        modules.communicationHub.submit(snapshot.value.sessionId, signal)
    }

    fun suspendRuntime() {
        scope.launch {
            if (snapshot.value.lifecycleState.isTerminal) return@launch
            setLifecycle(AutonomousLifecycleState.Suspended)
        }
    }

    fun resumeRuntime() {
        scope.launch {
            if (snapshot.value.lifecycleState != AutonomousLifecycleState.Suspended) return@launch
            setLifecycle(AutonomousLifecycleState.Running)
        }
    }

    fun stop() {
        stopRequested = true
    }

    suspend fun awaitCompletion(): AutonomousAgentSnapshot {
        job?.join()
        return if (completion.isCompleted) completion.await() else snapshot.value
    }

    suspend fun destroy() {
        val currentJob = mutex.withLock {
            stopRequested = true
            job
        }
        currentJob?.cancel()
        currentJob?.join()
        val sessionId = snapshot.value.sessionId
        modules.memoryManager.clear(sessionId)
        modules.communicationHub.clear(sessionId)
        setLifecycle(
            state = AutonomousLifecycleState.Destroyed,
            stopReason = AutonomousStopReason.Cancelled
        )
        completeIfNeeded(snapshot.value)
    }

    private suspend fun runLoop() {
        if (snapshot.value.lifecycleState == AutonomousLifecycleState.Created) {
            initialize()
        }
        setLifecycle(AutonomousLifecycleState.Running)
        val startedAt = System.currentTimeMillis()

        try {
            while (!stopRequested) {
                val current = snapshot.value
                if (current.lifecycleState == AutonomousLifecycleState.Suspended) {
                    delay(config.loopDelayMillis)
                    continue
                }
                if (current.cycle >= config.maxCycles) {
                    finish(
                        state = AutonomousLifecycleState.Stopped,
                        stopReason = AutonomousStopReason.StepLimitReached
                    )
                    return
                }
                if (System.currentTimeMillis() - startedAt >= config.maxRuntimeMillis) {
                    finish(
                        state = AutonomousLifecycleState.Stopped,
                        stopReason = AutonomousStopReason.RuntimeLimitReached
                    )
                    return
                }

                val cycle = current.cycle + 1
                val cycleStartedAt = System.currentTimeMillis()
                try {
                    val adapterSignals = modules.signalAdapters.flatMap { it.collect(current) }
                    val inboundSignals = modules.communicationHub.drain(current.sessionId)
                    val allSignals = inboundSignals + adapterSignals
                    val perception = modules.perceptionPipeline.process(current, allSignals)
                    modules.memoryManager.onPerception(current.sessionId, perception)
                    val memory = modules.memoryManager.snapshot(current.sessionId)
                    val goal = modules.goalParser.resolve(definition, perception, memory)
                    val plan = modules.taskPlanner.plan(definition, goal, perception, memory)
                    val reasoning = modules.reasoningEngine.reason(
                        definition = definition,
                        snapshot = current,
                        perception = perception,
                        memory = memory,
                        goal = goal,
                        plan = plan
                    )
                    val decision = modules.decisionSelector.select(definition, reasoning, memory)
                    modules.memoryManager.onDecision(current.sessionId, decision)
                    val guardedDecision = modules.ruleConstraintEngine.apply(definition, decision, plan, current)
                    val outcome = modules.executionCoordinator.execute(definition, current, guardedDecision)
                    outcome.outputs.forEach { output ->
                        modules.communicationHub.publish(current.sessionId, output)
                        _events.emit(AutonomousAgentEvent.OutputPublished(current.sessionId, output))
                    }
                    val validation = modules.resultValidator.validate(goal, guardedDecision, outcome)
                    modules.feedbackProcessor.process(current.sessionId, outcome, validation)
                    val record = AutonomousCycleRecord(
                        cycle = cycle,
                        perception = perception,
                        goal = goal,
                        plan = plan,
                        reasoning = reasoning,
                        guardedDecision = guardedDecision,
                        outcome = outcome,
                        validation = validation,
                        startedAt = cycleStartedAt
                    )
                    val metrics = modules.evaluationEngine.evaluate(record)
                    modules.learningEngine.learn(current.sessionId, record, metrics)
                    val outputs = modules.communicationHub.outputs(current.sessionId)
                    mutateSnapshot { state ->
                        val updatedRecords = (state.records + record).let { allRecords ->
                            val limit = config.maxRecords ?: config.maxCycles
                            if (allRecords.size > limit) allRecords.takeLast(limit) else allRecords
                        }
                        state.copy(
                            cycle = cycle,
                            idleCount = if (perception.cleanedSignals.isEmpty() && outcome.outputs.isEmpty()) {
                                state.idleCount + 1
                            } else {
                                0
                            },
                            lastPerception = perception,
                            lastGoal = goal,
                            lastPlan = plan,
                            lastReasoning = reasoning,
                            lastDecision = decision,
                            lastOutcome = outcome,
                            lastValidation = validation,
                            outputs = outputs,
                            records = updatedRecords,
                            errorMessage = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    _events.emit(
                        AutonomousAgentEvent.CycleCompleted(
                            sessionId = current.sessionId,
                            cycle = cycle,
                            validationStatus = validation.status
                        )
                    )

                    val afterCycle = snapshot.value
                    if (afterCycle.idleCount >= config.maxIdleCycles) {
                        finish(
                            state = AutonomousLifecycleState.Stopped,
                            stopReason = AutonomousStopReason.IdleLimitReached
                        )
                        return
                    }
                    if (validation.status == ValidationStatus.Completed) {
                        finish(
                            state = AutonomousLifecycleState.Stopped,
                            stopReason = AutonomousStopReason.GoalAchieved
                        )
                        return
                    }
                    if (!validation.shouldContinue && !validation.shouldRetry) {
                        finish(
                            state = if (validation.status == ValidationStatus.Failed) {
                                AutonomousLifecycleState.Failed
                            } else {
                                AutonomousLifecycleState.Stopped
                            },
                            stopReason = if (validation.status == ValidationStatus.Failed) {
                                AutonomousStopReason.Error
                            } else {
                                AutonomousStopReason.StoppedByRequest
                            }
                        )
                        return
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val updated = mutateSnapshotAndGet { state ->
                        state.copy(
                            failureCount = state.failureCount + 1,
                            errorMessage = error.message ?: "Autonomous cycle failed",
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    _events.emit(
                        AutonomousAgentEvent.FailureRecorded(
                            sessionId = updated.sessionId,
                            cycle = cycle,
                            message = updated.errorMessage.orEmpty()
                        )
                    )
                    if (updated.failureCount >= config.maxFailures) {
                        finish(
                            state = AutonomousLifecycleState.Failed,
                            stopReason = AutonomousStopReason.Error
                        )
                        return
                    }
                }

                delay(config.loopDelayMillis)
            }
            finish(
                state = AutonomousLifecycleState.Stopped,
                stopReason = AutonomousStopReason.StoppedByRequest
            )
        } catch (_: CancellationException) {
            finish(
                state = AutonomousLifecycleState.Destroyed,
                stopReason = AutonomousStopReason.Cancelled
            )
        }
    }

    private suspend fun setLifecycle(
        state: AutonomousLifecycleState,
        stopReason: AutonomousStopReason? = null
    ) {
        mutateSnapshot { current ->
            current.copy(
                lifecycleState = state,
                stopReason = stopReason,
                updatedAt = System.currentTimeMillis()
            )
        }
        _events.emit(
            AutonomousAgentEvent.LifecycleChanged(
                sessionId = snapshot.value.sessionId,
                state = state,
                stopReason = stopReason
            )
        )
    }

    private suspend fun finish(
        state: AutonomousLifecycleState,
        stopReason: AutonomousStopReason
    ) {
        setLifecycle(state, stopReason)
        completeIfNeeded(snapshot.value)
    }

    private suspend fun completeIfNeeded(finalSnapshot: AutonomousAgentSnapshot) {
        if (!completion.isCompleted) {
            completion.complete(finalSnapshot)
        }
    }

    private suspend fun mutateSnapshot(transform: (AutonomousAgentSnapshot) -> AutonomousAgentSnapshot) {
        mutex.withLock {
            _snapshot.value = transform(_snapshot.value)
        }
    }

    private suspend fun mutateSnapshotAndGet(
        transform: (AutonomousAgentSnapshot) -> AutonomousAgentSnapshot
    ): AutonomousAgentSnapshot {
        return mutex.withLock {
            transform(_snapshot.value).also { _snapshot.value = it }
        }
    }
}
