package com.bluepilot.remote.domain

import com.bluepilot.remote.model.keyboard.DefaultKeyboards
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec

/**
 * V2 PART C — Universal Property System, Phase 1: the honest inventory.
 *
 * Counts every user-customizable property the app really exposes, computed
 * from the REAL model shapes and REAL instance caps — no invented numbers.
 *
 * COUNTING RULES (verified by reflection in PropertyCatalogTest — if a
 * model gains/loses fields without updating this catalog, the build goes
 * red):
 *  1. A property = one primary-constructor field of a persisted spec that
 *     the user can set through the UI or stored JSON.
 *  2. Identity fields (id) are EXCLUDED — not customization.
 *  3. Nested style objects (PressedStyle, WidgetFrame, WidgetStyle) are
 *     expanded into their fields; the container field itself is not
 *     double-counted.
 *  4. Collection containers (controls/rows/widgets lists) are excluded as
 *     fields; their ELEMENTS are counted as instances instead.
 *  5. Instance counts use the app's real enforced caps (MAX_CONTROLS = 32,
 *     MAX_WIDGETS = 60, STEPS_MAX = 64) or the real default-board size —
 *     one fully-populated workspace: one gamepad layout, the keyboard
 *     board, one widget deck, one macro, global settings.
 *  6. Macro steps count the fields of the LARGEST step type (KeyHold = 3)
 *     per slot — not the sum of all types (a slot holds ONE step).
 *  7. Built-in themes are selectable, not editable — EXCLUDED (picking
 *     from a list is not a customizable property).
 */
object PropertyCatalog {

    data class Group(
        val name: String,
        /** Customizable fields per instance (see counting rules). */
        val perInstance: Int,
        /** Real enforced cap / real default-board size. */
        val instances: Int,
        val note: String
    ) {
        val total: Int get() = perInstance * instances
    }

    // Per-instance counts — verified against the real constructors by
    // reflection in PropertyCatalogTest.

    /** GamepadControlSpec: 50 ctor fields − id − pressedStyle container
     *  + 5 PressedStyle fields = 53. */
    const val GAMEPAD_CONTROL_PROPS = 53

    /** GamepadLayoutSpec: 8 ctor fields − controls list = 7
     *  (flickMap added in the M3/M5 deferred-items batch). */
    const val GAMEPAD_LAYOUT_PROPS = 7

    /** KeySpec: 11 ctor fields − id = 10. */
    const val KEYBOARD_KEY_PROPS = 10

    /** WidgetSpec: 12 ctor fields − id − frame − style + 4 WidgetFrame
     *  + 9 WidgetStyle = 22. */
    const val WIDGET_PROPS = 22

    /** LayoutSpec (widgets): 5 ctor fields − widgets list = 4. */
    const val WIDGET_LAYOUT_PROPS = 4

    /** Largest MacroStep type (KeyHold: key, modifiers, ms) = 3 fields
     *  per step slot (rule 6). */
    const val MACRO_STEP_PROPS = 3

    /** AppSettings 24 + MouseSettings 6 + KeyboardSettings 1
     *  + GamepadSettings 4 = 35 global toggles/values
     *  (spokenAlerts added in the M5 b2 batch). */
    const val SETTINGS_PROPS = 35

    /** Keys on the real default board + favorites cap + FN overlay cap. */
    fun keyboardKeyInstances(): Int {
        val board = DefaultKeyboards.fullQwerty()
        return board.rows.sumOf { it.size } +
            KeyboardLayoutSpec.FAVORITES_MAX +
            KeyboardLayoutSpec.FN_OVERLAY_MAX
    }

    fun groups(): List<Group> = listOf(
        Group(
            "Gamepad controls", GAMEPAD_CONTROL_PROPS,
            com.bluepilot.remote.model.gamepad.GamepadLayoutSpec.MAX_CONTROLS,
            "per control: geometry, shape, colors, curves, esports pipeline, " +
                "motion personality, pressed style, cooldowns, touch guards"
        ),
        Group("Gamepad layout meta", GAMEPAD_LAYOUT_PROPS, 1, "name, naming, grid, sensitivity, shift layer"),
        Group(
            "Keyboard keys", KEYBOARD_KEY_PROPS, keyboardKeyInstances(),
            "per key: label, bindings, secondary, width, color, typeText, " +
                "media usage — board + favorites + FN overlay"
        ),
        Group(
            "Control-deck widgets", WIDGET_PROPS,
            com.bluepilot.remote.model.widgets.LayoutSpec.MAX_WIDGETS,
            "per widget: frame (4), style (9), action, label, haptics"
        ),
        Group("Widget layout meta", WIDGET_LAYOUT_PROPS, 1, "name, category, skin, grid"),
        Group(
            "Macro steps", MACRO_STEP_PROPS,
            com.bluepilot.remote.model.macros.MacroSpec.STEPS_MAX,
            "each of up to 64 slots holds one of 10 step types (largest = 3 fields)"
        ),
        Group("Global settings", SETTINGS_PROPS, 1, "theme engine, haptics, 3D quality, automation, sensors"),
    )

    /** The honest total for one fully-populated workspace. */
    fun total(): Int = groups().sumOf { it.total }
}
