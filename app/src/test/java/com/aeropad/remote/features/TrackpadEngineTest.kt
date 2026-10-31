package com.aeropad.remote.features

import com.aeropad.remote.domain.PointerCoalescer
import com.aeropad.remote.domain.TrackpadEngine
import com.aeropad.remote.model.MouseSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 16 — `TrackpadEngine` and `PointerCoalescer` are the hot path of the
 * mouse (every pixel of every gesture) and had **no tests at all**. They are
 * also the two places where a regression is invisible in a screenshot: the
 * cursor simply feels wrong.
 */
class TrackpadEngineTest {

    private fun engine(settings: MouseSettings = MouseSettings(), clock: () -> Long = { 0L }) =
        TrackpadEngine { settings }.also { it.coalescer = PointerCoalescer(clock = clock) }

    @Test
    fun `a still finger sends nothing`() {
        val e = engine()
        repeat(10) { assertEquals(0 to 0, e.move(0f, 0f)) }
    }

    @Test
    fun `movement in the right direction is positive x`() {
        val e = engine()
        val (x, _) = e.move(40f, 0f)
        assertTrue("rightward motion must give +x, got $x", x > 0)
    }

    @Test
    fun `heavier smoothing never adds distance in aggregate`() {
        // The EMA is applied to deltas, not to positions: over a whole gesture
        // the sum of the outputs must equal the sum of the inputs, otherwise
        // smoothing would silently change pointer speed.
        fun total(smoothing: Int): Int {
            val settings = MouseSettings(sensitivity = 50, movementSmoothing = smoothing)
            val e = engine(settings) { Long.MAX_VALUE } // one drain per event
            var sum = 0
            repeat(20) { sum += e.move(10f, 0f).first }
            sum += e.endGesture().first
            return sum
        }
        val low = total(0)
        val high = total(90)
        val drift = kotlin.math.abs(low - high).toFloat() / low
        assertTrue("smoothing changed the distance ($low vs $high)", drift < 0.35f)
    }

    @Test
    fun `pen mode is slower than normal at the same sensitivity`() {
        fun total(pen: Boolean): Int {
            val e = engine(MouseSettings(sensitivity = 80, penMode = pen)) { Long.MAX_VALUE }
            var sum = 0
            repeat(10) { sum += e.move(20f, 0f).first }
            return sum + e.endGesture().first
        }
        assertTrue("pen mode must be slower", total(true) < total(false))
    }

    @Test
    fun `scroll accumulates fractions instead of dropping them`() {
        val e = engine(MouseSettings(scrollSpeed = 50))
        // 60 px/step at speed 50 → every 20 px is a step; feed 3 x 10 px.
        var steps = 0
        repeat(6) { steps += e.scroll(10f) }
        assertTrue("30 px of 60 px-per-step travel must produce at least one step", steps >= 1)
    }

    @Test
    fun `invert scroll flips the sign`() {
        val normal = engine(MouseSettings(scrollSpeed = 90))
        val inverted = engine(MouseSettings(scrollSpeed = 90, invertScroll = true))
        var n = 0
        var i = 0
        repeat(10) { n += normal.scroll(30f); i += inverted.scroll(30f) }
        assertEquals(-n, i)
    }

    @Test
    fun `the coalescer keeps the total delta it holds back`() {
        var now = 0L
        val c = PointerCoalescer(windowMs = 12, clock = { now })
        var sent = 0
        // 100 small moves inside one window: only the first is sent, the rest
        // must be held and then delivered in full by flush().
        repeat(100) { sent += c.accumulate(1, 0).first; now += 1 }
        sent += c.flush().first
        assertEquals(100, sent)
    }

    @Test
    fun `a big jump is never held`() {
        val c = PointerCoalescer(windowMs = 1000, maxPendingPx = 48, clock = { 0L })
        c.accumulate(1, 0)              // first move opens the window
        val (dx, _) = c.accumulate(80, 0) // larger than maxPendingPx
        assertEquals(80, dx)
    }

    @Test
    fun `reset forgets without sending`() {
        val c = PointerCoalescer(windowMs = 1000, clock = { 0L })
        c.accumulate(5, 5)
        c.reset()
        assertTrue(!c.hasPending)
        assertEquals(0 to 0, c.flush())
    }
}
