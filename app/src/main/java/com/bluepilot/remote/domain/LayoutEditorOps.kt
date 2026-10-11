package com.bluepilot.remote.domain

import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Pure layout-editing operations — the whole customization engine's logic
 * with zero Android dependencies, so every operation is unit-tested.
 *
 * All operations return a NEW LayoutSpec (immutable editing) and always
 * sanitize, so an editor bug can never persist an invalid layout.
 */
object LayoutEditorOps {

    /** Snap a coordinate to the grid (grid<=0 disables snapping). */
    fun snap(value: Float, grid: Float): Float =
        if (grid <= 0f) value else (value / grid).roundToInt() * grid

    /** Place a widget at an absolute fractional position (snap + clamp). */
    fun place(layout: LayoutSpec, id: String, x: Float, y: Float): LayoutSpec =
        updateWidget(layout, id) { w ->
            w.copy(
                frame = w.frame.copy(
                    x = snap(x, layout.gridSize),
                    y = snap(y, layout.gridSize)
                ).sanitized()
            )
        }

    /** Resize a widget to absolute fractional dimensions (snap + clamp). */
    fun resize(layout: LayoutSpec, id: String, w: Float, h: Float): LayoutSpec =
        updateWidget(layout, id) { widget ->
            widget.copy(
                frame = widget.frame.copy(
                    w = snap(w, layout.gridSize).coerceAtLeast(WidgetFrame.MIN_SIZE),
                    h = snap(h, layout.gridSize).coerceAtLeast(WidgetFrame.MIN_SIZE)
                ).sanitized()
            )
        }

    /** Generic single-widget transform (sanitizes the result). */
    fun updateWidget(layout: LayoutSpec, id: String, transform: (WidgetSpec) -> WidgetSpec): LayoutSpec =
        layout.copy(
            widgets = layout.widgets.map { if (it.id == id) transform(it).sanitized() else it }
        )

    /** Update only the style of a widget. */
    fun updateStyle(layout: LayoutSpec, id: String, transform: (WidgetStyle) -> WidgetStyle): LayoutSpec =
        updateWidget(layout, id) { it.copy(style = transform(it.style)) }

    /** Add a new widget of [type] near the canvas center. Returns (layout, newId). */
    fun add(layout: LayoutSpec, type: WidgetType): Pair<LayoutSpec, String?> {
        if (layout.widgets.size >= LayoutSpec.MAX_WIDGETS) return layout to null
        val id = UUID.randomUUID().toString()
        val defaults = when (type) {
            WidgetType.BUTTON -> WidgetFrame(0.35f, 0.4f, 0.3f, 0.12f)
            WidgetType.TRACKPAD -> WidgetFrame(0.1f, 0.1f, 0.6f, 0.4f)
            WidgetType.SCROLL_STRIP -> WidgetFrame(0.8f, 0.1f, 0.15f, 0.4f)
            WidgetType.JOYSTICK -> WidgetFrame(0.3f, 0.5f, 0.4f, 0.3f)
            WidgetType.SLIDER -> WidgetFrame(0.8f, 0.5f, 0.15f, 0.35f)
            WidgetType.DPAD -> WidgetFrame(0.3f, 0.55f, 0.4f, 0.35f)
            WidgetType.GESTURE_ZONE -> WidgetFrame(0.15f, 0.15f, 0.7f, 0.4f)
        }
        val label = when (type) {
            WidgetType.BUTTON -> "Button"
            WidgetType.TRACKPAD -> "Trackpad"
            WidgetType.SCROLL_STRIP -> "Scroll"
            WidgetType.JOYSTICK -> ""
            WidgetType.SLIDER -> ""
            WidgetType.DPAD -> ""
            WidgetType.GESTURE_ZONE -> "Swipe zone"
        }
        val widget = WidgetSpec(
            id = id,
            type = type,
            frame = defaults,
            style = WidgetStyle(label = label)
        ).sanitized()
        return layout.copy(widgets = layout.widgets + widget) to id
    }

    /** Duplicate a widget slightly offset. Returns (layout, newId or null). */
    fun duplicate(layout: LayoutSpec, id: String): Pair<LayoutSpec, String?> {
        if (layout.widgets.size >= LayoutSpec.MAX_WIDGETS) return layout to null
        val source = layout.widgets.firstOrNull { it.id == id } ?: return layout to null
        val newId = UUID.randomUUID().toString()
        val copy = source.copy(
            id = newId,
            frame = source.frame.copy(
                x = source.frame.x + 0.04f,
                y = source.frame.y + 0.04f
            ).sanitized()
        )
        return layout.copy(widgets = layout.widgets + copy) to newId
    }

    /** Remove a widget by id. */
    fun remove(layout: LayoutSpec, id: String): LayoutSpec =
        layout.copy(widgets = layout.widgets.filterNot { it.id == id })

    /** Rename the layout (validated). */
    fun rename(layout: LayoutSpec, name: String): LayoutSpec =
        layout.copy(name = name.take(LayoutSpec.NAME_MAX).ifBlank { layout.name })

    // ------------------------------------------------------------------
    // Zone splitter (combo system)
    // ------------------------------------------------------------------

