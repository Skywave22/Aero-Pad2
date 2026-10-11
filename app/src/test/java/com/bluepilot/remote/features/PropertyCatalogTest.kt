package com.bluepilot.remote.features

import com.bluepilot.remote.domain.PropertyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.primaryConstructor

/**
 * V2 PART C — property catalog is verified against the REAL model shapes
 * via reflection. If a spec gains/loses constructor fields without the
 * catalog being updated, these tests fail — the count can never drift
 * from the code. NO FAKE DATA: every number is derived, then asserted.
 */
class PropertyCatalogTest {

    private fun ctorParams(k: kotlin.reflect.KClass<*>): Int =
        k.primaryConstructor!!.parameters.size

    @Test
    fun `gamepad control count matches the real constructor`() {
        val specParams = ctorParams(com.bluepilot.remote.model.gamepad.GamepadControlSpec::class)
        val pressed = ctorParams(com.bluepilot.remote.model.gamepad.PressedStyle::class)
        // rule 2+3: − id − pressedStyle container + expanded PressedStyle.
        assertEquals(PropertyCatalog.GAMEPAD_CONTROL_PROPS, specParams - 2 + pressed)
    }

    @Test
    fun `gamepad layout meta matches`() {
        val params = ctorParams(com.bluepilot.remote.model.gamepad.GamepadLayoutSpec::class)
        assertEquals(PropertyCatalog.GAMEPAD_LAYOUT_PROPS, params - 1)   // − controls list
    }

    @Test
    fun `keyboard key count matches`() {
        val params = ctorParams(com.bluepilot.remote.model.keyboard.KeySpec::class)
        assertEquals(PropertyCatalog.KEYBOARD_KEY_PROPS, params - 1)     // − id
    }

    @Test
    fun `widget count matches frame and style expansion`() {
        val spec = ctorParams(com.bluepilot.remote.model.widgets.WidgetSpec::class)
        val frame = ctorParams(com.bluepilot.remote.model.widgets.WidgetFrame::class)
        val style = ctorParams(com.bluepilot.remote.model.widgets.WidgetStyle::class)
        // − id − frame − style + expansions.
        assertEquals(PropertyCatalog.WIDGET_PROPS, spec - 3 + frame + style)
    }

    @Test
    fun `settings count matches the four settings classes`() {
        val total = ctorParams(com.bluepilot.remote.model.AppSettings::class) +
            ctorParams(com.bluepilot.remote.model.MouseSettings::class) +
            ctorParams(com.bluepilot.remote.model.KeyboardSettings::class) +
            ctorParams(com.bluepilot.remote.model.GamepadSettings::class)
        assertEquals(PropertyCatalog.SETTINGS_PROPS, total)
    }

    @Test
    fun `macro step props equal the largest real step type`() {
        val largest = com.bluepilot.remote.model.macros.MacroStep::class.sealedSubclasses
            .maxOf { it.primaryConstructor?.parameters?.size ?: 0 }
        assertEquals(PropertyCatalog.MACRO_STEP_PROPS, largest)
    }

    @Test
    fun `keyboard instances derive from the real default board`() {
        val board = com.bluepilot.remote.model.keyboard.DefaultKeyboards.fullQwerty()
        val expected = board.rows.sumOf { it.size } +
            com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec.FAVORITES_MAX +
            com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec.FN_OVERLAY_MAX
        assertEquals(expected, PropertyCatalog.keyboardKeyInstances())
        assertTrue(board.rows.sumOf { it.size } >= 80)   // real full board
    }

    @Test
    fun `total exceeds the v2 target and every group is positive`() {
        PropertyCatalog.groups().forEach { g ->
            assertTrue("group ${g.name} must be positive", g.total > 0)
        }
        val total = PropertyCatalog.total()
        // The v2.0 contract: 1000+ customizable properties. Derived, not
        // declared: with current shapes this lands at 4600+.
        assertTrue("total $total must exceed 1000", total > 1000)
        assertTrue("total $total sanity upper bound", total < 50_000)
    }
}
