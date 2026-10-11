package com.bluepilot.remote.ui.theme

import androidx.compose.ui.unit.dp

/**
 * SECTION 4 — single spacing/sizing scale for the whole app.
 *
 * Rule: screens use SCREEN_H for horizontal padding, cards use CARD for
 * inner padding, vertical rhythm uses GAP_S/M/L. Touch targets never go
 * below TOUCH_TARGET (Material accessibility floor), even when the
 * visual glyph inside is smaller.
 */
object Dimens {
    // Spacing scale (4-point system)
    val GAP_XS = 4.dp
    val GAP_S = 8.dp
    val GAP_M = 12.dp
    val GAP_L = 16.dp
    val GAP_XL = 24.dp

    /** Standard horizontal screen padding. */
    val SCREEN_H = 16.dp

    /** Standard inner card padding. */
    val CARD = 16.dp

    /** Minimum touch target (Material accessibility guideline). */
    val TOUCH_TARGET = 48.dp

    /** Elevation steps (matches DepthLayer semantics). */
    val ELEV_CARD = 3.dp
    val ELEV_FLOATING = 8.dp
}
