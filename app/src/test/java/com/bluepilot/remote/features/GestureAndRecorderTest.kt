package com.bluepilot.remote.features

import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.domain.MacroRecorder
import com.bluepilot.remote.domain.SwipeDirection
import com.bluepilot.remote.domain.SwipeGestures
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.model.macros.MacroStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SECTION 3A contracts: swipe classification, recorder capture, new zones. */
class GestureAndRecorderTest {

    // ---------- swipe classification ----------

    @Test
    fun `short drags are not swipes`() {
        assertEquals(SwipeDirection.NONE, SwipeGestures.classify(20f, 30f))
        assertEquals(SwipeDirection.NONE, SwipeGestures.classify(0f, 0f))
    }

    @Test
    fun `dominant axis decides direction`() {
        assertEquals(SwipeDirection.RIGHT, SwipeGestures.classify(200f, 50f))
        assertEquals(SwipeDirection.LEFT, SwipeGestures.classify(-150f, 40f))
        assertEquals(SwipeDirection.DOWN, SwipeGestures.classify(30f, 180f))
        assertEquals(SwipeDirection.UP, SwipeGestures.classify(-20f, -120f))
    }

    @Test
    fun `NaN drag is safe`() {
        assertEquals(SwipeDirection.NONE, SwipeGestures.classify(Float.NaN, Float.NaN))
    }

    // ---------- macro recorder ----------

    @Test
    fun `recorder captures recordable actions in order`() {
        val rec = MacroRecorder()
        rec.start()
        rec.capture(HidAction.KeyTap(0x28), now = 1000)
        rec.capture(HidAction.MediaTap(0x00CD), now = 1050)          // gap < 150ms → no delay
        rec.capture(HidAction.MouseClick(MouseButton.LEFT), now = 1600) // gap 550ms → delay step
        val steps = rec.stop()
        assertEquals(4, steps.size)
        assertTrue(steps[0] is MacroStep.KeyTap)
        assertTrue(steps[1] is MacroStep.Media)
        assertTrue(steps[2] is MacroStep.Delay)
        assertEquals(550L, (steps[2] as MacroStep.Delay).ms)
        assertTrue(steps[3] is MacroStep.MouseClick)
    }

    @Test
    fun `recorder ignores non-recordable and idle input`() {
        val rec = MacroRecorder()
        rec.start()
        rec.capture(HidAction.MouseMove(5, 5), now = 1000)   // continuous → skipped
        rec.capture(HidAction.MouseScroll(1), now = 1010)    // continuous → skipped
        rec.capture(HidAction.TypeText(""), now = 1020)      // empty → skipped
        assertEquals(0, rec.stop().size)
    }

    @Test
    fun `capture without start is a no-op`() {
        val rec = MacroRecorder()
        rec.capture(HidAction.KeyTap(0x04))
        assertEquals(false, rec.recording.value)
        assertEquals(0, rec.stop().size)
    }

    @Test
    fun `recorder caps step count`() {
        val rec = MacroRecorder()
        rec.start()
        repeat(200) { rec.capture(HidAction.KeyTap(0x04), now = 1000L + it) }
        assertTrue(rec.stop().size <= com.bluepilot.remote.model.macros.MacroSpec.STEPS_MAX)
    }

    @Test
    fun `toStep maps exactly the recordable action types`() {
        assertTrue(MacroRecorder.toStep(HidAction.KeyTap(0x04, 0x01)) is MacroStep.KeyTap)
        assertTrue(MacroRecorder.toStep(HidAction.TypeText("hi")) is MacroStep.TypeText)
        assertTrue(MacroRecorder.toStep(HidAction.MediaTap(1)) is MacroStep.Media)
        assertTrue(MacroRecorder.toStep(HidAction.MouseClick(MouseButton.RIGHT)) is MacroStep.MouseClick)
        assertEquals(null, MacroRecorder.toStep(HidAction.MouseMove(1, 1)))
        assertEquals(null, MacroRecorder.toStep(HidAction.KeyRelease))
    }

    // ---------- new zone templates ----------

    @Test
    fun `quarter main split has a dominant main zone`() {
        val frames = LayoutEditorOps.zoneFrames(LayoutEditorOps.ZoneSplit.QUARTER_MAIN)
        assertEquals(3, frames.size)
        val main = frames[0]
        assertTrue(main.w > 0.6f && main.h > 0.9f)
        frames.forEach { f ->
            val s = f.sanitized()
            assertTrue(s.x >= 0f && s.x + s.w <= 1.0001f)
            assertTrue(s.y >= 0f && s.y + s.h <= 1.0001f)
        }
    }

    @Test
    fun `corner zones produce four corners plus center`() {
        val frames = LayoutEditorOps.zoneFrames(LayoutEditorOps.ZoneSplit.CORNER_ZONES)
        assertEquals(5, frames.size)
        frames.forEach { f ->
            val s = f.sanitized()
            assertTrue(s.x >= 0f && s.x + s.w <= 1.0001f && s.y >= 0f && s.y + s.h <= 1.0001f)
        }
    }
}
