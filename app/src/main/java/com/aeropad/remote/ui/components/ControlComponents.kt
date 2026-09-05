package com.aeropad.remote.ui.components

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
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val spec = LocalAppTheme.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    
    val base = if (emphasized) spec.primaryContainer else spec.surfaceVariant
    val contentColor = if (emphasized) spec.onPrimaryContainer else spec.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelLarge
    
    Box(
        modifier = modifier
            .heightIn(min = height)
            .background(base, shape)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
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
