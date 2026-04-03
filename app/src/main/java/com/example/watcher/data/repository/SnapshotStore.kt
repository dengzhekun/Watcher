package com.example.watcher.data.repository

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnapshotStore(
    private val context: Context
) {
    fun save(bitmap: Bitmap): String? {
        return save(
            bitmap = bitmap,
            directory = "Snapshots",
            prefix = "SNAPSHOT"
        )
    }

    fun save(
        bitmap: Bitmap,
        directory: String,
        prefix: String,
        quality: Int = 90
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(context.getExternalFilesDir(directory), "${prefix}_$timestamp.jpg")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun importImage(
        inputStream: InputStream,
        directory: String,
        prefix: String,
        extension: String = "jpg"
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val normalizedExtension = extension.trimStart('.').ifBlank { "jpg" }
            val file = File(
                context.getExternalFilesDir(directory),
                "${prefix}_$timestamp.$normalizedExtension"
            )
            file.parentFile?.mkdirs()
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun createFile(
        directory: String,
        fileName: String
    ): File? {
        return try {
            File(context.getExternalFilesDir(directory), fileName).also {
                it.parentFile?.mkdirs()
            }
        } catch (_: Exception) {
            null
        }
    }
}
