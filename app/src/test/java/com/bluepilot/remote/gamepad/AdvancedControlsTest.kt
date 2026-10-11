package com.bluepilot.remote.gamepad

import com.bluepilot.remote.domain.AdvancedControls
import com.bluepilot.remote.domain.GamepadRuntimeCore
import com.bluepilot.remote.hid.HidReportBuilder
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.gamepad.ArrowDirection
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADV SECTION 1 — every new control type must produce correct, verified
 * HID signals. These tests assert the EXACT wire bytes (report ID 5
 * payload: btnLo, btnHi, hat, lx, ly, rx, ry).
 */
class AdvancedControlsTest {

    // ---------- Toggle buttons ----------

    @Test
    fun `toggle latches on and off`() {
        assertTrue(AdvancedControls.toggleAfterTap(false))
        assertFalse(AdvancedControls.toggleAfterTap(true))
    }

    @Test
    fun `toggle wire state keeps button bit held between reports`() {
        var s = GamepadSnapshot()
        s = GamepadRuntimeCore.withButton(s, 2, true)     // latch ON (X button)
        val held = HidReportBuilder.gamepad(s)
        assertEquals(0b100.toByte(), held[0])             // bit 2 in btnLo
        // Other inputs must NOT clear the latched bit.
        s = GamepadRuntimeCore.withStick(s, com.bluepilot.remote.model.gamepad.StickSide.LEFT, 0.5f, 0f, 0)
        assertEquals(0b100.toByte(), HidReportBuilder.gamepad(s)[0])
        s = GamepadRuntimeCore.withButton(s, 2, false)    // latch OFF
        assertEquals(0.toByte(), HidReportBuilder.gamepad(s)[0])
    }

    // ---------- Arrow buttons (independent hat directions) ----------

    @Test
    fun `every arrow direction maps to its exact hat value`() {
        assertEquals(0, ArrowDirection.UP.hat)
        assertEquals(1, ArrowDirection.UP_RIGHT.hat)
        assertEquals(2, ArrowDirection.RIGHT.hat)
        assertEquals(3, ArrowDirection.DOWN_RIGHT.hat)
        assertEquals(4, ArrowDirection.DOWN.hat)
        assertEquals(5, ArrowDirection.DOWN_LEFT.hat)
        assertEquals(6, ArrowDirection.LEFT.hat)
        assertEquals(7, ArrowDirection.UP_LEFT.hat)
    }

    @Test
    fun `arrow press puts hat value on the wire, release neutralizes`() {
        var s = GamepadRuntimeCore.withHat(GamepadSnapshot(), ArrowDirection.LEFT.hat)
        assertEquals(6.toByte(), HidReportBuilder.gamepad(s)[2])
        s = GamepadRuntimeCore.withHat(s, 8)
        assertEquals(8.toByte(), HidReportBuilder.gamepad(s)[2])
    }

    // ---------- Diagonal-only filter ----------

    @Test
    fun `diagonalOnly passes diagonals, blocks cardinals`() {
        assertEquals(1, AdvancedControls.diagonalOnly(1))  // NE passes
        assertEquals(3, AdvancedControls.diagonalOnly(3))
        assertEquals(5, AdvancedControls.diagonalOnly(5))
        assertEquals(7, AdvancedControls.diagonalOnly(7))
        assertEquals(8, AdvancedControls.diagonalOnly(0))  // N blocked
        assertEquals(8, AdvancedControls.diagonalOnly(2))
        assertEquals(8, AdvancedControls.diagonalOnly(4))
        assertEquals(8, AdvancedControls.diagonalOnly(6))
        assertEquals(8, AdvancedControls.diagonalOnly(8))
    }

    // ---------- Circular D-pad ----------

    @Test
    fun `circular dpad has smaller dead zone and full 8-way`() {
        assertEquals(8, AdvancedControls.circularHat(0.05f, 0.05f))   // center
        assertEquals(2, AdvancedControls.circularHat(0.2f, 0f))       // east @20% (cross pad would be dead)
        assertEquals(0, AdvancedControls.circularHat(0f, -0.5f))      // north
        assertEquals(1, AdvancedControls.circularHat(0.5f, -0.5f))    // north-east diagonal
        assertEquals(5, AdvancedControls.circularHat(-0.5f, 0.5f))    // south-west
    }

    // ---------- Square gate ----------

