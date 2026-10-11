package com.bluepilot.remote.domain

import kotlin.math.abs
import kotlin.math.atan2

/**
 * V2 MATRIX 3 — gravity-vector steering (pure, unit-tested).
 *
 * Phone held flat like a steering wheel: the real gravity vector's
 * component along the device's long axis (device-x in landscape) tells
 * us the wheel angle. Flat = 0, tilt right edge down = steer right.
 * Output is -1..1 for the left stick X axis. NaN-safe, clamped.
 */
object GravitySteering {

    /**
     * @param gx gravity along device x (m/s²) — grows as the wheel tilts.
     * @param gz gravity along device z (m/s²) — ~9.81 when flat.
     * @param maxAngleDeg wheel angle that maps to full lock (15–75).
     * @param deadZoneDeg small tilt ignored so a resting phone reads 0.
     */
    fun steer(gx: Float, gz: Float, maxAngleDeg: Int, deadZoneDeg: Int = 4): Float {
        val x = if (gx.isNaN()) 0f else gx
        val z = if (gz.isNaN()) 0f else gz
        // Freefall / bad sensor guard: no usable gravity → neutral.
        if (abs(x) < 0.05f && abs(z) < 0.05f) return 0f
        val angle = Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
        val max = maxAngleDeg.coerceIn(15, 75).toFloat()
        val dz = deadZoneDeg.coerceIn(0, 14).toFloat()
        val mag = abs(angle)
        if (mag <= dz) return 0f
        val scaled = (mag - dz) / (max - dz)
        val out = if (angle > 0) scaled else -scaled
        return out.coerceIn(-1f, 1f)
    }
}

/**
 * V2 MATRIX 3 — proximity "wave" trigger (pure, unit-tested).
 *
 * Fires once per far→near transition (hand waved over the sensor),
 * with a refractory period so hovering can't machine-gun the button.
 * Feed real proximity distances; get true exactly when a wave lands.
 */
class ProximityTrigger(
    /** Distances below this (cm) count as "near". */
    var nearThresholdCm: Float = 4f,
    /** Minimum ms between fires. */
    var refractoryMs: Long = 500
) {
    private var wasNear = false
    private var lastFireAt = Long.MIN_VALUE / 2  // first wave always allowed

    fun onSample(distanceCm: Float, nowMs: Long): Boolean {
        val d = if (distanceCm.isNaN()) Float.MAX_VALUE else distanceCm
        val near = d < nearThresholdCm.coerceAtLeast(0.5f)
        val fired = near && !wasNear && (nowMs - lastFireAt >= refractoryMs)
        if (fired) lastFireAt = nowMs
        wasNear = near
        return fired
    }

    fun reset() { wasNear = false; lastFireAt = Long.MIN_VALUE / 2 }
}
