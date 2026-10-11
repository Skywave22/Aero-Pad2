package com.bluepilot.remote.model.keyboard

import com.bluepilot.remote.model.HidConsumer
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import kotlinx.serialization.Serializable

/**
 * SECTION: Full Keyboard + individual key customization.
 *
 * Every key is DATA: label, HID binding (keycode + modifiers — so a single
 * key can be a combo like Ctrl+C), width weight, optional color override,
 * and an optional secondary (long-press) binding. The whole board is a
 * serializable [KeyboardLayoutSpec]; user edits persist as JSON.
 */
@Serializable
@androidx.compose.runtime.Immutable
data class KeySpec(
    val id: String,
    val label: String,
    val keyCode: Byte,
    val modifiers: Byte = 0,
    /** Row width weight: 1.0 = standard key. Resizable per key. */
    val widthWeight: Float = 1f,
    /** ARGB override; null = themed default. */
    val colorArgb: Long? = null,
    /** Optional long-press binding (secondary function). */
    val secondaryKeyCode: Byte? = null,
    val secondaryModifiers: Byte = 0,
    val secondaryLabel: String = "",
    /** V2 MATRIX 7 — when set, tapping types this string instead of the
     *  keycode (enables multi-key commands like Vim ":wq"). */
    val typeText: String? = null,
    /** V2 MATRIX 7 b2 — when set, tapping sends this consumer-page usage
     *  (media keys: play/pause, volume…) via the media HID report instead
     *  of the keyboard report. Highest priority binding. */
    val consumerUsage: Int? = null
) {
    companion object {
        const val LABEL_MAX = 10
        const val WEIGHT_MIN = 0.5f
        const val WEIGHT_MAX = 3f
        const val TYPE_TEXT_MAX = 32
    }

    fun sanitized(): KeySpec = copy(
        label = label.take(LABEL_MAX).ifBlank { "?" },
        widthWeight = if (widthWeight.isNaN()) 1f else widthWeight.coerceIn(WEIGHT_MIN, WEIGHT_MAX),
        secondaryLabel = secondaryLabel.take(LABEL_MAX),
        typeText = typeText?.take(TYPE_TEXT_MAX)?.ifEmpty { null },
        consumerUsage = consumerUsage?.takeIf { it in 1..0xFFFF }
    )
}

@Serializable
@androidx.compose.runtime.Immutable
data class KeyboardLayoutSpec(
    /** Full board: rows of keys top-to-bottom. */
    val rows: List<List<KeySpec>> = emptyList(),
    /** Which row indices are hidden in COMPACT mode. */
    val compactHiddenRows: List<Int> = emptyList(),
    /** User's favorite custom keys (combo shortcuts row). */
    val favorites: List<KeySpec> = emptyList(),
    /** V2 MATRIX 7 — FN layer: key-id → alternate binding while FN is
     *  held/latched. Keys without an entry are unchanged. Empty map =
     *  no FN layer (legacy layouts load identically). */
    val fnOverlay: Map<String, KeySpec> = emptyMap()
) {
    companion object {
        const val FAVORITES_MAX = 12
        const val KEYS_PER_ROW_MAX = 20
        const val FN_OVERLAY_MAX = 40
    }

    fun sanitized(): KeyboardLayoutSpec = copy(
        rows = rows.map { row -> row.take(KEYS_PER_ROW_MAX).map { it.sanitized() } },
        favorites = favorites.take(FAVORITES_MAX).map { it.sanitized() },
        fnOverlay = fnOverlay.entries.take(FN_OVERLAY_MAX)
            .associate { it.key to it.value.sanitized() }
    )

    fun findKey(id: String): KeySpec? =
        rows.asSequence().flatten().firstOrNull { it.id == id }

    /** V2 MATRIX 7 — pure FN resolution: the key a tap should ACT as. */
    fun effectiveKey(key: KeySpec, fnActive: Boolean): KeySpec =
        if (fnActive) fnOverlay[key.id] ?: key else key
}

/** Factory for the built-in boards. */
object DefaultKeyboards {

    private fun k(
        id: String, label: String, code: Byte, mods: Byte = 0, w: Float = 1f
    ) = KeySpec(id = id, label = label, keyCode = code, modifiers = mods, widthWeight = w)

