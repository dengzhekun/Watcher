package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.watcher.data.model.CouncilKnowledgeEntity

@Dao
interface CouncilKnowledgeDao {

    @Query(
        """
        SELECT * FROM council_knowledge
        WHERE (sceneType = :sceneType OR sceneType = 'all')
        ORDER BY relevance DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRelevant(sceneType: String, limit: Int = 20): List<CouncilKnowledgeEntity>

    @Query(
        """
        SELECT * FROM council_knowledge
        WHERE category = :category
        ORDER BY relevance DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getByCategory(category: String, limit: Int = 10): List<CouncilKnowledgeEntity>

    @Query(
        """
        SELECT * FROM council_knowledge
        WHERE source = :source
        ORDER BY relevance DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getBySource(source: String, limit: Int = 20): List<CouncilKnowledgeEntity>

    /** Expert-specific calibration knowledge */
    @Query(
        """
        SELECT * FROM council_knowledge
        WHERE expertId = :expertId AND category = 'expert_calibration'
        ORDER BY relevance DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getExpertCalibration(expertId: String, limit: Int = 10): List<CouncilKnowledgeEntity>

    /** Shared user profile knowledge (readable by all experts) */
    @Query(
        """
        SELECT * FROM council_knowledge
        WHERE category = 'user_profile'
        ORDER BY relevance DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getUserProfile(limit: Int = 10): List<CouncilKnowledgeEntity>

    @Insert
    suspend fun insert(entry: CouncilKnowledgeEntity): Long

    @Insert
    suspend fun insertAll(entries: List<CouncilKnowledgeEntity>)

    @Query("UPDATE council_knowledge SET relevance = relevance * :factor, updatedAt = :now WHERE id = :id")
    suspend fun adjustRelevance(id: Long, factor: Float, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM council_knowledge WHERE relevance < 0.1 AND updatedAt < :before")
    suspend fun pruneStale(before: Long)

    @Query("DELETE FROM council_knowledge WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM council_knowledge WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM council_knowledge")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM council_knowledge")
    suspend fun count(): Int
}
