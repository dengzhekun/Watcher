package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.CouncilExpertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CouncilExpertDao {
    @Query(
        """
        SELECT * FROM council_experts
        ORDER BY selectedForCouncil DESC, sortOrder ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<CouncilExpertEntity>>

    @Query(
        """
        SELECT * FROM council_experts
        ORDER BY selectedForCouncil DESC, sortOrder ASC, name COLLATE NOCASE ASC
        """
    )
    suspend fun getAll(): List<CouncilExpertEntity>

    @Query(
        """
        SELECT * FROM council_experts
        WHERE enabled = 1 AND selectedForCouncil = 1
        ORDER BY sortOrder ASC, name COLLATE NOCASE ASC
        """
    )
    suspend fun getSelectedLineup(): List<CouncilExpertEntity>

    @Query("SELECT * FROM council_experts WHERE expertId = :expertId")
    suspend fun getByExpertId(expertId: String): CouncilExpertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expert: CouncilExpertEntity)

    @Query("DELETE FROM council_experts WHERE expertId = :expertId")
    suspend fun deleteByExpertId(expertId: String)

    @Query("SELECT COUNT(*) FROM council_experts")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM council_experts")
    suspend fun maxSortOrder(): Int
}
