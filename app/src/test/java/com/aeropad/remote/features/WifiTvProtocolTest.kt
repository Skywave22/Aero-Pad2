package com.aeropad.remote.features

import com.aeropad.remote.data.tv.PhoneRemoteProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiTvProtocolTest {
    @Test fun `validates local IPv4 addresses`() {
        assertTrue(PhoneRemoteProtocol.isIpv4("192.168.1.40"))
        assertFalse(PhoneRemoteProtocol.isIpv4("999.1.1.1"))
        assertFalse(PhoneRemoteProtocol.isIpv4("tv.local"))
    }

    @Test fun `extracts a compatible private LAN responder only`() {
        assertEquals("192.168.1.40", PhoneRemoteProtocol.discoveredHost("IP*:192.168.1.40", "192.168.1.40"))
        assertEquals("10.0.0.8", PhoneRemoteProtocol.discoveredHost("apps=Home,Settings", "10.0.0.8"))
        assertNull(PhoneRemoteProtocol.discoveredHost("unrelated response", "192.168.1.40"))
        assertNull(PhoneRemoteProtocol.discoveredHost("IP*:8.8.8.8", "8.8.8.8"))
    }

    @Test fun `wraps commands with the sender Wi-Fi address`() {
        assertEquals("192.168.1.7&23", PhoneRemoteProtocol.envelope("192.168.1.7", "23"))
    }
}
