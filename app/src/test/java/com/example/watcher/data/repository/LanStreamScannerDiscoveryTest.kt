package com.example.watcher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class LanStreamScannerDiscoveryTest {
    @Test
    fun determineProvisionRediscoveryModeUsesHighestPrioritySubnet() {
        val hotspotSubnet = discoverySubnet(
            host = "192.168.232.1",
            source = DiscoverySubnetSource.HotspotHost
        )
        val activeSubnet = discoverySubnet(
            host = "192.168.1.25",
            source = DiscoverySubnetSource.ActiveNetwork
        )

        val mode = determineProvisionRediscoveryMode(listOf(hotspotSubnet, activeSubnet))

        assertEquals(ProvisionRediscoveryMode.SearchingActiveLan, mode)
    }

    @Test
    fun determineProvisionRediscoveryModeFallsBackToHotspotSubnet() {
        val hotspotSubnet = discoverySubnet(
            host = "192.168.232.1",
            source = DiscoverySubnetSource.HotspotHost
        )

        val mode = determineProvisionRediscoveryMode(listOf(hotspotSubnet))

        assertEquals(ProvisionRediscoveryMode.SearchingHotspotSubnet, mode)
    }

    @Test
    fun classifyDiscoverySubnetSourceTreatsSoftApAsHotspot() {
        val source = classifyDiscoverySubnetSource(
            interfaceName = "ap0",
            address = ipv4("192.168.43.1"),
            prefixLength = 24,
            activeSubnet = null
        )

        assertEquals(DiscoverySubnetSource.HotspotHost, source)
    }

    @Test
    fun classifyDiscoverySubnetSourcePreservesActiveSubnet() {
        val activeSubnet = discoverySubnet(
            host = "192.168.1.25",
            source = DiscoverySubnetSource.ActiveNetwork
        )

        val source = classifyDiscoverySubnetSource(
            interfaceName = "wlan0",
            address = ipv4("192.168.1.99"),
            prefixLength = 24,
            activeSubnet = activeSubnet
        )

        assertEquals(DiscoverySubnetSource.ActiveNetwork, source)
    }

    private fun discoverySubnet(
        host: String,
        source: DiscoverySubnetSource
    ): DiscoverySubnet {
        return DiscoverySubnet(
            address = ipv4(host),
            prefixLength = 24,
            source = source,
            interfaceName = "test0"
        )
    }

    private fun ipv4(host: String): Inet4Address =
        InetAddress.getByName(host) as Inet4Address
}
