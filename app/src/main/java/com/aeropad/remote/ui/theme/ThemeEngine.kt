package com.aeropad.remote.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * AeroPad theme system — pure and simple.
 *
 * There are exactly TWO looks: **Light** (clean white) and **Dark**
 * (pure black, OLED-friendly). No theme gallery, no skins, no schedules —
 * the user picks Light / Dark / Follow system in Settings and that's it.
 *
 * [LocalAppTheme] carries the active [AppThemeSpec] design tokens to every
 * composable; [AeroPadAppTheme] bridges the tokens into Material 3 so all
 * screens are styled consistently from one place.
 */
val LocalAppTheme = staticCompositionLocalOf { AeroPadSpecs.DARK }

/** The two built-in looks. */
object AeroPadSpecs {

    /** Shared accent: a confident, high-contrast blue. */
    private val AccentBlue = androidx.compose.ui.graphics.Color(0xFF2563EB)
    private val AccentBlueBright = androidx.compose.ui.graphics.Color(0xFF5B8DFF)

    val LIGHT = AppThemeSpec(
        id = "light",
        name = "Light",
        isDark = false,
        primary = AccentBlue,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = androidx.compose.ui.graphics.Color(0xFF0E7490),
        background = androidx.compose.ui.graphics.Color(0xFFF7F8FA),
        onBackground = androidx.compose.ui.graphics.Color(0xFF111827),
        surface = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color(0xFF111827),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEEF1F5),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4B5563),
        outline = androidx.compose.ui.graphics.Color(0xFFD3D9E3),
        surfaceAlpha = 1f,
        cornerRadius = 20
    )

    val DARK = AppThemeSpec(
        id = "dark",
        name = "Dark",
        isDark = true,
        primary = AccentBlueBright,
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        secondary = androidx.compose.ui.graphics.Color(0xFF22D3EE),
        background = androidx.compose.ui.graphics.Color.Black,
        onBackground = androidx.compose.ui.graphics.Color(0xFFF3F4F6),
        surface = androidx.compose.ui.graphics.Color(0xFF121212),
        onSurface = androidx.compose.ui.graphics.Color(0xFFF3F4F6),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9CA3AF),
        outline = androidx.compose.ui.graphics.Color(0xFF2E2E33),
        surfaceAlpha = 1f,
        cornerRadius = 20
    )

    fun byMode(dark: Boolean): AppThemeSpec = if (dark) DARK else LIGHT

    /**
     * Round 9 — apply the user's accent palette on top of whatever finish is
     * active (Material / Glass / Neon). Only the accent hues move; every
     * surface colour stays as designed so contrast and OLED blackness are
     * preserved.
     */
    fun accented(spec: AppThemeSpec, accentId: String?): AppThemeSpec {
        val palette = AccentPalette.fromId(accentId)
        if (palette == AccentPalette.DEFAULT) return spec
        return spec.copy(
            primary = palette.accent(spec.isDark),
            onPrimary = palette.onAccent(spec.isDark),
            secondary = palette.secondary(spec.isDark)
        )
    }

    /** Round 7 — premium finishes layered on the Light/Dark base. */
    fun styled(style: String, dark: Boolean): AppThemeSpec = when (style) {
        "GLASS" -> byMode(dark).copy(
            id = "glass", name = "Glass",
            surfaceAlpha = 0.55f,
            edgeGlow = true,
            cornerRadius = 24,
            elevation = 0,
            backgroundOrbs = listOf(
                ThemeOrb(androidx.compose.ui.graphics.Color(0xFF3D8BFF), 0.15f, 0.2f, 0.5f, 0.25f),
                ThemeOrb(androidx.compose.ui.graphics.Color(0xFF9B59F6), 0.85f, 0.75f, 0.55f, 0.22f)
            )
        )
        "NEON" -> DARK.copy(
            id = "neon", name = "Neon",
            primary = androidx.compose.ui.graphics.Color(0xFF00E5FF),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF00272B),
            secondary = androidx.compose.ui.graphics.Color(0xFFFF2BD6),
            background = androidx.compose.ui.graphics.Color(0xFF05070D),
            surface = androidx.compose.ui.graphics.Color(0xFF0B1220),
            glowColor = androidx.compose.ui.graphics.Color(0xFF00E5FF),
            edgeGlow = true,
            cornerRadius = 18,
            backgroundOrbs = listOf(
                ThemeOrb(androidx.compose.ui.graphics.Color(0xFF00E5FF), 0.2f, 0.1f, 0.45f, 0.18f),
                ThemeOrb(androidx.compose.ui.graphics.Color(0xFFFF2BD6), 0.8f, 0.9f, 0.5f, 0.16f)
            )
        )
        else -> byMode(dark)
    }
}

