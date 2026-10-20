package com.aeropad.remote.widgets

import com.aeropad.remote.domain.WidgetActionMapper
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.model.widgets.WidgetAction
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

    @Test
    fun `toggleNext cycles through non-empty actions`() {
        val toggle = WidgetAction.ToggleNext(
            listOf(
                WidgetAction.Media(0x00CD),
                WidgetAction.MouseClick(0x01)
            )
        )
        val first = WidgetActionMapper.resolveToggle(toggle, 0)
        val second = WidgetActionMapper.resolveToggle(toggle, 1)
        val third = WidgetActionMapper.resolveToggle(toggle, 2)
        assertEquals(HidAction.MediaTap(0x00CD), WidgetActionMapper.toHidAction(first))
        assertEquals(HidAction.MouseClick(MouseButton.LEFT), WidgetActionMapper.toHidAction(second))
        assertEquals(HidAction.MediaTap(0x00CD), WidgetActionMapper.toHidAction(third))
    }
}
