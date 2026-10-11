package com.bluepilot.remote.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetType
import com.bluepilot.remote.ui.components.cornerBrackets
import com.bluepilot.remote.ui.theme.LocalAppTheme
import kotlin.math.roundToInt

/**
 * The generic widget renderer — draws ANY [WidgetSpec] from pure data.
 *
 * Interaction callbacks are delivered through [WidgetEvents]; the renderer
 * itself knows nothing about HID. Module 6's editor reuses exactly this
 * renderer with editing gestures layered on top.
 */
interface WidgetEvents {
    fun onPrimary(widget: WidgetSpec)                    // tap / click
    fun onSecondary(widget: WidgetSpec)                  // long-press etc.
    fun onTrackpadStart()
    fun onTrackpadDelta(dx: Float, dy: Float)
    fun onScrollDelta(dy: Float)
    fun onJoystick(x: Float, y: Float)                   // -1..1
    fun onDpad(dirX: Int, dirY: Int)                     // -1/0/1 each axis
    /** GESTURE_ZONE: classified swipe released. */
    fun onSwipe(widget: WidgetSpec, direction: com.bluepilot.remote.domain.SwipeDirection) {}
    /** GESTURE_ZONE: two-finger tap (multi-touch gesture). */
    fun onTwoFingerTap(widget: WidgetSpec) {}
}

/** Renders a full layout: canvas + all visible widgets at fractional frames. */
@Composable
fun LayoutCanvas(
    layout: LayoutSpec,
    events: WidgetEvents,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasW = maxWidth
        val canvasH = maxHeight
        layout.widgets.filter { it.style.visible }.forEach { widget ->
            val frame = widget.frame.sanitized()
            // SECTION 4 — accessibility: interactive widgets never render
            // below the 48dp Material touch-target floor, even if the user
            // resized them smaller in the editor.
            val minTouch = com.bluepilot.remote.ui.theme.Dimens.TOUCH_TARGET
            val w = (canvasW * frame.w).coerceAtLeast(minTouch)
            val h = (canvasH * frame.h).coerceAtLeast(minTouch)
            RenderWidget(
                widget = widget,
                events = events,
                modifier = Modifier
                    .offset(x = canvasW * frame.x, y = canvasH * frame.y)
                    .size(width = w, height = h)
            )
        }
    }
}

/** Renders one widget according to its type + style. */
@Composable
fun RenderWidget(
    widget: WidgetSpec,
    events: WidgetEvents,
    modifier: Modifier = Modifier
) {
    val style = widget.style.sanitized()
    val bg = style.backgroundColor.toComposeColor().copy(alpha = style.opacity)
    val fg = style.contentColor.toComposeColor()
    val shape = RoundedCornerShape(style.cornerRadius.dp)

    val themeSpec = LocalAppTheme.current
    val cornerBracketsModifier = if (themeSpec.monoFont) {
        Modifier.cornerBrackets(
            color = themeSpec.glowColor ?: fg,
            bracketLength = 10.dp,
            strokeWidth = 2.dp
        )
    } else Modifier

    Surface(
        modifier = modifier.then(cornerBracketsModifier),
        color = bg,
        contentColor = fg,
        shape = shape,
        shadowElevation = style.elevation.dp
    ) {
        when (widget.type) {
            WidgetType.BUTTON -> ButtonBody(widget, events, fg)
            WidgetType.TRACKPAD -> TrackpadBody(widget, events, fg)
            WidgetType.SCROLL_STRIP -> ScrollBody(widget, events, fg)
            WidgetType.JOYSTICK -> JoystickBody(events, fg)
            WidgetType.SLIDER -> SliderBody(widget, events, fg)
            WidgetType.DPAD -> DpadBody(events, fg)
            WidgetType.GESTURE_ZONE -> GestureZoneBody(widget, events, fg)
        }
    }
}

// ----------------------------------------------------------------------

