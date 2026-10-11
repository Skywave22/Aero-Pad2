package com.bluepilot.remote.gamepad

import com.bluepilot.remote.domain.TouchSlop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 MATRIX 1 finale — touch-slop pure math tests. */
class TouchSlopTest {

    @Test
    fun `inside bounds never outside`() {
        assertFalse(TouchSlop.isOutside(50f, 50f, 100f, 100f, 0f))
        assertFalse(TouchSlop.isOutside(0f, 0f, 100f, 100f, 0f))
        assertFalse(TouchSlop.isOutside(100f, 100f, 100f, 100f, 0f))
    }

    @Test
    fun `within slop margin stays pressed`() {
        // 10px slop: 8px past the right edge is still fine.
        assertFalse(TouchSlop.isOutside(108f, 50f, 100f, 100f, 10f))
        assertFalse(TouchSlop.isOutside(-8f, 50f, 100f, 100f, 10f))
        assertFalse(TouchSlop.isOutside(50f, 110f, 100f, 100f, 10f))
    }

    @Test
    fun `beyond slop margin cancels`() {
        assertTrue(TouchSlop.isOutside(111f, 50f, 100f, 100f, 10f))
        assertTrue(TouchSlop.isOutside(-11f, 50f, 100f, 100f, 10f))
        assertTrue(TouchSlop.isOutside(50f, -11f, 100f, 100f, 10f))
        assertTrue(TouchSlop.isOutside(50f, 111f, 100f, 100f, 10f))
    }

    @Test
    fun `nan and sentinel are safe`() {
        // NaN position → never cancel on garbage data.
        assertFalse(TouchSlop.isOutside(Float.NaN, 50f, 100f, 100f, 10f))
        assertFalse(TouchSlop.isOutside(50f, Float.NaN, 100f, 100f, 10f))
        // MAX_VALUE sentinel = slop disabled entirely.
        assertFalse(TouchSlop.isOutside(9999f, 9999f, 100f, 100f, Float.MAX_VALUE))
        // Negative slop = disabled (defensive).
        assertFalse(TouchSlop.isOutside(9999f, 9999f, 100f, 100f, -1f))
    }
}
