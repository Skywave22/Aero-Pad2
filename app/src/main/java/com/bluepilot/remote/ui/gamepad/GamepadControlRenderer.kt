package com.bluepilot.remote.ui.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.model.gamepad.ControlShape
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import kotlin.math.roundToInt

/**
 * SECTION 2 — Runtime renderer for custom gamepad layouts.
 *
 * Multi-touch: every control has its OWN pointerInput scope, so Compose
 * routes each finger to the control under it independently — moving the
 * stick while hammering buttons works out of the box.
 *
 * Press semantics for buttons/triggers: awaitPointerEventScope tracks
 * press → release precisely (no missed inputs, no double-fires: one
 * transition per physical press).
 */
// V2 PART C compression — @Stable: implementations are fixed callback
// bridges (ViewModel-backed); their behavior never changes for the same
// instance, so controls don't need to recompose when the parent does.
@androidx.compose.runtime.Stable
interface GamepadEvents {
    fun onButton(control: GamepadControlSpec, pressed: Boolean)
    fun onStick(control: GamepadControlSpec, x: Float, y: Float)
    /** dx/dy relative to pad center, -1..1; NaN never sent. */
    fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float)
    fun onDpadRelease(control: GamepadControlSpec)

    // ----- ADV SECTION 1 (defaults keep old callers compiling) -----
    /** Toggle-mode button tapped (latch flips in the VM). */
    fun onToggle(control: GamepadControlSpec) {}
    /** Multi-tap button tapped (VM resolves single vs double). */
    fun onMultiTap(control: GamepadControlSpec) {}
    /** Radial wheel option picked. */
    fun onRadialPick(control: GamepadControlSpec, index: Int) {}
    /** Independent arrow pressed/released. */
    fun onArrow(control: GamepadControlSpec, pressed: Boolean) {}
    /** Combo double-zone: zone 0 = top(bumper), 1 = bottom(trigger). */
    fun onComboZone(control: GamepadControlSpec, zone: Int, pressed: Boolean) {}
    /** Stick click (L3/R3) via long-press on the knob. */
    fun onStickClick(control: GamepadControlSpec, pressed: Boolean) {}
}

/**
 * Renders a full custom gamepad layout at fractional positions.
 * ADV S2 — [activeLayer]: layer-0 controls always render (dimmed while the
 * shift layer is active); layer-1 controls only render when activeLayer==1.
 * The shift control itself always renders highlighted.
 */
@Composable
fun GamepadCanvas(
    layout: GamepadLayoutSpec,
    events: GamepadEvents,
    modifier: Modifier = Modifier,
    activeLayer: Int = 0
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        layout.controls.forEach { control ->
            val isShiftKey = control.id == layout.shiftControlId
            val visible = control.layer == 0 || control.layer == activeLayer || isShiftKey
            if (!visible) return@forEach
            val dimmed = activeLayer == 1 && control.layer == 0 && !isShiftKey
            val frame = control.frame.sanitized()
            // V2 MATRIX 5 — TalkBack: every control announces its real
            // binding (naming-scheme aware). The canvas was silent before.
            val a11yLabel = com.bluepilot.remote.domain.A11y.describe(control, layout.naming)
            RenderGamepadControl(
                control = control,
                events = events,
                modifier = Modifier
                    .offset(x = w * frame.x, y = h * frame.y)
                    .size(width = w * frame.w, height = h * frame.h)
                    .graphicsLayer { if (dimmed) alpha = 0.35f }
                    .semantics { contentDescription = a11yLabel }
            )
        }
    }
}