@Composable
private fun WidgetLabel(icon: String, label: String, fontSize: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon.isNotBlank()) {
            Text(text = icon, fontSize = (fontSize + 6).sp, textAlign = TextAlign.Center)
        }
        if (label.isNotBlank()) {
            Text(text = label, fontSize = fontSize.sp, color = color, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ButtonBody(widget: WidgetSpec, events: WidgetEvents, fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(widget.id) {
                detectTapGestures(
                    onTap = { events.onPrimary(widget) },
                    onLongPress = { events.onSecondary(widget) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        WidgetLabel(widget.style.icon, widget.style.label, widget.style.fontSize, fg)
    }
}

@Composable
private fun TrackpadBody(widget: WidgetSpec, events: WidgetEvents, fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(widget.id + "-tap") {
                detectTapGestures(
                    onTap = { events.onPrimary(widget) },
                    onLongPress = { events.onSecondary(widget) }
                )
            }
            .pointerInput(widget.id + "-drag") {
                detectDragGestures(onDragStart = { events.onTrackpadStart() }) { change, drag ->
                    change.consume()
                    events.onTrackpadDelta(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        WidgetLabel(widget.style.icon, widget.style.label, widget.style.fontSize, fg.copy(alpha = 0.55f))
    }
}

@Composable
private fun ScrollBody(widget: WidgetSpec, events: WidgetEvents, fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(widget.id) {
                detectDragGestures { change, drag ->
                    change.consume()
                    events.onScrollDelta(drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        WidgetLabel(widget.style.icon, widget.style.label.ifBlank { "⇅" }, widget.style.fontSize, fg.copy(alpha = 0.55f))
    }
}

@Composable
private fun JoystickBody(events: WidgetEvents, fg: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val knobFraction = 0.38f
        val density = LocalDensity.current
        val baseMin: Dp = if (maxWidth < maxHeight) maxWidth else maxHeight
        val radiusPx = with(density) { (baseMin * (1f - knobFraction) / 2f).toPx() }
        var knob by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .size(baseMin)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { knob = Offset.Zero; events.onJoystick(0f, 0f) },
                        onDragCancel = { knob = Offset.Zero; events.onJoystick(0f, 0f) }
                    ) { change, drag ->
                        change.consume()
                        val next = knob + drag
                        val clamped = if (next.getDistance() > radiusPx && next.getDistance() > 0f) {
                            next * (radiusPx / next.getDistance())
                        } else next
                        knob = clamped
                        events.onJoystick(
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
                    .size(baseMin * knobFraction)
                    .background(fg.copy(alpha = 0.8f), CircleShape)
            )
        }
    }
}

@Composable
private fun SliderBody(widget: WidgetSpec, events: WidgetEvents, fg: Color) {
    // Vertical slider: accumulated drag fires primary (up) / secondary (down)
    // every stepPx pixels — good for volume/scroll-style repeat actions.
    var accum by remember { mutableStateOf(0f) }
    val stepPx = 48f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(widget.id) {
                detectDragGestures { change, drag ->
                    change.consume()
                    accum += drag.y
                    while (accum <= -stepPx) { accum += stepPx; events.onPrimary(widget) }
                    while (accum >= stepPx) { accum -= stepPx; events.onSecondary(widget) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        WidgetLabel(widget.style.icon, widget.style.label.ifBlank { "▲▼" }, widget.style.fontSize, fg.copy(alpha = 0.7f))
    }
}

@Composable
private fun DpadBody(events: WidgetEvents, fg: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = (if (maxWidth < maxHeight) maxWidth else maxHeight) / 3
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DpadArrow("▲", cell, fg) { events.onDpad(0, -1) }
            androidx.compose.foundation.layout.Row {
                DpadArrow("◀", cell, fg) { events.onDpad(-1, 0) }
                Box(Modifier.size(cell))
                DpadArrow("▶", cell, fg) { events.onDpad(1, 0) }
            }
            DpadArrow("▼", cell, fg) { events.onDpad(0, 1) }
        }
    }
}

@Composable
private fun DpadArrow(glyph: String, cell: Dp, fg: Color, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(cell)
            .pointerInput(glyph) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = fg)
    }
}

/**
 * SECTION 3A — Gesture zone.
 * One finger: accumulate drag → classify swipe on release (up/down/left/right).
 * Two fingers down together: two-finger tap. All bindable per direction.
 * Visual: 4 glowing directional arrows drawn via canvas for a premium HUD look.
 */
@Composable
private fun GestureZoneBody(widget: WidgetSpec, events: WidgetEvents, fg: Color) {
    val themeSpec = LocalAppTheme.current
    // Glow color — use theme glowColor if available, else use fg
    val arrowColor = (themeSpec.glowColor ?: fg).copy(alpha = 0.75f)
    val arrowGlow  = arrowColor.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(widget.id + "-multitouch") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            events.onTwoFingerTap(widget)
                            do {
                                val e = awaitPointerEvent()
                            } while (e.changes.any { it.pressed })
                        }
                    }
                }
            }
            .pointerInput(widget.id + "-swipe") {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { totalX = 0f; totalY = 0f },
                    onDragEnd = {
                        val dir = com.bluepilot.remote.domain.SwipeGestures.classify(totalX, totalY)
                        if (dir != com.bluepilot.remote.domain.SwipeDirection.NONE) {
                            events.onSwipe(widget, dir)
                        }
                    }
                ) { change, drag ->
                    change.consume()
                    totalX += drag.x
                    totalY += drag.y
                }
            }
            .pointerInput(widget.id + "-tap") {
                detectTapGestures(onTap = { events.onPrimary(widget) })
            },
        contentAlignment = Alignment.Center
    ) {
        // Draw 4 glowing directional arrows using canvas
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val arrowSize = minOf(w, h) * 0.14f
            val tipOffset = minOf(w, h) * 0.28f
            val strokePx = 3.5f

            // Draw each arrow: UP, DOWN, LEFT, RIGHT
            drawGlowArrow(
                direction = ArrowDir.UP,
                center = Offset(w / 2f, h / 2f - tipOffset),
                arrowSize = arrowSize,
                color = arrowColor,
                glowColor = arrowGlow,
                strokeWidth = strokePx
            )
            drawGlowArrow(
                direction = ArrowDir.DOWN,
                center = Offset(w / 2f, h / 2f + tipOffset),
                arrowSize = arrowSize,
                color = arrowColor,
                glowColor = arrowGlow,
                strokeWidth = strokePx
            )
            drawGlowArrow(
                direction = ArrowDir.LEFT,
                center = Offset(w / 2f - tipOffset, h / 2f),
                arrowSize = arrowSize,
                color = arrowColor,
                glowColor = arrowGlow,
                strokeWidth = strokePx
            )
            drawGlowArrow(
                direction = ArrowDir.RIGHT,
                center = Offset(w / 2f + tipOffset, h / 2f),
                arrowSize = arrowSize,
                color = arrowColor,
                glowColor = arrowGlow,
                strokeWidth = strokePx
            )
        }
    }
}

