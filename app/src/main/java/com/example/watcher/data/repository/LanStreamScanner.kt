package com.example.watcher.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import com.example.watcher.data.model.DiscoveredStreamDevice
import com.example.watcher.data.model.DiscoveredStreamDeviceKind
import com.example.watcher.data.model.VideoStreamSettings
import com.example.watcher.data.remote.DeviceInfoResponse
import com.example.watcher.data.remote.parseDeviceInfoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

class LanStreamScanner(
    private val appContext: Context
) {
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(SCAN_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SCAN_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(SCAN_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun scan(
        preferredPort: Int? = null,
        onDeviceFound: (DiscoveredStreamDevice) -> Unit
    ): StreamScanSummary = withContext(Dispatchers.IO) {
        val subnet = resolveActiveSubnet()
            ?: throw IllegalStateException("No active IPv4 LAN is available for discovery.")
        val hosts = buildScanHosts(subnet.address, subnet.prefixLength)
        if (hosts.isEmpty()) {
            throw IllegalStateException("The current network does not expose any scan targets.")
        }

        val ports = buildCandidatePorts(preferredPort)
        val discoveredCount = AtomicInteger(0)
        val discoveredHosts = linkedSetOf<String>()

        discoverByUdp(subnet = subnet).forEach { device ->
            if (discoveredHosts.add(device.host)) {
                discoveredCount.incrementAndGet()
                onDeviceFound(device)
            }
        }

        supervisorScope {
            val semaphore = Semaphore(SCAN_CONCURRENCY)
            hosts.forEach { host ->
                launch {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        if (host in discoveredHosts) {
                            return@withPermit
                        }
                        val device = probeHost(host = host, ports = ports) ?: return@withPermit
                        if (discoveredHosts.add(device.host)) {
                            discoveredCount.incrementAndGet()
                            onDeviceFound(device)
                        }
                    }
                }
            }
        }

        StreamScanSummary(
            subnetLabel = describeSubnet(subnet.address, subnet.prefixLength),
            scannedHostCount = hosts.size,
            discoveredCount = discoveredCount.get()
        )
    }

    suspend fun rediscoverProvisionedDevice(
        settings: VideoStreamSettings,
        expectedDeviceId: String? = null,
        knownMdnsUrl: String? = null
    ): DiscoveredStreamDevice? = withContext(Dispatchers.IO) {
        val normalizedSettings = settings.normalized()
        val ports = buildCandidatePorts(normalizedSettings.port)

        val preferredHosts = linkedSetOf<String>()
        normalizedSettings.ipAddress.takeIf(String::isNotBlank)?.let(preferredHosts::add)
        extractHost(knownMdnsUrl)?.let(preferredHosts::add)
        deriveMdnsHost(expectedDeviceId)?.let(preferredHosts::add)

        preferredHosts.forEach { host ->
            val device = probeHost(host = host, ports = ports) ?: return@forEach
            if (matchesExpectedDevice(device, expectedDeviceId)) {
                return@withContext device
            }
        }

        val subnet = resolveActiveSubnet() ?: return@withContext null
        val udpPayload = expectedDeviceId?.takeIf(String::isNotBlank) ?: DISCOVERY_PAYLOAD
        discoverByUdp(subnet = subnet, discoveryPayload = udpPayload)
            .firstOrNull { matchesExpectedDevice(it, expectedDeviceId) }
            ?.let { return@withContext it }

        return@withContext runCatching {
            var matchedDevice: DiscoveredStreamDevice? = null
            scan(preferredPort = normalizedSettings.port) { device ->
                if (matchedDevice == null && matchesExpectedDevice(device, expectedDeviceId)) {
                    matchedDevice = device
                }
            }
            matchedDevice
        }.getOrNull()
    }

    private fun resolveActiveSubnet(): ActiveSubnet? {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        val linkAddress = linkProperties.linkAddresses.firstOrNull(::isEligibleIpv4Address) ?: return null
        val address = linkAddress.address as? Inet4Address ?: return null
        return ActiveSubnet(address = address, prefixLength = linkAddress.prefixLength)
    }

    private fun isEligibleIpv4Address(linkAddress: LinkAddress): Boolean {
        val address = linkAddress.address
        return address is Inet4Address &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            !address.hostAddress.isNullOrBlank()
    }

    private fun discoverByUdp(
        subnet: ActiveSubnet,
        discoveryPayload: String = DISCOVERY_PAYLOAD
    ): List<DiscoveredStreamDevice> {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = UDP_RESPONSE_TIMEOUT_MS

                val probe = discoveryPayload.toByteArray(Charsets.UTF_8)
                val targets = linkedSetOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName(calculateBroadcastAddress(subnet.address, subnet.prefixLength))
                )
                targets.forEach { address ->
                    val packet = DatagramPacket(probe, probe.size, address, UDP_DISCOVERY_PORT)
                    socket.send(packet)
                }

                val discovered = linkedMapOf<String, DiscoveredStreamDevice>()
                val deadline = System.currentTimeMillis() + UDP_RESPONSE_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(UDP_PACKET_BUFFER_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }

                    val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val device = parseUdpDiscoveryResponse(payload) ?: continue
                    discovered[device.host] = device
                }
                discovered.values.toList()
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun probeHost(host: String, ports: List<Int>): DiscoveredStreamDevice? {
        val streamPorts = linkedSetOf<Int>()
        val statusPorts = linkedSetOf<Int>()
        val deviceInfoPorts = linkedSetOf<Int>()
        var deviceInfo: DeviceInfoResponse? = null

        ports.forEach { port ->
            currentCoroutineContext().ensureActive()
            val probedInfo = readDeviceInfo(host = host, port = port)
            if (probedInfo != null) {
                if (deviceInfo == null) {
                    deviceInfo = probedInfo
                }
                deviceInfoPorts += port
            }
            if (probeStatus(host = host, port = port)) {
                statusPorts += port
            }
            if (probeStream(host = host, port = port)) {
                streamPorts += port
            }
        }

        if (streamPorts.isEmpty()) {
            return null
        }

        val controlPort = deviceInfoPorts.firstOrNull() ?: statusPorts.firstOrNull()
        val streamPort = when {
            streamPorts.contains(VideoStreamSettings.DEFAULT_STREAM_PORT) -> VideoStreamSettings.DEFAULT_STREAM_PORT
            controlPort != null && streamPorts.contains(controlPort) -> controlPort
            else -> streamPorts.first()
        }
        val kind = if (controlPort != null) {
            DiscoveredStreamDeviceKind.Esp32Camera
        } else {
            DiscoveredStreamDeviceKind.MjpegOnly
        }
        val resolvedHost = deviceInfo?.ip?.takeIf(String::isNotBlank) ?: host

        return DiscoveredStreamDevice(
            host = resolvedHost,
            preferredPort = controlPort ?: streamPort,
            streamPort = streamPort,
            statusPort = controlPort,
            kind = kind,
            deviceId = deviceInfo?.device_id.orEmpty(),
            mdnsUrl = deviceInfo?.mdns.orEmpty()
        )
    }

    private fun readDeviceInfo(host: String, port: Int): DeviceInfoResponse? {
        val request = Request.Builder()
            .url(buildHttpUrl(host = host, port = port, path = "/device/info"))
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@use null
                }
                val parsed = parseDeviceInfoResponse(body)
                if (parsed.device_id.isBlank()) {
                    return@use null
                }
                parsed
            }
        }.getOrNull()
    }

    private fun probeStatus(host: String, port: Int): Boolean {
        val request = Request.Builder()
            .url(buildHttpUrl(host = host, port = port, path = "/status"))
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use false
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@use false
                }
                val json = org.json.JSONObject(body)
                json.has("framesize") && json.has("quality")
            }
        }.getOrDefault(false)
    }

    private fun probeStream(host: String, port: Int): Boolean {
        val request = Request.Builder()
            .url(buildHttpUrl(host = host, port = port, path = "/stream"))
            .get()
            .header("Accept", "multipart/x-mixed-replace,image/jpeg;q=0.9,*/*;q=0.5")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use false
                }
                val body = response.body ?: return@use false
                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (
                    contentType.contains("multipart/x-mixed-replace") ||
                    contentType.startsWith("image/") ||
                    contentType.contains("application/octet-stream")
                ) {
                    return@use true
                }

                val source = body.source()
                source.request(STREAM_PEEK_BYTES)
                val peekBuffer = source.buffer.clone()
                val peekSize = minOf(STREAM_PEEK_BYTES, peekBuffer.size)
                if (peekSize <= 0L) {
                    return@use false
                }
                val bytes = peekBuffer.readByteArray(peekSize)
                looksLikeMjpeg(bytes)
            }
        }.getOrDefault(false)
    }

    private fun looksLikeMjpeg(bytes: ByteArray): Boolean {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return true
        }
        if (bytes.size >= 2 && bytes[0] == '-'.code.toByte() && bytes[1] == '-'.code.toByte()) {
            return true
        }
        return false
    }
}

