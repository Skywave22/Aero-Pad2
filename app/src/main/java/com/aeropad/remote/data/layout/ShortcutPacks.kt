package com.aeropad.remote.data.layout

import com.aeropad.remote.model.HidConsumer
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.widgets.LayoutSpec
import com.aeropad.remote.model.widgets.WidgetAction
import com.aeropad.remote.model.widgets.WidgetFrame
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.model.widgets.WidgetStyle
import com.aeropad.remote.model.widgets.WidgetType

/**
 * F2 — Shortcut Packs.
 *
 * Ready-made layouts built from *documented* shortcuts only. Nothing here is
 * invented: every chord is a well-known, stable OS shortcut (Windows/Linux and
 * macOS where they differ, both are offered). No accounts, no network, no
 * second editor (X1/X2/X4/X6) — these are ordinary [LayoutSpec]s that go
 * through the exact same seeding → persistence → editor path as the original
 * three starters.
 *
 * Packs are additive and idempotent: [LayoutRepository.seedIfEmpty] inserts any
 * pack whose name is not already present, so upgrades receive new packs without
 * touching or duplicating user layouts.
 */
object ShortcutPacks {

    private val MOD_CTRL: Byte = HidModifiers.LEFT_CTRL
    private val MOD_SHIFT: Byte = HidModifiers.LEFT_SHIFT
    private val MOD_ALT: Byte = HidModifiers.LEFT_ALT
    private val MOD_GUI: Byte = HidModifiers.LEFT_GUI

    // Backgrounds keep every pack visually consistent with the starters.
    private const val BG_DEFAULT = 0xFF1A2238
    private const val BG_ACCENT = 0xFF2F6BFF
    private const val BG_DANGER = 0xFFB3261E
    private const val BG_GOOD = 0xFF1E6B45

    /** All packs, in the order they should appear in the layout list. */
    fun all(): List<LayoutSpec> = listOf(
        editing(),
        browser(),
        windowManager(),
        presentAndMeet(),
        mediaStudio()
    )

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun key(
        id: String, x: Float, y: Float, w: Float, h: Float,
        label: String, icon: String, k: Byte, mods: Byte = 0,
        bg: Long = BG_DEFAULT.toLong()
    ) = WidgetSpec(
        id = id,
        type = WidgetType.BUTTON,
        frame = WidgetFrame(x, y, w, h),
        style = WidgetStyle(label = label, icon = icon, backgroundColor = bg),
        action = WidgetAction.KeyTap(k, mods)
    )

    private fun text(
        id: String, x: Float, y: Float, w: Float, h: Float,
        label: String, payload: String, bg: Long = BG_DEFAULT.toLong()
    ) = WidgetSpec(
        id = id,
        type = WidgetType.BUTTON,
        frame = WidgetFrame(x, y, w, h),
        style = WidgetStyle(label = label, icon = "", backgroundColor = bg),
        action = WidgetAction.TypeText(payload)
    )

    private fun media(
        id: String, x: Float, y: Float, w: Float, h: Float,
        label: String, icon: String, usage: Int, bg: Long = BG_DEFAULT.toLong()
    ) = WidgetSpec(
        id = id,
        type = WidgetType.BUTTON,
        frame = WidgetFrame(x, y, w, h),
        style = WidgetStyle(label = label, icon = icon, backgroundColor = bg),
        action = WidgetAction.Media(usage)
    )

    private fun pad(id: String, x: Float, y: Float, w: Float, h: Float, label: String) =
        WidgetSpec(
            id = id,
            type = WidgetType.TRACKPAD,
            frame = WidgetFrame(x, y, w, h),
            style = WidgetStyle(label = label)
        )

    private fun strip(id: String, x: Float, y: Float, w: Float, h: Float, label: String) =
        WidgetSpec(
            id = id,
            type = WidgetType.SCROLL_STRIP,
            frame = WidgetFrame(x, y, w, h),
            style = WidgetStyle(label = label)
        )