@Composable
fun RenderGamepadControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier = Modifier
) {
    when (control.type) {
        GamepadControlType.BUTTON, GamepadControlType.TRIGGER ->
            when (control.buttonMode) {
                com.bluepilot.remote.model.gamepad.ButtonMode.TOGGLE ->
                    ToggleControl(control, events, modifier)
                com.bluepilot.remote.model.gamepad.ButtonMode.MULTI_TAP ->
                    MultiTapControl(control, events, modifier)
                com.bluepilot.remote.model.gamepad.ButtonMode.SLIDE ->
                    SlideControl(control, events, modifier)
                com.bluepilot.remote.model.gamepad.ButtonMode.RADIAL ->
                    RadialControl(control, events, modifier)
                else -> PressableControl(control, events, modifier)
            }
        GamepadControlType.STICK -> StickControl(control, events, modifier)
        GamepadControlType.DPAD -> DpadControl(control, events, modifier)
        GamepadControlType.ARROW -> ArrowControl(control, events, modifier)
        GamepadControlType.COMBO -> ComboControl(control, events, modifier)
    }
}

// ----------------------------------------------------------------------

internal fun controlShape(shape: ControlShape): Shape = when (shape) {
    ControlShape.CIRCLE -> CircleShape
    ControlShape.ROUNDED -> RoundedCornerShape(18.dp)
    ControlShape.SQUARE -> RoundedCornerShape(4.dp)
}

/** V2 MATRIX 1 b4 — per-corner radius override (TL,TR,BR,BL); falls back
 *  to the uniform [controlShape] when unset or CIRCLE. */
internal fun controlShapeFor(control: GamepadControlSpec): Shape =
    if (control.cornerRadii.size == 4 && control.shape != ControlShape.CIRCLE)
        RoundedCornerShape(
            topStart = control.cornerRadii[0].dp,
            topEnd = control.cornerRadii[1].dp,
            bottomEnd = control.cornerRadii[2].dp,
            bottomStart = control.cornerRadii[3].dp
        )
    else controlShape(control.shape)

internal fun controlColor(control: GamepadControlSpec): Color =
    control.color.toComposeColor()
        .copy(alpha = control.opacity.coerceIn(0.15f, 1f))

