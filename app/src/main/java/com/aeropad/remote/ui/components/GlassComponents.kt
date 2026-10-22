package com.aeropad.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AeroPad card — the ONE shared surface across the app.
 *
 * Pure and simple: a solid surface with a hairline outline. No gloss,
 * no translucency, no tilt tricks — content always stays readable in
 * both Light (white) and Dark (black) mode.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(borderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
    ) {
        content()
    }
}

/**
 * Tinted icon chip for grid tiles: a soft wash of the tile's hue behind
 * a solid icon of the same hue. Calm, flat, readable in both modes.
 */
@Composable
fun GelIcon(
    color: Color,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = if (enabled) color else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
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
 * One-line contextual hint bar for non-obvious gestures
 * (long-press, swipe zones). Subtle and readable in both modes.
 */
@Composable
fun HintBar(text: String, modifier: Modifier = Modifier) {
    // Round 12 — on a real 1600x720 phone the landscape window is ~360 dp tall
    // and a control screen stacks an app bar, a not-connected banner, these
    // tips and a chip row above the actual control. That left the trackpad
    // 55 dp tall. The tips are the least valuable of the four and they are
    // repeated in Help, so they step aside in landscape.
    if (com.aeropad.remote.ui.components.isLandscape()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp)
            )
    ) {
        androidx.compose.material3.Text(
            text = "💡 $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
