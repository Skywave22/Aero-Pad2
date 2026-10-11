package com.bluepilot.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SECTION 1 — Theme engine data model.
 *
 * An [AppThemeSpec] fully describes a visual theme: colors, background
 * treatment, widget/button styling and typography flavor. Every screen
 * reads these tokens through [LocalAppTheme], so switching the spec
 * re-skins the entire app instantly with zero restart.
 *
 * The 6 built-in themes encode the design mockups produced during the
 * redesign phase (Liquid Glass, Hawaii Harmony night/day, Cockpit HUD,
 * Dark Neon, OLED Black, Minimal Light).
 */
// V2 PART C compression — @Immutable: every field is a val of stable type;
// declaring it lets Compose skip recomposition wherever a theme is passed.
@androidx.compose.runtime.Immutable
data class AppThemeSpec(
    val id: String,
    val name: String,
    val isDark: Boolean,

    // ----- Core colors -----
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,

    // ----- Background treatment -----
    /** Gradient orbs painted behind content (empty = solid background). */
    val backgroundOrbs: List<ThemeOrb> = emptyList(),

    // ----- Component styling -----
    /** Corner radius (dp) for cards/buttons/keys. */
    val cornerRadius: Int = 16,
    /** Surface alpha: < 1f produces the frosted-glass look. */
    val surfaceAlpha: Float = 1f,
    /** Draw a thin luminous edge line on surfaces (liquid-glass edge light). */
    val edgeGlow: Boolean = false,
    /** Shadow/elevation strength (dp). */
    val elevation: Int = 2,
    /** Neon glow tint behind interactive elements (HUD/Neon themes). */
    val glowColor: Color? = null,

    // ----- Status colors -----
    val connected: Color = Color(0xFF2ECC71),
    val connecting: Color = Color(0xFFF1C40F),
    val error: Color = Color(0xFFE74C3C),

    // ----- Typography flavor -----
    /** Use monospace display font (Cockpit HUD theme). */
    val monoFont: Boolean = false
)

/** One blurred gradient orb of the themed background. */
@androidx.compose.runtime.Immutable
data class ThemeOrb(
    val color: Color,
    /** Center as fraction of screen (0..1). */
    val x: Float,
    val y: Float,
    /** Radius as fraction of the larger screen edge. */
    val radius: Float,
    val alpha: Float = 0.35f
)
