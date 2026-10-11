package com.bluepilot.remote.gamepad

import com.bluepilot.remote.domain.LayoutIntelligence
import com.bluepilot.remote.model.gamepad.ArrowDirection
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import com.bluepilot.remote.model.gamepad.StickSide
import com.bluepilot.remote.model.widgets.WidgetFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADV SECTION 2 — layout intelligence math must produce usable layouts. */
class LayoutIntelligenceTest {

    private fun ctrl(
        id: String, x: Float, y: Float, w: Float = 0.1f, h: Float = 0.1f,
        type: GamepadControlType = GamepadControlType.BUTTON
    ) = GamepadControlSpec(id = id, type = type, frame = WidgetFrame(x, y, w, h))

    // ---------- Magnetic grid ----------

    @Test
    fun `snap rounds to nearest grid line and off when zero`() {
        assertEquals(0.25f, LayoutIntelligence.snap(0.24f, 0.025f), 1e-4f)
        assertEquals(0.25f, LayoutIntelligence.snap(0.26f, 0.025f), 1e-4f)
        assertEquals(0.313f, LayoutIntelligence.snap(0.313f, 0f), 1e-4f) // off = identity
    }

    // ---------- Symmetry / handedness ----------

    @Test
    fun `mirrorCopy lands on opposite side, mirrors stick side and arrows`() {
        val left = ctrl("l", x = 0.1f, y = 0.5f, w = 0.2f, type = GamepadControlType.STICK)
            .copy(stickSide = StickSide.LEFT)
        val mirrored = LayoutIntelligence.mirrorCopy(left, "r")
        assertEquals(0.7f, mirrored.frame.x, 1e-4f)          // 1 - 0.1 - 0.2
        assertEquals(0.5f, mirrored.frame.y, 1e-4f)          // y unchanged
        assertEquals(StickSide.RIGHT, mirrored.stickSide)    // side flipped
        assertEquals("r", mirrored.id)

        val arrow = ctrl("a", 0.2f, 0.3f, type = GamepadControlType.ARROW)
            .copy(arrowDirection = ArrowDirection.UP_LEFT)
        assertEquals(ArrowDirection.UP_RIGHT, LayoutIntelligence.mirrorCopy(arrow, "a2").arrowDirection)
    }

    @Test
    fun `mirrorLayout is its own inverse and stays in bounds`() {
        val spec = GamepadLayoutSpec(controls = listOf(
            ctrl("a", 0.05f, 0.1f), ctrl("b", 0.8f, 0.6f, w = 0.15f),
            ctrl("s", 0.1f, 0.4f, w = 0.22f, type = GamepadControlType.STICK)
        ))
        val once = LayoutIntelligence.mirrorLayout(spec)
        // All frames still inside the canvas (usable layout).
        once.controls.forEach { c ->
            assertTrue(c.frame.x >= 0f && c.frame.x + c.frame.w <= 1.0001f)
        }
        // Flip twice = original geometry (within float tolerance).
        val twice = LayoutIntelligence.mirrorLayout(once)
        spec.controls.zip(twice.controls).forEach { (o, t) ->
            assertEquals(o.frame.x, t.frame.x, 1e-3f)
            assertEquals(o.stickSide, t.stickSide)
        }
    }

    // ---------- Smart spacing ----------

    @Test
    fun `spacingWarnings flags close and overlapping pairs only`() {
        val spec = GamepadLayoutSpec(controls = listOf(
            ctrl("a", 0.10f, 0.10f),               // a↔b gap = 0.01 → warn
            ctrl("b", 0.21f, 0.10f),
            ctrl("c", 0.60f, 0.60f),               // far from everything
            ctrl("d", 0.62f, 0.62f)                // overlaps c → warn (gap 0)
        ))
        val warns = LayoutIntelligence.spacingWarnings(spec, minGap = 0.02f)
        val pairs = warns.map { setOf(it.idA, it.idB) }
        assertTrue(setOf("a", "b") in pairs)
        assertTrue(setOf("c", "d") in pairs)
        assertEquals(2, warns.size)
        assertEquals(0f, warns.first { setOf(it.idA, it.idB) == setOf("c", "d") }.gap, 1e-4f)
    }

