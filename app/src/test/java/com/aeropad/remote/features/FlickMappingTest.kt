package com.aeropad.remote.features

import com.aeropad.remote.domain.FlickDetector.FlickDirection
import com.aeropad.remote.domain.FlickMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flick mapping cycle sanity tests. */
class FlickMappingTest {

    @Test
    fun `default preserves original behavior up=0 others off`() {
        assertEquals(0, FlickMapping.DEFAULT[FlickDirection.UP])
        assertEquals(-1, FlickMapping.DEFAULT[FlickDirection.DOWN])
        assertEquals(-1, FlickMapping.DEFAULT[FlickDirection.LEFT])
        assertEquals(-1, FlickMapping.DEFAULT[FlickDirection.RIGHT])
    }

    @Test
    fun `cycle walks off A B X Y and wraps`() {
        var map = FlickMapping.DEFAULT   // DOWN = -1 (off)
        val seen = mutableListOf<Int>()
        repeat(5) {
            map = FlickMapping.cycled(map, FlickDirection.DOWN)
            seen += map[FlickDirection.DOWN]!!
        }
        assertEquals(listOf(0, 1, 2, 3, -1), seen)   // full wrap
        // Other directions untouched during the whole cycle.
        assertEquals(0, map[FlickDirection.UP])
    }

    @Test
    fun `cycle heals unknown values to off-then-first`() {
        val corrupt = mapOf(FlickDirection.UP to 99)
        val next = FlickMapping.cycled(corrupt, FlickDirection.UP)
        assertEquals(-1, next[FlickDirection.UP])   // snaps into the cycle
    }

    @Test
    fun `labels match buttons`() {
        assertEquals("A", FlickMapping.label(0))
        assertEquals("B", FlickMapping.label(1))
        assertEquals("X", FlickMapping.label(2))
        assertEquals("Y", FlickMapping.label(3))
        assertEquals("×", FlickMapping.label(-1))
        assertEquals("×", FlickMapping.label(null))
    }

    // ------------------------------------------------------------------
    // V2 M3 deferred-item — per-profile persistence codec
    // ------------------------------------------------------------------

    @Test
    fun `spec round trip preserves the mapping`() {
        val runtime = mapOf(
            FlickDirection.UP to 2, FlickDirection.DOWN to -1,
            FlickDirection.LEFT to 0, FlickDirection.RIGHT to 3
        )
        assertEquals(runtime, FlickMapping.fromSpec(FlickMapping.toSpec(runtime)))
    }

    @Test
    fun `fromSpec heals unknown keys missing directions and bad values`() {
        val loaded = FlickMapping.fromSpec(
            mapOf("UP" to 99, "SIDEWAYS" to 1, "DOWN" to -42)
        )
        assertEquals(15, loaded[FlickDirection.UP])        // clamped
        assertEquals(-1, loaded[FlickDirection.DOWN])      // clamped to off
        assertEquals(-1, loaded[FlickDirection.LEFT])      // missing → off
        assertEquals(-1, loaded[FlickDirection.RIGHT])
        assertEquals(4, loaded.size)                       // unknown key dropped
    }

    @Test
    fun `spec sanitize on layout drops unknown directions`() {
        val spec = com.aeropad.remote.model.gamepad.GamepadLayoutSpec(
            flickMap = mapOf("UP" to 1, "DIAGONAL" to 2, "LEFT" to 99)
        ).sanitized()
        assertEquals(mapOf("UP" to 1, "LEFT" to 15), spec.flickMap)
    }
}
