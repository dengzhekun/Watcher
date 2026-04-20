package com.example.watcher.agentframework

import com.example.watcher.agentframework.core.AgentAction
import com.example.watcher.agentframework.core.AgentConversationItem
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.core.AgentSessionSnapshot
import com.example.watcher.agentframework.knowledge.AgentKnowledgeSnapshot
import com.example.watcher.agentframework.memory.AgentMemorySnapshot
import com.example.watcher.agentframework.runtime.AgentBrainRequest
import com.example.watcher.agentframework.runtime.AgentModelGateway
import com.example.watcher.agentframework.runtime.JsonProtocolAgentBrain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonProtocolAgentBrainTest {

    @Test
    fun parsesToolCallResponse() = runBlocking {
        val brain = JsonProtocolAgentBrain(
            gateway = object : AgentModelGateway {
                override suspend fun generate(
                    systemPrompt: String,
                    messages: List<AgentConversationItem>
                ): String {
                    return """
                        {
                          "thinking": "Need to inspect before answering",
                          "memory": [
                            { "scope": "working", "content": "pending inspection" }
                          ],
                          "action": {
                            "type": "tool_calls",
                            "calls": [
                              {
                                "id": "call_7",
                                "name": "inspect_target",
                                "arguments": { "target": "door" }
                              }
                            ]
                          }
                        }
                    """.trimIndent()
                }
            }
        )

        val decision = brain.decide(
            AgentBrainRequest(
                definition = AgentDefinition(
                    agentId = "agent_json",
                    name = "JSON Agent",
                    systemInstruction = "Return protocol JSON",
                    goal = "Use structured decisions"
                ),
                config = AgentRunConfig(),
                session = AgentSessionSnapshot(
                    sessionId = "session_1",
                    agentId = "agent_json",
                    agentName = "JSON Agent",
                    goal = "Use structured decisions"
                ),
                memory = AgentMemorySnapshot(
                    working = emptyList(),
                    episodic = emptyList()
                ),
                knowledge = AgentKnowledgeSnapshot(entries = emptyList()),
                recentInputs = emptyList(),
                availableTools = emptyList()
            )
        )

        val action = decision.action as AgentAction.UseTools
        assertEquals("Need to inspect before answering", decision.thinking)
        assertEquals(1, decision.memoryWrites.size)
        assertEquals("call_7", action.calls.first().id)
        assertEquals("inspect_target", action.calls.first().name)
        assertTrue(action.calls.first().arguments.containsKey("target"))
    }

    @Test
    fun fallsBackWhenResponseIsNotJson() = runBlocking {
        val brain = JsonProtocolAgentBrain(
            gateway = object : AgentModelGateway {
                override suspend fun generate(
                    systemPrompt: String,
                    messages: List<AgentConversationItem>
                ): String {
                    return "plain text output"
                }
            }
        )

        val decision = brain.decide(
            AgentBrainRequest(
                definition = AgentDefinition(
                    agentId = "agent_json",
                    name = "JSON Agent",
                    systemInstruction = "Return protocol JSON",
                    goal = "Use structured decisions"
                ),
                config = AgentRunConfig(),
                session = AgentSessionSnapshot(
                    sessionId = "session_2",
                    agentId = "agent_json",
                    agentName = "JSON Agent",
                    goal = "Use structured decisions"
                ),
                memory = AgentMemorySnapshot(
                    working = emptyList(),
                    episodic = emptyList()
                ),
                knowledge = AgentKnowledgeSnapshot(entries = emptyList()),
                recentInputs = emptyList(),
                availableTools = emptyList()
            )
        )

        val action = decision.action as AgentAction.Finish
        assertEquals(false, action.success)
        assertTrue(decision.reply!!.contains("plain text output"))
    }
}
