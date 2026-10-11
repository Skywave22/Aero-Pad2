package com.bluepilot.remote.domain

/**
 * V2 PART B — deep-3D tilt math (pure, unit-tested).
 *
 * Converts the real gravity vector into:
 *  1. a normalized device tilt (-1..1 per axis) for parallax planes, and
 *  2. a light-source position (fractions of the control size) so button
 *     highlights behave like a fixed world light: tilt the phone right,
 *     the highlight slides left — the same physics your eyes expect from
 *     a glossy real-world object.
 *
 * NaN-safe, clamped, and cheap (a few multiplies — safe in draw phase).
 */
object TiltMath {

    /** Highlight position when the phone lies flat (matches the previous
     *  hardcoded look, so FLAT/reduce-motion renders are unchanged). */
    const val CENTER_X = 0.35f
    const val CENTER_Y = 0.30f

    /** How far (fraction) the highlight may wander from center. */
    const val LIGHT_TRAVEL = 0.22f

    /** Tilt that equals full parallax/light deflection (m/s² along axis). */
    private const val FULL_TILT_MS2 = 6f

    /** Gravity x/y → normalized tilt (-1..1 each axis). NaN → 0. */
    fun normalizedTilt(gx: Float, gy: Float): Pair<Float, Float> {
        val x = if (gx.isNaN()) 0f else (gx / FULL_TILT_MS2).coerceIn(-1f, 1f)
        val y = if (gy.isNaN()) 0f else (gy / FULL_TILT_MS2).coerceIn(-1f, 1f)
        return x to y
    }

    /**
     * Light-source position (fraction coords) for a tilt. The light is
     * fixed in world space: positive tiltX (right edge down) moves the
     * highlight LEFT; positive tiltY (top toward you) moves it UP.
     */
    fun lightCenter(tiltX: Float, tiltY: Float): Pair<Float, Float> {
        val tx = if (tiltX.isNaN()) 0f else tiltX.coerceIn(-1f, 1f)
        val ty = if (tiltY.isNaN()) 0f else tiltY.coerceIn(-1f, 1f)
        val cx = (CENTER_X - tx * LIGHT_TRAVEL).coerceIn(0.05f, 0.95f)
        val cy = (CENTER_Y - ty * LIGHT_TRAVEL).coerceIn(0.05f, 0.95f)
        return cx to cy
    }

    /**
     * Low-pass smoothing for sensor jitter (alpha 0..1; 0.15 ≈ silky).
     * Returns the new smoothed value.
     */
    fun lowPass(previous: Float, sample: Float, alpha: Float = 0.15f): Float {
        val s = if (sample.isNaN()) previous else sample
        val a = alpha.coerceIn(0f, 1f)
        return previous + a * (s - previous)
    }
}
