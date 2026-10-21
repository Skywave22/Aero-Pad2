package com.aeropad.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent colours for the two base looks.
 *
 * Why a curated palette instead of Material You's wallpaper colours? Because
 * this app is used while driving another machine, often in the dark — a
 * wallpaper-derived accent can land on a low-contrast mustard and make every
 * button mushy. These six were picked for contrast against both the white and
 * the OLED-black surface, and each carries a separate dark variant that is
 * brightened for use on black.
 *
 * Pure data, no Compose dependencies beyond `Color`, so the mapping is trivially
 * testable and shared by the themed UI and the settings preview swatches.
 */
enum class AccentPalette(
    val id: String,
    val label: String,
    /** Accent used on the light (white) surface. */
    val light: Color,
    /** Accent used on the dark (black) surface — lightened for contrast. */
    val dark: Color,
    /** Secondary hue that pairs with the accent (also split light/dark). */
    val secondaryLight: Color,
    val secondaryDark: Color,
    /** Text/icon colour that sits on top of the accent. */
    val onAccentLight: Color,
    val onAccentDark: Color
) {
    BLUE(
        id = "BLUE", label = "Blue",
        light = Color(0xFF2563EB), dark = Color(0xFF5B8DFF),
        secondaryLight = Color(0xFF0E7490), secondaryDark = Color(0xFF22D3EE),
        onAccentLight = Color.White, onAccentDark = Color(0xFF06121F)
    ),
    TEAL(
        id = "TEAL", label = "Teal",
        light = Color(0xFF0F766E), dark = Color(0xFF2DD4BF),
        secondaryLight = Color(0xFF1D4ED8), secondaryDark = Color(0xFF60A5FA),
        onAccentLight = Color.White, onAccentDark = Color(0xFF00201C)
    ),
    VIOLET(
        id = "VIOLET", label = "Violet",
        light = Color(0xFF6D28D9), dark = Color(0xFFC4B5FD),
        secondaryLight = Color(0xFFBE185D), secondaryDark = Color(0xFFF472B6),
        onAccentLight = Color.White, onAccentDark = Color(0xFF14052B)
    ),
    AMBER(
        id = "AMBER", label = "Amber",
        light = Color(0xFFB45309), dark = Color(0xFFFBBF24),
        secondaryLight = Color(0xFF0F766E), secondaryDark = Color(0xFF34D399),
        // Amber needs dark text even on the light variant for contrast.
        onAccentLight = Color(0xFF2A1700), onAccentDark = Color(0xFF241800)
    ),
    ROSE(
        id = "ROSE", label = "Rose",
        light = Color(0xFFBE123C), dark = Color(0xFFFDA4AF),
        secondaryLight = Color(0xFF4338CA), secondaryDark = Color(0xFF93C5FD),
        onAccentLight = Color.White, onAccentDark = Color(0xFF2B0710)
    ),
    LIME(
        id = "LIME", label = "Lime",
        light = Color(0xFF4D7C0F), dark = Color(0xFFBEF264),
        secondaryLight = Color(0xFF0E7490), secondaryDark = Color(0xFF22D3EE),
        onAccentLight = Color.White, onAccentDark = Color(0xFF1A2300)
    );

    /** The colour to paint on a [dark] surface. */
    fun accent(dark: Boolean): Color = if (dark) this.dark else light
    fun secondary(dark: Boolean): Color = if (dark) secondaryDark else secondaryLight
    fun onAccent(dark: Boolean): Color = if (dark) onAccentDark else onAccentLight

    companion object {
        /** Default keeps the established AeroPad blue for existing users. */
        val DEFAULT = BLUE

        /** Unknown or missing ids fall back to the default instead of throwing. */
        fun fromId(id: String?): AccentPalette =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
