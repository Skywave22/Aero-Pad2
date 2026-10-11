package com.bluepilot.remote.theme

import com.bluepilot.remote.ui.theme.BuiltInThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 1 contract: theme catalog integrity.
 * A broken/missing theme id must NEVER crash the app — byId always
 * resolves to a valid spec.
 */
class BuiltInThemesTest {

    @Test
    fun `catalog has all thirteen design themes`() {
        assertTrue(BuiltInThemes.ALL.size >= 19)
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
    fun `byId falls back safely on unknown or null id`() {
        assertEquals(BuiltInThemes.PILOT_DARK, BuiltInThemes.byId("does_not_exist"))
        assertEquals(BuiltInThemes.PILOT_DARK, BuiltInThemes.byId(null))
        assertEquals(BuiltInThemes.PILOT_DARK, BuiltInThemes.byId(""))
    }

    @Test
    fun `catalog contains both dark and light themes`() {
        assertTrue(BuiltInThemes.ALL.any { it.isDark })
        assertTrue(BuiltInThemes.ALL.any { !it.isDark })
    }

    @Test
    fun `all surface alphas and radii are within valid ranges`() {
        BuiltInThemes.ALL.forEach { spec ->
            assertTrue("${spec.id} alpha", spec.surfaceAlpha in 0.1f..1f)
            assertTrue("${spec.id} radius", spec.cornerRadius in 0..60)
            assertTrue("${spec.id} elevation", spec.elevation in 0..24)
            spec.backgroundOrbs.forEach { orb ->
                assertTrue("${spec.id} orb alpha", orb.alpha in 0f..1f)
                assertTrue("${spec.id} orb pos", orb.x in 0f..1f && orb.y in 0f..1f)
            }
        }
    }

    @Test
    fun `design themes are present`() {
        val ids = BuiltInThemes.ALL.map { it.id }
        assertTrue(ids.containsAll(listOf(
            "pilot_dark", "pilot_glow",
            "liquid_glass", "liquid_glass_light",
            "glass_you_dark", "glass_you_light",
            "hawaii_night", "hawaii_day",
            "cockpit_hud", "day_flight",
            "dark_neon", "oled_black", "minimal_light"
        )))
    }
}
