package com.bluepilot.remote.domain

import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SECTION 3A — Macro recorder.
 *
 * Sits inside the SendHidAction pipeline: while recording, every discrete
 * input the user performs on ANY control screen (keyboard keys, shortcuts,
 * media taps, mouse clicks, typed text) is captured as macro steps, with
 * real pauses preserved as Delay steps. Stop → steps become an editable
 * macro draft, replayable with one tap.
 *
 * Continuous streams (mouse moves, scroll, gamepad axes) are intentionally
 * NOT recorded — they'd produce thousands of steps of noise.
 */
@Singleton
class MacroRecorder @Inject constructor() {

    companion object {
        /** Gaps shorter than this are considered "same moment" — no Delay step. */
        const val MIN_GAP_MS = 150L

        /** Convert a HidAction into a recordable step (null = not recordable). */
        fun toStep(action: HidAction): MacroStep? = when (action) {
            is HidAction.KeyTap -> MacroStep.KeyTap(action.key, action.modifiers)
            is HidAction.TypeText ->
                if (action.text.isNotEmpty()) MacroStep.TypeText(action.text) else null
            is HidAction.MediaTap -> MacroStep.Media(action.usage)
            is HidAction.MouseClick -> MacroStep.MouseClick(action.button.mask.toInt())
            else -> null // moves/scroll/gamepad/system: too noisy or stateful
        }
    }

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val steps = mutableListOf<MacroStep>()
    private var lastEventAt = 0L

    /** Begin capturing. Clears any previous unfinished recording. */
    @Synchronized
    fun start() {
        steps.clear()
        _stepCount.value = 0
        lastEventAt = 0L
        _recording.value = true
        Timber.i("macro recording started")
    }

    /**
     * Called by the send pipeline for every outgoing action.
     * No-op unless recording. [now] injectable for tests.
     */
    @Synchronized
    fun capture(action: HidAction, now: Long = System.currentTimeMillis()) {
        if (!_recording.value) return
        if (steps.size >= MacroSpec.STEPS_MAX) return
        val step = toStep(action) ?: return

        // Preserve the user's real pacing as Delay steps.
        if (steps.isNotEmpty() && lastEventAt > 0) {
            val gap = now - lastEventAt
            if (gap >= MIN_GAP_MS && steps.size < MacroSpec.STEPS_MAX - 1) {
                steps += MacroStep.Delay(gap.coerceAtMost(MacroSpec.DELAY_MAX_MS))
            }
        }
        steps += step.sanitized()
        lastEventAt = now
        _stepCount.value = steps.size
    }

    /** Stop and return the captured sequence (recorder resets). */
    @Synchronized
    fun stop(): List<MacroStep> {
        _recording.value = false
        val result = steps.toList()
        steps.clear()
        _stepCount.value = 0
        lastEventAt = 0L
        Timber.i("macro recording stopped: %d steps", result.size)
        return result
    }
}
