package com.example.watcher.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStreamSettingsTest {
    @Test
    fun streamUrlUsesFixedEsp32StreamPort() {
        val settings = VideoStreamSettings(
            ipAddress = "192.168.4.1",
            port = 80
        )

        assertEquals("http://192.168.4.1:81/stream", settings.streamUrl)
        assertEquals(listOf("http://192.168.4.1:81/stream"), settings.candidateStreamUrls)
    }

    @Test
    fun normalizedFallsBackToFixedDeviceIpAndTrimsPreferredWifi() {
        val normalized = VideoStreamSettings(
            ipAddress = "   ",
            preferredWifiSsid = "  HomeWiFi  "
        ).normalized()

        assertEquals(VideoStreamSettings.DEFAULT_DEVICE_IP, normalized.ipAddress)
        assertEquals("HomeWiFi", normalized.preferredWifiSsid)
    }
}
