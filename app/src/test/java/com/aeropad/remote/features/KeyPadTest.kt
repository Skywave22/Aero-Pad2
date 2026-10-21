package com.aeropad.remote.features

import com.aeropad.remote.domain.KeyPlacement
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.keys.KeyCatalog
import com.aeropad.remote.model.keys.KeyGroup
import com.aeropad.remote.model.keys.NamedCombo
import com.aeropad.remote.model.widgets.WidgetFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 8 — key pad catalog, named combos and grid placement. */
class KeyPadTest {

    // ---------------------------------------------------------------- catalog

    @Test
    fun `catalog covers every group`() {
        KeyGroup.entries.forEach { assertTrue("empty group $it", KeyCatalog.group(it).isNotEmpty()) }
    }

    @Test
    fun `letters are exactly A to Z`() {
        assertEquals(26, KeyCatalog.letters.size)
        assertEquals("A", KeyCatalog.letters.first().label)
        assertEquals("Z", KeyCatalog.letters.last().label)
    }

    @Test
    fun `no key code is used twice`() {
        val codes = KeyCatalog.all().map { it.code }
        assertEquals("duplicate HID usage in the catalog", codes.size, codes.distinct().size)
    }

    @Test
    fun `no label is used twice`() {
        val labels = KeyCatalog.all().map { it.label }
        assertEquals("duplicate label in the catalog", labels.size, labels.distinct().size)
    }

    @Test
    fun `single letters can be looked up by code`() {
        val a = KeyCatalog.byCode(KeyCatalog.letters.first { it.label == "A" }.code)
        assertEquals("A", a?.label)
        assertNull(KeyCatalog.byCode(0x00))
    }

    @Test
    fun `function keys run F1 to F12`() {
        assertEquals(12, KeyCatalog.functions.size)
        assertEquals("F12", KeyCatalog.functions.last().label)
    }

    // ----------------------------------------------------------------- combos

    @Test
    fun `chord renders in a stable modifier order`() {
        val mods = HidModifiers.LEFT_GUI.toInt() or HidModifiers.LEFT_CTRL.toInt()
        assertEquals("Ctrl+Win+Esc", NamedCombo.chord(mods, "Esc"))
    }

    @Test
    fun `ctrl shift esc renders as the user expects`() {
        val mods = HidModifiers.LEFT_CTRL.toInt() or HidModifiers.LEFT_SHIFT.toInt()
        assertEquals("Ctrl+Shift+Esc", NamedCombo.chord(mods, "Esc"))
    }

    @Test
    fun `a combo with no modifiers is just the key`() {
        assertEquals("A", NamedCombo.chord(0, "A"))
    }

    @Test
    fun `display name prefers the user's name then falls back to the chord`() {
        val esc = KeyCatalog.navigation.first { it.name == "Esc" }
        val named = NamedCombo(id = "1", name = "Close window", key = esc.code.toInt(),
            mods = HidModifiers.LEFT_ALT.toInt())
        assertEquals("Close window", named.displayName())
        assertEquals("Alt+Esc", named.chordString())

        val unnamed = named.copy(name = "")
        assertEquals("Alt+Esc", unnamed.displayName())
    }

    @Test
    fun `sanitize trims and caps the name`() {
        val c = NamedCombo(id = "1", name = "   " + "x".repeat(90) + "  ", key = 0x29, mods = 0)
        val clean = c.sanitized()
        assertEquals(NamedCombo.NAME_MAX, clean.name.length)
    }

    @Test
    fun `a combo without a key is invalid`() {
        assertFalse(NamedCombo(id = "1", name = "nothing", key = 0).isValid())
        assertTrue(NamedCombo(id = "1", name = "esc", key = 0x29).isValid())
    }

    @Test
    fun `mods are masked to one byte`() {
        val c = NamedCombo(id = "1", name = "x", key = 0x29, mods = 0x1FF).sanitized()
        assertEquals(0xFF, c.mods)
    }

    // -------------------------------------------------------------- placement

    @Test
    fun `first key lands in the top left cell`() {
        val f = KeyPlacement.firstFree(emptyList())
        assertNotNull(f)
        assertEquals(KeyPlacement.GAP, f!!.x, 0.0001f)
        assertEquals(KeyPlacement.GAP, f.y, 0.0001f)
    }

    @Test
    fun `second key goes beside the first, not on top`() {
        val first = KeyPlacement.firstFree(emptyList())!!
        val second = KeyPlacement.firstFree(listOf(first))!!
        assertTrue(second.x > first.x)
        assertEquals(first.y, second.y, 0.0001f)
    }

    @Test
    fun `a widget covering the whole canvas leaves no room`() {
        val full = WidgetFrame(0f, 0f, 1f, 1f)
        assertNull(KeyPlacement.firstFree(listOf(full)))
    }

    @Test
    fun `placement fills a row then wraps`() {
        val placed = mutableListOf<WidgetFrame>()
        repeat(KeyPlacement.COLUMNS + 1) {
            placed += KeyPlacement.firstFree(placed)!!
        }
        val rows = placed.map { it.y }.distinct()
        assertEquals("should wrap onto a second row", 2, rows.size)
    }

    @Test
    fun `a full grid returns null instead of stacking`() {
        val placed = mutableListOf<WidgetFrame>()
        repeat(KeyPlacement.COLUMNS * KeyPlacement.ROWS) {
            val next = KeyPlacement.firstFree(placed) ?: return@repeat
            placed += next
        }
        assertEquals(KeyPlacement.COLUMNS * KeyPlacement.ROWS, placed.size)
        assertNull(KeyPlacement.firstFree(placed))
    }

    @Test
    fun `cells never overlap each other`() {
        val (w, h) = KeyPlacement.cell()
        val placed = mutableListOf<WidgetFrame>()
        repeat(20) { KeyPlacement.firstFree(placed)?.let { placed += it } }

        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                val a = placed[i]
                val b = placed[j]
                val overlap = a.x < b.x + b.w - 1e-4f && b.x < a.x + a.w - 1e-4f &&
                    a.y < b.y + b.h - 1e-4f && b.y < a.y + a.h - 1e-4f
                assertFalse("cells $i and $j overlap", overlap)
            }
        }
        assertTrue(w > 0f && h > 0f)
    }

    @Test
    fun `thumb first starts at the bottom of the canvas`() {
        val f = KeyPlacement.firstFreeThumbFirst(emptyList())!!
        assertTrue("should be lower half", f.y > 0.5f)
    }

    @Test
    fun `thumb first still avoids an occupied bottom row`() {
        val bottom = KeyPlacement.firstFreeThumbFirst(emptyList())!!
        val next = KeyPlacement.firstFreeThumbFirst(listOf(bottom))!!
        assertFalse(next.y == bottom.y && next.x == bottom.x)
    }

    @Test
    fun `everything placed stays inside the canvas`() {
        val placed = mutableListOf<WidgetFrame>()
        repeat(KeyPlacement.COLUMNS * KeyPlacement.ROWS) {
            KeyPlacement.firstFree(placed)?.let { placed += it }
        }
        placed.forEach {
            assertTrue(it.x >= 0f && it.y >= 0f)
            assertTrue(it.x + it.w <= 1.0001f)
            assertTrue(it.y + it.h <= 1.0001f)
        }
    }
}
