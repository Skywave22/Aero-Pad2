package com.aeropad.remote.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.data.layout.LayoutRepository
import com.aeropad.remote.domain.LayoutEditorOps
import com.aeropad.remote.domain.MacroEngine
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.domain.WidgetInteractor
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.widgets.LayoutSpec
import com.aeropad.remote.model.widgets.WidgetAction
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.model.widgets.WidgetStyle
import com.aeropad.remote.model.widgets.WidgetType
import com.aeropad.remote.data.macros.Macro
import com.aeropad.remote.data.macros.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Layout editor state machine.
 *
 * Edit mode: drag/resize/style/add/delete widgets (all via LayoutEditorOps).
 * Preview mode: the SAME layout becomes live using WidgetInteractor.
 * Dirty tracking + explicit save to Room. Simple undo (last 20 states).
 */
@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    private val repository: LayoutRepository,
    sendAction: SendHidActionUseCase,
    settingsStore: SettingsStore,
    macroEngine: MacroEngine,
    private val macroRepository: MacroRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val UNDO_LIMIT = 20
    }

    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    /** Live behavior for Preview mode. */
    val interactor = WidgetInteractor(
        send = { sendAction(it) },
        mouseSettings = { mouseSettings.value },
        runMacro = { macroEngine.play(it) }
    )

    private val _layout = MutableStateFlow(LayoutSpec(name = "New layout"))
    val layout: StateFlow<LayoutSpec> = _layout.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _previewMode = MutableStateFlow(false)
    val previewMode: StateFlow<Boolean> = _previewMode.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val undoStack = ArrayDeque<LayoutSpec>()
    val canUndo: StateFlow<Boolean> get() = _canUndo
    private val _canUndo = MutableStateFlow(false)
    // P9.4 — human-readable undo history list (most recent first).
    private val _undoHistory = MutableStateFlow<List<String>>(emptyList())
    val undoHistory: StateFlow<List<String>> = _undoHistory.asStateFlow()

    // SECTION 2 — redo stack (cleared on any new mutation).
    private val redoStack = ArrayDeque<LayoutSpec>()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // SECTION 2 — element clipboard (copy/paste inside the editor).
    private val _clipboard = MutableStateFlow<WidgetSpec?>(null)
    val hasClipboard: StateFlow<Boolean> get() = _hasClipboard
    private val _hasClipboard = MutableStateFlow(false)

    // SECTION 2 — multi-select set (long-press to add/remove).
    private val _multiSelection = MutableStateFlow<Set<String>>(emptySet())
    val multiSelection: StateFlow<Set<String>> = _multiSelection.asStateFlow()

    // SECTION 2 — grid overlay + canvas zoom.
    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()
    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    // Round 6 — snap is OPT-IN now: default is free, smooth 1:1 movement.
    private val _snapToGrid = MutableStateFlow(false)
    val snapToGrid: StateFlow<Boolean> = _snapToGrid.asStateFlow()
    fun toggleSnap() { _snapToGrid.value = !_snapToGrid.value }

    /** null = apply the layout grid, 0 = free placement. */
    private fun gridArg(): Float? = if (_snapToGrid.value) null else 0f

    private var loadedBuiltIn = false

    init {
        if (profileId > 0) {
            viewModelScope.launch {
                repository.byId(profileId)?.let { profile ->
                    _layout.value = profile.spec
                    loadedBuiltIn = profile.isBuiltIn
                }
            }
        }
    }

    /** Selected widget spec (null when nothing selected). */
    fun selectedWidget(): WidgetSpec? =
        _layout.value.widgets.firstOrNull { it.id == _selectedId.value }

    // ------------------------------------------------------------------
    // Mode + selection
    // ------------------------------------------------------------------

    fun togglePreview() {
        _previewMode.value = !_previewMode.value
        if (_previewMode.value) _selectedId.value = null
    }

    fun select(id: String?) {
        if (!_previewMode.value) _selectedId.value = id
    }

    // ------------------------------------------------------------------
    // Mutations (all push undo + mark dirty)
    // ------------------------------------------------------------------

    private fun mutate(transform: (LayoutSpec) -> LayoutSpec) {
        pushUndo()
        _layout.value = transform(_layout.value).sanitized()
        _dirty.value = true
        _saved.value = false
    }

    /** Continuous drag: no undo per pixel — call [beginGesture] first.
     *  SECTION 2: if the widget belongs to a group, the whole group moves
     *  together (delta applied to every member). */
    fun moveSelected(x: Float, y: Float) {
        val id = _selectedId.value ?: return
        val current = _layout.value.widgets.firstOrNull { it.id == id } ?: return
        val members = com.aeropad.remote.domain.EditorProOps.groupMembers(_layout.value, id)
        _layout.value = if (members.size > 1) {
            val dx = x - current.frame.x
            val dy = y - current.frame.y
            com.aeropad.remote.domain.EditorProOps.moveMany(_layout.value, members, dx, dy)
        } else {
            LayoutEditorOps.place(_layout.value, id, x, y, gridArg())
        }
        _dirty.value = true
        _saved.value = false
    }

    fun resizeSelected(w: Float, h: Float) {
        val id = _selectedId.value ?: return
        _layout.value = LayoutEditorOps.resize(_layout.value, id, w, h, gridArg())
        _dirty.value = true
        _saved.value = false
    }

    /** Snapshot once at gesture start so a whole drag is one undo step. */
    fun beginGesture() = pushUndo()

    fun addWidget(type: WidgetType) {
        pushUndo()
        val (next, newId) = LayoutEditorOps.add(_layout.value, type)
        _layout.value = next
        newId?.let { _selectedId.value = it }
        _dirty.value = true
        _saved.value = false
    }

    fun duplicateSelected() {
        val id = _selectedId.value ?: return
        pushUndo()
        val (next, newId) = LayoutEditorOps.duplicate(_layout.value, id)
        _layout.value = next
        newId?.let { _selectedId.value = it }
        _dirty.value = true
        _saved.value = false
    }

    fun removeSelected() {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.remove(it, id) }
        _selectedId.value = null
    }

    fun updateSelectedStyle(transform: (WidgetStyle) -> WidgetStyle) {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.updateStyle(it, id, transform) }
    }

    fun setSelectedAction(action: WidgetAction) {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.updateWidget(it, id) { w -> w.copy(action = action) } }
    }

    fun setSelectedSecondaryAction(action: WidgetAction) {
        val id = _selectedId.value ?: return
        mutate { LayoutEditorOps.updateWidget(it, id) { w -> w.copy(secondaryAction = action) } }
    }

    /** GESTURE_ZONE: bind one swipe direction / two-finger tap. */
    fun setSelectedGestureAction(slot: GestureSlot, action: WidgetAction) {
        val id = _selectedId.value ?: return
        mutate {
            LayoutEditorOps.updateWidget(it, id) { w ->
                when (slot) {
                    GestureSlot.UP -> w.copy(swipeUp = action)
                    GestureSlot.DOWN -> w.copy(swipeDown = action)
                    GestureSlot.LEFT -> w.copy(swipeLeft = action)
                    GestureSlot.RIGHT -> w.copy(swipeRight = action)
                    GestureSlot.TWO_FINGER -> w.copy(twoFingerTap = action)
                }
            }
        }
    }

    enum class GestureSlot { UP, DOWN, LEFT, RIGHT, TWO_FINGER }

    fun rename(name: String) = mutate { LayoutEditorOps.rename(it, name) }

    fun setGridSize(size: Float) = mutate { it.copy(gridSize = size) }

    /** Apply a zone split: one widget per zone, sized to fill it. */
    fun applyZoneSplit(split: LayoutEditorOps.ZoneSplit, types: List<WidgetType>) =
        mutate { LayoutEditorOps.applyZones(it, split, types) }

    /** Saved macros for the action picker (RunMacro bindings). */
    val macros: StateFlow<List<Macro>> = macroRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** P9.3 — Create a new macro directly from the widget action picker and bind it. */
    fun createInlineMacro(
        name: String,
        textToType: String,
        onCreated: (WidgetAction.RunMacro) -> Unit
    ) {
        viewModelScope.launch {
            val steps = if (textToType.isNotBlank()) {
                listOf(com.aeropad.remote.model.macros.MacroStep.TypeText(textToType))
            } else {
                listOf(com.aeropad.remote.model.macros.MacroStep.KeyTap(com.aeropad.remote.model.HidKeys.ENTER))
            }
            val spec = com.aeropad.remote.model.macros.MacroSpec(
                name = name.ifBlank { "Quick macro" },
                steps = steps
            )
            runCatching { macroRepository.save(null, spec) }
                .onSuccess { id -> onCreated(WidgetAction.RunMacro(id)) }
                .onFailure { Timber.e(it, "inline macro save failed") }
        }
    }

    /** R3 — Load a built-in template (Gamepad / PC Combo / Media) into the editor. */
    fun applyTemplate(template: String) {
        val currentName = _layout.value.name
        val next = when (template) {
            "gamepad" -> repository.starterGamepadLayout().copy(name = currentName)
            "pc_combo" -> repository.starterTrackpadCombo().copy(name = currentName)
            "media" -> repository.starterMediaRemote().copy(name = currentName)
            else -> return
        }
        mutate { next }
    }

    // ------------------------------------------------------------------
    // Undo / save
    // ------------------------------------------------------------------

    private fun refreshUndoHistory() {
        _undoHistory.value = undoStack.reversed().mapIndexed { idx, spec ->
            "Step -${idx + 1}: ${spec.name} (${spec.widgets.size} widgets)"
        }
    }

    private fun pushUndo() {
        undoStack.addLast(_layout.value)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        _canUndo.value = undoStack.isNotEmpty()
        // New mutation invalidates the redo branch.
        redoStack.clear()
        _canRedo.value = false
        refreshUndoHistory()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_layout.value)
        while (redoStack.size > UNDO_LIMIT) redoStack.removeFirst()
        _canRedo.value = true
        _layout.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _dirty.value = true
        _saved.value = false
        refreshUndoHistory()
    }

    /** P9.4 — Revert [stepsBack] steps in one action from the undo history list. */
    fun revertUndoSteps(stepsBack: Int) {
        repeat(stepsBack.coerceAtLeast(1)) {
            if (undoStack.isNotEmpty()) undo()
        }
    }

    /** SECTION 2 — redo (inverse of undo). */
    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_layout.value)
        _canUndo.value = true
        _layout.value = next
        _canRedo.value = redoStack.isNotEmpty()
        _dirty.value = true
        _saved.value = false
        refreshUndoHistory()
    }

    // ------------------------------------------------------------------
    // SECTION 2 — clipboard, layering, alignment, multi-select, grouping
    // ------------------------------------------------------------------

    fun copySelected() {
        _clipboard.value = selectedWidget() ?: return
        _hasClipboard.value = true
    }

    fun pasteClipboard() {
        val clip = _clipboard.value ?: return
        pushUndo()
        val (next, newId) = com.aeropad.remote.domain.EditorProOps.paste(_layout.value, clip)
        _layout.value = next
        newId?.let { _selectedId.value = it }
        _dirty.value = true
        _saved.value = false
    }

    fun bringToFront() { val id = _selectedId.value ?: return
        mutate { com.aeropad.remote.domain.EditorProOps.bringToFront(it, id) } }

    fun sendToBack() { val id = _selectedId.value ?: return
        mutate { com.aeropad.remote.domain.EditorProOps.sendToBack(it, id) } }

    fun centerSelectedH() { val id = _selectedId.value ?: return
        mutate { com.aeropad.remote.domain.EditorProOps.centerHorizontally(it, id) } }

    fun centerSelectedV() { val id = _selectedId.value ?: return
        mutate { com.aeropad.remote.domain.EditorProOps.centerVertically(it, id) } }

    fun snapSelectedToEdge(edge: com.aeropad.remote.domain.EditorProOps.Edge) {
        val id = _selectedId.value ?: return
        mutate { com.aeropad.remote.domain.EditorProOps.snapToEdge(it, id, edge) }
    }

    fun nudgeSelected(dx: Float, dy: Float) {
        val id = _selectedId.value ?: return
        mutate { com.aeropad.remote.domain.EditorProOps.nudge(it, id, dx, dy) }
    }

    /** Long-press toggles a widget in/out of the multi-selection. */
    fun toggleMultiSelect(id: String) {
        if (_previewMode.value) return
        _multiSelection.value =
            if (id in _multiSelection.value) _multiSelection.value - id
            else _multiSelection.value + id
    }

    fun clearMultiSelect() { _multiSelection.value = emptySet() }

    fun moveMultiSelection(dx: Float, dy: Float) {
        if (_multiSelection.value.isEmpty()) return
        _layout.value = com.aeropad.remote.domain.EditorProOps
            .moveMany(_layout.value, _multiSelection.value, dx, dy)
        _dirty.value = true
        _saved.value = false
    }

    fun deleteMultiSelection() {
        if (_multiSelection.value.isEmpty()) return
        mutate { com.aeropad.remote.domain.EditorProOps.removeMany(it, _multiSelection.value) }
        _multiSelection.value = emptySet()
        _selectedId.value = null
    }

    fun duplicateMultiSelection() {
        if (_multiSelection.value.isEmpty()) return
        mutate { com.aeropad.remote.domain.EditorProOps.duplicateMany(it, _multiSelection.value) }
    }

    fun distributeMultiH() {
        if (_multiSelection.value.size < 3) return
        mutate { com.aeropad.remote.domain.EditorProOps.distributeHorizontally(it, _multiSelection.value.toList()) }
    }

    fun distributeMultiV() {
        if (_multiSelection.value.size < 3) return
        mutate { com.aeropad.remote.domain.EditorProOps.distributeVertically(it, _multiSelection.value.toList()) }
    }

    fun groupMultiSelection() {
        if (_multiSelection.value.size < 2) return
        mutate { com.aeropad.remote.domain.EditorProOps.group(it, _multiSelection.value).first }
    }

    fun ungroupSelected() {
        val w = selectedWidget() ?: return
        if (w.groupId.isBlank()) return
        mutate { com.aeropad.remote.domain.EditorProOps.ungroup(it, w.groupId) }
    }

    fun toggleGrid() { _showGrid.value = !_showGrid.value }

    fun setZoom(z: Float) { _zoom.value = z.coerceIn(1f, 2.5f) }

    /** SECTION 2 — profile metadata (category folder + notes). */
    fun setCategory(category: String) = mutate { it.copy(category = category) }
    fun setNotes(notes: String) = mutate { it.copy(notes = notes) }

    /**
     * Persist to Room. Built-in profiles are saved as a NEW user copy
     * (templates stay pristine).
     */
    fun save() {
        viewModelScope.launch {
            runCatching {
                if (loadedBuiltIn) {
                    val copy = _layout.value.copy(name = _layout.value.name + " (custom)")
                    repository.save(null, copy)
                } else {
                    repository.save(if (profileId > 0) profileId else null, _layout.value)
                }
            }.onSuccess {
                _dirty.value = false
                _saved.value = true
                Timber.i("layout saved")
            }.onFailure {
                Timber.e(it, "layout save failed")
            }
        }
    }
}