private enum class ArrowDir { UP, DOWN, LEFT, RIGHT }

/**
 * Draws a chevron arrow pointing [direction] at [center] with a soft glow halo.
 */
private fun DrawScope.drawGlowArrow(
    direction: ArrowDir,
    center: Offset,
    arrowSize: Float,
    color: Color,
    glowColor: Color,
    strokeWidth: Float
) {
    val half = arrowSize / 2f
    // Build chevron path relative to a UP-pointing arrow, then rotate
    val path = Path().apply {
        when (direction) {
            ArrowDir.UP -> {
                moveTo(center.x - half, center.y + half * 0.6f)
                lineTo(center.x, center.y - half * 0.6f)
                lineTo(center.x + half, center.y + half * 0.6f)
            }
            ArrowDir.DOWN -> {
                moveTo(center.x - half, center.y - half * 0.6f)
                lineTo(center.x, center.y + half * 0.6f)
                lineTo(center.x + half, center.y - half * 0.6f)
            }
            ArrowDir.LEFT -> {
                moveTo(center.x + half * 0.6f, center.y - half)
                lineTo(center.x - half * 0.6f, center.y)
                lineTo(center.x + half * 0.6f, center.y + half)
            }
            ArrowDir.RIGHT -> {
                moveTo(center.x - half * 0.6f, center.y - half)
                lineTo(center.x + half * 0.6f, center.y)
                lineTo(center.x - half * 0.6f, center.y + half)
            }
        }
    }

    // Glow pass (thicker, transparent)
    drawPath(
        path = path,
        color = glowColor,
        style = Stroke(width = strokeWidth * 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
    )
    // Solid arrow
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
    )
}
