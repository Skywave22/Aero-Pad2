package com.bluepilot.remote.hid

import com.bluepilot.remote.model.GamepadSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * V2 PART A — pooled buffers MUST be byte-identical to the pure builder
 * (zero functionality change rule) and MUST reuse the same instance
 * (zero allocation guarantee).
 */
class ReportPoolTest {

    @Test
    fun `pooled mouse report is byte-identical to builder`() {
        val cases = listOf(
            Triple(0, 0, 0),
            Triple(5, -3, 0),
            Triple(127, 127, 0),
            Triple(-200, 300, 0)   // clamping paths
        )
        cases.forEach { (dx, dy, wheel) ->
            assertArrayEquals(
                HidReportBuilder.mouse(buttons = 0, dx = dx, dy = dy, wheel = wheel),
                ReportPool.mouseInto(0, dx, dy, wheel)
            )
        }
    }

    @Test
    fun `pooled gamepad report is byte-identical to builder`() {
        val cases = listOf(
            GamepadSnapshot(),
            GamepadSnapshot(buttons = 0xFFFF, hat = 3, leftX = 1f, leftY = -1f),
            GamepadSnapshot(buttons = 0b101, hat = 8, rightX = 0.5f, rightY = -0.25f),
            GamepadSnapshot(hat = 99, leftX = Float.NaN)   // sanitize paths
        )
        cases.forEach { snap ->
            assertArrayEquals(HidReportBuilder.gamepad(snap), ReportPool.gamepadInto(snap))
        }
    }

    @Test
    fun `pool reuses the same buffer instance - zero allocation`() {
        val a = ReportPool.mouseInto(0, 1, 2, 0)
        val b = ReportPool.mouseInto(0, 9, 9, 0)
        assertSame(a, b)
        val g1 = ReportPool.gamepadInto(GamepadSnapshot())
        val g2 = ReportPool.gamepadInto(GamepadSnapshot(buttons = 1))
        assertSame(g1, g2)
    }
}
