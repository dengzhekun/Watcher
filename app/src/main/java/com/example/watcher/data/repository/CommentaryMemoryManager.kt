package com.example.watcher.data.repository

import android.util.Log
import com.example.watcher.data.remote.ContentItem
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.Message
import com.example.watcher.data.remote.extractOutputText
import com.example.watcher.data.model.CommentaryPromptProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CommentaryMemoryManager(
    private val apiService: DoubaoApiService,
    private val llmWalletRepository: LlmWalletRepository,
    private val promptProfile: CommentaryPromptProfile = CommentaryPromptProfile.liveRoom()
) {
    companion object {
        private const val TAG = "CommentaryMemory"
        const val B_COMPRESS_THRESHOLD = 10
        const val A_COMPRESS_THRESHOLD = 10
        const val RECENT_VISUAL_COUNT = 3
    }

    var memoryA: String = ""
        private set
    var latestMemoryB: String = ""
        private set

    val recentVisual = ArrayDeque<Pair<Long, String>>() // (timestamp, text)
    val rawSinceLastB = mutableListOf<String>() // mixed visual+speech, for B compression
    val bSinceLastA = mutableListOf<String>()
    private val mutex = Mutex()

    /** Called when a visual commentary segment completes. */
    suspend fun onNewCommentary(text: String) = mutex.withLock {
        recentVisual.addLast(System.currentTimeMillis() to text)
        while (recentVisual.size > RECENT_VISUAL_COUNT) {
            recentVisual.removeAt(0)
        }
        rawSinceLastB.add("[画面] $text")
        if (rawSinceLastB.size >= B_COMPRESS_THRESHOLD) {
            compressToMemoryB()
        }
    }

    /** Called when a speech transcript is finalized. Only feeds into B→A compression. */
    suspend fun onNewSpeech(text: String) = mutex.withLock {
        rawSinceLastB.add("[语音] $text")
        if (rawSinceLastB.size >= B_COMPRESS_THRESHOLD) {
            compressToMemoryB()
        }
    }

    fun buildContextBlock(): String = buildString {
        if (memoryA.isNotBlank()) {
            appendLine("【直播核心记忆】")
            appendLine(memoryA)
            appendLine()
        }
        if (latestMemoryB.isNotBlank()) {
            appendLine("【近期摘要】")
            appendLine(latestMemoryB)
            appendLine()
        }
        if (recentVisual.isNotEmpty()) {
            appendLine("【最近画面解说】")
            recentVisual.forEach { (_, text) -> appendLine("- $text") }
        }
    }

    fun reset() {
        memoryA = ""
        latestMemoryB = ""
        recentVisual.clear()
        rawSinceLastB.clear()
        bSinceLastA.clear()
    }

    private suspend fun compressToMemoryB() {
        val texts = rawSinceLastB.toList()
        rawSinceLastB.clear()

        Log.d(TAG, "Compressing ${texts.size} entries to memory B")
        try {
            val llmConfig = llmWalletRepository.resolveArkResponsesConfig(ArkConfig.intentModel)
            val prompt = buildString {
                appendLine(promptProfile.memoryBRole)
                appendLine()
                texts.forEachIndexed { i, t ->
                    appendLine("${i + 1}. $t")
                }
            }

            val response = apiService.analyzeIntent(
                authorization = llmConfig.bearerToken(),
                request = DoubaoRequest(
                    model = llmConfig.modelName,
                    input = listOf(
                        Message(
                            role = "user",
                            content = listOf(ContentItem(type = "input_text", text = prompt))
                        )
                    )
                )
            )

            val compressed = response.extractOutputText()
            if (!compressed.isNullOrBlank()) {
                latestMemoryB = compressed
                bSinceLastA.add(compressed)
                Log.d(TAG, "Memory B generated: ${compressed.take(60)}...")

                if (bSinceLastA.size >= A_COMPRESS_THRESHOLD) {
                    compressToMemoryA()
                }
            } else {
                Log.w(TAG, "Memory B compression returned empty")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Memory B compression failed", e)
        }
    }

    private suspend fun compressToMemoryA() {
        val bTexts = bSinceLastA.toList()
        bSinceLastA.clear()

        Log.d(TAG, "Compressing ${bTexts.size} memory B entries to memory A")
        try {
            val llmConfig = llmWalletRepository.resolveArkResponsesConfig(ArkConfig.intentModel)
            val prompt = buildString {
                appendLine(promptProfile.memoryARole)
                appendLine()
                appendLine("当前核心记忆：${memoryA.ifBlank { "暂无" }}")
                appendLine()
                appendLine("最近摘要：")
                bTexts.forEachIndexed { i, t ->
                    appendLine("${i + 1}. $t")
                }
            }

            val response = apiService.analyzeIntent(
                authorization = llmConfig.bearerToken(),
                request = DoubaoRequest(
                    model = llmConfig.modelName,
                    input = listOf(
                        Message(
                            role = "user",
                            content = listOf(ContentItem(type = "input_text", text = prompt))
                        )
                    )
                )
            )

            val compressed = response.extractOutputText()
            if (!compressed.isNullOrBlank()) {
                memoryA = compressed
                Log.d(TAG, "Memory A updated: ${compressed.take(60)}...")
            } else {
                Log.w(TAG, "Memory A compression returned empty")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Memory A compression failed", e)
        }
    }
}
