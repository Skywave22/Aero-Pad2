package com.bluepilot.remote.features

import com.bluepilot.remote.data.history.ConnectionSession
import com.bluepilot.remote.data.history.HistoryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AEROPAD v1.0 #41 — connection history codec (real sessions). */
class HistoryCodecTest {

    @Test
    fun `push newest first and capped at 50`() {
        var list = emptyList<ConnectionSession>()
        for (i in 1..60) {
            list = HistoryCodec.push(list, ConnectionSession("BLUETOOTH", "host$i", i.toLong()))
        }
        assertEquals(HistoryCodec.MAX, list.size)
        assertEquals("host60", list.first().hostName)
    }

    @Test
    fun `closeOpen closes only the open session of that transport`() {
        var list = listOf(
            ConnectionSession("WIFI", "pc", 100L),                          // open
            ConnectionSession("BLUETOOTH", "tv", 50L),                      // open
            ConnectionSession("WIFI", "old", 10L, endedAt = 20L)            // closed
        )
        list = HistoryCodec.closeOpen(list, "WIFI", 200L, "user disconnect")
        assertEquals(200L, list[0].endedAt)
        assertEquals("user disconnect", list[0].disconnectReason)
        assertEquals(0L, list[1].endedAt)      // BT session untouched
        assertEquals(20L, list[2].endedAt)     // already-closed untouched
    }

    @Test
    fun `round trip and corrupt input safe`() {
        val list = listOf(ConnectionSession("WIFI", "Desktop", 1L, 2L, "link dropped"))
        assertEquals(list, HistoryCodec.decode(HistoryCodec.encode(list)))
        assertTrue(HistoryCodec.decode("{bad").isEmpty())
        assertTrue(HistoryCodec.decode(null).isEmpty())
        assertTrue(HistoryCodec.decode("").isEmpty())
    }

    @Test
    fun `closeOpen with no open session is a no-op`() {
        val list = listOf(ConnectionSession("WIFI", "pc", 1L, 2L))
        assertEquals(list, HistoryCodec.closeOpen(list, "WIFI", 9L, "x"))
        assertEquals(emptyList<ConnectionSession>(),
            HistoryCodec.closeOpen(emptyList(), "BLUETOOTH", 9L, "x"))
    }
}
