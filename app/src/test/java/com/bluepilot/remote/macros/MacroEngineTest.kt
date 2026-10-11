package com.bluepilot.remote.macros

import com.bluepilot.remote.domain.MacroEngine
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Macro expansion contract: steps → executable timed plan.
 * Pure function, no Android — invalid steps must be skipped, never fatal.
 */
class MacroEngineTest {

    @Test
    fun `key steps expand to key taps with modifiers`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(name = "m", steps = listOf(MacroStep.KeyTap(0x04, 0x05)))
        )
        assertEquals(1, plan.size)
        assertEquals(HidAction.KeyTap(0x04, 0x05), plan[0].action)
        assertEquals(MacroEngine.INTER_STEP_DELAY_MS, plan[0].delayMs)
    }

    @Test
    fun `delay steps expand to pure waits`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.Delay(700)))
        )
        assertEquals(1, plan.size)
        assertEquals(null, plan[0].action)
        assertEquals(700L, plan[0].delayMs)
    }

    @Test
    fun `delay is capped by sanitize`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.Delay(999_999)))
        )
        assertEquals(MacroSpec.DELAY_MAX_MS, plan[0].delayMs)
    }

    @Test
    fun `full sequence keeps order`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.KeyTap(0x28),          // Enter
                    MacroStep.Delay(100),
                    MacroStep.TypeText("hi"),
                    MacroStep.Media(0x00CD),
                    MacroStep.MouseClick(0x01)
                )
            )
        )
        assertEquals(5, plan.size)
        assertTrue(plan[0].action is HidAction.KeyTap)
        assertEquals(null, plan[1].action)
        assertEquals(HidAction.TypeText("hi"), plan[2].action)
        assertEquals(HidAction.MediaTap(0x00CD), plan[3].action)
        assertTrue(plan[4].action is HidAction.MouseClick)
    }

    @Test
    fun `invalid mouse mask and empty text are skipped`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.MouseClick(0x40),   // invalid mask
                    MacroStep.TypeText(""),       // empty
                    MacroStep.KeyTap(0x2C)        // valid Space
                )
            )
        )
        assertEquals(1, plan.size)
        assertEquals(HidAction.KeyTap(0x2C, 0), plan[0].action)
    }

    @Test
    fun `step count is capped`() {
        val many = (1..200).map { MacroStep.KeyTap(0x04) }
        val plan = MacroEngine.Companion.expand(MacroSpec(steps = many))
        assertEquals(MacroSpec.STEPS_MAX, plan.size)
    }

    @Test
    fun `text step is capped at TEXT_MAX`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.TypeText("x".repeat(2000))))
        )
        val action = plan[0].action as HidAction.TypeText
        assertEquals(MacroSpec.TEXT_MAX, action.text.length)
    }

    @Test
    fun `macro spec sanitize fixes blank name`() {
        assertEquals("Macro", MacroSpec(name = "   ").sanitized().name)
        assertEquals(MacroSpec.NAME_MAX, MacroSpec(name = "y".repeat(99)).sanitized().name.length)
    }

    // ------------------------------------------------------------------
    // V2 MATRIX 2 — new step types
    // ------------------------------------------------------------------

    @Test
    fun `key hold expands to down then timed release`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.KeyHold(0x1A, 0x02, 800)))
        )
        assertEquals(2, plan.size)
        assertEquals(HidAction.KeyDown(0x1A, 0x02), plan[0].action)
        assertEquals(HidAction.KeyRelease, plan[1].action)
        assertEquals(800L, plan[1].delayMs)   // hold duration = wait before release
    }

    @Test
    fun `key hold duration is clamped`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.KeyHold(0x04, 0, 999_999)))
        )
        assertEquals(MacroSpec.DELAY_MAX_MS, plan[1].delayMs)
        // Below floor: 5ms hold clamps up to 20ms (too short = host misses it).
        val tiny = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.KeyHold(0x04, 0, 5)))
        )
        assertEquals(20L, tiny[1].delayMs)
    }

    @Test
    fun `random delay is deterministic with seeded random and within bounds`() {
        val spec = MacroSpec(steps = listOf(MacroStep.RandomDelay(100, 300)))
        val a = MacroEngine.Companion.expand(spec, kotlin.random.Random(42))
        val b = MacroEngine.Companion.expand(spec, kotlin.random.Random(42))
        assertEquals(a[0].delayMs, b[0].delayMs)          // same seed = same plan
        assertTrue(a[0].delayMs in 100..300)
        assertEquals(null, a[0].action)
        // Inverted bounds self-heal: max < min → max = min.
        val inv = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.RandomDelay(500, 100))),
            kotlin.random.Random(1)
        )
        assertEquals(500L, inv[0].delayMs)
    }

    @Test
    fun `repeat last unrolls the previous step`() {
        // KeyTap then RepeatLast(span=1, times=3) → 3 identical taps total.
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.KeyTap(0x04),
                    MacroStep.RepeatLast(span = 1, times = 3)
                )
            )
        )
        assertEquals(3, plan.size)
        assertTrue(plan.all { it.action == HidAction.KeyTap(0x04, 0) })
    }

    @Test
    fun `repeat spans multiple steps and keeps order`() {
        // (A, wait, RepeatLast span=2 times=2) → A wait A wait.
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.KeyTap(0x04),
                    MacroStep.Delay(100),
                    MacroStep.RepeatLast(span = 2, times = 2)
                )
            )
        )
        assertEquals(4, plan.size)
        assertEquals(HidAction.KeyTap(0x04, 0), plan[0].action)
        assertEquals(null, plan[1].action)
        assertEquals(HidAction.KeyTap(0x04, 0), plan[2].action)
        assertEquals(null, plan[3].action)
    }

    @Test
    fun `repeat as first step is a safe no-op and plan never exceeds cap`() {
        val empty = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.RepeatLast(1, 5)))
        )
        assertEquals(0, empty.size)
        // Stacked repeats can never blow past PLAN_MAX.
        val steps = mutableListOf<MacroStep>(MacroStep.KeyTap(0x04))
        repeat(20) { steps += MacroStep.RepeatLast(span = 8, times = 10) }
        val plan = MacroEngine.Companion.expand(MacroSpec(steps = steps))
        assertTrue(plan.size <= MacroEngine.PLAN_MAX)
    }

    @Test
    fun `sub macro expands inline via resolver`() {
        val sub = MacroSpec(name = "sub", steps = listOf(MacroStep.KeyTap(0x05)))
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.KeyTap(0x04),
                    MacroStep.RunMacro(7L),
                    MacroStep.KeyTap(0x06)
                )
            ),
            resolve = { id -> if (id == 7L) sub else null }
        )
        assertEquals(3, plan.size)
        assertEquals(HidAction.KeyTap(0x05, 0), plan[1].action)   // inlined
    }

    @Test
    fun `sub macro cycles and depth are guarded`() {
        // A calls B, B calls A → the back-edge is dropped, no hang.
        val a = MacroSpec(steps = listOf(MacroStep.KeyTap(0x04), MacroStep.RunMacro(2L)))
        val b = MacroSpec(steps = listOf(MacroStep.KeyTap(0x05), MacroStep.RunMacro(1L)))
        val lookup: (Long) -> MacroSpec? = { id -> when (id) { 1L -> a; 2L -> b; else -> null } }
        val plan = MacroEngine.Companion.expand(a, resolve = lookup)
        // a.tap, b.tap, then b's call BACK to a: a is not in `visiting`
        // anymore at that point? No — it IS: a(1) is only in visiting when
        // called VIA RunMacro. Root spec isn't registered, so depth cap is
        // what halts this chain. Either way: finite, bounded, ordered.
        assertTrue(plan.size <= MacroEngine.PLAN_MAX)
        assertEquals(HidAction.KeyTap(0x04, 0), plan[0].action)
        assertEquals(HidAction.KeyTap(0x05, 0), plan[1].action)
        // Direct self-call with resolver allowing it: `visiting` blocks it.
        val selfish = MacroSpec(steps = listOf(MacroStep.KeyTap(0x04), MacroStep.RunMacro(9L)))
        val selfPlan = MacroEngine.Companion.expand(
            selfish, resolve = { id -> if (id == 9L) selfish else null }
        )
        assertTrue(selfPlan.size <= MacroEngine.PLAN_MAX)
    }

    @Test
    fun `unresolved sub macro is skipped not fatal`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(steps = listOf(MacroStep.KeyTap(0x04), MacroStep.RunMacro(99L)))
        )
        assertEquals(1, plan.size)   // resolver default = null → skipped
    }

    @Test
    fun `scroll expands and zero scroll is skipped`() {
        val plan = MacroEngine.Companion.expand(
            MacroSpec(
                steps = listOf(
                    MacroStep.Scroll(3),
                    MacroStep.Scroll(0),      // no-op, skipped
                    MacroStep.Scroll(-99)     // clamped to -10
                )
            )
        )
        assertEquals(2, plan.size)
        assertEquals(HidAction.MouseScroll(3), plan[0].action)
        assertEquals(HidAction.MouseScroll(-10), plan[1].action)
    }
}