    @Test
    fun `square gate reaches corners, preserves cardinals, zero-safe`() {
        val (cx, cy) = AdvancedControls.squareGate(0.7071f, 0.7071f)  // 45° circle edge
        assertEquals(1f, cx, 0.01f)                                   // → square corner
        assertEquals(1f, cy, 0.01f)
        val (px, py) = AdvancedControls.squareGate(1f, 0f)            // pure cardinal
        assertEquals(1f, px, 1e-4f); assertEquals(0f, py, 1e-4f)
        val (zx, zy) = AdvancedControls.squareGate(0f, 0f)
        assertEquals(0f, zx, 1e-6f); assertEquals(0f, zy, 1e-6f)
    }

    // ---------- Anti-deadzone ----------

    @Test
    fun `anti-deadzone jumps output past host dead spot`() {
        assertEquals(0.5f, AdvancedControls.antiDeadZone(0.5f, 0), 1e-4f)     // off = pass-through
        assertEquals(0.0f, AdvancedControls.antiDeadZone(0.0f, 20), 1e-4f)    // center stays center
        assertEquals(0.2f + 0.01f * 0.8f, AdvancedControls.antiDeadZone(0.01f, 20), 1e-3f) // jumps to ~20%
        assertEquals(1f, AdvancedControls.antiDeadZone(1f, 20), 1e-4f)        // full stays full
        assertEquals(-(0.2f + 0.5f * 0.8f), AdvancedControls.antiDeadZone(-0.5f, 20), 1e-3f) // sign preserved
    }

    // ---------- Outer range ----------

    @Test
    fun `outer range scales partial travel to full output`() {
        assertEquals(1f, AdvancedControls.outerRange(0.75f, 0.75f), 1e-4f)  // 75% travel = max
        assertEquals(0.5f, AdvancedControls.outerRange(0.5f, 1f), 1e-4f)    // 100% range = identity
        assertEquals(-1f, AdvancedControls.outerRange(-0.9f, 0.8f), 1e-4f)  // clamped, sign kept
    }

    // ---------- Multi-tap / combo indices on the wire ----------

    @Test
    fun `multi-tap secondary and combo second index hit correct button bits`() {
        // Secondary index 9 → bit 1 of btnHi.
        var s = GamepadRuntimeCore.withButton(GamepadSnapshot(), 9, true)
        assertEquals(0b10.toByte(), HidReportBuilder.gamepad(s)[1])
        // Combo: bumper (4) + trigger (6) both held → bits 4 and 6 of btnLo.
        s = GamepadRuntimeCore.withButton(GamepadSnapshot(), 4, true)
        s = GamepadRuntimeCore.withButton(s, 6, true)
        assertEquals(0b1010000.toByte(), HidReportBuilder.gamepad(s)[0])
    }

    // ---------- Spec round-trip (customization rule compliance) ----------

    @Test
    fun `new control spec fields survive JSON round-trip and sanitize junk`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val spec = GamepadControlSpec(
            id = "adv1", type = GamepadControlType.ARROW,
            arrowDirection = ArrowDirection.DOWN_LEFT,
            arrowStyle = com.bluepilot.remote.model.gamepad.ArrowStyle.DOT,
            arrowRepeat = true, arrowRepeatRate = 99,          // out of range
            buttonMode = com.bluepilot.remote.model.gamepad.ButtonMode.MULTI_TAP,
            secondaryButtonIndex = 44,                          // out of range
            radialOptions = List(20) { it },                    // too many
            outerRange = 0.1f,                                  // below floor
            antiDeadZone = 90,                                  // above cap
            stickClickIndex = 77                                // out of range
        ).sanitized()
        assertEquals(30, spec.arrowRepeatRate)                  // clamped
        assertEquals(15, spec.secondaryButtonIndex)
        assertEquals(GamepadControlSpec.RADIAL_MAX, spec.radialOptions.size)
        assertEquals(0.5f, spec.outerRange, 1e-4f)
        assertEquals(40, spec.antiDeadZone)
        assertEquals(15, spec.stickClickIndex)
        val restored = json.decodeFromString(
            GamepadControlSpec.serializer(),
            json.encodeToString(GamepadControlSpec.serializer(), spec)
        )
        assertEquals(spec, restored)
    }

    @Test
    fun `legacy control JSON without new fields still loads with defaults`() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"id":"old1","type":"BUTTON","buttonIndex":3}"""
        val spec = json.decodeFromString(GamepadControlSpec.serializer(), legacy)
        assertEquals(com.bluepilot.remote.model.gamepad.ButtonMode.MOMENTARY, spec.buttonMode)
        assertEquals(com.bluepilot.remote.model.gamepad.StickGate.CIRCLE, spec.stickGate)
        assertFalse(spec.stickSticky)
        assertEquals(-1, spec.stickClickIndex)
        assertEquals(1f, spec.outerRange, 1e-4f)
        assertTrue(spec.pressGlow)
    }
}
