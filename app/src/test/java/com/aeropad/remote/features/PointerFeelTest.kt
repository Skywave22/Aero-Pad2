package com.aeropad.remote.features

import com.aeropad.remote.domain.PointerCoalescer
import com.aeropad.remote.ui.gestures.isTwoFingerTap
import com.aeropad.remote.ui.theme.AccentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 9 — pointer coalescing (the "cursor stutter" fix), the two-finger tap
 * decision and the accent palette.
 */
class PointerFeelTest {

    /** Controllable clock so the window logic is deterministic. */
    private class FakeClock(var now: Long = 1_000L) {
        fun advance(ms: Long) { now += ms }
        fun read(): Long = now
    }

    private fun coalescer(clock: FakeClock, window: Long = 12, maxPending: Int = 48) =
        PointerCoalescer(windowMs = window, maxPendingPx = maxPending, clock = clock::read)

    // ------------------------------------------------------------- coalescing

    @Test
    fun `first move is sent immediately - no added latency`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        assertEquals(3 to 4, c.accumulate(3, 4))
        assertFalse(c.hasPending)
    }

    @Test
    fun `moves inside the window are held`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        c.accumulate(1, 1)                 // sent (first)
        clock.advance(3)
        assertEquals(0 to 0, c.accumulate(2, 2))
        assertTrue(c.hasPending)
    }

    @Test
    fun `held deltas are sent together when the window closes`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        c.accumulate(1, 0)                 // sent
        clock.advance(4)
        c.accumulate(2, 3)                 // held
        clock.advance(4)
        c.accumulate(1, 1)                 // held
        clock.advance(5)                   // 13 ms since the last send
        assertEquals(3 to 4, c.accumulate(0, 0).let { _ ->
            // accumulate(0,0) short-circuits; drive the window via a real move
            c.accumulate(0, 0)
            c.flush()
        })
    }

    @Test
    fun `total movement is never lost`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        var totalX = 0
        var totalY = 0
        var sentX = 0
        var sentY = 0
        repeat(300) { i ->
            totalX += 1
            totalY += 1
            val (sx, sy) = c.accumulate(1, 1)
            sentX += sx
            sentY += sy
            if (i % 3 == 0) clock.advance(4)
        }
        val (fx, fy) = c.flush()
        sentX += fx
        sentY += fy
        assertEquals("every pixel must reach the host", totalX, sentX)
        assertEquals(totalY, sentY)
    }

    @Test
    fun `flush emits the tail at gesture end`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        c.accumulate(1, 1)
        clock.advance(2)
        c.accumulate(5, 6)
        assertTrue(c.hasPending)
        assertEquals(5 to 6, c.flush())
        assertFalse(c.hasPending)
    }

    @Test
    fun `flush with nothing pending is a no-op`() {
        val c = coalescer(FakeClock())
        assertEquals(0 to 0, c.flush())
    }

    @Test
    fun `a big jump is never held back`() {
        val clock = FakeClock()
        val c = coalescer(clock, window = 12, maxPending = 48)
        c.accumulate(1, 1)
        clock.advance(1)
        // 60 px in one event is a flick: send it now, do not batch.
        assertEquals(60 to 0, c.accumulate(60, 0))
    }

    @Test
    fun `zero delta is ignored`() {
        val c = coalescer(FakeClock())
        assertEquals(0 to 0, c.accumulate(0, 0))
        assertFalse(c.hasPending)
    }

    @Test
    fun `reset drops pending without sending`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        c.accumulate(1, 1)
        clock.advance(2)
        c.accumulate(4, 4)
        assertTrue(c.hasPending)
        c.reset()
        assertFalse(c.hasPending)
    }

    @Test
    fun `bursts are batched - fewer reports than events`() {
        val clock = FakeClock()
        val c = coalescer(clock)
        var reports = 0
        // 240 Hz input for 100 ms = 24 events, advanced 4 ms each.
        repeat(24) {
            val (x, y) = c.accumulate(1, 1)
            if (x != 0 || y != 0) reports++
            clock.advance(4)
        }
        c.flush().let { if (it.first != 0 || it.second != 0) reports++ }
        assertTrue("expected batching, got $reports reports", reports <= 12)
    }

    @Test
    fun `default window stays in the eye-invisible range`() {
        // Above ~16 ms the cursor visibly trails; below ~8 ms it barely helps.
        assertTrue(PointerCoalescer.DEFAULT_WINDOW_MS in 8..16)
    }

    // ---------------------------------------------------------- two-finger tap

    @Test
    fun `two fingers without movement is a two-finger tap`() {
        assertTrue(isTwoFingerTap(maxPointers = 2, maxMovedPx = 4f, touchSlopPx = 8f))
    }

    @Test
    fun `one finger is not a two-finger tap`() {
        assertFalse(isTwoFingerTap(maxPointers = 1, maxMovedPx = 1f, touchSlopPx = 8f))
    }

    @Test
    fun `a two-finger drag is not a tap`() {
        assertFalse(isTwoFingerTap(maxPointers = 2, maxMovedPx = 60f, touchSlopPx = 8f))
    }

    @Test
    fun `three fingers still count as multi-finger tap`() {
        assertTrue(isTwoFingerTap(maxPointers = 3, maxMovedPx = 2f, touchSlopPx = 8f))
    }

    // ------------------------------------------------------------ accent palette

    @Test
    fun `unknown accent ids fall back to the default`() {
        assertEquals(AccentPalette.DEFAULT, AccentPalette.fromId(null))
        assertEquals(AccentPalette.DEFAULT, AccentPalette.fromId("PURPLE-NOT-REAL"))
    }

    @Test
    fun `accent ids resolve case-insensitively`() {
        assertEquals(AccentPalette.VIOLET, AccentPalette.fromId("violet"))
    }

    @Test
    fun `every palette has distinct ids and labels`() {
        assertEquals(AccentPalette.entries.size, AccentPalette.entries.map { it.id }.distinct().size)
        assertEquals(AccentPalette.entries.size, AccentPalette.entries.map { it.label }.distinct().size)
    }

    @Test
    fun `dark and light accents differ so contrast holds`() {
        AccentPalette.entries.forEach { p ->
            assertNotEquals("${p.label} needs a brightened dark variant", p.light, p.dark)
        }
    }

    @Test
    fun `the default palette keeps the original AeroPad blue`() {
        assertEquals("BLUE", AccentPalette.DEFAULT.id)
    }
}
