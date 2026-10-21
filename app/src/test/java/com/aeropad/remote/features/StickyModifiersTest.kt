package com.aeropad.remote.features

import com.aeropad.remote.domain.StickyModifiers
import com.aeropad.remote.model.HidModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F3 — sticky / locked modifier latch. */
class StickyModifiersTest {

    private val ctrl = HidModifiers.LEFT_CTRL
    private val shift = HidModifiers.LEFT_SHIFT
    private val alt = HidModifiers.LEFT_ALT

    @Test
    fun `starts empty`() {
        val s = StickyModifiers()
        assertEquals(0, s.mask)
        assertFalse(s.isActive)
    }

    @Test
    fun `single tap latches for one keystroke`() {
        val s = StickyModifiers()
        assertEquals(ctrl.toInt(), s.toggle(ctrl, 0))
        assertTrue(s.isLatched(ctrl))
        assertFalse(s.isLocked(ctrl))

        // The keystroke consumes it.
        s.consume()
        assertEquals(0, s.mask)
        assertFalse(s.isActive)
    }

    @Test
    fun `double tap inside the window locks and survives keystrokes`() {
        val s = StickyModifiers(doubleTapWindowMs = 450)
        s.toggle(ctrl, 1_000)
        assertEquals(ctrl.toInt(), s.toggle(ctrl, 1_200))

        assertTrue(s.isLocked(ctrl))
        s.consume()
        assertEquals("locked modifier must survive consume()", ctrl.toInt(), s.mask)
    }

    @Test
    fun `double tap just outside the window does not lock`() {
        val s = StickyModifiers(doubleTapWindowMs = 450)
        s.toggle(ctrl, 1_000)
        s.toggle(ctrl, 1_450)          // exactly on the boundary is still inside
        assertTrue(s.isLocked(ctrl))

        val t = StickyModifiers(doubleTapWindowMs = 450)
        t.toggle(ctrl, 1_000)
        t.toggle(ctrl, 1_451)          // one ms too late
        assertFalse(t.isLocked(ctrl))
    }

    @Test
    fun `tap on a locked modifier turns it off`() {
        val s = StickyModifiers()
        s.toggle(ctrl, 0)
        s.toggle(ctrl, 100)            // locked
        assertEquals(ctrl.toInt(), s.mask)

        s.toggle(ctrl, 10_000)         // third tap, long later → off
        assertEquals(0, s.mask)
        assertFalse(s.isLocked(ctrl))
    }

    @Test
    fun `slow second tap turns a latch off`() {
        val s = StickyModifiers(doubleTapWindowMs = 450)
        s.toggle(ctrl, 0)
        s.toggle(ctrl, 5_000)
        assertEquals(0, s.mask)
    }

    @Test
    fun `modifiers combine`() {
        val s = StickyModifiers()
        s.toggle(ctrl, 0)
        s.toggle(shift, 1_000)
        val expected = (ctrl.toInt() or shift.toInt())
        assertEquals(expected, s.mask)
        assertTrue(s.isLatched(ctrl))
        assertTrue(s.isLatched(shift))
    }

    @Test
    fun `three way chord ctrl shift alt`() {
        val s = StickyModifiers()
        s.toggle(ctrl, 0)
        s.toggle(shift, 10)
        s.toggle(alt, 20)
        assertEquals((ctrl.toInt() or shift.toInt() or alt.toInt()), s.mask)
    }

    @Test
    fun `consume clears latches but keeps locks`() {
        val s = StickyModifiers()
        s.toggle(ctrl, 0)
        s.toggle(ctrl, 100)            // ctrl locked
        s.toggle(shift, 200)           // shift latched
        assertEquals((ctrl.toInt() or shift.toInt()), s.mask)

        s.consume()
        assertEquals(ctrl.toInt(), s.mask)
    }

    @Test
    fun `clear resets everything`() {
        val s = StickyModifiers()
        s.toggle(ctrl, 0)
        s.toggle(ctrl, 100)
        s.toggle(shift, 200)
        s.clear()
        assertEquals(0, s.mask)
        assertFalse(s.isActive)
        assertFalse(s.isLocked(ctrl))
        assertFalse(s.isLatched(shift))
    }

    @Test
    fun `zero byte is ignored`() {
        val s = StickyModifiers()
        assertEquals(0, s.toggle(HidModifiers.NONE, 0))
        assertFalse(s.isActive)
    }

    @Test
    fun `mask never exceeds one byte`() {
        val s = StickyModifiers()
        StickyModifiers.ALL.let { all ->
            s.toggle(HidModifiers.LEFT_CTRL, 0)
            s.toggle(HidModifiers.LEFT_SHIFT, 1)
            s.toggle(HidModifiers.LEFT_ALT, 2)
            s.toggle(HidModifiers.LEFT_GUI, 3)
            s.toggle(HidModifiers.RIGHT_CTRL, 4)
            s.toggle(HidModifiers.RIGHT_SHIFT, 5)
            s.toggle(HidModifiers.RIGHT_ALT, 6)
            s.toggle(HidModifiers.RIGHT_GUI, 7)
            assertEquals(all, s.mask)
            assertTrue(s.mask <= 0xFF)
        }
    }

    @Test
    fun `all bits are the union of both sides`() {
        val expected = (HidModifiers.LEFT_CTRL.toInt() or HidModifiers.LEFT_SHIFT.toInt() or
            HidModifiers.LEFT_ALT.toInt() or HidModifiers.LEFT_GUI.toInt() or
            HidModifiers.RIGHT_CTRL.toInt() or HidModifiers.RIGHT_SHIFT.toInt() or
            HidModifiers.RIGHT_ALT.toInt() or HidModifiers.RIGHT_GUI.toInt())
        assertEquals(expected and 0xFF, StickyModifiers.ALL)
    }

    @Test
    fun `default window is 450 ms`() {
        assertEquals(450L, StickyModifiers.DEFAULT_WINDOW_MS)
    }
}
