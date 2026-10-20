package com.aeropad.remote.domain

import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.widgets.WidgetAction
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.model.widgets.WidgetType

/**
 * Shared "use mode" behavior for data-driven widgets.
 *
 * Both the Layouts player and the editor's Preview mode delegate here, so
 * widget behavior is identical everywhere and tested once. Holds per-gesture
 * trackpad smoothing state; create one instance per screen.
 *
 * [runMacro] is invoked for WidgetAction.RunMacro bindings (macro engine).
 */
class WidgetInteractor(
    private val send: (HidAction) -> Unit,
    private val mouseSettings: () -> MouseSettings,
    private val runMacro: (Long) -> Unit = {}
) {

    // A5 — per-widget toggle state for WidgetAction.ToggleNext bindings.
    private val toggleStates = mutableMapOf<String, Boolean>()

    fun primary(widget: WidgetSpec) {
        dispatch("${widget.id}:primary", widget.action)
    }

    fun secondary(widget: WidgetSpec) {
        val resolved = WidgetActionMapper.resolveToggle("${widget.id}:secondary", widget.secondaryAction, toggleStates)
        if (resolved is WidgetAction.RunMacro) {
            runMacro(resolved.macroId)
            return
        }
        val bound = WidgetActionMapper.toHidAction(resolved)
        when {
            bound != null -> send(bound)
            // Sensible default: long-press on a trackpad = right click.
            widget.type == WidgetType.TRACKPAD -> send(HidAction.MouseClick(MouseButton.RIGHT))
        }
    }

    // OPTIMIZATION: shared TrackpadEngine replaces the copy-pasted
    // smoothing/carry pipeline (was ~35 lines here).
    private val trackpad = TrackpadEngine(mouseSettings)

    fun trackpadStart() = trackpad.startGesture()

    fun trackpadDelta(dx: Float, dy: Float) {
        val (ix, iy) = trackpad.move(dx, dy)
        if (ix != 0 || iy != 0) send(HidAction.MouseMove(ix, iy))
    }

    fun scrollDelta(dy: Float) {
        trackpad.scroll(dy).takeIf { it != 0 }?.let { send(HidAction.MouseScroll(it)) }
    }

    /** Joystick in custom layouts drives the mouse pointer. */
    fun joystick(x: Float, y: Float) {
        val speed = 14f
        val dx = (x * speed).toInt(); val dy = (y * speed).toInt()
        if (dx != 0 || dy != 0) send(HidAction.MouseMove(dx, dy))
    }

    /** GESTURE_ZONE swipe → its per-direction binding (macro-aware). */
    fun swipe(widget: WidgetSpec, direction: SwipeDirection) {
        val action = when (direction) {
            SwipeDirection.UP -> widget.swipeUp
            SwipeDirection.DOWN -> widget.swipeDown
            SwipeDirection.LEFT -> widget.swipeLeft
            SwipeDirection.RIGHT -> widget.swipeRight
            SwipeDirection.NONE -> return
        }
        dispatch("${widget.id}:swipe:${direction.name}", action)
    }

    /** GESTURE_ZONE two-finger tap binding. */
    fun twoFingerTap(widget: WidgetSpec) = dispatch("${widget.id}:twoFinger", widget.twoFingerTap)

    /** Shared dispatch honoring ToggleNext and RunMacro bindings. */
    private fun dispatch(slotKey: String, action: com.aeropad.remote.model.widgets.WidgetAction) {
        val resolved = WidgetActionMapper.resolveToggle(slotKey, action, toggleStates)
        if (resolved is com.aeropad.remote.model.widgets.WidgetAction.RunMacro) {
            runMacro(resolved.macroId)
            return
        }
        WidgetActionMapper.toHidAction(resolved)?.let(send)
    }

    fun dpad(dirX: Int, dirY: Int) {
        when {
            dirY < 0 -> send(HidAction.KeyTap(HidKeys.ARROW_UP))
            dirY > 0 -> send(HidAction.KeyTap(HidKeys.ARROW_DOWN))
            dirX < 0 -> send(HidAction.KeyTap(HidKeys.ARROW_LEFT))
            dirX > 0 -> send(HidAction.KeyTap(HidKeys.ARROW_RIGHT))
        }
    }
}