/** Button/trigger: exact press & release tracking, visual pressed state. */
@Composable
private fun PressableControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    // V2 MATRIX 1 b2 — pressed-state corner radius override: shape swaps
    // while held (CIRCLE stays circle; ROUNDED/SQUARE honor the override).
    val shape = if (pressed && control.pressedStyle.cornerRadius != null &&
        control.shape != ControlShape.CIRCLE
    ) RoundedCornerShape(control.pressedStyle.cornerRadius.dp)
    else controlShapeFor(control)   // V2 b4 — honors per-corner radii
    // V2 MATRIX 1 — pressed-state color override (null = original tint).
    val base = if (pressed && control.pressedStyle.color != null)
        control.pressedStyle.color.toComposeColor()
            .copy(alpha = control.opacity.coerceIn(0.15f, 1f))
    else controlColor(control)
    // SECTION 3D — raised button look: sits proud (slight highlight), and on
    // press sinks + tips back. State-driven graphicsLayer: applied on the
    // already-tracked `pressed` flag, so the input path gains ZERO latency.
    val reduceMotion = com.bluepilot.remote.ui.components.LocalReduceMotion.current
    // V2 MATRIX 1 — animation personality: spring-animated press progress
    // (physics from MotionTokens); read in the draw phase only.
    val tokens = remember(control.motionStyle) { MotionTokens.forStyle(control.motionStyle) }
    val pressT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tokens.press else tokens.release,
        label = "pressT"
    )
    val pressScale = control.pressedStyle.scale ?: tokens.pressScale
    // V2 MATRIX 1 b6 — idle breathing glow: slow alpha pulse on a ring.
    // Draw-phase only; disabled under Reduce Motion / while pressed.
    val breatheAlpha = if (control.breathingGlow && !reduceMotion) {
        val breathe = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathe")
        breathe.animateFloat(
            initialValue = 0.10f, targetValue = 0.45f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1400),
                androidx.compose.animation.core.RepeatMode.Reverse
            ), label = "breatheA"
        )
    } else null

    Box(
        modifier = modifier
            .graphicsLayer {
                // V2 MATRIX 1 — personality-driven press: spring progress
                // scales/tilts continuously (not a binary snap). Pure GPU,
                // zero input latency (visual only).
                if (reduceMotion) {
                    val s = 1f - (1f - 0.96f) * pressT
                    scaleX = s; scaleY = s
                } else {
                    cameraDistance = 8f * density
                    rotationX = -5f * pressT
                    val s = 1f - (1f - pressScale) * pressT
                    scaleX = s; scaleY = s
                    translationY = size.height * 0.02f * pressT
                }
            }
            .shadow(if (pressed) 1.dp else 6.dp, shape, clip = false)
            .background(
                // V2 PART B — dynamic lighting: the highlight tracks the real
                // gravity tilt like a fixed world light (LocalDeviceTilt is
                // (0,0) under FLAT/reduce-motion/no-sensor → this collapses
                // to the exact previous hardcoded 0.35/0.30 center).
                run {
                    val (tx, ty) = com.bluepilot.remote.ui.components.LocalDeviceTilt.current
                    val (lx, ly) = com.bluepilot.remote.domain.TiltMath.lightCenter(tx, ty)
                    Brush.radialGradient(
                        colors = listOf(
                            base.copy(alpha = 1f),
                            base.copy(
                                red = base.red * 0.65f,
                                green = base.green * 0.65f,
                                blue = base.blue * 0.65f
                            )
                        ),
                        center = androidx.compose.ui.geometry.Offset(lx * 100f, ly * 100f),
                        radius = 140f
                    )
                },
                shape
            )
            // V2 MATRIX 1 b6 — breathing glow ring (idle only, not pressed).
            .then(
                if (breatheAlpha != null && !pressed)
                    Modifier.border(2.dp, Color.White.copy(alpha = breatheAlpha.value), shape)
                else Modifier
            )
            // V2 MATRIX 1 b2 — pressed-state border override (width+color).
            .then(
                if (pressed && (control.pressedStyle.borderWidth ?: 0) > 0)
                    Modifier.border(
                        control.pressedStyle.borderWidth!!.dp,
                        (control.pressedStyle.borderColor ?: 0xFFFFFFFF)
                            .toComposeColor(),
                        shape
                    )
                else Modifier
            )
            .pointerInput(control.id, control.touchSlopDp) {
                // V2 MATRIX 1 finale — touch slop: slide-off cancel distance.
                // 0 = unlimited (old behavior: press holds until lift even if
                // the finger wanders). >0 = release fires once the finger
                // travels that many dp beyond the control's bounds.
                val slopPx = if (control.touchSlopDp > 0)
                    control.touchSlopDp.dp.toPx() else Float.MAX_VALUE
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        // V2 MATRIX 1 b4 — multi-touch rejection: ignore the
                        // press when too many pointers are on this control
                        // (palm/grip mis-touch guard). 0 = no limit.
                        val pointerCount = down.changes.count { it.pressed }
                        if (control.maxPointers > 0 && pointerCount > control.maxPointers) {
                            continue
                        }
                        if (down.changes.any { it.pressed } && !pressed) {
                            pressed = true
                            events.onButton(control, true)
                            // Hold until every pointer on this control lifts,
                            // or (slop set) the finger slides too far away.
                            var slopCancelled = false
                            do {
                                val event = awaitPointerEvent()
                                if (!slopCancelled && control.touchSlopDp > 0) {
                                    val p = event.changes.firstOrNull { it.pressed }?.position
                                    if (p != null && com.bluepilot.remote.domain.TouchSlop.isOutside(
                                            p.x, p.y,
                                            size.width.toFloat(), size.height.toFloat(),
                                            slopPx
                                        )
                                    ) {
                                        // Cancel the press NOW (no stuck HID
                                        // button), keep draining until lift so
                                        // we never instantly re-press.
                                        slopCancelled = true
                                        pressed = false
                                        events.onButton(control, false)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            if (!slopCancelled) {
                                pressed = false
                                events.onButton(control, false)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // V2 MATRIX 1 b2 — cooldown ring: sweeps 360° over cooldownMs after
        // each press. VISUAL ONLY (input never blocked — honest timing aid).
        if (control.cooldownMs > 0) {
            var cooldownStart by remember { mutableStateOf(0L) }
            LaunchedEffect(pressed) { if (pressed) cooldownStart = System.currentTimeMillis() }
            if (cooldownStart > 0L) {
                val progress by produceState(0f, cooldownStart) {
                    while (true) {
                        val t = (System.currentTimeMillis() - cooldownStart).toFloat() /
                            control.cooldownMs
                        value = t.coerceIn(0f, 1f)
                        if (t >= 1f) break
                        kotlinx.coroutines.delay(33)
                    }
                }
                if (progress < 1f) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.55f),
                            startAngle = -90f,
                            sweepAngle = 360f * (1f - progress),
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (control.icon.isNotBlank()) {
                Text(text = control.icon, style = MaterialTheme.typography.titleMedium)
            }
            if (control.label.isNotBlank()) {
                Text(
                    text = control.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Analog stick: knob follows finger inside the gate; snaps to center
 * (or stays — ADV S1 sticky mode). Supports square gate, 8-direction
 * guide overlay, outer-range ring and stick-click (long-press = L3/R3).
 */
@Composable
private fun StickControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val baseMin = if (maxWidth < maxHeight) maxWidth else maxHeight
        val knobSize = baseMin * 0.42f
        val density = LocalDensity.current
        val squareGate = control.stickGate == com.bluepilot.remote.model.gamepad.StickGate.SQUARE
        val radiusPx = (with(density) { ((baseMin - knobSize) / 2f).toPx() } *
            control.outerRange.coerceIn(0.5f, 1f)).coerceAtLeast(1f)
        var knob by remember { mutableStateOf(Offset.Zero) }
        val base = controlColor(control)
        val gateShape = if (squareGate) RoundedCornerShape(12.dp) else CircleShape

        // ADV S1 — stick click: long-press (>350ms) without movement = L3/R3.
        var clickJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val clickScope = androidx.compose.runtime.rememberCoroutineScope()
        var clickSent by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(baseMin)
                .background(base.copy(alpha = base.alpha * 0.4f), gateShape)
                // ADV S1 — 8-direction guide-line overlay (toggleable).
                .then(if (control.stickGuides) Modifier.drawBehind {
                    val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    for (i in 0 until 8) {
                        val a = Math.PI / 4 * i
                        drawLine(
                            color = Color.White.copy(alpha = if (i % 2 == 0) 0.30f else 0.15f),
                            start = c,
                            end = androidx.compose.ui.geometry.Offset(
                                c.x + (kotlin.math.cos(a) * r).toFloat(),
                                c.y + (kotlin.math.sin(a) * r).toFloat()
                            ),
                            strokeWidth = 1.5f
                        )
                    }
                    // Outer-range boundary ring.
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = radiusPx,
                        center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                } else Modifier)
                .pointerInput(control.id) {
                    detectDragGestures(
                        onDragStart = {
                            if (control.stickClickIndex >= 0) {
                                clickSent = false
                                clickJob?.cancel()
                                clickJob = clickScope.launch {
                                    kotlinx.coroutines.delay(350)
                                    // Still near center after 350ms = click intent.
                                    if (knob.getDistance() < radiusPx * 0.18f) {
                                        clickSent = true
                                        events.onStickClick(control, true)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            clickJob?.cancel()
                            if (clickSent) { events.onStickClick(control, false); clickSent = false }
                            if (!control.stickSticky) {
                                knob = Offset.Zero
                                events.onStick(control, 0f, 0f)
                            }
                        },
                        onDragCancel = {
                            clickJob?.cancel()
                            if (clickSent) { events.onStickClick(control, false); clickSent = false }
                            if (!control.stickSticky) {
                                knob = Offset.Zero
                                events.onStick(control, 0f, 0f)
                            }
                        }
                    ) { change, drag ->
                        change.consume()
                        val next = knob + drag
                        // Gate clamp: square = per-axis, circle = radial.
                        val clamped = if (squareGate) {
                            Offset(
                                next.x.coerceIn(-radiusPx, radiusPx),
                                next.y.coerceIn(-radiusPx, radiusPx)
                            )
                        } else {
                            val dist = next.getDistance()
                            if (dist > radiusPx) next * (radiusPx / dist) else next
                        }
                        knob = clamped
                        events.onStick(
                            control,
                            (clamped.x / radiusPx).coerceIn(-1f, 1f),
                            (clamped.y / radiusPx).coerceIn(-1f, 1f)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                    .size(knobSize)
                    .graphicsLayer {
                        // SECTION 3 - knob tilts toward drag direction
                        cameraDistance = 8f * density.density
                        rotationY = (knob.x / radiusPx).coerceIn(-1f, 1f) * 18f
                        rotationX = -(knob.y / radiusPx).coerceIn(-1f, 1f) * 18f
                    }
                    .shadow(4.dp, CircleShape, clip = false)
                    .background(
                        // V2 PART B b2 — knob shares the fixed world light.
                        // ZERO CHANGE guard: at (0,0) tilt we return the
                        // EXACT legacy brush (default centered gradient);
                        // only real tilt swaps in the world-light version.
                        run {
                            val (tx, ty) = com.bluepilot.remote.ui.components.LocalDeviceTilt.current
                            val darker = base.copy(
                                red = base.red * 0.6f,
                                green = base.green * 0.6f,
                                blue = base.blue * 0.6f
                            )
                            if (tx == 0f && ty == 0f) {
                                Brush.radialGradient(listOf(base.copy(alpha = 1f), darker))
                            } else {
                                val (lx, ly) = com.bluepilot.remote.domain.TiltMath.lightCenter(tx, ty)
                                Brush.radialGradient(
                                    listOf(base.copy(alpha = 1f), darker),
                                    center = androidx.compose.ui.geometry.Offset(lx * 100f, ly * 100f),
                                    radius = 120f
                                )
                            }
                        },
                        CircleShape
                    )
            )
        }
    }
}

/** D-pad: touch position → direction; release → neutral. */
@Composable
private fun DpadControl(
    control: GamepadControlSpec,
    events: GamepadEvents,
    modifier: Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val base = controlColor(control)
        val sizePx = with(LocalDensity.current) {
            (if (maxWidth < maxHeight) maxWidth else maxHeight).toPx()
        }.coerceAtLeast(1f)
        // SECTION 3 - 3D dpad: cross tilts toward the pressed direction
        var tiltX by remember { mutableStateOf(0f) }
        var tiltY by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    cameraDistance = 10f * density
                    rotationY = tiltX * 10f
                    rotationX = -tiltY * 10f
                }
                .shadow(if (tiltX != 0f || tiltY != 0f) 2.dp else 5.dp, RoundedCornerShape(20.dp), clip = false)
                .background(
                    // V2 PART B b2 — device-tilt lighting on the pad face.
                    // (0,0) tilt = the EXACT legacy flat color; real tilt
                    // adds a soft directional sheen from the world light.
                    run {
                        val (dtx, dty) = com.bluepilot.remote.ui.components.LocalDeviceTilt.current
                        if (dtx == 0f && dty == 0f) {
                            androidx.compose.ui.graphics.SolidColor(base)
                        } else {
                            val (lx, ly) = com.bluepilot.remote.domain.TiltMath.lightCenter(dtx, dty)
                            Brush.radialGradient(
                                listOf(
                                    base.copy(
                                        red = (base.red * 1.15f).coerceAtMost(1f),
                                        green = (base.green * 1.15f).coerceAtMost(1f),
                                        blue = (base.blue * 1.15f).coerceAtMost(1f)
                                    ),
                                    base
                                ),
                                center = androidx.compose.ui.geometry.Offset(lx * sizePx, ly * sizePx),
                                radius = sizePx * 0.9f
                            )
                        }
                    },
                    RoundedCornerShape(20.dp)
                )
                .pointerInput(control.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                val dx = ((change.position.x - size.width / 2f) / (sizePx / 2f)).coerceIn(-1f, 1f)
                                val dy = ((change.position.y - size.height / 2f) / (sizePx / 2f)).coerceIn(-1f, 1f)
                                tiltX = dx; tiltY = dy
                                events.onDpadTouch(control, dx, dy)
                            } else {
                                tiltX = 0f; tiltY = 0f
                                events.onDpadRelease(control)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (control.eightWay) "✚" else "✛",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
