package com.aeropad.remote.ui.screens.settings

/**
 * In-app release notes.
 *
 * Round 19 — the app had no way to tell anyone what changed between builds;
 * the only trace of a version was a `v3.6.0` chip sitting on the front page,
 * which told the user a number and nothing else. The version now lives in
 * Settings → About, next to this list, so "what did it just update?" has an
 * answer inside the app instead of only in a Git commit.
 *
 * Newest entry first. `CHANGELOG.md` at the repo root mirrors this for people
 * reading the repository, and `store/PLAY_STORE_LISTING.md` carries the
 * Play-Store "What's new" block — keep the three in step when cutting a build.
 */

data class ReleaseNote(
    val version: String,
    /** Short headline the user actually reads. */
    val headline: String,
    val bullets: List<String>
)

val releaseNotes: List<ReleaseNote> = listOf(
    ReleaseNote(
        version = "3.6.1",
        headline = "A home screen that looks made, not generated",
        bullets = listOf(
            "One status line instead of two: the connection card and the big Connect button are now a single tap-to-act row.",
            "The version number left the front page — Settings → About has it, with this list next to it.",
            "New hand-drawn icon set. Section colour now means something: blue for controls, green for builders, grey for system.",
            "Quick Tools fits on the screen — no sideways scrolling, no emoji.",
            "Every tile can be scrolled clear of the bottom dock, in portrait and landscape.",
            "Full Board keys have a lit top face and a bottom lip, so they read as physical keys.",
            "\"Not connected\" is a quiet one-line note on control screens, not a red alert before you have done anything wrong.",
            "Full Board: pressing a rounded key no longer flashes a square ripple behind it in light mode."
        )
    ),
    ReleaseNote(
        version = "3.6.0",
        headline = "Crash fix on Mouse and Keyboard",
        bullets = listOf(
            "Fixed the crash when opening Mouse or Keyboard (state reset ran before its own fields existed).",
            "Keyboard IME no longer covers text fields — handled once for every screen.",
            "Settings can be scrolled clear of the dock.",
            "Whole-repo audit fixes: duplicate back-stack entries, landscape overflow, ripple clipping."
        )
    )
)
