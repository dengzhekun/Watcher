package com.example.watcher.data.model

data class DeviceRuntimeInfo(
    val deviceId: String = "",
    val mode: String = "",
    val apSsid: String = "",
    val apIp: String = "",
    val wifiConfigured: Boolean = false,
    val staSsid: String = "",
    val ip: String = "",
    val httpPort: Int = VideoStreamSettings.DEFAULT_PORT,
    val streamPort: Int = VideoStreamSettings.DEFAULT_STREAM_PORT,
    val discoveryPort: Int = 32108,
    val mdnsUrl: String = "",
    val mdnsActive: Boolean = false,
    val streamUrl: String = ""
) {
    val isApMode: Boolean
        get() = mode.equals("ap", ignoreCase = true)
}

data class ProvisionWifiNetwork(
    val ssid: String,
    val rssi: Int,
    val secure: Boolean
)

data class DeviceProvisionUiState(
    val isLoadingInfo: Boolean = false,
    val isScanningWifi: Boolean = false,
    val isSubmittingWifi: Boolean = false,
    val isClearingWifi: Boolean = false,
    val isWaitingForReconnect: Boolean = false,
    val isFindingProvisionedDevice: Boolean = false,
    val deviceInfo: DeviceRuntimeInfo? = null,
    val wifiNetworks: List<ProvisionWifiNetwork> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
