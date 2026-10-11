package com.aeropad.remote.hid

/**
 * Pure trackpad/scroll math — no Android imports, fully unit-tested.
 *
 * Pipeline for each drag event:
 *   raw delta → gain (sensitivity, pen mode) → smoothing (EMA) → int cast
 * Fractional remainders are carried by the caller so slow precise motion
 * is not swallowed by integer truncation.
 */
object PointerMath {

    /**
     * Pointer gain from a 0..100 sensitivity setting.
     * 0 → 0.3x (very slow), 50 → ~1.4x, 100 → 2.5x. Pen mode = 40% of normal.
     */
    fun gain(sensitivity: Int, penMode: Boolean): Float {
        val s = sensitivity.coerceIn(0, 100)
        val base = 0.3f + (s / 100f) * 2.2f
        return if (penMode) base * 0.4f else base
    }

    /**
     * Exponential moving average smoothing.
     * smoothing 0 → no smoothing (returns current), 100 → heavy (85% previous).
     */
    fun smooth(previous: Float, current: Float, smoothing: Int): Float {
        val alpha = (smoothing.coerceIn(0, 100) / 100f) * 0.85f
        return previous * alpha + current * (1f - alpha)
    }

    /**
     * Scroll wheel steps from an accumulated finger-travel in pixels.
     * Higher speed = fewer pixels per step. Returns (steps, remainder).
     */
    fun scrollSteps(accumulatedPx: Float, speed: Int, invert: Boolean): Pair<Int, Float> {
        val s = speed.coerceIn(0, 100)
        val pxPerStep = 60f - (s / 100f) * 45f   // 60px (slow) .. 15px (fast)
        val rawSteps = (accumulatedPx / pxPerStep).toInt()
        if (rawSteps == 0) return 0 to accumulatedPx
        val remainder = accumulatedPx - rawSteps * pxPerStep
        val steps = if (invert) -rawSteps else rawSteps
        return steps to remainder
    }

    /**
     * Apply a radial dead zone to a joystick axis pair.
     * Inside the zone → (0,0); outside → rescaled so output is continuous.
     */
    fun applyDeadZone(x: Float, y: Float, deadZonePercent: Int): Pair<Float, Float> {
        val dz = (deadZonePercent.coerceIn(0, 50)) / 100f
        val magnitude = kotlin.math.sqrt(x * x + y * y)
        if (magnitude < dz || magnitude == 0f) return 0f to 0f
        val scale = ((magnitude - dz) / (1f - dz)).coerceIn(0f, 1f) / magnitude
        return (x * scale).coerceIn(-1f, 1f) to (y * scale).coerceIn(-1f, 1f)
    }

    /** Joystick sensitivity curve: 0..100 → linear gain 0.5..1.5 applied pre-clamp. */
    fun joystickGain(sensitivity: Int): Float =
        0.5f + (sensitivity.coerceIn(0, 100) / 100f)

    /**
     * Pointer acceleration: slow, precise motion keeps the base gain
     * (1.0x), while fast swipes get up to +45% extra gain so the cursor
     * flies across the screen — the classic "smooth AND fast" feel.
     * [mag] is the raw finger-travel magnitude of the current event (px).
     */
    fun acceleration(mag: Float): Float {
        val t = (mag / 220f).coerceIn(0f, 1f)
        return 1f + t * 0.45f
    }

    /**
     * Velocity-aware smoothing multiplier: heavy damping at slow speed
     * (buttery gliding), much lighter damping on fast swipes so the
     * pointer reacts instantly (1.0 = user's setting, 0.4 = 60% less).
     */
    fun adaptiveSmoothing(mag: Float): Float {
        val t = (mag / 220f).coerceIn(0f, 1f)
        return 1f - t * 0.6f
    }
}
