package com.bluepilot.remote.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Theme engine (Section 1, upgraded for the "implement all designs" pass).
 *
 * [LocalAppTheme] carries the active [AppThemeSpec] to every composable.
 * [BluePilotAppTheme] bridges the spec into Material3 so ALL existing
 * screens re-skin automatically. The glass look is applied globally:
 *
 *  - surface / surfaceVariant get the spec's [AppThemeSpec.surfaceAlpha]
 *    baked into the M3 color scheme -> every Card/sheet becomes a frosted
 *    translucent panel and the background orbs glow through, exactly like
 *    the designs/glass-*, lgm-*, hi-* mockups.
 *  - MaterialTheme.shapes are generated from [AppThemeSpec.cornerRadius]
 *    -> HUD themes get tight 8dp instrument corners while glass themes get
 *    soft 24-28dp pills, app-wide, with zero per-screen code.
 */
val LocalAppTheme = staticCompositionLocalOf { BuiltInThemes.PILOT_DARK }

@Composable
fun BluePilotAppTheme(
    spec: AppThemeSpec,
    content: @Composable () -> Unit
) {
    // Glass materials: translucent surfaces let the themed orbs shine through.
    val glassSurface = spec.surface.copy(alpha = spec.surfaceAlpha)
    val glassVariant = spec.surfaceVariant.copy(alpha = spec.surfaceAlpha)

    // FIX (screenshot bug): container colors must be OPAQUE. Translucent
    // containers on elevated Cards let the shadow show THROUGH the surface,
    // rendering an ugly dark "box inside a box" on emphasized keys
    // (ENTER / LEFT CLICK). Composite the tint over the surface instead.
    val primaryContainerC =
        spec.primary.copy(alpha = if (spec.isDark) 0.75f else 0.15f).compositeOver(spec.surface)
    val errorContainerC =
        spec.error.copy(alpha = if (spec.isDark) 0.25f else 0.15f).compositeOver(spec.surface)

    val scheme = if (spec.isDark) {
        darkColorScheme(
            primary = spec.primary, onPrimary = spec.onPrimary,
            primaryContainer = primaryContainerC,
            onPrimaryContainer = spec.onPrimary,
            secondary = spec.secondary, onSecondary = spec.onPrimary,
            background = spec.background, onBackground = spec.onBackground,
            surface = glassSurface, onSurface = spec.onSurface,
            surfaceVariant = glassVariant, onSurfaceVariant = spec.onSurfaceVariant,
            outline = spec.outline,
            error = spec.error, onError = spec.onPrimary,
            errorContainer = errorContainerC, onErrorContainer = spec.onBackground
        )
    } else {
        lightColorScheme(
            primary = spec.primary, onPrimary = spec.onPrimary,
            primaryContainer = primaryContainerC,
            onPrimaryContainer = spec.primary,
            secondary = spec.secondary, onSecondary = spec.onPrimary,
            background = spec.background, onBackground = spec.onBackground,
            surface = glassSurface, onSurface = spec.onSurface,
            surfaceVariant = glassVariant, onSurfaceVariant = spec.onSurfaceVariant,
            outline = spec.outline,
            error = spec.error, onError = spec.onPrimary,
            errorContainer = errorContainerC, onErrorContainer = spec.error
        )
    }

    // Themed shape scale: small/medium/large derived from the spec radius.
    val r = spec.cornerRadius
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((r / 3).coerceAtLeast(2).dp),
        small = RoundedCornerShape((r / 2).coerceAtLeast(4).dp),
        medium = RoundedCornerShape(r.dp),
        large = RoundedCornerShape((r + r / 3).dp),
        extraLarge = RoundedCornerShape((r * 2).coerceAtMost(56).dp)
    )

    // Cockpit HUD themes flip the type ramp to monospace.
    val typography = if (spec.monoFont) monoTypography() else BluePilotTypography

    // SECTION 1 — smooth animated theme switch: every scheme color
    // cross-dissolves (350ms) instead of snapping. Content is NOT
    // recreated (nav/back-stack state survives). Skipped for the very
    // first composition and under Reduce Motion.
    val animated = animateColorScheme(scheme)

    CompositionLocalProvider(LocalAppTheme provides spec) {
        MaterialTheme(
            colorScheme = animated,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

/** Animates the 14 most visible scheme colors on theme change. */
@Composable
private fun animateColorScheme(target: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val reduce = com.bluepilot.remote.ui.components.LocalReduceMotion.current
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

private fun monoTypography(): Typography {
    val base = BluePilotTypography
    return Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Monospace),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Monospace),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Monospace)
    )
}

/**
 * Themed background: solid color plus the theme's blurred gradient orbs
 * (aurora / lagoon / neon looks). Pure Canvas — no bitmaps, no blur cost.
 */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spec = LocalAppTheme.current
    val reduceMotion = com.bluepilot.remote.ui.components.LocalReduceMotion.current

    // SECTION 1 — theme switch cross-dissolve for the backdrop itself,
    // matching the animated Material scheme (no background "snap").
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = spec.background,
        animationSpec = if (reduceMotion) tween(0) else tween(350),
        label = "themedBg"
    )

    // SECTION 3D — parallax depth: orbs drift slowly on offset phases so
    // background layers feel separated. Draw-phase only (reading the
    // animated value inside Canvas), zero recomposition per frame.
    val drift = rememberInfiniteTransition(label = "orbDrift")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 26_000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "orbPhase"
    )

    // V2 PART B — gravity parallax: real tilt shifts each orb layer at a
    // depth-dependent rate (deeper layers move more), so the backdrop
    // separates into true planes when you tilt the phone. (0,0) tilt =
    // exact pre-B rendering.
    val (tiltX, tiltY) = com.bluepilot.remote.ui.components.LocalDeviceTilt.current

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        if (spec.backgroundOrbs.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxEdge = max(size.width, size.height)
                spec.backgroundOrbs.forEachIndexed { index, orb ->
                    // Each orb gets its own phase offset + drift radius, so
                    // layers move at different apparent depths (parallax).
                    val p = if (reduceMotion) 0f
                    else ((phase + index * 0.33f) % 1f) * 2f * Math.PI.toFloat()
                    val driftAmp = maxEdge * 0.015f * (1 + index % 3)
                    // Depth factor: layer 0 nearest (subtle), deeper layers
                    // shift up to ~4% of the long edge at full tilt.
                    val depth = 0.012f * (1 + index % 3)
                    val px = -tiltX * maxEdge * depth
                    val py = tiltY * maxEdge * depth
                    val cx = size.width * orb.x + kotlin.math.sin(p) * driftAmp + px
                    val cy = size.height * orb.y + kotlin.math.cos(p * 0.8f) * driftAmp + py
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                orb.color.copy(alpha = orb.alpha),
                                orb.color.copy(alpha = 0f)
                            ),
                            center = Offset(cx, cy),
                            radius = maxEdge * orb.radius
                        ),
                        radius = maxEdge * orb.radius,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
        content()
    }
}
