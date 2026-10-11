package com.aeropad.remote.features

import com.aeropad.remote.data.hosts.HostProfile
import com.aeropad.remote.data.hosts.HostProfileCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host profile codec (Bluetooth-only): upsert, cap, legacy-WIFI cleanup. */
class HostProfileTest {

    private fun p(addr: String) =
        HostProfile(id = addr, label = addr, transport = HostProfile.TRANSPORT_BT, address = addr)

    @Test
    fun `encode decode round trips`() {
        val list = listOf(p("AA:BB:CC:DD:EE:01"), p("AA:BB:CC:DD:EE:02"))
        val back = HostProfileCodec.decode(HostProfileCodec.encode(list))
        assertEquals(list.map { it.sanitized() }, back)
    }

    @Test
    fun `legacy wifi entries are dropped on decode`() {
        // An old file with an IP-based (WiFi) entry must decode without it.
        val raw = HostProfileCodec.encode(
            listOf(
                HostProfile(id = "1", label = "pc", transport = "WIFI", address = "192.168.1.10", port = 48653),
                p("AA:BB:CC:DD:EE:01")
            )
        )
        val back = HostProfileCodec.decode(raw)
        assertEquals(1, back.size)
        assertEquals("AA:BB:CC:DD:EE:01", back[0].address)
    }

    @Test
    fun `corrupt json decodes to empty never throws`() {
        assertEquals(emptyList<HostProfile>(), HostProfileCodec.decode("{broken"))
        assertEquals(emptyList<HostProfile>(), HostProfileCodec.decode(null))
        assertEquals(emptyList<HostProfile>(), HostProfileCodec.decode(""))
    }

    @Test
    fun `upsert dedupes by address and prepends`() {
        var list = HostProfileCodec.upsert(emptyList(), p("AA:BB:CC:DD:EE:01"))
        list = HostProfileCodec.upsert(list, p("AA:BB:CC:DD:EE:02"))
        // Same address again → moves to front, no duplicate.
        list = HostProfileCodec.upsert(list, p("AA:BB:CC:DD:EE:01").copy(label = "renamed"))
        assertEquals(2, list.size)
        assertEquals("renamed", list[0].label)
    }

    @Test
    fun `cap at MAX_PROFILES drops the oldest`() {
        var list = emptyList<HostProfile>()
        (1..20).forEach { list = HostProfileCodec.upsert(list, p("AA:BB:CC:DD:EE:%02X".format(it))) }
        assertEquals(HostProfileCodec.MAX_PROFILES, list.size)
        assertEquals("AA:BB:CC:DD:EE:14", list[0].address)           // newest first
        assertTrue(list.none { it.address == "AA:BB:CC:DD:EE:01" })  // oldest gone
    }

    @Test
    fun `sanitize fixes blank label and forces BT transport`() {
        val bad = HostProfile(
            id = "x", label = "   ", transport = "CARRIER_PIGEON",
            address = "AA:BB:CC:DD:EE:01", port = 99999
        ).sanitized()
        assertEquals("AA:BB:CC:DD:EE:01", bad.label)               // falls back to address
        assertEquals(HostProfile.TRANSPORT_BT, bad.transport)      // always BT now
        assertEquals(65535, bad.port)
    }

    @Test
    fun `remove by id`() {
        val list = listOf(p("AA:BB:CC:DD:EE:01"), p("AA:BB:CC:DD:EE:02"))
        assertEquals(listOf(p("AA:BB:CC:DD:EE:02").sanitized()), HostProfileCodec.remove(list.map { it.sanitized() }, "AA:BB:CC:DD:EE:01"))
    }
}
