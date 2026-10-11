package com.bluepilot.remote.domain

import com.bluepilot.remote.hid.PointerMath
import com.bluepilot.remote.model.MouseSettings

/**
 * OPTIMIZATION: single shared trackpad state machine.
 *
 * The smoothing + fractional-carry + scroll-accumulation pipeline was
 * copy-pasted in 3 ViewModels (RemoteControl, PcCombo, WidgetInteractor).
 * This class is now the one implementation (~20 lines replacing ~90).
 * AirMouseCore keeps its own variant intentionally: its input domain is
 * angular velocity, not pixel deltas.
 */
class TrackpadEngine(private val settings: () -> MouseSettings) {

    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f
    private var scrollAccum = 0f

    /** Reset smoothing state at gesture start (prevents cross-gesture bleed). */
    fun startGesture() {
        smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
    }

    /** Raw px delta → settings-adjusted int mouse delta (0,0 = don't send). */
    fun move(dxPx: Float, dyPx: Float): Pair<Int, Int> {
        val s = settings()
        val g = PointerMath.gain(s.sensitivity, s.penMode)
        smoothX = PointerMath.smooth(smoothX, dxPx * g, s.movementSmoothing)
        smoothY = PointerMath.smooth(smoothY, dyPx * g, s.movementSmoothing)
        val fx = smoothX + fracX
        val fy = smoothY + fracY
        val ix = fx.toInt()
        val iy = fy.toInt()
        fracX = fx - ix
        fracY = fy - iy
        return ix to iy
    }

    /** Accumulated scroll px → wheel steps (0 = don't send). */
    fun scroll(dyPx: Float): Int {
        val s = settings()
        scrollAccum += dyPx
        val (steps, remainder) = PointerMath.scrollSteps(scrollAccum, s.scrollSpeed, s.invertScroll)
        scrollAccum = remainder
        return steps
    }
}
