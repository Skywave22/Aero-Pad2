package com.aeropad.remote.domain

import com.aeropad.remote.model.AppSettings
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseSettings
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
