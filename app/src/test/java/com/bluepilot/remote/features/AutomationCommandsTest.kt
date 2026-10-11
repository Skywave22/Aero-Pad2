package com.bluepilot.remote.features

import com.bluepilot.remote.domain.AutomationCommands
import com.bluepilot.remote.model.HidConsumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** V2 MATRIX 8 b2 — automation command parsing (total, never throws). */
class AutomationCommandsTest {

    @Test
    fun `macro command parses with trimmed name`() {
        val c = AutomationCommands.parse("macro", "  Open browser ", null, null)
        assertEquals(AutomationCommands.Command.PlayMacro("Open browser"), c)
    }

    @Test
    fun `macro rejects blank and oversized names`() {
        assertNull(AutomationCommands.parse("macro", "   ", null, null))
        assertNull(AutomationCommands.parse("macro", null, null, null))
        assertNull(AutomationCommands.parse("macro", "x".repeat(41), null, null))
    }

    @Test
    fun `media keys map to real consumer usages`() {
        assertEquals(
            AutomationCommands.Command.MediaKey(HidConsumer.PLAY_PAUSE),
            AutomationCommands.parse("media", null, "play_pause", null)
        )
        assertEquals(
            AutomationCommands.Command.MediaKey(HidConsumer.VOLUME_UP),
            AutomationCommands.parse("MEDIA", null, "VOL_UP", null)   // case-insensitive
        )
        assertEquals(
            AutomationCommands.Command.MediaKey(HidConsumer.NEXT_TRACK),
            AutomationCommands.parse("media", null, "next", null)
        )
        assertNull(AutomationCommands.parse("media", null, "laser_beam", null))
        assertNull(AutomationCommands.parse("media", null, null, null))
    }

    @Test
    fun `type command caps text at TEXT_MAX and rejects empty`() {
        val c = AutomationCommands.parse("type", null, null, "hello")
        assertEquals(AutomationCommands.Command.TypeText("hello"), c)
        val long = AutomationCommands.parse("type", null, null, "y".repeat(999))
            as AutomationCommands.Command.TypeText
        assertEquals(AutomationCommands.TEXT_MAX, long.text.length)
        assertNull(AutomationCommands.parse("type", null, null, ""))
        assertNull(AutomationCommands.parse("type", null, null, null))
    }

    @Test
    fun `disconnect parses and garbage returns null`() {
        assertEquals(
            AutomationCommands.Command.Disconnect,
            AutomationCommands.parse("disconnect", null, null, null)
        )
        assertNull(AutomationCommands.parse(null, null, null, null))
        assertNull(AutomationCommands.parse("connect", "AA:BB:CC:DD:EE:FF", null, null))
        assertNull(AutomationCommands.parse("keycode", null, "0x04", null))
        assertNull(AutomationCommands.parse("💣", null, null, null))
    }
}
