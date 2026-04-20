package com.example.watcher.data.agent.memory

import com.example.watcher.data.local.CouncilKnowledgeDao
import com.example.watcher.data.model.CouncilKnowledgeEntity

/**
 * Per-agent persistent knowledge store. Backed by Room DB with expertId partitioning.
 * Future: migrate to per-agent file storage for export/import.
 */
class AgentKnowledgeStore(private val dao: CouncilKnowledgeDao) {

    suspend fun query(agentId: String, limit: Int = 10): List<KnowledgeEntry> {
        val calibration = dao.getExpertCalibration(agentId, limit)
        val userProfile = dao.getUserProfile(limit = 5)
        return (calibration + userProfile)
            .distinctBy { it.id }
            .sortedByDescending { it.relevance }
            .take(limit)
            .map { it.toEntry() }
    }

    suspend fun write(agentId: String, category: String, content: String) {
        val now = System.currentTimeMillis()
        dao.insert(
            CouncilKnowledgeEntity(
                category = category,
                expertId = agentId,
                sceneType = "all",
                content = content.take(300),
                source = agentId,
                relevance = 0.8f,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun CouncilKnowledgeEntity.toEntry() = KnowledgeEntry(
        id = id,
        category = category,
        content = content,
        relevance = relevance
    )
}

data class KnowledgeEntry(
    val id: Long,
    val category: String,
    val content: String,
    val relevance: Float
)
