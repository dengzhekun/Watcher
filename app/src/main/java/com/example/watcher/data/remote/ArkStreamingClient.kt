package com.example.watcher.data.remote

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ArkStreamingClient {
    suspend fun streamResponse(
        authorization: String,
        requestPayload: Any,
        onEvent: suspend (ArkResponseStreamEvent) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val requestBody = RetrofitClient.gson.toJson(requestPayload)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}api/v3/responses")
            .header("Authorization", authorization)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody)
            .build()

        val accumulatedText = StringBuilder()
        RetrofitClient.streamingOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.takeIf { it.isNotBlank() }
                throw IOException(detail ?: "Streaming request failed with HTTP ${response.code}")
            }

            val source = response.body?.source()
                ?: throw IOException("Streaming response body is empty.")

            var eventName: String? = null
            val dataLines = mutableListOf<String>()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) {
                    emitEvent(
                        eventName = eventName,
                        payload = dataLines.joinToString("\n"),
                        accumulatedText = accumulatedText,
                        onEvent = onEvent
                    )
                    eventName = null
                    dataLines.clear()
                    continue
                }

                when {
                    line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                    line.startsWith("data:") -> dataLines += line.substringAfter("data:").trimStart()
                }
            }

            if (dataLines.isNotEmpty()) {
                emitEvent(
                    eventName = eventName,
                    payload = dataLines.joinToString("\n"),
                    accumulatedText = accumulatedText,
                    onEvent = onEvent
                )
            }
        }

        accumulatedText.toString()
    }

    private suspend fun emitEvent(
        eventName: String?,
        payload: String,
        accumulatedText: StringBuilder,
        onEvent: suspend (ArkResponseStreamEvent) -> Unit
    ) {
        if (payload.isBlank() || payload == "[DONE]") {
            return
        }

        val json = runCatching {
            RetrofitClient.gson.fromJson(payload, JsonObject::class.java)
        }.getOrNull() ?: return

        val type = json.getAsJsonPrimitive("type")?.asString
            ?.takeIf { it.isNotBlank() }
            ?: eventName.orEmpty()

        // 支持两种事件类型格式：带前缀 (response.output_text.delta) 和不带前缀 (output_text.delta)
        val normalizedType = type.removePrefix("response.")

        when {
            normalizedType.endsWith("output_text.delta") -> {
                val delta = json.getAsJsonPrimitive("delta")?.asString.orEmpty()
                if (delta.isBlank()) return
                accumulatedText.append(delta)
                onEvent(
                    ArkResponseStreamEvent.OutputTextDelta(
                        delta = delta,
                        fullText = accumulatedText.toString()
                    )
                )
            }

            normalizedType.endsWith("output_text.done") -> {
                onEvent(
                    ArkResponseStreamEvent.OutputTextDone(
                        fullText = accumulatedText.toString()
                    )
                )
            }

            normalizedType.endsWith("completed") -> {
                onEvent(
                    ArkResponseStreamEvent.Completed(
                        fullText = accumulatedText.toString()
                    )
                )
            }

            normalizedType.endsWith("failed") || type.contains("error", ignoreCase = true) -> {
                val message = json.getAsJsonPrimitive("message")?.asString
                    ?: json.getAsJsonPrimitive("error")?.asString
                    ?: payload
                throw IOException(message)
            }
        }
    }
}

sealed interface ArkResponseStreamEvent {
    data class OutputTextDelta(
        val delta: String,
        val fullText: String
    ) : ArkResponseStreamEvent

    data class OutputTextDone(
        val fullText: String
    ) : ArkResponseStreamEvent

    data class Completed(
        val fullText: String
    ) : ArkResponseStreamEvent
}