    // ------------------------------------------------------------------
    // Pack 1 — Editing / text
    // ------------------------------------------------------------------
    /** Clipboard, undo/redo, find, select-all, save. Trackpad on top for cursor work. */
    internal fun editing() = LayoutSpec(
        name = "Editing Pack",
        widgets = listOf(
            pad("e-pad", 0.02f, 0.02f, 0.79f, 0.40f, "Trackpad"),
            strip("e-scroll", 0.83f, 0.02f, 0.15f, 0.40f, "Scroll"),

            key("e-undo", 0.02f, 0.45f, 0.23f, 0.11f, "Undo", "↶", HidKeys.Z, MOD_CTRL),
            key("e-redo", 0.27f, 0.45f, 0.23f, 0.11f, "Redo", "↷", HidKeys.Z, (MOD_CTRL.toInt() or MOD_SHIFT.toInt()).toByte()),
            key("e-save", 0.52f, 0.45f, 0.23f, 0.11f, "Save", "💾", HidKeys.S, MOD_CTRL, BG_ACCENT.toLong()),
            key("e-find", 0.77f, 0.45f, 0.21f, 0.11f, "Find", "🔍", HidKeys.F, MOD_CTRL),

            key("e-copy", 0.02f, 0.58f, 0.23f, 0.11f, "Copy", "", HidKeys.C, MOD_CTRL),
            key("e-cut", 0.27f, 0.58f, 0.23f, 0.11f, "Cut", "", HidKeys.X, MOD_CTRL),
            key("e-paste", 0.52f, 0.58f, 0.23f, 0.11f, "Paste", "", HidKeys.V, MOD_CTRL),
            key("e-all", 0.77f, 0.58f, 0.21f, 0.11f, "All", "", HidKeys.A, MOD_CTRL),

            key("e-bksp", 0.02f, 0.71f, 0.23f, 0.12f, "⌫", "", HidKeys.BACKSPACE),
            key("e-del", 0.27f, 0.71f, 0.23f, 0.12f, "Del", "", HidKeys.DELETE),
            key("e-enter", 0.52f, 0.71f, 0.23f, 0.12f, "Enter", "", HidKeys.ENTER, bg = BG_GOOD.toLong()),
            key("e-esc", 0.77f, 0.71f, 0.21f, 0.12f, "Esc", "", HidKeys.ESCAPE),

            key("e-home", 0.02f, 0.85f, 0.23f, 0.12f, "Home", "", HidKeys.HOME),
            key("e-left", 0.27f, 0.85f, 0.23f, 0.12f, "←", "", HidKeys.ARROW_LEFT),
            key("e-right", 0.52f, 0.85f, 0.23f, 0.12f, "→", "", HidKeys.ARROW_RIGHT),
            key("e-end", 0.77f, 0.85f, 0.21f, 0.12f, "End", "", HidKeys.END)
        )
    )

    // ------------------------------------------------------------------
    // Pack 2 — Browser / navigation
    // ------------------------------------------------------------------
    /** Tab, history, address bar, reload, page scroll, full screen. */
    internal fun browser() = LayoutSpec(
        name = "Browser Pack",
        widgets = listOf(
            pad("b-pad", 0.02f, 0.02f, 0.79f, 0.38f, "Trackpad"),
            strip("b-scroll", 0.83f, 0.02f, 0.15f, 0.38f, "Scroll"),

            key("b-back", 0.02f, 0.42f, 0.23f, 0.11f, "Back", "◀", HidKeys.ARROW_LEFT, MOD_ALT),
            key("b-fwd", 0.27f, 0.42f, 0.23f, 0.11f, "Fwd", "▶", HidKeys.ARROW_RIGHT, MOD_ALT),
            key("b-reload", 0.52f, 0.42f, 0.23f, 0.11f, "Reload", "⟳", HidKeys.F5),
            key("b-full", 0.77f, 0.42f, 0.21f, 0.11f, "Full", "⛶", HidKeys.F11),

            key("b-newtab", 0.02f, 0.55f, 0.32f, 0.11f, "New tab", "+", HidKeys.T, MOD_CTRL),
            key("b-closetab", 0.36f, 0.55f, 0.32f, 0.11f, "Close tab", "✕", HidKeys.W, MOD_CTRL),
            key("b-restore", 0.70f, 0.55f, 0.28f, 0.11f, "Reopen", "↺", HidKeys.T, (MOD_CTRL.toInt() or MOD_SHIFT.toInt()).toByte()),

            key("b-url", 0.02f, 0.68f, 0.47f, 0.11f, "Address bar", "", HidKeys.L, MOD_CTRL),
            key("b-tabnext", 0.51f, 0.68f, 0.23f, 0.11f, "Tab →", "", HidKeys.TAB, MOD_CTRL),
            key("b-tabprev", 0.76f, 0.68f, 0.22f, 0.11f, "Tab ←", "", HidKeys.TAB, (MOD_CTRL.toInt() or MOD_SHIFT.toInt()).toByte()),

            key("b-pgup", 0.02f, 0.81f, 0.23f, 0.12f, "PgUp", "", HidKeys.PAGE_UP),
            key("b-pgdn", 0.27f, 0.81f, 0.23f, 0.12f, "PgDn", "", HidKeys.PAGE_DOWN),
            key("b-homep", 0.52f, 0.81f, 0.23f, 0.12f, "Top", "", HidKeys.HOME),
            key("b-endp", 0.77f, 0.81f, 0.21f, 0.12f, "Bottom", "", HidKeys.END)
        )
    )

