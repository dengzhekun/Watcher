package com.example.watcher.data.repository

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jcodec.api.android.AndroidSequenceEncoder
import java.io.File

class VideoSegmentMerger {
    suspend fun mergeSegments(
        segmentFiles: List<File>,
        outputFile: File,
        samplingFps: Int
    ): File = withContext(Dispatchers.IO) {
        val validSegmentFiles = segmentFiles.filter { it.exists() && it.length() > 0L }
        require(validSegmentFiles.isNotEmpty()) { "No valid video segments were found to merge." }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val safeFps = samplingFps.coerceAtLeast(1)
        val frameIntervalUs = 1_000_000L / safeFps
        val encoder = AndroidSequenceEncoder.createSequenceEncoder(outputFile, safeFps)
        var encodedFrameCount = 0

        try {
            validSegmentFiles.forEach { segmentFile ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(segmentFile.absolutePath)
                    val durationUs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull()?.times(1_000L)?.coerceAtLeast(0L) ?: 0L
                    if (durationUs <= 0L) {
                        return@forEach
                    }

                    var timeUs = 0L
                    while (timeUs < durationUs) {
                        val frame = extractFrameAtTime(retriever, timeUs)
                        if (frame != null) {
                            encodeFrame(encoder, frame)
                            encodedFrameCount += 1
                        }
                        timeUs += frameIntervalUs
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }
        } finally {
            runCatching { encoder.finish() }
        }

        if (encodedFrameCount == 0) {
            outputFile.delete()
            throw IllegalStateException("No decodable frames were found in the recorded segments.")
        }

        outputFile
    }

    private fun extractFrameAtTime(
        retriever: MediaMetadataRetriever,
        timeUs: Long
    ): Bitmap? {
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun encodeFrame(
        encoder: AndroidSequenceEncoder,
        frame: Bitmap
    ) {
        val normalizedFrame = normalizeBitmapForEncoding(frame)
        var encodedFrame: Bitmap? = null

        try {
            encodedFrame = if (
                normalizedFrame.config == Bitmap.Config.ARGB_8888 && !normalizedFrame.isMutable
            ) {
                normalizedFrame
            } else {
                normalizedFrame.copy(Bitmap.Config.ARGB_8888, false)
            }
            encoder.encodeImage(encodedFrame)
        } finally {
            if (encodedFrame != null && encodedFrame !== normalizedFrame) {
                encodedFrame.recycle()
            }
            if (normalizedFrame != null && normalizedFrame !== frame) {
                normalizedFrame.recycle()
            }
            frame.recycle()
        }
    }
}
