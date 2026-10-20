package com.aeropad.remote.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Input validation contract: no matter what garbage reaches the models
 * (corrupt storage, bad import file, UI bug), sanitized() must clamp it.
 */
class SettingsSanitizeTest {

    @Test
    fun `mouse settings clamp out of range values`() {
        val junk = MouseSettings(
            sensitivity = 999,
            scrollSpeed = -5,
            movementSmoothing = 101
        ).sanitized()
        assertEquals(100, junk.sensitivity)
        assertEquals(0, junk.scrollSpeed)
        assertEquals(100, junk.movementSmoothing)
    }

    @Test
    fun `mouse settings keep valid values untouched`() {
        val ok = MouseSettings(sensitivity = 65, scrollSpeed = 50, movementSmoothing = 20).sanitized()
        assertEquals(65, ok.sensitivity)
        assertEquals(50, ok.scrollSpeed)
        assertEquals(20, ok.movementSmoothing)
    }

    @Test
    fun `gamepad dead zone capped at usability limit`() {
        val junk = GamepadSettings(joystickSensitivity = -1, deadZone = 90).sanitized()
        assertEquals(0, junk.joystickSensitivity)
        assertEquals(GamepadSettings.DEAD_ZONE_MAX, junk.deadZone)
    }

    @Test
    fun `defaults are already valid`() {
        assertEquals(MouseSettings(), MouseSettings().sanitized())
        assertEquals(GamepadSettings(), GamepadSettings().sanitized())
        assertEquals(KeyboardSettings(), KeyboardSettings().sanitized())
    }

    @Test
    fun `keyboard keyTapDelayMs is clamped to valid range`() {
        assertEquals(KeyboardSettings.DELAY_MIN, KeyboardSettings(keyTapDelayMs = 1).sanitized().keyTapDelayMs)
        assertEquals(KeyboardSettings.DELAY_MAX, KeyboardSettings(keyTapDelayMs = 500).sanitized().keyTapDelayMs)
        assertEquals(12, KeyboardSettings().sanitized().keyTapDelayMs)
    }
}
