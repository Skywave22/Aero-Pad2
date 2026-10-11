package com.bluepilot.remote.gamepad

import com.bluepilot.remote.domain.AnchorLayout
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import com.bluepilot.remote.model.widgets.WidgetFrame
import org.junit.Assert.assertEquals
import org.junit.Test

/** V2 MATRIX 1 b5 — anchor/relative placement math. */
class AnchorLayoutTest {

    private fun ctrl(id: String, x: Float = 0.4f, y: Float = 0.4f, w: Float = 0.2f, h: Float = 0.2f) =
        GamepadControlSpec(id = id, type = GamepadControlType.BUTTON, frame = WidgetFrame(x, y, w, h))

    private fun spec(vararg c: GamepadControlSpec) = GamepadLayoutSpec(controls = c.toList())

    @Test
    fun `anchor corners and center compute correct fractions`() {
        val s = spec(ctrl("a"))
        val bl = AnchorLayout.anchor(s, "a", AnchorLayout.Anchor.BOTTOM_LEFT).controls[0].frame
        assertEquals(0.02f, bl.x, 1e-4f)
        assertEquals(0.78f, bl.y, 1e-4f)          // 1 - 0.2 - 0.02
        val c = AnchorLayout.anchor(s, "a", AnchorLayout.Anchor.CENTER).controls[0].frame
        assertEquals(0.4f, c.x, 1e-4f)            // (1-0.2)/2
        assertEquals(0.4f, c.y, 1e-4f)
        val tr = AnchorLayout.anchor(s, "a", AnchorLayout.Anchor.TOP_RIGHT).controls[0].frame
        assertEquals(0.78f, tr.x, 1e-4f)
        assertEquals(0.02f, tr.y, 1e-4f)
    }

    @Test
    fun `anchor only moves the target control`() {
        val s = spec(ctrl("a"), ctrl("b", x = 0.1f, y = 0.1f))
        val out = AnchorLayout.anchor(s, "a", AnchorLayout.Anchor.TOP_LEFT)
        assertEquals(0.1f, out.controls[1].frame.x, 1e-4f)   // b untouched
        assertEquals(0.02f, out.controls[0].frame.x, 1e-4f)
    }

    @Test
    fun `placeRelative sides with gap and clamping`() {
        val s = spec(ctrl("ref", x = 0.5f, y = 0.5f), ctrl("m", w = 0.1f, h = 0.1f))
        val right = AnchorLayout.placeRelative(s, "m", "ref", AnchorLayout.Side.RIGHT_OF)
            .controls[1].frame
        assertEquals(0.715f, right.x, 1e-3f)      // 0.5+0.2+0.015
        assertEquals(0.5f, right.y, 1e-4f)
        val above = AnchorLayout.placeRelative(s, "m", "ref", AnchorLayout.Side.ABOVE)
            .controls[1].frame
        assertEquals(0.385f, above.y, 1e-3f)      // 0.5-0.1-0.015
        // Unknown reference id = no-op.
        assertEquals(s, AnchorLayout.placeRelative(s, "m", "nope", AnchorLayout.Side.BELOW))
    }
}
