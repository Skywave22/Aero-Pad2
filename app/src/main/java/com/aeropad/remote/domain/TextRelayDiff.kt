package com.aeropad.remote.domain

/**
 * Turns "the text field now contains X" into the minimum HID work needed on
 * the host: some Backspaces, then some new characters.
 *
 * Round 15 — needed for live IME typing. Soft keyboards do not append one
 * character at a time; they rewrite the field for autocorrect, suggestions,
 * select-all-and-replace, and backspace. A naive "send whatever is new at the
 * end" relay either duplicates characters or drifts out of sync the first time
 * a word is autocorrected, which is exactly the sort of bug that makes the
 * feature feel broken on a TV search box.
 *
 * Pure Kotlin so it can be tested without a device.
 */
object TextRelayDiff {

    data class Plan(val backspaces: Int, val insert: String)

    /**
     * @param previous what the host has right now
     * @param next     what the field contains now
     */
    fun plan(previous: String, next: String): Plan {
        if (previous == next) return Plan(0, "")

        // Common prefix: everything the user did not touch.
        var same = 0
        val limit = minOf(previous.length, next.length)
        while (same < limit && previous[same] == next[same]) same++

        // Everything in `previous` from `same` onward was removed, then the rest
        // of `next` was typed.
        val removed = previous.length - same
        return Plan(backspaces = removed, insert = next.substring(same))
    }
}