data class StreamScanSummary(
    val subnetLabel: String,
    val scannedHostCount: Int,
    val discoveredCount: Int
)

internal data class ActiveSubnet(
    val address: Inet4Address,
    val prefixLength: Int
)

internal fun parseUdpDiscoveryResponse(payload: String): DiscoveredStreamDevice? {
    return runCatching {
        val json = org.json.JSONObject(payload)
        val host = json.optString("ip").takeIf(String::isNotBlank) ?: return null
        val httpPort = json.optInt("http_port", VideoStreamSettings.DEFAULT_PORT)
        val streamPort = json.optInt("stream_port", VideoStreamSettings.DEFAULT_STREAM_PORT)
        DiscoveredStreamDevice(
            host = host,
            preferredPort = httpPort,
            streamPort = streamPort,
            statusPort = httpPort,
            kind = DiscoveredStreamDeviceKind.Esp32Camera,
            deviceId = json.optString("device_id"),
            mdnsUrl = json.optString("mdns")
        )
    }.getOrNull()
}

private fun matchesExpectedDevice(
    device: DiscoveredStreamDevice,
    expectedDeviceId: String?
): Boolean {
    val normalizedExpectedId = expectedDeviceId?.trim().orEmpty()
    return normalizedExpectedId.isBlank() || device.deviceId.equals(normalizedExpectedId, ignoreCase = true)
}

