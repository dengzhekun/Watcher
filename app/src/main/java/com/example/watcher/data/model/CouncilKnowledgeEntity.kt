package com.example.watcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent knowledge accumulated across council sessions.
 * Enables "越用越强" — agents get smarter with each analysis.
 */
@Entity(tableName = "council_knowledge")
data class CouncilKnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Category: session_fact (会话事实) | expert_calibration (专家校准) | user_profile (用户画像) */
    val category: String,
    /** Expert this knowledge belongs to (expertId for calibration, empty for shared user_profile) */
    val expertId: String = "",
    /** Scene type this knowledge applies to, or "all" for universal */
    val sceneType: String = "all",
    /** The knowledge content in natural language */
    val content: String,
    /** Where this knowledge came from (e.g., expert name, session summary) */
    val source: String = "",
    /** Relevance score — higher means more useful, decays or grows with feedback */
    val relevance: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
