package com.aeropad.remote.hid

/**
 * HID Report Descriptors for AeroPad Remote.
 *
 * One COMBINED descriptor exposes 5 logical devices over a single Bluetooth
 * HID connection, separated by Report IDs:
 *
 *  ID 1 — Keyboard   : 8 modifier bits + 6-key rollover + LED output report
 *  ID 2 — Mouse      : 3 buttons, relative X/Y, scroll wheel
 *  ID 3 — Consumer   : 16-bit media/consumer control (play, volume, …)
 *  ID 4 — System     : power / sleep / wake
 *  ID 5 — Gamepad    : 16 buttons, 8-way hat switch, 4 analog axes
 *
 * Every report layout has a matching builder in [HidReportBuilder] —
 * if you change a descriptor you MUST update the builder and its unit test.
 */
object HidDescriptors {

    const val REPORT_ID_KEYBOARD: Int = 1
    const val REPORT_ID_MOUSE: Int = 2
    const val REPORT_ID_CONSUMER: Int = 3
    const val REPORT_ID_SYSTEM: Int = 4
    const val REPORT_ID_GAMEPAD: Int = 5
    const val REPORT_ID_TOUCH: Int = 6

    /** Payload sizes in bytes (excluding the report ID, which Android passes separately). */
    const val SIZE_KEYBOARD: Int = 8   // [modifiers][reserved][key1..key6]
    const val SIZE_MOUSE: Int = 4      // [buttons][dx][dy][wheel]
    const val SIZE_CONSUMER: Int = 2   // [usage LSB][usage MSB]
    const val SIZE_SYSTEM: Int = 1     // [bit0=power bit1=sleep bit2=wake]
    const val SIZE_GAMEPAD: Int = 9    // [btnLo][btnHi][hat][lx][ly][rx][ry][lt][rt]
    const val SIZE_GAMEPAD_LEGACY: Int = 7  // classic 3.3.0 gamepad (no triggers)
    const val SIZE_TOUCH: Int = 5      // [tipSwitch|inRange bits][xLo][xHi][yLo][yHi] (0..32767)

