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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aeropad.remote.ui.components.GlassDock
import com.aeropad.remote.ui.components.OnboardingOverlay
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
import com.aeropad.remote.ui.screens.pccombo.PcComboScreen
import com.aeropad.remote.ui.screens.preview3d.Preview3DScreen
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
 * Routes are type-safe constants in [Routes]. Each module adds its own
 * destinations here (Module 2: permission/connection/devices/scanner,
 * Module 4: control screens, Module 6: layout editor, Module 7: combo profiles).
 */
object Routes {
    const val HOME = "home"
    // Placeholders — implemented in later modules:
    const val PERMISSIONS = "permissions"
    const val CONNECTION = "connection"
    const val DEVICES = "devices"
    const val SCANNER = "scanner"
    const val MOUSE = "mouse"
    const val KEYBOARD = "keyboard"
    const val NUMPAD = "numpad"
    const val MULTIMEDIA = "multimedia"
    const val PRESENTER = "presenter"
    const val GAMEPAD = "gamepad"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val LAYOUTS = "layouts"
    const val LAYOUT_EDITOR = "layout_editor"
    const val COMBO_PROFILES = "combo_profiles"
    const val MACROS = "macros"
    
    const val GAMEPAD_BUILDER = "gamepad_builder"
    /** GTA presets — builder in instant-play mode for a seeded preset. */
    const val GAMEPAD_PRESET = "gamepad_preset"
    const val FULL_KEYBOARD = "full_keyboard"
    const val PC_COMBO = "pc_combo"
    const val AIR_MOUSE = "air_mouse"
    const val PREVIEW_3D = "preview_3d"
    const val CONNECTION_HEALTH = "connection_health"
    }

