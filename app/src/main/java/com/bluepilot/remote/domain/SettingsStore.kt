package com.bluepilot.remote.domain

import com.bluepilot.remote.model.AppSettings
import com.bluepilot.remote.model.GamepadSettings
import com.bluepilot.remote.model.KeyboardSettings
import com.bluepilot.remote.model.MouseSettings
import kotlinx.coroutines.flow.Flow

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
}
