package com.bluepilot.remote.features

import com.bluepilot.remote.domain.VoiceCommandParser
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADV SECTION 4 — voice command parsing produces exact HID actions. */
class VoiceCommandParserTest {

    private fun parse(s: String, cmds: Boolean = true, punct: Boolean = false) =
        VoiceCommandParser.parse(s, cmds, punct)

    // ---------- Commands → HID ----------

    @Test
    fun `new line variants send Enter`() {
        listOf("new line", "NEW LINE", "newline", "enter", "new paragraph").forEach { s ->
            val p = parse(s) as VoiceCommandParser.Parsed.Actions
            assertEquals(listOf(HidAction.KeyTap(HidKeys.ENTER)), p.actions)
        }
    }

    @Test
    fun `delete that sends one backspace`() {
        val p = parse("delete that") as VoiceCommandParser.Parsed.Actions
        assertEquals(listOf(HidAction.KeyTap(HidKeys.BACKSPACE)), p.actions)
    }

    @Test
    fun `delete N characters sends N backspaces, digits or words`() {
        val p1 = parse("delete 5 characters") as VoiceCommandParser.Parsed.Actions
        assertEquals(5, p1.actions.size)
        assertTrue(p1.actions.all { it == HidAction.KeyTap(HidKeys.BACKSPACE) })
        val p2 = parse("delete three letters") as VoiceCommandParser.Parsed.Actions
        assertEquals(3, p2.actions.size)
        // Out of range → falls through to plain text (never a runaway loop).
        assertTrue(parse("delete 500 characters") is VoiceCommandParser.Parsed.Text)
    }

    @Test
    fun `tab and space commands map to their keys`() {
        assertEquals(
            listOf(HidAction.KeyTap(HidKeys.TAB)),
            (parse("tab") as VoiceCommandParser.Parsed.Actions).actions
        )
        assertEquals(
            listOf(HidAction.KeyTap(HidKeys.SPACE)),
            (parse("spacebar") as VoiceCommandParser.Parsed.Actions).actions
        )
    }

    @Test
    fun `commands disabled types the words literally`() {
        val p = parse("new line", cmds = false)
        assertEquals(VoiceCommandParser.Parsed.Text("new line"), p)
    }

    // ---------- Punctuation mode ----------

    @Test
    fun `spoken punctuation replaces words when enabled`() {
        assertEquals(
            VoiceCommandParser.Parsed.Text(","),
            parse("comma", punct = true)
        )
        assertEquals(
            VoiceCommandParser.Parsed.Text("hello, world."),
            parse("hello comma world period", punct = true)
        )
    }

    @Test
    fun `punctuation words stay literal when disabled`() {
        assertEquals(
            VoiceCommandParser.Parsed.Text("hello comma world"),
            parse("hello comma world", punct = false)
        )
    }

    // ---------- Plain text ----------

    @Test
    fun `ordinary speech passes through unchanged`() {
        assertEquals(
            VoiceCommandParser.Parsed.Text("The quick brown fox"),
            parse("The quick brown fox")
        )
        assertEquals(VoiceCommandParser.Parsed.Text(""), parse("   "))
    }

    @Test
    fun `language list has device default first and valid tags`() {
        assertEquals("", VoiceCommandParser.LANGUAGES.first().first)
        assertTrue(VoiceCommandParser.LANGUAGES.size >= 8)
        VoiceCommandParser.LANGUAGES.drop(1).forEach { (tag, _) ->
            assertTrue(tag.matches(Regex("[a-z]{2}-[A-Z]{2}")))
        }
    }
}
