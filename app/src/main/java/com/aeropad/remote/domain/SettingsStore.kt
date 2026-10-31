package com.aeropad.remote.domain

import com.aeropad.remote.model.AppSettings
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Domain contract for settings persistence.
 * The DataStore implementation lives in the data layer; tests use an
 * in-memory fake. All update methods must sanitize before persisting.
 */
interface SettingsStore {
    val appSettings: Flow<AppSettings>
    val mouseSettings: Flow<MouseSettings>
    val keyboardSettings: Flow<KeyboardSettings>
    val gamepadSettings: Flow<GamepadSettings>

    suspend fun updateApp(settings: AppSettings)
    suspend fun updateMouse(settings: MouseSettings)
    suspend fun updateKeyboard(settings: KeyboardSettings)
    suspend fun updateGamepad(settings: GamepadSettings)

    /**
     * Read-modify-write helpers.
     *
     * `updateApp(settings)` writes the WHOLE settings object, so a caller that
     * built it from a stale snapshot silently reverts every field changed in
     * between. That is a real loss-of-user-choice bug: the app keeps a
     * StateFlow copy, and two toggles inside one DataStore round-trip both
     * start from the same value — the second write undoes the first.
     *
     * These apply [transform] to the value that is actually stored at the
     * moment of the write. The default implementation here is correct but
     * still reads through the flow (one emission behind at worst); the
     * DataStore implementation overrides all four and runs the transform
     * inside the write transaction, so it is exactly linearizable.
     */
    suspend fun mutateApp(transform: (AppSettings) -> AppSettings) {
        updateApp(transform(appSettings.first()))
    }

    suspend fun mutateMouse(transform: (MouseSettings) -> MouseSettings) {
        updateMouse(transform(mouseSettings.first()))
    }

    suspend fun mutateKeyboard(transform: (KeyboardSettings) -> KeyboardSettings) {
        updateKeyboard(transform(keyboardSettings.first()))
    }

    suspend fun mutateGamepad(transform: (GamepadSettings) -> GamepadSettings) {
        updateGamepad(transform(gamepadSettings.first()))
    }
}