    /** How to split the canvas into zones. */
    enum class ZoneSplit { HORIZONTAL_2, VERTICAL_2, GRID_2X2, TOP_BOTTOM_THIRDS, QUARTER_MAIN, CORNER_ZONES }

    /** Fractional rectangles produced by a split (with a small gutter). */
    fun zoneFrames(split: ZoneSplit): List<WidgetFrame> {
        val g = 0.02f // gutter between zones
        return when (split) {
            ZoneSplit.HORIZONTAL_2 -> listOf(
                WidgetFrame(g, g, 1f - 2 * g, 0.5f - 1.5f * g),
                WidgetFrame(g, 0.5f + 0.5f * g, 1f - 2 * g, 0.5f - 1.5f * g)
            )
            ZoneSplit.VERTICAL_2 -> listOf(
                WidgetFrame(g, g, 0.5f - 1.5f * g, 1f - 2 * g),
                WidgetFrame(0.5f + 0.5f * g, g, 0.5f - 1.5f * g, 1f - 2 * g)
            )
            ZoneSplit.GRID_2X2 -> listOf(
                WidgetFrame(g, g, 0.5f - 1.5f * g, 0.5f - 1.5f * g),
                WidgetFrame(0.5f + 0.5f * g, g, 0.5f - 1.5f * g, 0.5f - 1.5f * g),
                WidgetFrame(g, 0.5f + 0.5f * g, 0.5f - 1.5f * g, 0.5f - 1.5f * g),
                WidgetFrame(0.5f + 0.5f * g, 0.5f + 0.5f * g, 0.5f - 1.5f * g, 0.5f - 1.5f * g)
            )
            ZoneSplit.TOP_BOTTOM_THIRDS -> listOf(
                WidgetFrame(g, g, 1f - 2 * g, 0.62f - 1.5f * g),
                WidgetFrame(g, 0.62f + 0.5f * g, 1f - 2 * g, 0.38f - 1.5f * g)
            )
            // Quarter-main: big 3/4 main zone + right column split in two.
            ZoneSplit.QUARTER_MAIN -> listOf(
                WidgetFrame(g, g, 0.72f - 1.5f * g, 1f - 2 * g),
                WidgetFrame(0.72f + 0.5f * g, g, 0.28f - 1.5f * g, 0.5f - g),
                WidgetFrame(0.72f + 0.5f * g, 0.5f + 0.5f * g, 0.28f - 1.5f * g, 0.5f - 1.5f * g)
            )
            // Corner zones: 4 corner pads + center strip.
            ZoneSplit.CORNER_ZONES -> listOf(
                WidgetFrame(g, g, 0.30f, 0.30f),
                WidgetFrame(0.68f, g, 0.30f, 0.30f),
                WidgetFrame(g, 0.68f, 0.30f, 0.30f),
                WidgetFrame(0.68f, 0.68f, 0.30f, 0.30f),
                WidgetFrame(0.34f, 0.36f, 0.32f, 0.28f)
            )
        }
    }

    /**
     * Apply a zone split: adds one widget of [types] per zone, sized to fill
     * that zone. Types beyond the zone count are ignored; missing types
     * default to BUTTON. Respects the widget cap.
     */
    fun applyZones(layout: LayoutSpec, split: ZoneSplit, types: List<WidgetType>): LayoutSpec {
        val frames = zoneFrames(split)
        var result = layout
        frames.forEachIndexed { index, frame ->
            if (result.widgets.size >= LayoutSpec.MAX_WIDGETS) return result
            val type = types.getOrNull(index) ?: WidgetType.BUTTON
            val (next, newId) = add(result, type)
            result = if (newId != null) {
                updateWidget(next, newId) { it.copy(frame = frame.sanitized()) }
            } else next
        }
        return result
    }
}

// ----------------------------------------------------------------------
// SECTION 2 — Combo Maker upgrade: pure ops (all unit-tested)
// ----------------------------------------------------------------------

/**
 * Professional editing operations added by the Combo Maker upgrade:
 * alignment, distribution, layering, nudge, clipboard paste, multi-select
 * and grouping. All are pure functions on LayoutSpec.
 */
object EditorProOps {

    // ---------- Layering (list order = z-order; later = on top) ----------

    fun bringToFront(layout: LayoutSpec, id: String): LayoutSpec {
        val w = layout.widgets.firstOrNull { it.id == id } ?: return layout
        return layout.copy(widgets = layout.widgets.filterNot { it.id == id } + w)
    }

    fun sendToBack(layout: LayoutSpec, id: String): LayoutSpec {
        val w = layout.widgets.firstOrNull { it.id == id } ?: return layout
        return layout.copy(widgets = listOf(w) + layout.widgets.filterNot { it.id == id })
    }

    // ---------- Alignment ----------

    fun centerHorizontally(layout: LayoutSpec, id: String): LayoutSpec =
        LayoutEditorOps.updateWidget(layout, id) { w ->
            w.copy(frame = w.frame.copy(x = (1f - w.frame.w) / 2f).sanitized())
        }

