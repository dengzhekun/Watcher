package com.example.watcher.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    override val id: String,
    override val displayName: String,
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun chat(
        systemPrompt: String,
        messages: List<ChatMessage>,
        imageDataUri: String?
    ): String = withContext(Dispatchers.IO) {
        val apiMessages = mutableListOf<ApiMessage>()

        // System message
        apiMessages.add(ApiMessage(
            role = "system",
            content = listOf(ApiContent(type = "text", text = systemPrompt))
        ))

        // User/assistant messages
        for (msg in messages) {
            val contentItems = mutableListOf<ApiContent>()
            if (msg.imageDataUri != null) {
                contentItems.add(ApiContent(
                    type = "image_url",
                    imageUrl = ApiImageUrl(url = msg.imageDataUri)
                ))
            }
            contentItems.add(ApiContent(type = "text", text = msg.content))
            apiMessages.add(ApiMessage(role = msg.role, content = contentItems))
        }

        // If there's a standalone image (e.g., current frame), attach to last user message
        if (imageDataUri != null && messages.none { it.imageDataUri != null }) {
            val lastUserIdx = apiMessages.indexOfLast { it.role == "user" }
            if (lastUserIdx >= 0) {
                val existing = apiMessages[lastUserIdx]
                apiMessages[lastUserIdx] = existing.copy(
                    content = listOf(ApiContent(
                        type = "image_url",
                        imageUrl = ApiImageUrl(url = imageDataUri)
                    )) + existing.content
                )
            }
        }

        val requestBody = ApiRequest(
            model = modelName,
            messages = apiMessages
        )

        val jsonBody = gson.toJson(requestBody)
            .toRequestBody("application/json".toMediaType())

        val url = endpoint.toChatCompletionsUrl()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "unknown error"
            throw RuntimeException("LLM API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
            ?: throw RuntimeException("LLM API returned empty body")
        val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)

        apiResponse.choices?.firstOrNull()?.message?.content
            ?: throw RuntimeException("LLM API returned no choices")
    }

    // --- API models (OpenAI-compatible format) ---

    private data class ApiRequest(
        val model: String,
        val messages: List<ApiMessage>
    )

    private data class ApiMessage(
        val role: String,
        val content: List<ApiContent>
    )

    private data class ApiContent(
        val type: String,
        val text: String? = null,
        @SerializedName("image_url") val imageUrl: ApiImageUrl? = null
    )

    private data class ApiImageUrl(
        val url: String
    )

    private data class ApiResponse(
        val choices: List<ApiChoice>? = null
    )

    private data class ApiChoice(
        val message: ApiChoiceMessage? = null
    )

    private data class ApiChoiceMessage(
        val content: String? = null
    )
}

private fun String.toChatCompletionsUrl(): String {
    val trimmed = trim().trimEnd('/')
    val normalizedBase = trimmed.removeKnownApiSuffix("/chat/completions")
        .removeKnownApiSuffix("/responses")
    return "$normalizedBase/chat/completions"
}

private fun String.removeKnownApiSuffix(suffix: String): String {
    return if (endsWith(suffix, ignoreCase = true)) {
        dropLast(suffix.length)
    } else {
        this
    }
}
