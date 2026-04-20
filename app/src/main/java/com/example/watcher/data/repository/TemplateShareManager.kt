package com.example.watcher.data.repository

import android.util.Base64
import com.example.watcher.data.model.CouncilExpertEntity
import com.example.watcher.data.model.CouncilTemplateEntity
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
 *   Share a Watcher template: [template name] #WTR#{base64_data}
 *
 * The base64 payload is DEFLATE-compressed JSON:
 *   { "type": "monitor"|"video"|"council"|"council_expert", "version": 1, "data": { ...template fields... } }
 */
object TemplateShareManager {

    private const val SHARE_MARKER = "#WTR#"
    private const val VERSION = 1
    private val gson = Gson()

    fun exportMonitorTemplate(template: MonitorTemplateEntity): String {
        return exportTemplate(type = "monitor", label = template.label, template = template)
    }

    fun exportVideoTemplate(template: VideoTemplateEntity): String {
        return exportTemplate(type = "video", label = template.label, template = template)
    }

    fun exportCouncilTemplate(template: CouncilTemplateEntity): String {
        return exportTemplate(type = "council", label = template.label, template = template)
    }

    fun exportCouncilExpertTemplate(expert: CouncilExpertEntity): String {
        return exportTemplate(type = "council_expert", label = expert.name, template = expert)
    }

    data class ImportResult(
        val type: String,
        val label: String,
        val monitorTemplate: MonitorTemplateEntity? = null,
        val videoTemplate: VideoTemplateEntity? = null,
        val councilTemplate: CouncilTemplateEntity? = null,
        val councilExpert: CouncilExpertEntity? = null
    )

    fun canImport(text: String): Boolean = text.contains(SHARE_MARKER)

    fun importTemplate(text: String): Result<ImportResult> = runCatching {
        val markerIdx = text.indexOf(SHARE_MARKER)
        require(markerIdx >= 0) { "Invalid shared template text." }

        val base64 = text.substring(markerIdx + SHARE_MARKER.length).trim()
        val jsonStr = decodeAndDecompress(base64)
        val json = gson.fromJson(jsonStr, JsonObject::class.java)

        val type = json.get("type").asString
        val data = json.getAsJsonObject("data")

        when (type) {
            "monitor" -> {
                val template = gson.fromJson(data, MonitorTemplateEntity::class.java)
                ImportResult(
                    type = "monitor",
                    label = template.label,
                    monitorTemplate = template.importedCopy()
                )
            }

            "video" -> {
                val template = gson.fromJson(data, VideoTemplateEntity::class.java)
                ImportResult(
                    type = "video",
                    label = template.label,
                    videoTemplate = template.importedCopy()
                )
            }

            "council" -> {
                val template = gson.fromJson(data, CouncilTemplateEntity::class.java)
                ImportResult(
                    type = "council",
                    label = template.label,
                    councilTemplate = template.importedCopy()
                )
            }

            "council_expert" -> {
                val expert = gson.fromJson(data, CouncilExpertEntity::class.java)
                ImportResult(
                    type = "council_expert",
                    label = expert.name,
                    councilExpert = expert
                )
            }

            else -> error("Unknown template type: $type")
        }
    }

    private fun exportTemplate(type: String, label: String, template: Any): String {
        val json = JsonObject().apply {
            addProperty("type", type)
            addProperty("version", VERSION)
            add("data", gson.toJsonTree(template))
        }
        val base64 = compressAndEncode(json.toString())
        return "Share a Watcher template: [$label] $SHARE_MARKER$base64"
    }

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

    private fun MonitorTemplateEntity.importedCopy(): MonitorTemplateEntity {
        return copy(
            templateId = importedTemplateId(),
            isDefault = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun VideoTemplateEntity.importedCopy(): VideoTemplateEntity {
        return copy(
            templateId = importedTemplateId(),
            isDefault = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun CouncilTemplateEntity.importedCopy(): CouncilTemplateEntity {
        return copy(
            templateId = importedTemplateId(),
            isDefault = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun importedTemplateId(): String {
        return "imported_${UUID.randomUUID().toString().take(8)}"
    }
}