    fun centerVertically(layout: LayoutSpec, id: String): LayoutSpec =
        LayoutEditorOps.updateWidget(layout, id) { w ->
            w.copy(frame = w.frame.copy(y = (1f - w.frame.h) / 2f).sanitized())
        }

    enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

    fun snapToEdge(layout: LayoutSpec, id: String, edge: Edge, margin: Float = 0.02f): LayoutSpec =
        LayoutEditorOps.updateWidget(layout, id) { w ->
            val f = w.frame
            val nf = when (edge) {
                Edge.LEFT -> f.copy(x = margin)
                Edge.RIGHT -> f.copy(x = 1f - f.w - margin)
                Edge.TOP -> f.copy(y = margin)
                Edge.BOTTOM -> f.copy(y = 1f - f.h - margin)
            }
            w.copy(frame = nf.sanitized())
        }

    /** Distribute [ids] evenly along X (by left edge order). */
    fun distributeHorizontally(layout: LayoutSpec, ids: List<String>): LayoutSpec {
        if (ids.size < 3) return layout
        val sel = layout.widgets.filter { it.id in ids }.sortedBy { it.frame.x }
        if (sel.size < 3) return layout
        val first = sel.first().frame.x
        val last = sel.last().frame.x
        val step = (last - first) / (sel.size - 1)
        var result = layout
        sel.forEachIndexed { i, w ->
            result = LayoutEditorOps.updateWidget(result, w.id) {
                it.copy(frame = it.frame.copy(x = first + step * i).sanitized())
            }
        }
        return result
    }

    /** Distribute [ids] evenly along Y (by top edge order). */
    fun distributeVertically(layout: LayoutSpec, ids: List<String>): LayoutSpec {
        if (ids.size < 3) return layout
        val sel = layout.widgets.filter { it.id in ids }.sortedBy { it.frame.y }
        if (sel.size < 3) return layout
        val first = sel.first().frame.y
        val last = sel.last().frame.y
        val step = (last - first) / (sel.size - 1)
        var result = layout
        sel.forEachIndexed { i, w ->
            result = LayoutEditorOps.updateWidget(result, w.id) {
                it.copy(frame = it.frame.copy(y = first + step * i).sanitized())
            }
        }
        return result
    }

    // ---------- Nudge (precise keyboard-style movement) ----------

    fun nudge(layout: LayoutSpec, id: String, dx: Float, dy: Float): LayoutSpec =
        LayoutEditorOps.updateWidget(layout, id) { w ->
            w.copy(frame = w.frame.copy(x = w.frame.x + dx, y = w.frame.y + dy).sanitized())
        }

    // ---------- Clipboard ----------

    /** Paste a copied widget as a NEW widget slightly offset. */
    fun paste(layout: LayoutSpec, clip: WidgetSpec): Pair<LayoutSpec, String?> {
        if (layout.widgets.size >= LayoutSpec.MAX_WIDGETS) return layout to null
        val newId = UUID.randomUUID().toString()
        val w = clip.copy(
            id = newId,
            frame = clip.frame.copy(x = clip.frame.x + 0.05f, y = clip.frame.y + 0.05f).sanitized()
        ).sanitized()
        return layout.copy(widgets = layout.widgets + w) to newId
    }

    // ---------- Multi-select ----------

    fun moveMany(layout: LayoutSpec, ids: Set<String>, dx: Float, dy: Float): LayoutSpec =
        layout.copy(widgets = layout.widgets.map { w ->
            if (w.id in ids)
                w.copy(frame = w.frame.copy(x = w.frame.x + dx, y = w.frame.y + dy).sanitized())
            else w
        })

    fun removeMany(layout: LayoutSpec, ids: Set<String>): LayoutSpec =
        layout.copy(widgets = layout.widgets.filterNot { it.id in ids })

    fun duplicateMany(layout: LayoutSpec, ids: Set<String>): LayoutSpec {
        var result = layout
        ids.forEach { id ->
            if (result.widgets.size < LayoutSpec.MAX_WIDGETS) {
                result = LayoutEditorOps.duplicate(result, id).first
            }
        }
        return result
    }

    // ---------- Grouping ----------

    /** Assign all [ids] one new group. Returns (layout, groupId). */
    fun group(layout: LayoutSpec, ids: Set<String>): Pair<LayoutSpec, String> {
        val gid = UUID.randomUUID().toString()
        return layout.copy(widgets = layout.widgets.map { w ->
            if (w.id in ids) w.copy(groupId = gid) else w
        }) to gid
    }

    fun ungroup(layout: LayoutSpec, groupId: String): LayoutSpec =
        layout.copy(widgets = layout.widgets.map { w ->
            if (w.groupId == groupId) w.copy(groupId = "") else w
        })

    /** Ids of every widget sharing [id]'s group (incl. itself). */
    fun groupMembers(layout: LayoutSpec, id: String): Set<String> {
        val w = layout.widgets.firstOrNull { it.id == id } ?: return setOf(id)
        if (w.groupId.isBlank()) return setOf(id)
        return layout.widgets.filter { it.groupId == w.groupId }.map { it.id }.toSet()
    }
}
