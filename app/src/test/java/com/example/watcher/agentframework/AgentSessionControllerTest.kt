package com.example.watcher.agentframework

import com.example.watcher.agentframework.core.AgentAction
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentDecision
import com.example.watcher.agentframework.core.AgentMemoryScope
import com.example.watcher.agentframework.core.AgentMemoryWrite
import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.core.AgentSessionStatus
import com.example.watcher.agentframework.core.AgentStopReason
import com.example.watcher.agentframework.core.AgentToolCall
import com.example.watcher.agentframework.core.AgentToolDefinition
import com.example.watcher.agentframework.core.AgentToolParameter
import com.example.watcher.agentframework.core.AgentToolResult
import com.example.watcher.agentframework.runtime.AgentBrain
import com.example.watcher.agentframework.runtime.AgentBrainRequest
import com.example.watcher.agentframework.runtime.AgentKernel
import com.example.watcher.agentframework.tools.AgentTool
import com.example.watcher.agentframework.tools.AgentToolContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionControllerTest {

    @Test
    fun autonomousLoopExecutesToolAndFinishes() = runBlocking {
        val kernel = AgentKernel().registerTool(
            object : AgentTool {
                override val definition = AgentToolDefinition(
                    name = "fetch_fact",
                    description = "Returns a fixed fact",
                    parameters = listOf(
                        AgentToolParameter(
                            name = "topic",
                            type = "string",
                            description = "Fact topic",
                            required = true
                        )
                    )
                )

                override suspend fun execute(
                    call: AgentToolCall,
                    context: AgentToolContext
                ): AgentToolResult {
                    return AgentToolResult(
                        callId = call.id,
                        toolName = call.name,
                        success = true,
                        output = mapOf("fact" to "done:${call.arguments["topic"]}")
                    )
                }
            }
        )

        var invocation = 0
        val brain = object : AgentBrain {
            override suspend fun decide(request: AgentBrainRequest): AgentDecision {
                invocation += 1
                return if (invocation == 1) {
                    AgentDecision(
                        thinking = "Need a tool first",
                        memoryWrites = listOf(
                            AgentMemoryWrite(
                                scope = AgentMemoryScope.Working,
                                content = "tool required"
                            )
                        ),
                        action = AgentAction.UseTools(
                            calls = listOf(
                                AgentToolCall(
                                    id = "call_1",
                                    name = "fetch_fact",
                                    arguments = mapOf("topic" to "status")
                                )
                            )
                        )
                    )
                } else {
                    AgentDecision(
                        reply = "Final answer ready",
                        action = AgentAction.Finish(
                            reason = "Goal achieved",
                            success = true
                        )
                    )
                }
            }
        }

        val session = kernel.createSession(
            definition = AgentDefinition(
                agentId = "agent_a",
                name = "Agent A",
                systemInstruction = "Use tools when needed",
                goal = "Reach a final answer"
            ),
            brain = brain
        )

        session.start()
        val finalSnapshot = session.awaitCompletion()

        assertEquals(AgentSessionStatus.Completed, finalSnapshot.status)
        assertEquals(AgentStopReason.GoalAchieved, finalSnapshot.stopReason)
        assertEquals(2, finalSnapshot.stepCount)
        assertEquals("Final answer ready", finalSnapshot.lastReply)
        assertTrue(finalSnapshot.turns.first().toolResults.first().success)
        assertTrue(finalSnapshot.history.any { it.content.contains("fetch_fact") })

        kernel.shutdown()
    }

    @Test
    fun waitLoopStopsAfterIdleBudget() = runBlocking {
        val kernel = AgentKernel()
        val brain = object : AgentBrain {
            override suspend fun decide(request: AgentBrainRequest): AgentDecision {
                return AgentDecision(
                    action = AgentAction.Wait(
                        reason = "No new signal",
                        resumeAfterMillis = 1L
                    )
                )
            }
        }

        val session = kernel.createSession(
            definition = AgentDefinition(
                agentId = "agent_wait",
                name = "Wait Agent",
                systemInstruction = "Wait when there is no work",
                goal = "Pause safely"
            ),
            brain = brain,
            config = AgentRunConfig(
                maxIdleTurns = 2,
                defaultWaitMillis = 1L,
                maxWaitMillis = 1L
            )
        )

        session.start()
        val finalSnapshot = session.awaitCompletion()

        assertEquals(AgentSessionStatus.Stopped, finalSnapshot.status)
        assertEquals(AgentStopReason.IdleLimitReached, finalSnapshot.stopReason)
        assertEquals(2, finalSnapshot.stepCount)

        kernel.shutdown()
    }
}
