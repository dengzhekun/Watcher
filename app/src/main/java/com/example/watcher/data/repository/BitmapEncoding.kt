package com.example.watcher.data.repository

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object BitmapEncoding {
    fun toBase64(bitmap: Bitmap): String {
        val estimatedSize = (bitmap.width * bitmap.height).coerceAtLeast(1)
        val outputStream = ByteArrayOutputStream(estimatedSize)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun toDataUri(bitmap: Bitmap): String {
        return "data:image/jpeg;base64,${toBase64(bitmap)}"
    }
}
