package com.aeropad.remote

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
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.ui.components.LocalHapticIntensity
import com.aeropad.remote.ui.components.IconPack
import com.aeropad.remote.ui.components.LocalIconPack
import com.aeropad.remote.ui.components.LocalQuality3D
import com.aeropad.remote.ui.components.LocalReduceMotion
import com.aeropad.remote.ui.components.Quality3D
import com.aeropad.remote.ui.navigation.AeroPadApp
import com.aeropad.remote.ui.theme.AeroPadAppTheme
import com.aeropad.remote.ui.theme.BuiltInThemes
import com.aeropad.remote.ui.theme.ThemedBackground
import com.aeropad.remote.viewmodel.SettingsViewModel
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
    lateinit var sensors: com.aeropad.remote.sensors.MotionSensorSource

    /** V2 M4 b2 — opt-in reconnect to the last host on launch. */
    @javax.inject.Inject
    lateinit var autoReconnector: com.aeropad.remote.domain.AutoReconnector

    @javax.inject.Inject
    lateinit var hostProfileStore: com.aeropad.remote.data.hosts.HostProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // V2 M4 b2 — one-shot, fully gated inside (opt-in setting, silent on failure).
        autoReconnector.maybeReconnect()

        // V2 PART B — bind HidService lifecycle to the activity.
        androidx.lifecycle.lifecycleScope.launchWhenStarted {
            com.aeropad.remote.service.HidService.start(this@MainActivity)
        }
        
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val app by settingsViewModel.app.collectAsState()

            androidx.compose.runtime.LaunchedEffect(app.fpsOverlay) {
                if (app.fpsOverlay) {
                    com.aeropad.remote.perf.FrameStats.start()
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            } else {
                    com.aeropad.remote.perf.FrameStats.stop()
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }

            // Section 1 theme engine: resolve the active AppThemeSpec.
            // Light/Dark/System mode maps onto the spec catalog: if the user
            // forces LIGHT but picked a dark spec (or vice versa), we swap to
            // the closest built-in of the requested brightness.
            val systemDark = isSystemInDarkTheme()
            // SECTION 1: auto theme scheduling — when enabled, the hour of
            // day picks day/night theme; re-evaluated every minute.
            var clockHour by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            androidx.compose.runtime.LaunchedEffect(app.autoThemeEnabled) {
                while (app.autoThemeEnabled) {
                    clockHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    kotlinx.coroutines.delay(60_000)
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            val scheduledId = com.aeropad.remote.ui.theme.ThemeScheduler.scheduledThemeId(
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
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            androidx.compose.runtime.LaunchedEffect(app.lightAutoTheme) {
                if (app.lightAutoTheme && sensors.hasLight) {
                    val gate = com.aeropad.remote.ui.theme.LightThemeGate()
                    sensors.ambientLight().collect { lux ->
                        lightIsDark = gate.decide(lux, lightIsDark ?: false)
                    // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            } else {
                    lightIsDark = null   // disabled → release the override
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            val lightThemeId = when (lightIsDark) {
                true -> app.autoNightTheme
                false -> app.autoDayTheme
                null -> null
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }.takeIf { app.lightAutoTheme }
            val baseSpec = BuiltInThemes.byId(lightThemeId ?: scheduledId ?: app.themeId)
            val wantDark = when (app.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            // Family-aware fallback: forcing Light while Hawaii Night is
            // active gives Hawaii Day (not a generic light theme), etc.
            val spec = if (baseSpec.isDark == wantDark) baseSpec
            else BuiltInThemes.counterpart(baseSpec)

            // Apply window-level settings as side effects, restoring on change.
            DisposableEffect(app.keepScreenOn, app.secureScreen, app.fullscreenMode) {
                if (app.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                if (app.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (app.fullscreenMode) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                onDispose { }
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }

            AeroPadAppTheme(spec = spec) {
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
                    // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            },
                    // V2 PART B — live device tilt from the real gravity
                    // sensor (low-pass smoothed). Off (0,0) under reduce
                    // motion / FLAT quality / no sensor — those paths render
                    // the exact pre-B visuals.
                    com.aeropad.remote.ui.components.LocalDeviceTilt provides run {
                        val quality = run {
                            val pm = getSystemService(android.os.PowerManager::class.java)
                            if (pm?.isPowerSaveMode == true) Quality3D.FLAT
                            else runCatching { Quality3D.valueOf(app.quality3D) }.getOrDefault(Quality3D.FULL)
                        // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                        val wantTilt = !app.reduceMotion &&
                            quality != Quality3D.FLAT && sensors.hasGravity
                        var tilt by androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(0f to 0f)
                        // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                        androidx.compose.runtime.LaunchedEffect(wantTilt) {
                            if (wantTilt) {
                                var sx = 0f; var sy = 0f
                                sensors.gravity().collect { g ->
                                    val (nx, ny) = com.aeropad.remote.domain.TiltMath
                                        .normalizedTilt(g.x, g.y)
                                    sx = com.aeropad.remote.domain.TiltMath.lowPass(sx, nx)
                                    sy = com.aeropad.remote.domain.TiltMath.lowPass(sy, ny)
                                    tilt = sx to sy
                                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            } else {
                                tilt = 0f to 0f
                            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                        // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                        tilt
                    // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                ) {
                    ThemedBackground {
                        // V2 MATRIX 8 — launcher shortcuts: the intent action
                        // picks the start screen (pure mapping, HOME default).
                        AeroPadApp(
                            startRoute =
                                com.aeropad.remote.domain.ShortcutActions.routeFor(intent?.action)
                        )
                    // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
                // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }

            }
        }
    }
}
