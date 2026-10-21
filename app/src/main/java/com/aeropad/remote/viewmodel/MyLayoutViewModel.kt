package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.data.keys.CustomComboStore
import com.aeropad.remote.data.layout.LayoutRepository
import com.aeropad.remote.data.layout.LayoutProfile
import com.aeropad.remote.data.hosts.HostProfiles
import com.aeropad.remote.domain.HidController
import com.aeropad.remote.domain.KeyPlacement
import com.aeropad.remote.domain.MacroEngine
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.domain.WidgetInteractor
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.keys.KeyCatalog
import com.aeropad.remote.model.keys.NamedCombo
import com.aeropad.remote.model.widgets.LayoutSpec
import com.aeropad.remote.model.widgets.WidgetAction
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.model.widgets.WidgetStyle
import com.aeropad.remote.model.widgets.WidgetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Multi-device profiles: the "My Layout" surface. The layout shown is
 * whatever the CURRENTLY CONNECTED host has bound to it — connect the
 * laptop and its panel appears; switch to the TV and its panel appears.
 * Picking a layout while connected also remembers it for that host, so
 * you never rebuild controls per device.
 *
 * Also owns the key pad: adding single keys (A, B, Enter…), adding named
 * combos the user created, and sending them live to the host.
 */
@HiltViewModel
class MyLayoutViewModel @Inject constructor(
    private val repository: LayoutRepository,
    private val hosts: HostProfiles,
    private val comboStore: CustomComboStore,
    private val sendAction: SendHidActionUseCase,
    hid: HidController,
    settingsStore: SettingsStore,
    macroEngine: MacroEngine
) : ViewModel() {

    private val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    /** Live behavior for the rendered layout. */
    val interactor = WidgetInteractor(
        send = { sendAction(it) },
        mouseSettings = { mouseSettings.value },
        runMacro = { macroEngine.play(it) }
    )

    val profiles = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val hostProfiles = hosts.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectedAddress: StateFlow<String?> = hid.state
        .map { (it as? HidConnectionState.Connected)?.device?.address }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _overrideId = MutableStateFlow<Long?>(null)

    private val _profile = MutableStateFlow<LayoutProfile?>(null)

    /** The layout on screen, if any. */
    val layout: StateFlow<LayoutSpec?> = _profile
        .map { it?.spec }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Row id of the layout on screen — needed to save edits back. */
    val currentLayoutId: StateFlow<Long?> = _profile
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True when the visible layout is a sealed built-in template. */
    val currentIsBuiltIn: StateFlow<Boolean> = _profile
        .map { it?.isBuiltIn == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The user's own named shortcuts. */
    val combos: StateFlow<List<NamedCombo>> = comboStore.combos

    private val _message = MutableStateFlow<String?>(null)

    /** One-shot text for the snackbar. Call [consumeMessage] after showing. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    init {
        viewModelScope.launch {
            combine(hostProfiles, connectedAddress, _overrideId) { hs, addr, override ->
                val bound = addr?.let { a ->
                    hs.firstOrNull { it.address == a }?.layoutId?.takeIf { it > 0L }
                }
                override ?: bound
            }.collect { id ->
                _profile.value = id?.let { repository.byId(it) }
            }
        }
    }

    /** Show a layout now, and remember it for the connected host. */
    fun show(layoutId: Long) {
        _overrideId.value = layoutId
        connectedAddress.value?.let { hosts.bindLayout(it, layoutId) }
    }

    // ------------------------------------------------------------------
    // Live key sending
    // ------------------------------------------------------------------

    /**
     * Send one key press with the given modifier mask. Used by the key pad in
     * "Send" mode, so the pad doubles as a real keyboard instead of only a
     * builder.
     */
    fun playKey(key: Byte, mods: Int) {
        viewModelScope.launch {
            runCatching { sendAction(HidAction.KeyTap(key, (mods and 0xFF).toByte())) }
                .onFailure { _message.value = "Couldn't send — is the host connected?" }
        }
    }

    fun playCombo(combo: NamedCombo) = playKey(combo.key.toByte(), combo.mods)

    // ------------------------------------------------------------------
    // Adding keys to the layout
    // ------------------------------------------------------------------

    /**
     * Add a single-key button to the layout on screen.
     *
     * Built-in templates are never modified in place — the first edit forks
     * them into a personal copy so a user can always go back to the original.
     */
    fun addKeyToLayout(label: String, key: Byte, mods: Int) {
        appendKeys(listOf(KeyEntry(label, key, mods)))
    }

    /** Add a small ready-made group of keys (WASD, arrows, …) in one write. */
    fun addPreset(preset: KeyPreset) {
        appendKeys(preset.keys.map { KeyEntry(it.label, it.code, 0) })
    }

    data class KeyEntry(val label: String, val key: Byte, val mods: Int)

    /**
     * One coroutine for the whole batch: the fork of a built-in happens once,
     * every widget is placed, and the layout is written a single time. Two
     * separate writes could interleave and fork the template twice.
     */
    private fun appendKeys(entries: List<KeyEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val current = _profile.value
            if (current == null) {
                _message.value = "Pick a layout first — then keys can be added."
                return@launch
            }
            val editableId = if (current.isBuiltIn) forkBuiltIn(current) else current.id
            val spec = repository.byId(editableId)?.spec ?: run {
                _message.value = "Couldn't open that layout."
                return@launch
            }

            val widgets = spec.widgets.toMutableList()
            var added = 0
            entries.forEach { entry ->
                val free = KeyPlacement.firstFreeThumbFirst(widgets.map { it.frame })
                    ?: return@forEach
                widgets += WidgetSpec(
                    id = UUID.randomUUID().toString(),
                    type = WidgetType.BUTTON,
                    frame = free,
                    style = WidgetStyle(
                        label = entry.label,
                        icon = "",
                        backgroundColor = KEY_BG
                    ),
                    action = WidgetAction.KeyTap(entry.key, (entry.mods and 0xFF).toByte())
                )
                added++
            }

            if (added == 0) {
                _message.value = "This layout is full — remove a control first."
                return@launch
            }

            val saved = runCatching {
                repository.save(editableId, spec.copy(widgets = widgets))
                repository.byId(editableId)
            }.getOrNull()

            if (saved != null) {
                _profile.value = saved
                _overrideId.value = saved.id
                _message.value = if (entries.size == 1) {
                    "Added “${entries.first().label}” — tap it to send."
                } else {
                    "Added $added keys."
                }
            } else {
                _message.value = "Couldn't save the key."
            }
        }
    }

    /** Copy a built-in into a personal layout bound to the current host. */
    private suspend fun forkBuiltIn(current: LayoutProfile): Long {
        val copy = current.spec.copy(name = current.spec.name.take(28) + " (mine)")
        val newId = repository.save(null, copy, isBuiltIn = false)
        connectedAddress.value?.let { hosts.bindLayout(it, newId) }
        _overrideId.value = newId
        _message.value = "Saved a copy you can edit — the template is untouched."
        return newId
    }

    // ------------------------------------------------------------------
    // Named combos
    // ------------------------------------------------------------------

    /**
     * Save the user's own shortcut. Returns the stored combo, or null when the
     * input was unusable (no key, or no name and no modifiers to describe).
     */
    fun saveCombo(name: String, key: Byte, mods: Int): NamedCombo? {
        val stored = comboStore.save(name, key.toInt(), mods)
        _message.value = when {
            stored == null -> "Pick a key on the pad first."
            else -> "Saved “${stored.displayName()}”."
        }
        return stored
    }

    fun deleteCombo(id: String) {
        comboStore.delete(id)
        _message.value = "Shortcut deleted."
    }

    /** Add a saved combo to the layout as one button. */
    fun addComboToLayout(combo: NamedCombo) {
        val label = combo.name.ifBlank {
            NamedCombo.chord(combo.mods, KeyCatalog.byCode(combo.key.toByte())?.label ?: "?")
        }
        addKeyToLayout(label.take(12), combo.key.toByte(), combo.mods)
    }

    companion object {
        /** Slightly different tint so key buttons read as keys, not actions. */
        const val KEY_BG = 0xFF23305A
    }
}

/** Ready-made key groups the pad can drop in one tap. */
enum class KeyPreset(val title: String, val keys: List<com.aeropad.remote.model.keys.KeyDef>) {
    WASD("WASD", listOf(
        com.aeropad.remote.model.keys.KeyCatalog.letters.first { it.label == "W" },
        com.aeropad.remote.model.keys.KeyCatalog.letters.first { it.label == "A" },
        com.aeropad.remote.model.keys.KeyCatalog.letters.first { it.label == "S" },
        com.aeropad.remote.model.keys.KeyCatalog.letters.first { it.label == "D" }
    )),
    ARROWS("Arrows", com.aeropad.remote.model.keys.KeyCatalog.navigation
        .filter { it.label in setOf("↑", "↓", "←", "→") }),
    ENTER_ESC("Esc + Enter", com.aeropad.remote.model.keys.KeyCatalog.navigation
        .filter { it.label in setOf("Esc", "⏎") })
}
