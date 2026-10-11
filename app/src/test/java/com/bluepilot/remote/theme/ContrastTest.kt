package com.bluepilot.remote.theme

import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.ui.components.bestContentColor
import org.junit.Assert.assertEquals
import org.junit.Test

/** SECTION 3 AUDIT — readable content color on any custom key color. */
class ContrastTest {

    @Test
    fun `light backgrounds get dark content`() {
        assertEquals(Color(0xFF15202B), Color.White.bestContentColor())
        assertEquals(Color(0xFF15202B), Color(0xFFF5C542).bestContentColor()) // amber
        assertEquals(Color(0xFF15202B), Color(0xFFB9F6CA).bestContentColor()) // pale green
    }

    @Test
    fun `dark backgrounds get white content`() {
        assertEquals(Color.White, Color.Black.bestContentColor())
        assertEquals(Color.White, Color(0xFF16305A).bestContentColor()) // navy
        assertEquals(Color.White, Color(0xFFE74C3C).bestContentColor()) // red
    }
}
