package com.aeropad.remote.domain

import com.aeropad.remote.hid.PointerMath
import com.aeropad.remote.model.MouseSettings

/**
 * OPTIMIZATION: single shared trackpad state machine.
 *
 * The smoothing + fractional-carry + scroll-accumulation pipeline was
 * copy-pasted in 3 ViewModels (RemoteControl, PcCombo, WidgetInteractor).
 * This class is now the one implementation (~20 lines replacing ~90).
 * AirMouseCore keeps its own variant intentionally: its input domain is
 * angular velocity, not pixel deltas.
 */
class TrackpadEngine(
    private val settings: () -> MouseSettings
) {
    /**
     * Rate-limits how often a report is actually sent. Nullable so tests that
     * want raw output can switch it off; production always has one.
     */
    internal var coalescer: PointerCoalescer? = PointerCoalescer()


    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f
    private var scrollAccum = 0f

    /** Reset smoothing state at gesture start (prevents cross-gesture bleed). */
    fun startGesture() {
        smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
        coalescer?.reset()
    }

    /**
     * Emit the tail of the movement that the coalescer is still holding.
     * Called at drag end so the cursor lands exactly under the finger instead
     * of stopping one window short.
     */
    fun endGesture(): Pair<Int, Int> = coalescer?.flush() ?: (0 to 0)

    /** Raw px delta → settings-adjusted int mouse delta (0,0 = don't send). */
    fun move(dxPx: Float, dyPx: Float): Pair<Int, Int> {
        val s = settings()
        // Speed-adaptive feel: fast swipes get extra gain and lighter
        // smoothing (instant response); slow glides stay buttery-smooth.
        val mag = kotlin.math.sqrt(dxPx * dxPx + dyPx * dyPx)
        val g = PointerMath.gain(s.sensitivity, s.penMode) * PointerMath.acceleration(mag)
        val smoothing = (s.movementSmoothing * PointerMath.adaptiveSmoothing(mag)).toInt()
        smoothX = PointerMath.smooth(smoothX, dxPx * g, smoothing)
        smoothY = PointerMath.smooth(smoothY, dyPx * g, smoothing)
        val fx = smoothX + fracX
        val fy = smoothY + fracY
        val ix = fx.toInt()
        val iy = fy.toInt()
        fracX = fx - ix
        fracY = fy - iy
        // Coalesce so a 240 Hz digitizer does not flood a Bluetooth link.
        return coalescer?.accumulate(ix, iy) ?: (ix to iy)
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
