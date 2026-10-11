package com.bluepilot.remote.features

import com.bluepilot.remote.domain.A11y
import com.bluepilot.remote.model.gamepad.ArrowDirection
import com.bluepilot.remote.model.gamepad.ButtonNaming
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.StickSide
import com.bluepilot.remote.model.widgets.WidgetFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 MATRIX 5 — accessibility descriptions + touch-target audit. */
class A11yTest {

    private fun control(
        type: GamepadControlType = GamepadControlType.BUTTON,
        label: String = "",
        index: Int = 0
    ) = GamepadControlSpec(id = "c", type = type, label = label, buttonIndex = index)

    @Test
    fun `custom label wins over generated name`() {
        assertEquals("Jump button", A11y.describe(control(label = "Jump")))
    }

    @Test
    fun `buttons use the naming scheme`() {
        // Xbox index 0 = A; PlayStation = Cross.
        val xbox = A11y.describe(control(index = 0), ButtonNaming.XBOX)
        val ps = A11y.describe(control(index = 0), ButtonNaming.PLAYSTATION)
        assertTrue("got '$xbox'", xbox.startsWith("A"))
        assertTrue("got '$ps'", ps.contains("Cross"))
        assertTrue(xbox.endsWith("button"))
    }

    @Test
    fun `sticks dpads arrows and combos describe their kind`() {
        assertEquals(
            "left stick",
            A11y.describe(control(GamepadControlType.STICK))
        )
        assertEquals(
            "directional pad",
            A11y.describe(control(GamepadControlType.DPAD))
        )
        val arrow = GamepadControlSpec(
            id = "a", type = GamepadControlType.ARROW,
            arrowDirection = ArrowDirection.UP
        )
        assertEquals("up arrow", A11y.describe(arrow))
        assertEquals(
            "bumper and trigger combo",
            A11y.describe(control(GamepadControlType.COMBO))
        )
    }

    @Test
    fun `right stick uses its side`() {
        val stick = GamepadControlSpec(
            id = "s", type = GamepadControlType.STICK, stickSide = StickSide.RIGHT
        )
        assertEquals("right stick", A11y.describe(stick))
    }

    @Test
    fun `min target flags small controls against real canvas`() {
        // 800x400dp canvas: 0.05 wide = 40dp < 48dp → flagged.
        assertTrue(A11y.isBelowMinTarget(WidgetFrame(0f, 0f, 0.05f, 0.2f), 800f, 400f))
        // 0.1 x 0.15 = 80x60dp → fine.
        assertFalse(A11y.isBelowMinTarget(WidgetFrame(0f, 0f, 0.1f, 0.15f), 800f, 400f))
        // Height violation alone also flags.
        assertTrue(A11y.isBelowMinTarget(WidgetFrame(0f, 0f, 0.2f, 0.05f), 800f, 400f))
    }

    @Test
    fun `garbage canvas never flags`() {
        val f = WidgetFrame(0f, 0f, 0.01f, 0.01f)
        assertFalse(A11y.isBelowMinTarget(f, Float.NaN, 400f))
        assertFalse(A11y.isBelowMinTarget(f, 800f, Float.NaN))
        assertFalse(A11y.isBelowMinTarget(f, 0f, 400f))
        assertFalse(A11y.isBelowMinTarget(f, -5f, 400f))
    }
}
