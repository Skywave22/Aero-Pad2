package com.bluepilot.remote.domain

/**
 * V2 MATRIX 1 finale — touch-slop math (pure, unit-tested).
 *
 * A press is slop-cancelled when the pointer travels more than [slopPx]
 * beyond the control's bounds (0..w, 0..h). Inside bounds is never out,
 * regardless of slop. NaN-safe (NaN position = not outside — never
 * cancel on garbage data).
 */
object TouchSlop {
    fun isOutside(x: Float, y: Float, w: Float, h: Float, slopPx: Float): Boolean {
        if (x.isNaN() || y.isNaN()) return false
        if (slopPx < 0f || slopPx == Float.MAX_VALUE) return false
        return x < -slopPx || y < -slopPx || x > w + slopPx || y > h + slopPx
    }
}
