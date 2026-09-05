package com.aeropad.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow

/**
 * SECTION 1 - GLOBAL 3D FOUNDATION.
 *
 * Depth-layer system: consistent Z elevations app-wide.
 * Virtual light source: top-left (highlights up-left, shadows down-right
).
 */
object DepthLayer {
    val BACKGROUND = 0.dp
    val MID = 3.dp
    val FOREGROUND = 8.dp
    val FLOATING = 16.dp
}

/** 3D surface material styles (Section 1.2). */
enum class Material3D {
GLOSSY, MATTE, METALLIC, GLASS, FROSTED }

/** 3D quality modes (Section 9). */
enum class Quality3D { FULL, REDUCED, FLAT }

val LocalQuality3D = staticCompositionLocalOf { Quality3D.FULL }

/**
 * V2 PART B — live device tilt (-1..1 per axis) from the real gravity
 * sensor, low-pass smoothed at the app root. (0,0) = flat/no sensor/
 * reduce-motion — every consumer then renders exactly the pre-B look.
 * Non-static local: updates flow only into readers (draw-phase lighting
 * and parallax planes), not the whole tree.
 */
val LocalDeviceTilt = androidx.compose.runtime.compositionLocalOf { 0f to 0f }

/**
 * Core 3D surface: gradient fake-lighting + top-left highlight border
 * + soft shadow. Pure GPU (gradient + shadow), no per-frame allocs.
 */

fun Modifier.surface3D(
    base: Color,
    shape: Shape = RoundedCornerShape(32.dp),
    material: Material3D = Material3D.MATTE,
    elevation: Dp = 0.dp
): Modifier = this.background(base, shape).clip(shape)
