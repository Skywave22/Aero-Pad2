package com.aeropad.remote.features

import com.aeropad.remote.domain.TurboRepeater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F10 — hold-to-repeat ("turbo") on a key button. */
class TurboRepeaterTest {

    private class FakeClock(var now: Long = 0L) {
        fun advance(ms: Long) { now += ms }
    }

    private fun repeater(clock: FakeClock) = TurboRepeater(
        holdDelayMs = 300,
        intervalMs = 60,
        clock = clock::now
    )

    @Test
    fun `a normal tap never repeats`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        clock.advance(120)                 // a quick tap
        assertEquals(0, r.poll())
        assertFalse(r.isRepeating)
        r.stop()
        assertEquals(0, r.poll())
    }

    @Test
    fun `nothing fires during the hold delay`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        clock.advance(299)
        assertEquals(0, r.poll())
    }

    @Test
    fun `the first repeat lands as soon as the delay expires`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        clock.advance(300)
        assertEquals(1, r.poll())
        assertTrue(r.isRepeating)
    }

    @Test
    fun `repeats follow the interval`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        clock.advance(300)
        assertEquals(1, r.poll())          // fires at 300
        clock.advance(59)
        assertEquals(0, r.poll())          // 359, not yet
        clock.advance(1)
        assertEquals(1, r.poll())          // 360
    }

    @Test
    fun `a one second hold produces a sane number of presses`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        var presses = 0
        repeat(100) {                      // 100 x 10 ms = 1 s
            clock.advance(10)
            presses += r.poll()
        }
        assertTrue("got $presses", presses in 10..17)
    }

    @Test
    fun `release stops everything`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        clock.advance(400)
        r.poll()
        r.stop()
        clock.advance(10_000)
        assertEquals(0, r.poll())
        assertFalse(r.isRepeating)
    }

    @Test
    fun `a long stall cannot dump a burst of presses`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start()
        clock.advance(300)
        assertEquals(1, r.poll())
        // App backgrounded for 30 s, then polled once.
        clock.advance(30_000)
        val due = r.poll()
        assertTrue("catch-up must be bounded, got $due", due <= TurboRepeater.MAX_CATCH_UP)
    }

    @Test
    fun `poll before start does nothing`() {
        val clock = FakeClock()
        val r = repeater(clock)
        clock.advance(5_000)
        assertEquals(0, r.poll())
    }

    @Test
    fun `held duration is reported`() {
        val clock = FakeClock()
        val r = repeater(clock)
        assertEquals(0L, r.heldForMs())
        r.start()
        clock.advance(250)
        assertEquals(250L, r.heldForMs())
    }

    @Test
    fun `defaults are humane`() {
        assertTrue("hold delay must not fire on a tap", TurboRepeater.DEFAULT_HOLD_DELAY_MS >= 250)
        assertTrue("interval must not flood the host", TurboRepeater.DEFAULT_INTERVAL_MS >= 40)
        assertTrue("but must be fast enough to be useful", TurboRepeater.DEFAULT_INTERVAL_MS <= 100)
    }

    @Test
    fun `restarting after release works`() {
        val clock = FakeClock()
        val r = repeater(clock)
        r.start(); clock.advance(400); r.poll(); r.stop()
        r.start()
        clock.advance(299)
        assertEquals(0, r.poll())
        clock.advance(1)
        assertEquals(1, r.poll())
    }
}
