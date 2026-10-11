package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.macros.Macro
import com.bluepilot.remote.data.macros.MacroRepository
import com.bluepilot.remote.domain.MacroEngine
import com.bluepilot.remote.domain.MacroRecorder
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Macros hub: list stored macros, build/edit step sequences, test-play.
 *
 * The editor works on an in-memory draft (spec + optional row id); Save
 * persists through the sanitizing repository.
 */
@HiltViewModel
class MacrosViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val engine: MacroEngine,
    private val recorder: MacroRecorder,
    observeConnection: ObserveConnectionUseCase
) : ViewModel() {

    /** Live recorder state for the REC banner. */
    val isRecording = recorder.recording
    val recordedSteps = recorder.stepCount

    val macros: StateFlow<List<Macro>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Draft being edited; null = list view. */
    private val _draft = MutableStateFlow<Pair<Long?, MacroSpec>?>(null)
    val draft: StateFlow<Pair<Long?, MacroSpec>?> = _draft.asStateFlow()

    // ------------------------------------------------------------------
    // List actions
    // ------------------------------------------------------------------

    fun newMacro() {
        _draft.value = null to MacroSpec(name = "New macro")
    }

    /** SECTION 3A — Recorder: arm capture; user then plays any screen. */
    fun startRecording() {
        recorder.start()
    }

    /** Stop and open the captured sequence as an editable draft. */
    fun stopRecording() {
        val steps = recorder.stop()
        _draft.value = null to MacroSpec(name = "Recorded macro", steps = steps)
    }

    fun edit(macro: Macro) {
        _draft.value = macro.id to macro.spec
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { Timber.e(it, "macro delete failed") }
        }
    }

    fun play(id: Long) = engine.play(id)
    // AEROPAD v1.0 #27/#28 — loop playback + panic stop + real running state.
    fun playLooped(id: Long) = engine.playLooped(id)
    fun panicStop() = engine.stop()
    val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> get() = engine.isRunning

    // ------------------------------------------------------------------
    // Draft editing
    // ------------------------------------------------------------------

    fun closeDraft() {
        _draft.value = null
    }

    fun renameDraft(name: String) {
        val (id, spec) = _draft.value ?: return
        _draft.value = id to spec.copy(name = name.take(MacroSpec.NAME_MAX))
    }

    fun addStep(step: MacroStep) {
        val (id, spec) = _draft.value ?: return
        if (spec.steps.size >= MacroSpec.STEPS_MAX) return
        _draft.value = id to spec.copy(steps = spec.steps + step.sanitized())
    }

    fun removeStep(index: Int) {
        val (id, spec) = _draft.value ?: return
        if (index !in spec.steps.indices) return
        _draft.value = id to spec.copy(steps = spec.steps.filterIndexed { i, _ -> i != index })
    }

    fun moveStepUp(index: Int) {
        val (id, spec) = _draft.value ?: return
        if (index !in 1 until spec.steps.size) return
        val steps = spec.steps.toMutableList()
        val tmp = steps[index - 1]; steps[index - 1] = steps[index]; steps[index] = tmp
        _draft.value = id to spec.copy(steps = steps)
    }

    /** Test-play the current draft without saving. */
    fun testDraft() {
        _draft.value?.let { engine.playSpec(it.second) }
    }

    fun saveDraft() {
        val (id, spec) = _draft.value ?: return
        viewModelScope.launch {
            runCatching { repository.save(id, spec) }
                .onSuccess { _draft.value = null }
                .onFailure { Timber.e(it, "macro save failed") }
        }
    }
}
