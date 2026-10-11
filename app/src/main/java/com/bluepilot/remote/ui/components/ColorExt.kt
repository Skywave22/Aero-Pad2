package com.bluepilot.remote.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** OPTIMIZATION: single ARGB-Long -> Compose Color conversion (was 9 copies). */
fun Long.toComposeColor(): Color = Color(this.toULong().toLong() and 0xFFFFFFFF)

/**
 * SECTION 3 AUDIT FIX — readable label color for ANY background.
 * White text on a light custom key color (or light theme primary) was
 * unreadable. Perceptual luminance decides black vs white content.
 */
fun Color.bestContentColor(): Color =
    if (luminance() > 0.5f) Color(0xFF15202B) else Color.White
