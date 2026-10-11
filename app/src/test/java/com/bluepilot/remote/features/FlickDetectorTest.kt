package com.bluepilot.remote.features

import com.bluepilot.remote.domain.FlickDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** V2 MATRIX 3 — flick detection: spikes, directions, refractory. */
class FlickDetectorTest {

    @Test
    fun `detects all four directions above threshold`() {
        val d = FlickDetector(thresholdMs2 = 12f, refractoryMs = 0)
        assertEquals(FlickDetector.FlickDirection.UP, d.onSample(0f, 15f, 1)!!.direction)
        assertEquals(FlickDetector.FlickDirection.DOWN, d.onSample(0f, -15f, 2)!!.direction)
        assertEquals(FlickDetector.FlickDirection.RIGHT, d.onSample(15f, 0f, 3)!!.direction)
        assertEquals(FlickDetector.FlickDirection.LEFT, d.onSample(-15f, 0f, 4)!!.direction)
    }

    @Test
    fun `ignores walking-level noise below threshold`() {
        val d = FlickDetector(thresholdMs2 = 12f, refractoryMs = 0)
        assertNull(d.onSample(5f, 5f, 1))
        assertNull(d.onSample(-11.9f, 0f, 2))
        assertNull(d.onSample(Float.NaN, Float.NaN, 3))
    }

    @Test
    fun `refractory period blocks double-trigger from one flick`() {
        val d = FlickDetector(thresholdMs2 = 12f, refractoryMs = 400)
        assertNotNull(d.onSample(0f, 20f, 1000))
        assertNull(d.onSample(0f, 20f, 1100))     // same physical flick tail
        assertNull(d.onSample(0f, 20f, 1399))
        assertNotNull(d.onSample(0f, 20f, 1401))  // new flick after window
    }

    @Test
    fun `magnitude reported and threshold floor enforced`() {
        val d = FlickDetector(thresholdMs2 = 1f, refractoryMs = 0) // floor -> 5
        assertNull(d.onSample(0f, 4.9f, 1))
        assertEquals(6f, d.onSample(0f, 6f, 2)!!.magnitude, 1e-4f)
    }
}
