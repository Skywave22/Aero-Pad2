package com.aeropad.remote.features

import com.aeropad.remote.data.tv.WifiTvActionMapper
import com.aeropad.remote.model.GamepadSnapshot
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConsumer
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiTvActionMapperTest {
    @Test fun `plain keyboard taps map to Android TV key events`() {
        assertEquals(listOf("29"), WifiTvActionMapper.commands(HidAction.KeyTap(HidKeys.A)))
        assertEquals(listOf("66"), WifiTvActionMapper.commands(HidAction.KeyTap(HidKeys.ENTER)))
        assertEquals(listOf("19"), WifiTvActionMapper.commands(HidAction.KeyTap(HidKeys.ARROW_UP)))
        assertEquals(listOf("8"), WifiTvActionMapper.commands(HidAction.KeyTap(HidKeys.NUM_1)))
    }

    @Test fun `text mouse and media actions map to TV protocol commands`() {
        assertEquals(listOf("key=search=hello"), WifiTvActionMapper.commands(HidAction.TypeText("hello")))
        assertEquals(listOf("key=mouseaction;-4=6"), WifiTvActionMapper.commands(HidAction.MouseMove(-4, 6)))
        assertEquals(listOf("key=clickmouse"), WifiTvActionMapper.commands(HidAction.MouseClick(MouseButton.LEFT)))
        assertEquals(listOf("24"), WifiTvActionMapper.commands(HidAction.MediaTap(HidConsumer.VOLUME_UP)))
        assertEquals(listOf("85"), WifiTvActionMapper.commands(HidAction.MediaTap(HidConsumer.PLAY_PAUSE)))
    }

    @Test fun `unrepresentable HID reports are not falsely mapped to Wi-Fi`() {
        assertNull(WifiTvActionMapper.commands(HidAction.KeyTap(HidKeys.A, HidModifiers.LEFT_CTRL)))
        assertNull(WifiTvActionMapper.commands(HidAction.KeyDown(HidKeys.A)))
        assertNull(WifiTvActionMapper.commands(HidAction.MouseClick(MouseButton.RIGHT)))
        assertNull(WifiTvActionMapper.commands(HidAction.GamepadUpdate(GamepadSnapshot())))
        assertNull(WifiTvActionMapper.commands(HidAction.TouchUpdate(true, .5f, .5f)))
    }
}
