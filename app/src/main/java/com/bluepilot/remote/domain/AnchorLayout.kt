package com.bluepilot.remote.domain

import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec

/**
 * V2 MATRIX 1 b5 — anchor & alignment ops for gamepad controls (pure).
 *
 * Design decision (honest): rather than a full CSS-flexbox engine (heavy,
 * risky for existing layouts), we provide deterministic anchor operations
 * that COMPUTE fractional positions once. Storage stays plain frames —
 * zero migration risk, layouts remain fully draggable afterward.
 */
object AnchorLayout {

    enum class Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    /** Anchor one control to a canvas position with [marginFrac] inset. */
    fun anchor(spec: GamepadLayoutSpec, id: String, anchor: Anchor, marginFrac: Float = 0.02f): GamepadLayoutSpec {
        val m = marginFrac.coerceIn(0f, 0.2f)
        return spec.copy(controls = spec.controls.map { c ->
            if (c.id != id) c else {
                val f = c.frame
                val x = when (anchor) {
                    Anchor.TOP_LEFT, Anchor.CENTER_LEFT, Anchor.BOTTOM_LEFT -> m
                    Anchor.TOP_CENTER, Anchor.CENTER, Anchor.BOTTOM_CENTER -> (1f - f.w) / 2f
                    else -> 1f - f.w - m
                }
                val y = when (anchor) {
                    Anchor.TOP_LEFT, Anchor.TOP_CENTER, Anchor.TOP_RIGHT -> m
                    Anchor.CENTER_LEFT, Anchor.CENTER, Anchor.CENTER_RIGHT -> (1f - f.h) / 2f
                    else -> 1f - f.h - m
                }
                c.copy(frame = f.copy(x = x, y = y).sanitized()).sanitized()
            }
        })
    }

    /** Align control [id] edge-to-edge NEXT TO control [relativeToId]. */
    enum class Side { LEFT_OF, RIGHT_OF, ABOVE, BELOW }

    fun placeRelative(
        spec: GamepadLayoutSpec, id: String, relativeToId: String,
        side: Side, gapFrac: Float = 0.015f
    ): GamepadLayoutSpec {
        val ref = spec.controls.firstOrNull { it.id == relativeToId } ?: return spec
        val g = gapFrac.coerceIn(0f, 0.2f)
        return spec.copy(controls = spec.controls.map { c ->
            if (c.id != id) c else {
                val f = c.frame; val r = ref.frame
                val (x, y) = when (side) {
                    Side.LEFT_OF -> (r.x - f.w - g) to r.y
                    Side.RIGHT_OF -> (r.x + r.w + g) to r.y
                    Side.ABOVE -> r.x to (r.y - f.h - g)
                    Side.BELOW -> r.x to (r.y + r.h + g)
                }
                c.copy(frame = f.copy(x = x, y = y).sanitized()).sanitized()
            }
        })
    }
}
