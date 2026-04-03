package com.example.watcher.data.model

/**
 * A structured entity identified in the live scene.
 * Consumer A creates and maintains these through structured tags.
 */
data class SceneEntity(
    val id: String,                              // stable ID (e.g., "player_red_5", "obj_scoreboard")
    val name: String,                            // display name ("红队5号")
    val type: String,                            // "person", "object", "team", "animal", etc.
    val attributes: MutableMap<String, String>,   // key → value (服装→红色球衣, 编号→5, etc.)
    val notes: MutableList<String>,               // free-form observations
    val firstSeenSegment: Int,
    var lastSeenSegment: Int,
    var status: EntityStatus = EntityStatus.ACTIVE
) {
    /** Compact display for prompts */
    fun toPromptString(): String = buildString {
        append("$name($id) [$type]")
        if (status != EntityStatus.ACTIVE) append(" [${status.label}]")
        if (attributes.isNotEmpty()) {
            append(" {${attributes.entries.joinToString(", ") { "${it.key}:${it.value}" }}}")
        }
        if (notes.isNotEmpty()) {
            append(" 备注:${notes.last()}")
        }
    }
}

enum class EntityStatus(val label: String) {
    ACTIVE("在场"),
    LEFT("离场"),
    UNCERTAIN("不确定")
}
