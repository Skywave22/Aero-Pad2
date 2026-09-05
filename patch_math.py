import re

with open('app/src/main/java/com/aeropad/remote/hid/PointerMath.kt', 'r') as f:
    text = f.read()

math_new = """    /**
     * Compute acceleration multiplier based on velocity (px/ms) and curve.
     * acceleration: 0..100
     * curve: NONE, LINEAR, EXPONENTIAL
     */
    fun applyAcceleration(
        deltaX: Float,
        deltaY: Float,
        dtMillis: Long,
        acceleration: Int,
        curveName: String
    ): Pair<Float, Float> {
        if (curveName == "NONE" || acceleration == 0 || dtMillis <= 0L) {
            return deltaX to deltaY
        }
        val velocity = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY) / dtMillis
        // base threshold where acceleration starts kicking in (e.g. > 1 px/ms)
        if (velocity < 0.5f) return deltaX to deltaY
        
        val factor = acceleration / 100f
        val multiplier = when (curveName) {
            "EXPONENTIAL" -> 1f + (velocity * velocity * factor * 0.05f)
            "LINEAR" -> 1f + (velocity * factor * 0.5f)
            else -> 1f
        }
        // Cap the multiplier to avoid crazy jumps
        val cappedMultiplier = multiplier.coerceAtMost(5f)
        return (deltaX * cappedMultiplier) to (deltaY * cappedMultiplier)
    }

    /**
     * Helper for trackpad profiles, returns base sensitivity and curve overrides
     * Returns (sensitivity, smoothing, acceleration, curveName)
     */
    fun applyProfile(profileName: String, sens: Int, smooth: Int, acc: Int, curve: String): Array<Any> {
        return when (profileName) {
            "PRECISE" -> arrayOf(sens / 2, smooth.coerceAtLeast(50), 0, "NONE")
            "FAST" -> arrayOf((sens * 1.5f).toInt().coerceAtMost(100), smooth / 2, acc.coerceAtLeast(50), "EXPONENTIAL")
            "GAMING" -> arrayOf(sens, 0, 0, "NONE") // raw input
            "NATURAL" -> arrayOf(sens, smooth.coerceAtLeast(20), acc.coerceAtLeast(30), "LINEAR")
            else -> arrayOf(sens, smooth, acc, curve) // CUSTOM
        }
    }
"""

text = text.replace("    fun gain(sensitivity: Int, penMode: Boolean): Float {", math_new + "\n    fun gain(sensitivity: Int, penMode: Boolean): Float {")

with open('app/src/main/java/com/aeropad/remote/hid/PointerMath.kt', 'w') as f:
    f.write(text)
