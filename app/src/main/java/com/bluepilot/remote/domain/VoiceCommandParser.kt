package com.bluepilot.remote.domain

import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidKeys

/**
 * ADV SECTION 4 — voice command parsing (pure, unit-tested).
 *
 * Turns a recognized phrase into either a spoken-command action (Enter,
 * Backspace×N, punctuation words) or plain text to type. Punctuation
 * voice-mode replaces the words "comma"/"period"/... with symbols when
 * manual punctuation is enabled.
 */
object VoiceCommandParser {

    sealed class Parsed {
        /** Plain text to type on the host. */
        data class Text(val text: String) : Parsed()
        /** One or more HID actions (e.g. Enter, Backspace xN). */
        data class Actions(val actions: List<HidAction>, val description: String) : Parsed()
    }

    private val PUNCTUATION = mapOf(
        "comma" to ",", "period" to ".", "full stop" to ".",
        "question mark" to "?", "exclamation mark" to "!",
        "exclamation point" to "!", "colon" to ":", "semicolon" to ";",
        "dash" to "-", "hyphen" to "-", "quote" to "\"",
        "open bracket" to "(", "close bracket" to ")"
    )

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    /**
     * Parse a recognized phrase.
     * [commandsEnabled]: spoken shortcuts ("new line", "delete that").
     * [manualPunctuation]: replace punctuation words with symbols.
     */
    fun parse(
        raw: String,
        commandsEnabled: Boolean,
        manualPunctuation: Boolean
    ): Parsed {
        val phrase = raw.trim()
        if (phrase.isEmpty()) return Parsed.Text("")
        val lower = phrase.lowercase()

        if (commandsEnabled) {
            // "new line" / "new paragraph" → Enter.
            if (lower == "new line" || lower == "newline" || lower == "enter" || lower == "new paragraph") {
                return Parsed.Actions(listOf(HidAction.KeyTap(HidKeys.ENTER)), "⏎ Enter")
            }
            // "delete that" → one Backspace; "delete N words|characters" → N backspaces.
            if (lower == "delete that" || lower == "delete last" || lower == "backspace") {
                return Parsed.Actions(listOf(HidAction.KeyTap(HidKeys.BACKSPACE)), "⌫ Backspace")
            }
            val deleteMatch = Regex("^delete (\\w+) (characters?|letters?)$").find(lower)
            if (deleteMatch != null) {
                val n = deleteMatch.groupValues[1].toIntOrNull()
                    ?: NUMBER_WORDS[deleteMatch.groupValues[1]]
                if (n != null && n in 1..20) {
                    return Parsed.Actions(
                        List(n) { HidAction.KeyTap(HidKeys.BACKSPACE) },
                        "⌫ ×$n"
                    )
                }
            }
            if (lower == "tab") {
                return Parsed.Actions(listOf(HidAction.KeyTap(HidKeys.TAB)), "⇥ Tab")
            }
            if (lower == "space" || lower == "spacebar") {
                return Parsed.Actions(listOf(HidAction.KeyTap(HidKeys.SPACE)), "␣ Space")
            }
        }

        if (manualPunctuation) {
            PUNCTUATION[lower]?.let { return Parsed.Text(it) }
            // Also replace embedded punctuation words at phrase end: "hello comma"
            var text = phrase
            PUNCTUATION.forEach { (word, symbol) ->
                text = text.replace(Regex("\\s+$word$", RegexOption.IGNORE_CASE), symbol)
                text = text.replace(Regex("\\s+$word\\s+", RegexOption.IGNORE_CASE), "$symbol ")
            }
            return Parsed.Text(text)
        }

        return Parsed.Text(phrase)
    }

    /** Languages offered in the picker (tag → display). */
    val LANGUAGES: List<Pair<String, String>> = listOf(
        "" to "Device default",
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "ur-PK" to "Urdu",
        "hi-IN" to "Hindi",
        "ar-SA" to "Arabic",
        "es-ES" to "Spanish",
        "fr-FR" to "French",
        "de-DE" to "German",
        "zh-CN" to "Chinese",
        "ja-JP" to "Japanese"
    )
}
