package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.keyboard.KeyboardLayoutStore
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.keyboard.KeySpec
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Full keyboard driver: renders the persisted board, sends HID on tap
 * (primary) / long-press (secondary), and powers the per-key editor.
 */
@HiltViewModel
class FullKeyboardViewModel @Inject constructor(
    private val store: KeyboardLayoutStore,
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase
) : ViewModel() {

    val layout: StateFlow<KeyboardLayoutSpec> = store.layout
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardLayoutSpec())

    val fullMode: StateFlow<Boolean> = store.fullMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // V2 MATRIX 7 b2 — one-handed mode (OFF / LEFT / RIGHT), persisted.
    val oneHanded: StateFlow<com.bluepilot.remote.data.keyboard.OneHandedMode> =
        store.oneHanded.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            com.bluepilot.remote.data.keyboard.OneHandedMode.OFF
        )

    fun cycleOneHanded() = viewModelScope.launch {
        val next = when (oneHanded.value) {
            com.bluepilot.remote.data.keyboard.OneHandedMode.OFF ->
                com.bluepilot.remote.data.keyboard.OneHandedMode.RIGHT
            com.bluepilot.remote.data.keyboard.OneHandedMode.RIGHT ->
                com.bluepilot.remote.data.keyboard.OneHandedMode.LEFT
            com.bluepilot.remote.data.keyboard.OneHandedMode.LEFT ->
                com.bluepilot.remote.data.keyboard.OneHandedMode.OFF
        }
        store.setOneHanded(next)
    }

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Key currently open in the editor sheet (null = none). */
    private val _editingKey = MutableStateFlow<KeySpec?>(null)
    val editingKey: StateFlow<KeySpec?> = _editingKey.asStateFlow()

    /** Edit mode: long-press opens the key editor instead of sending secondary. */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    // ------------------------------------------------------------------
    // V2 MATRIX 7 — FN layer (latched: tap FN chip, next taps use overlay;
    // stays on until toggled off, like phone symbol layers)
    // ------------------------------------------------------------------
    private val _fnActive = MutableStateFlow(false)
    val fnActive: StateFlow<Boolean> = _fnActive.asStateFlow()
    fun toggleFn() { _fnActive.value = !_fnActive.value }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun tap(key: KeySpec) {
        // V2 M7 — resolve through the FN overlay first (pure, unit-tested).
        val effective = layout.value.effectiveKey(key, _fnActive.value)
        // V2 M7 b2 — binding priority: media usage > typeText > keycode.
        val usage = effective.consumerUsage
        val text = effective.typeText
        when {
            usage != null -> sendAction(HidAction.MediaTap(usage))
            !text.isNullOrEmpty() -> sendAction(HidAction.TypeText(text))
            else -> sendAction(HidAction.KeyTap(effective.keyCode, effective.modifiers))
        }
    }

    /** Long-press: secondary function if bound; otherwise nothing. */
    fun longPress(key: KeySpec) {
        if (_editMode.value) {
            _editingKey.value = key
            return
        }
        val secondary = key.secondaryKeyCode ?: return
        sendAction(HidAction.KeyTap(secondary, key.secondaryModifiers))
    }

    // ------------------------------------------------------------------
    // Modes / editor
    // ------------------------------------------------------------------

    fun setFullMode(value: Boolean) = viewModelScope.launch { store.setFullMode(value) }

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
        if (!_editMode.value) _editingKey.value = null
    }

    fun closeEditor() {
        _editingKey.value = null
    }

    private var persistJob: kotlinx.coroutines.Job? = null

    /** Apply a transform to the edited key everywhere it appears.
     *  BUGFIX/PERF: slider drags fire dozens of updates per second — persist
     *  with a 150ms debounce (latest-wins) instead of a write per tick. */
    fun updateEditingKey(transform: (KeySpec) -> KeySpec) {
        val target = _editingKey.value ?: return
        val updated = transform(target).sanitized()
        _editingKey.value = updated
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            val current = layout.value
            store.save(
                current.copy(
                    rows = current.rows.map { row ->
                        row.map { if (it.id == target.id) updated else it }
                    },
                    favorites = current.favorites.map {
                        if (it.id == target.id) updated else it
                    }
                )
            )
        }
    }

    /** Move the edited key within its row (reposition left/right). */
    fun moveEditingKey(offset: Int) {
        val target = _editingKey.value ?: return
        viewModelScope.launch {
            val current = layout.value
            store.save(
                current.copy(
                    rows = current.rows.map { row ->
                        val idx = row.indexOfFirst { it.id == target.id }
                        if (idx < 0) row
                        else {
                            val to = (idx + offset).coerceIn(0, row.size - 1)
                            if (to == idx) row
                            else row.toMutableList().apply {
                                val item = removeAt(idx); add(to, item)
                            }
                        }
                    }
                )
            )
        }
    }

    /** Add the edited key (or any combo) to the Favorites row. */
    /**
     * V2 MATRIX 7 — apply a shortcut pack: REPLACES the favorites row with
     * the pack's curated keys (explicit user action from the pack chips;
     * the old favorites are recoverable only by re-adding — communicated
     * in the UI label "Replace favorites").
     */
    fun applyPack(pack: com.bluepilot.remote.model.keyboard.ShortcutPacks.Pack) {
        viewModelScope.launch {
            val current = layout.value
            store.save(
                current.copy(
                    favorites = pack.keys
                        .take(KeyboardLayoutSpec.FAVORITES_MAX)
                        .map { it.sanitized() }
                )
            )
        }
    }

    fun addToFavorites(key: KeySpec) {
        viewModelScope.launch {
            val current = layout.value
            if (current.favorites.size >= KeyboardLayoutSpec.FAVORITES_MAX) return@launch
            if (current.favorites.any { it.keyCode == key.keyCode && it.modifiers == key.modifiers }) return@launch
            store.save(
                current.copy(
                    favorites = current.favorites +
                        key.copy(id = "fav-" + UUID.randomUUID().toString()).sanitized()
                )
            )
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch {
            val current = layout.value
            store.save(current.copy(favorites = current.favorites.filterNot { it.id == id }))
        }
    }

    /** Create a custom combo key straight into Favorites (e.g. Ctrl+C). */
    fun addComboFavorite(label: String, keyCode: Byte, modifiers: Byte) {
        addToFavorites(
            KeySpec(
                id = "fav-" + UUID.randomUUID().toString(),
                label = label,
                keyCode = keyCode,
                modifiers = modifiers
            )
        )
    }

    fun resetBoard() = viewModelScope.launch { store.reset() }
}
