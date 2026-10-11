package com.bluepilot.remote.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * SECTION 6 - 3D theme carousel effect: cards tilt/recede based on their
 * distance from the viewport center (Coverflow-style), computed from the
 * LazyList layout info. Pure graphicsLayer - GPU-only.
 *
 * SECTION 5 PERF FIX: scroll state is now read INSIDE the graphicsLayer
 * block (draw phase). The old signature took firstVisible/offset as plain
 * values, so every scrolled pixel recomposed every visible card
 * (~hundreds of recompositions per fling). Now scrolling only re-draws
 * the layer — zero recompositions.
 */
fun Modifier.carousel3D(
    itemIndex: Int,
    gridState: LazyGridState,
    itemHeightPx: Float,
    enabled: Boolean = true
): Modifier {
    if (!enabled || itemHeightPx <= 0f) return this
    return this.graphicsLayer {
        // State reads here happen in the draw phase — no recomposition.
        val firstVisible = gridState.firstVisibleItemIndex
        val offsetPx = gridState.firstVisibleItemScrollOffset
        // Position of this item's center relative to viewport top, in items.
        val posItems = (itemIndex - firstVisible).toFloat() - (offsetPx / itemHeightPx)
        // 0 at top; tilt increases with distance from ~1.5 items down.
        val dist = (posItems - 1.5f) / 3f
        cameraDistance = 10f * density
        rotationX = (-dist * 10f).coerceIn(-12f, 12f)
        val scale = 1f - (kotlin.math.abs(dist) * 0.06f).coerceIn(0f, 0.12f)
        scaleX = scale
        scaleY = scale
    }
}
