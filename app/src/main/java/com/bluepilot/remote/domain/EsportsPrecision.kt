package com.bluepilot.remote.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V2 MATRIX 6 - Esports precision math (pure, unit-tested).
 * All functions NaN-safe and clamped; zero allocations in hot paths
 * beyond the returned Pair.
 */
object EsportsPrecision {

    /** Snap stick angle to N evenly spaced notches (8/16-way etc).
     *  notches<=0 = analog passthrough. Magnitude preserved. */
    fun snapAngles(x: Float, y: Float, notches: Int): Pair<Float, Float> {
        val sx = if (x.isNaN()) 0f else x.coerceIn(-1f, 1f)
        val sy = if (y.isNaN()) 0f else y.coerceIn(-1f, 1f)
        if (notches <= 0) return sx to sy
        val mag = sqrt(sx * sx + sy * sy)
        if (mag < 1e-6f) return 0f to 0f
        val step = (2.0 * Math.PI / notches)
        val angle = atan2(sy.toDouble(), sx.toDouble())
        val snapped = Math.round(angle / step) * step
        return (cos(snapped) * mag).toFloat().coerceIn(-1f, 1f) to
            (sin(snapped) * mag).toFloat().coerceIn(-1f, 1f)
    }

    /** Aim-smoothing zone: inside centerRadius sensitivity drops to
     *  innerPercent (10 = sniper), outside = 100%. Smooth blend at edge. */
    fun aimZone(v: Float, mag: Float, centerRadius: Float, innerPercent: Int): Float {
        val r = centerRadius.coerceIn(0.05f, 0.9f)
        val inner = innerPercent.coerceIn(1, 100) / 100f
        if (mag >= r) return v
        // Linear blend from inner% at center to 100% at zone edge.
        val t = (mag / r).coerceIn(0f, 1f)
        return v * (inner + (1f - inner) * t)
    }

    /** Stick-as-dpad: analog vector -> hat value (8-way digital), with
     *  activation threshold. Below threshold = neutral(8). */
    fun stickToHat(x: Float, y: Float, threshold: Float = 0.5f): Int {
        val sx = if (x.isNaN()) 0f else x
        val sy = if (y.isNaN()) 0f else y
        if (sqrt(sx * sx + sy * sy) < threshold.coerceIn(0.1f, 0.9f)) return 8
        val angle = Math.toDegrees(atan2(sy.toDouble(), sx.toDouble()))
        val sector = (((angle + 360 + 22.5) % 360) / 45.0).toInt() % 8
        return when (sector) {
            0 -> 2; 1 -> 3; 2 -> 4; 3 -> 5
            4 -> 6; 5 -> 7; 6 -> 0; else -> 1
        }
    }

    /** Trigger overlap curve: one pull value (0..1) drives two buttons.
     *  L1 fires at l1At, L2 at l2At (l1At < l2At). Returns (l1, l2). */
    fun triggerOverlap(pull: Float, l1At: Float, l2At: Float): Pair<Boolean, Boolean> {
        val p = if (pull.isNaN()) 0f else pull.coerceIn(0f, 1f)
        val a = l1At.coerceIn(0.05f, 0.95f)
        val b = l2At.coerceIn(a + 0.05f, 1f)
        return (p >= a) to (p >= b)
    }

    /** Input-history entry for the frame-data display (real timestamps). */
    data class InputEvent(val label: String, val atMs: Long)

    /** Rolling input history: newest first, capped, real timestamps. */
    fun pushHistory(history: List<InputEvent>, label: String, atMs: Long, cap: Int = 12): List<InputEvent> =
        (listOf(InputEvent(label, atMs)) + history).take(cap.coerceIn(1, 50))
}