    /**
     * Full PC board: F-row, number row, QWERTY, modifiers, nav cluster + arrows.
     * Row indices 0 (F-row), 6 (nav) and 7 (arrows) hide in compact mode.
     */
    fun fullQwerty(): KeyboardLayoutSpec = KeyboardLayoutSpec(
        rows = listOf(
            // 0: Esc + F1-F12
            listOf(
                k("esc", "Esc", HidKeys.ESCAPE, w = 1.4f),
                k("f1", "F1", HidKeys.F1), k("f2", "F2", HidKeys.F2),
                k("f3", "F3", HidKeys.F3), k("f4", "F4", HidKeys.F4),
                k("f5", "F5", HidKeys.F5), k("f6", "F6", HidKeys.F6),
                k("f7", "F7", HidKeys.F7), k("f8", "F8", HidKeys.F8),
                k("f9", "F9", HidKeys.F9), k("f10", "F10", HidKeys.F10),
                k("f11", "F11", HidKeys.F11), k("f12", "F12", HidKeys.F12)
            ),
            // 1: number row
            listOf(
                k("grave", "`", HidKeys.GRAVE),
                k("1", "1", HidKeys.NUM_1), k("2", "2", HidKeys.NUM_2),
                k("3", "3", HidKeys.NUM_3), k("4", "4", HidKeys.NUM_4),
                k("5", "5", HidKeys.NUM_5), k("6", "6", HidKeys.NUM_6),
                k("7", "7", HidKeys.NUM_7), k("8", "8", HidKeys.NUM_8),
                k("9", "9", HidKeys.NUM_9), k("0", "0", HidKeys.NUM_0),
                k("minus", "-", HidKeys.MINUS), k("equal", "=", HidKeys.EQUAL),
                k("bksp", "⌫", HidKeys.BACKSPACE, w = 1.8f)
            ),
            // 2: QWERTY
            listOf(
                k("tab", "Tab", HidKeys.TAB, w = 1.5f),
                k("q", "Q", HidKeys.Q), k("w", "W", HidKeys.W), k("e", "E", HidKeys.E),
                k("r", "R", HidKeys.R), k("t", "T", HidKeys.T), k("y", "Y", HidKeys.Y),
                k("u", "U", HidKeys.U), k("i", "I", HidKeys.I), k("o", "O", HidKeys.O),
                k("p", "P", HidKeys.P),
                k("lbr", "[", HidKeys.LEFT_BRACKET), k("rbr", "]", HidKeys.RIGHT_BRACKET),
                k("bslash", "\\", HidKeys.BACKSLASH, w = 1.3f)
            ),
            // 3: home row
            listOf(
                k("caps", "Caps", HidKeys.CAPS_LOCK, w = 1.8f),
                k("a", "A", HidKeys.A), k("s", "S", HidKeys.S), k("d", "D", HidKeys.D),
                k("f", "F", HidKeys.F), k("g", "G", HidKeys.G), k("h", "H", HidKeys.H),
                k("j", "J", HidKeys.J), k("k", "K", HidKeys.K), k("l", "L", HidKeys.L),
                k("semi", ";", HidKeys.SEMICOLON), k("quote", "'", HidKeys.QUOTE),
                k("enter", "Enter ⏎", HidKeys.ENTER, w = 2.1f)
            ),
            // 4: shift row
            listOf(
                KeySpec("lshift", "Shift", HidKeys.NONE, HidModifiers.LEFT_SHIFT, widthWeight = 2.3f),
                k("z", "Z", HidKeys.Z), k("x", "X", HidKeys.X), k("c", "C", HidKeys.C),
                k("v", "V", HidKeys.V), k("b", "B", HidKeys.B), k("n", "N", HidKeys.N),
                k("m", "M", HidKeys.M),
                k("comma", ",", HidKeys.COMMA), k("period", ".", HidKeys.PERIOD),
                k("slash", "/", HidKeys.SLASH),
                KeySpec("rshift", "Shift", HidKeys.NONE, HidModifiers.RIGHT_SHIFT, widthWeight = 2.3f)
            ),
            // 5: bottom modifiers
            listOf(
                KeySpec("lctrl", "Ctrl", HidKeys.NONE, HidModifiers.LEFT_CTRL, widthWeight = 1.5f),
                KeySpec("lgui", "Win", HidKeys.NONE, HidModifiers.LEFT_GUI, widthWeight = 1.3f),
                KeySpec("lalt", "Alt", HidKeys.NONE, HidModifiers.LEFT_ALT, widthWeight = 1.3f),
                k("space", "Space", HidKeys.SPACE, w = 6f),
                KeySpec("ralt", "Alt", HidKeys.NONE, HidModifiers.RIGHT_ALT, widthWeight = 1.3f),
                k("menu", "☰", HidKeys.APPLICATION),
                KeySpec("rctrl", "Ctrl", HidKeys.NONE, HidModifiers.RIGHT_CTRL, widthWeight = 1.5f)
            ),
            // 6: nav cluster
            listOf(
                k("prtsc", "PrtSc", HidKeys.PRINT_SCREEN),
                k("ins", "Ins", HidKeys.INSERT), k("del", "Del", HidKeys.DELETE),
                k("home", "Home", HidKeys.HOME), k("end", "End", HidKeys.END),
                k("pgup", "PgUp", HidKeys.PAGE_UP), k("pgdn", "PgDn", HidKeys.PAGE_DOWN)
            ),
            // 7: arrows
            listOf(
                k("left", "←", HidKeys.ARROW_LEFT, w = 2f),
                k("up", "↑", HidKeys.ARROW_UP, w = 2f),
                k("down", "↓", HidKeys.ARROW_DOWN, w = 2f),
                k("right", "→", HidKeys.ARROW_RIGHT, w = 2f)
            )
        ),
        compactHiddenRows = listOf(0, 6, 7),
        favorites = listOf(
            KeySpec("fav-copy", "Copy", HidKeys.C, HidModifiers.LEFT_CTRL),
            KeySpec("fav-paste", "Paste", HidKeys.V, HidModifiers.LEFT_CTRL),
            KeySpec("fav-undo", "Undo", HidKeys.Z, HidModifiers.LEFT_CTRL)
        ),
        fnOverlay = defaultFnOverlay()
    )

