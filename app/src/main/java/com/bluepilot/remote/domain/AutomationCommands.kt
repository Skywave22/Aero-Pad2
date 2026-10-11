package com.bluepilot.remote.domain

import com.bluepilot.remote.model.HidConsumer

/**
 * V2 MATRIX 8 b2 — Tasker/automation intent API (pure parsing, unit-tested).
 *
 * External automation apps broadcast [ACTION] with string extras:
 *   cmd = "macro"      + name=<macro name>   → play a saved macro
 *   cmd = "media"      + key=<media key>     → media tap (play_pause, mute,
 *                                              vol_up, vol_down, next, prev)
 *   cmd = "type"       + text=<string ≤200>  → type text on the host
 *   cmd = "disconnect"                       → drop the HID link
 *
 * Parsing is total: bad/unknown input → null, never an exception. The
 * receiver additionally requires the user-enabled Settings toggle
 * (default OFF) — an installed rogue app can't drive the host unless the
 * user explicitly opted in.
 */
object AutomationCommands {

    const val ACTION = "com.bluepilot.remote.AUTOMATION"
    const val EXTRA_CMD = "cmd"
    const val EXTRA_NAME = "name"
    const val EXTRA_KEY = "key"
    const val EXTRA_TEXT = "text"
    const val TEXT_MAX = 200

    sealed class Command {
        data class PlayMacro(val name: String) : Command()
        data class MediaKey(val usage: Int) : Command()
        data class TypeText(val text: String) : Command()
        data object Disconnect : Command()
    }

    /** Media key name → consumer usage; unknown → null. */
    fun mediaUsage(key: String?): Int? = when (key?.trim()?.lowercase()) {
        "play_pause", "playpause", "play", "pause" -> HidConsumer.PLAY_PAUSE
        "mute" -> HidConsumer.MUTE
        "vol_up", "volume_up" -> HidConsumer.VOLUME_UP
        "vol_down", "volume_down" -> HidConsumer.VOLUME_DOWN
        "next", "next_track" -> HidConsumer.NEXT_TRACK
        "prev", "previous", "prev_track" -> HidConsumer.PREV_TRACK
        "stop" -> HidConsumer.STOP
        else -> null
    }

    /** Total parse: extras → command or null. Never throws. */
    fun parse(cmd: String?, name: String?, key: String?, text: String?): Command? =
        when (cmd?.trim()?.lowercase()) {
            "macro" -> name?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 }
                ?.let { Command.PlayMacro(it) }
            "media" -> mediaUsage(key)?.let { Command.MediaKey(it) }
            "type" -> text?.take(TEXT_MAX)?.takeIf { it.isNotEmpty() }
                ?.let { Command.TypeText(it) }
            "disconnect" -> Command.Disconnect
            else -> null
        }
}
