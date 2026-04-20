package com.example.watcher.agentframework.multiagent

import com.example.watcher.agentframework.autonomy.AgentSignal
import com.example.watcher.agentframework.autonomy.AutonomousAgentRuntime
import com.example.watcher.agentframework.autonomy.SignalChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SharedBlackboard {
    suspend fun publish(entry: BlackboardEntry)
    suspend fun latest(key: String): BlackboardEntry?
    suspend fun query(
        tags: Set<String> = emptySet(),
        taskId: String? = null
    ): List<BlackboardEntry>
    suspend fun all(): List<BlackboardEntry>
    suspend fun clear()
}

class InMemorySharedBlackboard : SharedBlackboard {
    private val mutex = Mutex()
    private val entries = mutableListOf<BlackboardEntry>()

    override suspend fun publish(entry: BlackboardEntry) {
        mutex.withLock {
            entries += entry
        }
    }

    override suspend fun latest(key: String): BlackboardEntry? {
        return mutex.withLock {
            entries.lastOrNull { it.key == key }
        }
    }

    override suspend fun query(
        tags: Set<String>,
        taskId: String?
    ): List<BlackboardEntry> {
        return mutex.withLock {
            entries.filter { entry ->
                (taskId == null || entry.taskId == taskId) &&
                    (tags.isEmpty() || tags.all { it in entry.tags })
            }
        }
    }

    override suspend fun all(): List<BlackboardEntry> {
        return mutex.withLock { entries.toList() }
    }

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
        }
    }
}

interface TeamMessageBus {
    suspend fun registerAgent(agentId: String)
    suspend fun send(message: TeamMessage)
    suspend fun broadcast(message: TeamMessage)
    suspend fun drain(agentId: String): List<TeamMessage>
    suspend fun history(): List<TeamMessage>
    suspend fun clear()
}

class InMemoryTeamMessageBus : TeamMessageBus {
    private val mutex = Mutex()
    private val inbox = mutableMapOf<String, MutableList<TeamMessage>>()
    private val allMessages = mutableListOf<TeamMessage>()

    override suspend fun registerAgent(agentId: String) {
        mutex.withLock {
            inbox.getOrPut(agentId) { mutableListOf() }
        }
    }

    override suspend fun send(message: TeamMessage) {
        mutex.withLock {
            allMessages += message
            message.toAgentId?.let { target ->
                inbox.getOrPut(target) { mutableListOf() } += message
            }
        }
    }

    override suspend fun broadcast(message: TeamMessage) {
        mutex.withLock {
            allMessages += message
            inbox.values.forEach { queue -> queue += message }
        }
    }

    override suspend fun drain(agentId: String): List<TeamMessage> {
        return mutex.withLock {
            val queue = inbox.getOrPut(agentId) { mutableListOf() }
            val drained = queue.toList()
            queue.clear()
            drained
        }
    }

    override suspend fun history(): List<TeamMessage> {
        return mutex.withLock { allMessages.toList() }
    }

    override suspend fun clear() {
        mutex.withLock {
            inbox.clear()
            allMessages.clear()
        }
    }
}

data class TeamAgentOutput(
    val agentId: String,
    val outputs: List<String>,
    val isTerminal: Boolean,
    val isFailed: Boolean
)

data class TeamAgentContext(
    val team: TeamDefinition,
    val blackboard: SharedBlackboard,
    val messageBus: TeamMessageBus,
    val parentScope: CoroutineScope
)

interface CollaborativeAgentHandle {
    val spec: TeamAgentSpec
    suspend fun start()
    suspend fun assignTask(task: TeamTask)
    suspend fun deliver(message: TeamMessage)
    suspend fun collectOutputs(): TeamAgentOutput
    fun currentState(currentTaskId: String? = null): TeamAgentRuntimeState
    suspend fun stop()
}

interface CollaborativeAgentFactory {
    fun create(spec: TeamAgentSpec, context: TeamAgentContext): CollaborativeAgentHandle
}

class AutonomousCollaborativeAgentHandle(
    override val spec: TeamAgentSpec,
    private val runtime: AutonomousAgentRuntime
) : CollaborativeAgentHandle {
    private val outputMutex = Mutex()
    private var lastOutputIndex = 0

    override suspend fun start() {
        runtime.start()
    }

    override suspend fun assignTask(task: TeamTask) {
        runtime.submitSignal(
            AgentSignal(
                channel = SignalChannel.System,
                content = buildString {
                    appendLine("Assigned team task")
                    appendLine("taskId=${task.taskId}")
                    appendLine("title=${task.title}")
                    appendLine("description=${task.description}")
                    if (task.dependsOnTaskIds.isNotEmpty()) {
                        appendLine("dependsOn=${task.dependsOnTaskIds.joinToString(",")}")
                    }
                },
                metadata = mapOf(
                    "taskId" to task.taskId,
                    "taskKind" to task.kind
                )
            )
        )
    }

    override suspend fun deliver(message: TeamMessage) {
        runtime.submitSignal(
            AgentSignal(
                channel = SignalChannel.Agent,
                content = buildString {
                    appendLine("Message from ${message.fromAgentId}")
                    appendLine("subject=${message.subject}")
                    appendLine("content=${message.content}")
                },
                metadata = buildMap {
                    put("messageId", message.messageId)
                    put("kind", message.kind.name)
                    message.taskId?.let { put("taskId", it) }
                }
            )
        )
    }

    override suspend fun collectOutputs(): TeamAgentOutput {
        return outputMutex.withLock {
            val snapshot = runtime.snapshot.value
            val outputs = snapshot.outputs.drop(lastOutputIndex)
            lastOutputIndex = snapshot.outputs.size
            TeamAgentOutput(
                agentId = spec.agentId,
                outputs = outputs,
                isTerminal = snapshot.lifecycleState == com.example.watcher.agentframework.autonomy.AutonomousLifecycleState.Stopped ||
                    snapshot.lifecycleState == com.example.watcher.agentframework.autonomy.AutonomousLifecycleState.Failed ||
                    snapshot.lifecycleState == com.example.watcher.agentframework.autonomy.AutonomousLifecycleState.Destroyed,
                isFailed = snapshot.lifecycleState == com.example.watcher.agentframework.autonomy.AutonomousLifecycleState.Failed
            )
        }
    }

    override fun currentState(currentTaskId: String?): TeamAgentRuntimeState {
        val snapshot = runtime.snapshot.value
        return TeamAgentRuntimeState(
            agentId = spec.agentId,
            lifecycleState = snapshot.lifecycleState,
            currentTaskId = currentTaskId,
            lastOutput = snapshot.outputs.lastOrNull()
        )
    }

    override suspend fun stop() {
        runtime.stop()
    }
}

class TeamAgentRegistry {
    private val handles = linkedMapOf<String, CollaborativeAgentHandle>()

    fun register(handle: CollaborativeAgentHandle): TeamAgentRegistry {
        handles[handle.spec.agentId] = handle
        return this
    }

    fun get(agentId: String): CollaborativeAgentHandle? = handles[agentId]

    fun all(): List<CollaborativeAgentHandle> = handles.values.toList()

    suspend fun startAll() {
        for (handle in handles.values) {
            handle.start()
        }
    }

    suspend fun stopAll() {
        for (handle in handles.values) {
            handle.stop()
        }
    }
}