    // ------------------------------------------------------------------
    // Pack 3 — Window manager
    // ------------------------------------------------------------------
    /** Snap left/right, maximise, minimise, switch windows, task view, lock. */
    internal fun windowManager() = LayoutSpec(
        name = "Window Pack",
        widgets = listOf(
            pad("w-pad", 0.02f, 0.02f, 0.79f, 0.36f, "Trackpad"),
            strip("w-scroll", 0.83f, 0.02f, 0.15f, 0.36f, "Scroll"),

            key("w-left", 0.02f, 0.40f, 0.23f, 0.12f, "Snap ◧", "", HidKeys.ARROW_LEFT, MOD_GUI),
            key("w-right", 0.27f, 0.40f, 0.23f, 0.12f, "Snap ◨", "", HidKeys.ARROW_RIGHT, MOD_GUI),
            key("w-up", 0.52f, 0.40f, 0.23f, 0.12f, "Maximise", "", HidKeys.ARROW_UP, MOD_GUI),
            key("w-down", 0.77f, 0.40f, 0.21f, 0.12f, "Minimise", "", HidKeys.ARROW_DOWN, MOD_GUI),

            key("w-switch", 0.02f, 0.53f, 0.32f, 0.11f, "Switch", "", HidKeys.TAB, MOD_ALT),
            key("w-task", 0.36f, 0.53f, 0.32f, 0.11f, "Task view", "", HidKeys.TAB, MOD_GUI),
            key("w-close", 0.70f, 0.53f, 0.28f, 0.11f, "Close", "✕", HidKeys.F4, MOD_ALT, BG_DANGER.toLong()),

            key("w-lock", 0.02f, 0.66f, 0.32f, 0.11f, "Lock PC", "🔒", HidKeys.L, MOD_GUI),
            key("w-desktop", 0.36f, 0.66f, 0.32f, 0.11f, "Desktop", "", HidKeys.D, MOD_GUI),
            key("w-explorer", 0.70f, 0.66f, 0.28f, 0.11f, "Files", "📁", HidKeys.E, MOD_GUI),

            media("w-vdn", 0.02f, 0.79f, 0.23f, 0.12f, "Vol −", "🔉", HidConsumer.VOLUME_DOWN),
            media("w-mute", 0.27f, 0.79f, 0.23f, 0.12f, "Mute", "🔇", HidConsumer.MUTE),
            media("w-vup", 0.52f, 0.79f, 0.23f, 0.12f, "Vol +", "🔊", HidConsumer.VOLUME_UP),
            media("w-play", 0.77f, 0.79f, 0.21f, 0.12f, "Play", "⏯", HidConsumer.PLAY_PAUSE, BG_ACCENT.toLong())
        )
    )

