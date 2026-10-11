package com.bluepilot.remote.keyboard

import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.keyboard.DefaultKeyboards
import com.bluepilot.remote.model.keyboard.KeySpec
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec
import com.bluepilot.remote.model.keyboard.ShortcutPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 MATRIX 7 — FN layer resolution + shortcut pack data contracts. */
class FnLayerTest {

    @Test
    fun `fn inactive returns the base key`() {
        val board = DefaultKeyboards.fullQwerty()
        val f1 = board.findKey("f1")!!
        assertEquals(f1, board.effectiveKey(f1, fnActive = false))
    }

    @Test
    fun `fn active resolves overlay and leaves unmapped keys alone`() {
        val board = DefaultKeyboards.fullQwerty()
        val f1 = board.findKey("f1")!!
        val q = board.findKey("q")!!
        assertEquals(HidKeys.HOME, board.effectiveKey(f1, fnActive = true).keyCode)
        assertEquals(q, board.effectiveKey(q, fnActive = true))   // no overlay entry
    }

    @Test
    fun `default overlay ids all exist on the board`() {
        val board = DefaultKeyboards.fullQwerty()
        DefaultKeyboards.defaultFnOverlay().keys.forEach { id ->
            assertTrue("overlay maps missing key '$id'", board.findKey(id) != null)
        }
    }

    @Test
    fun `legacy layout without overlay behaves identically`() {
        val legacy = KeyboardLayoutSpec(
            rows = listOf(listOf(KeySpec("a", "A", HidKeys.A)))
        )
        val a = legacy.findKey("a")!!
        assertEquals(a, legacy.effectiveKey(a, fnActive = true))
        assertTrue(legacy.sanitized().fnOverlay.isEmpty())
    }

    @Test
    fun `overlay is capped by sanitize`() {
        val big = (1..99).associate { i ->
            "k$i" to KeySpec("fn-k$i", "X", HidKeys.A)
        }
        val spec = KeyboardLayoutSpec(fnOverlay = big).sanitized()
        assertEquals(KeyboardLayoutSpec.FN_OVERLAY_MAX, spec.fnOverlay.size)
    }

    @Test
    fun `type text sanitizes to null when empty and caps length`() {
        assertNull(KeySpec("t", "T", HidKeys.T, typeText = "").sanitized().typeText)
        val long = KeySpec("t", "T", HidKeys.T, typeText = "x".repeat(200)).sanitized()
        assertEquals(KeySpec.TYPE_TEXT_MAX, long.typeText!!.length)
    }

    // ------------------------------------------------------------------
    // V2 MATRIX 7 b2 — media FN keys + consumerUsage sanitize
    // ------------------------------------------------------------------

    @Test
    fun `fn f9 to f12 resolve to real media usages`() {
        val board = DefaultKeyboards.fullQwerty()
        val expected = mapOf(
            "f9" to com.bluepilot.remote.model.HidConsumer.PLAY_PAUSE,
            "f10" to com.bluepilot.remote.model.HidConsumer.MUTE,
            "f11" to com.bluepilot.remote.model.HidConsumer.VOLUME_DOWN,
            "f12" to com.bluepilot.remote.model.HidConsumer.VOLUME_UP
        )
        expected.forEach { (id, usage) ->
            val key = board.findKey(id)!!
            val eff = board.effectiveKey(key, fnActive = true)
            assertEquals("FN $id", usage, eff.consumerUsage)
            // And without FN they stay plain F-keys (no media leak).
            assertNull(board.effectiveKey(key, fnActive = false).consumerUsage)
        }
    }

    @Test
    fun `consumer usage sanitize drops invalid values`() {
        assertNull(KeySpec("x", "X", HidKeys.A, consumerUsage = 0).sanitized().consumerUsage)
        assertNull(KeySpec("x", "X", HidKeys.A, consumerUsage = -5).sanitized().consumerUsage)
        assertNull(KeySpec("x", "X", HidKeys.A, consumerUsage = 0x10000).sanitized().consumerUsage)
        assertEquals(
            0x00CD,
            KeySpec("x", "X", HidKeys.A, consumerUsage = 0x00CD).sanitized().consumerUsage
        )
    }

    @Test
    fun `one handed mode enum round trips by name`() {
        com.bluepilot.remote.data.keyboard.OneHandedMode.values().forEach { m ->
            assertEquals(
                m,
                com.bluepilot.remote.data.keyboard.OneHandedMode.valueOf(m.name)
            )
        }
        // Unknown string is what the store guards against with getOrDefault.
        assertTrue(
            runCatching {
                com.bluepilot.remote.data.keyboard.OneHandedMode.valueOf("SIDEWAYS")
            }.isFailure
        )
    }

    @Test
    fun `shortcut packs are well formed`() {
        assertEquals(4, ShortcutPacks.ALL.size)
        ShortcutPacks.ALL.forEach { pack ->
            assertTrue(pack.keys.isNotEmpty())
            assertTrue(
                "pack ${pack.id} exceeds favorites cap",
                pack.keys.size <= KeyboardLayoutSpec.FAVORITES_MAX
            )
            // Unique ids inside each pack, labels non-blank after sanitize.
            assertEquals(pack.keys.size, pack.keys.map { it.id }.toSet().size)
            pack.keys.forEach { k ->
                assertTrue(k.sanitized().label.isNotBlank())
                // typeText keys must actually carry text; key-coded keys a code.
                if (k.typeText == null) assertTrue(k.keyCode != HidKeys.NONE || k.modifiers != 0.toByte())
            }
        }
        // Pack ids unique across packs.
        assertEquals(
            ShortcutPacks.ALL.size,
            ShortcutPacks.ALL.map { it.id }.toSet().size
        )
    }
}
