package com.aeropad.remote.features

import com.aeropad.remote.domain.FlapGuard
import com.aeropad.remote.domain.ReconnectDecision
import com.aeropad.remote.domain.ReconnectDecision.Action
import com.aeropad.remote.domain.ReconnectPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 10 — the "won't reconnect after the host sleeps" fix.
 *
 * The behaviour these tests lock in is the point of the whole change: the app
 * must never stop trying on its own, must not hammer the radio while doing it,
 * and must recover immediately when the user picks the phone up.
 */
class ReconnectStrategyTest {

    private val policy = ReconnectPolicy()

    // ------------------------------------------------------------- scheduling

    @Test
    fun `fast phase covers a normal drop in under fifteen seconds`() {
        var total = 0L
        for (attempt in 1..5) total += policy.delayFor(attempt, random = 0.5f)
        assertEquals(500 + 1000 + 2000 + 4000 + 8000, total)
        assertTrue("a momentary drop should be solved quickly", total < 16_000)
    }

    @Test
    fun `it never gives up - attempts keep producing a delay`() {
        // The old code stopped after 4 attempts. Every one of these must still
        // schedule something, or the user is left with a dead app.
        (1..500).forEach { attempt ->
            assertTrue("attempt $attempt produced no delay", policy.delayFor(attempt, 0.5f) > 0)
        }
    }

    @Test
    fun `slow phase is calm but bounded`() {
        val first = policy.delayFor(policy.slowPhaseStartsAt(), random = 0.5f)
        val later = policy.delayFor(policy.slowPhaseStartsAt() + 6, random = 0.5f)
        assertEquals(15_000, first)
        assertTrue("must back off", later >= first)
        assertTrue("but never wait absurdly long", later <= 30_000)
    }

    @Test
    fun `delay never collapses to a busy loop`() {
        (1..50).forEach { attempt ->
            assertTrue(policy.delayFor(attempt, random = 1f) >= ReconnectPolicy.MIN_DELAY_MS)
            assertTrue(policy.delayFor(attempt, random = 0f) >= ReconnectPolicy.MIN_DELAY_MS)
        }
    }

    @Test
    fun `jitter varies the delay by at most twenty percent`() {
        val low = policy.delayFor(4, random = 0f)     // base 4000
        val high = policy.delayFor(4, random = 1f)
        assertEquals(4000L, policy.delayFor(4, random = 0.5f))
        assertTrue(low in 3_000..3_400)
        assertTrue(high in 4_600..5_000)
    }

    @Test
    fun `jitter input outside zero to one is clamped`() {
        assertEquals(policy.delayFor(2, 0f), policy.delayFor(2, -5f))
        assertEquals(policy.delayFor(2, 1f), policy.delayFor(2, 5f))
    }

    @Test
    fun `phase reporting matches the real schedule`() {
        assertTrue(policy.isFastPhase(1))
        assertTrue(policy.isFastPhase(policy.slowPhaseStartsAt() - 1))
        assertFalse(policy.isFastPhase(policy.slowPhaseStartsAt()))
        assertFalse(policy.isFastPhase(99))
    }

    @Test
    fun `attempt zero or negative is treated as the first attempt`() {
        assertEquals(policy.delayFor(1, 0.5f), policy.delayFor(0, 0.5f))
        assertEquals(policy.delayFor(1, 0.5f), policy.delayFor(-7, 0.5f))
    }

    // -------------------------------------------------------------- flap guard

    @Test
    fun `a long-lived link is not a flap`() {
        val guard = FlapGuard()
        repeat(10) { assertNull(guard.onDrop(heldMs = 30_000, nowMs = 0)) }
        assertEquals(0, guard.flaps)
        assertFalse(guard.isResting(0))
    }

    @Test
    fun `short drops accumulate until the guard rests`() {
        val guard = FlapGuard(maxFlaps = 3)
        assertNull(guard.onDrop(heldMs = 500, nowMs = 0))
        assertNull(guard.onDrop(heldMs = 500, nowMs = 0))
        val rest = guard.onDrop(heldMs = 500, nowMs = 1_000)
        assertNotNull("third flap should trigger a rest", rest)
        assertEquals(60_000, rest!!.restMs)
        assertTrue(guard.isResting(30_000))
        assertFalse(guard.isResting(1_000 + 60_000))
    }

