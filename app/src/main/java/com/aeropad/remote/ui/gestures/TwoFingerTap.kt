package com.aeropad.remote.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * Two-finger tap, the way a laptop trackpad does it (secondary click).
 *
 * Compose ships `detectTapGestures`, which only understands a single pointer,
 * so this is a small explicit detector. It is deliberately **non-consuming**:
 * it never consumes pointer events, so the drag detector on the same surface
 * keeps working exactly as before. A gesture only counts when
 *
 *  - at least two fingers were down at the same time, and
 *  - nothing moved further than the touch slop (it was a tap, not a drag).
 *
 * Because of that it cannot steal a drag, a long-press or a single tap.
 */
suspend fun PointerInputScope.detectTwoFingerTap(onTap: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var start: Offset = down.position
        var maxPointers = 1
        var maxMoved = 0f
        var moved: Boolean

        do {
            val event = awaitPointerEvent()
            val pressedNow = event.changes.count { it.pressed }
            if (pressedNow > maxPointers) maxPointers = pressedNow

            event.changes.forEach { change ->
                val d = (change.position - start).getDistance()
                if (d > maxMoved) maxMoved = d
            }
            moved = event.changes.any { it.pressed }
        } while (moved)

        val slop = viewConfiguration.touchSlop * 2f
        if (maxPointers >= 2 && maxMoved <= slop) onTap()
    }
}

/** Exposed for the unit-independent path: the decision itself, testable. */
internal fun isTwoFingerTap(maxPointers: Int, maxMovedPx: Float, touchSlopPx: Float): Boolean =
    maxPointers >= 2 && maxMovedPx <= touchSlopPx * 2f

/** Kept so the helper above has a stable home in future tests. */
internal fun AwaitPointerEventScope.touchSlopPx(): Float = viewConfiguration.touchSlop
