package com.bluepilot.remote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.model.HapticIntensity
import com.bluepilot.remote.ui.theme.LocalAppTheme

/**
 * Shared building blocks for control screens.
 *
 * SECTION 3B polish:
 *  - [KeyCard] now animates a press-scale (spring) driven by the real
 *    pressed interaction state — instant visual feedback on touch-down.
 *  - Haptics honor BOTH the on/off setting and the intensity setting,
 *    delivered app-wide through [LocalHapticIntensity] (no per-call-site
 *    plumbing needed).
 */

/** Active haptic intensity, provided at the app root. */
val LocalHapticIntensity = staticCompositionLocalOf { HapticIntensity.MEDIUM }

/**
 * Returns a haptic trigger honoring the user's on/off + intensity settings.
 * LIGHT  → subtle clock-tick, MEDIUM → keyboard tap, STRONG → long-press buzz.
 */
@Composable
fun rememberHaptic(hapticEnabled: Boolean): () -> Unit {
    val view = LocalView.current
    val intensity = LocalHapticIntensity.current
    return {
        if (hapticEnabled) {
            val constant = when (intensity) {
                HapticIntensity.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
                HapticIntensity.MEDIUM -> HapticFeedbackConstants.KEYBOARD_TAP
                HapticIntensity.STRONG -> HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
        }
    }
}

/**
 * A tappable key/button card used across keyboard/numpad/media screens.
 * Press feedback: springy scale-down to 94% while touched (plus the
 * default Material ripple). [height] is a minimum — cards stretch with
 * weight()/fillMaxHeight() so no screen size clips them.
 */
@Composable
fun KeyCard(
    label: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    // SECTION 3D: true press-depth — key tips back and sinks in, and its
    // shadow collapses (light-source simulation). Honors Reduce Motion.
    val elevation = pressedElevation(interactionSource, idle = 6f, pressed = 1f)
    val spec = LocalAppTheme.current
    val shape = MaterialTheme.shapes.medium

    // Monospace & uppercase for HUD theme compatibility
    val finalLabel = if (spec.monoFont) label.uppercase() else label
    val labelStyle = if (spec.monoFont) {
        MaterialTheme.typography.labelLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    } else {
        MaterialTheme.typography.labelLarge
    }

    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val baseModifier = modifier
        .pressDepth3D(interactionSource)
        .heightIn(min = height)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )

    if (spec.surfaceAlpha < 1f) {
        // Glass look: use custom sheen gradient and border
        val surfaceColor = if (emphasized) {
            spec.primary.copy(alpha = 0.35f)
        } else {
            spec.surfaceVariant.copy(alpha = spec.surfaceAlpha)
        }

        val sheenBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0f)
            )
        )

        val borderBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.35f),
                spec.outline.copy(alpha = 0.15f)
            )
        )

        Box(
            modifier = baseModifier
                .background(surfaceColor, shape)
                .background(sheenBrush, shape)
                .border(1.dp, borderBrush, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = finalLabel,
                style = labelStyle,
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    } else {
        // Solid look — real 3D key: gradient fake-lighting (top-left light
        // source) + dynamic shadow that collapses while pressed. This
        // replaces the old elevated Card, whose shadow bled through
        // translucent container colors and painted a dark "box inside a
        // box" on emphasized keys (ENTER / LEFT CLICK screenshot bug).
        val base = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

        val edgeModifier = if (spec.edgeGlow) {
            Modifier.border(
                width = 1.dp,
                color = (spec.glowColor ?: spec.onSurface).copy(alpha = 0.22f),
                shape = shape
            )
        } else Modifier

        Box(
            modifier = baseModifier
                .surface3D(
                    base = base,
                    shape = shape,
                    material = Material3D.GLOSSY,
                    elevation = elevation.dp
                )
                .then(edgeModifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = finalLabel,
                style = labelStyle,
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** Thin banner shown on control screens when not connected. */
@Composable
fun NotConnectedBanner(visible: Boolean) {
    if (!visible) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = "Not connected — controls are inactive. Open Connect to pair.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
