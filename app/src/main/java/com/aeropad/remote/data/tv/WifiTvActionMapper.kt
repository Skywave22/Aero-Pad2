package com.aeropad.remote.data.tv

import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConsumer
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.MouseButton
import kotlin.math.abs

/** Maps the subset of AeroPad actions understood by the legacy TV UDP protocol. */
object WifiTvActionMapper {
    /** Null means this action needs Bluetooth HID or a TV-side receiver. */
    fun commands(action: HidAction): List<String>? = when (action) {
        is HidAction.KeyTap -> keyCode(action.key, action.modifiers)?.let { listOf(it.toString()) }
        is HidAction.TypeText -> listOf(PhoneRemoteProtocol.searchCommand(action.text))
        is HidAction.MouseMove -> listOf("key=mouseaction;${action.dx}=${action.dy}")
        is HidAction.MouseClick -> if (action.button == MouseButton.LEFT) listOf("key=clickmouse") else null
        is HidAction.MouseDoubleClick -> if (action.button == MouseButton.LEFT) {
            listOf("key=clickmouse", "key=clickmouse")
        } else null
        is HidAction.MouseScroll -> {
            val code = if (action.amount < 0) ANDROID_PAGE_UP else ANDROID_PAGE_DOWN
            List(abs(action.amount).coerceIn(0, 5)) { code.toString() }
        }
        is HidAction.MediaTap -> mediaCode(action.usage)?.let { listOf(it.toString()) }
        // The TV protocol only supports tap commands. Held keys/buttons, drag,
        // full gamepad reports, system power, and touch-digitizer reports are
        // deliberately not misrepresented as equivalent TV features.
        is HidAction.KeyDown,
        HidAction.KeyRelease,
        is HidAction.MouseDown,
        is HidAction.MouseUp,
        is HidAction.MouseDrag,
        is HidAction.GamepadUpdate,
        is HidAction.SystemTap,
        is HidAction.TouchUpdate -> null
    }

    private fun keyCode(key: Byte, modifiers: Byte): Int? {
        if (modifiers.toInt() != 0) return null // UDP protocol has no chord/held-key form.
        val usage = key.toInt() and 0xFF
        return when (usage) {
            in 0x04..0x1D -> ANDROID_A + usage - 0x04
            in 0x1E..0x26 -> ANDROID_1 + usage - 0x1E
            0x27 -> ANDROID_0
            0x28 -> ANDROID_ENTER
            0x29 -> ANDROID_ESCAPE
            0x2A -> ANDROID_DEL
            0x2B -> ANDROID_TAB
            0x2C -> ANDROID_SPACE
            0x2D -> ANDROID_MINUS
            0x2E -> ANDROID_EQUALS
            0x2F -> ANDROID_LEFT_BRACKET
            0x30 -> ANDROID_RIGHT_BRACKET
            0x31 -> ANDROID_BACKSLASH
            0x33 -> ANDROID_SEMICOLON
            0x34 -> ANDROID_APOSTROPHE
            0x35 -> ANDROID_GRAVE
            0x36 -> ANDROID_COMMA
            0x37 -> ANDROID_PERIOD
            0x38 -> ANDROID_SLASH
            0x39 -> ANDROID_CAPS_LOCK
            in 0x3A..0x45 -> ANDROID_F1 + usage - 0x3A
            0x46 -> ANDROID_PRINT_SCREEN
            0x47 -> ANDROID_SCROLL_LOCK
            0x48 -> ANDROID_PAUSE
            0x49 -> ANDROID_INSERT
            0x4A -> ANDROID_HOME
            0x4B -> ANDROID_PAGE_UP
            0x4C -> ANDROID_FORWARD_DEL
            0x4D -> ANDROID_MOVE_END
            0x4E -> ANDROID_PAGE_DOWN
            0x4F -> ANDROID_DPAD_RIGHT
            0x50 -> ANDROID_DPAD_LEFT
            0x51 -> ANDROID_DPAD_DOWN
            0x52 -> ANDROID_DPAD_UP
            0x53 -> ANDROID_NUM_LOCK
            0x54 -> ANDROID_NUMPAD_DIVIDE
            0x55 -> ANDROID_NUMPAD_MULTIPLY
            0x56 -> ANDROID_NUMPAD_SUBTRACT
            0x57 -> ANDROID_NUMPAD_ADD
            0x58 -> ANDROID_NUMPAD_ENTER
            in 0x59..0x61 -> ANDROID_NUMPAD_1 + usage - 0x59
            0x62 -> ANDROID_NUMPAD_0
            0x63 -> ANDROID_NUMPAD_DOT
            0x65 -> ANDROID_MENU
            else -> null
        }
    }

