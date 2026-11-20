package com.aeropad.remote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.aeropad.remote.model.HapticIntensity
import com.aeropad.remote.ui.theme.LocalAppTheme

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
 * Material uses a restrained outlined surface; Glass and Neon retain press depth.
 * [height] is the preferred minimum within the parent’s constraints.
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
    maxLines: Int = 1,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val finish = LocalAppTheme.current.id
    val quality = LocalQuality3D.current
    val flat = finish == "light" || finish == "dark" || quality == Quality3D.FLAT

    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val baseModifier = modifier
        .then(if (flat || !enabled) Modifier else Modifier.pressDepth3D(interactionSource))
        .heightIn(min = height)
        // Clip BEFORE clickable so the ripple follows the rounded key
        // shape — no square corners flashing outside the key on tap.
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )

    val colors = MaterialTheme.colorScheme
    val base = if (!enabled) colors.surface else if (emphasized) colors.primaryContainer else colors.surfaceVariant
    val surfaceModifier = if (flat || !enabled) {
        Modifier.background(base).border(1.dp, colors.outlineVariant, shape)
    } else {
        val elevation = pressedElevation(interactionSource, idle = 4f, pressed = 1f)
        Modifier.surface3D(base, shape, material = Material3D.GLOSSY, elevation = elevation.dp, quality = quality)
    }
    val ink = if (enabled) contentColor else colors.onSurfaceVariant.copy(alpha = 0.65f)
    Box(
        modifier = baseModifier.then(surfaceModifier),
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
                    tint = ink,
                    modifier = Modifier.size(20.dp)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/** The app shell supplies the pairing route; standalone previews remain informational. */
val LocalOpenConnection = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun NotConnectedBanner(visible: Boolean) {
    if (!visible) return
    val connect = LocalOpenConnection.current
    val compact = isLandscape()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = if (compact) 4.dp else 8.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .then(if (connect == null) Modifier else Modifier.clickable(
                onClickLabel = "Open Bluetooth connection", role = Role.Button, onClick = connect
            ))
            .heightIn(min = if (compact) 36.dp else 48.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (connect == null) "Not connected — controls are inactive." else "Not connected",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (connect != null) {
            Spacer(Modifier.width(8.dp))
            Text("Connect", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}
