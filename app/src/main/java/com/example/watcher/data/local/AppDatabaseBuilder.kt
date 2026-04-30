package com.example.watcher.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration

internal fun buildWatcherDatabase(
    context: Context,
    migrations: Array<Migration>,
    databaseProvider: () -> AppDatabase?
): AppDatabase {
    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "watcher_database"
    )
        .addMigrations(*migrations)
        .addCallback(AppDatabaseSeeders.openCallback(databaseProvider))
        .build()
}
