package com.bluepilot.remote.domain

import com.bluepilot.remote.model.gamepad.ArrowDirection
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import com.bluepilot.remote.model.gamepad.StickSide

/**
 * ADV SECTION 2 — Gamepad Layout Intelligence (pure logic, unit-tested).
 *
 * Everything here is geometry math on fractional frames — zero Android
 * dependencies, so the ergonomic tools are provably correct.
 */
object LayoutIntelligence {

    // ------------------------------------------------------------------
    // Magnetic grid
    // ------------------------------------------------------------------

    /** Snap a coordinate to [grid] (fraction of canvas); grid<=0 = off. */
    fun snap(value: Float, grid: Float): Float =
        if (grid <= 0f) value
        else (Math.round(value / grid) * grid)

    // ------------------------------------------------------------------
    // Symmetry / handedness tools
    // ------------------------------------------------------------------

    /** Horizontal mirror of one control's direction-sensitive fields. */
    private fun mirrorFields(c: GamepadControlSpec): GamepadControlSpec = c.copy(
        stickSide = when (c.stickSide) {
            StickSide.LEFT -> StickSide.RIGHT
            StickSide.RIGHT -> StickSide.LEFT
        },
        arrowDirection = when (c.arrowDirection) {
            ArrowDirection.LEFT -> ArrowDirection.RIGHT
            ArrowDirection.RIGHT -> ArrowDirection.LEFT
            ArrowDirection.UP_LEFT -> ArrowDirection.UP_RIGHT
            ArrowDirection.UP_RIGHT -> ArrowDirection.UP_LEFT
            ArrowDirection.DOWN_LEFT -> ArrowDirection.DOWN_RIGHT
            ArrowDirection.DOWN_RIGHT -> ArrowDirection.DOWN_LEFT
            else -> c.arrowDirection
        }
    )

    /**
     * Symmetry tool: create a mirrored COPY of [control] on the opposite
     * side of the canvas (x' = 1 - x - w). Direction-sensitive fields
     * (stick side, arrow direction) are mirrored too.
     */
    fun mirrorCopy(control: GamepadControlSpec, newId: String): GamepadControlSpec =
        mirrorFields(control).copy(
            id = newId,
            frame = control.frame.copy(x = (1f - control.frame.x - control.frame.w))
        ).sanitized()

    /** One-tap handedness flip: mirrors EVERY control in place. */
    fun mirrorLayout(spec: GamepadLayoutSpec): GamepadLayoutSpec =
        spec.copy(controls = spec.controls.map { c ->
            mirrorFields(c).copy(
                frame = c.frame.copy(x = (1f - c.frame.x - c.frame.w))
            ).sanitized()
        })

    // ------------------------------------------------------------------
    // Smart spacing (mis-tap risk)
    // ------------------------------------------------------------------

    data class SpacingWarning(val idA: String, val idB: String, val gap: Float)

    /**
     * Finds pairs of controls whose edge-to-edge gap is below [minGap]
     * (default 2% of canvas ≈ 8dp on a phone) — the classic mis-tap zone.
     * Overlapping pairs report gap 0.
     */
    fun spacingWarnings(spec: GamepadLayoutSpec, minGap: Float = 0.02f): List<SpacingWarning> {
        val out = mutableListOf<SpacingWarning>()
        val list = spec.controls
        for (i in list.indices) for (j in i + 1 until list.size) {
            val a = list[i].frame; val b = list[j].frame
            val gapX = maxOf(b.x - (a.x + a.w), a.x - (b.x + b.w))
            val gapY = maxOf(b.y - (a.y + a.h), a.y - (b.y + b.h))
            // Separated on an axis → gap is that axis distance; overlapping
            // on both axes → gap 0.
            val gap = maxOf(gapX, gapY).coerceAtLeast(0f)
            val separated = gapX > 0f || gapY > 0f
            if (!separated || gap < minGap) {
                out += SpacingWarning(list[i].id, list[j].id, if (separated) gap else 0f)
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Alignment guides
    // ------------------------------------------------------------------

    data class AlignmentGuides(
        val verticalCenter: Boolean,     // dragged center ≈ canvas center X
        val horizontalCenter: Boolean,   // dragged center ≈ canvas center Y
        /** X centers of OTHER controls the dragged one aligns with. */
        val matchX: List<Float>,
        /** Y centers of OTHER controls the dragged one aligns with. */
        val matchY: List<Float>
    )

    /** Computes live alignment guides for the control being dragged. */
    fun alignmentGuides(
        spec: GamepadLayoutSpec,
        draggedId: String,
        tolerance: Float = 0.012f
    ): AlignmentGuides {
        val dragged = spec.controls.firstOrNull { it.id == draggedId }
            ?: return AlignmentGuides(false, false, emptyList(), emptyList())
        val cx = dragged.frame.x + dragged.frame.w / 2f
        val cy = dragged.frame.y + dragged.frame.h / 2f
        val others = spec.controls.filter { it.id != draggedId }
        return AlignmentGuides(
            verticalCenter = kotlin.math.abs(cx - 0.5f) < tolerance,
            horizontalCenter = kotlin.math.abs(cy - 0.5f) < tolerance,
            matchX = others.map { it.frame.x + it.frame.w / 2f }
                .filter { kotlin.math.abs(it - cx) < tolerance },
            matchY = others.map { it.frame.y + it.frame.h / 2f }
                .filter { kotlin.math.abs(it - cy) < tolerance }
        )
    }

    // ------------------------------------------------------------------
    // Thumb-reach ergonomics (grip presets)
    // ------------------------------------------------------------------

    enum class GripStyle { TWO_THUMB, CLAW, PALM }

    /**
     * Thumb anchor points + comfortable/stretch reach radii for a grip
     * style, in canvas fractions (landscape phone assumption). Derived
     * from published one-handed reach studies (~72mm comfortable thumb
     * arc on a 150mm-wide device ≈ 0.45 canvas fraction).
     */
    data class ReachZone(val cx: Float, val cy: Float, val comfortable: Float, val stretch: Float)

    fun reachZones(grip: GripStyle): List<ReachZone> = when (grip) {
        // Thumbs anchored at bottom corners.
        GripStyle.TWO_THUMB -> listOf(
            ReachZone(0.02f, 1.0f, 0.42f, 0.58f),
            ReachZone(0.98f, 1.0f, 0.42f, 0.58f)
        )
        // Claw: thumbs lower-middle + index fingers reach top corners.
        GripStyle.CLAW -> listOf(
            ReachZone(0.10f, 1.0f, 0.38f, 0.52f),
            ReachZone(0.90f, 1.0f, 0.38f, 0.52f),
            ReachZone(0.05f, 0.0f, 0.25f, 0.36f),
            ReachZone(0.95f, 0.0f, 0.25f, 0.36f)
        )
        // Palm: device rests on fingers, thumbs cover less area.
        GripStyle.PALM -> listOf(
            ReachZone(0.06f, 0.95f, 0.34f, 0.46f),
            ReachZone(0.94f, 0.95f, 0.34f, 0.46f)
        )
    }

    /** True when a control's center is inside any comfortable reach zone. */
    fun isComfortable(control: GamepadControlSpec, grip: GripStyle): Boolean {
        val cx = control.frame.x + control.frame.w / 2f
        val cy = control.frame.y + control.frame.h / 2f
        return reachZones(grip).any { z ->
            val dx = cx - z.cx; val dy = cy - z.cy
            kotlin.math.sqrt(dx * dx + dy * dy) <= z.comfortable
        }
    }
}
