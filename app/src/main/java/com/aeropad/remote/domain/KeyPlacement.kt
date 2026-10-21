package com.aeropad.remote.domain

import com.aeropad.remote.model.widgets.WidgetFrame

/**
 * Finds an empty spot for a new key button.
 *
 * Why not drop it in the middle like the editor does? Because "add key" in My
 * Layout is used repeatedly — the user taps A, B, C… and every button must be
 * visible without dragging anything afterwards. So we walk a grid and take the
 * first cell that does not collide with an existing widget, growing the number
 * of rows until a free cell appears.
 *
 * Pure Kotlin, no Android, unit tested.
 */
object KeyPlacement {

    const val COLUMNS = 4
    const val ROWS = 8
    const val GAP = 0.01f

    /** Size of one key cell, in canvas fractions. */
    fun cell(cols: Int = COLUMNS, rows: Int = ROWS): Pair<Float, Float> {
        val w = (1f - GAP * (cols + 1)) / cols
        val h = (1f - GAP * (rows + 1)) / rows
        return w to h
    }

    private fun overlaps(a: WidgetFrame, b: WidgetFrame): Boolean {
        val ax2 = a.x + a.w
        val ay2 = a.y + a.h
        val bx2 = b.x + b.w
        val by2 = b.y + b.h
        // Touching edges are not an overlap.
        return a.x < bx2 - 0.0001f && b.x < ax2 - 0.0001f &&
            a.y < by2 - 0.0001f && b.y < ay2 - 0.0001f
    }

    /**
     * First free cell, scanning row by row (top-left first). Returns null only
     * when every cell is taken, so the caller can tell the user instead of
     * stacking invisible buttons on top of each other.
     */
    fun firstFree(
        occupied: List<WidgetFrame>,
        cols: Int = COLUMNS,
        rows: Int = ROWS
    ): WidgetFrame? {
        val (w, h) = cell(cols, rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val candidate = WidgetFrame(
                    x = GAP + col * (w + GAP),
                    y = GAP + row * (h + GAP),
                    w = w,
                    h = h
                )
                if (occupied.none { overlaps(candidate, it) }) return candidate
            }
        }
        return null
    }

    /**
     * Like [firstFree] but biased to the bottom of the canvas, which is where a
     * thumb reaches. Falls back to the plain top-left scan when the bottom is
     * full.
     */
    fun firstFreeThumbFirst(
        occupied: List<WidgetFrame>,
        cols: Int = COLUMNS,
        rows: Int = ROWS
    ): WidgetFrame? {
        val (w, h) = cell(cols, rows)
        for (row in rows - 1 downTo 0) {
            for (col in 0 until cols) {
                val candidate = WidgetFrame(
                    x = GAP + col * (w + GAP),
                    y = GAP + row * (h + GAP),
                    w = w,
                    h = h
                )
                if (occupied.none { overlaps(candidate, it) }) return candidate
            }
        }
        return null
    }
}