    // ---------- Alignment guides ----------

    @Test
    fun `alignment guides detect canvas center and matching controls`() {
        val spec = GamepadLayoutSpec(controls = listOf(
            ctrl("moving", 0.45f, 0.45f),          // center = 0.5, 0.5
            ctrl("match", 0.45f, 0.80f),           // same X center 0.5
            ctrl("nomatch", 0.10f, 0.10f)
        ))
        val g = LayoutIntelligence.alignmentGuides(spec, "moving")
        assertTrue(g.verticalCenter)               // at canvas center X
        assertTrue(g.horizontalCenter)             // at canvas center Y
        assertEquals(1, g.matchX.size)             // aligns with "match" only
        assertEquals(0.5f, g.matchX[0], 1e-3f)
    }

    // ---------- Thumb-reach ergonomics ----------

    @Test
    fun `thumb-reach zones classify corner vs center controls correctly`() {
        val bottomLeft = ctrl("bl", 0.05f, 0.75f, w = 0.2f, h = 0.2f)
        val deadCenterTop = ctrl("ct", 0.45f, 0.02f)
        assertTrue(LayoutIntelligence.isComfortable(bottomLeft, LayoutIntelligence.GripStyle.TWO_THUMB))
        assertFalse(LayoutIntelligence.isComfortable(deadCenterTop, LayoutIntelligence.GripStyle.TWO_THUMB))
        // Claw grip: index fingers DO reach top corners.
        val topLeft = ctrl("tl", 0.02f, 0.02f, w = 0.12f, h = 0.15f)
        assertTrue(LayoutIntelligence.isComfortable(topLeft, LayoutIntelligence.GripStyle.CLAW))
        assertFalse(LayoutIntelligence.isComfortable(topLeft, LayoutIntelligence.GripStyle.TWO_THUMB))
    }

    @Test
    fun `every grip style provides at least two reach zones inside canvas`() {
        LayoutIntelligence.GripStyle.entries.forEach { grip ->
            val zones = LayoutIntelligence.reachZones(grip)
            assertTrue(zones.size >= 2)
            zones.forEach { z ->
                assertTrue(z.cx in 0f..1f && z.cy in 0f..1f)
                assertTrue(z.comfortable > 0f && z.stretch > z.comfortable)
            }
        }
    }

    // ---------- Shift layer model ----------

    @Test
    fun `layer field sanitizes and shift spec round-trips`() {
        val c = ctrl("x", 0.1f, 0.1f).copy(layer = 7).sanitized()
        assertEquals(1, c.layer)                    // clamped to 0..1
        val spec = GamepadLayoutSpec(
            controls = listOf(ctrl("shift", 0.8f, 0.8f), ctrl("hidden", 0.2f, 0.2f).copy(layer = 1)),
            shiftControlId = "shift"
        ).sanitized()
        assertEquals("shift", spec.shiftControlId)
        assertEquals(1, spec.controls[1].layer)
        // JSON round-trip via the share format.
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString(
            GamepadLayoutSpec.serializer(),
            json.encodeToString(GamepadLayoutSpec.serializer(), spec)
        )
        assertEquals(spec, restored)
    }

    // ---------- Grid persists per-layout ----------

    @Test
    fun `gridSize and tags sanitize junk`() {
        val spec = GamepadLayoutSpec(
            gridSize = Float.NaN,
            tags = listOf("FPS", "", "a-very-long-tag-name-exceeding", "Racing", "x", "y", "z", "extra")
        ).sanitized()
        assertEquals(0f, spec.gridSize, 1e-6f)
        assertEquals(6, spec.tags.size)             // capped at 6, blanks dropped
        assertTrue(spec.tags.all { it.length <= 16 })
    }
}
