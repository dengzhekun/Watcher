package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorReasoningLog
import com.example.watcher.data.model.ObservationGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorModelDao {

    @Query("SELECT * FROM behavior_claims ORDER BY updatedAt DESC")
    fun observeAllClaims(): Flow<List<BehaviorClaim>>

    @Query("SELECT * FROM behavior_claims WHERE sceneId = :sceneId ORDER BY updatedAt DESC")
    fun observeClaimsByScene(sceneId: String): Flow<List<BehaviorClaim>>

    @Query("SELECT * FROM behavior_claims WHERE sceneId IS NULL ORDER BY updatedAt DESC")
    fun observeUniversalClaims(): Flow<List<BehaviorClaim>>

    @Query("SELECT * FROM behavior_claims WHERE dimensionKey = :dimensionKey ORDER BY updatedAt DESC")
    suspend fun getClaimsByDimension(dimensionKey: String): List<BehaviorClaim>

    @Query("SELECT * FROM behavior_claims WHERE sceneId = :sceneId ORDER BY updatedAt DESC")
    suspend fun getClaimsByScene(sceneId: String): List<BehaviorClaim>

    @Query("SELECT * FROM behavior_claims WHERE sceneId IS NULL ORDER BY updatedAt DESC")
    suspend fun getUniversalClaims(): List<BehaviorClaim>

    @Query("SELECT * FROM behavior_claims WHERE claimId = :claimId")
    suspend fun getClaimById(claimId: String): BehaviorClaim?

    @Query("SELECT * FROM behavior_claims WHERE dimensionKey = :dimensionKey AND claimText = :claimText LIMIT 1")
    suspend fun getClaimByDimensionAndText(dimensionKey: String, claimText: String): BehaviorClaim?

    @Query(
        """
        SELECT * FROM behavior_claims
        WHERE sceneId = :sceneId AND dimensionKey = :dimensionKey AND claimText = :claimText
        LIMIT 1
        """
    )
    suspend fun getClaimBySceneDimensionAndText(
        sceneId: String,
        dimensionKey: String,
        claimText: String
    ): BehaviorClaim?

    @Query(
        """
        SELECT * FROM behavior_claims
        WHERE sceneId IS NULL AND dimensionKey = :dimensionKey AND claimText = :claimText
        LIMIT 1
        """
    )
    suspend fun getUniversalClaimByDimensionAndText(
        dimensionKey: String,
        claimText: String
    ): BehaviorClaim?

    @Query(
        """
        SELECT * FROM behavior_claims
        WHERE claimText = :claimText AND status = 'stable'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getStableClaimsByText(claimText: String): List<BehaviorClaim>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: BehaviorClaim)

    @Query("DELETE FROM behavior_claims WHERE sceneId = :sceneId")
    suspend fun deleteClaimsByScene(sceneId: String)

    @Query("DELETE FROM behavior_claims WHERE claimId = :claimId")
    suspend fun deleteClaimById(claimId: String)

    @Query("UPDATE behavior_claims SET sceneId = :targetSceneId WHERE sceneId = :sourceSceneId")
    suspend fun reassignClaimsScene(sourceSceneId: String, targetSceneId: String)

    @Query("DELETE FROM behavior_claims")
    suspend fun deleteAllClaims()

    @Query("SELECT * FROM observation_goals ORDER BY status ASC, priority DESC, updatedAt DESC")
    fun observeAllGoals(): Flow<List<ObservationGoal>>

    @Query("SELECT * FROM observation_goals WHERE status = :status ORDER BY priority DESC, updatedAt DESC")
    fun observeGoalsByStatus(status: String): Flow<List<ObservationGoal>>

    @Query(
        """
        SELECT * FROM observation_goals
        WHERE sceneId = :sceneId AND status = 'active'
        ORDER BY priority DESC, updatedAt DESC
        """
    )
    fun observeGoalsByScene(sceneId: String): Flow<List<ObservationGoal>>

    @Query("SELECT * FROM observation_goals WHERE status = :status AND dimensionKey = :dimensionKey ORDER BY priority DESC, updatedAt DESC")
    suspend fun getGoalsByStatusAndDimension(status: String, dimensionKey: String): List<ObservationGoal>

    @Query(
        """
        SELECT * FROM observation_goals
        WHERE sceneId = :sceneId AND status = 'active'
        ORDER BY priority DESC, updatedAt DESC
        """
    )
    suspend fun getGoalsByScene(sceneId: String): List<ObservationGoal>

    @Query("SELECT * FROM observation_goals WHERE goalId = :goalId")
    suspend fun getGoalById(goalId: String): ObservationGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: ObservationGoal)

    @Query("DELETE FROM observation_goals WHERE sceneId = :sceneId")
    suspend fun deleteGoalsByScene(sceneId: String)

    @Query("DELETE FROM observation_goals WHERE goalId = :goalId")
    suspend fun deleteGoalById(goalId: String)

    @Query("UPDATE observation_goals SET sceneId = :targetSceneId WHERE sceneId = :sourceSceneId")
    suspend fun reassignGoalsScene(sourceSceneId: String, targetSceneId: String)

    @Query("DELETE FROM observation_goals")
    suspend fun deleteAllGoals()

    @Insert
    suspend fun insertReasoningLog(log: BehaviorReasoningLog)

    @Query("SELECT * FROM behavior_reasoning_logs WHERE dayDate = :date ORDER BY createdAt DESC")
    fun observeReasoningByDate(date: String): Flow<List<BehaviorReasoningLog>>

    @Query("SELECT * FROM behavior_reasoning_logs WHERE sceneId = :sceneId ORDER BY createdAt DESC")
    fun observeReasoningByScene(sceneId: String): Flow<List<BehaviorReasoningLog>>

    @Query("SELECT * FROM behavior_reasoning_logs ORDER BY createdAt DESC")
    fun observeAllReasoningLogs(): Flow<List<BehaviorReasoningLog>>

    @Query("SELECT * FROM behavior_reasoning_logs WHERE dayDate = :date ORDER BY createdAt DESC")
    suspend fun getReasoningByDate(date: String): List<BehaviorReasoningLog>

    @Query("SELECT * FROM behavior_reasoning_logs WHERE sceneId = :sceneId ORDER BY createdAt DESC")
    suspend fun getReasoningByScene(sceneId: String): List<BehaviorReasoningLog>

    @Query("DELETE FROM behavior_reasoning_logs WHERE sceneId = :sceneId")
    suspend fun deleteReasoningByScene(sceneId: String)

    @Query("UPDATE behavior_reasoning_logs SET sceneId = :targetSceneId WHERE sceneId = :sourceSceneId")
    suspend fun reassignReasoningScene(sourceSceneId: String, targetSceneId: String)

    @Query("DELETE FROM behavior_reasoning_logs WHERE dayDate = :date")
    suspend fun deleteReasoningByDate(date: String)
}
