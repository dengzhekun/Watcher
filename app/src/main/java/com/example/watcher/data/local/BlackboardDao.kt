package com.example.watcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.watcher.data.model.BlackboardDay
import com.example.watcher.data.model.BlackboardEntry
import com.example.watcher.data.model.BlackboardObservationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BlackboardDao {

    // --- Day operations ---

    @Query("SELECT * FROM blackboard_days ORDER BY date DESC")
    fun observeAllDays(): Flow<List<BlackboardDay>>

    @Query("SELECT * FROM blackboard_days WHERE date = :date")
    suspend fun getDay(date: String): BlackboardDay?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDayIgnore(day: BlackboardDay): Long

    @Update
    suspend fun updateDay(day: BlackboardDay)

    // --- Entry operations ---

    @Insert
    suspend fun insertEntry(entry: BlackboardEntry): Long

    @Query("SELECT * FROM blackboard_entries WHERE dayDate = :date ORDER BY timestamp ASC")
    suspend fun getEntriesByDate(date: String): List<BlackboardEntry>

    @Query("SELECT * FROM blackboard_entries WHERE dayDate = :date ORDER BY timestamp ASC")
    fun observeEntriesByDate(date: String): Flow<List<BlackboardEntry>>

    @Query("SELECT COUNT(*) FROM blackboard_entries WHERE dayDate = :date")
    suspend fun countEntriesByDate(date: String): Int

    // --- Observation item operations ---

    @Insert
    suspend fun insertObservationItems(items: List<BlackboardObservationItem>)

    @Query("SELECT * FROM blackboard_observation_items WHERE dayDate = :date ORDER BY timestamp ASC, id ASC")
    suspend fun getObservationItemsByDate(date: String): List<BlackboardObservationItem>

    @Query("SELECT * FROM blackboard_observation_items WHERE dayDate = :date ORDER BY timestamp ASC, id ASC")
    fun observeObservationItemsByDate(date: String): Flow<List<BlackboardObservationItem>>

}
