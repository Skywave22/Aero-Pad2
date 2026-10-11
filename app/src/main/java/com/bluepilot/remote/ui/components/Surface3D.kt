package com.bluepilot.remote.ui.components

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
    shape: Shape,
    material: Material3D = Material3D.MATTE,
    elevation: androidx.compose.ui.unit.Dp = DepthLayer.MID,
    quality: Quality3D = Quality3D.FULL
): Modifier {
    if (quality == Quality3D.FLAT) {
        return this.background(base, shape)
    }
    val lightAmt = when (material) {
        Material3D.GLOSSY -> 0.35f
        Material3D.METALLIC -> 0.30f
        Material3D.GLASS -> 0.22f
        Material3D.FROSTED -> 0.15f
        Material3D.MATTE -> 0.10f
    }
    // Virtual top-left light: lighter top fading to darker bottom.
    fun lighten(c: Color, f: Float) = Color(
        (c.red + (1 - c.red) * f).coerceIn(0f, 1f),
        (c.green + (1 - c.green) * f).coerceIn(0f, 1f),
        (c.blue + (1 - c.blue) * f).coerceIn(0f, 1f),
        c.alpha
    )
    fun darken(c: Color, f: Float) = Color(
        c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha
    )
    val grad = Brush.verticalGradient(
        listOf(lighten(base, lightAmt), base, darken(base, lightAmt * 0.7f))
    )
    val highlight = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = lightAmt * 0.9f), Color.Transparent)
    )
    val shadowMod = if (quality == Quality3D.FULL && elevation > 0.dp) {
        Modifier.shadow(elevation, shape, clip = false)
    } else Modifier
    return this
        .then(shadowMod)
        .background(grad, shape)
        .border(1.dp, highlight, shape)
}
