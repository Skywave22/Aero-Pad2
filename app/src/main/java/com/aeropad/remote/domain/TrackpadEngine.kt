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
class TrackpadEngine(private val settings: () -> MouseSettings) {

    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f
    private var scrollAccum = 0f
    private var lastTime = 0L

    /** Reset smoothing state at gesture start (prevents cross-gesture bleed). */
    fun startGesture() {
        smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
        lastTime = System.currentTimeMillis()
    }

    /** Raw px delta → settings-adjusted int mouse delta (0,0 = don't send). */
    fun move(dxPx: Float, dyPx: Float): Pair<Int, Int> {
        val s = settings()
        
        val curTime = System.currentTimeMillis()
        val dt = if (lastTime > 0) (curTime - lastTime) else 1L
        lastTime = curTime
        
        // Trackpad profile overrides
        val profileConfig = PointerMath.applyProfile(s.profile.name, s.sensitivity, s.movementSmoothing, s.acceleration, s.curve.name)
        val pSens = profileConfig[0] as Int
        val pSmooth = profileConfig[1] as Int
        val pAcc = profileConfig[2] as Int
        val pCurveName = profileConfig[3] as String
        
        val g = PointerMath.gain(pSens, s.penMode)
        
        // acceleration
        val (ax, ay) = PointerMath.applyAcceleration(dxPx, dyPx, dt, pAcc, pCurveName)
        
        smoothX = PointerMath.smooth(smoothX, ax * g, pSmooth)
        smoothY = PointerMath.smooth(smoothY, ay * g, pSmooth)
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
