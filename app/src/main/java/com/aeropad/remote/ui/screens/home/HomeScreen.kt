package com.aeropad.remote.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.ui.components.ConnectionStatusCard
import com.aeropad.remote.ui.components.GelIcon
import com.aeropad.remote.ui.components.toComposeColor
import com.aeropad.remote.ui.navigation.Routes
import com.aeropad.remote.viewmodel.ConnectionViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel

/**
 * Home: live Bluetooth status + one-tap connect + the full control grid.
 *
 * Rebuilt as a clean, sectioned dashboard:
 *  - Status card (tap → connection flow)
 *  - Primary "Connect" action
 *  - Controls / Create / System sections
 */
private data class HomeTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val needsPermissions: Boolean = false,
    /** Accent hue for the tile's icon chip. */
    val tint: Long = 0xFF2F6BFF
)

private val controlTiles = listOf(
    HomeTile("Mouse", "Trackpad and buttons", Icons.Rounded.Mouse, Routes.MOUSE, tint = 0xFF3D8BFF),
    HomeTile("Air Mouse", "Point the phone to move", Icons.Rounded.Mouse, Routes.AIR_MOUSE, tint = 0xFF00C2A8),
    HomeTile("Keyboard", "Shortcuts & text input", Icons.Rounded.Keyboard, Routes.KEYBOARD, tint = 0xFF9B59F6),
    HomeTile("Full Board", "Complete QWERTY keyboard", Icons.Rounded.Keyboard, Routes.FULL_KEYBOARD, tint = 0xFF7B68EE),
    HomeTile("PC Combo", "Trackpad + keyboard together", Icons.Rounded.Computer, Routes.PC_COMBO, tint = 0xFF00A8CC),
    HomeTile("Numpad", "Numeric keypad", Icons.Rounded.Pin, Routes.NUMPAD, tint = 0xFFFF8C42),
    HomeTile("Multimedia", "Media and volume", Icons.Rounded.MusicNote, Routes.MULTIMEDIA, tint = 0xFF17C3CE),
    HomeTile("Presenter", "Slide control", Icons.Rounded.Slideshow, Routes.PRESENTER, tint = 0xFFF5C542),
    HomeTile("Gamepad", "Game controller", Icons.Rounded.Gamepad, Routes.GAMEPAD, tint = 0xFFFF5C8A),
    HomeTile("Android", "Remote for phones & TVs", Icons.Rounded.Android, Routes.ANDROID, tint = 0xFF3DDC84),
    HomeTile("Decks", "App shortcut panels", Icons.Rounded.Keyboard, Routes.SHORTCUTS, tint = 0xFFFF7043),
    HomeTile("Text & Clips", "Snippets & clipboard", Icons.Rounded.ContentCopy, Routes.TEXT, tint = 0xFF26A69A),
    HomeTile("My Layout", "Your custom surface", Icons.Rounded.Dashboard, Routes.MY_LAYOUT, tint = 0xFF2ED5A5)
)

private val createTiles = listOf(
    HomeTile("Layouts", "Custom control screens", Icons.Rounded.Dashboard, Routes.LAYOUTS, tint = 0xFF2ED5A5),
    HomeTile("Macros", "Record & play sequences", Icons.Rounded.Bolt, Routes.MACROS, tint = 0xFF57D163),
    HomeTile("Pad Builder", "Design your own gamepad", Icons.Rounded.SportsEsports, Routes.GAMEPAD_BUILDER, tint = 0xFF6E8BFF)
)

private val systemTiles = listOf(
    HomeTile("Health", "Live connection metrics", Icons.Rounded.MonitorHeart, Routes.CONNECTION_HEALTH, tint = 0xFF00C853),
    HomeTile("Settings", "Tune everything", Icons.Rounded.Settings, Routes.SETTINGS, tint = 0xFF8B9BB5),
    HomeTile("Help", "Pairing & troubleshooting", Icons.AutoMirrored.Rounded.HelpOutline, Routes.HELP, tint = 0xFF64B6F0)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val view = LocalView.current
    // Round 7 — personalized dashboard: hide/show tiles.
    val settingsVm: SettingsViewModel = hiltViewModel()
    val app by settingsVm.app.collectAsState()
    val hidden = remember(app.hiddenTiles) {
        app.hiddenTiles.split(',').filter { it.isNotBlank() }.toSet()
    }
    var showCustomize by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    fun visible(tiles: List<HomeTile>) = tiles.filterNot { it.title in hidden }
    fun hideTile(tile: HomeTile) =
        settingsVm.setHiddenTiles((hidden + tile.title).joinToString(","))
    fun showTile(title: String) =
        settingsVm.setHiddenTiles((hidden - title).joinToString(","))

    // If permissions are already granted, keep the engine warm so the
    // status card is truthful even before visiting the Connection screen.
    LaunchedEffect(Unit) { viewModel.initialize() }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                // ---------- Header ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AeroPad",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Bluetooth keyboard, mouse & gamepad",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "v" + com.aeropad.remote.BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ---------- Live status (tap → connection flow) ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    ConnectionStatusCard(
                        state = state,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (!permissionsGranted) onNavigate(Routes.PERMISSIONS)
                                else onNavigate(Routes.CONNECTION)
                            }
                    )
                }

                // ---------- Primary action ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    val connected = state is HidConnectionState.Connected
                    Button(
                        onClick = {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.KEYBOARD_TAP
                            )
                            if (!permissionsGranted) onNavigate(Routes.PERMISSIONS)
                            else onNavigate(Routes.CONNECTION)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (connected) "Manage connection" else "Connect to a device",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // ---------- Controls ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionHeader("Controls")
                }
                if (hidden.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${hidden.size} tile(s) hidden",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showCustomize = true }) {
                                Text("Customize")
                            }
                        }
                    }
                }

                items(visible(controlTiles)) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) {
                        if (tile.needsPermissions && !permissionsGranted) onNavigate(Routes.PERMISSIONS)
                        else onNavigate(tile.route)
                    }
                }

                // ---------- Create ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionHeader("Create")
                }
                items(visible(createTiles)) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) { onNavigate(tile.route) }
                }

                // ---------- System ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionHeader("System")
                }
                items(visible(systemTiles)) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) { onNavigate(tile.route) }
                }
            }
        }
    }

    // Round 7 — re-show hidden tiles.
    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text("Show tiles") },
            text = {
                Column {
                    hidden.sorted().forEach { title ->
                        TextButton(onClick = { showTile(title) }) {
                            Text("＋ $title")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomize = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 2.dp)
    )
}

/**
 * Tile card with tactile press feedback: scales down to 96% while held.
 * Pure solid surface, tinted icon chip — readable in Light and Dark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTileCard(
    tile: HomeTile,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val reduceMotion = com.aeropad.remote.ui.components.LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.96f else 1f,
        animationSpec = tween(120),
        label = "tileScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            // Clip BEFORE the clickable so the tap ripple stays inside
            // the rounded card — no square corners on press.
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            GelIcon(
                color = tile.tint.toComposeColor(),
                icon = tile.icon,
                contentDescription = tile.title
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
