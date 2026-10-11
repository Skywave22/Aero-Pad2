package com.bluepilot.remote.hid

import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharToHidMapperTest {

    @Test
    fun `lowercase letters map without shift`() {
        val stroke = CharToHidMapper.map('a')
        assertEquals(HidKeys.A, stroke?.key)
        assertEquals(0.toByte(), stroke?.modifiers)
    }

    @Test
    fun `uppercase letters map with shift`() {
        val stroke = CharToHidMapper.map('Z')
        assertEquals(HidKeys.Z, stroke?.key)
        assertEquals(HidModifiers.LEFT_SHIFT, stroke?.modifiers)
    }

    @Test
    fun `digits map to number row`() {
        assertEquals(HidKeys.NUM_1, CharToHidMapper.map('1')?.key)
        assertEquals(HidKeys.NUM_0, CharToHidMapper.map('0')?.key)
    }

    @Test
    fun `shifted symbols map to digit keys with shift`() {
        val at = CharToHidMapper.map('@')
        assertEquals(HidKeys.NUM_2, at?.key)
        assertEquals(HidModifiers.LEFT_SHIFT, at?.modifiers)
    }

    @Test
    fun `whitespace maps to space enter tab`() {
        assertEquals(HidKeys.SPACE, CharToHidMapper.map(' ')?.key)
        assertEquals(HidKeys.ENTER, CharToHidMapper.map('\n')?.key)
        assertEquals(HidKeys.TAB, CharToHidMapper.map('\t')?.key)
    }

    @Test
    fun `punctuation maps correctly`() {
        assertEquals(HidKeys.PERIOD, CharToHidMapper.map('.')?.key)
        val question = CharToHidMapper.map('?')
        assertEquals(HidKeys.SLASH, question?.key)
        assertEquals(HidModifiers.LEFT_SHIFT, question?.modifiers)
    }

    @Test
    fun `unmappable characters return null`() {
        assertNull(CharToHidMapper.map('é'))
        assertNull(CharToHidMapper.map('中'))
    }

    @Test
    fun `mapText skips unmappable chars and keeps order`() {
        val strokes = CharToHidMapper.mapText("Hi é!")
        // H, i, space, ! (é skipped)
        assertEquals(4, strokes.size)
        assertEquals(HidKeys.H, strokes[0].key)
        assertEquals(HidModifiers.LEFT_SHIFT, strokes[0].modifiers)
        assertEquals(HidKeys.I, strokes[1].key)
        assertEquals(HidKeys.SPACE, strokes[2].key)
        assertEquals(HidKeys.NUM_1, strokes[3].key)
    }
}
