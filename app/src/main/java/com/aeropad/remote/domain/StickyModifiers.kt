package com.aeropad.remote.domain

import com.aeropad.remote.model.HidModifiers

/**
 * F3 — latched modifier state for one-handed typing.
 *
 * The problem this solves: typing Ctrl+Shift+Esc (or Alt+Tab) on a phone needs
 * one finger holding a modifier while another finger taps a key. That is
 * awkward two-handed and impossible one-handed.
 *
 * Behaviour (mirrors a laptop Fn/Caps-style latch, or the iOS keyboard):
 *  - **tap once**  → the modifier latches for exactly the next keystroke
 *  - **double-tap** → the modifier locks down until tapped again
 *  - tap a locked modifier → turns it off
 *
 * Pure Kotlin with no Android imports, so the whole state machine is unit
 * tested without a device. [doubleTapWindowMs] is injectable for tests.
 */
class StickyModifiers(private val doubleTapWindowMs: Long = DEFAULT_WINDOW_MS) {

    companion object {
        const val DEFAULT_WINDOW_MS = 450L

        /** Modifier bits a latch may hold (both sides of Ctrl/Shift/Alt/Gui). */
        const val ALL: Int = (HidModifiers.LEFT_CTRL.toInt() or
            HidModifiers.LEFT_SHIFT.toInt() or
            HidModifiers.LEFT_ALT.toInt() or
            HidModifiers.LEFT_GUI.toInt() or
            HidModifiers.RIGHT_CTRL.toInt() or
            HidModifiers.RIGHT_SHIFT.toInt() or
            HidModifiers.RIGHT_ALT.toInt() or
            HidModifiers.RIGHT_GUI.toInt()) and 0xFF
    }

    private var latchedBits: Int = 0
    private var lockedBits: Int = 0
    private var lastToggledBit: Int = -1
    private var lastToggleAtMs: Long = Long.MIN_VALUE

    /** One-shot modifiers (cleared by the next keystroke). */
    val latched: Int get() = latchedBits

    /** Locked modifiers (survive keystrokes until unlocked). */
    val locked: Int get() = lockedBits

    /** Combined mask to OR into the next keyboard report. */
    val mask: Int get() = (latchedBits or lockedBits) and 0xFF

    val isActive: Boolean get() = mask != 0

    fun isLatched(mod: Byte): Boolean = (latchedBits and (mod.toInt() and 0xFF)) != 0

    fun isLocked(mod: Byte): Boolean = (lockedBits and (mod.toInt() and 0xFF)) != 0

    /**
     * Handle a tap on [mod] at [nowMs]. Returns the new combined [mask].
     *
     * A tap while LOCKED unlocks it (and drops any latch for that bit), so the
     * third tap in a row always returns the modifier to "off" — there is no
     * state a user can get stuck in.
     */
    fun toggle(mod: Byte, nowMs: Long = System.currentTimeMillis()): Int {
        val bit = mod.toInt() and 0xFF
        if (bit == 0 || (bit and ALL) == 0) return mask

        val withinWindow = bit == lastToggledBit &&
            lastToggleAtMs != Long.MIN_VALUE &&
            nowMs - lastToggleAtMs <= doubleTapWindowMs
        lastToggledBit = bit
        lastToggleAtMs = nowMs

        when {
            // Locked → tap turns it off.
            (lockedBits and bit) != 0 -> {
                lockedBits = lockedBits and bit.inv()
                latchedBits = latchedBits and bit.inv()
            }
            // Latched + quick second tap → promote to locked.
            (latchedBits and bit) != 0 && withinWindow -> {
                latchedBits = latchedBits and bit.inv()
                lockedBits = lockedBits or bit
            }
            // Latched + slow second tap → off.
            (latchedBits and bit) != 0 -> {
                latchedBits = latchedBits and bit.inv()
            }
            // Off → latch.
            else -> latchedBits = latchedBits or bit
        }
        return mask
    }

    /**
     * Called after a keystroke consumed the one-shot modifiers.
     * Locked modifiers stay down (that is the point of locking).
     */
    fun consume(): Int {
        latchedBits = 0
        return mask
    }

    /** Force everything off (disconnect, panic button, leaving the screen). */
    fun clear() {
        latchedBits = 0
        lockedBits = 0
        lastToggledBit = -1
        lastToggleAtMs = Long.MIN_VALUE
    }
}
