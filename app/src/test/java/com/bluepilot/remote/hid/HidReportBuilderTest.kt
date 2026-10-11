package com.bluepilot.remote.hid

import com.bluepilot.remote.model.DpadDirection
import com.bluepilot.remote.model.GamepadButton
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.HidModifiers
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.MouseButton
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the HID report byte contract. If any of these fail after an edit,
 * the report layout no longer matches the descriptor — fix before shipping.
 */
class HidReportBuilderTest {

    // ---------- Keyboard ----------

    @Test
    fun `keyboard report is 8 bytes with modifiers and key`() {
        val report = HidReportBuilder.keyboard(HidModifiers.LEFT_CTRL, listOf(HidKeys.C))
        assertEquals(HidDescriptors.SIZE_KEYBOARD, report.size)
        assertEquals(HidModifiers.LEFT_CTRL, report[0])
        assertEquals(0.toByte(), report[1]) // reserved
        assertEquals(HidKeys.C, report[2])
        assertEquals(0.toByte(), report[3])
    }

    @Test
    fun `keyboard supports six key rollover and drops extras`() {
        val keys = listOf<Byte>(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B)
        val report = HidReportBuilder.keyboard(0, keys)
        assertEquals(HidDescriptors.SIZE_KEYBOARD, report.size)
        assertEquals(0x04.toByte(), report[2])
        assertEquals(0x09.toByte(), report[7]) // 6th key kept, 7th/8th dropped
    }

    @Test
    fun `keyboard release is all zeros`() {
        assertArrayEquals(ByteArray(8), HidReportBuilder.keyboardRelease())
    }

    // ---------- Mouse ----------

    @Test
    fun `mouse report layout is buttons dx dy wheel`() {
        val report = HidReportBuilder.mouse(MouseButton.LEFT.mask, 10, -20, 3)
        assertArrayEquals(byteArrayOf(0x01, 10, -20, 3), report)
    }

    @Test
    fun `mouse deltas are clamped to signed byte range`() {
        val report = HidReportBuilder.mouse(0, 500, -500, 1000)
        assertEquals(127.toByte(), report[1])
        assertEquals((-127).toByte(), report[2])
        assertEquals(127.toByte(), report[3])
    }

    @Test
    fun `mouseButtons combines masks`() {
        val report = HidReportBuilder.mouseButtons(setOf(MouseButton.LEFT, MouseButton.RIGHT))
        assertEquals(0x03.toByte(), report[0])
    }

    // ---------- Consumer ----------

    @Test
    fun `consumer report is little endian 16 bit`() {
        val report = HidReportBuilder.consumer(0x00CD) // Play/Pause
        assertArrayEquals(byteArrayOf(0xCD.toByte(), 0x00), report)
    }

    @Test
    fun `consumer usage above descriptor max is clamped`() {
        val report = HidReportBuilder.consumer(0xFFFF)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x03), report) // 0x03FF
    }

    // ---------- Gamepad ----------

    @Test
    fun `neutral gamepad report is centered`() {
        val report = HidReportBuilder.gamepadNeutral()
        assertEquals(HidDescriptors.SIZE_GAMEPAD, report.size)
        assertEquals(0.toByte(), report[0])                       // buttons lo
        assertEquals(0.toByte(), report[1])                       // buttons hi
        assertEquals(DpadDirection.NONE.hatValue.toByte(), report[2])
        assertEquals(127.toByte(), report[3])                     // centered axis
    }

    @Test
    fun `gamepad buttons pack into two bytes little endian`() {
        val snap = GamepadSnapshot()
            .press(GamepadButton.A)      // bit 0
            .press(GamepadButton.START)  // bit 9
        val report = HidReportBuilder.gamepad(snap)
        assertEquals(0x01.toByte(), report[0])
        assertEquals(0x02.toByte(), report[1])
    }

    @Test
    fun `gamepad axes map minus1 to 0 and plus1 to 255`() {
        assertEquals(0.toByte(), HidReportBuilder.axisToByte(-1f))
        assertEquals(255.toByte(), HidReportBuilder.axisToByte(1f))
        assertEquals(127.toByte(), HidReportBuilder.axisToByte(0f))
    }

    @Test
    fun `gamepad axis NaN is treated as center`() {
        assertEquals(127.toByte(), HidReportBuilder.axisToByte(Float.NaN))
    }

    @Test
    fun `gamepad snapshot press and release round trips`() {
        val pressed = GamepadSnapshot().press(GamepadButton.X)
        assertEquals(true, pressed.isPressed(GamepadButton.X))
        val released = pressed.release(GamepadButton.X)
        assertEquals(false, released.isPressed(GamepadButton.X))
        assertEquals(0, released.buttons)
    }
}
