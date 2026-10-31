package com.aeropad.remote.features

import com.aeropad.remote.domain.TextRelayDiff
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 15 — "cannot type from phone keyboard to dialogue box".
 * The relay sends only what changed; these are the cases a soft keyboard
 * actually produces, including autocorrect rewriting a whole word.
 */
class TextRelayDiffTest {

    @Test
    fun `typing one character sends one character`() {
        val plan = TextRelayDiff.plan("hel", "hell")
        assertEquals(0, plan.backspaces)
        assertEquals("l", plan.insert)
    }

    @Test
    fun `nothing changed sends nothing`() {
        val plan = TextRelayDiff.plan("same", "same")
        assertEquals(0, plan.backspaces)
        assertEquals("", plan.insert)
    }

    @Test
    fun `backspace deletes on the host`() {
        val plan = TextRelayDiff.plan("hell", "hel")
        assertEquals(1, plan.backspaces)
        assertEquals("", plan.insert)
    }

    @Test
    fun `autocorrect replacing a word sends the right deletes then the replacement`() {
        // Gboard turns "teh" into "the" when you type a space.
        val plan = TextRelayDiff.plan("teh", "teh ")
        assertEquals(0, plan.backspaces)
        assertEquals(" ", plan.insert)
    }

    @Test
    fun `select all and retype clears first`() {
        val plan = TextRelayDiff.plan("old text", "new")
        assertEquals(8, plan.backspaces)
        assertEquals("new", plan.insert)
    }

    @Test
    fun `clearing the field deletes everything on the host`() {
        val plan = TextRelayDiff.plan("hello", "")
        assertEquals(5, plan.backspaces)
        assertEquals("", plan.insert)
    }

    @Test
    fun `a shared prefix is not retyped`() {
        val plan = TextRelayDiff.plan("prefix-one", "prefix-two")
        assertEquals(3, plan.backspaces)   // "one"
        assertEquals("two", plan.insert)
    }
}
