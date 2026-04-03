package com.example.watcher.data.repository

import android.util.Base64
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.VideoTemplateEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Template share/import via Base64 encoded text.
 *
 * Share format:
 *   我向你分享了一个模板skill~,[模板名称]打开Watcher查看吧！#WTR#{base64_data}
 *
 * The base64 payload is DEFLATE-compressed JSON:
 *   { "type": "monitor"|"video", "version": 1, "data": { ...template fields... } }
 */
object TemplateShareManager {

    private const val SHARE_MARKER = "#WTR#"
    private const val VERSION = 1
    private val gson = Gson()

    // --- Export ---

    fun exportMonitorTemplate(template: MonitorTemplateEntity): String {
        val json = JsonObject().apply {
            addProperty("type", "monitor")
            addProperty("version", VERSION)
            add("data", gson.toJsonTree(template))
        }
        val base64 = compressAndEncode(json.toString())
        return "我向你分享了一个模板skill~,[${template.label}]打开Watcher查看吧！${SHARE_MARKER}${base64}"
    }

    fun exportVideoTemplate(template: VideoTemplateEntity): String {
        val json = JsonObject().apply {
            addProperty("type", "video")
            addProperty("version", VERSION)
            add("data", gson.toJsonTree(template))
        }
        val base64 = compressAndEncode(json.toString())
        return "我向你分享了一个模板skill~,[${template.label}]打开Watcher查看吧！${SHARE_MARKER}${base64}"
    }

    // --- Import ---

    data class ImportResult(
        val type: String,  // "monitor" or "video"
        val label: String,
        val monitorTemplate: MonitorTemplateEntity? = null,
        val videoTemplate: VideoTemplateEntity? = null
    )

    fun canImport(text: String): Boolean = text.contains(SHARE_MARKER)

    fun importTemplate(text: String): Result<ImportResult> = runCatching {
        val markerIdx = text.indexOf(SHARE_MARKER)
        require(markerIdx >= 0) { "无效的分享文本" }

        val base64 = text.substring(markerIdx + SHARE_MARKER.length).trim()
        val jsonStr = decodeAndDecompress(base64)
        val json = gson.fromJson(jsonStr, JsonObject::class.java)

        val type = json.get("type").asString
        val data = json.getAsJsonObject("data")

        when (type) {
            "monitor" -> {
                val template = gson.fromJson(data, MonitorTemplateEntity::class.java)
                // Assign new ID to avoid conflict
                val imported = template.copy(
                    templateId = "imported_${UUID.randomUUID().toString().take(8)}",
                    label = template.label,
                    isDefault = false,
                    updatedAt = System.currentTimeMillis()
                )
                ImportResult(type = "monitor", label = imported.label, monitorTemplate = imported)
            }
            "video" -> {
                val template = gson.fromJson(data, VideoTemplateEntity::class.java)
                val imported = template.copy(
                    templateId = "imported_${UUID.randomUUID().toString().take(8)}",
                    label = template.label,
                    isDefault = false,
                    updatedAt = System.currentTimeMillis()
                )
                ImportResult(type = "video", label = imported.label, videoTemplate = imported)
            }
            else -> error("未知模板类型: $type")
        }
    }

    // --- Compression ---

    private fun compressAndEncode(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()

        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeAndDecompress(base64: String): String {
        val compressed = Base64.decode(base64, Base64.NO_WRAP)
        val inflater = Inflater()
        inflater.setInput(compressed)

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            output.write(buffer, 0, count)
        }
        inflater.end()

        return output.toString(Charsets.UTF_8.name())
    }
}
