package com.aeropad.remote.features

import com.aeropad.remote.ui.components.fittedAspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 12 — control decks keep their authored shape within reason. */
class DeckAspectTest {

    @Test
    fun `a portrait deck in a landscape window is corrected but never a strip`() {
        val window = 800f / 360f          // a real phone in landscape
        val fit = fittedAspect(9f / 16f, window)
        assertTrue("must not stay 0.5625", fit > 9f / 16f)
        assertTrue("must not fill 2.22", fit < window)
        // 62% of the window is the floor
        assertEquals(window * 0.62f, fit, 0.001f)
    }

    @Test
    fun `a deck authored for the same shape is left alone`() {
        val window = 1.7f
        assertEquals(window, fittedAspect(window, window), 0.001f)
    }

    @Test
    fun `an unknown aspect falls back to a portrait phone`() {
        assertEquals(fittedAspect(9f / 16f, 1.0f), fittedAspect(0f, 1.0f), 0.0001f)
    }

    @Test
    fun `portrait windows are untouched by the floor`() {
        val window = 360f / 800f
        assertEquals(9f / 16f, fittedAspect(9f / 16f, window), 0.0001f)
    }
}
