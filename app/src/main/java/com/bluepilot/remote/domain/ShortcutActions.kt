package com.bluepilot.remote.domain

/**
 * V2 MATRIX 8 — launcher shortcut → start route mapping (pure, unit-tested).
 *
 * Static shortcuts (long-press app icon) fire intents with these action
 * strings; MainActivity resolves them to a navigation start route through
 * [routeFor]. Unknown/null actions land on HOME — a bad shortcut can never
 * crash startup or navigate nowhere.
 */
object ShortcutActions {
    const val OPEN_KEYBOARD = "com.bluepilot.remote.OPEN_KEYBOARD"
    const val OPEN_MOUSE = "com.bluepilot.remote.OPEN_MOUSE"
    const val OPEN_GAMEPAD = "com.bluepilot.remote.OPEN_GAMEPAD"
    const val OPEN_MEDIA = "com.bluepilot.remote.OPEN_MEDIA"

    const val ROUTE_HOME = "home"

    /** Maps a shortcut intent action to a nav route; null/unknown → HOME. */
    fun routeFor(action: String?): String = when (action) {
        OPEN_KEYBOARD -> "full_keyboard"
        OPEN_MOUSE -> "mouse"
        OPEN_GAMEPAD -> "gamepad_builder"
        OPEN_MEDIA -> "multimedia"
        else -> ROUTE_HOME
    }
}
