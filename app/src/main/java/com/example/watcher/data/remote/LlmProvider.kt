package com.example.watcher.data.remote

data class ChatMessage(
    val role: String,
    val content: String,
    val imageDataUri: String? = null
)

interface LlmProvider {
    val id: String
    val displayName: String

    suspend fun chat(
        systemPrompt: String,
        messages: List<ChatMessage>,
        imageDataUri: String? = null
    ): String
}
