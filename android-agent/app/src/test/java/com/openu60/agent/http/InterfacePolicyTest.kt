package com.openu60.agent.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class InterfacePolicyTest {
    @Test fun hotspotAndUsbAreLocal() {
        listOf("wlan0", "ap0", "swlan0", "rndis0", "ncm0", "bt-pan", "br0", "tailscale0", "tun0", "lo")
            .forEach { assertTrue(it, InterfacePolicy.isLocalName(it)) }
    }

    @Test fun cellularIsRefused() {
        listOf("rmnet_data0", "seth_lte0", "sipa_eth0", "ccmni0", "radio0", "eth0", "dummy0")
            .forEach { assertFalse(it, InterfacePolicy.isLocalName(it)) }
    }

    @Test fun loopbackAlwaysAllowed() {
        assertTrue(InterfacePolicy.allows(InetAddress.getByName("127.0.0.1")))
    }
}
