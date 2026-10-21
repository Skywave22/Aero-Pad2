package com.aeropad.remote.domain

import kotlin.math.abs

/**
 * How hard — and how often — AeroPad tries to get the link back.
 *
 * **Why this replaced the old logic.** The previous implementation gave up
 * after four attempts (1 + 2 + 4 + 8 = 15 s) and then went silent. Every review
 * of every app in this category contains the same sentence in some form: *"it
 * won't reconnect after the PC sleeps"* — because a host that goes to sleep for
 * a minute, or a phone that dozes for five, outlives a 15-second budget. The app
 * was correct and useless at the same time.
 *
 * The new strategy never gives up while the user still wants the connection,
 * but it does not hammer the radio either:
 *
 *  - **fast phase** — 0.5 s, 1 s, 2 s, 4 s, 8 s. Covers the normal case (a
 *    momentary drop, a host waking up) in under 15 s.
 *  - **slow phase** — then every 15 s, backing off to a 30 s ceiling, forever.
 *    A sleeping host costs one cheap connect attempt every half minute instead
 *    of a dead app.
 * - **jitter** — ±20 %, so retries do not resonate with the host's own
 *    scanning interval.
 *
 * Pure Kotlin, no Android, injectable randomness: the whole schedule is unit
 * tested.
 */
class ReconnectPolicy(
    private val fastDelaysMs: LongArray = DEFAULT_FAST_DELAYS,
    private val slowDelayMs: Long = 15_000L,
    private val maxDelayMs: Long = 30_000L,
    private val jitterFraction: Float = 0.2f
) {

    companion object {
        val DEFAULT_FAST_DELAYS = longArrayOf(500L, 1_000L, 2_000L, 4_000L, 8_000L)

        /** Never schedule anything shorter than this, even with jitter. */
        const val MIN_DELAY_MS = 250L
    }

    /** True while we are still in the fast phase (worth showing a countdown). */
    fun isFastPhase(attempt: Int): Boolean = attempt in 1..fastDelaysMs.size

    /**
     * Delay before attempt number [attempt] (1-based).
     *
     * @param random a value in 0f..1f; callers pass a real random, tests pass a
     *   constant so the schedule is deterministic.
     */
    fun delayFor(attempt: Int, random: Float = 0.5f): Long {
        val base = baseDelay(attempt)
        val jitter = 1f + (random.coerceIn(0f, 1f) * 2f - 1f) * jitterFraction
        return (base * jitter).toLong().coerceAtLeast(MIN_DELAY_MS)
    }

    private fun baseDelay(attempt: Int): Long {
        val a = attempt.coerceAtLeast(1)
        if (a <= fastDelaysMs.size) return fastDelaysMs[a - 1]
        // Slow phase: 15 s, 30 s, then the ceiling.
        val step = a - fastDelaysMs.size - 1
        if (step <= 0) return slowDelayMs
        var d = slowDelayMs
        repeat(step) { d = d * 2 }
        return d.coerceAtMost(maxDelayMs)
    }

    /**
     * Attempt number at which the schedule reaches its slowest cadence — used
     * to explain to the user that the app is still trying, just quietly.
     */
    fun slowPhaseStartsAt(): Int = fastDelaysMs.size + 1
}

/**
 * Detects a link that connects and immediately dies, without punishing the user
 * forever.
 *
 * The old code counted three short-lived connections and then refused to try
 * again until the user intervened — but a host that was mid-wake is exactly the
 * situation that produces flaps, so the penalty landed on the one case the user
 * most wanted fixed. Now a flapping link **rests** for a while (60 s, doubling
 * to a 15-minute cap) and then tries again on its own. A link that stays up for
 * [stableLinkMs] clears the history completely.
 *
 * Clock is injected; no Android types.
 */
class FlapGuard(
    private val stableLinkMs: Long = 5_000L,
    private val maxFlaps: Int = 3,
    private val baseRestMs: Long = 60_000L,
    private val maxRestMs: Long = 15 * 60_000L
) {

    /** A rest imposed after too many flaps. */
    data class Rest(val restMs: Long, val untilMs: Long)

    var flaps: Int = 0
        private set

    var restUntilMs: Long = 0L
        private set

    /** Call on every successful connection: a long-lived link clears history. */
    fun onStableLink() {
        flaps = 0
        restUntilMs = 0
    }

    /**
     * Record a drop.
     *
     * @param heldMs how long the link stayed up before dropping.
     * @param nowMs current time.
     * @return a [Rest] when the app should wait instead of retrying immediately,
     *   or null when a normal retry is fine.
     */
    fun onDrop(heldMs: Long, nowMs: Long): Rest? {
        if (heldMs >= stableLinkMs) {
            // It worked for a while — this is a normal drop, not a flap.
            flaps = 0
            return null
        }
        flaps++
        if (flaps < maxFlaps) return null

        val step = (flaps - maxFlaps).coerceAtMost(8)
        var rest = baseRestMs
        repeat(step) { rest *= 2 }
        rest = rest.coerceAtMost(maxRestMs)
        restUntilMs = nowMs + rest
        return Rest(restMs = rest, untilMs = restUntilMs)
    }

    /** True while the guard is holding retries back. */
    fun isResting(nowMs: Long): Boolean = nowMs < restUntilMs

    /** Milliseconds left in the current rest (0 when not resting). */
    fun remainingRestMs(nowMs: Long): Long = (restUntilMs - nowMs).coerceAtLeast(0L)

    /** User asked to try again now — drop the penalty. */
    fun forgive() {
        flaps = 0
        restUntilMs = 0L
    }
}

/**
 * Decides *when* a retry should happen given the engine's current situation.
 *
 * Kept separate from [ReconnectPolicy] because the interesting part is not the
 * delay — it is which of these is actually stopping the connection:
 *
 *  - the adapter is off              → nothing to do until it comes back
 *  - the HID app got unregistered    → must re-register first, then connect
 *  - the host is asleep              → keep the slow cadence running
 *
 * Pure data so the decision table is testable without a Bluetooth stack.
 */
object ReconnectDecision {

    enum class Action {
        /** Wait: the radio is off, retrying would only burn battery. */
        WAIT_FOR_ADAPTER,
        /** Re-register the HID app, then continue. */
        RE_REGISTER,
        /** A connect attempt is appropriate now. */
        CONNECT,
        /** Nothing to retry (no host remembered, or the user disconnected). */
        STOP
    }

    fun decide(
        adapterOn: Boolean,
        appRegistered: Boolean,
        hasRememberedHost: Boolean,
        userInitiatedDisconnect: Boolean,
        resting: Boolean
    ): Action = when {
        userInitiatedDisconnect -> Action.STOP
        !hasRememberedHost -> Action.STOP
        !adapterOn -> Action.WAIT_FOR_ADAPTER
        !appRegistered -> Action.RE_REGISTER
        resting -> Action.WAIT_FOR_ADAPTER
        else -> Action.CONNECT
    }

    /** Small helper for the UI: "still trying" vs "gave up". Never gives up. */
    fun statusLine(attempt: Int, fastPhase: Boolean, nextRetryInMs: Long?): String = when {
        attempt <= 0 -> ""
        nextRetryInMs == null -> "Reconnecting…"
        fastPhase -> "Reconnecting — attempt $attempt in ${seconds(nextRetryInMs)}s"
        else -> "Still trying every ${seconds(nextRetryInMs)}s — is the host awake?"
    }

    private fun seconds(ms: Long): Long = (abs(ms) + 999) / 1000
}
