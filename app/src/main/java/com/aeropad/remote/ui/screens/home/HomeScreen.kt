package com.aeropad.remote.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.ui.components.ConnectionStatusCard
import com.aeropad.remote.ui.components.GelIcon
import com.aeropad.remote.ui.components.toComposeColor
import com.aeropad.remote.ui.navigation.Routes
import com.aeropad.remote.viewmodel.ConnectionViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Home: live Bluetooth status + one-tap connect + the streamlined control grid.
 *
 * Includes:
 *  - Stable tile IDs for hidden-tile persistence (M3)
 *  - Undo Snackbar on long-press hide (M2) + permanent Customize chip in header (P9.7)
 *  - Screen-reader semantics & non-duplicate icon descriptions (M4)
 *  - Consolidated 12-tile dashboard (R1–R7)
 */
private data class HomeTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    /** Accent hue for the tile's icon chip. */
    val tint: Long = 0xFF2F6BFF
)

private val controlTiles = listOf(
    HomeTile("mouse", "Mouse", "Trackpad, scroll & air mouse", Icons.Rounded.Mouse, Routes.MOUSE, tint = 0xFF3D8BFF),
    HomeTile("keyboard", "Keyboard", "Shortcuts, numpad & text", Icons.Rounded.Keyboard, Routes.KEYBOARD, tint = 0xFF9B59F6),
    HomeTile("full_keyboard", "Full Board", "Complete QWERTY keyboard", Icons.Rounded.Keyboard, Routes.FULL_KEYBOARD, tint = 0xFF7B68EE),
    HomeTile("gamepad", "Gamepad", "Game controller & gyro", Icons.Rounded.Gamepad, Routes.GAMEPAD, tint = 0xFFFF5C8A),
    HomeTile("multimedia", "Multimedia", "Media and volume", Icons.Rounded.MusicNote, Routes.MULTIMEDIA, tint = 0xFF17C3CE),
    HomeTile("presenter", "Presenter", "Slide control & timer", Icons.Rounded.Slideshow, Routes.PRESENTER, tint = 0xFFF5C542),
    HomeTile("android_remote", "Android", "Remote for phones & TVs", Icons.Rounded.Android, Routes.ANDROID, tint = 0xFF3DDC84),
    HomeTile("text_clips", "Text & Clips", "Clipboard, snippets & decks", Icons.Rounded.ContentCopy, Routes.TEXT, tint = 0xFF26A69A)
)

private val createTiles = listOf(
    HomeTile("layouts", "Layouts", "Custom screens, PC Combo & Pads", Icons.Rounded.Dashboard, Routes.LAYOUTS, tint = 0xFF2ED5A5),
    HomeTile("macros", "Macros", "Record & play sequences", Icons.Rounded.Bolt, Routes.MACROS, tint = 0xFF57D163)
)

private val systemTiles = listOf(
    HomeTile("settings", "Settings", "Tune everything", Icons.Rounded.Settings, Routes.SETTINGS, tint = 0xFF8B9BB5),
    HomeTile("help", "Help", "Pairing & troubleshooting", Icons.AutoMirrored.Rounded.HelpOutline, Routes.HELP, tint = 0xFF64B6F0)
)

private val allDashboardTiles = controlTiles + createTiles + systemTiles

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val permissionsGranted by viewModel.permissionsGranted.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onPermissionsResult()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectiveState = if (!permissionsGranted && state is HidConnectionState.Idle) {
        HidConnectionState.PermissionMissing
    } else {
        state
    }

    // M2 + M3 + P9.7 — personalized dashboard keyed by stable HomeTile.id.
    val settingsVm: SettingsViewModel = hiltViewModel()
    val app by settingsVm.app.collectAsStateWithLifecycle()
    val hidden = remember(app.hiddenTiles) {
        app.hiddenTiles.split(',').filter { it.isNotBlank() }.toSet()
    }
    var showCustomize by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    fun isHidden(tile: HomeTile): Boolean =
        tile.id in hidden || tile.title in hidden
    fun visible(tiles: List<HomeTile>) = tiles.filterNot(::isHidden)
    fun hideTile(tile: HomeTile) {
        val next = (hidden - tile.title) + tile.id
        settingsVm.setHiddenTiles(next.joinToString(","))
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "Hidden '${tile.title}' tile",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                settingsVm.setHiddenTiles((next - tile.id - tile.title).joinToString(","))
            }
        }
    }
    fun toggleTile(tile: HomeTile, show: Boolean) {
        val cleaned = hidden - tile.id - tile.title
        val next = if (show) cleaned else (cleaned + tile.id)
        settingsVm.setHiddenTiles(next.joinToString(","))
    }

    // If permissions are already granted, keep the engine warm so the
    // status card is truthful even before visiting the Connection screen.
    LaunchedEffect(permissionsGranted) { viewModel.initialize() }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
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
                        Column(modifier = Modifier.weight(1f)) {
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // P9.7 — Permanent Customize button in the header.
                            AssistChip(
                                onClick = { showCustomize = true },
                                label = { Text("Customize") }
                            )
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
                }

                // ---------- Live status (tap → connection flow) ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    ConnectionStatusCard(
                        state = effectiveState,
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
                                Text("Show / Hide")
                            }
                        }
                    }
                }

                items(visible(controlTiles), key = { it.id }) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) {
                        onNavigate(tile.route)
                    }
                }

                // ---------- Create ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionHeader("Create")
                }
                items(visible(createTiles), key = { it.id }) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) { onNavigate(tile.route) }
                }

                // ---------- System ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionHeader("System")
                }
                items(visible(systemTiles), key = { it.id }) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) { onNavigate(tile.route) }
                }

                // ---------- Quick Tools (1-tap access to secondary & builder screens) ----------
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Column {
                        SectionHeader("Quick Tools")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { onNavigate(Routes.AIR_MOUSE) },
                                label = { Text("🎯 Air Mouse") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.PC_COMBO) },
                                label = { Text("⚡ PC Combo") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.GAMEPAD_BUILDER) },
                                label = { Text("🎮 Pad Builder") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.MY_LAYOUT) },
                                label = { Text("🧩 My Layout") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.NUMPAD) },
                                label = { Text("🔢 Numpad") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.SHORTCUTS) },
                                label = { Text("🎛️ Decks") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.CONNECTION_HEALTH) },
                                label = { Text("💓 Health") }
                            )
                            AssistChip(
                                onClick = { onNavigate(Routes.DEVICES) },
                                label = { Text("💻 Devices") }
                            )
                        }
                    }
                }
            }
        }
    }

    // P9.7 + M2 + M3 — Customize dashboard dialog: toggle any tile on/off.
    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text("Customize dashboard") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Toggle which tiles appear on Home (or long-press any tile to hide it):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    allDashboardTiles.forEach { tile ->
                        val shown = !isHidden(tile)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tile.title, modifier = Modifier.weight(1f))
                            Switch(
                                checked = shown,
                                onCheckedChange = { toggleTile(tile, it) }
                            )
                        }
                    }
                }
            },
            dismissButton = {
                if (hidden.isNotEmpty()) {
                    TextButton(onClick = { settingsVm.setHiddenTiles("") }) {
                        Text("Reset all")
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
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClickLabel = "Open ${tile.title}",
                onLongClickLabel = "Hide ${tile.title} tile",
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // M4 — contentDescription = null because the tile title and subtitle are right below.
            GelIcon(
                color = tile.tint.toComposeColor(),
                icon = tile.icon,
                contentDescription = null
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
