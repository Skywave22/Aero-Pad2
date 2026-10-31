package com.aeropad.remote.features

import com.aeropad.remote.domain.AirMouseCore
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15 — "air mouse needs to be optimised".
 *
 * The old core scaled every sample by a fixed gain with no time base, so the
 * cursor speed depended on the sensor's report rate and the same flick could
 * move the pointer 40 px or 400 px. It also had no bias tracking, so a still
 * phone drifted, and a hard tremor gate made slow aiming impossible.
 */
class AirMouseCoreTest {

    private fun travel(samples: Int, radPerSec: Float, dtNs: Long = 10_000_000L): Pair<Int, Int> {
        val core = AirMouseCore(sensitivity = 50, smoothing = 0, jitterGate = 0.0001f)
        var x = 0
        var y = 0
        var t = 1_000_000_000L
        repeat(samples) {
            val (dx, dy) = core.step(radPerSec, 0f, t)
            x += dx; y += dy
            t += dtNs
        }
        return x to y
    }

    @Test
    fun `the same motion over the same time moves the same distance at any sample rate`() {
        // Half a second of rotation at 100 Hz, then the same half second at
        // 200 Hz. Total travel must match: that is the whole point of the time
        // base, and it is what made the old version feel different per device.
        val slow = travel(samples = 50, radPerSec = 0.5f, dtNs = 10_000_000L)
        val fast = travel(samples = 100, radPerSec = 0.5f, dtNs = 5_000_000L)
        val tolerance = (abs(slow.first).coerceAtLeast(1) * 0.30f)
        assertTrue(
            "100 Hz moved ${slow.first} px, 200 Hz moved ${fast.first} px — must be within 30%",
            abs(slow.first - fast.first) <= tolerance
        )
    }

    @Test
    fun `a still phone with a biased gyro does not drift`() {
        val core = AirMouseCore(sensitivity = 60, smoothing = 40)
        var totalX = 0
        var totalY = 0
        var t = 1_000_000_000L
        // 4 seconds of a stationary phone whose gyro reports a constant offset.
        repeat(400) {
            val (dx, dy) = core.step(0.02f, -0.015f, t)
            totalX += dx; totalY += dy
            t += 10_000_000L
        }
        assertTrue("drifted $totalX px on X while still", abs(totalX) <= 3)
        assertTrue("drifted $totalY px on Y while still", abs(totalY) <= 3)
    }

    @Test
    fun `fast motion is not held back by smoothing`() {
        val smooth = AirMouseCore(sensitivity = 70, smoothing = 90)
        var t = 1_000_000_000L
        var moved = 0
        repeat(30) {
            val (dx, _) = smooth.step(1.2f, 0f, t)
            moved += abs(dx)
            t += 10_000_000L
        }
        assertTrue(
            "heavy smoothing must still let a fast flick move the cursor (moved $moved px)",
            moved > 20
        )
    }

    @Test
    fun `axis lock zeroes the other axis`() {
        val core = AirMouseCore(sensitivity = 80, smoothing = 0, jitterGate = 0.0001f)
        core.axisLock = com.aeropad.remote.domain.AxisLock.X_ONLY
        var t = 1_000_000_000L
        repeat(20) {
            val (_, dy) = core.step(0.4f, 0.4f, t)
            assertEquals(0, dy)
            t += 10_000_000L
        }
    }

    @Test
    fun `sensibility increases distance`() {
        val low = travel(samples = 20, radPerSec = 0.5f)
        val highCore = AirMouseCore(sensitivity = 100, smoothing = 0, jitterGate = 0.0001f)
        var high = 0
        var t = 1_000_000_000L
        repeat(20) {
            val (dx, _) = highCore.step(0.5f, 0f, t)
            high += dx
            t += 10_000_000L
        }
        assertTrue("sensitivity 100 must travel further than 50", abs(high) > abs(low.first))
    }
}
