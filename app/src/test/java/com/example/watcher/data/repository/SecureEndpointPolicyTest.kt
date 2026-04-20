package com.example.watcher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureEndpointPolicyTest {
    @Test
    fun acceptsHttpsEndpoints() {
        assertNull(validateSecureEndpoint("https://api.example.com/v1", "Provider endpoint"))
    }

    @Test
    fun rejectsHttpEndpoints() {
        assertEquals(
            "Provider endpoint must use https:// to protect API keys in transit.",
            validateSecureEndpoint("http://api.example.com/v1", "Provider endpoint")
        )
    }
}
