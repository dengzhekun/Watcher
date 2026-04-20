package com.example.watcher.data.model

const val LEGACY_PROVISION_WIFI_SSID_MAX_BYTES = 32
const val PROVISION_WIFI_PASSWORD_MIN_LENGTH = 8
const val PROVISION_WIFI_PASSWORD_MAX_LENGTH = 64

fun normalizedProvisionWifiSsid(ssid: String): String = ssid

fun provisionWifiSsidUtf8Length(ssid: String): Int {
    return normalizedProvisionWifiSsid(ssid).toByteArray(Charsets.UTF_8).size
}

fun validateProvisionWifiSsid(ssid: String): String? {
    val normalizedSsid = normalizedProvisionWifiSsid(ssid)
    if (normalizedSsid.isEmpty()) {
        return "Wi-Fi name must not be empty."
    }
    return null
}

fun exceedsLegacyProvisionWifiSsidLimit(ssid: String): Boolean {
    return provisionWifiSsidUtf8Length(ssid) > LEGACY_PROVISION_WIFI_SSID_MAX_BYTES
}

fun validateProvisionWifiPassword(password: String): String? {
    val passwordLength = password.length
    if (
        passwordLength > PROVISION_WIFI_PASSWORD_MAX_LENGTH ||
        (passwordLength > 0 && passwordLength < PROVISION_WIFI_PASSWORD_MIN_LENGTH)
    ) {
        return "Password length is invalid. Current length is $passwordLength, but non-empty passwords must be 8 to 64 characters."
    }
    return null
}
