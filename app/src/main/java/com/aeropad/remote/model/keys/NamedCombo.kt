package com.aeropad.remote.model.keys

import com.aeropad.remote.model.HidModifiers
import kotlinx.serialization.Serializable

/**
 * A user-defined shortcut: a name they chose plus the keys it fires.
 *
 * Example: name "Close window", modifiers Ctrl+Shift, key Esc → the combo shows
 * as "Ctrl+Shift+Esc" everywhere and can be dropped onto any layout as a single
 * button, exactly like one of the built-in shortcuts.
 *
 * Stored as plain ints so old files keep decoding if the bit layout ever moves.
 */
@Serializable
data class NamedCombo(
    val id: String,
    val name: String,
    /** HID usage code of the non-modifier key. */
    val key: Int,
    /** Modifier bitmask (HidModifiers). */
    val mods: Int = 0
) {
    companion object {
        const val NAME_MAX = 28
        const val MAX_COMBOS = 60

        /** Ordered list of modifier bits, so labels are stable. */
        private val MOD_ORDER = listOf(
            HidModifiers.LEFT_CTRL to "Ctrl",
            HidModifiers.LEFT_SHIFT to "Shift",
            HidModifiers.LEFT_ALT to "Alt",
            HidModifiers.LEFT_GUI to "Win"
        )

        /** "Ctrl+Shift+Esc" — built from the real bits, never from user text. */
        fun chord(mods: Int, keyLabel: String): String {
            val parts = MOD_ORDER.filter { (bit, _) -> (mods and bit.toInt()) != 0 }
                .map { it.second }
            return (parts + keyLabel).joinToString("+")
        }
    }

    /** Label for the chip: the user's name when set, otherwise the chord. */
    fun displayName(): String {
        val chord = chord(mods, KeyCatalog.byCode(key.toByte())?.name ?: "?")
        return name.ifBlank { chord }
    }

    fun chordString(): String =
        chord(mods, KeyCatalog.byCode(key.toByte())?.name ?: "?")

    /** Keep persisted data sane: trimmed name, no modifier-only combos. */
    fun sanitized(): NamedCombo = copy(
        name = name.trim().take(NAME_MAX),
        key = key and 0xFF,
        mods = mods and 0xFF
    )

    /** A combo with no key would send nothing — refuse it. */
    fun isValid(): Boolean = key and 0xFF != 0
}
