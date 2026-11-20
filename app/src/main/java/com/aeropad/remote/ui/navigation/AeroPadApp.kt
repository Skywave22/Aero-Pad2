package com.aeropad.remote.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aeropad.remote.domain.RouteGuard
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.ui.components.GlassDock
import com.aeropad.remote.ui.components.LocalOpenConnection
import com.aeropad.remote.ui.components.isLandscape
import com.aeropad.remote.ui.theme.AeroIcons
import com.aeropad.remote.ui.components.OnboardingOverlay
import com.aeropad.remote.viewmodel.HostLayoutsViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aeropad.remote.ui.screens.connection.ConnectionScreen
import com.aeropad.remote.ui.screens.devices.DevicesScreen
import com.aeropad.remote.ui.screens.home.HomeScreen
import com.aeropad.remote.ui.screens.controls.ControlsScreen
import com.aeropad.remote.ui.screens.permission.PermissionScreen
import com.aeropad.remote.ui.screens.gamepad.GamepadScreen
import com.aeropad.remote.ui.screens.fullkeyboard.FullKeyboardScreen
import com.aeropad.remote.ui.screens.airmouse.AirMouseScreen
import com.aeropad.remote.ui.screens.androidremote.AndroidControlScreen
import com.aeropad.remote.ui.screens.androidremote.TouchControlScreen
import com.aeropad.remote.ui.screens.shortcuts.ShortcutDeckScreen
import com.aeropad.remote.ui.screens.texttools.TextToolsScreen
import com.aeropad.remote.ui.screens.mylayout.MyLayoutScreen
import com.aeropad.remote.ui.screens.pccombo.PcComboScreen
import com.aeropad.remote.ui.screens.gamepadbuilder.GamepadBuilderScreen
import com.aeropad.remote.ui.screens.health.ConnectionHealthScreen
import com.aeropad.remote.ui.screens.help.HelpScreen
import com.aeropad.remote.ui.screens.keyboard.KeyboardScreen
import com.aeropad.remote.ui.screens.editor.LayoutEditorScreen
import com.aeropad.remote.ui.screens.layouts.LayoutsScreen
import com.aeropad.remote.ui.screens.macros.MacrosScreen
import com.aeropad.remote.ui.screens.mouse.MouseScreen
import com.aeropad.remote.ui.screens.multimedia.MultimediaScreen
import com.aeropad.remote.ui.screens.numpad.NumpadScreen
import com.aeropad.remote.ui.screens.presenter.PresenterScreen
import com.aeropad.remote.ui.screens.settings.SettingsScreen

/**
 * Central navigation graph.
 *
 * Dead routes D1 (SCANNER), D2 (COMBO_PROFILES), and R4 (PREVIEW_3D) removed.
 */
object Routes {
    const val HOME = "home"
    const val CONTROLS = "controls"
    const val PERMISSIONS = "permissions"
    const val CONNECTION = "connection"
    const val DEVICES = "devices"
    const val MOUSE = "mouse"
    const val KEYBOARD = "keyboard"
    const val NUMPAD = "numpad"
    const val MULTIMEDIA = "multimedia"
    const val PRESENTER = "presenter"
    const val GAMEPAD = "gamepad"
    const val ANDROID = "android_remote"
    const val TOUCH = "touch_screen"
    const val SHORTCUTS = "shortcut_decks"
    const val TEXT = "text_clips"
    const val MY_LAYOUT = "my_layout"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val LAYOUTS = "layouts"
    const val LAYOUT_EDITOR = "layout_editor"
    const val MACROS = "macros"
    const val GAMEPAD_BUILDER = "gamepad_builder"
    const val FULL_KEYBOARD = "full_keyboard"
    const val PC_COMBO = "pc_combo"
    const val AIR_MOUSE = "air_mouse"
    const val CONNECTION_HEALTH = "connection_health"
}