    /**
     * Keyboard, Report ID 1.
     * Input : 1 byte modifiers (bitmap), 1 byte reserved, 6 bytes keycodes.
     * Output: 5 LED bits + 3 padding (host → device, e.g. CapsLock LED).
     */
    private val KEYBOARD = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x06,                    // Usage (Keyboard)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), //   Report ID 1
        0x05, 0x07,                    //   Usage Page (Key Codes)
        0x19, 0xE0.toByte(),           //   Usage Min (LeftControl 0xE0)
        0x29, 0xE7.toByte(),           //   Usage Max (Right GUI 0xE7)
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x01,                    //   Logical Max 1
        0x75, 0x01,                    //   Report Size 1
        0x95.toByte(), 0x08,           //   Report Count 8  -> modifier bitmap
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0x95.toByte(), 0x01,           //   Report Count 1
        0x75, 0x08,                    //   Report Size 8   -> reserved byte
        0x81.toByte(), 0x01,           //   Input (Const)
        0x95.toByte(), 0x05,           //   Report Count 5  -> LED output
        0x75, 0x01,                    //   Report Size 1
        0x05, 0x08,                    //   Usage Page (LEDs)
        0x19, 0x01,                    //   Usage Min (Num Lock)
        0x29, 0x05,                    //   Usage Max (Kana)
        0x91.toByte(), 0x02,           //   Output (Data, Var, Abs)
        0x95.toByte(), 0x01,           //   Report Count 1
        0x75, 0x03,                    //   Report Size 3   -> LED padding
        0x91.toByte(), 0x03,           //   Output (Const)
        0x95.toByte(), 0x06,           //   Report Count 6  -> 6-key rollover
        0x75, 0x08,                    //   Report Size 8
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x65,                    //   Logical Max 101
        0x05, 0x07,                    //   Usage Page (Key Codes)
        0x19, 0x00,                    //   Usage Min 0
        0x29, 0x65,                    //   Usage Max 101
        0x81.toByte(), 0x00,           //   Input (Data, Array)
        0xC0.toByte()                  // End Collection
    )

    /**
     * Mouse, Report ID 2.
     * Input: 3 button bits + 5 padding, X rel (-127..127), Y rel, wheel (-7..7 declared, byte-range used).
     */
    private val MOUSE = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x02,                    // Usage (Mouse)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_MOUSE.toByte(), //   Report ID 2
        0x09, 0x01,                    //   Usage (Pointer)
        0xA1.toByte(), 0x00,           //   Collection (Physical)
        0x05, 0x09,                    //     Usage Page (Buttons)
        0x19, 0x01,                    //     Usage Min (Button 1)
        0x29, 0x03,                    //     Usage Max (Button 3)
        0x15, 0x00,                    //     Logical Min 0
        0x25, 0x01,                    //     Logical Max 1
        0x95.toByte(), 0x03,           //     Report Count 3
        0x75, 0x01,                    //     Report Size 1  -> L/R/M buttons
        0x81.toByte(), 0x02,           //     Input (Data, Var, Abs)
        0x95.toByte(), 0x01,           //     Report Count 1
        0x75, 0x05,                    //     Report Size 5  -> padding
        0x81.toByte(), 0x03,           //     Input (Const)
        0x05, 0x01,                    //     Usage Page (Generic Desktop)
        0x09, 0x30,                    //     Usage (X)
        0x09, 0x31,                    //     Usage (Y)
        0x15, 0x81.toByte(),           //     Logical Min -127
        0x25, 0x7F,                    //     Logical Max 127
        0x75, 0x08,                    //     Report Size 8
        0x95.toByte(), 0x02,           //     Report Count 2 -> dX, dY
        0x81.toByte(), 0x06,           //     Input (Data, Var, Rel)
        0x09, 0x38,                    //     Usage (Wheel)
        0x15, 0x81.toByte(),           //     Logical Min -127
        0x25, 0x7F,                    //     Logical Max 127
        0x75, 0x08,                    //     Report Size 8
        0x95.toByte(), 0x01,           //     Report Count 1 -> wheel
        0x81.toByte(), 0x06,           //     Input (Data, Var, Rel)
        0xC0.toByte(),                 //   End Collection (Physical)
        0xC0.toByte()                  // End Collection (Application)
    )

    /**
     * Consumer control, Report ID 3.
     * Input: one 16-bit usage value (0x0000..0x03FF), e.g. Play/Pause 0x00CD.
     */
    private val CONSUMER = byteArrayOf(
        0x05, 0x0C,                    // Usage Page (Consumer)
        0x09, 0x01,                    // Usage (Consumer Control)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_CONSUMER.toByte(), //   Report ID 3
        0x15, 0x00,                    //   Logical Min 0
        0x26, 0xFF.toByte(), 0x03,     //   Logical Max 0x03FF
        0x19, 0x00,                    //   Usage Min 0
        0x2A, 0xFF.toByte(), 0x03,     //   Usage Max 0x03FF
        0x75, 0x10,                    //   Report Size 16
        0x95.toByte(), 0x01,           //   Report Count 1
        0x81.toByte(), 0x00,           //   Input (Data, Array)
        0xC0.toByte()                  // End Collection
    )

    /**
     * System control, Report ID 4.
     * Input: 3 bits (power / sleep / wake) + 5 padding.
     */
    private val SYSTEM = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x80.toByte(),           // Usage (System Control)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_SYSTEM.toByte(), //   Report ID 4
        0x19, 0x81.toByte(),           //   Usage Min (System Power Down)
        0x29, 0x83.toByte(),           //   Usage Max (System Wake Up)
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x01,                    //   Logical Max 1
        0x75, 0x01,                    //   Report Size 1
        0x95.toByte(), 0x03,           //   Report Count 3
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0x95.toByte(), 0x01,           //   Report Count 1
        0x75, 0x05,                    //   Report Size 5 -> padding
        0x81.toByte(), 0x03,           //   Input (Const)
        0xC0.toByte()                  // End Collection
    )

    /**
     * Gamepad, Report ID 5.
     * Input: 16 button bits, hat switch (0..8, 0 = neutral encoded as null state),
     * 4 axes (X/Y left stick, Z/Rz right stick) as unsigned 0..255 centered at 128.
     */
    private val GAMEPAD = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x05,                    // Usage (Game Pad)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_GAMEPAD.toByte(), //   Report ID 5
        0x05, 0x09,                    //   Usage Page (Buttons)
        0x19, 0x01,                    //   Usage Min (Button 1)
        0x29, 0x10,                    //   Usage Max (Button 16)
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x01,                    //   Logical Max 1
        0x95.toByte(), 0x10,           //   Report Count 16
        0x75, 0x01,                    //   Report Size 1
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0x05, 0x01,                    //   Usage Page (Generic Desktop)
        0x09, 0x39,                    //   Usage (Hat Switch)
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x08,                    //   Logical Max 8
        0x95.toByte(), 0x01,           //   Report Count 1
        0x75, 0x08,                    //   Report Size 8
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0x05, 0x01,                    //   Usage Page (Generic Desktop)
        0x09, 0x30,                    //   Usage (X)  - left stick X
        0x09, 0x31,                    //   Usage (Y)  - left stick Y
        0x09, 0x32,                    //   Usage (Z)  - right stick X
        0x09, 0x35,                    //   Usage (Rz) - right stick Y
        0x09, 0x33,                    //   Usage (Rx) - left trigger  (round 7)
        0x09, 0x34,                    //   Usage (Ry) - right trigger (round 7)
        0x15, 0x00,                    //   Logical Min 0
        0x26, 0xFF.toByte(), 0x00,     //   Logical Max 255
        0x75, 0x08,                    //   Report Size 8
        0x95.toByte(), 0x06,           //   Report Count 6
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0xC0.toByte()                  // End Collection
    )

    /**
     * Touch screen (HID digitizer), Report ID 6.
     * Single absolute pointer: tip-switch + in-range bits, then X/Y as
     * unsigned 16-bit coordinates in 0..32767. Hosts that support HID
     * touch input (Android tablet hosts, Windows) render it as a real
     * finger touch; hosts without digitizer support simply ignore it.
     */
    private val TOUCH = byteArrayOf(
        0x05, 0x0D,                    // Usage Page (Digitizer)
        0x09, 0x04,                    // Usage (Touch Screen)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_TOUCH.toByte(), //   Report ID 6
        0x09, 0x22,                    //   Usage (Finger)
        0xA1.toByte(), 0x00,           //   Collection (Physical)
        0x09, 0x42,                    //     Usage (Tip Switch)
        0x09, 0x32,                    //     Usage (In Range)
        0x15, 0x00,                    //     Logical Min 0
        0x25, 0x01,                    //     Logical Max 1
        0x75, 0x01,                    //     Report Size 1
        0x95.toByte(), 0x02,           //     Report Count 2 -> switch + in-range
        0x81.toByte(), 0x02,           //     Input (Data, Var, Abs)
        0x95.toByte(), 0x06,           //     Report Count 6
        0x75, 0x01,                    //     Report Size 1 -> padding
        0x81.toByte(), 0x03,           //     Input (Const)
        0x05, 0x01,                    //     Usage Page (Generic Desktop)
        0x09, 0x30,                    //     Usage (X)
        0x09, 0x31,                    //     Usage (Y)
        0x15, 0x00,                    //     Logical Min 0
        0x26, 0xFF.toByte(), 0x7F,     //     Logical Max 32767
        0x75, 0x10,                    //     Report Size 16
        0x95.toByte(), 0x02,           //     Report Count 2 -> X, Y absolute
        0x81.toByte(), 0x02,           //     Input (Data, Var, Abs)
        0xC0.toByte(),                 //   End Collection (Physical)
        0xC0.toByte()                  // End Collection (Application)
    )

    /**
     * Round 9 — classic 3.3.0 gamepad collection (4 axes, 7-byte report).
     * This is the byte-exact collection the known-good v3.3.0 build shipped.
     */
    private val GAMEPAD_LEGACY = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x05,                    // Usage (Game Pad)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID_GAMEPAD.toByte(), //   Report ID 5
        0x05, 0x09,                    //   Usage Page (Buttons)
        0x19, 0x01,                    //   Usage Min (Button 1)
        0x29, 0x10,                    //   Usage Max (Button 16)
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x01,                    //   Logical Max 1
        0x95.toByte(), 0x10,           //   Report Count 16
        0x75, 0x01,                    //   Report Size 1
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0x05, 0x01,                    //   Usage Page (Generic Desktop)
        0x09, 0x39,                    //   Usage (Hat Switch)
        0x15, 0x00,                    //   Logical Min 0
        0x25, 0x08,                    //   Logical Max 8
        0x95.toByte(), 0x01,           //   Report Count 1
        0x75, 0x08,                    //   Report Size 8
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0x05, 0x01,                    //   Usage Page (Generic Desktop)
        0x09, 0x30,                    //   Usage (X)  - left stick X
        0x09, 0x31,                    //   Usage (Y)  - left stick Y
        0x09, 0x32,                    //   Usage (Z)  - right stick X
        0x09, 0x35,                    //   Usage (Rz) - right stick Y
        0x15, 0x00,                    //   Logical Min 0
        0x26, 0xFF.toByte(), 0x00,     //   Logical Max 255
        0x75, 0x08,                    //   Report Size 8
        0x95.toByte(), 0x04,           //   Report Count 4
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)
        0xC0.toByte()                  // End Collection
    )

    /**
     * Round 9 — LEGACY descriptor: byte-identical device set to the stable
     * v3.3.0 build (5 collections, 7-byte gamepad, no digitizer). Registered
     * by default for maximum host compatibility.
     */
    val LEGACY: ByteArray = KEYBOARD + MOUSE + CONSUMER + SYSTEM + GAMEPAD_LEGACY

    /** The single descriptor registered with Android — all six devices combined. */
    val COMBINED: ByteArray = KEYBOARD + MOUSE + CONSUMER + SYSTEM + GAMEPAD + TOUCH
}
