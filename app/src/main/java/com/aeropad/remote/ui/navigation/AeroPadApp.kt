package com.aeropad.remote.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.ui.components.GlassDock
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
    Routes.MOUSE to "Mouse",
    Routes.AIR_MOUSE to "Air Mouse",
    Routes.KEYBOARD to "Keyboard",
    Routes.FULL_KEYBOARD to "Full Board",
    Routes.GAMEPAD to "Gamepad",
    Routes.MULTIMEDIA to "Media",
    Routes.PRESENTER to "Presenter",
    Routes.ANDROID to "Android",
    Routes.TOUCH to "Touch",
    Routes.TEXT to "Text & Clips"
)

@Composable
fun AeroPadApp(
    /** V2 MATRIX 8 — start destination override (launcher shortcuts). */
    startRoute: String = Routes.HOME
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
    val dockRoutes = setOf(Routes.HOME, Routes.LAYOUTS, Routes.DEVICES, Routes.SETTINGS)

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

    Box(modifier = Modifier.fillMaxSize()) {

    // SECTION 3D — card-flip screen transitions: incoming screen rotates in
    // around the Y axis (12° -> 0°) with a slide, like turning a card.
    // Under Reduce Motion this collapses to a plain fast fade.
    val reduceMotion = com.aeropad.remote.ui.components.LocalReduceMotion.current
    val flipIn = scaleIn(tween(280), initialScale = 0.92f) + fadeIn(tween(280)) +
        slideInHorizontally(tween(280)) { it / 5 }
    val flipOut = scaleOut(tween(220), targetScale = 0.94f) + fadeOut(tween(200)) +
        slideOutHorizontally(tween(280)) { -it / 5 }
    val flipPopIn = scaleIn(tween(280), initialScale = 0.92f) + fadeIn(tween(280)) +
        slideInHorizontally(tween(280)) { -it / 5 }
    val flipPopOut = scaleOut(tween(220), targetScale = 0.94f) + fadeOut(tween(200)) +
        slideOutHorizontally(tween(280)) { it / 5 }
    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { if (reduceMotion) fadeIn(tween(120)) else flipIn },
        exitTransition = { if (reduceMotion) fadeOut(tween(120)) else flipOut },
        popEnterTransition = { if (reduceMotion) fadeIn(tween(120)) else flipPopIn },
        popExitTransition = { if (reduceMotion) fadeOut(tween(120)) else flipPopOut }
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
                    navController.navigate(Routes.DEVICES) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onOpenConnection = {
                    navController.navigate(Routes.CONNECTION) { launchSingleTop = true }
                }
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

    // Floating glass dock (top-level hubs only)
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
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // A10 — Quick-Switch floating pill between the last two visited control screens.
    val previousControlRoute = if (
        currentRoute in controlScreenLabels && recentControlRoutes.size >= 2
    ) {
        recentControlRoutes.firstOrNull { it != currentRoute }
    } else null
    if (previousControlRoute != null) {
        val label = controlScreenLabels[previousControlRoute] ?: previousControlRoute
        AssistChip(
            onClick = {
                navController.navigate(previousControlRoute) { launchSingleTop = true }
            },
            label = { Text("⇄ $label") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 52.dp)
        )
    }

    // ADV S5 — persistent mini health indicator (real classification),
    // top-right corner during active use; tap opens the dashboard.
    if (currentRoute != Routes.CONNECTION_HEALTH) {
        MiniHealthIndicator(
            onOpen = {
                navController.navigate(Routes.CONNECTION_HEALTH) { launchSingleTop = true }
            },
            modifier = Modifier.align(Alignment.TopEnd)
        )
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

/**
 * ADV S5 — tiny always-available health dot (only rendered when connected).
 * Real classification from measured metrics; tap opens the dashboard.
 */
@Composable
private fun MiniHealthIndicator(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val vm: com.aeropad.remote.viewmodel.ConnectionHealthViewModel = hiltViewModel()
    val connection by vm.connection.collectAsStateWithLifecycle()
    if (!connection.isConnected) return
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val health = com.aeropad.remote.domain.ConnectionHealthTracker.classify(snapshot)
    val color = when (health) {
        com.aeropad.remote.domain.ConnectionHealthTracker.Health.GOOD -> androidx.compose.ui.graphics.Color(0xFF2ECC71)
        com.aeropad.remote.domain.ConnectionHealthTracker.Health.FAIR -> androidx.compose.ui.graphics.Color(0xFFF1C40F)
        com.aeropad.remote.domain.ConnectionHealthTracker.Health.POOR -> androidx.compose.ui.graphics.Color(0xFFE74C3C)
    }
    // CLICK FIX: a 12dp dot is untappable and (worse) blocked taps on
    // whatever sits under it. The dot stays 12dp visually, but its touch
    // area is a proper 44dp circle centered on it.
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .padding(top = 26.dp, end = 0.dp)
            .size(44.dp)
            .clickable { onOpen() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
        )
    }
}