private val controlScreenLabels = mapOf(
    Routes.MOUSE to "Trackpad",
    Routes.AIR_MOUSE to "Air Mouse",
    Routes.KEYBOARD to "Keyboard",
    Routes.FULL_KEYBOARD to "Full Board",
    Routes.GAMEPAD to "Gamepad",
    Routes.MULTIMEDIA to "Media remote",
    Routes.PRESENTER to "Presenter",
    Routes.ANDROID to "Android",
    Routes.TOUCH to "Touch",
    Routes.TEXT to "Text & Clips",
    Routes.NUMPAD to "Numpad",
    Routes.PC_COMBO to "PC Combo",
    Routes.MY_LAYOUT to "My Layout",
    Routes.SHORTCUTS to "Decks"
)

@Composable
fun AeroPadApp(
    /** V2 MATRIX 8 — start destination override (launcher shortcuts). */
    startRoute: String = Routes.HOME,
    onLeaveDestination: () -> Unit = {}
) {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val hostLayoutsVm: HostLayoutsViewModel = hiltViewModel()
    val healthVm: com.aeropad.remote.viewmodel.ConnectionHealthViewModel = hiltViewModel()
    val appSettings by settingsViewModel.app.collectAsStateWithLifecycle()
    val connState by healthVm.connection.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // A10 — Track the last two visited control screens for one-tap quick-switching.
    val recentControlRoutes = remember { mutableStateListOf<String>() }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute in controlScreenLabels) {
            if (recentControlRoutes.lastOrNull() != currentRoute) {
                recentControlRoutes.remove(currentRoute)
                recentControlRoutes.add(currentRoute)
                while (recentControlRoutes.size > 2) {
                    recentControlRoutes.removeAt(0)
                }
            }
        }
    }

    // UI/UX redesign: dock shows only on the four top-level hubs so control
    // surfaces (mouse/keyboard/gamepad...) keep the full screen.
    val dockRoutes = setOf(Routes.HOME, Routes.CONTROLS, Routes.LAYOUTS, Routes.SETTINGS)

    // A4 + V2 MATRIX 5 b2 — Auto-apply per-host tuning overrides on connect + spoken alerts.
    val view = androidx.compose.ui.platform.LocalView.current
    var lastAnnounced by remember { mutableStateOf<String?>(null) }
    var lastAppliedHost by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(connState) {
        if (connState is HidConnectionState.Connected) {
            val addr = (connState as HidConnectionState.Connected).device.address
            if (addr != lastAppliedHost) {
                lastAppliedHost = addr
                hostLayoutsVm.profileFor(addr)?.let { profile ->
                    if (profile.mouseSensitivity >= 0) {
                        settingsViewModel.setMouseSensitivity(profile.mouseSensitivity)
                    }
                    if (profile.keyTapDelayMs >= 0) {
                        settingsViewModel.setKeyTapDelayMs(profile.keyTapDelayMs)
                    }
                    if (profile.gamepadMode.isNotBlank()) {
                        runCatching { GamepadMappingMode.valueOf(profile.gamepadMode) }
                            .getOrNull()?.let { settingsViewModel.setGamepadMode(it) }
                    }
                    if (profile.themeMode.isNotBlank()) {
                        runCatching { ThemeMode.valueOf(profile.themeMode) }
                            .getOrNull()?.let { settingsViewModel.setTheme(it) }
                    }
                    // F19 — the remaining per-host overrides.
                    if (profile.scrollSpeed >= 0) {
                        settingsViewModel.setScrollSpeed(profile.scrollSpeed)
                    }
                    if (profile.hapticIntensity.isNotBlank()) {
                        runCatching {
                            com.aeropad.remote.model.HapticIntensity.valueOf(profile.hapticIntensity)
                        }.getOrNull()?.let { settingsViewModel.setHapticIntensity(it) }
                    }
                }
            }
        } else {
            lastAppliedHost = null
        }

        if (appSettings.spokenAlerts) {
            val message = when (val s = connState) {
                is HidConnectionState.Connected -> "Connected to ${s.device.name}"
                is HidConnectionState.Error -> "Connection lost"
                else -> null
            }
            if (message != null && message != lastAnnounced) {
                lastAnnounced = message
                view.announceForAccessibility(message)
            }
            if (message == null) lastAnnounced = null
        }
    }

    // Keep Home as the back-stack root even when launched from a shortcut.
    var openedStartRoute by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(navController) {
        if (!openedStartRoute) {
            openedStartRoute = true
            val route = RouteGuard.sanitize(startRoute)
            if (route != Routes.HOME) navController.navigate(route) { launchSingleTop = true }
        }
    }
    val releaseOnLeave by androidx.compose.runtime.rememberUpdatedState(onLeaveDestination)
    DisposableEffect(navController) {
        var previous: androidx.navigation.NavBackStackEntry? = null
        val listener = NavController.OnDestinationChangedListener { controller, _, _ ->
            val next = controller.currentBackStackEntry
            if (previous != null && previous !== next) releaseOnLeave()
            previous = next
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    fun openConnection() {
        val target = if (connState == HidConnectionState.PermissionMissing) Routes.PERMISSIONS else Routes.CONNECTION
        if (navController.previousBackStackEntry?.destination?.route == target) navController.popBackStack()
        else navController.navigate(target) { launchSingleTop = true }
    }

    val previousControlRoute = if (currentRoute in controlScreenLabels) {
        recentControlRoutes.lastOrNull { it != currentRoute }
    } else null
    val showTools = connState.isConnected && currentRoute in controlScreenLabels
    val compact = isLandscape()
    val reduceMotion = com.aeropad.remote.ui.components.LocalReduceMotion.current
    val openHealth: () -> Unit = {
        navController.navigate(Routes.CONNECTION_HEALTH) { launchSingleTop = true }
    }
    val switchControl: (() -> Unit)? = previousControlRoute?.let { target ->
        {
            if (navController.previousBackStackEntry?.destination?.route == target) navController.popBackStack()
            else navController.navigate(target) {
                currentRoute?.let { popUpTo(it) { inclusive = true } }
                launchSingleTop = true
            }
            Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        CompositionLocalProvider(LocalOpenConnection provides ::openConnection) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        enterTransition = { if (reduceMotion) androidx.compose.animation.EnterTransition.None else fadeIn(tween(140)) },
                        exitTransition = { if (reduceMotion) androidx.compose.animation.ExitTransition.None else fadeOut(tween(100)) }
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                // launchSingleTop: a double-tap on a tile must never push the
                                // same destination twice (duplicate back-stack entries made
                                // Back feel broken).
                                onNavigate = { route ->
                                    navController.navigate(route) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.CONTROLS) {
                            ControlsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenDevices = { navController.navigate(Routes.DEVICES) { launchSingleTop = true } },
                                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } }
                            )
                        }
                        composable(Routes.PERMISSIONS) {
                            PermissionScreen(
                                onGranted = {
                                    navController.navigate(Routes.CONNECTION) {
                                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.CONNECTION) {
                            ConnectionScreen(
                                onBack = { navController.popBackStack() },
                                onOpenDevices = {
                                    if (navController.previousBackStackEntry?.destination?.route == Routes.DEVICES) {
                                        navController.popBackStack()
                                    } else navController.navigate(Routes.DEVICES) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.DEVICES) {
                            DevicesScreen(
                                onBack = { navController.popBackStack() },
                                onOpenConnection = ::openConnection
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenHealth = {
                                    navController.navigate(Routes.CONNECTION_HEALTH) { launchSingleTop = true }
                                },
                                onReplayTutorial = {
                                    settingsViewModel.setOnboardingDone(false)
                                }
                            )
                        }
                        composable(Routes.MOUSE) {
                            MouseScreen(
                                onBack = { navController.popBackStack() },
                                onOpenAirMouse = {
                                    navController.navigate(Routes.AIR_MOUSE) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.KEYBOARD) {
                            KeyboardScreen(
                                onBack = { navController.popBackStack() },
                                onOpenFullBoard = {
                                    navController.navigate(Routes.FULL_KEYBOARD) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.NUMPAD) { NumpadScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.MULTIMEDIA) { MultimediaScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.PRESENTER) { PresenterScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.GAMEPAD) {
                            GamepadScreen(
                                onBack = { navController.popBackStack() },
                                onOpenBuilder = {
                                    navController.navigate(Routes.GAMEPAD_BUILDER) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.ANDROID) {
                            AndroidControlScreen(
                                onBack = { navController.popBackStack() },
                                onOpenTouch = {
                                    navController.navigate(Routes.TOUCH) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.TOUCH) { TouchControlScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.SHORTCUTS) { ShortcutDeckScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.TEXT) { TextToolsScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.MY_LAYOUT) { MyLayoutScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.CONNECTION_HEALTH) {
                            ConnectionHealthScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.LAYOUTS) {
                            LayoutsScreen(
                                onBack = { navController.popBackStack() },
                                onEdit = { id -> navController.navigate("${Routes.LAYOUT_EDITOR}/$id") },
                                onOpenGamepadBuilder = {
                                    navController.navigate(Routes.GAMEPAD_BUILDER) { launchSingleTop = true }
                                },
                                onOpenPcCombo = {
                                    navController.navigate(Routes.PC_COMBO) { launchSingleTop = true }
                                },
                                onOpenMyLayout = {
                                    navController.navigate(Routes.MY_LAYOUT) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(Routes.MACROS) { MacrosScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.GAMEPAD_BUILDER) { GamepadBuilderScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.FULL_KEYBOARD) { FullKeyboardScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.PC_COMBO) { PcComboScreen(onBack = { navController.popBackStack() }) }
                        composable(Routes.AIR_MOUSE) { AirMouseScreen(onBack = { navController.popBackStack() }) }
                        composable(
                            route = "${Routes.LAYOUT_EDITOR}/{profileId}",
                            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
                        ) {
                            LayoutEditorScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    if (showTools && !compact) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (switchControl != null && previousControlRoute != null) {
                                TextButton(onClick = switchControl) {
                                    Text("Switch to ${controlScreenLabels.getValue(previousControlRoute)}",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HealthButton(openHealth)
                        }
                    }
                    if (currentRoute in dockRoutes) {
                        GlassDock(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            hapticsEnabled = appSettings.touchVibrations
                        )
                    }
                }
                // Reserve width, not height, in short landscape control windows.
                if (showTools && compact) {
                    Column {
                        HealthButton(openHealth)
                        if (switchControl != null && previousControlRoute != null) {
                            IconButton(onClick = switchControl) {
                                Icon(AeroIcons.Combo, "Switch to ${controlScreenLabels.getValue(previousControlRoute)}")
                            }
                        }
                    }
                }
            }
        }

        // V2 PART A — real FPS overlay (Choreographer-measured); toggle in
        // Settings. Lifecycle-safe: stops measuring the moment it's disabled.
        if (appSettings.fpsOverlay) {
            androidx.compose.runtime.DisposableEffect(Unit) {
                com.aeropad.remote.perf.FrameStats.start()
                onDispose { com.aeropad.remote.perf.FrameStats.stop() }
            }
            val frameStats by com.aeropad.remote.perf.FrameStats.stats.collectAsStateWithLifecycle()
            androidx.compose.material3.Text(
                text = "${frameStats.fps} fps" +
                    if (frameStats.jankPercent > 0) " • ${frameStats.jankPercent}% jank" else "",
                color = when {
                    frameStats.jankPercent > 20 -> androidx.compose.ui.graphics.Color(0xFFE74C3C)
                    frameStats.jankPercent > 5 -> androidx.compose.ui.graphics.Color(0xFFF1C40F)
                    else -> androidx.compose.ui.graphics.Color(0xFF2ECC71)
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 42.dp, start = 12.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                        androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // First-run tutorial overlay
        OnboardingOverlay(
            visible = !appSettings.onboardingDone,
            onFinish = { settingsViewModel.setOnboardingDone() }
        )
    } // Box
}

/** Opens real connection metrics without covering the active control surface. */
@Composable
private fun HealthButton(onOpen: () -> Unit) {
    IconButton(onClick = onOpen) {
        Icon(AeroIcons.Pulse, "Connection health", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
    }
}
