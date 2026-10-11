package com.bluepilot.remote.domain

import kotlin.math.abs

/**
 * SECTION: Air Mouse — pure motion→cursor math (unit-tested, no Android).
 *
 * Pipeline per gyro sample (angular velocity rad/s):
 *  1. axis mapping: phone pointed at screen like a laser pointer —
 *     yaw (rotation around device Y) → cursor X, pitch (around X) → cursor Y
 *  2. jitter gate: tiny angular velocities (hand tremor) are zeroed
 *  3. sensitivity gain
 *  4. exponential smoothing (stabilization)
 *  5. axis lock (X-only / Y-only / both)
 *  6. fractional remainder carry (slow precise motion never lost)
 *
 * Also used by gamepad motion controls with different mapping targets.
 */
enum class AxisLock { BOTH, X_ONLY, Y_ONLY }

class AirMouseCore(
    var sensitivity: Int = 50,        // 0..100
    var smoothing: Int = 40,          // 0..100
    var axisLock: AxisLock = AxisLock.BOTH,
    /** rad/s below which motion is treated as hand tremor. */
    var jitterGate: Float = 0.015f
) {
    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f

    /** Gain: 0 → 4 px per rad/s·frame … 100 → 40 px. */
    private fun gain(): Float = 4f + (sensitivity.coerceIn(0, 100) / 100f) * 36f

    /**
     * One gyro sample → (dx, dy) int pixels for the HID mouse report.
     * [gyroYaw] = angular velocity around device Y (rad/s),
     * [gyroPitch] = around device X. NaN-safe.
     */
    fun step(gyroYaw: Float, gyroPitch: Float): Pair<Int, Int> {
        var wx = if (gyroYaw.isNaN()) 0f else gyroYaw
        var wy = if (gyroPitch.isNaN()) 0f else gyroPitch

        // 2. jitter gate
        if (abs(wx) < jitterGate) wx = 0f
        if (abs(wy) < jitterGate) wy = 0f

        // 1+3. mapping & gain: pointing right = negative yaw → +X
        val g = gain()
        var dx = -wx * g
        var dy = -wy * g

        // 4. exponential smoothing (stabilization)
        val alpha = (smoothing.coerceIn(0, 100) / 100f) * 0.85f
        smoothX = smoothX * alpha + dx * (1f - alpha)
        smoothY = smoothY * alpha + dy * (1f - alpha)
        dx = smoothX; dy = smoothY

        // 5. axis lock
        when (axisLock) {
            AxisLock.X_ONLY -> dy = 0f
            AxisLock.Y_ONLY -> dx = 0f
            AxisLock.BOTH -> Unit
        }

        // 6. fractional carry
        val fx = dx + fracX
        val fy = dy + fracY
        val ix = fx.toInt().coerceIn(-127, 127)
        val iy = fy.toInt().coerceIn(-127, 127)
        fracX = fx - ix
        fracY = fy - iy
        return ix to iy
    }

    /** Calibration / recenter: zero all accumulated state. */
    fun recenter() {
        smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
    }
}
