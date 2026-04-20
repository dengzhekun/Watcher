package com.example.watcher.data.agent.runtime

import com.example.watcher.data.agent.core.*
import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Backend that forwards agent requests to an external HTTP endpoint.
 * The external agent speaks the exact same protocol as LlmBackend.
 */
class HttpBackend(private val endpointUrl: String) : AgentBackend {

    private val gson = Gson()

    override suspend fun call(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentToolSchema>
    ): AgentResponse {
        val requestBody = mapOf(
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content, "tool_call_id" to it.toolCallId) },
            "system_prompt" to systemPrompt,
            "available_tools" to tools.map { tool ->
                mapOf("name" to tool.name, "description" to tool.description, "parameters" to tool.parameters)
            }
        )

        val url = URL("$endpointUrl/think")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(gson.toJson(requestBody))
        }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } else {
            val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "HTTP $responseCode"
            throw RuntimeException("Agent endpoint returned $responseCode: $errorBody")
        }

        return parseResponse(responseBody)
    }

    private fun parseResponse(raw: String): AgentResponse {
        @Suppress("UNCHECKED_CAST")
        val map = gson.fromJson(raw, Map::class.java) as? Map<String, Any?> ?: throw RuntimeException("Invalid response")

        return when (map["type"] as? String) {
            "tool_calls" -> {
                val calls = (map["calls"] as? List<Map<String, Any?>>)?.mapIndexed { i, c ->
                    @Suppress("UNCHECKED_CAST")
                    ToolCall(
                        id = c["id"] as? String ?: "call_$i",
                        name = c["name"] as? String ?: "",
                        arguments = c["arguments"] as? Map<String, Any?> ?: emptyMap()
                    )
                } ?: emptyList()
                AgentResponse.ToolCalls(calls)
            }
            "final_answer" -> {
                @Suppress("UNCHECKED_CAST")
                val op = map["opinion"] as? Map<String, Any?> ?: map
                AgentResponse.FinalAnswer(parseOpinion(op))
            }
            else -> throw RuntimeException("Unknown response type: ${map["type"]}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOpinion(map: Map<String, Any?>) = AgentOpinion(
        summary = (map["summary"] as? String)?.take(220) ?: "",
        findings = (map["findings"] as? List<String>)?.take(4) ?: emptyList(),
        risks = (map["risks"] as? List<String>)?.take(4) ?: emptyList(),
        nextActions = (map["nextActions"] as? List<String>)?.take(4) ?: emptyList(),
        voteLevel = (map["voteLevel"] as? String) ?: "pass",
        voteReason = (map["voteReason"] as? String)?.take(220) ?: "",
        confidence = ((map["confidence"] as? Number)?.toInt() ?: 50).coerceIn(0, 100)
    )
}