private fun extractHost(rawUrl: String?): String? {
    val value = rawUrl?.trim().orEmpty()
    if (value.isBlank()) return null
    return runCatching { URI(value).host?.trim()?.takeIf(String::isNotBlank) }.getOrNull()
}

private fun deriveMdnsHost(deviceId: String?): String? {
    val normalized = deviceId?.trim().orEmpty()
    if (normalized.isBlank()) return null
    val suffix = normalized.takeLast(6).lowercase()
    return "esp32cam-$suffix.local"
}

internal fun buildCandidatePorts(preferredPort: Int?): List<Int> {
    val ports = linkedSetOf<Int>()
    preferredPort?.takeIf { it in 1..65535 }?.let(ports::add)
    ports += VideoStreamSettings.DEFAULT_PORT
    ports += VideoStreamSettings.DEFAULT_STREAM_PORT
    return ports.toList()
}

internal fun buildScanHosts(localAddress: Inet4Address, prefixLength: Int): List<String> {
    val effectivePrefix = when {
        prefixLength in 1..23 -> 24
        prefixLength in 24..30 -> prefixLength
        else -> return emptyList()
    }
    val local = ipv4ToLong(localAddress)
    val hostBits = 32 - effectivePrefix
    val mask = (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
    val network = local and mask
    val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
    if (broadcast - network <= 1L) {
        return emptyList()
    }

    return buildList {
        for (candidate in (network + 1)..<broadcast) {
            if (candidate != local) {
                add(longToIpv4(candidate))
            }
        }
    }
}

internal fun describeSubnet(localAddress: Inet4Address, prefixLength: Int): String {
    val effectivePrefix = when {
        prefixLength in 1..23 -> 24
        prefixLength in 24..30 -> prefixLength
        else -> prefixLength
    }
    val local = ipv4ToLong(localAddress)
    val hostBits = 32 - effectivePrefix
    val mask = if (effectivePrefix in 1..31) {
        (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
    } else {
        0xFFFFFFFFL
    }
    val network = local and mask
    return "${longToIpv4(network)}/$effectivePrefix"
}

internal fun calculateBroadcastAddress(localAddress: Inet4Address, prefixLength: Int): String {
    val effectivePrefix = when {
        prefixLength in 1..23 -> 24
        prefixLength in 24..30 -> prefixLength
        else -> 24
    }
    val local = ipv4ToLong(localAddress)
    val hostBits = 32 - effectivePrefix
    val mask = (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
    val network = local and mask
    val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
    return longToIpv4(broadcast)
}

private fun buildHttpUrl(host: String, port: Int, path: String): String {
    return if (port == VideoStreamSettings.DEFAULT_PORT) {
        "http://$host$path"
    } else {
        "http://$host:$port$path"
    }
}

private fun ipv4ToLong(address: Inet4Address): Long {
    return address.address.fold(0L) { acc, byte ->
        (acc shl 8) or (byte.toInt() and 0xFF).toLong()
    }
}

private fun longToIpv4(value: Long): String {
    return listOf(24, 16, 8, 0)
        .joinToString(".") { shift -> ((value shr shift) and 0xFF).toString() }
}

private const val SCAN_CONCURRENCY = 24
private const val SCAN_CONNECT_TIMEOUT_MS = 350L
private const val SCAN_READ_TIMEOUT_MS = 500L
private const val SCAN_CALL_TIMEOUT_MS = 900L
private const val STREAM_PEEK_BYTES = 16L
private const val UDP_DISCOVERY_PORT = 32108
private const val UDP_RESPONSE_TIMEOUT_MS = 900
private const val UDP_PACKET_BUFFER_SIZE = 2048
private const val DISCOVERY_PAYLOAD = "DISCOVER_ESP32_CAM"
