package com.example.watcher.agentframework

import com.example.watcher.agentframework.autonomy.AgentSignal
import com.example.watcher.agentframework.autonomy.AutonomousAgentConfig
import com.example.watcher.agentframework.autonomy.AutonomousAgentRuntime
import com.example.watcher.agentframework.autonomy.AutonomousLifecycleState
import com.example.watcher.agentframework.autonomy.InMemoryCommunicationHub
import com.example.watcher.agentframework.autonomy.SignalChannel
import com.example.watcher.agentframework.autonomy.defaultAutonomousModules
import com.example.watcher.agentframework.core.AgentAction
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentDecision
import com.example.watcher.agentframework.runtime.AgentBrain
import com.example.watcher.agentframework.runtime.AgentBrainRequest
import com.example.watcher.agentframework.tools.AgentToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousAgentRuntimeTest {

    @Test
    fun closedLoopRuntimeRunsToStop() = runBlocking {
        val brain = object : AgentBrain {
            override suspend fun decide(request: AgentBrainRequest): AgentDecision {
                return AgentDecision(
                    reply = "task completed",
                    action = AgentAction.Finish(
                        reason = "done",
                        success = true
                    )
                )
            }
        }

        val runtime = AutonomousAgentRuntime(
            definition = AgentDefinition(
                agentId = "auto_1",
                name = "Auto Agent",
                systemInstruction = "Complete the task safely",
                goal = "Finish when the job is complete"
            ),
            config = AutonomousAgentConfig(
                maxCycles = 4,
                loopDelayMillis = 1L
            ),
            modules = defaultAutonomousModules(
                brain = brain,
                toolRegistry = AgentToolRegistry()
            ),
            parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        runtime.submitSignal(
            AgentSignal(
                channel = SignalChannel.User,
                content = "begin"
            )
        )
        runtime.start()
        val finalSnapshot = runtime.awaitCompletion()

        assertEquals(AutonomousLifecycleState.Stopped, finalSnapshot.lifecycleState)
        assertEquals(1, finalSnapshot.cycle)
        assertTrue(finalSnapshot.outputs.contains("task completed"))
        assertTrue(finalSnapshot.records.isNotEmpty())
    }

    @Test
    fun runtimeCapsRetainedOutputsInCommunicationHub() = runBlocking {
        var cycle = 0
        val brain = object : AgentBrain {
            override suspend fun decide(request: AgentBrainRequest): AgentDecision {
                cycle += 1
                return if (cycle < 5) {
                    AgentDecision(
                        reply = "step-$cycle",
                        action = AgentAction.Continue
                    )
                } else {
                    AgentDecision(
                        reply = "step-$cycle",
                        action = AgentAction.Finish(reason = "done", success = true)
                    )
                }
            }
        }

        val runtime = AutonomousAgentRuntime(
            definition = AgentDefinition(
                agentId = "auto_cap",
                name = "Capped Agent",
                systemInstruction = "Keep only recent outputs",
                goal = "Run through several cycles"
            ),
            config = AutonomousAgentConfig(
                maxCycles = 6,
                loopDelayMillis = 1L
            ),
            modules = defaultAutonomousModules(
                brain = brain,
                toolRegistry = AgentToolRegistry(),
                communicationHub = InMemoryCommunicationHub(maxOutputsPerSession = 2)
            ),
            parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        runtime.submitSignal(
            AgentSignal(
                channel = SignalChannel.User,
                content = "begin"
            )
        )
        runtime.start()
        val finalSnapshot = runtime.awaitCompletion()

        assertEquals(AutonomousLifecycleState.Stopped, finalSnapshot.lifecycleState)
        assertEquals(listOf("step-4", "step-5"), finalSnapshot.outputs)
    }
}
