package com.bluepilot.remote.ui.gamepad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.model.gamepad.ArrowStyle
import com.bluepilot.remote.model.gamepad.ArrowDirection
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import kotlin.math.roundToInt

/**
 * ADV SECTION 1 — renderers for the advanced control library.
 * Each control type is its own reusable composable (modularity rule).
 * All follow the same press-tracking pattern as PressableControl:
 * awaitPointerEventScope press→release, one transition per physical press.
 */

/** Shared press-tracking modifier: exact press & release callbacks. */
private fun Modifier.pressTracking(
    key: String,
    onChange: (Boolean) -> Unit
): Modifier = pointerInput(key) {
    awaitPointerEventScope {
        var pressed = false
        while (true) {
            val event = awaitPointerEvent()
            val anyDown = event.changes.any { it.pressed }
            if (anyDown && !pressed) { pressed = true; onChange(true) }
            if (!anyDown && pressed) { pressed = false; onChange(false) }
        }
    }
}

/** ADV S1 — press-confirmation glow ring (input trust feedback). */
@Composable
internal fun Modifier.pressGlow(control: GamepadControlSpec, active: Boolean): Modifier {
    if (!control.pressGlow) return this
    val alpha by animateFloatAsState(
        targetValue = if (active) 0.9f else 0f,
        animationSpec = tween(if (active) 40 else 240),
        label = "pressGlow"
    )
    if (alpha <= 0.01f) return this
    return this.border(
        2.dp,
        Color.White.copy(alpha = alpha * 0.8f),
        controlShape(control.shape)
    )
}

// ----------------------------------------------------------------------
// TOGGLE — tap = latch ON (stays lit), tap again = OFF
// ----------------------------------------------------------------------

