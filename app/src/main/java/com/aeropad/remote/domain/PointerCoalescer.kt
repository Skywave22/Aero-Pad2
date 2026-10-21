package com.aeropad.remote.domain

/**
 * Batches pointer movement so the radio only carries what the eye can see.
 *
 * **Why this exists.** Every review of every phone-as-mouse app says the same
 * thing: the cursor stutters. The usual cause is not the transport, it is that
 * a 240 Hz touchscreen produces ~240 Bluetooth reports per second, and a
 * Bluetooth link carrying a HID channel cannot drain that queue — the backlog
 * shows up as the cursor lagging behind the finger, then jumping.
 *
 * This coalescer merges everything that arrives inside one window into a single
 * report and **keeps the total delta**, so the pointer lands in exactly the same
 * place it would have without coalescing — only fewer packets carry it.
 *
 * Rules that make it safe:
 *  - the **first** delta of a gesture is sent immediately, so there is no added
 *    latency at the moment the user starts moving;
 *  - a delta that would otherwise wait longer than [windowMs] is flushed on the
 *    next call, so the cursor can never lag behind by more than one window;
 *  - a big jump ([maxPendingPx]) is never held back;
 *  - [flush] lets the caller emit the tail exactly at gesture end, so the
 *    cursor never stops a few pixels short.
 *
 * Pure Kotlin with an injectable clock — fully unit tested.
 */
class PointerCoalescer(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxPendingPx: Int = MAX_PENDING_PX,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {

    companion object {
        /**
         * ~80 Hz. Below this the eye cannot see a difference on a cursor;
         * well below the 240 Hz of modern touch panels, so radio work drops
         * by up to 3x on bursty input.
         */
        const val DEFAULT_WINDOW_MS = 12L

        /** A flick this large is never worth batching. */
        const val MAX_PENDING_PX = 48
    }

    private var pendingX = 0
    private var pendingY = 0
    private var lastSendMs = 0L
    private var started = false

    /** True when a delta is waiting for its window to close. */
    val hasPending: Boolean get() = pendingX != 0 || pendingY != 0

    /**
     * Offer one move delta. Returns the delta to send now, or (0, 0) to hold.
     */
    fun accumulate(dx: Int, dy: Int): Pair<Int, Int> {
        if (dx == 0 && dy == 0) return 0 to 0
        pendingX += dx
        pendingY += dy

        val now = clock()

        // First move of a gesture: send at once, never make the user wait.
        if (!started) {
            started = true
            return drain(now)
        }

        // Never hold a big jump.
        if (kotlin.math.abs(pendingX) >= maxPendingPx ||
            kotlin.math.abs(pendingY) >= maxPendingPx
        ) {
            return drain(now)
        }

        if (now - lastSendMs >= windowMs) return drain(now)
        return 0 to 0
    }

    /** Emit whatever is pending (call at gesture end). */
    fun flush(): Pair<Int, Int> = drain(clock())

    /** Forget the gesture entirely (no report is sent). */
    fun reset() {
        pendingX = 0
        pendingY = 0
        started = false
        lastSendMs = 0L
    }

    private fun drain(now: Long): Pair<Int, Int> {
        val out = pendingX to pendingY
        pendingX = 0
        pendingY = 0
        lastSendMs = now
        return out
    }
}
