package com.aeropad.remote.theme

import com.aeropad.remote.ui.theme.BuiltInThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THEME CATALOG v3 contract: catalog integrity.
 */
class BuiltInThemesTest {

    @Test
    fun `catalog has the new MD3 themes`() {
        assertEquals(4, BuiltInThemes.ALL.size)
    }

    @Test
    fun `theme ids are unique`() {
        val ids = BuiltInThemes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `byId resolves every catalog theme`() {
        BuiltInThemes.ALL.forEach { spec ->
            assertEquals(spec, BuiltInThemes.byId(spec.id))
        }
    }

    @Test
    fun `byId falls back safely on unknown legacy or null id`() {
        assertEquals(BuiltInThemes.EARTHY_SAND, BuiltInThemes.byId("does_not_exist"))
        assertEquals(BuiltInThemes.EARTHY_SAND, BuiltInThemes.byId(null))
        assertEquals(BuiltInThemes.EARTHY_SAND, BuiltInThemes.byId(""))
    }
}
