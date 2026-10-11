package com.bluepilot.remote.domain

import kotlin.math.abs

/**
 * SECTION 3A — Pure swipe-gesture classification (unit-tested).
 *
 * A gesture zone accumulates the drag vector of a finger; on release the
 * total (dx, dy) is classified into one of four directions, or NONE when
 * the travel is below [minDistancePx] (treated as a tap, not a swipe).
 */
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT, NONE }

object SwipeGestures {

    /** Default minimum travel (in px) for a drag to count as a swipe. */
    const val DEFAULT_MIN_DISTANCE_PX = 90f

    /**
     * Classify a total drag vector. Dominant axis wins; NaN-safe.
     * Screen coordinates: +y is down.
     */
    fun classify(dx: Float, dy: Float, minDistancePx: Float = DEFAULT_MIN_DISTANCE_PX): SwipeDirection {
        val sx = if (dx.isNaN()) 0f else dx
        val sy = if (dy.isNaN()) 0f else dy
        if (abs(sx) < minDistancePx && abs(sy) < minDistancePx) return SwipeDirection.NONE
        return if (abs(sx) >= abs(sy)) {
            if (sx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        } else {
            if (sy > 0) SwipeDirection.DOWN else SwipeDirection.UP
        }
    }
}
