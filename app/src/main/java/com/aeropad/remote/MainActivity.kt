package com.aeropad.remote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.domain.HidController
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.permission.PermissionManager
import com.aeropad.remote.service.HidService
import com.aeropad.remote.ui.components.LocalHapticIntensity
import com.aeropad.remote.ui.components.IconPack
import com.aeropad.remote.ui.components.LocalIconPack
import com.aeropad.remote.ui.components.LocalQuality3D
import com.aeropad.remote.ui.components.LocalReduceMotion
import com.aeropad.remote.ui.components.Quality3D
import com.aeropad.remote.ui.navigation.AeroPadApp
import com.aeropad.remote.ui.theme.AeroPadAppTheme
import com.aeropad.remote.ui.theme.AeroPadSpecs
import com.aeropad.remote.ui.theme.ThemedBackground
import com.aeropad.remote.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Round 14 — audit hook: a debug build can be launched straight onto any route
 * via `am start --es com.aeropad.remote.EXTRA_ROUTE <route>`.
 */
private const val EXTRA_ROUTE = "com.aeropad.remote.EXTRA_ROUTE"

/**
 * Single-activity host. Applies live app settings:
 *  - appearance (Light / Dark / Follow system — the ONLY appearance choice)
 *  - keep screen on
 *  - secure screen (FLAG_SECURE)
 *  - fullscreen (immersive) mode
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Motion sensors: gravity for the subtle device-tilt parallax. */
    @javax.inject.Inject
    lateinit var sensors: com.aeropad.remote.sensors.MotionSensorSource

    @javax.inject.Inject lateinit var hidController: HidController
    @javax.inject.Inject lateinit var permissions: PermissionManager

    override fun onResume() {
        super.onResume()
        if (permissions.hasAllBluetoothPermissions()) HidService.start(this)
        else hidController.start() // publishes PermissionMissing rather than stale Connected
    }

    override fun onPause() {
        hidController.releaseInputs()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        hidController.releaseInputs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val app by settingsViewModel.app.collectAsStateWithLifecycle()

            // Pure two-mode appearance: Light / Dark / Follow system.
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (app.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            val themeSpec = AeroPadSpecs.accented(AeroPadSpecs.styled(app.uiStyle, darkTheme), app.accentColor)
            // Apply window-level settings as side effects, restoring on change.
            DisposableEffect(app.keepScreenOn, app.secureScreen, app.fullscreenMode, themeSpec.isDark) {
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
                controller.isAppearanceLightStatusBars = !themeSpec.isDark
                controller.isAppearanceLightNavigationBars = !themeSpec.isDark
                if (app.fullscreenMode) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides app.reduceMotion) {
                AeroPadAppTheme(spec = themeSpec) {
                    val iconPack = runCatching { IconPack.valueOf(app.iconPack) }
                        .getOrDefault(IconPack.ROUNDED)
                    val powerManager = getSystemService(android.os.PowerManager::class.java)
                    val quality = if (powerManager?.isPowerSaveMode == true) Quality3D.FLAT
                    else runCatching { Quality3D.valueOf(app.quality3D) }.getOrDefault(Quality3D.FULL)
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalHapticIntensity provides app.hapticIntensity,
                        LocalIconPack provides iconPack,
                        // Battery-aware: real PowerManager saver state
                        // auto-drops 3D to FLAT (user pref untouched).
                        LocalQuality3D provides quality,
                        com.aeropad.remote.ui.components.LocalDeviceTilt provides run {
                            val wantTilt = !app.reduceMotion &&
                                quality != Quality3D.FLAT && sensors.hasGravity
                            var tilt by androidx.compose.runtime.remember {
                                androidx.compose.runtime.mutableStateOf(0f to 0f)
                            }
                            androidx.compose.runtime.LaunchedEffect(lifecycle, wantTilt) {
                                if (!wantTilt) {
                                    tilt = 0f to 0f
                                    return@LaunchedEffect
                                }
                                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                    var sx = 0f
                                    var sy = 0f
                                    try {
                                        sensors.gravity().collect { g ->
                                            val (nx, ny) = com.aeropad.remote.domain.TiltMath.normalizedTilt(g.x, g.y)
                                            sx = com.aeropad.remote.domain.TiltMath.lowPass(sx, nx)
                                            sy = com.aeropad.remote.domain.TiltMath.lowPass(sy, ny)
                                            tilt = sx to sy
                                        }
                                    } finally {
                                        tilt = 0f to 0f
                                    }
                                }
                            }
                            tilt
                        }
                    ) {
                        ThemedBackground {
                            // Launcher shortcuts: the intent action picks the
                            // start screen (pure mapping, HOME default).
                            // Round 14 - automated audit hook. Debug builds accept an explicit
                            // route extra so a test harness can drive every screen directly:
                            //   adb shell am start -n com.aeropad.remote/.MainActivity \
                            //     --es com.aeropad.remote.EXTRA_ROUTE settings
                            // Release builds ignore the extra entirely.
                            val auditedRoute = if (com.aeropad.remote.BuildConfig.DEBUG) {
                                intent?.getStringExtra(EXTRA_ROUTE)
                            } else {
                                null
                            }
                            AeroPadApp(
                                startRoute = com.aeropad.remote.domain.RouteGuard.sanitize(
                                    auditedRoute
                                        ?: com.aeropad.remote.domain.ShortcutActions.routeFor(intent?.action)
                                ),
                                onLeaveDestination = hidController::releaseInputs
                            )
                        }
                    }
                }
            }
        }
    }
}
