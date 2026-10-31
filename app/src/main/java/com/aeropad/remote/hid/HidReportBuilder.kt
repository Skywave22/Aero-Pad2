package com.aeropad.remote.hid

import com.aeropad.remote.model.GamepadSnapshot
import com.aeropad.remote.model.MouseButton

/**
 * Pure functions that build HID report payloads.
 *
 * No Android dependencies — 100% unit-testable on the JVM.
 * Every byte layout here MUST match [HidDescriptors]; the unit tests in
 * `HidReportBuilderTest` lock the contract.
 */
object HidReportBuilder {

    /**
     * Keyboard report layout: modifiers, reserved 0, then key1..key6.
     * Up to 6 simultaneous keys (6-key rollover). Extra keys are ignored
     * (defensive: never throws, never truncates below 6).
     */
    fun keyboard(modifiers: Byte = 0, keys: List<Byte> = emptyList()): ByteArray {
        val report = ByteArray(HidDescriptors.SIZE_KEYBOARD)
        report[0] = modifiers
        // report[1] is the reserved byte, always 0.
        keys.take(6).forEachIndexed { index, key -> report[2 + index] = key }
        return report
    }

    /** All-zero keyboard report — releases every key and modifier. */
    fun keyboardRelease(): ByteArray = ByteArray(HidDescriptors.SIZE_KEYBOARD)

    /**
     * Mouse report layout: buttons, dx, dy, wheel.
     * dx/dy/wheel are clamped to -127..127 to stay inside the declared logical range.
     */
    fun mouse(buttons: Byte = 0, dx: Int = 0, dy: Int = 0, wheel: Int = 0): ByteArray {
        return byteArrayOf(
            buttons,
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
        )
    }

    /** Mouse report with a set of pressed buttons and no movement. */
    fun mouseButtons(pressed: Set<MouseButton>): ByteArray {
        var mask = 0
        pressed.forEach { mask = mask or it.mask.toInt() }
        return mouse(buttons = mask.toByte())
    }

    /**
     * Consumer report: 16-bit usage, little-endian. Usage is clamped to the
     * descriptor's declared 0..0x03FF range.
     */
    fun consumer(usage: Int): ByteArray {
        val clamped = usage.coerceIn(0, 0x03FF)
        return byteArrayOf(
            (clamped and 0xFF).toByte(),
            ((clamped shr 8) and 0xFF).toByte()
        )
    }

    /** Consumer release (usage 0). */
    fun consumerRelease(): ByteArray = ByteArray(HidDescriptors.SIZE_CONSUMER)

    /** System report: single byte, bits from [com.aeropad.remote.model.HidSystem]. */
    fun system(bits: Byte): ByteArray = byteArrayOf(bits)

    /** System release. */
    fun systemRelease(): ByteArray = ByteArray(HidDescriptors.SIZE_SYSTEM)

    /**
     * Touch screen (digitizer) — single absolute contact, Report ID 6 payload.
     * [x]/[y] are 0..32767 (clamped); [contact] drives both Tip Switch and
     * In Range so the host treats it as a full finger touch/release.
     */
    fun touch(contact: Boolean, x: Int, y: Int): ByteArray {
        val cx = x.coerceIn(0, 32767)
        val cy = y.coerceIn(0, 32767)
        return byteArrayOf(
            if (contact) 0x03 else 0x00,      // bit0 tip-switch, bit1 in-range
            (cx and 0xFF).toByte(), (cx shr 8 and 0xFF).toByte(),
            (cy and 0xFF).toByte(), (cy shr 8 and 0xFF).toByte(),
        )
    }

    /**
     * Gamepad report layout (9 bytes, matches [HidDescriptors.GAMEPAD]):
     * btnLo, btnHi, hat, lx, ly, rx, ry, lt, rt.
     *
     * Axes arrive as -1.0..1.0 floats and map to unsigned 0..255 centered at
     * 128 (the descriptor declares Logical 0..255). Triggers are 0..1 → 0..255.
     * Hat uses 0..7 for the eight directions (0 = north, clockwise) and 8 for
     * neutral, which is why the descriptor declares Logical Max 8.
     *
     * The 7-byte legacy report (no triggers) is
     * [com.aeropad.remote.hid.ReportPool.gamepadInto]`(extended = false)`.
     */
    fun gamepad(snapshot: GamepadSnapshot): ByteArray {
        val buttons = snapshot.buttons and 0xFFFF
        return byteArrayOf(
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte(),
            snapshot.hat.coerceIn(0, 8).toByte(),
            axisToByte(snapshot.leftX),
            axisToByte(snapshot.leftY),
            axisToByte(snapshot.rightX),
            axisToByte(snapshot.rightY),
            triggerToByte(snapshot.leftTrigger),
            triggerToByte(snapshot.rightTrigger)
        )
    }

    /** Neutral gamepad report (no buttons, hat neutral=8, sticks centered). */
    fun gamepadNeutral(): ByteArray = gamepad(GamepadSnapshot())

    /** 0..1 trigger pressure → unsigned axis byte (round 7 Pro triggers). */
    fun triggerToByte(t: Float): Byte =
        (t.coerceIn(0f, 1f) * 255f).toInt().toByte()

    /** Map -1.0..1.0 to 0..255 with 128 center. NaN is treated as center (defensive). */
    internal fun axisToByte(value: Float): Byte {
        val safe = if (value.isNaN()) 0f else value.coerceIn(-1f, 1f)
        val scaled = ((safe + 1f) / 2f * 255f).toInt().coerceIn(0, 255)
        return scaled.toByte()
    }
}

/**
 * Tracks which mouse buttons are currently held down across HID reports (C1 / C2 / M7 / A1).
 *
 * A HID mouse report is a complete snapshot of currently held buttons, not a delta.
 * Keeping the active button bitmask here ensures that MouseMove, MouseScroll,
 * and idle keep-alives never zero out a held button (such as Drag Lock or
 * holding L/M/R while moving).
 */
class MouseButtonTracker {
    var mask: Int = 0
        private set

    val currentByte: Byte get() = (mask and 0xFF).toByte()
    val hasHeldButtons: Boolean get() = mask != 0

    fun onDown(button: MouseButton): Byte {
        mask = mask or (button.mask.toInt() and 0xFF)
        return currentByte
    }

    fun onUp(button: MouseButton): Byte {
        mask = mask and (button.mask.toInt() and 0xFF).inv()
        return currentByte
    }

    fun withTransientPress(button: MouseButton): Byte =
        ((mask or (button.mask.toInt() and 0xFF)) and 0xFF).toByte()

    fun clear() {
        mask = 0
    }
}
