package com.bluepilot.remote.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural sanity checks on the combined HID descriptor.
 * These catch accidental edits that would silently break registration.
 */
class HidDescriptorsTest {

    private val descriptor = HidDescriptors.COMBINED

    @Test
    fun `descriptor is non-trivial`() {
        assertTrue("Descriptor suspiciously small", descriptor.size > 100)
    }

    @Test
    fun `collections are balanced`() {
        // 0xA1 = Collection, 0xC0 = End Collection.
        // Count opcode bytes conservatively by scanning: every 0xA1 must pair with a 0xC0.
        var open = 0
        var close = 0
        var i = 0
        while (i < descriptor.size) {
            val b = descriptor[i].toInt() and 0xFF
            when (b) {
                0xA1 -> { open++; i += 2 }      // Collection takes 1 data byte
                0xC0 -> { close++; i += 1 }     // End Collection takes none
                0x26, 0x2A -> i += 3            // 2-byte data items we use
                else -> i += 2                  // all other items we emit carry 1 data byte
            }
        }
        assertEquals("Unbalanced HID collections", open, close)
    }

    @Test
    fun `all five report ids are declared`() {
        // 0x85 <id> is the Report ID item.
        val declaredIds = mutableSetOf<Int>()
        for (i in 0 until descriptor.size - 1) {
            if ((descriptor[i].toInt() and 0xFF) == 0x85) {
                declaredIds += descriptor[i + 1].toInt() and 0xFF
            }
        }
        assertTrue(declaredIds.containsAll(
            listOf(
                HidDescriptors.REPORT_ID_KEYBOARD,
                HidDescriptors.REPORT_ID_MOUSE,
                HidDescriptors.REPORT_ID_CONSUMER,
                HidDescriptors.REPORT_ID_SYSTEM,
                HidDescriptors.REPORT_ID_GAMEPAD
            )
        ))
    }

    @Test
    fun `payload size constants match builders`() {
        assertEquals(HidDescriptors.SIZE_KEYBOARD, HidReportBuilder.keyboardRelease().size)
        assertEquals(HidDescriptors.SIZE_MOUSE, HidReportBuilder.mouse().size)
        assertEquals(HidDescriptors.SIZE_CONSUMER, HidReportBuilder.consumerRelease().size)
        assertEquals(HidDescriptors.SIZE_SYSTEM, HidReportBuilder.systemRelease().size)
        assertEquals(HidDescriptors.SIZE_GAMEPAD, HidReportBuilder.gamepadNeutral().size)
    }
}
