package com.aeropad.remote.features

import com.aeropad.remote.data.hosts.HostProfile
import com.aeropad.remote.data.hosts.HostProfileCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 MATRIX 4 — host profile codec: upsert, cap, PIN obfuscation. */
class HostProfileTest {

    private fun p(addr: String, transport: String = HostProfile.TRANSPORT_BT) =
        HostProfile(id = addr, label = addr, transport = transport, address = addr)

    @Test
    fun `encode decode round trips`() {
        val list = listOf(p("192.168.1.10"), p("AA:BB", HostProfile.TRANSPORT_BT))
        val back = HostProfileCodec.decode(HostProfileCodec.encode(list))
        assertEquals(list.map { it.sanitized() }, back)
    }

    @Test
    fun `corrupt json decodes to empty never throws`() {
        assertEquals(emptyList<HostProfile>(), HostProfileCodec.decode("{broken"))
        assertEquals(emptyList<HostProfile>(), HostProfileCodec.decode(null))
        assertEquals(emptyList<HostProfile>(), HostProfileCodec.decode(""))
    }

    @Test
    fun `upsert dedupes by transport plus address and prepends`() {
        var list = HostProfileCodec.upsert(emptyList(), p("10.0.0.1"))
        list = HostProfileCodec.upsert(list, p("10.0.0.2"))
        // Same address again → moves to front, no duplicate.
        list = HostProfileCodec.upsert(list, p("10.0.0.1").copy(label = "renamed"))
        assertEquals(2, list.size)
        assertEquals("renamed", list[0].label)
        // Same ADDRESS but different transport = a different machine entry.
        list = HostProfileCodec.upsert(list, p("10.0.0.1_bt", HostProfile.TRANSPORT_BT))
        assertEquals(3, list.size)
    }

    @Test
    fun `cap at MAX_PROFILES drops the oldest`() {
        var list = emptyList<HostProfile>()
        (1..20).forEach { list = HostProfileCodec.upsert(list, p("10.0.0.$it")) }
        assertEquals(HostProfileCodec.MAX_PROFILES, list.size)
        assertEquals("10.0.0.20", list[0].address)          // newest first
        assertTrue(list.none { it.address == "10.0.0.1" })  // oldest gone
    }

    @Test
    fun `sanitize fixes blank label bad transport and port`() {
        val bad = HostProfile(
            id = "x", label = "   ", transport = "CARRIER_PIGEON",
            address = "10.1.1.1"
        ).sanitized()
        assertEquals("10.1.1.1", bad.label)                     // falls back to address
        assertEquals(HostProfile.TRANSPORT_BT, bad.transport)   // unknown → BT
        
    }

    @Test
    fun `remove by id`() {
        val list = listOf(p("a"), p("b"))
        assertEquals(listOf(p("b").sanitized()), HostProfileCodec.remove(list.map { it.sanitized() }, "a"))
    }
}
