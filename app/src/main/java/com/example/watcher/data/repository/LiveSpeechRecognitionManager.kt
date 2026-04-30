package com.example.watcher.data.repository

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import com.example.watcher.data.model.LiveSpeechState

interface LiveSpeechRecognizer {
    val state: StateFlow<LiveSpeechState>
    var onSpeechResult: ((String) -> Unit)?

    fun start()
    fun stop()
    fun release()
    fun setMicEnabled(enabled: Boolean)
    fun getFinalTranscripts(): List<Pair<Long, String>>
}

class LiveSpeechRecognitionManager(
    context: Context,
    memoryManager: CommentaryMemoryManager
) : LiveSpeechRecognizer {
    private val backend: LiveSpeechRecognizer = VolcengineLiveSpeechRecognizer(
        context = context,
        memoryManager = memoryManager
    )

    override val state: StateFlow<LiveSpeechState>
        get() = backend.state

    override var onSpeechResult: ((String) -> Unit)?
        get() = backend.onSpeechResult
        set(value) {
            backend.onSpeechResult = value
        }

    override fun start() = backend.start()

    override fun stop() = backend.stop()

    override fun release() = backend.release()

    override fun setMicEnabled(enabled: Boolean) = backend.setMicEnabled(enabled)

    override fun getFinalTranscripts(): List<Pair<Long, String>> = backend.getFinalTranscripts()
}
