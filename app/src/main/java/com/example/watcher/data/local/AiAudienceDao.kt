package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.LlmProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LlmProviderDao {
    @Query("SELECT * FROM llm_providers ORDER BY name ASC")
    fun observeAll(): Flow<List<LlmProviderEntity>>

    @Query("SELECT * FROM llm_providers ORDER BY name ASC")
    suspend fun getAll(): List<LlmProviderEntity>

    @Query("SELECT * FROM llm_providers WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<LlmProviderEntity>

    @Query("SELECT * FROM llm_providers WHERE id = :id")
    suspend fun getById(id: String): LlmProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: LlmProviderEntity)

    @Query("DELETE FROM llm_providers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AiAudienceDao {
    @Query("SELECT * FROM ai_audiences ORDER BY name ASC")
    fun observeAll(): Flow<List<AiAudienceEntity>>

    @Query("SELECT * FROM ai_audiences WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<AiAudienceEntity>

    @Query("SELECT * FROM ai_audiences WHERE id = :id")
    suspend fun getById(id: Long): AiAudienceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audience: AiAudienceEntity): Long

    @Update
    suspend fun update(audience: AiAudienceEntity)

    @Query("DELETE FROM ai_audiences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE ai_audiences SET personalMemory = :memory, updatedAt = :now WHERE id = :id")
    suspend fun updateMemory(id: Long, memory: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE ai_audiences SET agentStateJson = :stateJson, updatedAt = :now WHERE id = :id")
    suspend fun updateAgentState(id: Long, stateJson: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE ai_audiences SET agentStateJson = '' WHERE audienceType = :audienceType")
    suspend fun clearAgentStatesByType(audienceType: String)
}

@Dao
interface AiAudienceMessageDao {
    @Query("SELECT * FROM ai_audience_messages ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AiAudienceMessageEntity>>

    @Query("SELECT * FROM ai_audience_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<AiAudienceMessageEntity>

    @Query("""
        SELECT * FROM ai_audience_messages
        WHERE mentionedAudienceId = :audienceId AND timestamp > :since
        ORDER BY timestamp ASC
    """)
    suspend fun getPendingMentions(audienceId: Long, since: Long): List<AiAudienceMessageEntity>

    @Insert
    suspend fun insert(message: AiAudienceMessageEntity): Long

    @Query("DELETE FROM ai_audience_messages")
    suspend fun deleteAll()

    @Query("DELETE FROM ai_audience_messages WHERE audienceId = :audienceId")
    suspend fun deleteByAudienceId(audienceId: Long)
}