    private fun mediaCode(usage: Int): Int? = when (usage) {
        HidConsumer.PLAY_PAUSE -> ANDROID_MEDIA_PLAY_PAUSE
        HidConsumer.STOP -> ANDROID_MEDIA_STOP
        HidConsumer.NEXT_TRACK -> ANDROID_MEDIA_NEXT
        HidConsumer.PREV_TRACK -> ANDROID_MEDIA_PREVIOUS
        HidConsumer.VOLUME_UP -> ANDROID_VOLUME_UP
        HidConsumer.VOLUME_DOWN -> ANDROID_VOLUME_DOWN
        HidConsumer.MUTE -> ANDROID_VOLUME_MUTE
        HidConsumer.AC_BACK -> ANDROID_BACK
        HidConsumer.AC_HOME -> ANDROID_HOME
        else -> null
    }

    private const val ANDROID_HOME = 3
    private const val ANDROID_BACK = 4
    private const val ANDROID_0 = 7
    private const val ANDROID_1 = 8
    private const val ANDROID_A = 29
    private const val ANDROID_ENTER = 66
    private const val ANDROID_ESCAPE = 111
    private const val ANDROID_DEL = 67
    private const val ANDROID_TAB = 61
    private const val ANDROID_SPACE = 62
    private const val ANDROID_MINUS = 69
    private const val ANDROID_EQUALS = 70
    private const val ANDROID_LEFT_BRACKET = 71
    private const val ANDROID_RIGHT_BRACKET = 72
    private const val ANDROID_BACKSLASH = 73
    private const val ANDROID_SEMICOLON = 74
    private const val ANDROID_APOSTROPHE = 75
    private const val ANDROID_GRAVE = 68
    private const val ANDROID_COMMA = 55
    private const val ANDROID_PERIOD = 56
    private const val ANDROID_SLASH = 76
    private const val ANDROID_CAPS_LOCK = 115
    private const val ANDROID_F1 = 131
    private const val ANDROID_PRINT_SCREEN = 120
    private const val ANDROID_SCROLL_LOCK = 116
    private const val ANDROID_PAUSE = 121
    private const val ANDROID_INSERT = 124
    private const val ANDROID_PAGE_UP = 92
    private const val ANDROID_PAGE_DOWN = 93
    private const val ANDROID_FORWARD_DEL = 112
    private const val ANDROID_MOVE_END = 123
    private const val ANDROID_DPAD_RIGHT = 22
    private const val ANDROID_DPAD_LEFT = 21
    private const val ANDROID_DPAD_DOWN = 20
    private const val ANDROID_DPAD_UP = 19
    private const val ANDROID_NUM_LOCK = 143
    private const val ANDROID_NUMPAD_0 = 144
    private const val ANDROID_NUMPAD_1 = 145
    private const val ANDROID_NUMPAD_DIVIDE = 154
    private const val ANDROID_NUMPAD_MULTIPLY = 155
    private const val ANDROID_NUMPAD_SUBTRACT = 156
    private const val ANDROID_NUMPAD_ADD = 157
    private const val ANDROID_NUMPAD_DOT = 158
    private const val ANDROID_NUMPAD_ENTER = 160
    private const val ANDROID_MENU = 82
    private const val ANDROID_MEDIA_PLAY_PAUSE = 85
    private const val ANDROID_MEDIA_STOP = 86
    private const val ANDROID_MEDIA_NEXT = 87
    private const val ANDROID_MEDIA_PREVIOUS = 88
    private const val ANDROID_VOLUME_UP = 24
    private const val ANDROID_VOLUME_DOWN = 25
    private const val ANDROID_VOLUME_MUTE = 164
}
