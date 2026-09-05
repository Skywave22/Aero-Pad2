package com.aeropad.remote.ui.screens.mouse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import kotlin.math.abs

suspend fun PointerInputScope.detectTrackpadGestures(
    viewModel: RemoteControlViewModel,
    isEdgeScrollEnabled: Boolean,
    isThreeFingerEnabled: Boolean,
    isPalmRejectionEnabled: Boolean
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var pointerCount = 1
        var isEdgeScrolling = false
        
        // Palm rejection: if the first down has a huge area (not exposed via standard compose API usually, 
        // but we can reject touches near the very bottom/edges if it's not edge scrolling).
        // For simplicity, if start is in bottom 10%, ignore it if palm rejection is on.
        val size = size
        if (isPalmRejectionEnabled && down.position.y > size.height * 0.9f) {
            return@awaitEachGesture
        }

        // Edge scroll check (right 10% of trackpad)
        if (isEdgeScrollEnabled && down.position.x > size.width * 0.85f) {
            isEdgeScrolling = true
        }

        viewModel.onTrackpadGestureStart()

        var gestureType = 0 // 1 = move, 2 = scroll, 3 = three-finger swipe

        do {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val changes = event.changes
            val currentPointerCount = changes.count { it.pressed }
            
            if (currentPointerCount > pointerCount) {
                pointerCount = currentPointerCount
                viewModel.onTrackpadGestureStart() // reset smoothing on finger count change
            }
            
            if (pointerCount == 1) {
                if (isEdgeScrolling) {
                    val dy = changes.first().positionChange().y
                    if (dy != 0f) {
                        viewModel.onScrollDelta(dy)
                        changes.first().consume()
                    }
                } else {
                    val change = changes.first()
                    val dx = change.positionChange().x
                    val dy = change.positionChange().y
                    if (dx != 0f || dy != 0f) {
                        viewModel.onTrackpadDelta(dx, dy)
                        change.consume()
                    }
                }
            } else if (pointerCount == 2) {
                // Two finger scroll
                val pan = event.calculatePan()
                if (pan.y != 0f || pan.x != 0f) {
                    // Send vertical scroll
                    viewModel.onScrollDelta(pan.y)
                    // If we had horizontal scroll, we'd send it too, but we only have vertical in onScrollDelta
                    changes.forEach { it.consume() }
                }
            } else if (pointerCount == 3 && isThreeFingerEnabled) {
                // Three finger gesture (can send media keys or app switch, let's just trigger three-finger swipe on viewmodel)
                val pan = event.calculatePan()
                if (abs(pan.x) > 10f || abs(pan.y) > 10f) {
                    viewModel.onThreeFingerSwipe(pan.x, pan.y)
                    changes.forEach { it.consume() }
                }
            }
            
        } while (changes.any { it.pressed })
        
        viewModel.onThreeFingerSwipeEnd()
    }
}
