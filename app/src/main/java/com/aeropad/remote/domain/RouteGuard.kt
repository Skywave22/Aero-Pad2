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

    /** Resolve only registered routes, and canonicalize the editor's Long argument. */
    fun sanitize(candidate: String?): String {
        val raw = candidate?.trim().orEmpty().substringBefore('?')
        if (raw in TOP_LEVEL) return raw
        if (raw == "layout_editor") return "layout_editor/0"
        if (raw.startsWith("layout_editor/")) {
            val id = raw.substringAfter('/').toLongOrNull()
            if (id != null && id >= 0) return "layout_editor/$id"
        }
        return HOME
    }

    const val HOME = "home"
}
