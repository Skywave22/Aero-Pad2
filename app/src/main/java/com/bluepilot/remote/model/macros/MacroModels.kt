package com.bluepilot.remote.model.macros

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Macro system models.
 *
 * A macro is a named ordered list of [MacroStep]s. Steps are serializable
 * (stored as JSON in the Room `macros` table) and the macro engine expands
 * them into HidActions at playback time.
 *
 * Design rules:
 *  - Every step type is explicit — no "raw bytes" escape hatch that could
 *    crash a host.
 *  - All values validated by sanitized(): delays capped, text capped,
 *    step count capped.
 */

@Serializable
sealed class MacroStep {

    /** Press + release one key with optional modifiers (chords like Ctrl+Alt+Del). */
    @Serializable
    @SerialName("key")
    data class KeyTap(val key: Byte, val modifiers: Byte = 0) : MacroStep()

    /** Type a text string. */
    @Serializable
    @SerialName("text")
    data class TypeText(val text: String) : MacroStep()

    /** Media/consumer control tap. */
    @Serializable
    @SerialName("media")
    data class Media(val usage: Int) : MacroStep()

    /** Mouse click (HID mask: 1=left 2=right 4=middle). */
    @Serializable
    @SerialName("mouse")
    data class MouseClick(val buttonMask: Int) : MacroStep()

    /** Wait between steps. */
    @Serializable
    @SerialName("delay")
    data class Delay(val ms: Long) : MacroStep()

    /** V2 MATRIX 2 — hold a key down for a duration (charge attacks, etc). */
    @Serializable
    @SerialName("keyhold")
    data class KeyHold(val key: Byte, val modifiers: Byte = 0, val ms: Long = 250) : MacroStep()

    /** V2 MATRIX 2 — humanizer: random wait in [minMs, maxMs] (anti-pattern
     *  jitter so looped macros don't tick like a metronome). */
    @Serializable
    @SerialName("randdelay")
    data class RandomDelay(val minMs: Long = 100, val maxMs: Long = 300) : MacroStep()

    /** V2 MATRIX 2 — mouse wheel step (+ = up/away, − = down/toward). */
    @Serializable
    @SerialName("scroll")
    data class Scroll(val amount: Int) : MacroStep()

    /** V2 MATRIX 2 b2 — repeat the previous [span] steps [times] times
     *  total (flat loop, unrolled at expand time — no nesting needed). */
    @Serializable
    @SerialName("repeat")
    data class RepeatLast(val span: Int = 1, val times: Int = 2) : MacroStep()

    /** V2 MATRIX 2 b2 — run another stored macro inline (sub-macro).
     *  Cycles and depth are guarded at expand time — can never hang. */
    @Serializable
    @SerialName("run")
    data class RunMacro(val macroId: Long) : MacroStep()

    fun sanitized(): MacroStep = when (this) {
        is TypeText -> copy(text = text.take(MacroSpec.TEXT_MAX))
        is Delay -> copy(ms = ms.coerceIn(0, MacroSpec.DELAY_MAX_MS))
        is KeyHold -> copy(ms = ms.coerceIn(20, MacroSpec.DELAY_MAX_MS))
        is RandomDelay -> {
            val lo = minMs.coerceIn(0, MacroSpec.DELAY_MAX_MS)
            val hi = maxMs.coerceIn(lo, MacroSpec.DELAY_MAX_MS)
            copy(minMs = lo, maxMs = hi)
        }
        is Scroll -> copy(amount = amount.coerceIn(-10, 10))
        is RepeatLast -> copy(
            span = span.coerceIn(1, 8),
            times = times.coerceIn(2, 10)
        )
        else -> this
    }
}

@Serializable
@androidx.compose.runtime.Immutable
data class MacroSpec(
    val name: String = "New macro",
    val steps: List<MacroStep> = emptyList()
) {
    companion object {
        const val STEPS_MAX = 64
        const val TEXT_MAX = 500
        const val DELAY_MAX_MS = 5_000L
        const val NAME_MAX = 40
    }

    fun sanitized(): MacroSpec = copy(
        name = name.take(NAME_MAX).ifBlank { "Macro" },
        steps = steps.take(STEPS_MAX).map { it.sanitized() }
    )
}