@Composable
fun AeroPadApp(
    /** V2 MATRIX 8 — start destination override (launcher shortcuts). */
    startRoute: String = Routes.HOME
) {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val appSettings by settingsViewModel.app.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // UI/UX redesign: dock shows only on the four top-level hubs so control
    // surfaces (mouse/keyboard/gamepad...) keep the full screen.
    val dockRoutes = setOf(Routes.HOME, Routes.LAYOUTS, Routes.DEVICES, Routes.SETTINGS)

    // V2 MATRIX 5 b2 — spoken connection alerts: connect/disconnect events
    // are announced through the active screen reader. Uses the platform
    // announceForAccessibility — literally a no-op when TalkBack is off,
    // so there is zero behavior change for sighted users. Toggleable.
    if (appSettings.spokenAlerts) {
        val healthVm: com.aeropad.remote.viewmodel.ConnectionHealthViewModel = hiltViewModel()
        val connState by healthVm.connection.collectAsState()
        val view = androidx.compose.ui.platform.LocalView.current
        var lastAnnounced by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<String?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(connState) {
            val message = when (val s = connState) {
                is com.aeropad.remote.model.HidConnectionState.Connected ->
                    "Connected to ${s.device.name}"
                is com.aeropad.remote.model.HidConnectionState.Error ->
                    "Connection lost"
                else -> null
            }
            // Announce only real transitions (not recompositions/app start
            // replays of the same state).
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
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionScreen(
                onGranted = {
                    navController.navigate(Routes.CONNECTION) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CONNECTION) {
            ConnectionScreen(
                onBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Routes.DEVICES) }
            )
        }
        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                
                onOpen3DPreview = { navController.navigate(Routes.PREVIEW_3D) }
            )
        }
        composable(Routes.MOUSE) { MouseScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.KEYBOARD) {
            KeyboardScreen(
                onBack = { navController.popBackStack() },
                onOpenFullBoard = { navController.navigate(Routes.FULL_KEYBOARD) },
                onOpenPcCombo = { navController.navigate(Routes.PC_COMBO) }
            )
        }
        composable(Routes.NUMPAD) { NumpadScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.MULTIMEDIA) {
            MultimediaScreen(
                onBack = { navController.popBackStack() },
                onOpenPresenter = { navController.navigate(Routes.PRESENTER) },
                onOpenNumpad = { navController.navigate(Routes.NUMPAD) }
            )
        }
        composable(Routes.PRESENTER) { PresenterScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.GAMEPAD) {
            GamepadScreen(
                onBack = { navController.popBackStack() },
                onOpenBuilder = { navController.navigate(Routes.GAMEPAD_BUILDER) },
                onOpenPreset = { key -> navController.navigate("${Routes.GAMEPAD_PRESET}/$key") }
            )
        }
        composable(Routes.CONNECTION_HEALTH) {
            ConnectionHealthScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.LAYOUTS) {
            LayoutsScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("${Routes.LAYOUT_EDITOR}/$id") }
            )
        }
        composable(Routes.MACROS) { MacrosScreen(onBack = { navController.popBackStack() }) }
        
        composable(Routes.GAMEPAD_BUILDER) { GamepadBuilderScreen(onBack = { navController.popBackStack() }) }
        composable(
            route = "${Routes.GAMEPAD_PRESET}/{presetKey}",
            arguments = listOf(navArgument("presetKey") { type = NavType.StringType })
        ) { entry ->
            GamepadBuilderScreen(
                onBack = { navController.popBackStack() },
                presetKey = entry.arguments?.getString("presetKey")
            )
        }
        composable(Routes.FULL_KEYBOARD) { FullKeyboardScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PC_COMBO) { PcComboScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.AIR_MOUSE) { AirMouseScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PREVIEW_3D) { Preview3DScreen(onBack = { navController.popBackStack() }) }
        composable(
            route = "${Routes.LAYOUT_EDITOR}/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            LayoutEditorScreen(onBack = { navController.popBackStack() })
        }
        // Layout editor (Module 6) and combo profiles (Module 7) append here.
    }

    // Floating glass dock — hubs only. UI/UX v2.1: animated slide in/out
    // (auto-hides the moment any control section opens, slides back on
    // return) instead of popping in and out.
    androidx.compose.animation.AnimatedVisibility(
        visible = currentRoute in dockRoutes,
        enter = androidx.compose.animation.slideInVertically(tween(220)) { it } +
            fadeIn(tween(180)),
        exit = androidx.compose.animation.slideOutVertically(tween(180)) { it } +
            fadeOut(tween(140)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        GlassDock(
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }

    // ADV S5 — persistent mini health indicator (real classification),
    // top-right corner during active use; tap opens the dashboard.
    if (currentRoute != Routes.CONNECTION_HEALTH) {
        MiniHealthIndicator(
            onOpen = { navController.navigate(Routes.CONNECTION_HEALTH) },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }

    // UI/UX v2.1 — privacy-blank 👁 button removed per user feedback
    // (cluttered the corner on every screen; feature rarely used).

    // V2 PART A — real FPS overlay (Choreographer-measured); toggle in
    // Settings. Lifecycle-safe: stops measuring the moment it's disabled.
    if (appSettings.fpsOverlay) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            com.aeropad.remote.perf.FrameStats.start()
            onDispose { com.aeropad.remote.perf.FrameStats.stop() }
        }
        val frameStats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
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
    val connection by vm.connection.collectAsState()
    if (!connection.isConnected) return
    val snapshot by vm.snapshot.collectAsState()
    val health = com.aeropad.remote.domain.ConnectionHealthTracker.classify(snapshot)
    val color = when (health) {
        com.aeropad.remote.domain.ConnectionHealthTracker.Health.GOOD -> androidx.compose.ui.graphics.Color(0xFF2ECC71)
        com.aeropad.remote.domain.ConnectionHealthTracker.Health.FAIR -> androidx.compose.ui.graphics.Color(0xFFF1C40F)
        com.aeropad.remote.domain.ConnectionHealthTracker.Health.POOR -> androidx.compose.ui.graphics.Color(0xFFE74C3C)
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .padding(top = 42.dp, end = 10.dp)
            .size(12.dp)
            .background(color, androidx.compose.foundation.shape.CircleShape)
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
            .clickable { onOpen() }
    )
}
