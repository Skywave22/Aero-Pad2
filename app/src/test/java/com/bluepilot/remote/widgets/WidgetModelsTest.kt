package com.bluepilot.remote.widgets

import com.bluepilot.remote.data.layout.LayoutSerializer
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.SkinSpec
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget model validation + JSON round-trip contract.
 * These tests guarantee that no stored/imported layout can carry values
 * that break rendering, and that export → import is lossless.
 */
class WidgetModelsTest {

    // ---------- frame sanitization ----------

    @Test
    fun `frame clamps to canvas and enforces min size`() {
        val junk = WidgetFrame(x = 2f, y = -1f, w = 0.001f, h = 5f).sanitized()
        assertTrue(junk.w >= WidgetFrame.MIN_SIZE)
        assertTrue(junk.h <= 1f)
        assertTrue(junk.x + junk.w <= 1.0001f)
        assertTrue(junk.y >= 0f)
    }

    @Test
    fun `valid frame is untouched`() {
        val ok = WidgetFrame(0.1f, 0.2f, 0.3f, 0.4f)
        assertEquals(ok, ok.sanitized())
    }

    // ---------- style sanitization ----------

    @Test
    fun `style clamps opacity radius font and label length`() {
        val junk = WidgetStyle(
            opacity = 9f,
            cornerRadius = 500,
            elevation = -3,
            fontSize = 200,
            label = "x".repeat(100),
            icon = "🎮🎮🎮🎮🎮"
        ).sanitized()
        assertEquals(1f, junk.opacity, 0.001f)
        assertEquals(WidgetStyle.RADIUS_MAX, junk.cornerRadius)
        assertEquals(0, junk.elevation)
        assertEquals(WidgetStyle.FONT_MAX, junk.fontSize)
        assertEquals(WidgetStyle.LABEL_MAX, junk.label.length)
        assertTrue(junk.icon.length <= 4)
    }

    @Test
    fun `NaN opacity falls back to opaque`() {
        assertEquals(1f, WidgetStyle(opacity = Float.NaN).sanitized().opacity, 0.001f)
    }

    // ---------- layout sanitization ----------

    @Test
    fun `layout caps widget count and fixes blank name`() {
        val many = (1..100).map {
            WidgetSpec(id = "w$it", type = WidgetType.BUTTON)
        }
        val junk = LayoutSpec(name = "   ", widgets = many).sanitized()
        assertEquals(LayoutSpec.MAX_WIDGETS, junk.widgets.size)
        assertEquals("Untitled", junk.name.trim().ifBlank { "Untitled" })
    }

    // ---------- JSON round trip ----------

    @Test
    fun `layout survives JSON round trip losslessly`() {
        val original = LayoutSpec(
            name = "Test Layout",
            gridSize = 0.05f,
            widgets = listOf(
                WidgetSpec(
                    id = "b1",
                    type = WidgetType.BUTTON,
                    frame = WidgetFrame(0.1f, 0.1f, 0.3f, 0.2f),
                    style = WidgetStyle(label = "Play", icon = "▶", backgroundColor = 0xFF123456),
                    action = WidgetAction.Media(0x00CD),
                    secondaryAction = WidgetAction.KeyTap(0x29)
                ),
                WidgetSpec(id = "t1", type = WidgetType.TRACKPAD),
                WidgetSpec(id = "j1", type = WidgetType.JOYSTICK, action = WidgetAction.TypeText("hi"))
            )
        )
        val json = LayoutSerializer.layoutToJson(original)
        val decoded = LayoutSerializer.layoutFromJson(json)
        assertEquals(original.sanitized(), decoded)
    }

    @Test
    fun `all action types survive round trip`() {
        val actions = listOf(
            WidgetAction.KeyTap(0x04, 0x01),
            WidgetAction.Media(0x00E9),
            WidgetAction.MouseClick(0x02),
            WidgetAction.TypeText("hello"),
            WidgetAction.RunMacro(7L),
            WidgetAction.None
        )
        val layout = LayoutSpec(
            name = "Actions",
            widgets = actions.mapIndexed { i, action ->
                WidgetSpec(id = "w$i", type = WidgetType.BUTTON, action = action)
            }
        )
        val decoded = LayoutSerializer.layoutFromJson(LayoutSerializer.layoutToJson(layout))
        assertNotNull(decoded)
        assertEquals(actions, decoded!!.widgets.map { it.action })
    }

    @Test
    fun `corrupt JSON returns null instead of throwing`() {
        assertNull(LayoutSerializer.layoutFromJson("{ not json at all"))
        assertNull(LayoutSerializer.layoutFromJson(""))
        assertNull(LayoutSerializer.layoutFromJson(null))
        assertNull(LayoutSerializer.layoutFromJson("""{"totally":"different"}""").takeIf { it?.widgets?.isNotEmpty() == true })
    }

    @Test
    fun `unknown JSON fields are ignored - forward compatibility`() {
        val json = LayoutSerializer.layoutToJson(LayoutSpec(name = "X"))
            .removeSuffix("}") + ""","futureField":123}"""
        val decoded = LayoutSerializer.layoutFromJson(json)
        assertNotNull(decoded)
        assertEquals("X", decoded!!.name)
    }

    @Test
    fun `skin survives round trip`() {
        val skin = SkinSpec(name = "Neon", isDark = true, primary = 0xFF00FFAA)
        val decoded = LayoutSerializer.skinFromJson(LayoutSerializer.skinToJson(skin))
        assertEquals(skin.sanitized(), decoded)
    }
}
