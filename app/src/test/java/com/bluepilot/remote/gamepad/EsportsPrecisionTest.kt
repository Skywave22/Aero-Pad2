package com.bluepilot.remote.gamepad

import com.bluepilot.remote.domain.EsportsPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** V2 MATRIX 6 — esports precision math. */
class EsportsPrecisionTest {

    @Test
    fun `snap 8-way pulls 40deg input onto 45deg notch, magnitude kept`() {
        val inX = (Math.cos(Math.toRadians(40.0)) * 0.8).toFloat()
        val inY = (Math.sin(Math.toRadians(40.0)) * 0.8).toFloat()
        val (x, y) = EsportsPrecision.snapAngles(inX, inY, 8)
        val angle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()))
        assertEquals(45.0, angle, 0.5)
        assertEquals(0.8f, sqrt(x * x + y * y), 0.01f)
    }

    @Test
    fun `snap 0 is analog passthrough and 16-way has finer notches`() {
        assertEquals(0.33f to 0.44f, EsportsPrecision.snapAngles(0.33f, 0.44f, 0))
        // 22.5 deg input snaps exactly onto a 16-way notch (22.5 deg).
        val inX = Math.cos(Math.toRadians(22.5)).toFloat()
        val inY = Math.sin(Math.toRadians(22.5)).toFloat()
        val (x, y) = EsportsPrecision.snapAngles(inX, inY, 16)
        assertEquals(
            22.5, Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())), 0.5
        )
    }

    @Test
    fun `aim zone scales center, passes outside, blends smoothly`() {
        // Deep center: 10% sensitivity.
        assertEquals(0.02f, EsportsPrecision.aimZone(0.2f, 0.0f, 0.4f, 10), 0.005f)
        // Outside zone: unchanged.
        assertEquals(0.9f, EsportsPrecision.aimZone(0.9f, 0.5f, 0.4f, 10), 1e-4f)
        // At zone edge: ~full sensitivity (blend reaches 100%).
        assertEquals(0.5f, EsportsPrecision.aimZone(0.5f, 0.4f, 0.4f, 10), 0.01f)
    }

    @Test
    fun `stick-as-dpad maps vectors to hat with threshold`() {
        assertEquals(8, EsportsPrecision.stickToHat(0.1f, 0.1f))          // below threshold
        assertEquals(2, EsportsPrecision.stickToHat(0.9f, 0f))            // east
        assertEquals(0, EsportsPrecision.stickToHat(0f, -0.9f))           // north
        assertEquals(1, EsportsPrecision.stickToHat(0.7f, -0.7f))         // north-east
        assertEquals(6, EsportsPrecision.stickToHat(-0.9f, 0f))           // west
        assertEquals(4, EsportsPrecision.stickToHat(Float.NaN, 0.9f))     // NaN x -> 0; y alone = south
    }

    @Test
    fun `trigger overlap fires L1 then L2 by pull depth`() {
        assertEquals(false to false, EsportsPrecision.triggerOverlap(0.2f, 0.5f, 0.8f))
        assertEquals(true to false, EsportsPrecision.triggerOverlap(0.6f, 0.5f, 0.8f))
        assertEquals(true to true, EsportsPrecision.triggerOverlap(0.9f, 0.5f, 0.8f))
        // Invalid config self-heals: l2At forced to l1At+0.05 (=0.55).
        val (l1, l2) = EsportsPrecision.triggerOverlap(0.52f, 0.5f, 0.3f)
        assertTrue(l1); assertFalse(l2)   // 0.52 >= 0.5 but < 0.55
    }

    @Test
    fun `input history newest first and capped`() {
        var h = emptyList<EsportsPrecision.InputEvent>()
        for (i in 1..20) h = EsportsPrecision.pushHistory(h, "B$i", i.toLong())
        assertEquals(12, h.size)
        assertEquals("B20", h.first().label)
        assertEquals(20L, h.first().atMs)
    }
}