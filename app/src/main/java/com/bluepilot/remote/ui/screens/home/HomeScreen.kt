package com.bluepilot.remote.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.ui.components.ConnectionStatusCard
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.components.GelIcon
import com.bluepilot.remote.ui.navigation.Routes
import com.bluepilot.remote.ui.theme.LocalAppTheme
import com.bluepilot.remote.viewmodel.ConnectionViewModel

/**
 * Home: live connection status + navigation grid.
 *
 * The Connect tile routes through the permission screen when permissions
 * are missing; control tiles activate as their modules are built.
 */
private data class HomeTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String?,          // null = not built yet (disabled)
    val needsPermissions: Boolean = false,
    /** Gel icon color (designs/glass-01: every tile has its own vivid hue). */
    val gel: Long = 0xFF2F6BFF
)

private val tiles = listOf(
    HomeTile("Connect", "Pair with a PC or device", Icons.Rounded.Bluetooth, Routes.CONNECTION, needsPermissions = true, gel = 0xFF2F6BFF),
    HomeTile("Mouse", "Trackpad and buttons", Icons.Rounded.Mouse, Routes.MOUSE, gel = 0xFF3D8BFF),
    HomeTile("Air Mouse", "Point the phone to move", Icons.Rounded.Mouse, Routes.AIR_MOUSE, gel = 0xFF00C2A8),
    HomeTile("Keyboard", "Shortcuts & text input", Icons.Rounded.Keyboard, Routes.KEYBOARD, gel = 0xFF9B59F6),
    HomeTile("Full Board", "Complete QWERTY keyboard", Icons.Rounded.Keyboard, Routes.FULL_KEYBOARD, gel = 0xFF7B68EE),
    HomeTile("PC Combo", "Trackpad + keyboard together", Icons.Rounded.Computer, Routes.PC_COMBO, gel = 0xFF00A8CC),
    HomeTile("Numpad", "Numeric keypad", Icons.Rounded.Pin, Routes.NUMPAD, gel = 0xFFFF8C42),
    HomeTile("Multimedia", "Media and volume", Icons.Rounded.MusicNote, Routes.MULTIMEDIA, gel = 0xFF17C3CE),
    HomeTile("Presenter", "Slide control", Icons.Rounded.Slideshow, Routes.PRESENTER, gel = 0xFFF5C542),
    HomeTile("Gamepad", "Game controller", Icons.Rounded.Gamepad, Routes.GAMEPAD, gel = 0xFFFF5C8A),
    HomeTile("Layouts", "Custom control screens", Icons.Rounded.Dashboard, Routes.LAYOUTS, gel = 0xFF2ED5A5),
    HomeTile("Macros", "Record & play sequences", Icons.Rounded.Bolt, Routes.MACROS, gel = 0xFF57D163),
    HomeTile("Themes", "Change the whole look", Icons.Rounded.Palette, Routes.THEMES, gel = 0xFFB86BFF),
    HomeTile("Pad Builder", "Design your own gamepad", Icons.Rounded.SportsEsports, Routes.GAMEPAD_BUILDER, gel = 0xFF6E8BFF),
    HomeTile("WiFi Control", "NEW • control over your network", Icons.Rounded.Wifi, Routes.WIFI_CONNECT, gel = 0xFF00B0FF),
    HomeTile("Health", "Live connection metrics", Icons.Rounded.MonitorHeart, Routes.CONNECTION_HEALTH, gel = 0xFF00C853),
    HomeTile("Settings", "Tune everything", Icons.Rounded.Settings, Routes.SETTINGS, gel = 0xFF8B9BB5),
    HomeTile("Help", "Pairing & troubleshooting", Icons.AutoMirrored.Rounded.HelpOutline, Routes.HELP, gel = 0xFF64B6F0)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val spec = LocalAppTheme.current

    // If permissions are already granted, keep the engine warm so the
    // status card is truthful even before visiting the Connection screen.
    LaunchedEffect(Unit) { viewModel.initialize() }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                // Title and Version Chip Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (spec.monoFont) "AEROPAD" else "AeroPad",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (spec.monoFont) "WIRELESS KEYBOARD, MOUSE & GAMEPAD" else "Wireless keyboard, mouse & gamepad",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Version Chip — reads BuildConfig so CI builds show their real version
                    GlassCard(
                        shape = CircleShape,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = com.bluepilot.remote.BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = if (spec.monoFont) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = spec.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                ConnectionStatusCard(state)

                Spacer(Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 128.dp), // Space to scroll past floating dock + gesture bar
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tiles) { tile ->
                        HomeTileCard(
                            tile = tile,
                            enabled = tile.route != null,
                            onClick = {
                                val route = tile.route ?: return@HomeTileCard
                                if (tile.needsPermissions && !permissionsGranted) {
                                    onNavigate(Routes.PERMISSIONS)
                                } else {
                                    onNavigate(route)
                                }
                            }
                        )
                    }
                }
            }
            // NOTE: the old in-screen dock was removed — the app-level
            // GlassDock (BluePilotApp) is the single bottom dock now.
            // Having both stacked caused ghost icons to peek out behind
            // the floating dock (bug reported via screenshot).
        }
    }
}

@Composable
private fun HomeTileCard(tile: HomeTile, enabled: Boolean, onClick: () -> Unit) {
    // SECTION 2 - 3D: idle float loop + press-lift. Cards gently hover
    // (staggered by title hash so they do not move in sync) and sink with
    // pressDepth3D on touch. Off under Reduce Motion / FLAT quality.
    val reduceMotion = com.bluepilot.remote.ui.components.LocalReduceMotion.current ||
        com.bluepilot.remote.ui.components.LocalQuality3D.current == com.bluepilot.remote.ui.components.Quality3D.FLAT
    val floatAnim = rememberInfiniteTransition(label = "tileFloat")
    // SECTION 5 PERF FIX: keep the animated value as State and read it
    // inside graphicsLayer (draw phase). Reading `phase` directly here
    // recomposed every tile on every animation frame (16 tiles × 60fps).
    val phaseState = floatAnim.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3600 + (tile.title.hashCode() and 0x3FF), easing = androidx.compose.animation.core.LinearEasing),
            RepeatMode.Restart
        ), label = "tilePhase"
    )

    val spec = LocalAppTheme.current
    val gel = tile.gel.toComposeColor()

    GlassCard(
        modifier = Modifier
            .androidGraphicsFloat(phaseState, !reduceMotion)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            GelIcon(
                color = gel,
                icon = tile.icon,
                contentDescription = tile.title,
                enabled = enabled
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (spec.monoFont) tile.title.uppercase() else tile.title,
                style = if (spec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (enabled) tile.subtitle else "Coming in a later module",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * SECTION 2 - idle 3D float: gentle vertical bob via graphicsLayer.
 * SECTION 5 PERF: takes State<Float> so the per-frame value is read in
 * the draw phase only — animating costs zero recompositions.
 */
private fun androidx.compose.ui.Modifier.androidGraphicsFloat(
    phase: androidx.compose.runtime.State<Float>, enabled: Boolean
): androidx.compose.ui.Modifier =
    if (!enabled) this else this.then(
        androidx.compose.ui.Modifier.graphicsLayer {
            translationY = kotlin.math.sin(phase.value * 2f * Math.PI).toFloat() * 3f * density
        }
    )
