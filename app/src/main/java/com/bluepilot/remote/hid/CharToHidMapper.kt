package com.bluepilot.remote.hid

import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers

/**
 * Maps a character to its US-QWERTY HID keystroke (keycode + modifier).
 *
 * Pure JVM code — fully unit-tested. Characters that have no US-layout
 * representation return null; the caller decides to skip or warn.
 */
object CharToHidMapper {

    /** One keystroke: usage ID + modifier byte. */
    data class KeyStroke(val key: Byte, val modifiers: Byte)

    private val SHIFT = HidModifiers.LEFT_SHIFT

    private val map: Map<Char, KeyStroke> = buildMap {
        // Lowercase letters a..z -> 0x04..0x1D, no modifier
        ('a'..'z').forEachIndexed { i, c -> put(c, KeyStroke((0x04 + i).toByte(), 0)) }
        // Uppercase letters need Shift
        ('A'..'Z').forEachIndexed { i, c -> put(c, KeyStroke((0x04 + i).toByte(), SHIFT)) }
        // Digits 1..9 then 0
        put('1', KeyStroke(HidKeys.NUM_1, 0)); put('2', KeyStroke(HidKeys.NUM_2, 0))
        put('3', KeyStroke(HidKeys.NUM_3, 0)); put('4', KeyStroke(HidKeys.NUM_4, 0))
        put('5', KeyStroke(HidKeys.NUM_5, 0)); put('6', KeyStroke(HidKeys.NUM_6, 0))
        put('7', KeyStroke(HidKeys.NUM_7, 0)); put('8', KeyStroke(HidKeys.NUM_8, 0))
        put('9', KeyStroke(HidKeys.NUM_9, 0)); put('0', KeyStroke(HidKeys.NUM_0, 0))
        // Shifted digit row symbols
        put('!', KeyStroke(HidKeys.NUM_1, SHIFT)); put('@', KeyStroke(HidKeys.NUM_2, SHIFT))
        put('#', KeyStroke(HidKeys.NUM_3, SHIFT)); put('$', KeyStroke(HidKeys.NUM_4, SHIFT))
        put('%', KeyStroke(HidKeys.NUM_5, SHIFT)); put('^', KeyStroke(HidKeys.NUM_6, SHIFT))
        put('&', KeyStroke(HidKeys.NUM_7, SHIFT)); put('*', KeyStroke(HidKeys.NUM_8, SHIFT))
        put('(', KeyStroke(HidKeys.NUM_9, SHIFT)); put(')', KeyStroke(HidKeys.NUM_0, SHIFT))
        // Whitespace / control
        put(' ', KeyStroke(HidKeys.SPACE, 0))
        put('\n', KeyStroke(HidKeys.ENTER, 0))
        put('\t', KeyStroke(HidKeys.TAB, 0))
        // Punctuation
        put('-', KeyStroke(HidKeys.MINUS, 0));        put('_', KeyStroke(HidKeys.MINUS, SHIFT))
        put('=', KeyStroke(HidKeys.EQUAL, 0));        put('+', KeyStroke(HidKeys.EQUAL, SHIFT))
        put('[', KeyStroke(HidKeys.LEFT_BRACKET, 0)); put('{', KeyStroke(HidKeys.LEFT_BRACKET, SHIFT))
        put(']', KeyStroke(HidKeys.RIGHT_BRACKET, 0)); put('}', KeyStroke(HidKeys.RIGHT_BRACKET, SHIFT))
        put('\\', KeyStroke(HidKeys.BACKSLASH, 0));   put('|', KeyStroke(HidKeys.BACKSLASH, SHIFT))
        put(';', KeyStroke(HidKeys.SEMICOLON, 0));    put(':', KeyStroke(HidKeys.SEMICOLON, SHIFT))
        put('\'', KeyStroke(HidKeys.QUOTE, 0));       put('"', KeyStroke(HidKeys.QUOTE, SHIFT))
        put('`', KeyStroke(HidKeys.GRAVE, 0));        put('~', KeyStroke(HidKeys.GRAVE, SHIFT))
        put(',', KeyStroke(HidKeys.COMMA, 0));        put('<', KeyStroke(HidKeys.COMMA, SHIFT))
        put('.', KeyStroke(HidKeys.PERIOD, 0));       put('>', KeyStroke(HidKeys.PERIOD, SHIFT))
        put('/', KeyStroke(HidKeys.SLASH, 0));        put('?', KeyStroke(HidKeys.SLASH, SHIFT))
    }

    /** Returns the keystroke for [char], or null if unmappable on US layout. */
    fun map(char: Char): KeyStroke? = map[char]

    /** Maps a whole string, silently skipping unmappable characters. */
    fun mapText(text: String): List<KeyStroke> = text.mapNotNull { map(it) }
}
