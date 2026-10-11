package com.bluepilot.remote.ui.theme

/**
 * SECTION 1 — theme utilities (pure logic, fully unit-tested).
 *
 * [ThemeScheduler]: resolves whether "night" theme should be active for a
 * given hour, supporting windows that cross midnight (19 → 7).
 *
 * [ThemeListCodec]: tiny CSV codec for the recents / favorites lists kept
 * in DataStore (no extra tables, no migration risk).
 */
object ThemeScheduler {

    /** True when [hour] falls inside the night window [startHour, endHour). */
    fun isNight(hour: Int, startHour: Int, endHour: Int): Boolean {
        val h = ((hour % 24) + 24) % 24
        val s = ((startHour % 24) + 24) % 24
        val e = ((endHour % 24) + 24) % 24
        if (s == e) return false                    // zero-length window
        return if (s < e) h in s until e            // same-day window
        else h >= s || h < e                        // crosses midnight
    }

    /** Picks the scheduled theme id for [hour]; null when scheduling off. */
    fun scheduledThemeId(
        enabled: Boolean,
        hour: Int,
        nightStart: Int,
        nightEnd: Int,
        dayTheme: String,
        nightTheme: String
    ): String? {
        if (!enabled) return null
        return if (isNight(hour, nightStart, nightEnd)) nightTheme else dayTheme
    }
}

/**
 * V2 MATRIX 3 finale — ambient-light → dark/light decision with hysteresis
 * (pure, unit-tested). Two thresholds prevent flapping when the room sits
 * right at the boundary: it takes < [darkBelowLux] to go dark and
 * > [lightAboveLux] to go light; between them the previous state holds.
 * Typical indoor lighting is 100-500 lux; a dim evening room < 20 lux.
 */
class LightThemeGate(
    private val darkBelowLux: Float = 15f,
    private val lightAboveLux: Float = 60f
) {
    /**
     * @param lux current ambient light (NaN/negative treated as "no info").
     * @param wasDark previous decision.
     * @return new decision (dark = true).
     */
    fun decide(lux: Float, wasDark: Boolean): Boolean {
        if (lux.isNaN() || lux < 0f) return wasDark   // garbage → hold state
        return when {
            lux < darkBelowLux -> true
            lux > lightAboveLux -> false
            else -> wasDark                            // hysteresis band
        }
    }
}

object ThemeListCodec {

    fun decode(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun encode(ids: List<String>): String = ids.joinToString(",")

    /** Pushes [id] to the front, dedupes, caps at [max]. */
    fun push(csv: String, id: String, max: Int = 6): String {
        val list = decode(csv).toMutableList()
        list.remove(id)
        list.add(0, id)
        return encode(list.take(max))
    }

    /** Adds or removes [id]. */
    fun toggle(csv: String, id: String): String {
        val list = decode(csv).toMutableList()
        if (!list.remove(id)) list.add(id)
        return encode(list)
    }

    fun contains(csv: String, id: String): Boolean = decode(csv).contains(id)
}
