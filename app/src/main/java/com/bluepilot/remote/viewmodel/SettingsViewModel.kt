package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.model.AppSettings
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.model.HapticIntensity
import com.bluepilot.remote.model.GamepadSettings
import com.bluepilot.remote.model.KeyboardSettings
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen driver. Exposes each settings group as StateFlow and
 * provides granular update methods (each persists via the sanitizing store).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val haptics: com.bluepilot.remote.haptics.Haptics
) : ViewModel() {

    init {
        // SECTION 8 — keep the engine's global intensity in sync.
        viewModelScope.launch {
            store.appSettings.collect { s ->
                haptics.intensityScale = when (s.hapticIntensity) {
                    HapticIntensity.LIGHT -> 0.25f
                    HapticIntensity.MEDIUM -> 0.6f
                    HapticIntensity.STRONG -> 1.0f
                }
            }
        }
    }

    val app: StateFlow<AppSettings> = store.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val mouse: StateFlow<MouseSettings> = store.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    val keyboard: StateFlow<KeyboardSettings> = store.keyboardSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardSettings())

    val gamepad: StateFlow<GamepadSettings> = store.gamepadSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GamepadSettings())

    // ----- App -----
    fun setTheme(theme: ThemeMode) = updateApp { it.copy(theme = theme) }

    /** SECTION 1: applying a theme also records it in the recents row. */
    fun setThemeId(id: String) = updateApp {
        it.copy(
            themeId = id,
            recentThemes = com.bluepilot.remote.ui.theme.ThemeListCodec.push(it.recentThemes, id)
        )
    }.also { haptics.play(com.bluepilot.remote.model.gamepad.HapticPattern.MEDIUM_CLICK) }

    /** SECTION 1: pin/unpin a theme in the favorites row. */
    fun toggleFavoriteTheme(id: String) = updateApp {
        it.copy(favoriteThemes = com.bluepilot.remote.ui.theme.ThemeListCodec.toggle(it.favoriteThemes, id))
    }

    // SECTION 1: auto theme scheduling.
    fun setAutoTheme(enabled: Boolean) = updateApp { it.copy(autoThemeEnabled = enabled) }
    fun setAutoDayTheme(id: String) = updateApp { it.copy(autoDayTheme = id) }
    fun setAutoNightTheme(id: String) = updateApp { it.copy(autoNightTheme = id) }
    fun setAutoNightStart(hour: Int) = updateApp { it.copy(autoNightStart = hour.coerceIn(0, 23)) }
    fun setAutoNightEnd(hour: Int) = updateApp { it.copy(autoNightEnd = hour.coerceIn(0, 23)) }
    fun setFullscreen(value: Boolean) = updateApp { it.copy(fullscreenMode = value) }
    fun setKeepScreenOn(value: Boolean) = updateApp { it.copy(keepScreenOn = value) }
    fun setTouchVibrations(value: Boolean) = updateApp { it.copy(touchVibrations = value) }
    fun setHapticIntensity(value: HapticIntensity) = updateApp { it.copy(hapticIntensity = value) }
    fun setSecureScreen(value: Boolean) = updateApp { it.copy(secureScreen = value) }
    fun setOnboardingDone() = updateApp { it.copy(onboardingDone = true) }
    fun setReduceMotion(value: Boolean) = updateApp { it.copy(reduceMotion = value) }
    fun setIconPack(value: String) = updateApp { it.copy(iconPack = value) }
    fun setQuality3D(value: String) = updateApp { it.copy(quality3D = value) }
    fun setFpsOverlay(value: Boolean) = updateApp { it.copy(fpsOverlay = value) }   // V2 A5
    fun setAutomationApi(value: Boolean) = updateApp { it.copy(automationApi = value) }   // V2 M8 b2
    fun setLightAutoTheme(value: Boolean) = updateApp { it.copy(lightAutoTheme = value) }   // V2 M3
    fun setSpokenAlerts(value: Boolean) = updateApp { it.copy(spokenAlerts = value) }   // V2 M5 b2

    // ----- Mouse -----
    fun setMouseSensitivity(value: Int) = updateMouse { it.copy(sensitivity = value) }
    fun setScrollSpeed(value: Int) = updateMouse { it.copy(scrollSpeed = value) }
    fun setMovementSmoothing(value: Int) = updateMouse { it.copy(movementSmoothing = value) }
    fun setInvertScroll(value: Boolean) = updateMouse { it.copy(invertScroll = value) }
    fun setTapToClick(value: Boolean) = updateMouse { it.copy(tapToClick = value) }
    fun setPenMode(value: Boolean) = updateMouse { it.copy(penMode = value) }

    // ----- Keyboard -----
    // SECTION 3 AUDIT FIX: copy the CURRENT settings instead of constructing
    // a fresh KeyboardSettings() — the old code silently reset every other
    // keyboard field to defaults (latent data-loss bug as fields grow).
    fun setShowTextInputBar(value: Boolean) =
        viewModelScope.launch { store.updateKeyboard(keyboard.value.copy(showTextInputBar = value)) }

    // ----- Gamepad -----
    fun setGamepadMode(mode: GamepadMappingMode) = updateGamepad { it.copy(mappingMode = mode) }
    fun setJoystickSensitivity(value: Int) = updateGamepad { it.copy(joystickSensitivity = value) }
    fun setDeadZone(value: Int) = updateGamepad { it.copy(deadZone = value) }
    fun setHapticFeedback(value: Boolean) = updateGamepad { it.copy(hapticFeedback = value) }

    // ------------------------------------------------------------------

    private fun updateApp(transform: (AppSettings) -> AppSettings) =
        viewModelScope.launch { store.updateApp(transform(app.value)) }

    private fun updateMouse(transform: (MouseSettings) -> MouseSettings) =
        viewModelScope.launch { store.updateMouse(transform(mouse.value)) }

    private fun updateGamepad(transform: (GamepadSettings) -> GamepadSettings) =
        viewModelScope.launch { store.updateGamepad(transform(gamepad.value)) }
}