    @Test
    fun `a good connection forgives earlier flaps`() {
        val guard = FlapGuard(maxFlaps = 3)
        guard.onDrop(500, 0)
        guard.onDrop(500, 0)
        guard.onStableLink()
        assertEquals(0, guard.flaps)
        assertNull(guard.onDrop(500, 0))   // needs three more to rest again
    }

    @Test
    fun `rest doubles on each further flap but has a ceiling`() {
        val guard = FlapGuard(maxFlaps = 3, baseRestMs = 60_000, maxRestMs = 15 * 60_000)
        guard.onDrop(100, 0); guard.onDrop(100, 0)
        val first = guard.onDrop(100, 0)!!.restMs
        val second = guard.onDrop(100, 0)!!.restMs
        assertEquals(60_000, first)
        assertEquals(120_000, second)
        repeat(20) { guard.onDrop(100, 0) }
        val capped = guard.onDrop(100, 0)!!.restMs
        assertTrue(capped <= 15 * 60_000)
    }

    @Test
    fun `user retry forgives the penalty`() {
        val guard = FlapGuard(maxFlaps = 1)
        guard.onDrop(100, 0)
        assertTrue(guard.isResting(100))
        guard.forgive()
        assertFalse(guard.isResting(100))
        assertEquals(0, guard.flaps)
    }

    @Test
    fun `remaining rest never goes negative`() {
        val guard = FlapGuard(maxFlaps = 1)
        guard.onDrop(100, 1_000)
        assertEquals(0L, guard.remainingRestMs(999_999))
        assertTrue(guard.remainingRestMs(2_000) > 0)
    }

    // ---------------------------------------------------------------- decision

    @Test
    fun `user disconnect stops everything`() {
        assertEquals(
            Action.STOP,
            ReconnectDecision.decide(
                adapterOn = true, appRegistered = true, hasRememberedHost = true,
                userInitiatedDisconnect = true, resting = false
            )
        )
    }

    @Test
    fun `no remembered host means nothing to retry`() {
        assertEquals(
            Action.STOP,
            ReconnectDecision.decide(
                adapterOn = true, appRegistered = true, hasRememberedHost = false,
                userInitiatedDisconnect = false, resting = false
            )
        )
    }

    @Test
    fun `radio off waits instead of spinning`() {
        assertEquals(
            Action.WAIT_FOR_ADAPTER,
            ReconnectDecision.decide(
                adapterOn = false, appRegistered = true, hasRememberedHost = true,
                userInitiatedDisconnect = false, resting = false
            )
        )
    }

    @Test
    fun `lost HID registration is repaired before connecting`() {
        // This is the regression that used to end the story silently.
        assertEquals(
            Action.RE_REGISTER,
            ReconnectDecision.decide(
                adapterOn = true, appRegistered = false, hasRememberedHost = true,
                userInitiatedDisconnect = false, resting = false
            )
        )
    }

    @Test
    fun `a resting flapping link does not connect yet`() {
        assertEquals(
            Action.WAIT_FOR_ADAPTER,
            ReconnectDecision.decide(
                adapterOn = true, appRegistered = true, hasRememberedHost = true,
                userInitiatedDisconnect = false, resting = true
            )
        )
    }

    @Test
    fun `the healthy case connects`() {
        assertEquals(
            Action.CONNECT,
            ReconnectDecision.decide(
                adapterOn = true, appRegistered = true, hasRememberedHost = true,
                userInitiatedDisconnect = false, resting = false
            )
        )
    }

    // ------------------------------------------------------------------ status

    @Test
    fun `status line never claims the app gave up`() {
        val lines = (1..40).map { attempt ->
            ReconnectDecision.statusLine(
                attempt = attempt,
                fastPhase = policy.isFastPhase(attempt),
                nextRetryInMs = policy.delayFor(attempt, 0.5f)
            )
        }
        assertTrue(lines.none { it.contains("gave up", ignoreCase = true) })
        assertTrue(lines.all { it.isNotBlank() })
    }

    @Test
    fun `idle status is blank`() {
        assertEquals("", ReconnectDecision.statusLine(0, true, null))
    }
}
