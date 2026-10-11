package com.bluepilot.remote.model

/**
 * USB HID usage tables used across the app.
 * Reference: USB HID Usage Tables 1.12, chapter 10 (Keyboard/Keypad page 0x07)
 * and chapter 15 (Consumer page 0x0C).
 */

/** Keyboard modifier bitmap (byte 0 of the keyboard report). */
object HidModifiers {
    const val NONE: Byte = 0x00
    const val LEFT_CTRL: Byte = 0x01
    const val LEFT_SHIFT: Byte = 0x02
    const val LEFT_ALT: Byte = 0x04
    const val LEFT_GUI: Byte = 0x08          // Windows / Cmd key
    const val RIGHT_CTRL: Byte = 0x10
    const val RIGHT_SHIFT: Byte = 0x20
    const val RIGHT_ALT: Byte = 0x40
    const val RIGHT_GUI: Byte = 0x80.toByte()
}

/** Keyboard/Keypad usage IDs (page 0x07). */
object HidKeys {
    const val NONE: Byte = 0x00

    // Letters
    const val A: Byte = 0x04; const val B: Byte = 0x05; const val C: Byte = 0x06
    const val D: Byte = 0x07; const val E: Byte = 0x08; const val F: Byte = 0x09
    const val G: Byte = 0x0A; const val H: Byte = 0x0B; const val I: Byte = 0x0C
    const val J: Byte = 0x0D; const val K: Byte = 0x0E; const val L: Byte = 0x0F
    const val M: Byte = 0x10; const val N: Byte = 0x11; const val O: Byte = 0x12
    const val P: Byte = 0x13; const val Q: Byte = 0x14; const val R: Byte = 0x15
    const val S: Byte = 0x16; const val T: Byte = 0x17; const val U: Byte = 0x18
    const val V: Byte = 0x19; const val W: Byte = 0x1A; const val X: Byte = 0x1B
    const val Y: Byte = 0x1C; const val Z: Byte = 0x1D

    // Number row
    const val NUM_1: Byte = 0x1E; const val NUM_2: Byte = 0x1F; const val NUM_3: Byte = 0x20
    const val NUM_4: Byte = 0x21; const val NUM_5: Byte = 0x22; const val NUM_6: Byte = 0x23
    const val NUM_7: Byte = 0x24; const val NUM_8: Byte = 0x25; const val NUM_9: Byte = 0x26
    const val NUM_0: Byte = 0x27

    // Control keys
    const val ENTER: Byte = 0x28
    const val ESCAPE: Byte = 0x29
    const val BACKSPACE: Byte = 0x2A
    const val TAB: Byte = 0x2B
    const val SPACE: Byte = 0x2C

    // Punctuation
    const val MINUS: Byte = 0x2D
    const val EQUAL: Byte = 0x2E
    const val LEFT_BRACKET: Byte = 0x2F
    const val RIGHT_BRACKET: Byte = 0x30
    const val BACKSLASH: Byte = 0x31
    const val SEMICOLON: Byte = 0x33
    const val QUOTE: Byte = 0x34
    const val GRAVE: Byte = 0x35
    const val COMMA: Byte = 0x36
    const val PERIOD: Byte = 0x37
    const val SLASH: Byte = 0x38

    const val CAPS_LOCK: Byte = 0x39

    // Function keys
    const val F1: Byte = 0x3A; const val F2: Byte = 0x3B; const val F3: Byte = 0x3C
    const val F4: Byte = 0x3D; const val F5: Byte = 0x3E; const val F6: Byte = 0x3F
    const val F7: Byte = 0x40; const val F8: Byte = 0x41; const val F9: Byte = 0x42
    const val F10: Byte = 0x43; const val F11: Byte = 0x44; const val F12: Byte = 0x45

    // Navigation cluster
    const val PRINT_SCREEN: Byte = 0x46
    const val SCROLL_LOCK: Byte = 0x47
    const val PAUSE: Byte = 0x48
    const val INSERT: Byte = 0x49
    const val HOME: Byte = 0x4A
    const val PAGE_UP: Byte = 0x4B
    const val DELETE: Byte = 0x4C
    const val END: Byte = 0x4D
    const val PAGE_DOWN: Byte = 0x4E
    const val ARROW_RIGHT: Byte = 0x4F
    const val ARROW_LEFT: Byte = 0x50
    const val ARROW_DOWN: Byte = 0x51
    const val ARROW_UP: Byte = 0x52

    // Keypad
    const val NUM_LOCK: Byte = 0x53
    const val KP_DIVIDE: Byte = 0x54
    const val KP_MULTIPLY: Byte = 0x55
    const val KP_MINUS: Byte = 0x56
    const val KP_PLUS: Byte = 0x57
    const val KP_ENTER: Byte = 0x58
    const val KP_1: Byte = 0x59; const val KP_2: Byte = 0x5A; const val KP_3: Byte = 0x5B
    const val KP_4: Byte = 0x5C; const val KP_5: Byte = 0x5D; const val KP_6: Byte = 0x5E
    const val KP_7: Byte = 0x5F; const val KP_8: Byte = 0x60; const val KP_9: Byte = 0x61
    const val KP_0: Byte = 0x62
    const val KP_PERIOD: Byte = 0x63

    const val APPLICATION: Byte = 0x65   // context-menu key
}

/** Consumer page (0x0C) usages for media keys. */
object HidConsumer {
    const val PLAY_PAUSE: Int = 0x00CD
    const val STOP: Int = 0x00B7
    const val NEXT_TRACK: Int = 0x00B5
    const val PREV_TRACK: Int = 0x00B6
    const val VOLUME_UP: Int = 0x00E9
    const val VOLUME_DOWN: Int = 0x00EA
    const val MUTE: Int = 0x00E2
    const val BRIGHTNESS_UP: Int = 0x006F
    const val BRIGHTNESS_DOWN: Int = 0x0070
    const val AC_HOME: Int = 0x0223
    const val AC_BACK: Int = 0x0224
}

/** System control report bits (report ID 4). */
object HidSystem {
    const val POWER: Byte = 0x01
    const val SLEEP: Byte = 0x02
    const val WAKE: Byte = 0x04
}

/** Mouse button bitmap (byte 0 of the mouse report). */
enum class MouseButton(val mask: Byte) {
    LEFT(0x01),
    RIGHT(0x02),
    MIDDLE(0x04)
}
