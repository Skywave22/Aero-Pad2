package com.aeropad.remote.domain

/**
 * Hold-to-repeat ("turbo") for a key button.
 *
 * Holding a button on a phone is the natural gesture for "keep pressing" — fire
 * the same key over and over, or hold a key down for continuous movement. Every
 * gamepad and every serious macro tool has it; a phone remote without it makes
 * you tap forty times to walk somewhere.
 *
 * The behaviour is deliberately conservative:
 *
 *  - nothing fires until the key has been held for [holdDelayMs] (300 ms), so a
 *    normal tap can never produce two presses;
 *  - the first repeat is fired immediately at that point, then every
 *    [intervalMs] (60 ms — about 16 presses/second, the fastest rate a HID
 *    keyboard reliably accepts);
 *  - releasing the button removes the final press, so a hold of 1.5 s produces a
 *    predictable count rather than trailing repeats after release.
 *
 * Pure Kotlin with an injected clock so the whole thing is unit tested.
 */
class TurboRepeater(
    private val holdDelayMs: Long = DEFAULT_HOLD_DELAY_MS,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {

    companion object {
        /** Long enough that a tap is never mistaken for a hold. */
        const val DEFAULT_HOLD_DELAY_MS = 300L

        /** ~16 presses/second: fast, but every host keeps up. */
        const val DEFAULT_INTERVAL_MS = 60L

        /** Never replay more than this after a stall. */
        const val MAX_CATCH_UP = 3
    }

    private var startedAtMs = 0L
    private var nextFireAtMs = 0L
    private var active = false
    /** Explicit flag: a clock that starts at 0 must not look like "stopped". */
    private var running = false

    /** True once the hold has matured into repeating. */
    val isRepeating: Boolean get() = active

    /** Call when the button goes down. Does not fire anything. */
    fun start() {
        running = true
        startedAtMs = clock()
        nextFireAtMs = startedAtMs + holdDelayMs
        active = false
    }

    /**
     * Ask "should I fire now?" — returns the number of presses due since the
     * last call (0, 1 or, after a stall, more than one).
     *
     * Bounded on purpose: a long stall (app backgrounded, GC pause) must not
     * dump 200 keypresses at the host when it resumes.
     */
    fun poll(nowMs: Long = clock()): Int {
        if (!running) return 0
        if (nowMs < nextFireAtMs) return 0

        active = true
        var due = 0
        while (nextFireAtMs <= nowMs && due < MAX_CATCH_UP) {
            due++
            nextFireAtMs += intervalMs
        }
        // If we hit the catch-up cap, resynchronise instead of spiralling.
        if (nextFireAtMs <= nowMs) nextFireAtMs = nowMs + intervalMs
        return due
    }

    /** Call on release. */
    fun stop() {
        running = false
        startedAtMs = 0L
        nextFireAtMs = 0L
        active = false
    }

    /** How long the button has been held, 0 when not held. */
    fun heldForMs(nowMs: Long = clock()): Long =
        if (!running) 0L else (nowMs - startedAtMs).coerceAtLeast(0L)
}
