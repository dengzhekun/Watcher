package com.example.watcher.data.repository

import com.example.watcher.data.model.DeviceRuntimeInfo
import com.example.watcher.data.model.ProvisionWifiNetwork
import com.example.watcher.data.remote.DeviceInfoResponse
import com.example.watcher.data.remote.DeviceProvisionService
import com.example.watcher.data.remote.WifiScanResponse
import com.example.watcher.data.remote.parseDeviceInfoResponse
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeviceProvisionCoordinator(
    baseUrl: String
) {
    private val targets = buildList {
        add(ServiceTarget(baseUrl = normalizeBaseUrl(baseUrl), label = "current"))
        val hotspotBaseUrl = normalizeBaseUrl(DEFAULT_DEVICE_HOTSPOT_BASE_URL)
        if (hotspotBaseUrl != normalizeBaseUrl(baseUrl)) {
            add(ServiceTarget(baseUrl = hotspotBaseUrl, label = "hotspot"))
        }
    }

    suspend fun fetchDeviceInfo(): DeviceRuntimeInfo {
        val response = executeWithFallback { service ->
            service.getDeviceInfo()
        }
        if (!response.isSuccessful) {
            throw IllegalStateException(
                "Unable to read device info. Make sure the phone is on the device hotspot or current address."
            )
        }
        val rawBody = response.body()?.string().orEmpty()
        if (rawBody.isBlank()) {
            throw IllegalStateException("Device info response was empty.")
        }
        return runCatching { parseDeviceInfoResponse(rawBody).toRuntimeInfo() }
            .getOrElse {
                throw IllegalStateException("The device returned invalid status data. Try again after reconnecting to the device.")
            }
    }

    suspend fun scanWifiNetworks(): List<ProvisionWifiNetwork> {
        val response = executeWithFallback { service ->
            service.scanWifi()
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to scan nearby Wi-Fi networks. Try again later.")
        }
        return response.body().toWifiNetworks()
    }

    suspend fun saveWifiConfig(ssid: String, password: String): String {
        val normalizedSsid = ssid.trim()
        val rawPassword = password
        require(normalizedSsid.isNotBlank()) { "Wi-Fi name must not be empty." }

        val response = executeWithFallback { service ->
            service.saveWifiConfig(normalizedSsid, rawPassword)
        }
        val body = response.body()
        if (!response.isSuccessful || body?.success != true) {
            throw IllegalStateException(
                mapWifiConfigError(
                    error = body?.error ?: parseErrorCode(response.errorBody()?.string()),
                    password = rawPassword
                )
            )
        }
        return "Saved Wi-Fi \"$normalizedSsid\" to the device. It is rebooting now, and the app will keep looking for its new LAN address."
    }

    suspend fun clearWifiConfig(): String {
        val response = executeWithFallback { service ->
            service.clearWifiConfig()
        }
        val body = response.body()
        if (!response.isSuccessful || body?.success != true) {
            throw IllegalStateException(mapWifiClearError(body?.error ?: parseErrorCode(response.errorBody()?.string())))
        }
        return "Cleared the saved Wi-Fi from the device. It is rebooting back into hotspot mode."
    }

    private suspend fun <T> executeWithFallback(
        block: suspend (DeviceProvisionService) -> T
    ): T {
        var lastError: Exception? = null
        targets.forEach { target ->
            try {
                return block(target.service)
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No provisioning target is available.")
    }
}

private data class ServiceTarget(
    val baseUrl: String,
    val label: String
) {
    val service: DeviceProvisionService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeviceProvisionService::class.java)
}

private fun DeviceInfoResponse?.toRuntimeInfo(): DeviceRuntimeInfo {
    val body = this ?: return DeviceRuntimeInfo()
    return DeviceRuntimeInfo(
        deviceId = body.device_id,
        mode = body.mode,
        apSsid = body.ap_ssid,
        apIp = body.ap_ip,
        wifiConfigured = body.wifi_configured,
        staSsid = body.sta_ssid,
        ip = body.ip,
        httpPort = body.http_port,
        streamPort = body.stream_port,
        discoveryPort = body.discovery_port,
        mdnsUrl = body.mdns,
        mdnsActive = body.mdns_active,
        streamUrl = body.stream_url
    )
}

private fun WifiScanResponse?.toWifiNetworks(): List<ProvisionWifiNetwork> {
    val body = this ?: return emptyList()
    return body.networks
        .map { ProvisionWifiNetwork(ssid = it.ssid, rssi = it.rssi, secure = it.secure) }
        .filter { it.ssid.isNotBlank() }
        .sortedByDescending { it.rssi }
}

private fun parseErrorCode(rawBody: String?): String? {
    val payload = rawBody?.trim().orEmpty()
    if (payload.isBlank()) return null
    return runCatching {
        JSONObject(payload).optString("error").takeIf(String::isNotBlank)
    }.getOrNull()
}

private fun mapWifiConfigError(error: String?, password: String): String {
    return when (error) {
        "ssid_required" -> "Wi-Fi name must not be empty."
        "ssid_too_long" -> "Wi-Fi name is too long. Keep it within 32 characters."
        "invalid_password_length" -> {
            val passwordLength = password.length
            "Password length is invalid. Current length is $passwordLength, but non-empty passwords must be 8 to 64 characters."
        }
        "wifi_save_ssid_failed" -> "The device failed to save the Wi-Fi name. Try again later."
        "wifi_save_password_failed" -> "The device failed to save the Wi-Fi password. Try again later."
        "wifi_save_failed" -> "The device failed to save Wi-Fi settings. Try again later."
        else -> "The device failed to save Wi-Fi settings. Try again later."
    }
}

private fun mapWifiClearError(error: String?): String {
    return when (error) {
        "wifi_clear_failed" -> "Failed to clear Wi-Fi settings from the device. Try again later."
        else -> "Failed to clear Wi-Fi settings from the device. Try again later."
    }
}

private const val DEFAULT_DEVICE_HOTSPOT_BASE_URL = "http://192.168.4.1/"
