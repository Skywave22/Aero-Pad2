package com.bluepilot.remote.features

import com.bluepilot.remote.domain.GravitySteering
import com.bluepilot.remote.domain.ProximityTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 MATRIX 3 — gravity steering + proximity wave, pure math tests. */
class GravitySteeringTest {

    @Test
    fun `flat phone steers zero`() {
        // Gravity fully on z (phone flat on table / held flat).
        assertEquals(0f, GravitySteering.steer(0f, 9.81f, 45), 0.0001f)
    }

    @Test
    fun `dead zone swallows tiny tilt`() {
        // ~3° tilt with a 4° dead zone → still neutral.
        val gx = 9.81f * kotlin.math.sin(Math.toRadians(3.0)).toFloat()
        val gz = 9.81f * kotlin.math.cos(Math.toRadians(3.0)).toFloat()
        assertEquals(0f, GravitySteering.steer(gx, gz, 45), 0.0001f)
    }

    @Test
    fun `full lock at max angle and clamped beyond`() {
        val gx45 = 9.81f * kotlin.math.sin(Math.toRadians(45.0)).toFloat()
        val gz45 = 9.81f * kotlin.math.cos(Math.toRadians(45.0)).toFloat()
        assertEquals(1f, GravitySteering.steer(gx45, gz45, 45), 0.001f)
        // 60° tilt with a 45° max stays clamped at 1.
        val gx60 = 9.81f * kotlin.math.sin(Math.toRadians(60.0)).toFloat()
        val gz60 = 9.81f * kotlin.math.cos(Math.toRadians(60.0)).toFloat()
        assertEquals(1f, GravitySteering.steer(gx60, gz60, 45), 0.001f)
    }

    @Test
    fun `left tilt is negative and symmetric`() {
        val gx = 9.81f * kotlin.math.sin(Math.toRadians(30.0)).toFloat()
        val gz = 9.81f * kotlin.math.cos(Math.toRadians(30.0)).toFloat()
        val right = GravitySteering.steer(gx, gz, 45)
        val left = GravitySteering.steer(-gx, gz, 45)
        assertTrue(right > 0f)
        assertEquals(-right, left, 0.0001f)
    }

    @Test
    fun `nan and freefall are neutral`() {
        assertEquals(0f, GravitySteering.steer(Float.NaN, Float.NaN, 45), 0f)
        assertEquals(0f, GravitySteering.steer(0f, 0f, 45), 0f)
    }

    @Test
    fun `proximity fires once per wave with refractory`() {
        val t = ProximityTrigger(nearThresholdCm = 4f, refractoryMs = 500)
        assertFalse(t.onSample(10f, 0))       // far
        assertTrue(t.onSample(1f, 100))        // wave lands
        assertFalse(t.onSample(1f, 150))       // still hovering — no repeat
        assertFalse(t.onSample(10f, 200))      // hand lifted
        assertFalse(t.onSample(1f, 300))       // second wave inside refractory
        assertFalse(t.onSample(10f, 400))
        assertTrue(t.onSample(1f, 700))        // refractory passed → fires
    }

    @Test
    fun `proximity nan is far`() {
        val t = ProximityTrigger()
        assertFalse(t.onSample(Float.NaN, 0))
        assertTrue(t.onSample(0f, 600))
    }
}
