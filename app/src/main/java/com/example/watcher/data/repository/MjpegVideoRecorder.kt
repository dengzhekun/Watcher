package com.example.watcher.data.repository

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jcodec.api.android.AndroidSequenceEncoder
import java.io.File

class MjpegVideoRecorder {
    suspend fun recordSegment(
        outputFile: File,
        durationSeconds: Int,
        samplingFps: Int,
        frameProvider: () -> Bitmap?
    ): RecordingResult = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val frameIntervalMs = (1_000L / samplingFps.coerceAtLeast(1)).coerceAtLeast(125L)
        val durationMs = durationSeconds.coerceAtLeast(1) * 1_000L
        val encoder = AndroidSequenceEncoder.createSequenceEncoder(
            outputFile,
            samplingFps.coerceAtLeast(1)
        )
        var capturedFrameCount = 0
        val startedAt = System.currentTimeMillis()

        try {
            while (System.currentTimeMillis() - startedAt < durationMs) {
                val bitmap = frameProvider()
                    ?.let(::normalizeBitmapForEncoding)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                if (bitmap != null) {
                    encoder.encodeImage(bitmap)
                    capturedFrameCount += 1
                }
                delay(frameIntervalMs)
            }
        } finally {
            runCatching { encoder.finish() }
        }

        if (capturedFrameCount == 0) {
            outputFile.delete()
            throw IllegalStateException("录制期间没有采集到可用视频帧。")
        }

        RecordingResult(
            file = outputFile,
            capturedFrameCount = capturedFrameCount,
            durationSeconds = durationSeconds
        )
    }
}

internal fun normalizeBitmapForEncoding(bitmap: Bitmap): Bitmap {
    val safeWidth = bitmap.width - (bitmap.width % 2)
    val safeHeight = bitmap.height - (bitmap.height % 2)
    if (safeWidth == bitmap.width && safeHeight == bitmap.height) {
        return bitmap
    }
    return Bitmap.createScaledBitmap(bitmap, safeWidth.coerceAtLeast(2), safeHeight.coerceAtLeast(2), true)
}

data class RecordingResult(
    val file: File,
    val capturedFrameCount: Int,
    val durationSeconds: Int
)
