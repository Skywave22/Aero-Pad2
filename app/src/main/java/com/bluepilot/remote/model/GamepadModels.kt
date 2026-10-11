package com.bluepilot.remote.model

/**
 * Gamepad models shared between the HID engine and the gamepad UI.
 */

/** Buttons mapped to bits 0..12 of the 16-bit button field (report ID 5). */
enum class GamepadButton(val bit: Int) {
    A(0), B(1), X(2), Y(3),
    L1(4), R1(5), L2(6), R2(7),
    SELECT(8), START(9),
    LEFT_STICK(10), RIGHT_STICK(11),
    HOME(12);

    val mask: Int get() = 1 shl bit
}

/** 8-way hat/dpad values matching the descriptor (0=N .. 7=NW, 8=neutral). */
enum class DpadDirection(val hatValue: Int) {
    UP(0), UP_RIGHT(1), RIGHT(2), DOWN_RIGHT(3),
    DOWN(4), DOWN_LEFT(5), LEFT(6), UP_LEFT(7),
    NONE(8)
}

/**
 * Immutable snapshot of the full gamepad state, sent as one HID report.
 * Axes are -1.0..1.0 floats (0 = centered).
 */
data class GamepadSnapshot(
    val buttons: Int = 0,
    val hat: Int = DpadDirection.NONE.hatValue,
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f
) {
    fun isPressed(button: GamepadButton): Boolean = (buttons and button.mask) != 0
    fun press(button: GamepadButton): GamepadSnapshot = copy(buttons = buttons or button.mask)
    fun release(button: GamepadButton): GamepadSnapshot = copy(buttons = buttons and button.mask.inv())
    fun withDpad(direction: DpadDirection): GamepadSnapshot = copy(hat = direction.hatValue)
}

/** How the gamepad screen translates input into HID output. */
enum class GamepadMappingMode {
    HID_GAMEPAD,        // real gamepad reports (report ID 5)
    KEYBOARD_FALLBACK,  // buttons → keyboard keys (for hosts without gamepad support)
    MOUSE_KEYBOARD      // left stick → mouse, buttons → keys
}

/** Default button→key mapping for KEYBOARD_FALLBACK mode. */
object GamepadKeyboardMapping {
    val DEFAULT: Map<GamepadButton, Byte> = mapOf(
        GamepadButton.A to HidKeys.SPACE,
        GamepadButton.B to HidKeys.C,
        GamepadButton.X to HidKeys.E,
        GamepadButton.Y to HidKeys.F,
        GamepadButton.L1 to HidKeys.Q,
        GamepadButton.R1 to HidKeys.R,
        GamepadButton.SELECT to HidKeys.TAB,
        GamepadButton.START to HidKeys.ENTER,
        GamepadButton.HOME to HidKeys.ESCAPE
    )
}
