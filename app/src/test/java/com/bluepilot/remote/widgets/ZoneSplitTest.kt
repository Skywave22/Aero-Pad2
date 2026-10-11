package com.bluepilot.remote.widgets

import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zone splitter contract: zones fill the canvas without overlap or overflow. */
class ZoneSplitTest {

    @Test
    fun `every split produces frames inside the canvas`() {
        LayoutEditorOps.ZoneSplit.entries.forEach { split ->
            LayoutEditorOps.zoneFrames(split).forEach { frame ->
                val f = frame.sanitized()
                assertTrue("$split x out of bounds", f.x >= 0f && f.x + f.w <= 1.0001f)
                assertTrue("$split y out of bounds", f.y >= 0f && f.y + f.h <= 1.0001f)
            }
        }
    }

    @Test
    fun `horizontal split zones do not overlap vertically`() {
        val frames = LayoutEditorOps.zoneFrames(LayoutEditorOps.ZoneSplit.HORIZONTAL_2)
        assertEquals(2, frames.size)
        assertTrue(frames[0].y + frames[0].h <= frames[1].y + 0.0001f)
    }

    @Test
    fun `grid split produces four zones`() {
        assertEquals(4, LayoutEditorOps.zoneFrames(LayoutEditorOps.ZoneSplit.GRID_2X2).size)
    }

    @Test
    fun `applyZones adds one widget per zone with requested types`() {
        val layout = LayoutSpec(name = "z")
        val result = LayoutEditorOps.applyZones(
            layout,
            LayoutEditorOps.ZoneSplit.TOP_BOTTOM_THIRDS,
            listOf(WidgetType.TRACKPAD, WidgetType.DPAD)
        )
        assertEquals(2, result.widgets.size)
        assertEquals(WidgetType.TRACKPAD, result.widgets[0].type)
        assertEquals(WidgetType.DPAD, result.widgets[1].type)
        // Trackpad zone is the big top zone.
        assertTrue(result.widgets[0].frame.h > result.widgets[1].frame.h)
    }

    @Test
    fun `applyZones defaults missing types to button`() {
        val result = LayoutEditorOps.applyZones(
            LayoutSpec(name = "z"),
            LayoutEditorOps.ZoneSplit.GRID_2X2,
            listOf(WidgetType.JOYSTICK) // only 1 of 4 provided
        )
        assertEquals(4, result.widgets.size)
        assertEquals(WidgetType.JOYSTICK, result.widgets[0].type)
        assertTrue(result.widgets.drop(1).all { it.type == WidgetType.BUTTON })
    }

    @Test
    fun `applyZones respects the widget cap`() {
        val nearlyFull = LayoutSpec(
            name = "full",
            widgets = (1 until LayoutSpec.MAX_WIDGETS).map {
                WidgetSpec(id = "w$it", type = WidgetType.BUTTON)
            }
        )
        val result = LayoutEditorOps.applyZones(
            nearlyFull,
            LayoutEditorOps.ZoneSplit.GRID_2X2,
            listOf(WidgetType.BUTTON, WidgetType.BUTTON, WidgetType.BUTTON, WidgetType.BUTTON)
        )
        assertTrue(result.widgets.size <= LayoutSpec.MAX_WIDGETS)
    }
}
