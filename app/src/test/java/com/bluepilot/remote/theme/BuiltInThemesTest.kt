package com.bluepilot.remote.theme

import com.bluepilot.remote.ui.theme.BuiltInThemes
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
        assertEquals(3, BuiltInThemes.ALL.size)
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
        assertEquals(BuiltInThemes.MATERIAL_YOU_LIGHT, BuiltInThemes.byId("does_not_exist"))
        assertEquals(BuiltInThemes.MATERIAL_YOU_LIGHT, BuiltInThemes.byId(null))
        assertEquals(BuiltInThemes.MATERIAL_YOU_LIGHT, BuiltInThemes.byId(""))
    }
}
