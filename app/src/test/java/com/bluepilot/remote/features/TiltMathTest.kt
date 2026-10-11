package com.bluepilot.remote.features

import com.bluepilot.remote.domain.TiltMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 PART B — tilt/lighting math (pure). */
class TiltMathTest {

    @Test
    fun `flat phone gives zero tilt and the legacy light center`() {
        val (tx, ty) = TiltMath.normalizedTilt(0f, 0f)
        assertEquals(0f, tx, 0f)
        assertEquals(0f, ty, 0f)
        val (lx, ly) = TiltMath.lightCenter(0f, 0f)
        // Must equal the pre-B hardcoded highlight (0.35, 0.30) exactly —
        // zero functionality change when sensors are off.
        assertEquals(TiltMath.CENTER_X, lx, 0f)
        assertEquals(TiltMath.CENTER_Y, ly, 0f)
    }

    @Test
    fun `tilt clamps at plus minus one`() {
        val (tx, _) = TiltMath.normalizedTilt(99f, 0f)
        assertEquals(1f, tx, 0f)
        val (nx, _) = TiltMath.normalizedTilt(-99f, 0f)
        assertEquals(-1f, nx, 0f)
    }

    @Test
    fun `light moves opposite the tilt like a fixed world light`() {
        // Tilt right (positive x) → highlight slides LEFT of center.
        val (rx, _) = TiltMath.lightCenter(1f, 0f)
        assertTrue(rx < TiltMath.CENTER_X)
        // Tilt left → highlight right of center.
        val (lx2, _) = TiltMath.lightCenter(-1f, 0f)
        assertTrue(lx2 > TiltMath.CENTER_X)
        // Symmetric travel.
        assertEquals(TiltMath.CENTER_X - rx, lx2 - TiltMath.CENTER_X, 0.0001f)
    }

    @Test
    fun `light center stays inside the control`() {
        listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { x ->
            listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { y ->
                val (lx, ly) = TiltMath.lightCenter(x, y)
                assertTrue(lx in 0.05f..0.95f)
                assertTrue(ly in 0.05f..0.95f)
            }
        }
    }

    @Test
    fun `nan inputs are safe everywhere`() {
        val (tx, ty) = TiltMath.normalizedTilt(Float.NaN, Float.NaN)
        assertEquals(0f, tx, 0f); assertEquals(0f, ty, 0f)
        val (lx, ly) = TiltMath.lightCenter(Float.NaN, Float.NaN)
        assertEquals(TiltMath.CENTER_X, lx, 0f)
        assertEquals(TiltMath.CENTER_Y, ly, 0f)
        assertEquals(0.5f, TiltMath.lowPass(0.5f, Float.NaN), 0f)   // holds
    }

    @Test
    fun `low pass converges and respects alpha bounds`() {
        var v = 0f
        repeat(60) { v = TiltMath.lowPass(v, 1f) }
        assertTrue("60 samples at 0.15 alpha should converge near 1, got $v", v > 0.99f)
        // alpha 0 = frozen, alpha 1 = instant.
        assertEquals(0.3f, TiltMath.lowPass(0.3f, 9f, 0f), 0f)
        assertEquals(9f, TiltMath.lowPass(0.3f, 9f, 1f), 0f)
    }
}
