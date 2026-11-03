package com.aeropad.remote.data.tv

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * Small LAN client for the UDP protocol used by PhoneRemote-compatible
 * HiSilicon TV boxes. This is an independent feature; Bluetooth HID controls
 * continue to use AeroPad's existing connection stack.
 *
 * The TV protocol is unauthenticated and unencrypted. Only use it on a trusted
 * home network. No TV vendor code or binaries are bundled here.
 */
object PhoneRemoteProtocol {
    const val PORT = 7899
    private val ipv4 = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
    private val discoveryMarkers = listOf("IP*:", "apps=", "ap*s=", "apks=")

    fun isIpv4(value: String): Boolean = ipv4.matches(value) &&
        value.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }

    /** Accept only replies associated with the TV discovery protocol. */
    fun discoveredHost(payload: String, sourceAddress: String): String? {
        if (discoveryMarkers.none(payload::contains)) return null
        val marked = Regex("IP\\*:\\s*([0-9]{1,3}(?:\\.[0-9]{1,3}){3})")
            .find(payload)?.groupValues?.getOrNull(1)
        val candidate = marked?.takeIf(::isIpv4) ?: sourceAddress
        if (!isIpv4(candidate)) return null
        val addr = runCatching { InetAddress.getByName(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { addr.isSiteLocalAddress }
    }

    fun envelope(localIp: String, command: String): String = "$localIp&$command"

    /** Replaces the live text/search value understood by the compatible TV remote protocol. */
    fun searchCommand(text: String): String = "key=search=$text"
}

data class WifiTv(val address: String, val label: String = "HiSilicon TV box")

/** UDP discovery, pairing handshake, and command transport. Methods do blocking
 * socket work and must be called on Dispatchers.IO. */
class WifiTvRemoteClient {
    fun discover(timeoutMs: Int = 2_500): List<WifiTv> {
        val found = linkedMapOf<String, WifiTv>()
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(PhoneRemoteProtocol.PORT))
            socket.soTimeout = 350
            sendPacket(socket, "POSTIP", InetAddress.getByName("255.255.255.255"))
            val deadline = System.currentTimeMillis() + timeoutMs
            val bytes = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(bytes, bytes.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val payload = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                val host = PhoneRemoteProtocol.discoveredHost(payload, packet.address.hostAddress ?: continue)
                if (host != null) found.putIfAbsent(host, WifiTv(host))
            }
        }
        return found.values.toList()
    }

    fun connect(tvAddress: String) {
        require(PhoneRemoteProtocol.isIpv4(tvAddress)) { "Enter a valid IPv4 address." }
        val localIp = localWifiIpv4() ?: throw IllegalStateException(
            "No active Wi-Fi IPv4 address. Join the same Wi-Fi network as your TV and try again."
        )
        send("TVIP=$tvAddress&${android.os.Build.BRAND} ${android.os.Build.MODEL}", localIp)
    }

    fun sendRemoteCommand(command: String) {
        require(command.isNotBlank())
        val localIp = localWifiIpv4() ?: throw IllegalStateException(
            "Wi-Fi is not connected. Join the same network as your TV."
        )
        send(command, localIp)
    }

    fun disconnect() {
        localWifiIpv4()?.let { runCatching { send("i will disconnect", it) } }
    }

    private fun send(command: String, localIp: String) {
        val bytes = PhoneRemoteProtocol.envelope(localIp, command).toByteArray(StandardCharsets.UTF_8)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val packet = DatagramPacket(
                bytes, bytes.size, InetAddress.getByName("255.255.255.255"), PhoneRemoteProtocol.PORT
            )
            socket.send(packet)
        }
    }

    private fun sendPacket(socket: DatagramSocket, payload: String, destination: InetAddress) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        socket.send(DatagramPacket(bytes, bytes.size, destination, PhoneRemoteProtocol.PORT))
    }

    private fun localWifiIpv4(): String? {
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrNull() ?: return null
        val ordered = interfaces.sortedBy { if (it.name.startsWith("wlan", true) || it.name.startsWith("wifi", true)) 0 else 1 }
        for (network in ordered) {
            if (runCatching { !network.isUp || network.isLoopback }.getOrDefault(true)) continue
            val addresses = runCatching { Collections.list(network.inetAddresses) }.getOrDefault(emptyList())
            for (address in addresses) {
                if (!address.isLoopbackAddress && !address.isLinkLocalAddress && address is java.net.Inet4Address) {
                    return address.hostAddress?.takeIf(PhoneRemoteProtocol::isIpv4)
                }
            }
        }
        return null
    }
}
