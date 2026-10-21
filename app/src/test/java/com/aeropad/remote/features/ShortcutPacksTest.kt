package com.aeropad.remote.features

import com.aeropad.remote.data.layout.ShortcutPacks
import com.aeropad.remote.model.widgets.WidgetAction
import com.aeropad.remote.model.widgets.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F2 — every shortcut pack must be a valid, loadable layout. */
class ShortcutPacksTest {

    private val packs = ShortcutPacks.all()

    @Test
    fun `ships five packs`() {
        assertEquals(5, packs.size)
    }

    @Test
    fun `pack names are unique and non blank`() {
        val names = packs.map { it.name }
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.all { it.isNotBlank() })
    }

    @Test
    fun `widget ids are unique across every pack`() {
        val all = packs.flatMap { it.widgets }.map { it.id }
        assertEquals(all.size, all.distinct().size)
    }

    @Test
    fun `every widget sits inside the canvas`() {
        packs.forEach { pack ->
            pack.widgets.forEach { w ->
                val f = w.frame
                assertTrue("${pack.name}/${w.id} x", f.x >= 0f)
                assertTrue("${pack.name}/${w.id} y", f.y >= 0f)
                assertTrue("${pack.name}/${w.id} w", f.w > 0f)
                assertTrue("${pack.name}/${w.id} h", f.h > 0f)
                assertTrue("${pack.name}/${w.id} right edge", f.x + f.w <= 1.0001f)
                assertTrue("${pack.name}/${w.id} bottom edge", f.y + f.h <= 1.0001f)
            }
        }
    }

    @Test
    fun `no widget has a blank label`() {
        packs.forEach { pack ->
            pack.widgets.forEach { w ->
                assertTrue("${pack.name}/${w.id} label", w.style.label.isNotBlank())
            }
        }
    }

    @Test
    fun `every button is bound to a real action`() {
        packs.forEach { pack ->
            // Trackpads and scroll strips are gesture surfaces, not buttons;
            // every BUTTON must carry a real binding.
            pack.widgets.filter { it.type == WidgetType.BUTTON }.forEach { w ->
                assertTrue(
                    "${pack.name}/${w.id} has no action",
                    w.action !is WidgetAction.None
                )
            }
        }
    }

    @Test
    fun `key taps never bind the empty keycode`() {
        packs.flatMap { it.widgets }
            .mapNotNull { it.action as? WidgetAction.KeyTap }
            .forEach { assertEquals("a KeyTap bound to key 0 does nothing", false, it.key == 0.toByte()) }
    }

    @Test
    fun `typed text is never empty`() {
        packs.flatMap { it.widgets }
            .mapNotNull { it.action as? WidgetAction.TypeText }
            .forEach { assertTrue(it.text.isNotBlank()) }
    }

    @Test
    fun `no widget overlaps another in the same pack`() {
        packs.forEach { pack ->
            val ws = pack.widgets
            for (i in ws.indices) {
                for (j in i + 1 until ws.size) {
                    val a = ws[i].frame
                    val b = ws[j].frame
                    val overlapX = a.x < b.x + b.w && b.x < a.x + a.w
                    val overlapY = a.y < b.y + b.h && b.y < a.y + a.h
                    assertTrue(
                        "${pack.name}: ${ws[i].id} overlaps ${ws[j].id}",
                        !(overlapX && overlapY)
                    )
                }
            }
        }
    }

    @Test
    fun `each pack carries a trackpad or enough buttons to be usable`() {
        packs.forEach { pack ->
            assertTrue("${pack.name} is nearly empty", pack.widgets.size >= 6)
        }
    }
}
