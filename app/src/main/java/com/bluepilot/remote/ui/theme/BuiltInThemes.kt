package com.bluepilot.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NEW MD3 LIGHT THEME SET
 */
object BuiltInThemes {

    // ==================== MATERIAL FAMILY ====================

    /** Material 3 Light (Default) */
    val MATERIAL_YOU_LIGHT = AppThemeSpec(
        id = "material_you_light", name = "Material Light", isDark = false,
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        secondary = Color(0xFF625B71),
        background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFF7F2FA), onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFFCAC4D0),
        cornerRadius = 28, surfaceAlpha = 1f, edgeGlow = false, elevation = 0
    )

    /** Material Mint Light */
    val MATERIAL_MINT_LIGHT = AppThemeSpec(
        id = "material_mint_light", name = "Material Mint", isDark = false,
        primary = Color(0xFF006C4C), onPrimary = Color.White,
        secondary = Color(0xFF4C6357),
        background = Color(0xFFFBFDF9), onBackground = Color(0xFF191C1A),
        surface = Color(0xFFFBFDF9), onSurface = Color(0xFF191C1A),
        surfaceVariant = Color(0xFFDBE5DD), onSurfaceVariant = Color(0xFF404943),
        outline = Color(0xFF707973),
        cornerRadius = 28, surfaceAlpha = 1f, edgeGlow = false, elevation = 0
    )

    /** Material Rose Light */
    val MATERIAL_ROSE_LIGHT = AppThemeSpec(
        id = "material_rose_light", name = "Material Rose", isDark = false,
        primary = Color(0xFF904A41), onPrimary = Color.White,
        secondary = Color(0xFF775753),
        background = Color(0xFFFFFBFA), onBackground = Color(0xFF201A19),
        surface = Color(0xFFFFFBFA), onSurface = Color(0xFF201A19),
        surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
        outline = Color(0xFF857371),
        cornerRadius = 28, surfaceAlpha = 1f, edgeGlow = false, elevation = 0
    )

    // ==================== CATALOG ====================

    val ALL: List<AppThemeSpec> = listOf(
        MATERIAL_YOU_LIGHT, MATERIAL_MINT_LIGHT, MATERIAL_ROSE_LIGHT
    )

    fun byId(id: String?): AppThemeSpec =
        ALL.firstOrNull { it.id == id } ?: MATERIAL_YOU_LIGHT

    fun counterpart(spec: AppThemeSpec): AppThemeSpec = spec
}
