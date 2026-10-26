package com.aeropad.remote.domain

/**
 * Validates a navigation destination before it reaches the NavHost.
 *
 * Found by the Round 14 feature audit: launching the app with
 * `startRoute = "layout_editor"` crashed immediately with
 *
 *   java.lang.IllegalArgumentException: navigation destination layout_editor
 *   is not a direct child of this NavGraph
 *
 * ...because the editor is registered as `layout_editor/{profileId}`. Any
 * caller that supplies a route — a launcher shortcut, an intent extra, a future
 * deep link — could take the whole app down before the first frame is drawn.
 * [ShortcutActions.routeFor] already promises "a bad shortcut can never crash
 * startup"; this is the guard that actually makes that true, and it repairs the
 * one route that legitimately needs an argument.
 */
object RouteGuard {

    /** Destinations registered directly on the root graph in AeroPadApp. */
    val TOP_LEVEL = setOf(
        "home", "permissions", "connection", "devices", "mouse", "keyboard",
        "numpad", "multimedia", "presenter", "gamepad", "android_remote",
        "touch_screen", "shortcut_decks", "text_clips", "my_layout", "settings",
        "help", "layouts", "macros", "gamepad_builder", "full_keyboard",
        "pc_combo", "air_mouse", "connection_health"
    )

    /** Routes that take a required path argument, with the argument to use. */
    private val DEFAULT_ARGS = mapOf("layout_editor" to "0")

    /**
     * Returns a destination that is safe to hand to the NavHost.
     *
     * Unknown or blank input falls back to `home`; a route that needs an
     * argument gets its default one; a route that is already complete with a
     * path or query is accepted as-is.
     */
    fun sanitize(candidate: String?): String {
        val raw = candidate?.trim().orEmpty()
        if (raw.isEmpty()) return HOME

        val base = raw.substringBefore('/').substringBefore('?')

        // Routes that take a required argument are not direct children of the
        // graph, so they must never be handed over bare.
        DEFAULT_ARGS[base]?.let { fallback ->
            return if (raw.contains('/') || raw.contains('?')) raw else "$base/$fallback"
        }
        if (base in TOP_LEVEL) return raw
        return HOME
    }

    const val HOME = "home"
}
