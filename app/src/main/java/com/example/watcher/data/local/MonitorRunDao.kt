package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.watcher.data.model.MonitorRun
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorRunDao {
    @Query("SELECT * FROM monitor_runs ORDER BY updatedAt DESC")
    fun observeAllRuns(): Flow<List<MonitorRun>>

    @Query("SELECT * FROM monitor_runs ORDER BY updatedAt DESC LIMIT 20")
    fun observeRecentRuns(): Flow<List<MonitorRun>>

    @Query("SELECT * FROM monitor_runs WHERE id = :id")
    fun observeRunById(id: Long): Flow<MonitorRun?>

    @Query("SELECT * FROM monitor_runs WHERE id = :id")
    suspend fun getRunById(id: Long): MonitorRun?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(run: MonitorRun): Long

    @Update
    suspend fun update(run: MonitorRun)

    @Query("DELETE FROM monitor_runs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    suspend fun upsert(run: MonitorRun): Long {
        return if (run.id == 0L) {
            insert(run)
        } else {
            update(run)
            run.id
        }
    }
}