@Composable
fun AeroPadAppTheme(
    spec: AppThemeSpec,
    content: @Composable () -> Unit
) {
    // Container colors must stay OPAQUE so elevated cards never show
    // shadow bleed-through on emphasized keys.
    val primaryContainerC =
        spec.primary.copy(alpha = if (spec.isDark) 0.22f else 0.12f).compositeOver(spec.surface)
    val errorContainerC =
        spec.error.copy(alpha = if (spec.isDark) 0.25f else 0.12f).compositeOver(spec.surface)

    val scheme = if (spec.isDark) {
        darkColorScheme(
            primary = spec.primary, onPrimary = spec.onPrimary,
            primaryContainer = primaryContainerC,
            onPrimaryContainer = spec.primary,
            secondary = spec.secondary, onSecondary = spec.onPrimary,
            background = spec.background, onBackground = spec.onBackground,
            surface = spec.surface, onSurface = spec.onSurface,
            surfaceVariant = spec.surfaceVariant, onSurfaceVariant = spec.onSurfaceVariant,
            outline = spec.outline,
            error = spec.error, onError = androidx.compose.ui.graphics.Color.White,
            errorContainer = errorContainerC, onErrorContainer = spec.error
        )
    } else {
        lightColorScheme(
            primary = spec.primary, onPrimary = spec.onPrimary,
            primaryContainer = primaryContainerC,
            onPrimaryContainer = spec.primary,
            secondary = spec.secondary, onSecondary = androidx.compose.ui.graphics.Color.White,
            background = spec.background, onBackground = spec.onBackground,
            surface = spec.surface, onSurface = spec.onSurface,
            surfaceVariant = spec.surfaceVariant, onSurfaceVariant = spec.onSurfaceVariant,
            outline = spec.outline,
            error = spec.error, onError = androidx.compose.ui.graphics.Color.White,
            errorContainer = errorContainerC, onErrorContainer = spec.error
        )
    }

    // Shape scale derived from the single design-token radius.
    val r = spec.cornerRadius
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((r / 3).coerceAtLeast(2).dp),
        small = RoundedCornerShape((r / 2).coerceAtLeast(4).dp),
        medium = RoundedCornerShape(r.dp),
        large = RoundedCornerShape((r + r / 3).dp),
        extraLarge = RoundedCornerShape((r * 2).coerceAtMost(56).dp)
    )

    // Smooth Light<->Dark cross-dissolve (350ms); skipped under Reduce Motion.
    val animated = animateColorScheme(scheme)

    CompositionLocalProvider(LocalAppTheme provides spec) {
        MaterialTheme(
            colorScheme = animated,
            typography = AeroPadTypography,
            shapes = shapes,
            content = content
        )
    }
}

/** Animates the most visible scheme colors on mode change. */
@Composable
private fun animateColorScheme(target: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val reduce = com.aeropad.remote.ui.components.LocalReduceMotion.current
    if (reduce) return target
    val spec = tween<androidx.compose.ui.graphics.Color>(350)

    @Composable
    fun c(color: androidx.compose.ui.graphics.Color) =
        androidx.compose.animation.animateColorAsState(color, spec, label = "themeColor").value

    return target.copy(
        primary = c(target.primary),
        onPrimary = c(target.onPrimary),
        primaryContainer = c(target.primaryContainer),
        onPrimaryContainer = c(target.onPrimaryContainer),
        secondary = c(target.secondary),
        background = c(target.background),
        onBackground = c(target.onBackground),
        surface = c(target.surface),
        onSurface = c(target.onSurface),
        surfaceVariant = c(target.surfaceVariant),
        onSurfaceVariant = c(target.onSurfaceVariant),
        outline = c(target.outline),
        error = c(target.error),
        errorContainer = c(target.errorContainer)
    )
}

/**
 * App background: a clean solid color from the active mode.
 */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spec = LocalAppTheme.current
    val reduceMotion = com.aeropad.remote.ui.components.LocalReduceMotion.current

    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = spec.background,
        animationSpec = if (reduceMotion) tween(0) else tween(350),
        label = "themedBg"
    )

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        content()
    }
}
