package com.example.watcher.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface DeviceProvisionService {
    @GET("/device/info")
    suspend fun getDeviceInfo(): Response<ResponseBody>

    @GET("/wifi/scan")
    suspend fun scanWifi(): Response<WifiScanResponse>

    @FormUrlEncoded
    @POST("/wifi/config")
    suspend fun saveWifiConfig(
        @Field("ssid") ssid: String,
        @Field("password") password: String
    ): Response<WifiConfigResponse>

    @POST("/wifi/clear")
    suspend fun clearWifiConfig(): Response<WifiClearResponse>
}

data class DeviceInfoResponse(
    val device_id: String = "",
    val mode: String = "",
    val ap_ssid: String = "",
    val ap_ip: String = "",
    val wifi_configured: Boolean = false,
    val sta_ssid: String = "",
    val ip: String = "",
    val http_port: Int = 80,
    val stream_port: Int = 81,
    val discovery_port: Int = 32108,
    val mdns: String = "",
    val mdns_active: Boolean = false,
    val stream_url: String = "",
    val wifi_connect_result: String = "",
    val wifi_connect_status: Int = 0,
    val wifi_connect_esp_err: Int = 0,
    val wifi_disconnect_reason: Int = 0,
    val wifi_fallback_to_ap: Boolean = false,
    val wifi_ssid_bytes: Int = 0
)

data class WifiScanResponse(
    val count: Int = 0,
    val networks: List<WifiScanNetworkResponse> = emptyList()
)

data class WifiScanNetworkResponse(
    val ssid: String = "",
    val rssi: Int = 0,
    val secure: Boolean = true
)

data class WifiConfigResponse(
    val success: Boolean = false,
    val mode: String = "",
    val sta_ssid: String = "",
    val message: String = "",
    val error: String? = null
)

data class WifiClearResponse(
    val success: Boolean = false,
    val mode: String = "",
    val message: String = "",
    val error: String? = null
)