    /** V2 M7 b2 — media key helper for the FN overlay. */
    private fun m(id: String, label: String, usage: Int) =
        KeySpec(id = id, label = label, keyCode = HidKeys.NONE, consumerUsage = usage)

    /**
     * V2 MATRIX 7 — default FN layer, laptop-style:
     * F1–F8 → nav/editing rebinds over the keyboard report;
     * F9–F12 → REAL media keys (consumer usages routed through the media
     * HID report via KeySpec.consumerUsage — M7 b2 closed the earlier gap).
     * Arrows → PgUp/PgDn/Home/End; Backspace → Delete.
     */
    fun defaultFnOverlay(): Map<String, KeySpec> = mapOf(
        "f1" to k("fn-f1", "Home", HidKeys.HOME),
        "f2" to k("fn-f2", "End", HidKeys.END),
        "f3" to k("fn-f3", "PgUp", HidKeys.PAGE_UP),
        "f4" to k("fn-f4", "PgDn", HidKeys.PAGE_DOWN),
        "f5" to k("fn-f5", "Ins", HidKeys.INSERT),
        "f6" to k("fn-f6", "Del", HidKeys.DELETE),
        "f7" to k("fn-f7", "PrtSc", HidKeys.PRINT_SCREEN),
        "f8" to k("fn-f8", "Menu", HidKeys.APPLICATION),
        // V2 M7 b2 — laptop-style media on the high F-row.
        "f9" to m("fn-f9", "⏯", HidConsumer.PLAY_PAUSE),
        "f10" to m("fn-f10", "🔇", HidConsumer.MUTE),
        "f11" to m("fn-f11", "Vol−", HidConsumer.VOLUME_DOWN),
        "f12" to m("fn-f12", "Vol+", HidConsumer.VOLUME_UP),
        "up" to k("fn-up", "PgUp", HidKeys.PAGE_UP, w = 2f),
        "down" to k("fn-down", "PgDn", HidKeys.PAGE_DOWN, w = 2f),
        "left" to k("fn-left", "Home", HidKeys.HOME, w = 2f),
        "right" to k("fn-right", "End", HidKeys.END, w = 2f),
        "bksp" to k("fn-bksp", "Del ⌦", HidKeys.DELETE, w = 1.8f)
    )
}

/**
 * V2 MATRIX 7 — productivity shortcut packs: curated favorite-row sets for
 * Vim, VS Code, IntelliJ and terminal work. Pure data (unit-tested: ids
 * unique, labels non-blank, sizes within FAVORITES_MAX).
 */
object ShortcutPacks {

    data class Pack(val id: String, val name: String, val keys: List<KeySpec>)

    private fun s(id: String, label: String, code: Byte, mods: Byte = 0, text: String? = null) =
        KeySpec(id = id, label = label, keyCode = code, modifiers = mods, typeText = text)

    private val CTRL = HidModifiers.LEFT_CTRL
    private val SHIFT = HidModifiers.LEFT_SHIFT
    private val ALT = HidModifiers.LEFT_ALT
    private fun mods(vararg m: Byte): Byte {
        var acc = 0
        m.forEach { acc = acc or it.toInt() }
        return acc.toByte()
    }

