package com.example.watcher.data.remote

import org.json.JSONObject

internal fun parseDeviceInfoResponse(rawBody: String): DeviceInfoResponse {
    val repairedBody = repairMalformedDeviceInfoJson(rawBody)
    val json = JSONObject(repairedBody)
    return DeviceInfoResponse(
        device_id = json.optString("device_id"),
        mode = json.optString("mode"),
        ap_ssid = json.optString("ap_ssid"),
        ap_ip = json.optString("ap_ip"),
        wifi_configured = json.optBoolean("wifi_configured"),
        sta_ssid = json.optString("sta_ssid"),
        ip = json.optString("ip"),
        http_port = json.optInt("http_port", 80),
        stream_port = json.optInt("stream_port", 81),
        discovery_port = json.optInt("discovery_port", 32108),
        mdns = json.optString("mdns"),
        mdns_active = json.optBoolean("mdns_active"),
        stream_url = json.optString("stream_url"),
        wifi_connect_result = json.optString("wifi_connect_result"),
        wifi_connect_status = json.optInt("wifi_connect_status", 0),
        wifi_connect_esp_err = json.optInt("wifi_connect_esp_err", 0),
        wifi_disconnect_reason = json.optInt("wifi_disconnect_reason", 0),
        wifi_fallback_to_ap = json.optBoolean("wifi_fallback_to_ap"),
        wifi_ssid_bytes = json.optInt("wifi_ssid_bytes", 0)
    )
}

internal fun repairMalformedDeviceInfoJson(rawBody: String): String {
    return rawBody
        .replace("\"mode\":\"ap,\"ap_ssid\"", "\"mode\":\"ap\",\"ap_ssid\"")
        .replace("\"mode\":\"sta,\"ap_ssid\"", "\"mode\":\"sta\",\"ap_ssid\"")
}
