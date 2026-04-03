package com.example.watcher.data.repository

import com.example.watcher.data.model.VideoStreamSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StreamDeviceCoordinator(
    private val monitorManager: MonitorManager
) {
    data class ApplyOutcome(
        val persistedSettings: VideoStreamSettings? = null,
        val notice: String? = null
    )

    private var ledController: LedController? = null
    private var cameraController: Esp32CameraController? = null
    private var currentLedBaseUrl: String? = null
    private var currentCameraBaseUrl: String? = null

    suspend fun applySettings(settings: VideoStreamSettings): ApplyOutcome {
        val normalizedSettings = settings.normalized()
        if (!normalizedSettings.supportsDeviceControl) {
            ledController?.release()
            ledController = null
            currentLedBaseUrl = null
            cameraController = null
            currentCameraBaseUrl = null
            monitorManager.updateSettings(normalizedSettings)
            monitorManager.setLedController(null)
            return ApplyOutcome()
        }

        val normalizedBaseUrl = normalizeBaseUrl(normalizedSettings.baseUrl)
        if (ledController == null || currentLedBaseUrl != normalizedBaseUrl) {
            ledController?.release()
            ledController = LedController(normalizedBaseUrl)
            currentLedBaseUrl = normalizedBaseUrl
        }
        if (cameraController == null || currentCameraBaseUrl != normalizedBaseUrl) {
            cameraController = Esp32CameraController(normalizedBaseUrl)
            currentCameraBaseUrl = normalizedBaseUrl
        }

        ledController?.updateConfig(
            LedController.AutoLightConfig(
                enabled = normalizedSettings.ledAutoLightEnabled,
                targetBrightness = normalizedSettings.ledTargetBrightness
            )
        )

        monitorManager.updateSettings(normalizedSettings)
        monitorManager.setLedController(
            if (normalizedSettings.ledControlEnabled) ledController else null
        )

        val controller = cameraController ?: return ApplyOutcome()
        val result = withContext(Dispatchers.IO) {
            controller.applyPreferredSettings(normalizedSettings)
        }

        return if (result.usedFallback && result.appliedSettings != normalizedSettings) {
            ApplyOutcome(
                persistedSettings = result.appliedSettings.copy(
                    preferredWifiSsid = normalizedSettings.preferredWifiSsid
                ),
                notice = RESOLUTION_FALLBACK_NOTICE
            )
        } else {
            ApplyOutcome()
        }
    }

    fun release() {
        ledController?.release()
        ledController = null
        cameraController = null
        currentLedBaseUrl = null
        currentCameraBaseUrl = null
    }
}

private const val RESOLUTION_FALLBACK_NOTICE =
    "Default stream resolution fell back to VGA because 1280x720 is not stable on this device."