    val VIM = Pack(
        id = "vim", name = "Vim",
        keys = listOf(
            s("vim-esc", "Esc", HidKeys.ESCAPE),
            s("vim-wq", ":wq", HidKeys.NONE, text = ":wq\n"),
            s("vim-q", ":q!", HidKeys.NONE, text = ":q!\n"),
            s("vim-w", ":w", HidKeys.NONE, text = ":w\n"),
            s("vim-dd", "dd", HidKeys.NONE, text = "dd"),
            s("vim-yy", "yy", HidKeys.NONE, text = "yy"),
            s("vim-p", "p", HidKeys.P),
            s("vim-u", "u", HidKeys.U),
            s("vim-gg", "gg", HidKeys.NONE, text = "gg"),
            s("vim-G", "G", HidKeys.G, SHIFT),
            s("vim-search", "/", HidKeys.SLASH),
            s("vim-visual", "v", HidKeys.V)
        )
    )

    val VSCODE = Pack(
        id = "vscode", name = "VS Code",
        keys = listOf(
            s("vsc-palette", "Cmd Pal", HidKeys.P, mods(CTRL, SHIFT)),
            s("vsc-quick", "Go File", HidKeys.P, CTRL),
            s("vsc-term", "Terminal", HidKeys.GRAVE, CTRL),
            s("vsc-comment", "Comment", HidKeys.SLASH, CTRL),
            s("vsc-format", "Format", HidKeys.F, mods(CTRL, SHIFT, ALT)),
            s("vsc-rename", "Rename", HidKeys.F2),
            s("vsc-def", "Go Def", HidKeys.F12),
            s("vsc-find", "Find All", HidKeys.F, mods(CTRL, SHIFT)),
            s("vsc-sidebar", "Sidebar", HidKeys.B, CTRL),
            s("vsc-split", "Split", HidKeys.BACKSLASH, CTRL),
            s("vsc-dup", "Dup Line", HidKeys.ARROW_DOWN, mods(SHIFT, ALT)),
            s("vsc-dell", "Del Line", HidKeys.K, mods(CTRL, SHIFT))
        )
    )

    val INTELLIJ = Pack(
        id = "intellij", name = "IntelliJ",
        keys = listOf(
            // Search-everywhere is double-Shift (a timing gesture a single
            // KeyTap can't express) — Ctrl+N class search is the honest swap.
            s("ij-class", "Class", HidKeys.N, CTRL),
            s("ij-action", "Action", HidKeys.A, mods(CTRL, SHIFT)),
            s("ij-run", "Run", HidKeys.F10, SHIFT),
            s("ij-debug", "Debug", HidKeys.F9, SHIFT),
            s("ij-reformat", "Format", HidKeys.L, mods(CTRL, ALT)),
            s("ij-rename", "Rename", HidKeys.F6, SHIFT),
            s("ij-usages", "Usages", HidKeys.F7, ALT),
            s("ij-def", "Go Def", HidKeys.B, CTRL),
            s("ij-comment", "Comment", HidKeys.SLASH, CTRL),
            s("ij-optimize", "Opt Imp", HidKeys.O, mods(CTRL, ALT)),
            s("ij-recent", "Recent", HidKeys.E, CTRL),
            s("ij-extract", "Extract", HidKeys.V, mods(CTRL, ALT))
        )
    )

    val TERMINAL = Pack(
        id = "terminal", name = "Terminal",
        keys = listOf(
            s("term-c", "Ctrl+C", HidKeys.C, CTRL),
            s("term-d", "Ctrl+D", HidKeys.D, CTRL),
            s("term-z", "Ctrl+Z", HidKeys.Z, CTRL),
            s("term-l", "Clear", HidKeys.L, CTRL),
            s("term-r", "Hist ⌕", HidKeys.R, CTRL),
            s("term-a", "Line ⇤", HidKeys.A, CTRL),
            s("term-e", "Line ⇥", HidKeys.E, CTRL),
            s("term-w", "Del Word", HidKeys.W, CTRL),
            s("term-tab", "Tab", HidKeys.TAB),
            s("term-up", "Prev ↑", HidKeys.ARROW_UP),
            s("term-ls", "ls -la", HidKeys.NONE, text = "ls -la\n"),
            s("term-cd", "cd ..", HidKeys.NONE, text = "cd ..\n")
        )
    )

    val ALL: List<Pack> = listOf(VIM, VSCODE, INTELLIJ, TERMINAL)
}