    // ------------------------------------------------------------------
    // Pack 4 — Present / meet
    // ------------------------------------------------------------------
    /** For driving a projector or a call from the phone, screen mostly off. */
    internal fun presentAndMeet() = LayoutSpec(
        name = "Present Pack",
        widgets = listOf(
            key("p-next", 0.02f, 0.03f, 0.46f, 0.16f, "Next slide", "▶", HidKeys.ARROW_RIGHT, bg = BG_ACCENT.toLong()),
            key("p-prev", 0.52f, 0.03f, 0.46f, 0.16f, "Prev slide", "◀", HidKeys.ARROW_LEFT),

            key("p-start", 0.02f, 0.21f, 0.46f, 0.13f, "Start show", "", HidKeys.F5),
            key("p-black", 0.52f, 0.21f, 0.46f, 0.13f, "Black screen", "", HidKeys.B),
            key("p-end", 0.02f, 0.36f, 0.46f, 0.13f, "End show", "Esc", HidKeys.ESCAPE),
            key("p-pointer", 0.52f, 0.36f, 0.46f, 0.13f, "Pointer", "◉", HidKeys.C, MOD_CTRL),

            media("p-micmute", 0.02f, 0.51f, 0.46f, 0.13f, "Mic / Play", "⏯", HidConsumer.PLAY_PAUSE),
            media("p-mute", 0.52f, 0.51f, 0.46f, 0.13f, "Mute", "🔇", HidConsumer.MUTE),

            text("p-hi", 0.02f, 0.66f, 0.31f, 0.13f, "\"Hi all\"", "Hi everyone, thanks for joining."),
            text("p-q", 0.35f, 0.66f, 0.31f, 0.13f, "\"Question?\"", "Any questions so far?"),
            text("p-brb", 0.68f, 0.66f, 0.30f, 0.13f, "\"One sec\"", "One moment please."),

            media("p-vdn", 0.02f, 0.81f, 0.31f, 0.14f, "Vol −", "🔉", HidConsumer.VOLUME_DOWN),
            media("p-vup", 0.35f, 0.81f, 0.31f, 0.14f, "Vol +", "🔊", HidConsumer.VOLUME_UP),
            key("p-esc2", 0.68f, 0.81f, 0.30f, 0.14f, "Escape", "", HidKeys.ESCAPE, bg = BG_DANGER.toLong())
        )
    )

    // ------------------------------------------------------------------
    // Pack 5 — Media studio (playback transport for music/video work)
    // ------------------------------------------------------------------
    internal fun mediaStudio() = LayoutSpec(
        name = "Media Studio",
        widgets = listOf(
            key("s-rewind", 0.02f, 0.03f, 0.30f, 0.15f, "◀◀ 10s", "", HidKeys.ARROW_LEFT, MOD_SHIFT),
            media("s-prev", 0.35f, 0.03f, 0.30f, 0.15f, "Prev", "⏮", HidConsumer.PREV_TRACK),
            key("s-fwd", 0.68f, 0.03f, 0.30f, 0.15f, "10s ▶▶", "", HidKeys.ARROW_RIGHT, MOD_SHIFT),

            media("s-play", 0.02f, 0.20f, 0.46f, 0.18f, "Play / Pause", "⏯", HidConsumer.PLAY_PAUSE, BG_ACCENT.toLong()),
            media("s-stop", 0.52f, 0.20f, 0.46f, 0.18f, "Stop", "⏹", HidConsumer.STOP),

            media("s-mute", 0.02f, 0.40f, 0.23f, 0.13f, "Mute", "🔇", HidConsumer.MUTE),
            media("s-vdn", 0.27f, 0.40f, 0.23f, 0.13f, "Vol −", "🔉", HidConsumer.VOLUME_DOWN),
            media("s-vup", 0.52f, 0.40f, 0.23f, 0.13f, "Vol +", "🔊", HidConsumer.VOLUME_UP),
            media("s-next", 0.77f, 0.40f, 0.21f, 0.13f, "Next", "⏭", HidConsumer.NEXT_TRACK),

            strip("s-scroll", 0.02f, 0.55f, 0.15f, 0.40f, "Seek"),
            pad("s-pad", 0.19f, 0.55f, 0.79f, 0.40f, "Trackpad")
        )
    )
}
