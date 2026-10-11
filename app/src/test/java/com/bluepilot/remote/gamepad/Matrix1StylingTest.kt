package com.bluepilot.remote.gamepad

import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.MotionStyle
import com.bluepilot.remote.model.gamepad.PressedStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 MATRIX 1 — state styling + motion personality model. */
class Matrix1StylingTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `pressed style sanitizes out-of-range and keeps nulls`() {
        val s = PressedStyle(scale = 3f, cornerRadius = 99, borderWidth = 44).sanitized()
        assertEquals(1.2f, s.scale!!, 1e-4f)
        assertEquals(40, s.cornerRadius)
        assertEquals(6, s.borderWidth)
        val n = PressedStyle().sanitized()
        assertNull(n.color); assertNull(n.scale); assertNull(n.cornerRadius)
        // NaN scale collapses to null (inherit).
        assertNull(PressedStyle(scale = Float.NaN).sanitized().scale)
    }

    @Test
    fun `motion style and pressed style survive JSON round trip`() {
        val spec = GamepadControlSpec(
            id = "m1", type = GamepadControlType.BUTTON,
            motionStyle = MotionStyle.ELASTIC,
            pressedStyle = PressedStyle(color = 0xFF2ECC71, scale = 0.85f)
        ).sanitized()
        val restored = json.decodeFromString(
            GamepadControlSpec.serializer(),
            json.encodeToString(GamepadControlSpec.serializer(), spec)
        )
        assertEquals(MotionStyle.ELASTIC, restored.motionStyle)
        assertEquals(0xFF2ECC71, restored.pressedStyle.color)
        assertEquals(0.85f, restored.pressedStyle.scale!!, 1e-4f)
    }

    @Test
    fun `legacy JSON defaults to STANDARD personality and empty overrides`() {
        val legacy = """{"id":"old","type":"BUTTON","buttonIndex":2}"""
        val spec = json.decodeFromString(GamepadControlSpec.serializer(), legacy)
        assertEquals(MotionStyle.STANDARD, spec.motionStyle)
        assertNull(spec.pressedStyle.color)
        assertNull(spec.pressedStyle.scale)
    }

    @Test
    fun `cooldown clamps to sane range and defaults off`() {
        val spec = GamepadControlSpec(
            id = "cd", type = GamepadControlType.BUTTON, cooldownMs = 999_999
        ).sanitized()
        assertEquals(60_000, spec.cooldownMs)
        assertEquals(0, GamepadControlSpec(id = "x", type = GamepadControlType.BUTTON)
            .sanitized().cooldownMs)
        assertEquals(0, GamepadControlSpec(
            id = "y", type = GamepadControlType.BUTTON, cooldownMs = -50
        ).sanitized().cooldownMs)
    }

    @Test
    fun `hold-to-activate clamps and defaults instant`() {
        assertEquals(3000, GamepadControlSpec(
            id = "h", type = GamepadControlType.BUTTON, holdToActivateMs = 99_999
        ).sanitized().holdToActivateMs)
        assertEquals(0, GamepadControlSpec(
            id = "h2", type = GamepadControlType.BUTTON
        ).sanitized().holdToActivateMs)
        assertEquals(0, GamepadControlSpec(
            id = "h3", type = GamepadControlType.BUTTON, holdToActivateMs = -5
        ).sanitized().holdToActivateMs)
    }

    @Test
    fun `corner radii require exactly four entries and clamp`() {
        // Wrong count collapses to empty (uniform shape).
        assertTrue(GamepadControlSpec(
            id = "c1", type = GamepadControlType.BUTTON, cornerRadii = listOf(5, 5)
        ).sanitized().cornerRadii.isEmpty())
        // Four entries kept, each clamped to 0..40.
        assertEquals(listOf(0, 40, 12, 0), GamepadControlSpec(
            id = "c2", type = GamepadControlType.BUTTON,
            cornerRadii = listOf(-3, 99, 12, 0)
        ).sanitized().cornerRadii)
    }

    @Test
    fun `touch modifiers clamp and default off`() {
        val s = GamepadControlSpec(
            id = "t", type = GamepadControlType.BUTTON,
            touchSlopDp = 200, maxPointers = 99
        ).sanitized()
        assertEquals(48, s.touchSlopDp)
        assertEquals(5, s.maxPointers)
        val d = GamepadControlSpec(id = "d", type = GamepadControlType.BUTTON).sanitized()
        assertEquals(0, d.touchSlopDp)
        assertEquals(0, d.maxPointers)
    }

    @Test
    fun `all seven personalities exist for the editor chips`() {
        assertEquals(7, MotionStyle.entries.size)
        assertTrue(MotionStyle.entries.map { it.name }.containsAll(
            listOf("SUBTLE", "STANDARD", "BOUNCY", "SNAPPY", "ELASTIC", "GLIDE", "MECHANICAL")
        ))
    }
}