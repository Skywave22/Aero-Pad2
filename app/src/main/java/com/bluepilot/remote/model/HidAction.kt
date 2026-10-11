package com.bluepilot.remote.model

/**
 * Every input the app can send, as one sealed hierarchy.
 *
 * UI layers emit HidActions; the HidEngine translates them into reports.
 * This is also the vocabulary of the Module 7 macro system — a macro is
 * simply a stored List<HidAction>.
 */
sealed interface HidAction {

    // ----- Keyboard -----
    /** Press + release a key (with optional modifiers) in one action. */
    data class KeyTap(val key: Byte, val modifiers: Byte = 0) : HidAction
    /** Hold a key down (must be balanced by [KeyRelease]). */
    data class KeyDown(val key: Byte, val modifiers: Byte = 0) : HidAction
    /** Release all keys and modifiers. */
    data object KeyRelease : HidAction
    /** Type a full text string (translated char-by-char). */
    data class TypeText(val text: String) : HidAction

    // ----- Mouse -----
    data class MouseMove(val dx: Int, val dy: Int) : HidAction
    data class MouseClick(val button: MouseButton) : HidAction
    data class MouseDoubleClick(val button: MouseButton) : HidAction
    data class MouseDown(val button: MouseButton) : HidAction
    data class MouseUp(val button: MouseButton) : HidAction
    data class MouseScroll(val amount: Int) : HidAction
    /** Move while holding buttons (drag). */
    data class MouseDrag(val dx: Int, val dy: Int, val button: MouseButton) : HidAction

    // ----- Consumer / media -----
    /** Tap a consumer usage (play/pause, volume…). */
    data class MediaTap(val usage: Int) : HidAction

    // ----- System -----
    data class SystemTap(val bits: Byte) : HidAction

    // ----- Gamepad -----
    data class GamepadUpdate(val snapshot: GamepadSnapshot) : HidAction
}
