package com.aeropad.remote.domain

import com.aeropad.remote.model.HidModifiers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One shared keyboard modifier state for the whole app.
 *
 * Round 15 — why this exists. There used to be two independent keyboards with
 * two independent ViewModels:
 *
 *  - `KeyboardScreen` (RemoteControlViewModel) had the Ctrl/Shift/Alt/Win
 *    latch chips, and its `keyTap()` folded the latched mask in.
 *  - `FullKeyboardScreen` (FullKeyboardViewModel) is the board that actually
 *    has letters, and it had **no modifier chips at all**. Its `tap()` sent
 *    `HidAction.KeyTap` directly, bypassing the latch entirely, and the
 *    Ctrl/Shift/Alt keys on the board were plain `KeySpec`s whose action was
 *    "tap the modifier" — pressed and released inside ~8 ms, so the host saw
 *    nothing.
 *
 * Net result: Ctrl+, Shift+ and Alt+ simply did not work on the one screen
 * where you would use them.
 *
 * This object is now the single source of truth (a Hilt singleton), so both
 * keyboards agree on what is latched, and every key press — from either screen,
 * from a macro, from a widget — folds the same mask in.
 *
 * Behaviour is unchanged from the old F3 design: tap latches for the next
 * keystroke, double-tap locks, tapping a locked modifier releases it.
 *
 * It also tracks Caps Lock, but purely locally: the board decides the case and
 * sends a capital letter as Shift + letter. The host's own caps state cannot be
 * read back over Bluetooth HID, so sending CAPS_LOCK could flip it the wrong way
 * (host caps already on → key off) while the board showed capitals. That is the
 * "letters never change case" bug that was reported, and the reason Caps is no
 * longer forwarded to the host at all.
 */
@Singleton
class KeyboardModifierState @Inject constructor() {

    /** What the UI draws. */
    data class Ui(
        val latched: Int = 0,
        val locked: Int = 0,
        val capsLock: Boolean = false
    ) {
        val mask: Int get() = latched or locked
        fun isOn(mod: Byte): Boolean = (mask and (mod.toInt() and 0xFF)) != 0
        fun isLocked(mod: Byte): Boolean = (locked and (mod.toInt() and 0xFF)) != 0
        val anyActive: Boolean get() = mask != 0 || capsLock

        /** True while the next keystroke arrives shifted: Shift latched or Caps on. */
        val effectivelyShifted: Boolean
            get() = capsLock ||
                (mask and (HidModifiers.LEFT_SHIFT.toInt() or HidModifiers.RIGHT_SHIFT.toInt())) != 0
    }

    private val sticky = StickyModifiers()
    private val _state = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _state.asStateFlow()

    /** Mask to OR into the next keyboard report. */
    val mask: Int get() = sticky.mask
    val capsLock: Boolean get() = _state.value.capsLock

    /**
     * True while the next keystroke should arrive shifted — either a Shift is
     * latched, or Caps Lock is on. Used for letter case in the UI.
     */
    val effectivelyShifted: Boolean
        get() = sticky.mask and (HidModifiers.LEFT_SHIFT.toInt() or HidModifiers.RIGHT_SHIFT.toInt()) != 0 ||
            _state.value.capsLock

    fun toggle(mod: Byte) {
        sticky.toggle(mod)
        publish()
    }

    /** Record a Caps toggle. The caller still sends the real CAPS_LOCK tap. */
    fun toggleCaps() {
        _state.value = _state.value.copy(capsLock = !_state.value.capsLock)
    }

    /** Force Caps off without sending anything (disconnect / new host). */
    fun resetCaps() {
        if (_state.value.capsLock) _state.value = _state.value.copy(capsLock = false)
    }

    /** Called after a keystroke swallowed the one-shot modifiers. */
    fun consume() {
        sticky.consume()
        publish()
    }

    fun clear() {
        sticky.clear()
        publish(capsLock = false)
    }

    private fun publish(capsLock: Boolean = _state.value.capsLock) {
        _state.value = Ui(sticky.latched, sticky.locked, capsLock)
    }

    companion object {
        /** Modifiers the UI offers as chips, in display order. */
        val CHIPS: List<Pair<String, Byte>> = listOf(
            "Ctrl" to HidModifiers.LEFT_CTRL,
            "Shift" to HidModifiers.LEFT_SHIFT,
            "Alt" to HidModifiers.LEFT_ALT,
            "Win" to HidModifiers.LEFT_GUI
        )
    }
}
