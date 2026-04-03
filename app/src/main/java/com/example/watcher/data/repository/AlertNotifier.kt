package com.example.watcher.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.watcher.MainActivity
import com.example.watcher.R
import com.example.watcher.data.model.CheckResult
import com.example.watcher.data.model.MonitorDecision

interface AlertNotifier {
    fun notify(taskTitle: String, decision: MonitorDecision)

    fun stopAlerting() = Unit
}

object NoOpAlertNotifier : AlertNotifier {
    override fun notify(taskTitle: String, decision: MonitorDecision) = Unit
}

class AndroidAlertNotifier(
    context: Context
) : AlertNotifier {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var alertLoopRunnable: Runnable? = null

    init {
        createNotificationChannels()
    }

    override fun notify(taskTitle: String, decision: MonitorDecision) {
        val result = decision.result
        if (result != CheckResult.ALERT && result != CheckResult.WARNING) {
            return
        }
        if (!canPostNotifications()) {
            return
        }

        val title = if (taskTitle.isBlank()) {
            appContext.getString(R.string.app_name)
        } else {
            taskTitle
        }
        val channelId = if (result == CheckResult.ALERT) {
            ALERT_CHANNEL_ID
        } else {
            WARNING_CHANNEL_ID
        }
        val notificationTitle = if (result == CheckResult.ALERT) {
            appContext.getString(R.string.notification_alert_title, title)
        } else {
            appContext.getString(R.string.notification_warning_title, title)
        }
        val notificationText = buildNotificationText(decision)

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(
                if (result == CheckResult.ALERT) {
                    android.R.drawable.ic_dialog_alert
                } else {
                    android.R.drawable.ic_dialog_info
                }
            )
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(
                if (result == CheckResult.ALERT) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_STATUS
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .setVibrate(
                if (result == CheckResult.ALERT) {
                    ALERT_VIBRATION_PATTERN
                } else {
                    WARNING_VIBRATION_PATTERN
                }
            )
            .build()

        NotificationManagerCompat.from(appContext)
            .notify(nextNotificationId(), notification)
        triggerDeviceFeedback(result)
    }

    override fun stopAlerting() {
        cancelAlertLoop()
        mediaPlayer?.runCatching {
            stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun canPostNotifications(): Boolean {
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return permissionGranted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun createNotificationChannels() {
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            appContext.getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.notification_alert_channel_description)
            enableVibration(true)
            setVibrationPattern(ALERT_VIBRATION_PATTERN)
            setSound(null, null)
        }

        val warningChannel = NotificationChannel(
            WARNING_CHANNEL_ID,
            appContext.getString(R.string.notification_warning_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.notification_warning_channel_description)
            enableVibration(true)
            setVibrationPattern(WARNING_VIBRATION_PATTERN)
            setSound(null, null)
        }

        notificationManager.createNotificationChannels(listOf(alertChannel, warningChannel))
    }

    private fun triggerDeviceFeedback(result: CheckResult) {
        vibrate(
            if (result == CheckResult.ALERT) {
                ALERT_VIBRATION_PATTERN
            } else {
                WARNING_VIBRATION_PATTERN
            }
        )
        if (result == CheckResult.ALERT) {
            playAlertLoop()
        }
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) {
            return
        }

        val effect = VibrationEffect.createWaveform(pattern, -1)
        vibrator.vibrate(effect)
    }

    private fun playAlertLoop() {
        val player = mediaPlayer ?: MediaPlayer.create(appContext, R.raw.alert_alarm)
            ?.also { mediaPlayer = it }
            ?: return

        cancelAlertLoop()
        if (player.isPlaying) {
            return
        }

        runCatching {
            player.seekTo(0)
            player.start()
        }.onFailure {
            player.release()
            mediaPlayer = null
            return
        }

        if (player.duration > ALERT_CLIP_DURATION_MS) {
            val loopRunnable = object : Runnable {
                override fun run() {
                    val activePlayer = mediaPlayer ?: return
                    if (!activePlayer.isPlaying) {
                        return
                    }
                    runCatching {
                        activePlayer.pause()
                        activePlayer.seekTo(0)
                        activePlayer.start()
                    }.onSuccess {
                        mainHandler.postDelayed(this, ALERT_CLIP_DURATION_MS.toLong())
                    }
                }
            }
            alertLoopRunnable = loopRunnable
            mainHandler.postDelayed(loopRunnable, ALERT_CLIP_DURATION_MS.toLong())
        } else {
            player.isLooping = true
        }
    }

    private fun cancelAlertLoop() {
        alertLoopRunnable?.let(mainHandler::removeCallbacks)
        alertLoopRunnable = null
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotificationText(decision: MonitorDecision): String {
        val summary = decision.summary.ifBlank { decision.result.name }
        return if (decision.reason.isBlank()) {
            summary
        } else {
            "$summary\n${decision.reason}"
        }
    }

    private fun nextNotificationId(): Int {
        nextId += 1
        if (nextId == Int.MAX_VALUE) {
            nextId = 1
        }
        return nextId
    }

    companion object {
        private const val ALERT_CHANNEL_ID = "watcher_alerts_v3"
        private const val WARNING_CHANNEL_ID = "watcher_warnings_v3"
        private const val ALERT_CLIP_DURATION_MS = 10_000
        private var nextId = 2000
        private val ALERT_VIBRATION_PATTERN = longArrayOf(0, 500, 250, 700)
        private val WARNING_VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400)
    }
}
