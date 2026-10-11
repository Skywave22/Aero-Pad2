package com.bluepilot.remote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.ThemeMode
import com.bluepilot.remote.ui.components.LocalHapticIntensity
import com.bluepilot.remote.ui.components.IconPack
import com.bluepilot.remote.ui.components.LocalIconPack
import com.bluepilot.remote.ui.components.LocalQuality3D
import com.bluepilot.remote.ui.components.LocalReduceMotion
import com.bluepilot.remote.ui.components.Quality3D
import com.bluepilot.remote.ui.navigation.BluePilotApp
import com.bluepilot.remote.ui.theme.BluePilotAppTheme
import com.bluepilot.remote.ui.theme.BuiltInThemes
import com.bluepilot.remote.ui.theme.ThemedBackground
import com.bluepilot.remote.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Applies live app settings:
 *  - theme (Light/Dark/System)
 *  - keep screen on
 *  - secure screen (FLAG_SECURE)
 *  - fullscreen (immersive) mode
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** V2 MATRIX 3 finale — ambient light for the light-auto-theme. */
    @javax.inject.Inject
    lateinit var sensors: com.bluepilot.remote.sensors.MotionSensorSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val app by settingsViewModel.app.collectAsState()

            // Section 1 theme engine: resolve the active AppThemeSpec.
            // Light/Dark/System mode maps onto the spec catalog: if the user
            // forces LIGHT but picked a dark spec (or vice versa), we swap to
            // the closest built-in of the requested brightness.
            val systemDark = isSystemInDarkTheme()
            // SECTION 1: auto theme scheduling — when enabled, the hour of
            // day picks day/night theme; re-evaluated every minute.
            var clockHour by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
            }
            androidx.compose.runtime.LaunchedEffect(app.autoThemeEnabled) {
                while (app.autoThemeEnabled) {
                    clockHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    kotlinx.coroutines.delay(60_000)
                }
            }
            val scheduledId = com.bluepilot.remote.ui.theme.ThemeScheduler.scheduledThemeId(
                enabled = app.autoThemeEnabled,
                hour = clockHour,
                nightStart = app.autoNightStart,
                nightEnd = app.autoNightEnd,
                dayTheme = app.autoDayTheme,
                nightTheme = app.autoNightTheme
            )
            // V2 MATRIX 3 finale — ambient-light theme: while enabled (and a
            // real light sensor exists), lux decides day/night with
            // hysteresis and TAKES PRIORITY over clock scheduling. null =
            // no decision yet → fall through to schedule/manual.
            var lightIsDark by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<Boolean?>(null)
            }
            androidx.compose.runtime.LaunchedEffect(app.lightAutoTheme) {
                if (app.lightAutoTheme && sensors.hasLight) {
                    val gate = com.bluepilot.remote.ui.theme.LightThemeGate()
                    sensors.ambientLight().collect { lux ->
                        lightIsDark = gate.decide(lux, lightIsDark ?: false)
                    }
                } else {
                    lightIsDark = null   // disabled → release the override
                }
            }
            val lightThemeId = when (lightIsDark) {
                true -> app.autoNightTheme
                false -> app.autoDayTheme
                null -> null
            }.takeIf { app.lightAutoTheme }
            val baseSpec = BuiltInThemes.byId(lightThemeId ?: scheduledId ?: app.themeId)
            val wantDark = when (app.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            // Family-aware fallback: forcing Light while Hawaii Night is
            // active gives Hawaii Day (not a generic light theme), etc.
            val spec = if (baseSpec.isDark == wantDark) baseSpec
            else BuiltInThemes.counterpart(baseSpec)

            // Apply window-level settings as side effects, restoring on change.
            DisposableEffect(app.keepScreenOn, app.secureScreen, app.fullscreenMode) {
                if (app.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                if (app.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (app.fullscreenMode) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }

            BluePilotAppTheme(spec = spec) {
                val iconPack = runCatching { IconPack.valueOf(app.iconPack) }
                    .getOrDefault(IconPack.ROUNDED)
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalHapticIntensity provides app.hapticIntensity,
                    LocalReduceMotion provides app.reduceMotion,
                    LocalIconPack provides iconPack,
                    // AEROPAD v1.0 #60 — battery-aware: real PowerManager
                    // saver state auto-drops 3D to FLAT (user pref untouched).
                    LocalQuality3D provides run {
                        val pm = getSystemService(android.os.PowerManager::class.java)
                        if (pm?.isPowerSaveMode == true) Quality3D.FLAT
                        else runCatching { Quality3D.valueOf(app.quality3D) }.getOrDefault(Quality3D.FULL)
                    },
                    // V2 PART B — live device tilt from the real gravity
                    // sensor (low-pass smoothed). Off (0,0) under reduce
                    // motion / FLAT quality / no sensor — those paths render
                    // the exact pre-B visuals.
                    com.bluepilot.remote.ui.components.LocalDeviceTilt provides run {
                        val quality = run {
                            val pm = getSystemService(android.os.PowerManager::class.java)
                            if (pm?.isPowerSaveMode == true) Quality3D.FLAT
                            else runCatching { Quality3D.valueOf(app.quality3D) }.getOrDefault(Quality3D.FULL)
                        }
                        val wantTilt = !app.reduceMotion &&
                            quality != Quality3D.FLAT && sensors.hasGravity
                        var tilt by androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(0f to 0f)
                        }
                        androidx.compose.runtime.LaunchedEffect(wantTilt) {
                            if (wantTilt) {
                                var sx = 0f; var sy = 0f
                                sensors.gravity().collect { g ->
                                    val (nx, ny) = com.bluepilot.remote.domain.TiltMath
                                        .normalizedTilt(g.x, g.y)
                                    sx = com.bluepilot.remote.domain.TiltMath.lowPass(sx, nx)
                                    sy = com.bluepilot.remote.domain.TiltMath.lowPass(sy, ny)
                                    tilt = sx to sy
                                }
                            } else {
                                tilt = 0f to 0f
                            }
                        }
                        tilt
                    }
                ) {
                    ThemedBackground {
                        // V2 MATRIX 8 — launcher shortcuts: the intent action
                        // picks the start screen (pure mapping, HOME default).
                        BluePilotApp(
                            startRoute =
                                com.bluepilot.remote.domain.ShortcutActions.routeFor(intent?.action)
                        )
                    }
                }
            }
        }
    }
}
