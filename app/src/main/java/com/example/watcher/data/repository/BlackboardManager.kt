package com.example.watcher.data.repository

import android.util.Log
import com.example.watcher.data.local.BlackboardDao
import com.example.watcher.data.model.BlackboardDay
import com.example.watcher.data.model.BlackboardEntry
import com.example.watcher.data.model.BlackboardObservationCategories
import com.example.watcher.data.model.BlackboardObservationItem
import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.LiveCommentaryState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bridges the real-time commentary layer to the persistent blackboard.
 * Portrait updates are handled by [PortraitCuratorAgent].
 */
class BlackboardManager(
    private val blackboardDao: BlackboardDao
) {
    companion object {
        private const val TAG = "BlackboardMgr"
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Write a single completed commentary entry to the blackboard. */
    suspend fun onCommentaryCompleted(entry: CommentaryEntry) {
        val date = today()
        ensureDayExists(date)
        val entryId = blackboardDao.insertEntry(
            BlackboardEntry(
                dayDate = date,
                segmentIndex = entry.segmentIndex,
                timestamp = entry.wallClockStartTime,
                text = entry.text,
                status = entry.status.name
            )
        )
        val observationItems = parseObservationItems(
            entryId = entryId,
            dayDate = date,
            entry = entry
        )
        if (observationItems.isNotEmpty()) {
            blackboardDao.insertObservationItems(observationItems)
        }
        val count = blackboardDao.countEntriesByDate(date)
        blackboardDao.getDay(date)?.let {
            blackboardDao.updateDay(
                it.copy(
                    totalEntries = count,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        Log.d(
            TAG,
            "Entry #${entry.segmentIndex} persisted to blackboard ($date, total=$count, parsed=${observationItems.size})"
        )
    }

    /** Snapshot the current in-memory state to today's blackboard day record. */
    suspend fun syncDaySnapshot(state: LiveCommentaryState) {
        val date = today()
        ensureDayExists(date)
        val existing = blackboardDao.getDay(date) ?: return
        blackboardDao.updateDay(
            existing.copy(
                sceneMemory = state.sceneMemory,
                entityMemory = state.entityMemory,
                actionSummary = state.actionSummary,
                coreMemoryA = state.memoryA,
                latestMemoryB = state.latestMemoryB,
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Day snapshot synced for $date")
    }

    private suspend fun ensureDayExists(date: String) {
        if (blackboardDao.getDay(date) == null) {
            blackboardDao.insertDayIgnore(BlackboardDay(date = date))
        }
    }

    private fun parseObservationItems(
        entryId: Long,
        dayDate: String,
        entry: CommentaryEntry
    ): List<BlackboardObservationItem> {
        val taggedItems = entry.text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val tag = observationTagOf(line) ?: return@mapNotNull null
                val content = line.substringAfter("]").trim()
                if (content.isBlank()) return@mapNotNull null
                BlackboardObservationItem(
                    entryId = entryId,
                    dayDate = dayDate,
                    segmentIndex = entry.segmentIndex,
                    timestamp = entry.wallClockStartTime,
                    category = tag,
                    content = content,
                    dimensionHint = dimensionHintFor(tag, content)
                )
            }
            .toList()

        if (taggedItems.isNotEmpty()) {
            return taggedItems
        }

        val fallbackText = entry.text.trim()
        if (fallbackText.isBlank()) return emptyList()
        return listOf(
            BlackboardObservationItem(
                entryId = entryId,
                dayDate = dayDate,
                segmentIndex = entry.segmentIndex,
                timestamp = entry.wallClockStartTime,
                category = BlackboardObservationCategories.UNKNOWN,
                content = fallbackText,
                dimensionHint = ""
            )
        )
    }

    private fun observationTagOf(line: String): String? = when {
        line.startsWith("[SCENE]") -> BlackboardObservationCategories.SCENE
        line.startsWith("[USER]") -> BlackboardObservationCategories.USER
        line.startsWith("[INTERACTION]") -> BlackboardObservationCategories.INTERACTION
        line.startsWith("[TIME]") -> BlackboardObservationCategories.TIME
        else -> null
    }

    private fun dimensionHintFor(category: String, content: String): String = ""
}
