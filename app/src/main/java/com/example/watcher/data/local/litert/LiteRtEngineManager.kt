package com.example.watcher.data.local.litert

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtEngineManager(private val context: Context) {

    private val mutex = Mutex()
    private val _status = MutableStateFlow(
        LiteRtEngineStatus(state = LiteRtEngineState.NotConfigured)
    )
    val status: StateFlow<LiteRtEngineStatus> = _status.asStateFlow()

    private var engine: Engine? = null
    private var currentConfig: LiteRtModelConfig? = null

    fun isReady(): Boolean = _status.value.state == LiteRtEngineState.Ready

    suspend fun initialize(config: LiteRtModelConfig): Result<Unit> = mutex.withLock {
        try {
            // Close existing engine if loaded with a different config
            engine?.let { existing ->
                _status.value = LiteRtEngineStatus(
                    state = LiteRtEngineState.Closing,
                    modelConfig = currentConfig
                )
                runCatching { existing.close() }
                engine = null
                currentConfig = null
            }

            // Validate model file exists
            val modelFile = File(config.modelPath)
            if (!modelFile.exists()) {
                val error = "Model file not found: ${config.modelPath}"
                _status.value = LiteRtEngineStatus(
                    state = LiteRtEngineState.Error,
                    modelConfig = config,
                    errorMessage = error
                )
                return Result.failure(IllegalArgumentException(error))
            }

            _status.value = LiteRtEngineStatus(
                state = LiteRtEngineState.Initializing,
                modelConfig = config
            )

            val startTime = System.currentTimeMillis()

            val engineConfig = EngineConfig(
                modelPath = config.modelPath,
                backend = mapBackend(config.backend),
                visionBackend = config.visionBackend?.let { mapBackend(it) },
                audioBackend = config.audioBackend?.let { mapBackend(it) },
                cacheDir = context.cacheDir.path
            )

            val newEngine = withContext(Dispatchers.IO) {
                Engine(engineConfig).also { it.initialize() }
            }

            val durationMs = System.currentTimeMillis() - startTime
            engine = newEngine
            currentConfig = config

            _status.value = LiteRtEngineStatus(
                state = LiteRtEngineState.Ready,
                modelConfig = config,
                initDurationMs = durationMs
            )

            Log.i(TAG, "LiteRT engine initialized in ${durationMs}ms: ${config.modelPath}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "LiteRT engine initialization failed", e)
            engine = null
            currentConfig = null
            _status.value = LiteRtEngineStatus(
                state = LiteRtEngineState.Error,
                modelConfig = config,
                errorMessage = e.message ?: "Unknown initialization error"
            )
            Result.failure(e)
        }
    }

    suspend fun <T> withConversation(
        systemInstruction: String,
        block: suspend (Conversation) -> T
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val activeEngine = engine
                ?: throw IllegalStateException("Engine is not initialized")
            val config = currentConfig
                ?: throw IllegalStateException("Engine config is missing")

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(
                    topK = config.topK,
                    topP = config.topP.toDouble(),
                    temperature = config.temperature.toDouble()
                )
            )

            val conversation = activeEngine.createConversation(conversationConfig)
            try {
                block(conversation)
            } finally {
                conversation.close()
            }
        }
    }

    suspend fun close() = mutex.withLock {
        engine?.let { existing ->
            _status.value = LiteRtEngineStatus(
                state = LiteRtEngineState.Closing,
                modelConfig = currentConfig
            )
            runCatching { existing.close() }
            engine = null
            currentConfig = null
            _status.value = LiteRtEngineStatus(state = LiteRtEngineState.NotConfigured)
            Log.i(TAG, "LiteRT engine closed")
        }
    }

    private fun mapBackend(type: LiteRtBackendType): Backend = when (type) {
        LiteRtBackendType.GPU -> Backend.GPU()
        LiteRtBackendType.NPU -> Backend.NPU(
            nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        )
        LiteRtBackendType.CPU -> Backend.CPU()
    }

    private companion object {
        const val TAG = "LiteRtEngine"
    }
}
