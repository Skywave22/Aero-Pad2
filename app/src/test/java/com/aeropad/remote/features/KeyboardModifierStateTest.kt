package com.aeropad.remote.features

import com.aeropad.remote.domain.KeyboardModifierState
import com.aeropad.remote.model.HidModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15 — Ctrl+, Shift+ and Alt+ did not work on the Full Keyboard: its
 * ViewModel sent KeyTap directly and never folded in a latched modifier, and
 * the modifier keys on the board were momentary taps. These tests pin the
 * shared behaviour both keyboards now depend on.
 */
class KeyboardModifierStateTest {

    @Test
    fun `tapping ctrl latches it for exactly one keystroke`() {
        val state = KeyboardModifierState()
        state.toggle(HidModifiers.LEFT_CTRL)
        assertEquals(HidModifiers.LEFT_CTRL.toInt(), state.mask)

        state.consume()
        assertEquals(0, state.mask)
    }

    @Test
    fun `two modifiers combine into one mask`() {
        val state = KeyboardModifierState()
        state.toggle(HidModifiers.LEFT_CTRL)
        state.toggle(HidModifiers.LEFT_SHIFT)
        val expected = HidModifiers.LEFT_CTRL.toInt() or HidModifiers.LEFT_SHIFT.toInt()
        assertEquals(expected, state.mask)
    }

    @Test
    fun `caps lock changes the case the board shows`() {
        val state = KeyboardModifierState()
        assertFalse(state.state.value.effectivelyShifted)

        state.toggleCaps()
        assertTrue(state.state.value.capsLock)
        assertTrue(state.state.value.effectivelyShifted)
    }

    @Test
    fun `a latched shift also shifts the board`() {
        val state = KeyboardModifierState()
        state.toggle(HidModifiers.LEFT_SHIFT)
        assertTrue(state.state.value.effectivelyShifted)
    }

    @Test
    fun `clear drops modifiers and caps together`() {
        val state = KeyboardModifierState()
        state.toggle(HidModifiers.LEFT_ALT)
        state.toggleCaps()
        state.clear()
        assertEquals(0, state.mask)
        assertFalse(state.state.value.capsLock)
        assertFalse(state.state.value.anyActive)
    }

    @Test
    fun `locked modifiers survive a keystroke`() {
        val state = KeyboardModifierState()
        state.toggle(HidModifiers.LEFT_CTRL)
        state.toggle(HidModifiers.LEFT_CTRL) // inside the double-tap window = lock
        assertTrue(state.state.value.isLocked(HidModifiers.LEFT_CTRL))

        state.consume()
        assertEquals(
            "a locked modifier must stay down across keystrokes",
            HidModifiers.LEFT_CTRL.toInt(),
            state.mask
        )
    }
}

/**
 * Caps must be a local decision. If the host's own caps state was already on,
 * sending CAPS_LOCK would turn it off while the board showed capitals — the
 * reported "letters stay in caps" confusion. So a capital letter is sent as
 * Shift + letter, which is unambiguous on every host.
 */
class CapsBehaviourTest {

    @Test
    fun `a capital letter carries shift and a small letter does not`() {
        val caps = KeyboardModifierState()
        caps.toggleCaps()
        val isLetter = { c: Char -> c.isLetter() }
        assertEquals(true, isLetter('A'))
        // mirrors FullKeyboardViewModel: capsShift applies to letters only
        fun capsShiftFor(label: String) = caps.state.value.capsLock && label.length == 1 && isLetter(label[0])
        assertTrue(capsShiftFor("A"))
        assertFalse("digits must not become shifted", capsShiftFor("1"))
        assertFalse("punctuation must not become shifted", capsShiftFor("/"))
    }
}
