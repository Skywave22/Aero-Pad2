package com.bluepilot.remote.hid

import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.MouseButton

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

    /** System report: single byte, bits from [com.bluepilot.remote.model.HidSystem]. */
    fun system(bits: Byte): ByteArray = byteArrayOf(bits)

    /** System release. */
    fun systemRelease(): ByteArray = ByteArray(HidDescriptors.SIZE_SYSTEM)

    /**
     * Gamepad report layout: btnLo, btnHi, hat, lx, ly, rx, ry.
     * Axes arrive as -1.0..1.0 floats and are mapped to unsigned 0..255
     * centered at 128 (matches descriptor Logical 0..255).
     * Hat: 0 = released/neutral is encoded as 0x08? No — we use 0=N..7=NW and
     * 8 = neutral per our descriptor (Logical Max 8).
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
            axisToByte(snapshot.rightY)
        )
    }

    /** Neutral gamepad report (no buttons, hat neutral=8, sticks centered). */
    fun gamepadNeutral(): ByteArray = gamepad(GamepadSnapshot())

    /** Map -1.0..1.0 to 0..255 with 128 center. NaN is treated as center (defensive). */
    internal fun axisToByte(value: Float): Byte {
        val safe = if (value.isNaN()) 0f else value.coerceIn(-1f, 1f)
        val scaled = ((safe + 1f) / 2f * 255f).toInt().coerceIn(0, 255)
        return scaled.toByte()
    }
}
