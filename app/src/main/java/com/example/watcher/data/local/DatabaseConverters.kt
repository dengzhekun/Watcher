package com.example.watcher.data.local

import androidx.room.TypeConverter
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.model.CheckResult
import com.example.watcher.data.model.MonitorMediaType
import com.example.watcher.data.model.MonitorLogAction
import com.example.watcher.data.model.MonitorMode
import com.example.watcher.data.model.MonitorRunStatus
import com.example.watcher.data.model.TargetTrigger
import com.example.watcher.data.model.VideoRunStatus

class DatabaseConverters {
    @TypeConverter
    fun fromAudienceEngineType(value: AudienceEngineType?): String? = value?.name

    @TypeConverter
    fun toAudienceEngineType(value: String?): AudienceEngineType? {
        return value?.let { runCatching { AudienceEngineType.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromVideoRunStatus(value: VideoRunStatus?): String? = value?.name

    @TypeConverter
    fun toVideoRunStatus(value: String?): VideoRunStatus? {
        return value?.let { runCatching { VideoRunStatus.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromCheckResult(value: CheckResult?): String? = value?.name

    @TypeConverter
    fun toCheckResult(value: String?): CheckResult? {
        return value?.let { runCatching { CheckResult.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromMonitorLogAction(value: MonitorLogAction?): String? = value?.name

    @TypeConverter
    fun toMonitorLogAction(value: String?): MonitorLogAction? {
        return value?.let { runCatching { MonitorLogAction.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromMonitorRunStatus(value: MonitorRunStatus?): String? = value?.name

    @TypeConverter
    fun toMonitorRunStatus(value: String?): MonitorRunStatus? {
        return value?.let { runCatching { MonitorRunStatus.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromMonitorMediaType(value: MonitorMediaType?): String? = value?.name

    @TypeConverter
    fun toMonitorMediaType(value: String?): MonitorMediaType? {
        return value?.let { runCatching { MonitorMediaType.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromMonitorMode(value: MonitorMode?): String? = value?.name

    @TypeConverter
    fun toMonitorMode(value: String?): MonitorMode? {
        return value?.let { runCatching { MonitorMode.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromTargetTrigger(value: TargetTrigger?): String? = value?.name

    @TypeConverter
    fun toTargetTrigger(value: String?): TargetTrigger? {
        return value?.let { runCatching { TargetTrigger.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun fromBaselineSource(value: BaselineSource?): String? = value?.name

    @TypeConverter
    fun toBaselineSource(value: String?): BaselineSource? {
        return value?.let { runCatching { BaselineSource.valueOf(it) }.getOrNull() }
    }
}
