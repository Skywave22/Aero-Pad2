package com.bluepilot.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.ui.theme.LocalAppTheme

/**
 * Reusable GlassCard implementing the liquid-glass look:
 * - Translucent surface (derived from active spec surfaceAlpha)
 * - White sheen vertical gradient overlay (top ~12% alpha to 0%)
 * - 1dp top-bright vertical gradient border
 * - Optional system blur on Android 12+ (API 31+)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    val spec = LocalAppTheme.current
    val surfaceAlpha = spec.surfaceAlpha
    val surfaceColor = spec.surface.copy(alpha = surfaceAlpha)

    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0f)
        )
    )

    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            spec.outline.copy(alpha = 0.12f)
        )
    )

    // NOTE: no Modifier.blur() here — Compose's blur() blurs the card's
    // CONTENT (children), not the backdrop behind it. Applying it made the
    // whole Themes screen (and any GlassCard content) an unreadable fog on
    // Android 12+. The frosted look comes from translucency + sheen alone.
    //
    // V2 PART B b2 — micro-tilt: cards lean ~1.5° with real device tilt
    // (GPU graphicsLayer, draw-phase read; (0,0) tilt = perfectly flat,
    // the exact pre-B rendering — no gate needed, zero times anything = 0).
    val (deviceTiltX, deviceTiltY) = LocalDeviceTilt.current
    Box(
        modifier = modifier
            .graphicsLayer {
                if (deviceTiltX != 0f || deviceTiltY != 0f) {
                    cameraDistance = 24f * density
                    rotationY = deviceTiltX * 1.5f
                    rotationX = deviceTiltY * 1.5f
                }
            }
            .background(surfaceColor, shape)
            .background(sheenBrush, shape)
            .border(borderWidth, borderBrush, shape)
    ) {
        content()
    }
}

/**
 * 3D Gel Icon badge:
 * - Rounded-square badge with two-tone vertical gradient of the tile's hue
 * - Inner top highlight (white-alpha rounded rectangle near top edge)
 * - Thin white-alpha border
 * - White icon inside
 * - Spot shadow color matched to the hue for soft colored glow/shadow
 */
@Composable
fun GelIcon(
    color: Color,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    val factor = 0.72f
    val bottomColor = Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )

    val gradient = Brush.verticalGradient(
        colors = if (enabled) listOf(color, bottomColor)
        else listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline)
    )

    val shadowModifier = if (enabled) {
        Modifier.graphicsLayer {
            shadowElevation = 8.dp.toPx()
            this.shape = shape
            clip = false
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    Box(
        modifier = modifier
            .then(shadowModifier)
            .size(size)
            .background(gradient, shape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(size * 0.22f)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
        )

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Custom Modifier for drawing selection HUD corner brackets instead of full borders.
 */
fun Modifier.cornerBrackets(
    color: Color,
    bracketLength: Dp = 12.dp,
    strokeWidth: Dp = 2.dp
) = drawWithContent {
    drawContent()
    val length = bracketLength.toPx()
    val stroke = strokeWidth.toPx()
    val w = size.width
    val h = size.height

    // Top-Left corner
    drawLine(color, Offset(0f, 0f), Offset(length, 0f), strokeWidth = stroke)
    drawLine(color, Offset(0f, 0f), Offset(0f, length), strokeWidth = stroke)

    // Top-Right corner
    drawLine(color, Offset(w, 0f), Offset(w - length, 0f), strokeWidth = stroke)
    drawLine(color, Offset(w, 0f), Offset(w, length), strokeWidth = stroke)

    // Bottom-Left corner
    drawLine(color, Offset(0f, h), Offset(length, h), strokeWidth = stroke)
    drawLine(color, Offset(0f, h), Offset(0f, h - length), strokeWidth = stroke)

    // Bottom-Right corner
    drawLine(color, Offset(w, h), Offset(w - length, h), strokeWidth = stroke)
    drawLine(color, Offset(w, h), Offset(w, h - length), strokeWidth = stroke)
}

/**
 * SECTION 4 — one-line contextual hint bar for non-obvious gestures
 * (long-press, swipe zones). Subtle, dismiss-free, theme-aware.
 */
@Composable
fun HintBar(text: String, modifier: Modifier = Modifier) {
    val spec = LocalAppTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                spec.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(10.dp)
            )
    ) {
        androidx.compose.material3.Text(
            text = "💡 $text",
            style = MaterialTheme.typography.bodyMedium,
            color = spec.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
