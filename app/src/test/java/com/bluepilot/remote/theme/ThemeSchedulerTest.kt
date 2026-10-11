package com.bluepilot.remote.theme

import com.bluepilot.remote.ui.theme.ThemeListCodec
import com.bluepilot.remote.ui.theme.ThemeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SECTION 1 — auto theme scheduling + recents/favorites codec. */
class ThemeSchedulerTest {

    // ---- isNight: window crossing midnight (19 → 7) ----
    @Test
    fun `night window crossing midnight`() {
        assertTrue(ThemeScheduler.isNight(19, 19, 7))
        assertTrue(ThemeScheduler.isNight(23, 19, 7))
        assertTrue(ThemeScheduler.isNight(0, 19, 7))
        assertTrue(ThemeScheduler.isNight(6, 19, 7))
        assertFalse(ThemeScheduler.isNight(7, 19, 7))
        assertFalse(ThemeScheduler.isNight(12, 19, 7))
        assertFalse(ThemeScheduler.isNight(18, 19, 7))
    }

    @Test
    fun `same-day night window`() {
        assertTrue(ThemeScheduler.isNight(2, 1, 5))
        assertFalse(ThemeScheduler.isNight(5, 1, 5))
        assertFalse(ThemeScheduler.isNight(0, 1, 5))
    }

    @Test
    fun `zero-length window is never night and hours wrap`() {
        assertFalse(ThemeScheduler.isNight(3, 8, 8))
        assertTrue(ThemeScheduler.isNight(25, 19, 7))   // 25 wraps to 1am → night
    }

    @Test
    fun `negative hour wraps correctly`() {
        // -2 wraps to 22 which IS inside 19..7
        assertTrue(ThemeScheduler.isNight(-2, 19, 7))
    }

    // ---- scheduledThemeId ----
    @Test
    fun `disabled returns null`() {
        assertNull(ThemeScheduler.scheduledThemeId(false, 23, 19, 7, "day", "night"))
    }

    @Test
    fun `picks night and day themes`() {
        assertEquals("night", ThemeScheduler.scheduledThemeId(true, 22, 19, 7, "day", "night"))
        assertEquals("day", ThemeScheduler.scheduledThemeId(true, 12, 19, 7, "day", "night"))
    }

    // ---- ThemeListCodec ----
    @Test
    fun `push dedupes moves to front and caps`() {
        var csv = ""
        csv = ThemeListCodec.push(csv, "a")
        csv = ThemeListCodec.push(csv, "b")
        csv = ThemeListCodec.push(csv, "a")            // moves a to front
        assertEquals(listOf("a", "b"), ThemeListCodec.decode(csv))
        for (i in 0 until 10) csv = ThemeListCodec.push(csv, "t$i")
        assertEquals(6, ThemeListCodec.decode(csv).size) // capped
    }

    @Test
    fun `toggle adds and removes`() {
        var csv = ThemeListCodec.toggle("", "x")
        assertTrue(ThemeListCodec.contains(csv, "x"))
        csv = ThemeListCodec.toggle(csv, "x")
        assertFalse(ThemeListCodec.contains(csv, "x"))
    }

    @Test
    fun `decode survives junk`() {
        assertEquals(listOf("a", "b"), ThemeListCodec.decode(" a , ,b, "))
        assertEquals(emptyList<String>(), ThemeListCodec.decode(""))
    }
}
