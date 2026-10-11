package com.aeropad.remote.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.model.AppSettings
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HapticIntensity
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aeropad_settings"
)

/**
 * DataStore-backed implementation of [SettingsStore].
 *
 * Defensive rules:
 *  - Read errors (corrupt file) fall back to defaults instead of crashing.
 *  - Enum values are parsed with safe fallback.
 *  - Every write sanitizes numeric ranges first.
 */
@Singleton
class DataStoreSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsStore {

    private object Keys {
        // App
        val THEME = stringPreferencesKey("theme")
        val FULLSCREEN = booleanPreferencesKey("fullscreen")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VIBRATIONS = booleanPreferencesKey("vibrations")
        val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val QUALITY_3D = stringPreferencesKey("quality_3d")
        val FAV_GAMEPADS = stringPreferencesKey("fav_gamepads")
        val RECENT_GAMEPADS = stringPreferencesKey("recent_gamepads")
        val FPS_OVERLAY = booleanPreferencesKey("fps_overlay")
        val AUTOMATION_API = booleanPreferencesKey("automation_api")   // V2 M8 b2
        val SPOKEN_ALERTS = booleanPreferencesKey("spoken_alerts")   // V2 M5 b2
        // Mouse
        val M_SENSITIVITY = intPreferencesKey("m_sensitivity")
        val M_SCROLL = intPreferencesKey("m_scroll")
        val M_SMOOTHING = intPreferencesKey("m_smoothing")
        val M_INVERT = booleanPreferencesKey("m_invert")
        val M_TAP_CLICK = booleanPreferencesKey("m_tap_click")
        val M_PEN = booleanPreferencesKey("m_pen")
        // Keyboard
        val K_TEXT_BAR = booleanPreferencesKey("k_text_bar")
        // Gamepad
        val G_MODE = stringPreferencesKey("g_mode")
        val G_SENSITIVITY = intPreferencesKey("g_sensitivity")
        val G_DEADZONE = intPreferencesKey("g_deadzone")
        val G_HAPTIC = booleanPreferencesKey("g_haptic")
    }

    /** Preferences flow that never throws: IO errors → defaults. */
    private val safePrefs: Flow<Preferences> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                Timber.e(error, "settings read failed; using defaults")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    override val appSettings: Flow<AppSettings> = safePrefs.map { p ->
        AppSettings(
            theme = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
            fullscreenMode = p[Keys.FULLSCREEN] ?: false,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            touchVibrations = p[Keys.VIBRATIONS] ?: true,
            hapticIntensity = p[Keys.HAPTIC_INTENSITY].toEnum(HapticIntensity.MEDIUM),
            secureScreen = p[Keys.SECURE_SCREEN] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
            iconPack = p[Keys.ICON_PACK] ?: "ROUNDED",
            quality3D = p[Keys.QUALITY_3D] ?: "FULL",
            favoriteGamepads = p[Keys.FAV_GAMEPADS] ?: "",
            recentGamepads = p[Keys.RECENT_GAMEPADS] ?: "",
            fpsOverlay = p[Keys.FPS_OVERLAY] ?: false,
            automationApi = p[Keys.AUTOMATION_API] ?: false,
            spokenAlerts = p[Keys.SPOKEN_ALERTS] ?: true
        )
    }

    override val mouseSettings: Flow<MouseSettings> = safePrefs.map { p ->
        MouseSettings(
            sensitivity = p[Keys.M_SENSITIVITY] ?: 65,
            scrollSpeed = p[Keys.M_SCROLL] ?: 50,
            movementSmoothing = p[Keys.M_SMOOTHING] ?: 20,
            invertScroll = p[Keys.M_INVERT] ?: false,
            tapToClick = p[Keys.M_TAP_CLICK] ?: true,
            penMode = p[Keys.M_PEN] ?: false
        ).sanitized()
    }

    override val keyboardSettings: Flow<KeyboardSettings> = safePrefs.map { p ->
        KeyboardSettings(showTextInputBar = p[Keys.K_TEXT_BAR] ?: true)
    }

    override val gamepadSettings: Flow<GamepadSettings> = safePrefs.map { p ->
        GamepadSettings(
            mappingMode = p[Keys.G_MODE].toEnum(GamepadMappingMode.HID_GAMEPAD),
            joystickSensitivity = p[Keys.G_SENSITIVITY] ?: 70,
            deadZone = p[Keys.G_DEADZONE] ?: 10,
            hapticFeedback = p[Keys.G_HAPTIC] ?: true
        ).sanitized()
    }

    // ------------------------------------------------------------------
    // Writes (sanitize first, never store junk)
    // ------------------------------------------------------------------

    override suspend fun updateApp(settings: AppSettings) {
        safeEdit { p ->
            p[Keys.THEME] = settings.theme.name
            p[Keys.FULLSCREEN] = settings.fullscreenMode
            p[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
            p[Keys.VIBRATIONS] = settings.touchVibrations
            p[Keys.HAPTIC_INTENSITY] = settings.hapticIntensity.name
            p[Keys.SECURE_SCREEN] = settings.secureScreen
            p[Keys.ONBOARDING_DONE] = settings.onboardingDone
            p[Keys.REDUCE_MOTION] = settings.reduceMotion
            p[Keys.ICON_PACK] = settings.iconPack
            p[Keys.QUALITY_3D] = settings.quality3D
            p[Keys.FAV_GAMEPADS] = settings.favoriteGamepads
            p[Keys.RECENT_GAMEPADS] = settings.recentGamepads
            p[Keys.FPS_OVERLAY] = settings.fpsOverlay
            p[Keys.AUTOMATION_API] = settings.automationApi
            p[Keys.SPOKEN_ALERTS] = settings.spokenAlerts
        }
    }

    override suspend fun updateMouse(settings: MouseSettings) {
        val s = settings.sanitized()
        safeEdit { p ->
            p[Keys.M_SENSITIVITY] = s.sensitivity
            p[Keys.M_SCROLL] = s.scrollSpeed
            p[Keys.M_SMOOTHING] = s.movementSmoothing
            p[Keys.M_INVERT] = s.invertScroll
            p[Keys.M_TAP_CLICK] = s.tapToClick
            p[Keys.M_PEN] = s.penMode
        }
    }

    override suspend fun updateKeyboard(settings: KeyboardSettings) {
        safeEdit { p -> p[Keys.K_TEXT_BAR] = settings.showTextInputBar }
    }

    override suspend fun updateGamepad(settings: GamepadSettings) {
        val s = settings.sanitized()
        safeEdit { p ->
            p[Keys.G_MODE] = s.mappingMode.name
            p[Keys.G_SENSITIVITY] = s.joystickSensitivity
            p[Keys.G_DEADZONE] = s.deadZone
            p[Keys.G_HAPTIC] = s.hapticFeedback
        }
    }

    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.settingsDataStore.edit(block) }
            .onFailure { Timber.e(it, "settings write failed") }
    }

    /** Enum-from-string with fallback — storage corruption can't crash us. */
    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
