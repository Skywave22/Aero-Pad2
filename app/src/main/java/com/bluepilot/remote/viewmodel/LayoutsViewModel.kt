package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.layout.LayoutProfile
import com.bluepilot.remote.data.layout.LayoutRepository
import com.bluepilot.remote.domain.MacroEngine
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.domain.WidgetInteractor
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.widgets.LayoutSpec
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
 * Drives the Layouts list + the layout player (use mode).
 * Playback behavior is delegated to WidgetInteractor (shared with the
 * editor's preview mode). Also handles create/duplicate/delete and
 * JSON import/export.
 */
@HiltViewModel
class LayoutsViewModel @Inject constructor(
    private val repository: LayoutRepository,
    observeConnection: ObserveConnectionUseCase,
    sendAction: SendHidActionUseCase,
    settingsStore: SettingsStore,
    private val macroEngine: MacroEngine
) : ViewModel() {

    val profiles: StateFlow<List<LayoutProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // SECTION 2 — profile list search/filter (name, category, notes).
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** Profiles filtered by the search query, grouped by category. */
    val filteredProfiles: StateFlow<List<LayoutProfile>> =
        kotlinx.coroutines.flow.combine(profiles, _searchQuery) { list, q ->
            if (q.isBlank()) list
            else list.filter {
                it.spec.name.contains(q, true) ||
                    it.spec.category.contains(q, true) ||
                    it.spec.notes.contains(q, true)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    /** Shared use-mode behavior. */
    val interactor = WidgetInteractor(
        send = { sendAction(it) },
        mouseSettings = { mouseSettings.value },
        runMacro = { macroEngine.play(it) }
    )

    private val _activeProfile = MutableStateFlow<LayoutProfile?>(null)
    val activeProfile: StateFlow<LayoutProfile?> = _activeProfile.asStateFlow()

    /** One-shot user messages (import errors etc.). Null = nothing to show. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** JSON produced by an export request; UI writes it to the picked file. */
    private val _exportPayload = MutableStateFlow<Pair<String, String>?>(null) // (suggestedName, json)
    val exportPayload: StateFlow<Pair<String, String>?> = _exportPayload.asStateFlow()

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    // ------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------

    fun openProfile(id: Long) {
        viewModelScope.launch { _activeProfile.value = repository.byId(id) }
    }

    fun closeProfile() {
        _activeProfile.value = null
    }

    /**
     * Combo-profile switching: swipe left/right in the player moves to the
     * next/previous saved layout (wraps around).
     */
    fun switchProfile(direction: Int) {
        val list = profiles.value
        if (list.size < 2) return
        val currentId = _activeProfile.value?.id ?: return
        val index = list.indexOfFirst { it.id == currentId }
        if (index < 0) return
        val next = list[(index + direction + list.size) % list.size]
        viewModelScope.launch { _activeProfile.value = repository.byId(next.id) }
    }

    // ------------------------------------------------------------------
    // Management
    // ------------------------------------------------------------------

    /** Create an empty layout; returns via [onCreated] with the new row id. */
    fun createNew(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.save(null, LayoutSpec(name = "My layout"))
            }.onSuccess(onCreated)
                .onFailure { Timber.e(it, "create layout failed") }
        }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            runCatching {
                val profile = repository.byId(id) ?: return@launch
                repository.save(null, profile.spec.copy(name = profile.spec.name + " (copy)"))
            }.onFailure { Timber.e(it, "duplicate failed") }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { Timber.e(it, "delete failed") }
        }
    }

    // ------------------------------------------------------------------
    // Import / export
    // ------------------------------------------------------------------

    /** Prepare export: UI opens a file picker, then calls consumeExport(). */
    fun requestExport(id: Long) {
        viewModelScope.launch {
            val profile = repository.byId(id)
            val json = repository.exportJson(id)
            if (profile != null && json != null) {
                val fileName = profile.spec.name
                    .replace(Regex("[^A-Za-z0-9 _-]"), "")
                    .ifBlank { "layout" }
                    .replace(' ', '_') + ".bplayout.json"
                _exportPayload.value = fileName to json
            } else {
                _message.value = "Export failed - layout not found."
            }
        }
    }

    fun consumeExport() { _exportPayload.value = null }

    /** Import raw JSON text read from a user-picked file. */
    fun importFromJson(raw: String?) {
        viewModelScope.launch {
            if (raw.isNullOrBlank()) {
                _message.value = "Import failed - file was empty."
                return@launch
            }
            val newId = repository.importJson(raw)
            _message.value = if (newId != null) "Layout imported ✓"
            else "Import failed - not a valid BluePilot layout file."
        }
    }

    fun consumeMessage() { _message.value = null }
}
