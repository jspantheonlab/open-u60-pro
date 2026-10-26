package com.openu60.agent.http

import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Which local interfaces may serve the API. Like zte-agent's mobile-side check, but an
 * allow-list: a hotspot's cellular interface may carry a public address (names vary by
 * modem: rmnet*, seth_lte*, ccmni*), and anything not known to be local is refused.
 */
object InterfacePolicy {
    private val LOCAL_PREFIXES = listOf(
        "lo", // adb forward
        "wlan", "ap", "swlan", "softap", "wifi", // hotspot and Wi-Fi repeater
        "rndis", "usb", "ncm", // USB tethering
        "bt-pan", "br", // Bluetooth PAN, bridged hotspots (no "eth": UNISOC names cellular sipa_eth*)
        "tailscale", "tun", "wg", // VPN to reach it remotely, by choice
    )

    fun isLocalName(name: String): Boolean = LOCAL_PREFIXES.any { name.startsWith(it) }

    /** True when the connection arrived on an allowed interface. Unknown means refused. */
    fun allows(localAddress: InetAddress): Boolean {
        if (localAddress.isLoopbackAddress) return true
        val nif = runCatching { NetworkInterface.getByInetAddress(localAddress) }.getOrNull() ?: return false
        return isLocalName(nif.name)
    }
}
