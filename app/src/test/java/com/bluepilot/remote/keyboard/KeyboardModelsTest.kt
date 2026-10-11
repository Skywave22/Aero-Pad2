package com.bluepilot.remote.keyboard

import com.bluepilot.remote.model.keyboard.DefaultKeyboards
import com.bluepilot.remote.model.keyboard.KeySpec
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full-keyboard model contracts: default board integrity, sanitize, JSON. */
class KeyboardModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "kind" }

    @Test
    fun `default board has all main sections`() {
        val board = DefaultKeyboards.fullQwerty()
        assertEquals(8, board.rows.size)
        // F-row present
        assertTrue(board.rows[0].any { it.label == "F12" })
        // QWERTY letters present
        val letters = board.rows.flatten().map { it.label }
        listOf("Q","W","E","R","T","Y","A","S","D","Z","X","C").forEach {
            assertTrue("missing $it", it in letters)
        }
        // Modifiers + nav + arrows
        assertTrue(letters.containsAll(listOf("Ctrl","Win","Alt","Space")))
        assertTrue(board.rows[6].any { it.label == "PgUp" })
        assertTrue(board.rows[7].any { it.label == "←" })
    }

    @Test
    fun `default key ids are unique across the whole board`() {
        val board = DefaultKeyboards.fullQwerty()
        val ids = board.rows.flatten().map { it.id } + board.favorites.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `compact mode hides f-row nav and arrows`() {
        val board = DefaultKeyboards.fullQwerty()
        assertEquals(listOf(0, 6, 7), board.compactHiddenRows)
    }

    @Test
    fun `key sanitize clamps weight and label`() {
        val junk = KeySpec(
            id = "x", label = "WAYTOOLONGLABEL", keyCode = 0x04,
            widthWeight = 99f
        ).sanitized()
        assertEquals(KeySpec.LABEL_MAX, junk.label.length)
        assertEquals(KeySpec.WEIGHT_MAX, junk.widthWeight)
        assertEquals(KeySpec.WEIGHT_MIN, KeySpec(id="y", label="a", keyCode=0x04, widthWeight = 0.01f).sanitized().widthWeight)
        assertEquals(1f, KeySpec(id="z", label="a", keyCode=0x04, widthWeight = Float.NaN).sanitized().widthWeight)
    }

    @Test
    fun `blank label becomes placeholder`() {
        assertEquals("?", KeySpec(id="b", label="  ", keyCode=0x04).sanitized().label.trim().ifBlank { "?" })
    }

    @Test
    fun `layout survives JSON round trip with combo and secondary`() {
        val board = KeyboardLayoutSpec(
            rows = listOf(listOf(
                KeySpec(
                    id = "combo", label = "Copy", keyCode = 0x06, modifiers = 0x01,
                    colorArgb = 0xFF2F6BFF,
                    secondaryKeyCode = 0x19, secondaryModifiers = 0x01, secondaryLabel = "Paste"
                )
            )),
            favorites = listOf(KeySpec(id = "fav1", label = "Undo", keyCode = 0x1D, modifiers = 0x01))
        )
        val decoded = json.decodeFromString(
            KeyboardLayoutSpec.serializer(),
            json.encodeToString(KeyboardLayoutSpec.serializer(), board.sanitized())
        ).sanitized()
        assertEquals(board.sanitized(), decoded)
        val key = decoded.findKey("combo")
        assertNotNull(key)
        assertEquals(0x01.toByte(), key!!.modifiers)      // combo: Ctrl held
        assertEquals(0x19.toByte(), key.secondaryKeyCode) // long-press: Ctrl+V
    }

    @Test
    fun `corrupt keyboard JSON decodes to null via runCatching pattern`() {
        val result = runCatching {
            json.decodeFromString(KeyboardLayoutSpec.serializer(), "{ nope")
        }.getOrNull()
        assertNull(result)
    }

    @Test
    fun `favorites and row lengths are capped`() {
        val many = (1..30).map { KeySpec(id = "f$it", label = "K", keyCode = 0x04) }
        val board = KeyboardLayoutSpec(rows = listOf(many), favorites = many).sanitized()
        assertEquals(KeyboardLayoutSpec.FAVORITES_MAX, board.favorites.size)
        assertEquals(KeyboardLayoutSpec.KEYS_PER_ROW_MAX, board.rows[0].size)
    }
}
