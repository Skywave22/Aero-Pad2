package com.bluepilot.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The built-in theme catalog — each one is a direct translation of a
 * design mockup from the redesign phase (designs/ folder).
 */
object BuiltInThemes {

    /** Classic BluePilot look (the pre-update default). */
    /** STITCH REDESIGN — "AeroPad Liquid Glass": the new default look.
     *  Deep navy ink #0B1220, teal/cyan accent, frosted glass surfaces,
     *  soft teal+purple orbs — matches the generated design system. */
    val AERO_GLASS = AppThemeSpec(
        id = "aero_glass",
        name = "Aero Glass",
        isDark = true,
        primary = Color(0xFF22D3EE), onPrimary = Color(0xFF06222B),
        secondary = Color(0xFF7C6CF5),
        background = Color(0xFF0B1220), onBackground = Color(0xFFE8F1F8),
        surface = Color(0xFF131C2E), onSurface = Color(0xFFE8F1F8),
        surfaceVariant = Color(0xFF1B2740), onSurfaceVariant = Color(0xFFA6B6CE),
        outline = Color(0xFF2E4060),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF22D3EE), 0.85f, 0.10f, 0.38f, 0.16f),
            ThemeOrb(Color(0xFF7C6CF5), 0.12f, 0.80f, 0.42f, 0.18f),
            ThemeOrb(Color(0xFF16A085), 0.75f, 0.90f, 0.30f, 0.12f)
        ),
        cornerRadius = 22, surfaceAlpha = 0.72f, edgeGlow = true, elevation = 0,
        glowColor = Color(0xFF22D3EE)
    )

    val PILOT_DARK = AppThemeSpec(
        id = "pilot_dark",
        name = "Pilot Dark",
        isDark = true,
        primary = Color(0xFF2F6BFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF0B0F1A), onBackground = Color(0xFFE6EAF5),
        surface = Color(0xFF121828), onSurface = Color(0xFFE6EAF5),
        surfaceVariant = Color(0xFF1A2238), onSurfaceVariant = Color(0xFFAAB4CE),
        outline = Color(0xFF2C3A5C),
        cornerRadius = 16, elevation = 2
    )

    /** designs/glass-*: frosted panels over aurora orbs, luminous edges. */
    val LIQUID_GLASS = AppThemeSpec(
        id = "liquid_glass",
        name = "Liquid Glass",
        isDark = true,
        primary = Color(0xFF4D8DFF), onPrimary = Color.White,
        secondary = Color(0xFF7AE7FF),
        background = Color(0xFF0A0E1C), onBackground = Color(0xFFEDF2FF),
        surface = Color(0xFF18203A), onSurface = Color(0xFFEDF2FF),
        surfaceVariant = Color(0xFF222C4E), onSurfaceVariant = Color(0xFFB6C2E4),
        outline = Color(0xFF3A4A7E),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF3B5BFF), 0.15f, 0.10f, 0.45f, 0.40f),
            ThemeOrb(Color(0xFF8A4DFF), 0.90f, 0.35f, 0.40f, 0.30f),
            ThemeOrb(Color(0xFF29C5FF), 0.30f, 0.90f, 0.50f, 0.28f)
        ),
        cornerRadius = 24, surfaceAlpha = 0.72f, edgeGlow = true, elevation = 0
    )

    /** designs/hi-* dark: Tropical Night — moonlit lagoon + tiki lantern glow. */
    val HAWAII_NIGHT = AppThemeSpec(
        id = "hawaii_night",
        name = "Hawaii Night",
        isDark = true,
        // SECTION 4 accessibility fix: white on bright teal was 2.5:1 —
        // dark ink on teal reads at ~7:1 (standard for cyan primaries).
        primary = Color(0xFF00B4C6), onPrimary = Color(0xFF042A30),
        secondary = Color(0xFFFF6B5E),
        background = Color(0xFF06222B), onBackground = Color(0xFFE2F6F8),
        surface = Color(0xFF0C3340), onSurface = Color(0xFFE2F6F8),
        surfaceVariant = Color(0xFF14424F), onSurfaceVariant = Color(0xFF9CCBD4),
        outline = Color(0xFF20596B),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF00B4C6), 0.20f, 0.15f, 0.45f, 0.35f),
            ThemeOrb(Color(0xFFFF6B5E), 0.88f, 0.80f, 0.35f, 0.30f),
            ThemeOrb(Color(0xFF3EB489), 0.75f, 0.20f, 0.30f, 0.22f)
        ),
        cornerRadius = 26, surfaceAlpha = 0.78f, edgeGlow = true, elevation = 0,
        connected = Color(0xFF3EB489)
    )

    /** designs/hi-* light: Island Day — sun-washed lagoon + sand. */
    val HAWAII_DAY = AppThemeSpec(
        id = "hawaii_day",
        name = "Hawaii Day",
        isDark = false,
        primary = Color(0xFF00A0B4), onPrimary = Color.White,
        secondary = Color(0xFFFF6B5E),
        background = Color(0xFFF2FBFC), onBackground = Color(0xFF0E3A44),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E3A44),
        surfaceVariant = Color(0xFFD9F2F4), onSurfaceVariant = Color(0xFF3C6E79),
        outline = Color(0xFFA5D6DD),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF4DD9E8), 0.18f, 0.12f, 0.45f, 0.35f),
            ThemeOrb(Color(0xFFFFB347), 0.90f, 0.30f, 0.32f, 0.28f),
            ThemeOrb(Color(0xFFF2E3C6), 0.55f, 0.92f, 0.50f, 0.45f)
        ),
        cornerRadius = 26, surfaceAlpha = 0.85f, edgeGlow = true, elevation = 0
    )

    /** designs/hud-* dark: Night Flight — carbon + HUD green, mono readouts. */
    val COCKPIT_HUD = AppThemeSpec(
        id = "cockpit_hud",
        name = "Cockpit HUD",
        isDark = true,
        primary = Color(0xFF00FF9C), onPrimary = Color.Black,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF0A0C10), onBackground = Color(0xFFC8FFE9),
        surface = Color(0xFF10141C), onSurface = Color(0xFFC8FFE9),
        surfaceVariant = Color(0xFF161D28), onSurfaceVariant = Color(0xFF6FBF9E),
        outline = Color(0xFF1F4A38),
        cornerRadius = 8, surfaceAlpha = 1f, edgeGlow = true, elevation = 0,
        glowColor = Color(0xFF00FF9C),
        connected = Color(0xFF00FF9C), connecting = Color(0xFFFFB300),
        monoFont = true
    )

    /** Gaming RGB energy: magenta/cyan neon on near-black. */
    val DARK_NEON = AppThemeSpec(
        id = "dark_neon",
        name = "Dark Neon",
        isDark = true,
        primary = Color(0xFFFF2D95), onPrimary = Color.White,
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF0D0716), onBackground = Color(0xFFF4E9FF),
        surface = Color(0xFF190E2A), onSurface = Color(0xFFF4E9FF),
        surfaceVariant = Color(0xFF241540), onSurfaceVariant = Color(0xFFB79ADB),
        outline = Color(0xFF4A2A78),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFFFF2D95), 0.10f, 0.85f, 0.40f, 0.25f),
            ThemeOrb(Color(0xFF00E5FF), 0.90f, 0.12f, 0.38f, 0.25f)
        ),
        cornerRadius = 18, surfaceAlpha = 0.92f, edgeGlow = true, elevation = 0,
        glowColor = Color(0xFFFF2D95)
    )

    /** Pure-black AMOLED battery saver. */
    val OLED_BLACK = AppThemeSpec(
        id = "oled_black",
        name = "OLED Black",
        isDark = true,
        primary = Color(0xFF4D8DFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF000000), onBackground = Color(0xFFDDE3F0),
        surface = Color(0xFF0A0A0C), onSurface = Color(0xFFDDE3F0),
        surfaceVariant = Color(0xFF131318), onSurfaceVariant = Color(0xFF8B93A8),
        outline = Color(0xFF26262E),
        cornerRadius = 14, elevation = 0
    )

    /** Clean bright minimalism. */
    val MINIMAL_LIGHT = AppThemeSpec(
        id = "minimal_light",
        name = "Minimal Light",
        isDark = false,
        primary = Color(0xFF2F6BFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFFF7F8FB), onBackground = Color(0xFF16203A),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16203A),
        surfaceVariant = Color(0xFFECEFF6), onSurfaceVariant = Color(0xFF4A5878),
        outline = Color(0xFFD3DAE8),
        cornerRadius = 18, elevation = 1
    )


    /** designs/01-08 "Modern Material Glow": Pilot Dark + neon glow accents. */
    val PILOT_GLOW = AppThemeSpec(
        id = "pilot_glow",
        name = "Pilot Glow",
        isDark = true,
        primary = Color(0xFF2F6BFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFF0B0F1A), onBackground = Color(0xFFE6EAF5),
        surface = Color(0xFF121828), onSurface = Color(0xFFE6EAF5),
        surfaceVariant = Color(0xFF1A2238), onSurfaceVariant = Color(0xFFAAB4CE),
        outline = Color(0xFF2C3A5C),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF2F6BFF), 0.85f, 0.10f, 0.35f, 0.22f),
            ThemeOrb(Color(0xFF29C5FF), 0.10f, 0.85f, 0.40f, 0.18f)
        ),
        cornerRadius = 16, surfaceAlpha = 0.94f, edgeGlow = true, elevation = 2,
        glowColor = Color(0xFF2F6BFF)
    )

    /** designs/glass-* light: Frost Glass — milky panels over pastel sky. */
    val LIQUID_GLASS_LIGHT = AppThemeSpec(
        id = "liquid_glass_light",
        name = "Frost Glass",
        isDark = false,
        primary = Color(0xFF2F6BFF), onPrimary = Color.White,
        secondary = Color(0xFF29C5FF),
        background = Color(0xFFF2F5FC), onBackground = Color(0xFF1A2745),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A2745),
        surfaceVariant = Color(0xFFE7EDFA), onSurfaceVariant = Color(0xFF4A5878),
        outline = Color(0xFFBFCDE8),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF9DB8FF), 0.15f, 0.12f, 0.45f, 0.40f),
            ThemeOrb(Color(0xFFC9A8FF), 0.88f, 0.40f, 0.38f, 0.32f),
            ThemeOrb(Color(0xFF8FE3FF), 0.35f, 0.90f, 0.48f, 0.35f)
        ),
        cornerRadius = 24, surfaceAlpha = 0.78f, edgeGlow = true, elevation = 0
    )

    /** designs/lgm-* dark: Material You Glass — charcoal-indigo mesh. */
    val GLASS_YOU_DARK = AppThemeSpec(
        id = "glass_you_dark",
        name = "You Glass Dark",
        isDark = true,
        primary = Color(0xFF9FC2FF), onPrimary = Color(0xFF0E2A55),
        secondary = Color(0xFF6FE0D2),
        background = Color(0xFF121420), onBackground = Color(0xFFE9EAF4),
        surface = Color(0xFF1C1F30), onSurface = Color(0xFFE9EAF4),
        surfaceVariant = Color(0xFF272B42), onSurfaceVariant = Color(0xFFB4B9D6),
        outline = Color(0xFF434A6E),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF5B6BD6), 0.20f, 0.15f, 0.45f, 0.30f),
            ThemeOrb(Color(0xFF3EB9A9), 0.85f, 0.55f, 0.38f, 0.24f),
            ThemeOrb(Color(0xFF8A64C9), 0.40f, 0.92f, 0.42f, 0.24f)
        ),
        cornerRadius = 28, surfaceAlpha = 0.80f, edgeGlow = true, elevation = 0
    )

    /** designs/lgm-* light: Material You Glass — cream-pastel mesh. */
    val GLASS_YOU_LIGHT = AppThemeSpec(
        id = "glass_you_light",
        name = "You Glass Light",
        isDark = false,
        primary = Color(0xFF3B5BA9), onPrimary = Color.White,
        secondary = Color(0xFF2E8C80),
        background = Color(0xFFFBF8F2), onBackground = Color(0xFF23283D),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF23283D),
        surfaceVariant = Color(0xFFEFEAF8), onSurfaceVariant = Color(0xFF565D7A),
        outline = Color(0xFFCCC6E0),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFFB9C6F2), 0.18f, 0.14f, 0.45f, 0.45f),
            ThemeOrb(Color(0xFFA8E3DA), 0.85f, 0.45f, 0.36f, 0.38f),
            ThemeOrb(Color(0xFFE7C7F2), 0.45f, 0.90f, 0.45f, 0.38f)
        ),
        cornerRadius = 28, surfaceAlpha = 0.85f, edgeGlow = true, elevation = 0
    )

    /** designs/hud-* light: Day Flight — brushed aluminum, navy + amber. */
    val DAY_FLIGHT = AppThemeSpec(
        id = "day_flight",
        name = "Day Flight",
        isDark = false,
        primary = Color(0xFF16305A), onPrimary = Color.White,
        secondary = Color(0xFFE07B00),
        background = Color(0xFFD8DCE2), onBackground = Color(0xFF16213A),
        surface = Color(0xFFE9ECF1), onSurface = Color(0xFF16213A),
        surfaceVariant = Color(0xFFC9CFD9), onSurfaceVariant = Color(0xFF44506B),
        outline = Color(0xFF9AA5B8),
        cornerRadius = 8, surfaceAlpha = 1f, edgeGlow = true, elevation = 1,
        glowColor = Color(0xFFE07B00),
        connected = Color(0xFF1E7B4F), connecting = Color(0xFFE07B00),
        monoFont = true
    )


    val CYBERPUNK = AppThemeSpec(
        id = "cyberpunk", name = "Cyberpunk", isDark = true,
        primary = Color(0xFFFCEE0A), onPrimary = Color.Black,
        secondary = Color(0xFF00F0FF),
        background = Color(0xFF0D0221), onBackground = Color(0xFFF3E9FF),
        surface = Color(0xFF1A0B38), onSurface = Color(0xFFF3E9FF),
        surfaceVariant = Color(0xFF261352), onSurfaceVariant = Color(0xFFB79ADB),
        outline = Color(0xFF4A2A78),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFFFCEE0A), 0.85f, 0.15f, 0.32f, 0.20f),
            ThemeOrb(Color(0xFF00F0FF), 0.12f, 0.80f, 0.40f, 0.22f),
            ThemeOrb(Color(0xFFFF2D95), 0.55f, 0.45f, 0.35f, 0.15f)
        ),
        cornerRadius = 10, surfaceAlpha = 0.90f, edgeGlow = true,
        elevation = 0, glowColor = Color(0xFFFCEE0A)
    )

    val CHROME = AppThemeSpec(
        id = "chrome", name = "Chrome Metal", isDark = false,
        primary = Color(0xFF37474F), onPrimary = Color.White,
        secondary = Color(0xFF00838F),
        background = Color(0xFFE8EBEE), onBackground = Color(0xFF20262B),
        surface = Color(0xFFF6F8FA), onSurface = Color(0xFF20262B),
        surfaceVariant = Color(0xFFD5DBe1), onSurfaceVariant = Color(0xFF4A555E),
        outline = Color(0xFFAAB4BD),
        cornerRadius = 12, elevation = 3, edgeGlow = true
    )

    val SYNTHWAVE = AppThemeSpec(
        id = "synthwave", name = "Synthwave", isDark = true,
        primary = Color(0xFFFF6EC7), onPrimary = Color.Black,
        secondary = Color(0xFF7DF9FF),
        background = Color(0xFF16003B), onBackground = Color(0xFFFFE3F8),
        surface = Color(0xFF260A52), onSurface = Color(0xFFFFE3F8),
        surfaceVariant = Color(0xFF351570), onSurfaceVariant = Color(0xFFC9A8E8),
        outline = Color(0xFF5A2D96),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFFFF6EC7), 0.50f, 0.85f, 0.55f, 0.28f),
            ThemeOrb(Color(0xFF7DF9FF), 0.85f, 0.15f, 0.35f, 0.20f)
        ),
        cornerRadius = 18, surfaceAlpha = 0.88f, edgeGlow = true,
        elevation = 0, glowColor = Color(0xFFFF6EC7)
    )

    val GALAXY = AppThemeSpec(
        id = "galaxy", name = "Galaxy", isDark = true,
        primary = Color(0xFF9F7BFF), onPrimary = Color.White,
        secondary = Color(0xFF4DD0E1),
        background = Color(0xFF060312), onBackground = Color(0xFFEDE7FF),
        surface = Color(0xFF150E2E), onSurface = Color(0xFFEDE7FF),
        surfaceVariant = Color(0xFF201646), onSurfaceVariant = Color(0xFFAF9FD6),
        outline = Color(0xFF3D2E6E),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFF6A3DE8), 0.20f, 0.20f, 0.45f, 0.30f),
            ThemeOrb(Color(0xFF4DD0E1), 0.80f, 0.60f, 0.30f, 0.20f),
            ThemeOrb(Color(0xFFE91E63), 0.45f, 0.90f, 0.35f, 0.15f)
        ),
        cornerRadius = 22, surfaceAlpha = 0.82f, edgeGlow = true, elevation = 0
    )

    val TACTICAL = AppThemeSpec(
        id = "tactical", name = "Tactical", isDark = true,
        primary = Color(0xFF8BC34A), onPrimary = Color.Black,
        secondary = Color(0xFFCDDC39),
        background = Color(0xFF14180F), onBackground = Color(0xFFE2E8D5),
        surface = Color(0xFF1E2417), onSurface = Color(0xFFE2E8D5),
        surfaceVariant = Color(0xFF2A331F), onSurfaceVariant = Color(0xFF9AA88A),
        outline = Color(0xFF44523A),
        cornerRadius = 6, elevation = 1, edgeGlow = true, monoFont = true,
        glowColor = Color(0xFF8BC34A)
    )

    val PASTEL = AppThemeSpec(
        id = "pastel", name = "Pastel Soft", isDark = false,
        // SECTION 4 accessibility fix: white on pastel blue was 2.6:1.
        primary = Color(0xFF7C9EF5), onPrimary = Color(0xFF1B2B52),
        secondary = Color(0xFFF5A9C4),
        background = Color(0xFFFDF7FA), onBackground = Color(0xFF3A3A4A),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF3A3A4A),
        surfaceVariant = Color(0xFFF3E9F2), onSurfaceVariant = Color(0xFF77778A),
        outline = Color(0xFFD9CBE0),
        backgroundOrbs = listOf(
            ThemeOrb(Color(0xFFBFD4FF), 0.20f, 0.15f, 0.45f, 0.40f),
            ThemeOrb(Color(0xFFFFD6E8), 0.85f, 0.50f, 0.38f, 0.38f),
            ThemeOrb(Color(0xFFD9F7E8), 0.40f, 0.90f, 0.42f, 0.35f)
        ),
        cornerRadius = 26, surfaceAlpha = 0.90f, edgeGlow = true, elevation = 0
    )

    /** All themes, gallery order. */
    val ALL: List<AppThemeSpec> = listOf(
        AERO_GLASS,
        PILOT_DARK, PILOT_GLOW,
        LIQUID_GLASS, LIQUID_GLASS_LIGHT,
        GLASS_YOU_DARK, GLASS_YOU_LIGHT,
        HAWAII_NIGHT, HAWAII_DAY,
        COCKPIT_HUD, DAY_FLIGHT,
        DARK_NEON, OLED_BLACK, MINIMAL_LIGHT,
        CYBERPUNK, SYNTHWAVE, GALAXY, TACTICAL, CHROME, PASTEL
    )

    /** Safe lookup with default fallback. */
    fun byId(id: String?): AppThemeSpec =
        ALL.firstOrNull { it.id == id } ?: PILOT_DARK

    /**
     * Dark/light counterpart within the same design family, used when the
     * user forces LIGHT or DARK mode while a theme of the other brightness
     * is selected (Hawaii Night <-> Hawaii Day, Liquid Glass <-> Frost
     * Glass, Cockpit HUD <-> Day Flight, ...).
     */
    fun counterpart(spec: AppThemeSpec): AppThemeSpec = when (spec.id) {
        "aero_glass" -> MINIMAL_LIGHT
        "pilot_dark", "pilot_glow", "oled_black", "dark_neon" -> MINIMAL_LIGHT
        "liquid_glass" -> LIQUID_GLASS_LIGHT
        "liquid_glass_light" -> LIQUID_GLASS
        "glass_you_dark" -> GLASS_YOU_LIGHT
        "glass_you_light" -> GLASS_YOU_DARK
        "hawaii_night" -> HAWAII_DAY
        "hawaii_day" -> HAWAII_NIGHT
        "cockpit_hud" -> DAY_FLIGHT
        "day_flight" -> COCKPIT_HUD
        "minimal_light" -> PILOT_DARK
        else -> if (spec.isDark) MINIMAL_LIGHT else PILOT_DARK
    }
}
