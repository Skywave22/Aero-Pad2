package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.model.AppSettings
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HapticIntensity
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.ThemeMode
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
    private val haptics: com.aeropad.remote.haptics.Haptics
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
    /** Appearance: Light / Dark / Follow system. */
    fun setTheme(theme: ThemeMode) = updateApp { it.copy(theme = theme) }
    // Round 7 — personalization.
    fun setUiStyle(style: String) = updateApp { it.copy(uiStyle = style) }
    fun setHiddenTiles(csv: String) = updateApp { it.copy(hiddenTiles = csv) }
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
    // Round 7 — Pro gamepad settings.
    fun setGyroAim(value: Boolean) = updateGamepad { it.copy(gyroAim = value) }
    fun setTiltSteer(value: Boolean) = updateGamepad { it.copy(tiltSteer = value) }
    fun setGyroSensitivity(value: Int) = updateGamepad { it.copy(gyroSensitivity = value) }
    fun setTiltSensitivity(value: Int) = updateGamepad { it.copy(tiltSensitivity = value) }
    fun setJoystickSens(value: Int) = updateGamepad { it.copy(joystickSensitivity = value) }
    fun setDeadZoneValue(value: Int) = updateGamepad { it.copy(deadZone = value) }
    /** Apply a whole saved per-game profile. */
    fun applyGamepad(settings: GamepadSettings) = updateGamepad { settings }

    // ------------------------------------------------------------------

    private fun updateApp(transform: (AppSettings) -> AppSettings) =
        viewModelScope.launch { store.updateApp(transform(app.value)) }

    private fun updateMouse(transform: (MouseSettings) -> MouseSettings) =
        viewModelScope.launch { store.updateMouse(transform(mouse.value)) }

    private fun updateGamepad(transform: (GamepadSettings) -> GamepadSettings) =
        viewModelScope.launch { store.updateGamepad(transform(gamepad.value)) }
}
