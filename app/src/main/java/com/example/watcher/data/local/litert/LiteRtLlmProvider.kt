package com.example.watcher.data.local.litert

import android.content.Context
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.remote.LlmProvider
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class LiteRtLlmProvider(
    private val engineManager: LiteRtEngineManager,
    private val context: Context
) : LlmProvider {

    override val id: String = LITERT_PROVIDER_ID
    override val displayName: String = "LiteRT Local Model"

    override suspend fun chat(
        systemPrompt: String,
        messages: List<ChatMessage>,
        imageDataUri: String?
    ): String {
        check(engineManager.isReady()) {
            "Local model engine is not ready. Current state: ${engineManager.status.value.state}"
        }
        return engineManager.withConversation(systemPrompt) { conversation ->
            var lastResponse = ""
            for (message in messages) {
                if (message.role == "user") {
                    val response = conversation.sendMessage(message.content)
                    lastResponse = extractText(response)
                }
            }
            lastResponse
        }
    }

    fun chatStream(
        systemPrompt: String,
        messages: List<ChatMessage>,
        imageFilePath: String? = null
    ): Flow<String> = channelFlow {
        check(engineManager.isReady()) {
            "Local model engine is not ready. Current state: ${engineManager.status.value.state}"
        }
        engineManager.withConversation(systemPrompt) { conversation ->
            // Replay history except the last user message
            for (i in 0 until (messages.size - 1)) {
                val msg = messages[i]
                if (msg.role == "user") {
                    conversation.sendMessage(msg.content)
                }
            }
            // Stream the last message
            val lastMessage = messages.last()
            val streamFlow = if (imageFilePath != null) {
                conversation.sendMessageAsync(
                    Contents.of(
                        Content.ImageFile(imageFilePath),
                        Content.Text(lastMessage.content)
                    )
                )
            } else {
                conversation.sendMessageAsync(lastMessage.content)
            }
            streamFlow.collect { chunk ->
                send(extractText(chunk))
            }
        }
    }

    private fun extractText(message: Message): String {
        try {
            val textProp = message::class.members.firstOrNull { it.name == "text" }
            if (textProp != null) {
                val value = textProp.call(message)
                if (value is String) return value
            }
        } catch (_: Exception) {}

        try {
            val getter = message::class.java.getMethod("getText")
            val value = getter.invoke(message)
            if (value is String) return value
        } catch (_: Exception) {}

        val raw = message.toString()
        val cleaned = raw
            .replace(Regex("^Message\\(text="), "")
            .replace(Regex("\\)$"), "")
        return String(cleaned.toByteArray(Charsets.UTF_8), Charsets.UTF_8)
    }


    companion object {
        const val LITERT_PROVIDER_ID = "litert_local"
    }
}
