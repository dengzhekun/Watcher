package com.example.watcher.data.repository

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jcodec.api.android.AndroidSequenceEncoder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class MonitorSessionRecorder {
    private var recordingJob: Job? = null
    private var outputFile: File? = null
    private val capturedFrameCount = AtomicInteger(0)

    fun start(
        scope: CoroutineScope,
        file: File,
        samplingFps: Int,
        frameProvider: () -> Bitmap?
    ) {
        stopWithoutJoin()
        outputFile = file
        capturedFrameCount.set(0)
        recordingJob = scope.launch(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val frameIntervalMs = (1_000L / samplingFps.coerceAtLeast(1)).coerceAtLeast(250L)
            val encoder = AndroidSequenceEncoder.createSequenceEncoder(
                file,
                samplingFps.coerceAtLeast(1)
            )

            try {
                while (isActive) {
                    val bitmap = frameProvider()
                        ?.let(::normalizeBitmapForEncoding)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    if (bitmap != null) {
                        encoder.encodeImage(bitmap)
                        capturedFrameCount.incrementAndGet()
                    }
                    delay(frameIntervalMs)
                }
            } finally {
                runCatching { encoder.finish() }
                if (capturedFrameCount.get() == 0) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    suspend fun stop(): String? {
        val activeJob = recordingJob
        recordingJob = null
        activeJob?.cancelAndJoin()
        val file = outputFile
        outputFile = null
        return file
            ?.takeIf { it.exists() && capturedFrameCount.get() > 0 }
            ?.absolutePath
    }

    private fun stopWithoutJoin() {
        recordingJob?.cancel()
        recordingJob = null
        outputFile = null
        capturedFrameCount.set(0)
    }
}
