package com.bluepilot.remote.domain

import com.bluepilot.remote.domain.FlickDetector.FlickDirection

/**
 * V2 MATRIX 3 finale — flick direction → HID button mapping (pure).
 *
 * Values are HID button indices 0..15, or -1 = direction unmapped.
 * DEFAULT preserves the original flick feature exactly: UP taps button 0
 * (jump convention), the other directions are off.
 */
object FlickMapping {

    val DEFAULT: Map<FlickDirection, Int> = mapOf(
        FlickDirection.UP to 0,
        FlickDirection.DOWN to -1,
        FlickDirection.LEFT to -1,
        FlickDirection.RIGHT to -1
    )

    /** Buttons offered by the cycle UI: off, A(0), B(1), X(2), Y(3). */
    private val CYCLE = listOf(-1, 0, 1, 2, 3)

    /**
     * Next mapping in the cycle for [direction]; other directions
     * untouched. Unknown current values snap to off (defensive).
     */
    fun cycled(
        current: Map<FlickDirection, Int>,
        direction: FlickDirection
    ): Map<FlickDirection, Int> {
        val now = current[direction] ?: -1
        val at = CYCLE.indexOf(now)
        val next = CYCLE[(if (at < 0) 0 else at + 1) % CYCLE.size]
        return current + (direction to next)
    }

    /** Short label for chips: "×" when off, else the button name. */
    fun label(index: Int?): String = when (index) {
        0 -> "A"; 1 -> "B"; 2 -> "X"; 3 -> "Y"
        else -> "×"
    }

    /** V2 M3 persistence — spec map (string keys) → runtime map.
     *  Unknown keys ignored; missing directions fall back to off. */
    fun fromSpec(saved: Map<String, Int>): Map<FlickDirection, Int> =
        FlickDirection.values().associateWith { dir ->
            (saved[dir.name] ?: -1).coerceIn(-1, 15)
        }

    /** Runtime map → spec map for JSON storage. */
    fun toSpec(map: Map<FlickDirection, Int>): Map<String, Int> =
        map.entries.associate { it.key.name to it.value.coerceIn(-1, 15) }
}
