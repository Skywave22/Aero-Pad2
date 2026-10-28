package com.aeropad.remote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.aeropad.remote.model.HapticIntensity
import com.aeropad.remote.ui.theme.LocalAppTheme

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
    /**
     * Round 12 — optional vector icon drawn beside the label.
     *
     * Labels used to embed symbols like "⏻ Power" (U+23FB). That codepoint is
     * missing from many Android system fonts, so on a real device it rendered
     * as a tofu box next to the word Power. A vector can never tofu.
     */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    // True press-depth: the key tips back and sinks in, and its shadow
    // collapses while pressed (light-source simulation). Honors Reduce Motion.
    val elevation = pressedElevation(interactionSource, idle = 6f, pressed = 1f)
    val shape = MaterialTheme.shapes.medium

    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val baseModifier = modifier
        .pressDepth3D(interactionSource)
        .heightIn(min = height)
        // Clip BEFORE clickable so the ripple follows the rounded key
        // shape — no square corners flashing outside the key on tap.
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )

    // Solid key: gradient fake-lighting (top-left light source) + dynamic
    // shadow that collapses while pressed. Emphasized keys (ENTER, LEFT
    // CLICK…) use the primary container so they stand out instantly.
    val base = if (emphasized) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = baseModifier
            .surface3D(
                base = base,
                shape = shape,
                material = Material3D.GLOSSY,
                elevation = elevation.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** Thin banner shown on control screens when not connected. */
@Composable
fun NotConnectedBanner(visible: Boolean) {
    if (!visible) return
    // Round 14 — on a landscape phone the two-line sentence took about 60 dp
    // of a ~270 dp window, which is the difference between a complete gamepad
    // and a clipped one. Short landscape wording, single line, smaller padding;
    // the full sentence stays in portrait where there is room for it.
    val compact = com.aeropad.remote.ui.components.isLandscape()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 4.dp else 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = if (compact) "Not connected — open Connect to pair."
            else "Not connected — controls are inactive. Open Connect to pair.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = if (compact) 1 else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp)
        )
    }
}
