package com.aeropad.remote.model

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

/** A12 — Power / battery profile controlling keep-alive, sensor rate, 3D quality, and haptics. */
enum class PowerProfile(val label: String, val sensorSamplingUs: Int) {
    PERFORMANCE("Performance (120 Hz)", 8_333),
    BALANCED("Balanced (50 Hz)", 20_000),
    BATTERY_SAVER("Battery Saver (30 Hz)", 33_333)
}

/** General app behavior. */
data class AppSettings(
    /** Appearance: Light / Dark / Follow system — the only look choice. */
    val theme: ThemeMode = ThemeMode.SYSTEM,
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
    /** H2 — secret token required in every automation broadcast. */
    val automationToken: String = "",
    /** V2 MATRIX 5 b2 — speak connect/disconnect events through the active
     *  screen reader (announceForAccessibility — a no-op without one). */
    val spokenAlerts: Boolean = true,
    // Round 7 — premium personalization.
    /** UI finish: MATERIAL / GLASS / NEON. */
    val uiStyle: String = "MATERIAL",
    /** Home tiles hidden by the user (CSV of stable tile IDs). */
    val hiddenTiles: String = "",
    /** Round 9 — register the extended HID descriptor (touch digitizer +
     *  analog triggers). OFF (default) = the classic 3.3.0 descriptor that
     *  is proven stable on real hosts. */
    val extendedReports: Boolean = false,
    /** A12 — one-tap power profile (Performance / Balanced / Battery saver). */
    val powerProfile: PowerProfile = PowerProfile.BALANCED,
    /** Round 9 — accent colour id (see AccentPalette). */
    val accentColor: String = "BLUE"
)

/** Mouse/trackpad tuning. All percentages 0..100. */
data class MouseSettings(
    val sensitivity: Int = 75,
    val scrollSpeed: Int = 55,
    val movementSmoothing: Int = 12,
    val invertScroll: Boolean = false,
    val tapToClick: Boolean = true,
    val penMode: Boolean = false,
    /**
     * Mirror the mouse for left-handed use: tap = right click, long-press =
     * left click, and the on-screen buttons swap sides.
     */
    val leftHanded: Boolean = false
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
    val showTextInputBar: Boolean = true,
    /** L2 — key press-to-release delay in ms (default 12ms; raise for Smart TVs/BIOS). */
    val keyTapDelayMs: Int = 12
) {
    companion object {
        const val DELAY_MIN = 5
        const val DELAY_MAX = 100
    }

    fun sanitized(): KeyboardSettings = copy(
        keyTapDelayMs = keyTapDelayMs.coerceIn(DELAY_MIN, DELAY_MAX)
    )
}

/** Gamepad behavior. Percentages 0..100; dead zone capped at 50 (usability). */
data class GamepadSettings(
    val mappingMode: GamepadMappingMode = GamepadMappingMode.HID_GAMEPAD,
    val joystickSensitivity: Int = 70,
    val deadZone: Int = 10,
    val hapticFeedback: Boolean = true,
    // Round 7 — Pro gamepad mode.
    val gyroAim: Boolean = false,
    val tiltSteer: Boolean = false,
    val gyroSensitivity: Int = 50,
    val tiltSensitivity: Int = 50
) {
    companion object { const val DEAD_ZONE_MAX = 50 }

    fun sanitized(): GamepadSettings = copy(
        joystickSensitivity = joystickSensitivity.coerceIn(0, 100),
        deadZone = deadZone.coerceIn(0, DEAD_ZONE_MAX),
        gyroSensitivity = gyroSensitivity.coerceIn(0, 100),
        tiltSensitivity = tiltSensitivity.coerceIn(0, 100)
    )
}
