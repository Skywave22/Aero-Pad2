package com.bluepilot.remote.domain

/**
 * V2 MATRIX 3 — accelerometer flick detection (pure, unit-tested).
 *
 * Detects sharp acceleration spikes on one axis (phone flick up / down /
 * left / right) with a refractory period so one physical flick can't
 * trigger twice. Feed real linear-acceleration samples; get a FlickEvent
 * or null. NaN-safe.
 */
class FlickDetector(
    /** m/s² spike threshold (12 ≈ deliberate flick; walking ≈ 3-6). */
    var thresholdMs2: Float = 12f,
    /** Minimum ms between flicks (debounce/refractory). */
    var refractoryMs: Long = 400
) {
    enum class FlickDirection { UP, DOWN, LEFT, RIGHT }
    data class FlickEvent(val direction: FlickDirection, val magnitude: Float, val atMs: Long)

    private var lastFlickAt = 0L

    /**
     * Process one real accelerometer sample (device axes: x right,
     * y up, z out of screen). Returns an event on flick, else null.
     */
    fun onSample(ax: Float, ay: Float, nowMs: Long): FlickEvent? {
        val x = if (ax.isNaN()) 0f else ax
        val y = if (ay.isNaN()) 0f else ay
        if (nowMs - lastFlickAt < refractoryMs) return null
        val t = thresholdMs2.coerceAtLeast(5f)
        val event = when {
            y >= t -> FlickEvent(FlickDirection.UP, y, nowMs)
            y <= -t -> FlickEvent(FlickDirection.DOWN, -y, nowMs)
            x >= t -> FlickEvent(FlickDirection.RIGHT, x, nowMs)
            x <= -t -> FlickEvent(FlickDirection.LEFT, -x, nowMs)
            else -> null
        }
        if (event != null) lastFlickAt = nowMs
        return event
    }

    fun reset() { lastFlickAt = 0L }
}