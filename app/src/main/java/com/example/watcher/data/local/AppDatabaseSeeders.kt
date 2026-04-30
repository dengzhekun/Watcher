package com.example.watcher.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.watcher.data.model.CouncilExpertDefaults
import com.example.watcher.data.model.CouncilTemplates
import com.example.watcher.data.model.MonitorTaskTemplates
import com.example.watcher.data.model.PortraitDimension
import com.example.watcher.data.model.VideoTaskTemplates
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object AppDatabaseSeeders {
    private val seedScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
        CoroutineExceptionHandler { _, t ->
            Log.e("AppDatabaseSeeders", "Database seeding failed", t)
        }
    )

    fun openCallback(databaseProvider: () -> AppDatabase?): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                databaseProvider()?.let { database ->
                    seedScope.launch {
                        seedDefaultTemplates(database.templateDao())
                        seedDefaultCouncilExperts(database.councilExpertDao())
                        seedDefaultPortraitDimensions(database.portraitDao())
                    }
                }
            }
        }
    }

    private suspend fun seedDefaultTemplates(dao: TemplateDao) {
        for (entity in MonitorTaskTemplates.defaultEntities()) {
            if (dao.getMonitorTemplate(entity.templateId) == null) {
                dao.upsertMonitor(entity)
            }
        }
        for (entity in VideoTaskTemplates.defaultEntities()) {
            if (dao.getVideoTemplate(entity.templateId) == null) {
                dao.upsertVideo(entity)
            }
        }
        for (entity in CouncilTemplates.defaultEntities()) {
            if (dao.getCouncilTemplate(entity.templateId) == null) {
                dao.upsertCouncil(entity)
            }
        }
    }

    private suspend fun seedDefaultCouncilExperts(dao: CouncilExpertDao) {
        val existingIds = dao.getAll().mapTo(linkedSetOf()) { it.expertId }
        for (entity in CouncilExpertDefaults.defaultEntities()) {
            if (entity.expertId !in existingIds) {
                dao.upsert(entity)
            }
        }
    }

    private suspend fun seedDefaultPortraitDimensions(dao: PortraitDao) {
        if (dao.count() == 0) {
            PortraitDimension.defaults().forEach { dao.upsert(it) }
        }
    }
}
