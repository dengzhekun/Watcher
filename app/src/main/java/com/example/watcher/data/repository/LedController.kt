package com.example.watcher.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.watcher.data.remote.LedControlService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LedController(baseUrl: String = "http://192.168.4.1/") {
    data class AutoLightConfig(
        val enabled: Boolean = true,
        val targetBrightness: Int = 80,
        val tolerance: Int = 15,
        val minLedBrightness: Int = 0,
        val maxLedBrightness: Int = 200,
        val adjustmentStep: Int = 5
    )

    private val tag = "LedController"
    private val ledService = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LedControlService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var blinkJob: Job? = null
    private var currentBrightness = 0
    private var currentBlinkMode = BlinkMode.OFF
    private var autoLightRunning = false
    private var lastFrameBrightness = -1
    private var lastLedBrightness = -1

    var autoLightConfig = AutoLightConfig()
        private set

    enum class BlinkMode {
        OFF,
        SLOW,
        FAST
    }

    suspend fun setModeByMonitorStatus(level: String) {
        when (level.lowercase()) {
            "alert" -> startBlink(BlinkMode.FAST, 255)
            "warning" -> startBlink(BlinkMode.SLOW, 200)
            "normal" -> {
                stopBlink()
                if (!autoLightConfig.enabled) {
                    setBrightness(50)
                }
            }
            else -> turnOff()
        }
    }

    suspend fun startAutoLight(frameProvider: () -> Bitmap?) {
        if (autoLightRunning || !autoLightConfig.enabled) return

        autoLightRunning = true
        stopBlink()

        withContext(Dispatchers.Default) {
            while (isActive && autoLightRunning) {
                frameProvider()?.let { frame ->
                    adjustLedBrightness(calculateFrameBrightness(frame))
                }
                delay(1_000)
            }
        }
    }

    suspend fun stopAutoLight() {
        autoLightRunning = false
    }

    suspend fun setBrightness(value: Int) {
        val brightness = value.coerceIn(0, 255)
        try {
            ledService.setBrightness(value = brightness)
            currentBrightness = brightness
        } catch (error: Exception) {
            Log.e(tag, "Failed to set LED brightness", error)
        }
    }

    fun updateConfig(config: AutoLightConfig) {
        autoLightConfig = config
    }

    suspend fun turnOff() {
        autoLightRunning = false
        stopBlink()
        try {
            ledService.setBrightness(value = 0)
            currentBrightness = 0
        } catch (error: Exception) {
            Log.e(tag, "Failed to turn off LED", error)
        }
    }

    fun release() {
        scope.launch {
            turnOff()
            scope.cancel()
        }
    }

    private suspend fun startBlink(mode: BlinkMode, brightness: Int) {
        stopBlink()
        stopAutoLight()
        currentBlinkMode = mode

        val duration = when (mode) {
            BlinkMode.FAST -> 150L
            BlinkMode.SLOW -> 600L
            BlinkMode.OFF -> return
        }

        blinkJob = scope.launch {
            while (isActive && currentBlinkMode == mode) {
                try {
                    ledService.setBrightness(value = brightness)
                    currentBrightness = brightness
                    delay(duration)
                    ledService.setBrightness(value = 0)
                    currentBrightness = 0
                    delay(duration)
                } catch (error: Exception) {
                    Log.e(tag, "Blink command failed", error)
                    delay(500)
                }
            }
        }
    }

    private suspend fun stopBlink() {
        currentBlinkMode = BlinkMode.OFF
        blinkJob?.cancel()
        blinkJob = null
    }

    private fun calculateFrameBrightness(bitmap: Bitmap): Int {
        val sampleStep = 12
        var totalBrightness = 0L
        var sampleCount = 0

        for (x in 0 until bitmap.width step sampleStep) {
            for (y in 0 until bitmap.height step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (0.299 * Color.red(pixel) +
                    0.587 * Color.green(pixel) +
                    0.114 * Color.blue(pixel)).toInt()
                totalBrightness += brightness
                sampleCount += 1
            }
        }

        return if (sampleCount == 0) 128 else (totalBrightness / sampleCount).toInt()
    }

    private suspend fun adjustLedBrightness(frameBrightness: Int) {
        if (currentBlinkMode != BlinkMode.OFF) return

        val config = autoLightConfig
        val diff = config.targetBrightness - frameBrightness

        if (lastLedBrightness > 0 && lastFrameBrightness > 0) {
            val ledDelta = currentBrightness - lastLedBrightness
            val frameDelta = frameBrightness - lastFrameBrightness
            if (ledDelta > 0 && frameDelta <= 2) return
        }

        if (kotlin.math.abs(diff) <= config.tolerance) {
            lastFrameBrightness = -1
            lastLedBrightness = -1
            return
        }

        val newBrightness = when {
            diff > 0 -> (currentBrightness + config.adjustmentStep)
                .coerceIn(config.minLedBrightness, config.maxLedBrightness)
            else -> (currentBrightness - config.adjustmentStep)
                .coerceIn(config.minLedBrightness, config.maxLedBrightness)
        }

        if (newBrightness == currentBrightness) return

        lastLedBrightness = currentBrightness
        lastFrameBrightness = frameBrightness
        setBrightness(newBrightness)
    }
}
