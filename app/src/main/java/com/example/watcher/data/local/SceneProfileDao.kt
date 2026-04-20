package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.watcher.data.model.SceneProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneProfileDao {

    @Query("SELECT * FROM scene_profiles ORDER BY lastVerifiedAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SceneProfile>>

    @Query("SELECT * FROM scene_profiles ORDER BY lastVerifiedAt DESC, updatedAt DESC")
    suspend fun getAll(): List<SceneProfile>

    @Query("SELECT * FROM scene_profiles WHERE sceneId = :sceneId")
    suspend fun getById(sceneId: String): SceneProfile?

    @Query("UPDATE scene_profiles SET userLabel = :userLabel, updatedAt = :updatedAt WHERE sceneId = :sceneId")
    suspend fun updateUserLabel(sceneId: String, userLabel: String?, updatedAt: Long)

    @Query("DELETE FROM scene_profiles WHERE sceneId = :sceneId")
    suspend fun deleteById(sceneId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SceneProfile)
}
