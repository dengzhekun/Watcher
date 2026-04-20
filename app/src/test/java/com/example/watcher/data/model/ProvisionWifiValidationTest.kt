package com.example.watcher.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionWifiValidationTest {
    @Test
    fun chineseWifiNameIsMeasuredByUtf8Bytes() {
        assertEquals(6, provisionWifiSsidUtf8Length("中文"))
        assertNull(validateProvisionWifiSsid("家庭WiFi"))
    }

    @Test
    fun wifiNamePreservesLeadingAndTrailingSpaces() {
        val ssid = "  Home WiFi  "

        assertEquals(ssid, normalizedProvisionWifiSsid(ssid))
        assertNull(validateProvisionWifiSsid(" "))
    }

    @Test
    fun wifiNameLongerThan32Utf8BytesIsAllowedButFlaggedForLegacyFirmware() {
        val ssid = "超长中文超长中文超长中文"

        assertEquals(27, provisionWifiSsidUtf8Length(ssid))
        assertNull(validateProvisionWifiSsid(ssid))
        assertFalse(exceedsLegacyProvisionWifiSsidLimit(ssid))
        assertNull(validateProvisionWifiSsid("$ssid网络"))
        assertTrue(exceedsLegacyProvisionWifiSsidLimit("$ssid网络"))
    }

    @Test
    fun passwordValidationMatchesDeviceRules() {
        assertEquals(
            "Password length is invalid. Current length is 7, but non-empty passwords must be 8 to 64 characters.",
            validateProvisionWifiPassword("1234567")
        )
        assertNull(validateProvisionWifiPassword(""))
        assertNull(validateProvisionWifiPassword("12345678"))
    }
}
