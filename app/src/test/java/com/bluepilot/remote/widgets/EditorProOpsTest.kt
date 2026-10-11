package com.bluepilot.remote.widgets

import com.bluepilot.remote.data.layout.LayoutSerializer
import com.bluepilot.remote.domain.EditorProOps
import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SECTION 2 — Combo Maker upgrade: pure ops + round-trip proof. */
class EditorProOpsTest {

    private fun widget(id: String, x: Float = 0.1f, y: Float = 0.1f, w: Float = 0.2f, h: Float = 0.1f) =
        WidgetSpec(id = id, type = WidgetType.BUTTON, frame = WidgetFrame(x, y, w, h))

    private fun layout(vararg ws: WidgetSpec) = LayoutSpec(name = "T", gridSize = 0f, widgets = ws.toList())

    // ---------- Layering ----------

    @Test
    fun `bringToFront moves widget to end of list`() {
        val l = layout(widget("a"), widget("b"), widget("c"))
        val r = EditorProOps.bringToFront(l, "a")
        assertEquals(listOf("b", "c", "a"), r.widgets.map { it.id })
    }

    @Test
    fun `sendToBack moves widget to start of list`() {
        val l = layout(widget("a"), widget("b"), widget("c"))
        val r = EditorProOps.sendToBack(l, "c")
        assertEquals(listOf("c", "a", "b"), r.widgets.map { it.id })
    }

    // ---------- Alignment ----------

    @Test
    fun `centerHorizontally centers on canvas`() {
        val r = EditorProOps.centerHorizontally(layout(widget("a", w = 0.4f)), "a")
        assertEquals(0.3f, r.widgets[0].frame.x, 1e-4f)
    }

    @Test
    fun `snapToEdge right puts widget at right margin`() {
        val r = EditorProOps.snapToEdge(layout(widget("a", w = 0.2f)), "a", EditorProOps.Edge.RIGHT)
        assertEquals(0.78f, r.widgets[0].frame.x, 1e-4f)
    }

    @Test
    fun `distributeHorizontally spaces three evenly`() {
        val l = layout(widget("a", x = 0.0f), widget("b", x = 0.1f), widget("c", x = 0.6f))
        val r = EditorProOps.distributeHorizontally(l, listOf("a", "b", "c"))
        val xs = r.widgets.associate { it.id to it.frame.x }
        assertEquals(0.0f, xs["a"]!!, 1e-4f)
        assertEquals(0.3f, xs["b"]!!, 1e-4f)
        assertEquals(0.6f, xs["c"]!!, 1e-4f)
    }

    // ---------- Clipboard / multi ----------

    @Test
    fun `paste creates new id offset position`() {
        val src = widget("a")
        val (r, newId) = EditorProOps.paste(layout(src), src)
        assertNotNull(newId)
        assertEquals(2, r.widgets.size)
        assertTrue(r.widgets.last().id != "a")
        assertEquals(0.15f, r.widgets.last().frame.x, 1e-4f)
    }

    @Test
    fun `moveMany shifts only selected`() {
        val l = layout(widget("a"), widget("b"), widget("c"))
        val r = EditorProOps.moveMany(l, setOf("a", "b"), 0.1f, 0.05f)
        assertEquals(0.2f, r.widgets[0].frame.x, 1e-4f)
        assertEquals(0.2f, r.widgets[1].frame.x, 1e-4f)
        assertEquals(0.1f, r.widgets[2].frame.x, 1e-4f)
    }

    @Test
    fun `removeMany deletes all selected`() {
        val r = EditorProOps.removeMany(layout(widget("a"), widget("b"), widget("c")), setOf("a", "c"))
        assertEquals(listOf("b"), r.widgets.map { it.id })
    }

    // ---------- Grouping ----------

    @Test
    fun `group then members then ungroup`() {
        val l = layout(widget("a"), widget("b"), widget("c"))
        val (grouped, gid) = EditorProOps.group(l, setOf("a", "b"))
        assertTrue(gid.isNotBlank())
        assertEquals(setOf("a", "b"), EditorProOps.groupMembers(grouped, "a"))
        assertEquals(setOf("c"), EditorProOps.groupMembers(grouped, "c"))
        val ungrouped = EditorProOps.ungroup(grouped, gid)
        assertTrue(ungrouped.widgets.all { it.groupId.isBlank() })
    }

    // ---------- REQUIRED PROOF: 5 element types round-trip ----------

    @Test
    fun `combo with 5 element types saves and loads with full fidelity`() {
        // Build a combo using 5 different element types with custom
        // positions, sizes, colors, actions and a group.
        var spec = LayoutSpec(name = "Proof", gridSize = 0.025f, category = "Test", notes = "5-type proof")
        val types = listOf(
            WidgetType.BUTTON, WidgetType.TRACKPAD, WidgetType.JOYSTICK,
            WidgetType.SLIDER, WidgetType.GESTURE_ZONE
        )
        types.forEach { t -> spec = LayoutEditorOps.add(spec, t).first }
        assertEquals(5, spec.widgets.size)

        // Customize each: unique frame, color, label, action.
        spec = spec.copy(widgets = spec.widgets.mapIndexed { i, w ->
            w.copy(
                frame = WidgetFrame(0.05f + i * 0.1f, 0.05f + i * 0.12f, 0.25f, 0.14f),
                style = WidgetStyle(
                    label = "El$i",
                    backgroundColor = 0xFF102030L + i,
                    opacity = 0.5f + i * 0.1f,
                    cornerRadius = 4 + i
                ),
                action = WidgetAction.KeyTap((HidKeys.A + i).toByte())
            ).sanitized()
        })
        // Group the first two.
        spec = EditorProOps.group(spec, spec.widgets.take(2).map { it.id }.toSet()).first

        // Round-trip through the EXACT persistence format (Room column JSON).
        val json = LayoutSerializer.layoutToJson(spec)
        val loaded = LayoutSerializer.layoutFromJson(json)

        assertNotNull(loaded)
        loaded!!
        assertEquals("Proof", loaded.name)
        assertEquals("Test", loaded.category)
        assertEquals("5-type proof", loaded.notes)
        assertEquals(5, loaded.widgets.size)
        assertEquals(types, loaded.widgets.map { it.type })
        loaded.widgets.forEachIndexed { i, w ->
            assertEquals(0.05f + i * 0.1f, w.frame.x, 1e-4f)
            assertEquals("El$i", w.style.label)
            assertEquals(0xFF102030L + i, w.style.backgroundColor)
            assertEquals(WidgetAction.KeyTap((HidKeys.A + i).toByte()), w.action)
        }
        // Group survived the round-trip.
        assertEquals(
            EditorProOps.groupMembers(spec, spec.widgets[0].id),
            EditorProOps.groupMembers(loaded, loaded.widgets[0].id)
        )
    }

    @Test
    fun `legacy JSON without new fields still loads`() {
        // Pre-upgrade layout JSON: no groupId, no category, no notes.
        val legacy = """{"name":"Old","gridSize":0.025,"widgets":[
            {"id":"w1","type":"BUTTON","frame":{"x":0.1,"y":0.1,"w":0.3,"h":0.1},
             "style":{"label":"OK"},"action":{"kind":"none"}}]}"""
        val loaded = LayoutSerializer.layoutFromJson(legacy)
        assertNotNull(loaded)
        assertEquals("", loaded!!.widgets[0].groupId)
        assertEquals("", loaded.category)
    }
}
