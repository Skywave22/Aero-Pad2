package com.aeropad.remote.domain

import kotlin.math.abs
import kotlin.math.exp

/**
 * SECTION: Air Mouse — pure motion→cursor math (unit-tested, no Android).
 *
 * Round 15 rewrite. Reported as "air mouse needs to be optimised". The old
 * pipeline had four real problems, all of which a user feels immediately:
 *
 *  1. **No time base.** Each gyro sample was scaled by a fixed gain, so the
 *     cursor speed depended on the sensor's sample rate. Report rates differ
 *     from 50 Hz to 400 Hz between phones, and Android may batch samples under
 *     load — the same flick could move 40 px or 400 px, and the cursor
 *     stuttered whenever the rate changed. Motion is now integrated against
 *     real elapsed time (rad/s × s), which makes it device-independent.
 *
 *  2. **Gyro bias / drift.** Cheap gyros report a small constant rotation while
 *     perfectly still (the "the cursor creeps across the screen by itself"
 *     complaint). A slow bias tracker learns that offset while the phone is
 *     still and subtracts it, so a stationery phone produces exactly zero.
 *
 *  3. **Fixed smoothing = lag.** A constant exponential filter on velocity adds
 *     a fixed delay, which reads as "the cursor trails my hand". The filter is
 *     now adaptive: heavy near zero (kills tremor) and almost none during fast
 *     motion (no lag where you can see it).
 *
 *  4. **Tremor gate too crude.** A hard cut-off at 0.015 rad/s made slow,
 *     deliberate aiming impossible — it snapped to zero and then jumped. A soft
 *     knee keeps slow motion usable while still rejecting hand shake.
 *
 * Also used by gamepad motion controls with different mapping targets.
 */
enum class AxisLock { BOTH, X_ONLY, Y_ONLY }

class AirMouseCore(
    var sensitivity: Int = 50,        // 0..100
    var smoothing: Int = 40,          // 0..100
    var axisLock: AxisLock = AxisLock.BOTH,
    /** rad/s below which motion is treated as hand tremor. */
    var jitterGate: Float = 0.015f,
    /** Everything below this is bias tracking, not movement. */
    var biasGate: Float = 0.06f
) {
    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f
    private var lastTimestampNs = 0L

    // Bias estimate (rad/s) — the phone's idea of "not moving".
    private var biasX = 0f
    private var biasY = 0f
    private var biasSamples = 0

    /** Reference sample interval. 10 ms = 100 Hz, the middle of the range. */
    private val referenceDt = 0.010f

    /**
     * Gain in pixels per second of angular travel at sensitivity 100.
     * Tuned against the old behaviour: a comfortable flick rotated ~20 deg/s
     * and moved ~35 px per sample at 100 Hz.
     */
    private fun gain(): Float {
        // Exponential curve: the bottom of the slider is fine aiming, the top
        // is a fast sweep across a 1080p screen. Linear felt "dead then wild".
        val t = (sensitivity.coerceIn(0, 100) / 100f)
        val curved = t * t * 1.6f + t * 0.4f   // 0 → 2.0, gentle in the middle
        return 900f * curved + 120f
    }

    /** Effective click/keystroke timestamp, for the time base. */
    fun step(gyroYaw: Float, gyroPitch: Float, timestampNs: Long = 0L): Pair<Int, Int> {
        var wx = if (gyroYaw.isNaN() || gyroYaw.isInfinite()) 0f else gyroYaw
        var wy = if (gyroPitch.isNaN() || gyroPitch.isInfinite()) 0f else gyroPitch

        // --- time base -------------------------------------------------
        val dt: Float = if (lastTimestampNs != 0L && timestampNs > lastTimestampNs) {
            // Clamped: a dropped frame must not become a cursor jump, and a
            // device whose clock is not monotonic must not produce nonsense.
            ((timestampNs - lastTimestampNs) / 1_000_000_000.0)
                .toFloat().coerceIn(0.002f, 0.05f)
        } else {
            referenceDt
        }
        if (timestampNs != 0L) lastTimestampNs = timestampNs

        // --- bias tracking (drift cancellation) ------------------------
        val magnitude = kotlin.math.hypot(wx, wy)
        if (magnitude < biasGate) {
            // Still: learn the offset. Two seconds of samples is enough, and
            // the rate is capped so a long still period cannot freeze the
            // estimate forever.
            biasSamples = (biasSamples + 1).coerceAtMost(200)
            val k = 1f / biasSamples
            biasX += (wx - biasX) * k
            biasY += (wy - biasY) * k
        }
        wx -= biasX
        wy -= biasY

        // --- soft tremor gate ------------------------------------------
        val gated = softGate(wx) to softGate(wy)
        wx = gated.first
        wy = gated.second

        // --- mapping & gain ---------------------------------------------
        // Angular velocity (rad/s) × gain (px/rad) is distance; the time base
        // is already in dt, so this is the same distance regardless of the
        // sensor's report rate.
        val pxPerRad = gain()
        val pxPerSecond = pxPerRad
        // Pointing right = negative yaw → +X.
        var dx = -wx * pxPerSecond * dt
        var dy = -wy * pxPerSecond * dt

        // --- adaptive smoothing ----------------------------------------
        // Smoothing slider 0..100 → base alpha. High alpha = more smoothing.
        val baseAlpha = (smoothing.coerceIn(0, 100) / 100f) * 0.88f
        // Speed-dependent relaxation: the faster the phone moves, the less the
        // filter is allowed to lag.
        val speed = abs(dx) + abs(dy)
        val relax = (speed / 12f).coerceIn(0f, 1f)
        val alpha = baseAlpha * (1f - 0.85f * relax)
        smoothX = smoothX * alpha + dx * (1f - alpha)
        smoothY = smoothY * alpha + dy * (1f - alpha)
        dx = smoothX
        dy = smoothY

        // --- axis lock --------------------------------------------------
        when (axisLock) {
            AxisLock.X_ONLY -> dy = 0f
            AxisLock.Y_ONLY -> dx = 0f
            AxisLock.BOTH -> Unit
        }

        // --- sub-pixel carry -------------------------------------------
        val fx = dx + fracX
        val fy = dy + fracY
        val ix = fx.toInt().coerceIn(-127, 127)
        val iy = fy.toInt().coerceIn(-127, 127)
        fracX = fx - ix
        fracY = fy - iy
        return ix to iy
    }

    /**
     * Soft knee: below the gate, attenuate smoothly instead of cutting to zero.
     * A hard cut made slow aiming feel like the cursor was stuck.
     */
    private fun softGate(w: Float): Float {
        val a = abs(w)
        if (a <= 0f) return 0f
        if (a >= jitterGate * 3f) return w
        val t = (a / (jitterGate * 3f)).coerceIn(0f, 1f)
        // quadratic ease-in keeps tremor down but never fully freezes movement
        val scale = t * t
        return w * scale
    }

    /** Calibration / recenter: zero accumulated state, keep the learned bias. */
    fun recenter() {
        smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
        lastTimestampNs = 0L
    }

    /** Forget the learned bias too (new posture, e.g. after recenter button). */
    fun resetBias() {
        biasX = 0f; biasY = 0f; biasSamples = 0
    }

    /** For diagnostics/tests. */
    val bias: Pair<Float, Float> get() = biasX to biasY
}
