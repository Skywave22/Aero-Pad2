package com.bluepilot.remote.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.bluepilot.remote.ui.theme.BuiltInThemes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SECTION 4 — accessibility audit: every built-in theme must keep body
 * text readable. WCAG contrast ratio = (L1+0.05)/(L2+0.05).
 *
 * Thresholds: 4.5:1 for primary text (onBackground/onSurface), 3.0:1 for
 * secondary text (onSurfaceVariant — always paired with larger/bolder UI
 * text in this app).
 */
class ThemeContrastAuditTest {

    private fun ratio(fg: Color, bg: Color): Double {
        val l1 = maxOf(fg.luminance(), bg.luminance()) + 0.05
        val l2 = minOf(fg.luminance(), bg.luminance()) + 0.05
        return (l1 / l2).toDouble()
    }

    @Test
    fun `onBackground readable on background in all themes`() {
        BuiltInThemes.ALL.forEach { t ->
            val r = ratio(t.onBackground, t.background)
            assertTrue("${t.id}: onBackground contrast $r < 4.5", r >= 4.5)
        }
    }

    @Test
    fun `onSurface readable on effective surface in all themes`() {
        BuiltInThemes.ALL.forEach { t ->
            // Translucent surfaces composite over the background.
            val effective = t.surface.copy(alpha = t.surfaceAlpha).compositeOver(t.background)
            val r = ratio(t.onSurface, effective)
            assertTrue("${t.id}: onSurface contrast $r < 4.5", r >= 4.5)
        }
    }

    @Test
    fun `onSurfaceVariant readable on effective surfaceVariant in all themes`() {
        BuiltInThemes.ALL.forEach { t ->
            val effective = t.surfaceVariant.copy(alpha = t.surfaceAlpha).compositeOver(t.background)
            val r = ratio(t.onSurfaceVariant, effective)
            assertTrue("${t.id}: onSurfaceVariant contrast $r < 3.0", r >= 3.0)
        }
    }

    @Test
    fun `onPrimary readable on primary in all themes`() {
        BuiltInThemes.ALL.forEach { t ->
            val r = ratio(t.onPrimary, t.primary)
            assertTrue("${t.id}: onPrimary contrast $r < 3.0", r >= 3.0)
        }
    }
}
