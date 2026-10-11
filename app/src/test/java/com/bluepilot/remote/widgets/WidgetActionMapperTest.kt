package com.bluepilot.remote.widgets

import com.bluepilot.remote.domain.WidgetActionMapper
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.model.widgets.WidgetAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WidgetAction → HidAction translation contract. */
class WidgetActionMapperTest {

    @Test
    fun `key tap maps with modifiers`() {
        val hid = WidgetActionMapper.toHidAction(WidgetAction.KeyTap(0x04, 0x02))
        assertEquals(HidAction.KeyTap(0x04, 0x02), hid)
    }

    @Test
    fun `media maps to consumer tap`() {
        assertEquals(HidAction.MediaTap(0x00CD), WidgetActionMapper.toHidAction(WidgetAction.Media(0x00CD)))
    }

    @Test
    fun `mouse masks map to correct buttons`() {
        assertEquals(HidAction.MouseClick(MouseButton.LEFT), WidgetActionMapper.toHidAction(WidgetAction.MouseClick(0x01)))
        assertEquals(HidAction.MouseClick(MouseButton.RIGHT), WidgetActionMapper.toHidAction(WidgetAction.MouseClick(0x02)))
        assertEquals(HidAction.MouseClick(MouseButton.MIDDLE), WidgetActionMapper.toHidAction(WidgetAction.MouseClick(0x04)))
    }

    @Test
    fun `invalid mouse mask is rejected`() {
        assertNull(WidgetActionMapper.toHidAction(WidgetAction.MouseClick(0x08)))
        assertNull(WidgetActionMapper.toHidAction(WidgetAction.MouseClick(0)))
        assertNull(WidgetActionMapper.toHidAction(WidgetAction.MouseClick(-1)))
    }

    @Test
    fun `text maps and is capped at 500 chars`() {
        val long = "a".repeat(600)
        val hid = WidgetActionMapper.toHidAction(WidgetAction.TypeText(long)) as HidAction.TypeText
        assertEquals(500, hid.text.length)
    }

    @Test
    fun `empty text and none produce nothing`() {
        assertNull(WidgetActionMapper.toHidAction(WidgetAction.TypeText("")))
        assertNull(WidgetActionMapper.toHidAction(WidgetAction.None))
    }

    @Test
    fun `run macro defers to macro engine`() {
        assertNull(WidgetActionMapper.toHidAction(WidgetAction.RunMacro(3L)))
    }
}
