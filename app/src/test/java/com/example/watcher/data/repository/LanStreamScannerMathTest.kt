package com.example.watcher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class LanStreamScannerMathTest {
    @Test
    fun buildCandidatePortsKeepsPreferredPortAheadOfDefaults() {
        assertEquals(listOf(8080, 80, 81), buildCandidatePorts(8080))
    }

    @Test
    fun buildScanHostsCapsBroadNetworksToCurrentSlash24() {
        val localAddress = InetAddress.getByName("192.168.4.10") as Inet4Address

        val hosts = buildScanHosts(localAddress, prefixLength = 16)

        assertEquals(254, hosts.size)
        assertTrue(hosts.contains("192.168.4.1"))
        assertTrue(hosts.contains("192.168.4.254"))
        assertFalse(hosts.contains("192.168.4.10"))
        assertFalse(hosts.contains("192.168.3.1"))
    }

    @Test
    fun buildScanHostsRespectsSmallSubnets() {
        val localAddress = InetAddress.getByName("192.168.4.2") as Inet4Address

        val hosts = buildScanHosts(localAddress, prefixLength = 30)

        assertEquals(listOf("192.168.4.1"), hosts)
    }

    @Test
    fun calculateBroadcastAddressUsesCurrentSubnet() {
        val localAddress = InetAddress.getByName("192.168.4.10") as Inet4Address

        val broadcast = calculateBroadcastAddress(localAddress, prefixLength = 24)

        assertEquals("192.168.4.255", broadcast)
    }

    @Test
    fun parseUdpDiscoveryResponseBuildsEsp32Candidate() {
        val payload = """
            {
              "device_id": "F835DA34E3EC",
              "mode": "sta",
              "ip": "192.168.1.88",
              "http_port": 80,
              "stream_port": 81,
              "stream_url": "http://192.168.1.88:81/stream",
              "discovery_port": 32108,
              "mdns": "http://esp32cam-34e3ec.local"
            }
        """.trimIndent()

        val device = parseUdpDiscoveryResponse(payload)

        assertNotNull(device)
        assertEquals("192.168.1.88", device?.host)
        assertEquals(80, device?.preferredPort)
        assertEquals(81, device?.streamPort)
        assertEquals("F835DA34E3EC", device?.deviceId)
        assertEquals("http://esp32cam-34e3ec.local", device?.mdnsUrl)
    }
}
