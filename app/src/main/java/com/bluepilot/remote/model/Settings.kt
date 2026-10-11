package com.bluepilot.remote.model

/**
 * User settings models.
 *
 * Every numeric field has a defined valid range and a `sanitized()` function —
 * values coming from storage or UI are ALWAYS clamped before use, so an
 * out-of-range value can never break rendering or HID math.
 */

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Haptic feedback strength (Section 3B polish). */
enum class HapticIntensity { LIGHT, MEDIUM, STRONG }

/** General app behavior. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Active visual theme id from BuiltInThemes (Section 1 theme engine). */
    val themeId: String = "aero_glass",
    val fullscreenMode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val touchVibrations: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val secureScreen: Boolean = false,
    /** First-run onboarding shown & dismissed (UI/UX redesign). */
    val onboardingDone: Boolean = false,
    /** Disable 3D tilts/parallax/flip transitions (accessibility/battery). */
    val reduceMotion: Boolean = false,
    /** 3D quality: FULL / REDUCED / FLAT (Section 9). */
    val quality3D: String = "FULL",
    /** Icon pack style: FILLED / OUTLINED / ROUNDED / SHARP. */
    val iconPack: String = "ROUNDED",

    // ----- SECTION 1 (deep theme pass) -----
    /** Recently applied theme ids, newest first, CSV (max 6). */
    val recentThemes: String = "",
    /** Favorite/pinned theme ids, CSV. */
    val favoriteThemes: String = "",
    /** Auto theme scheduling: switch by time of day. */
    val autoThemeEnabled: Boolean = false,
    /** Theme id used during the day window. */
    val autoDayTheme: String = "minimal_light",
    /** Theme id used during the night window. */
    val autoNightTheme: String = "pilot_dark",
    /** Night window start hour 0..23 (default 19:00). */
    val autoNightStart: Int = 19,
    /** Night window end hour 0..23 (default 07:00). */
    val autoNightEnd: Int = 7,

    // ----- ADV SECTION 3 (gamepad profile enhancements) -----
    /** Favorite gamepad profile row-ids, CSV. */
    val favoriteGamepads: String = "",
    /** Recently played gamepad profile row-ids, newest first, CSV (max 6). */
    val recentGamepads: String = "",
    /** V2 PART A — real-time FPS overlay (debug/power-user toggle). */
    val fpsOverlay: Boolean = false,
    /** V2 MATRIX 8 b2 — allow external automation apps (Tasker etc.) to
     *  send commands via broadcast. SECURITY: default OFF. */
    val automationApi: Boolean = false,
    /** V2 MATRIX 3 finale — ambient light sensor picks day/night theme
     *  (overrides clock scheduling while enabled; hysteresis-gated). */
    val lightAutoTheme: Boolean = false,
    /** V2 MATRIX 5 b2 — speak connect/disconnect events through the active
     *  screen reader (announceForAccessibility — a no-op without one). */
    val spokenAlerts: Boolean = true
)

/** Mouse/trackpad tuning. All percentages 0..100. */
data class MouseSettings(
    val sensitivity: Int = 65,
    val scrollSpeed: Int = 50,
    val movementSmoothing: Int = 20,
    val invertScroll: Boolean = false,
    val tapToClick: Boolean = true,
    val penMode: Boolean = false
) {
    companion object { const val MIN = 0; const val MAX = 100 }

    /** Clamp every numeric field into its valid range. */
    fun sanitized(): MouseSettings = copy(
        sensitivity = sensitivity.coerceIn(MIN, MAX),
        scrollSpeed = scrollSpeed.coerceIn(MIN, MAX),
        movementSmoothing = movementSmoothing.coerceIn(MIN, MAX)
    )
}

/** Keyboard screen behavior. */
data class KeyboardSettings(
    val showTextInputBar: Boolean = true
)

/** Gamepad behavior. Percentages 0..100; dead zone capped at 50 (usability). */
data class GamepadSettings(
    val mappingMode: GamepadMappingMode = GamepadMappingMode.HID_GAMEPAD,
    val joystickSensitivity: Int = 70,
    val deadZone: Int = 10,
    val hapticFeedback: Boolean = true
) {
    companion object { const val DEAD_ZONE_MAX = 50 }

    fun sanitized(): GamepadSettings = copy(
        joystickSensitivity = joystickSensitivity.coerceIn(0, 100),
        deadZone = deadZone.coerceIn(0, DEAD_ZONE_MAX)
    )
}
