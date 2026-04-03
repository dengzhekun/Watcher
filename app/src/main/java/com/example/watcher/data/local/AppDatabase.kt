package com.example.watcher.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.watcher.data.model.MonitorTask
import com.example.watcher.data.model.MonitorEventEntity
import com.example.watcher.data.model.MonitorMediaEntity
import com.example.watcher.data.model.MonitorRun
import com.example.watcher.data.model.MonitorTaskTemplates
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.TimelineEventEntity
import com.example.watcher.data.model.VideoProcessRun
import com.example.watcher.data.model.VideoProcessTask
import com.example.watcher.data.model.VideoSegmentRun
import com.example.watcher.data.model.VideoStreamSettings
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.model.VideoTaskTemplates
import com.example.watcher.data.model.VideoTemplateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MonitorTask::class,
        MonitorRun::class,
        MonitorEventEntity::class,
        MonitorMediaEntity::class,
        VideoStreamSettings::class,
        VideoProcessTask::class,
        VideoProcessRun::class,
        VideoSegmentRun::class,
        TimelineEventEntity::class,
        MonitorTemplateEntity::class,
        VideoTemplateEntity::class,
        LlmProviderEntity::class,
        AiAudienceEntity::class,
        AiAudienceMessageEntity::class
    ],
    version = 25,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitorTaskDao(): MonitorTaskDao
    abstract fun monitorRunDao(): MonitorRunDao
    abstract fun monitorEventDao(): MonitorEventDao
    abstract fun monitorMediaDao(): MonitorMediaDao
    abstract fun videoStreamSettingsDao(): VideoStreamSettingsDao
    abstract fun videoProcessTaskDao(): VideoProcessTaskDao
    abstract fun videoProcessRunDao(): VideoProcessRunDao
    abstract fun videoSegmentRunDao(): VideoSegmentRunDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun templateDao(): TemplateDao
    abstract fun llmProviderDao(): LlmProviderDao
    abstract fun aiAudienceDao(): AiAudienceDao
    abstract fun aiAudienceMessageDao(): AiAudienceMessageDao

    companion object {
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `video_process_tasks`
                    ADD COLUMN `finalSummaryPrompt` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `video_process_runs`
                    ADD COLUMN `mergedVideoPath` TEXT
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `monitor_tasks`
                    ADD COLUMN `baselineImagePath` TEXT
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `monitor_tasks`
                    ADD COLUMN `monitorMode` TEXT NOT NULL DEFAULT 'SceneBaseline'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `monitor_tasks`
                    ADD COLUMN `targetTrigger` TEXT NOT NULL DEFAULT 'OnAppear'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `monitor_tasks`
                    ADD COLUMN `baselineSource` TEXT NOT NULL DEFAULT 'CapturedFrame'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `monitor_runs`
                    ADD COLUMN `monitorMode` TEXT NOT NULL DEFAULT 'SceneBaseline'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `monitor_runs`
                    ADD COLUMN `targetTrigger` TEXT NOT NULL DEFAULT 'OnAppear'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `monitor_runs`
                    ADD COLUMN `baselineSource` TEXT NOT NULL DEFAULT 'CapturedFrame'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `video_stream_settings`
                    ADD COLUMN `deviceProfile` TEXT NOT NULL DEFAULT 'Esp32Camera'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `video_stream_settings`
                    ADD COLUMN `deviceToken` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE `video_stream_settings`
                    ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `video_stream_settings`
                    ADD COLUMN `preferredWifiSsid` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `monitor_templates` (
                        `templateId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `userRequirement` TEXT NOT NULL,
                        `originalSceneDescription` TEXT NOT NULL,
                        `checkIntervalSeconds` INTEGER NOT NULL,
                        `promptTemplate` TEXT NOT NULL,
                        `monitorMode` TEXT NOT NULL,
                        `targetTrigger` TEXT NOT NULL,
                        `baselineSource` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`templateId`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_templates` (
                        `templateId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `taskCategory` TEXT NOT NULL,
                        `strategyReason` TEXT NOT NULL,
                        `userRequirement` TEXT NOT NULL,
                        `sceneContext` TEXT NOT NULL,
                        `segmentAnalysisPrompt` TEXT NOT NULL,
                        `finalSummaryPrompt` TEXT NOT NULL,
                        `recordingDurationSeconds` INTEGER NOT NULL,
                        `segmentDurationSeconds` INTEGER NOT NULL,
                        `captureIntervalSeconds` INTEGER NOT NULL,
                        `samplingFps` INTEGER NOT NULL,
                        `autoStartStreamingOutput` INTEGER NOT NULL,
                        `finalSummaryEnabled` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`templateId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `llm_providers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `endpoint` TEXT NOT NULL,
                        `apiKey` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_audiences` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `persona` TEXT NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `heartbeatIntervalSeconds` INTEGER NOT NULL DEFAULT 15,
                        `includeFrame` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_audience_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audienceId` INTEGER NOT NULL,
                        `audienceName` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `mentionedAudienceId` INTEGER,
                        `mentionedAudienceName` TEXT,
                        `triggerType` TEXT NOT NULL DEFAULT 'heartbeat',
                        `timestamp` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`audienceId`) REFERENCES `ai_audiences`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_audience_messages_audienceId` ON `ai_audience_messages` (`audienceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_audience_messages_timestamp` ON `ai_audience_messages` (`timestamp`)")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `ai_audiences` ADD COLUMN `personalMemory` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `ai_audiences` ADD COLUMN `audienceType` TEXT NOT NULL DEFAULT 'Agent'"
                )
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `ai_audiences` ADD COLUMN `socialArchetype` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `ai_audiences` ADD COLUMN `speakingStyle` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `ai_audiences` ADD COLUMN `spendingStyle` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `ai_audiences` ADD COLUMN `socialDrive` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `ai_audiences` ADD COLUMN `agentStateJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_stream_settings_new` (
                        `id` INTEGER NOT NULL,
                        `ipAddress` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `resolution` TEXT NOT NULL,
                        `quality` INTEGER NOT NULL,
                        `brightness` INTEGER NOT NULL,
                        `contrast` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `ledControlEnabled` INTEGER NOT NULL,
                        `ledAutoLightEnabled` INTEGER NOT NULL,
                        `ledTargetBrightness` INTEGER NOT NULL,
                        `changeDetectionEnabled` INTEGER NOT NULL,
                        `changeThresholdPercent` INTEGER NOT NULL,
                        `notificationCooldownSeconds` INTEGER NOT NULL,
                        `videoAnalysisStreamingEnabled` INTEGER NOT NULL,
                        `deviceProfile` TEXT NOT NULL,
                        `preferredWifiSsid` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `video_stream_settings_new` (
                        `id`,
                        `ipAddress`,
                        `port`,
                        `resolution`,
                        `quality`,
                        `brightness`,
                        `contrast`,
                        `enabled`,
                        `ledControlEnabled`,
                        `ledAutoLightEnabled`,
                        `ledTargetBrightness`,
                        `changeDetectionEnabled`,
                        `changeThresholdPercent`,
                        `notificationCooldownSeconds`,
                        `videoAnalysisStreamingEnabled`,
                        `deviceProfile`,
                        `preferredWifiSsid`
                    )
                    SELECT
                        `id`,
                        `ipAddress`,
                        `port`,
                        `resolution`,
                        `quality`,
                        `brightness`,
                        `contrast`,
                        `enabled`,
                        `ledControlEnabled`,
                        `ledAutoLightEnabled`,
                        `ledTargetBrightness`,
                        `changeDetectionEnabled`,
                        `changeThresholdPercent`,
                        `notificationCooldownSeconds`,
                        `videoAnalysisStreamingEnabled`,
                        `deviceProfile`,
                        `preferredWifiSsid`
                    FROM `video_stream_settings`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `video_stream_settings`")
                database.execSQL("ALTER TABLE `video_stream_settings_new` RENAME TO `video_stream_settings`")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "watcher_database"
                ).addMigrations(
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25
                )
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            instance?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedDefaultTemplates(database.templateDao())
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
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
        }
    }
}
