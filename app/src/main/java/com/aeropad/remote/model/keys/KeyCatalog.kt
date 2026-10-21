package com.aeropad.remote.model.keys

import com.aeropad.remote.model.HidKeys

/**
 * Every key the user can drop onto a layout, grouped so the picker can show
 * five readable pages instead of one wall of buttons.
 *
 * Pure data (no Android, no Compose) so the catalog can be unit tested and is
 * shared by the My Layout key sheet, the combo builder and the editor.
 */
data class KeyDef(
    /** What the key looks like on screen. */
    val label: String,
    /** HID usage code sent to the host. */
    val code: Byte,
    /** Long-form name used inside combo names ("Ctrl+Shift+Esc"). */
    val name: String = label
)

enum class KeyGroup(val title: String) {
    LETTERS("ABC"),
    DIGITS("123"),
    FUNCTION("F"),
    NAVIGATION("Nav"),
    SYMBOLS("#+=")
}

object KeyCatalog {

    private fun k(label: String, code: Byte, name: String = label) = KeyDef(label, code, name)

    val letters: List<KeyDef> = listOf(
        k("A", HidKeys.A), k("B", HidKeys.B), k("C", HidKeys.C), k("D", HidKeys.D),
        k("E", HidKeys.E), k("F", HidKeys.F), k("G", HidKeys.G), k("H", HidKeys.H),
        k("I", HidKeys.I), k("J", HidKeys.J), k("K", HidKeys.K), k("L", HidKeys.L),
        k("M", HidKeys.M), k("N", HidKeys.N), k("O", HidKeys.O), k("P", HidKeys.P),
        k("Q", HidKeys.Q), k("R", HidKeys.R), k("S", HidKeys.S), k("T", HidKeys.T),
        k("U", HidKeys.U), k("V", HidKeys.V), k("W", HidKeys.W), k("X", HidKeys.X),
        k("Y", HidKeys.Y), k("Z", HidKeys.Z)
    )

    val digits: List<KeyDef> = listOf(
        k("1", HidKeys.NUM_1), k("2", HidKeys.NUM_2), k("3", HidKeys.NUM_3),
        k("4", HidKeys.NUM_4), k("5", HidKeys.NUM_5), k("6", HidKeys.NUM_6),
        k("7", HidKeys.NUM_7), k("8", HidKeys.NUM_8), k("9", HidKeys.NUM_9),
        k("0", HidKeys.NUM_0)
    )

    val functions: List<KeyDef> = listOf(
        k("F1", HidKeys.F1), k("F2", HidKeys.F2), k("F3", HidKeys.F3),
        k("F4", HidKeys.F4), k("F5", HidKeys.F5), k("F6", HidKeys.F6),
        k("F7", HidKeys.F7), k("F8", HidKeys.F8), k("F9", HidKeys.F9),
        k("F10", HidKeys.F10), k("F11", HidKeys.F11), k("F12", HidKeys.F12)
    )

    val navigation: List<KeyDef> = listOf(
        k("Esc", HidKeys.ESCAPE),
        k("Tab", HidKeys.TAB),
        k("⏎", HidKeys.ENTER, "Enter"),
        k("␣", HidKeys.SPACE, "Space"),
        k("⌫", HidKeys.BACKSPACE, "Bksp"),
        k("Del", HidKeys.DELETE, "Del"),
        k("↑", HidKeys.ARROW_UP, "Up"),
        k("↓", HidKeys.ARROW_DOWN, "Down"),
        k("←", HidKeys.ARROW_LEFT, "Left"),
        k("→", HidKeys.ARROW_RIGHT, "Right"),
        k("Home", HidKeys.HOME),
        k("End", HidKeys.END),
        k("PgUp", HidKeys.PAGE_UP, "PgUp"),
        k("PgDn", HidKeys.PAGE_DOWN, "PgDn"),
        k("Ins", HidKeys.INSERT, "Insert"),
        k("Caps", HidKeys.CAPS_LOCK, "Caps")
    )

    val symbols: List<KeyDef> = listOf(
        k("-", HidKeys.MINUS), k("=", HidKeys.EQUAL),
        k("[", HidKeys.LEFT_BRACKET), k("]", HidKeys.RIGHT_BRACKET),
        k("\\", HidKeys.BACKSLASH), k(";", HidKeys.SEMICOLON),
        k("'", HidKeys.QUOTE), k("`", HidKeys.GRAVE),
        k(",", HidKeys.COMMA), k(".", HidKeys.PERIOD), k("/", HidKeys.SLASH)
    )

    fun group(group: KeyGroup): List<KeyDef> = when (group) {
        KeyGroup.LETTERS -> letters
        KeyGroup.DIGITS -> digits
        KeyGroup.FUNCTION -> functions
        KeyGroup.NAVIGATION -> navigation
        KeyGroup.SYMBOLS -> symbols
    }

    /** Flat list, useful for tests and for resolving a saved combo back to a key. */
    fun all(): List<KeyDef> = KeyGroup.entries.flatMap { group(it) }

    fun byCode(code: Byte): KeyDef? = all().firstOrNull { it.code == code }
}
