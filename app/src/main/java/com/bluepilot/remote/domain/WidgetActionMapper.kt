package com.bluepilot.remote.domain

import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.model.widgets.WidgetAction

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
        is WidgetAction.RunMacro -> null   // handled by the macro engine (Module 7)
        is WidgetAction.None -> null
    }

    /** HID mask → MouseButton; unknown masks are rejected (defensive). */
    fun maskToButton(mask: Int): MouseButton? = when (mask) {
        0x01 -> MouseButton.LEFT
        0x02 -> MouseButton.RIGHT
        0x04 -> MouseButton.MIDDLE
        else -> null
    }
}
