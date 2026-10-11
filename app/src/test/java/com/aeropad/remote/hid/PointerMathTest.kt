package com.aeropad.remote.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the trackpad/joystick math contract used by RemoteControlViewModel. */
class PointerMathTest {

    // ---------- gain ----------

    @Test
    fun `gain grows with sensitivity`() {
        assertTrue(PointerMath.gain(0, false) < PointerMath.gain(50, false))
        assertTrue(PointerMath.gain(50, false) < PointerMath.gain(100, false))
    }

    @Test
    fun `gain bounds are 0_3 to 2_5`() {
        assertEquals(0.3f, PointerMath.gain(0, false), 0.001f)
        assertEquals(2.5f, PointerMath.gain(100, false), 0.001f)
    }

    @Test
    fun `pen mode reduces gain to 40 percent`() {
        assertEquals(PointerMath.gain(60, false) * 0.4f, PointerMath.gain(60, true), 0.001f)
    }

    @Test
    fun `gain clamps out of range sensitivity`() {
        assertEquals(PointerMath.gain(100, false), PointerMath.gain(999, false), 0.001f)
        assertEquals(PointerMath.gain(0, false), PointerMath.gain(-5, false), 0.001f)
    }

    // ---------- smoothing ----------

    @Test
    fun `zero smoothing returns current value`() {
        assertEquals(10f, PointerMath.smooth(previous = 0f, current = 10f, smoothing = 0), 0.001f)
    }

    @Test
    fun `high smoothing biases toward previous value`() {
        val smoothed = PointerMath.smooth(previous = 0f, current = 10f, smoothing = 100)
        assertTrue("expected heavy damping, got $smoothed", smoothed < 2f)
    }

    // ---------- scroll ----------

    @Test
    fun `scroll accumulates below threshold`() {
        val (steps, remainder) = PointerMath.scrollSteps(5f, speed = 0, invert = false)
        assertEquals(0, steps)
        assertEquals(5f, remainder, 0.001f)
    }

    @Test
    fun `scroll emits steps above threshold and keeps remainder`() {
        // speed 0 => 60 px per step
        val (steps, remainder) = PointerMath.scrollSteps(130f, speed = 0, invert = false)
        assertEquals(2, steps)
        assertEquals(10f, remainder, 0.001f)
    }

    @Test
    fun `invert flips step sign`() {
        val (steps, _) = PointerMath.scrollSteps(130f, speed = 0, invert = true)
        assertEquals(-2, steps)
    }

    @Test
    fun `faster speed needs fewer pixels`() {
        val (slowSteps, _) = PointerMath.scrollSteps(50f, speed = 0, invert = false)   // 60px/step
        val (fastSteps, _) = PointerMath.scrollSteps(50f, speed = 100, invert = false) // 15px/step
        assertEquals(0, slowSteps)
        assertEquals(3, fastSteps)
    }

    // ---------- dead zone ----------

    @Test
    fun `inside dead zone returns zero`() {
        val (x, y) = PointerMath.applyDeadZone(0.05f, 0.05f, deadZonePercent = 20)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `outside dead zone rescales continuously`() {
        // Just past the zone → small but nonzero output.
        val (x1, _) = PointerMath.applyDeadZone(0.25f, 0f, deadZonePercent = 20)
        assertTrue(x1 > 0f && x1 < 0.15f)
        // Full deflection stays full.
        val (x2, _) = PointerMath.applyDeadZone(1f, 0f, deadZonePercent = 20)
        assertEquals(1f, x2, 0.001f)
    }

    @Test
    fun `zero input never divides by zero`() {
        val (x, y) = PointerMath.applyDeadZone(0f, 0f, deadZonePercent = 0)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    // ---------- joystick gain ----------

    @Test
    fun `joystick gain range is half to one and a half`() {
        assertEquals(0.5f, PointerMath.joystickGain(0), 0.001f)
        assertEquals(1.0f, PointerMath.joystickGain(50), 0.001f)
        assertEquals(1.5f, PointerMath.joystickGain(100), 0.001f)
    }

    // ---------- speed-adaptive acceleration ----------

    @Test
    fun `acceleration is neutral at rest and tops out at plus 45 percent`() {
        assertEquals(1.0f, PointerMath.acceleration(0f), 0.001f)
        assertEquals(1.45f, PointerMath.acceleration(500f), 0.001f)
        // Half the saturation travel → roughly half the boost.
        assertEquals(1.225f, PointerMath.acceleration(110f), 0.01f)
    }

    @Test
    fun `adaptive smoothing is full at rest and backs off on fast swipes`() {
        assertEquals(1.0f, PointerMath.adaptiveSmoothing(0f), 0.001f)
        assertEquals(0.4f, PointerMath.adaptiveSmoothing(500f), 0.001f)
        // Fast but not extreme → still meaningfully damped.
        val mid = PointerMath.adaptiveSmoothing(60f)
        assertTrue(mid > 0.7f && mid < 0.95f)
    }
}
