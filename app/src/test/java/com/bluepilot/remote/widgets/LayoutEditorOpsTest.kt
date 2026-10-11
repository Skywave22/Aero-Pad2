package com.bluepilot.remote.widgets

import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Customization-engine logic contract. Every editing operation must
 * produce a valid layout no matter what input arrives.
 */
class LayoutEditorOpsTest {

    private fun layoutWith(vararg widgets: WidgetSpec) =
        LayoutSpec(name = "T", gridSize = 0.1f, widgets = widgets.toList())

    private val button = WidgetSpec(
        id = "b1",
        type = WidgetType.BUTTON,
        frame = WidgetFrame(0.2f, 0.2f, 0.3f, 0.2f)
    )

    // ---------- snapping ----------

    @Test
    fun `snap rounds to nearest grid line`() {
        assertEquals(0.1f, LayoutEditorOps.snap(0.12f, 0.1f), 0.0001f)
        assertEquals(0.2f, LayoutEditorOps.snap(0.17f, 0.1f), 0.0001f)
    }

    @Test
    fun `zero grid disables snapping`() {
        assertEquals(0.1234f, LayoutEditorOps.snap(0.1234f, 0f), 0.0001f)
    }

    // ---------- place ----------

    @Test
    fun `place moves widget with grid snap`() {
        val moved = LayoutEditorOps.place(layoutWith(button), "b1", 0.33f, 0.48f)
        val frame = moved.widgets[0].frame
        assertEquals(0.3f, frame.x, 0.0001f)
        assertEquals(0.5f, frame.y, 0.0001f)
    }

    @Test
    fun `place clamps to canvas bounds`() {
        val moved = LayoutEditorOps.place(layoutWith(button), "b1", 5f, -3f)
        val frame = moved.widgets[0].frame
        assertTrue(frame.x + frame.w <= 1.0001f)
        assertTrue(frame.y >= 0f)
    }

    @Test
    fun `place on unknown id is a no-op`() {
        val layout = layoutWith(button)
        assertEquals(layout, LayoutEditorOps.place(layout, "nope", 0.5f, 0.5f))
    }

    // ---------- resize ----------

    @Test
    fun `resize applies snap and minimum size`() {
        val resized = LayoutEditorOps.resize(layoutWith(button), "b1", 0.001f, 0.27f)
        val frame = resized.widgets[0].frame
        assertTrue(frame.w >= WidgetFrame.MIN_SIZE)
        assertEquals(0.3f, frame.h, 0.0001f)
    }

    // ---------- add / duplicate / remove ----------

    @Test
    fun `add creates widget of requested type with unique id`() {
        val (next, id) = LayoutEditorOps.add(layoutWith(button), WidgetType.JOYSTICK)
        assertNotNull(id)
        assertEquals(2, next.widgets.size)
        assertEquals(WidgetType.JOYSTICK, next.widgets.last().type)
        assertTrue(next.widgets.map { it.id }.toSet().size == 2)
    }

    @Test
    fun `add refuses beyond widget cap`() {
        val full = LayoutSpec(
            name = "full",
            widgets = (1..LayoutSpec.MAX_WIDGETS).map {
                WidgetSpec(id = "w$it", type = WidgetType.BUTTON)
            }
        )
        val (next, id) = LayoutEditorOps.add(full, WidgetType.BUTTON)
        assertNull(id)
        assertEquals(LayoutSpec.MAX_WIDGETS, next.widgets.size)
    }

    @Test
    fun `duplicate copies widget with offset and new id`() {
        val (next, newId) = LayoutEditorOps.duplicate(layoutWith(button), "b1")
        assertNotNull(newId)
        assertEquals(2, next.widgets.size)
        val copy = next.widgets.last()
        assertTrue(copy.id != "b1")
        assertEquals(button.frame.x + 0.04f, copy.frame.x, 0.0001f)
    }

    @Test
    fun `duplicate of unknown id returns null`() {
        val (next, newId) = LayoutEditorOps.duplicate(layoutWith(button), "ghost")
        assertNull(newId)
        assertEquals(1, next.widgets.size)
    }

    @Test
    fun `remove deletes only the target`() {
        val other = button.copy(id = "b2")
        val next = LayoutEditorOps.remove(layoutWith(button, other), "b1")
        assertEquals(listOf("b2"), next.widgets.map { it.id })
    }

    // ---------- style / action updates ----------

    @Test
    fun `updateStyle changes only the target widget and sanitizes`() {
        val other = button.copy(id = "b2")
        val next = LayoutEditorOps.updateStyle(layoutWith(button, other), "b1") {
            it.copy(cornerRadius = 999, label = "New")
        }
        val edited = next.widgets.first { it.id == "b1" }
        val untouched = next.widgets.first { it.id == "b2" }
        assertEquals(60, edited.style.cornerRadius) // clamped to RADIUS_MAX
        assertEquals("New", edited.style.label)
        assertEquals(button.style, untouched.style)
    }

    @Test
    fun `updateWidget can rebind action`() {
        val next = LayoutEditorOps.updateWidget(layoutWith(button), "b1") {
            it.copy(action = WidgetAction.Media(0x00E9))
        }
        assertEquals(WidgetAction.Media(0x00E9), next.widgets[0].action)
    }

    // ---------- rename ----------

    @Test
    fun `rename respects max length and rejects blank`() {
        val layout = layoutWith(button)
        assertEquals("T", LayoutEditorOps.rename(layout, "   ").name)
        val long = LayoutEditorOps.rename(layout, "x".repeat(100))
        assertEquals(LayoutSpec.NAME_MAX, long.name.length)
    }
}