@Composable
internal fun ToggleControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var latched by remember(control.id) { mutableStateOf(false) }
    val shape = controlShape(control.shape)
    val base = controlColor(control)
    Box(
        modifier = modifier
            .background(
                if (latched) base.copy(alpha = 1f) else base.copy(alpha = base.alpha * 0.55f),
                shape
            )
            .border(
                2.dp,
                if (latched) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.2f),
                shape
            )
            .pressTracking(control.id) { pressed ->
                if (pressed) { latched = !latched; events.onToggle(control) }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = control.label.ifBlank { "⇋" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = if (latched) "ON" else "OFF",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

// ----------------------------------------------------------------------
// MULTI-TAP — single tap = A, double tap = B (VM resolves timing)
// ----------------------------------------------------------------------

@Composable
internal fun MultiTapControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var active by remember { mutableStateOf(false) }
    val shape = controlShape(control.shape)
    Box(
        modifier = modifier
            .pressGlow(control, active)
            .background(controlColor(control), shape)
            .pressTracking(control.id) { pressed ->
                active = pressed
                if (pressed) events.onMultiTap(control)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = control.label.ifBlank { "1|2" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "×2",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ----------------------------------------------------------------------
// SLIDE — fires when a finger slides INTO it (piano-key combos).
// Implemented per-control: fires on entry even if the press began on a
// neighboring control (each control has its own pointer scope, so entry
// is detected by the first event seen while a pointer is inside).
// ----------------------------------------------------------------------

@Composable
internal fun SlideControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var active by remember { mutableStateOf(false) }
    val shape = controlShape(control.shape)
    Box(
        modifier = modifier
            .pressGlow(control, active)
            .background(controlColor(control), shape)
            .pressTracking(control.id) { pressed ->
                active = pressed
                events.onButton(control, pressed)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = control.label.ifBlank { "♪" },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

// ----------------------------------------------------------------------
// RADIAL — long-press opens a wheel of up to 8 options
// ----------------------------------------------------------------------

@Composable
internal fun RadialControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var open by remember { mutableStateOf(false) }
    val shape = controlShape(control.shape)
    Box(
        modifier = modifier
            .background(controlColor(control), shape)
            .pointerInput(control.id) {
                detectTapGestures(
                    onTap = {
                        // Plain tap = primary action.
                        events.onRadialPick(control, control.buttonIndex)
                    },
                    onLongPress = { open = true }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = control.label.ifBlank { "◎" },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
    // Radial wheel overlay: options in a circle around the button.
    if (open) {
        androidx.compose.ui.window.Popup(
            alignment = Alignment.Center,
            onDismissRequest = { open = false }
        ) {
            val options = control.radialOptions.ifEmpty { listOf(0, 1, 2, 3) }
            BoxWithConstraints(
                modifier = Modifier
                    .size(220.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 78.dp.toPx() }
                options.forEachIndexed { i, opt ->
                    val angle = 2.0 * Math.PI * i / options.size - Math.PI / 2
                    val ox = (kotlin.math.cos(angle) * radiusPx).roundToInt()
                    val oy = (kotlin.math.sin(angle) * radiusPx).roundToInt()
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(ox, oy) }
                            .size(52.dp)
                            .background(controlColor(control).copy(alpha = 1f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .pointerInput(opt) {
                                detectTapGestures {
                                    events.onRadialPick(control, opt)
                                    open = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "B${opt + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = "✕",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .pointerInput("close") {
                            detectTapGestures { open = false }
                        }
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// ARROW — single independently-placeable directional button
// ----------------------------------------------------------------------

private fun arrowGlyph(direction: ArrowDirection): String = when (direction) {
    ArrowDirection.UP -> "▲"; ArrowDirection.DOWN -> "▼"
    ArrowDirection.LEFT -> "◀"; ArrowDirection.RIGHT -> "▶"
    ArrowDirection.UP_LEFT -> "◤"; ArrowDirection.UP_RIGHT -> "◥"
    ArrowDirection.DOWN_LEFT -> "◣"; ArrowDirection.DOWN_RIGHT -> "◢"
}

@Composable
internal fun ArrowControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var active by remember { mutableStateOf(false) }
    val base = controlColor(control)
    val shape = when (control.arrowStyle) {
        ArrowStyle.SQUARE -> RoundedCornerShape(4.dp)
        ArrowStyle.ROUNDED -> RoundedCornerShape(14.dp)
        ArrowStyle.ARROW -> RoundedCornerShape(50)
        ArrowStyle.DOT -> CircleShape
    }
    Box(
        modifier = modifier
            .pressGlow(control, active)
            .background(
                if (control.arrowStyle == ArrowStyle.DOT) base.copy(alpha = base.alpha * 0.6f) else base,
                shape
            )
            .pressTracking(control.id) { pressed ->
                active = pressed
                events.onArrow(control, pressed)
            },
        contentAlignment = Alignment.Center
    ) {
        if (control.arrowStyle == ArrowStyle.DOT) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.White.copy(alpha = if (active) 1f else 0.7f), CircleShape)
            )
        } else {
            Text(
                text = control.label.ifBlank { arrowGlyph(control.arrowDirection) },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

// ----------------------------------------------------------------------
// COMBO — bumper+trigger double zone (top half & bottom half)
// ----------------------------------------------------------------------

@Composable
internal fun ComboControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    val base = controlColor(control)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var topActive by remember { mutableStateOf(false) }
        var bottomActive by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pressGlow(control, topActive)
                .background(base, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .pressTracking(control.id + "-top") { pressed ->
                    topActive = pressed
                    events.onComboZone(control, 0, pressed)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (control.label.ifBlank { "L" }) + "1",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pressGlow(control, bottomActive)
                .background(
                    base.copy(
                        red = base.red * 0.75f,
                        green = base.green * 0.75f,
                        blue = base.blue * 0.75f
                    ),
                    RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                )
                .pressTracking(control.id + "-bottom") { pressed ->
                    bottomActive = pressed
                    events.onComboZone(control, 1, pressed)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (control.label.ifBlank { "L" }) + "2",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}
