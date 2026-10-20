package com.aeropad.remote.domain

import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.model.widgets.WidgetAction

/**
 * Translates stored [WidgetAction] bindings into runtime [HidAction]s.
 * Pure function — unit-tested. RunMacro resolves in Module 7 (returns null
 * here so the caller can route it to the macro engine).
 */
object WidgetActionMapper {

    fun toHidAction(action: WidgetAction): HidAction? = when (action) {
        is WidgetAction.KeyTap -> HidAction.KeyTap(action.key, action.modifiers)
        is WidgetAction.Media -> HidAction.MediaTap(action.usage)
        is WidgetAction.MouseClick -> maskToButton(action.buttonMask)?.let { HidAction.MouseClick(it) }
        is WidgetAction.TypeText ->
            if (action.text.isNotEmpty()) HidAction.TypeText(action.text.take(500)) else null
        is WidgetAction.ToggleNext -> toHidAction(action.first)
        is WidgetAction.RunMacro -> null   // handled by the macro engine (Module 7)
        is WidgetAction.None -> null
    }

    /**
     * A5 — Stateful resolution for [WidgetAction.ToggleNext]: alternates
     * between [WidgetAction.ToggleNext.first] and [WidgetAction.ToggleNext.second]
     * per [slotKey] inside [toggleStates]. Non-toggle actions return unchanged.
     */
    fun resolveToggle(
        slotKey: String,
        action: WidgetAction,
        toggleStates: MutableMap<String, Boolean>
    ): WidgetAction = when (action) {
        is WidgetAction.ToggleNext -> {
            val useSecond = toggleStates[slotKey] == true
            toggleStates[slotKey] = !useSecond
            if (useSecond) action.second else action.first
        }
        else -> action
    }

    /** HID mask → MouseButton; unknown masks are rejected (defensive). */
    fun maskToButton(mask: Int): MouseButton? = when (mask) {
        0x01 -> MouseButton.LEFT
        0x02 -> MouseButton.RIGHT
        0x04 -> MouseButton.MIDDLE
        else -> null
    }
}
