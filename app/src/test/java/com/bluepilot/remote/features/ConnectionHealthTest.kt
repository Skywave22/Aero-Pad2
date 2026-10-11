package com.bluepilot.remote.features

import com.bluepilot.remote.domain.ConnectionHealthTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADV SECTION 5 — health tracker aggregates REAL measurements correctly.
 * A fake clock drives time; the values fed in represent measured send
 * durations exactly as HidEngine produces them.
 */
class ConnectionHealthTest {

    private var now = 1_000_000L
    private fun tracker() = ConnectionHealthTracker(clock = { now })

    @Test
    fun `counters, rate and rolling stats aggregate measured sends`() {
        val t = tracker()
        t.markConnected()
        // 10 reports this second, 100µs each.
        repeat(10) { t.onReport(true, 100) }
        // One slow (2000µs) + one failed.
        t.onReport(true, 2000)
        t.onReport(false, 50)
        val s = t.snapshot()
        assertEquals(12, s.totalSent)
        assertEquals(1, s.totalFailed)
        assertEquals(12, s.reportsPerSecond)
        assertEquals((10 * 100L + 2000 + 50) / 12, s.recentMeanSendUs)
        assertEquals(2000, s.recentMaxSendUs)
        assertNull(s.rssiDbm)                      // never invented
    }

    @Test
    fun `window drops old records but totals persist`() {
        val t = tracker()
        repeat(5) { t.onReport(true, 100) }
        now += 61_000                              // move past the 60s window
        t.onReport(true, 100)
        val s = t.snapshot()
        assertEquals(6, s.totalSent)               // lifetime total kept
        assertEquals(1, s.history.sumOf { it.sent })  // window holds only the fresh one
        assertEquals(1, s.reportsPerSecond)
    }

    @Test
    fun `history buckets group by second with failure counts`() {
        val t = tracker()
        repeat(3) { t.onReport(true, 100) }
        now += 1000
        t.onReport(false, 300)
        t.onReport(true, 500)
        val h = t.snapshot().history
        assertEquals(2, h.size)
        assertEquals(3, h[0].sent); assertEquals(0, h[0].failed)
        assertEquals(2, h[1].sent); assertEquals(1, h[1].failed)
        assertEquals(400, h[1].meanSendUs)
        assertEquals(500, h[1].maxSendUs)
    }

    @Test
    fun `session time and disconnects tracked`() {
        val t = tracker()
        t.markConnected()
        now += 30_000
        t.markDisconnected()
        t.markDisconnected()
        val s = t.snapshot()
        assertEquals(30_000, s.sessionMs)
        assertEquals(2, s.disconnects)
    }

    // ---------- Classification (thresholds documented in code) ----------

    @Test
    fun `classification green yellow red from real conditions`() {
        val t = tracker()
        repeat(5) { t.onReport(true, 100) }
        assertEquals(ConnectionHealthTracker.Health.GOOD,
            ConnectionHealthTracker.classify(t.snapshot()))

        t.onRssi(-75)                              // weak-ish signal
        assertEquals(ConnectionHealthTracker.Health.FAIR,
            ConnectionHealthTracker.classify(t.snapshot()))

        t.onRssi(-90)                              // very weak
        assertEquals(ConnectionHealthTracker.Health.POOR,
            ConnectionHealthTracker.classify(t.snapshot()))

        val t2 = tracker()
        t2.onReport(false, 100)                    // failures → POOR
        assertEquals(ConnectionHealthTracker.Health.POOR,
            ConnectionHealthTracker.classify(t2.snapshot()))

        val t3 = tracker()
        t3.onReport(true, 8_000)                   // slow sends → FAIR
        assertEquals(ConnectionHealthTracker.Health.FAIR,
            ConnectionHealthTracker.classify(t3.snapshot()))
    }

    @Test
    fun `suggestions derive only from real conditions`() {
        val t = tracker()
        repeat(3) { t.onReport(true, 100) }
        // Healthy + no saver → no suggestions.
        assertTrue(ConnectionHealthTracker.suggestions(t.snapshot(), false, false).isEmpty())
        // Battery saver only.
        val saver = ConnectionHealthTracker.suggestions(t.snapshot(), false, true)
        assertEquals(1, saver.size)
        assertTrue(saver[0].contains("Battery saver"))
        // Weak RSSI + slow sends + 3D on.
        t.onRssi(-84)
        t.onReport(true, 9_000)
        val tips = ConnectionHealthTracker.suggestions(t.snapshot(), true, false)
        assertTrue(tips.any { it.contains("Weak signal (-84 dBm)") })
        assertTrue(tips.any { it.contains("Reduce Motion") })
    }

    @Test
    fun `reset clears everything`() {
        val t = tracker()
        t.markConnected(); t.onReport(true, 100); t.onRssi(-60); t.markDisconnected()
        t.reset()
        val s = t.snapshot()
        assertEquals(0, s.totalSent)
        assertEquals(0, s.disconnects)
        assertNull(s.rssiDbm)
        assertEquals(0, s.sessionMs)
    }
}
