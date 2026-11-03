package com.aeropad.remote.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.data.tv.WifiTvSession
import com.aeropad.remote.ui.components.statusVisual
import com.aeropad.remote.ui.components.toComposeColor
import com.aeropad.remote.ui.navigation.Routes
import com.aeropad.remote.ui.theme.AeroIcons
import com.aeropad.remote.viewmodel.ConnectionViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Home — live Bluetooth status plus the control deck.
 *
 * Round 19 (v3.6.1) redesign. The previous version put four competing elements
 * above the first control — title, "Customize" chip, a `v3.6.0` chip, a
 * category tagline — then repeated the connection state twice (status card +
 * a 52 dp Connect button), and finished with a rainbow of eight identical
 * Material icons over a horizontally scrolling row of emoji chips. It was
 * functional and it looked machine-assembled. What replaced it:
 *
 *  - brand mark + wordmark + two quiet icon actions; the version number moved
 *    to Settings, where a version number belongs
 *  - ONE status surface that is also the primary action (no duplicate button)
 *  - grouped colour instead of per-tile rainbow: hue encodes the section
 *  - a drawn icon set ([AeroIcons]) instead of `Icons.Rounded.*`
 *  - Quick Tools as a quiet 4×2 strip of icon chips, not emoji
 *
 * Behaviour is unchanged: stable tile IDs for the Customize dialog, long-press
 * to hide with Undo, per-tile routes, screen-reader labels on every tap target.
 */

private data class HomeTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    /** Hue for the tile glyph. Grouped by section — see [ControlsHue] etc. */
    val tint: Long = ControlsHue
)

/**
 * Colour as taxonomy, not decoration.
 *
 * Controls are the app's core and carry the brand hue. Create tools (layout and
 * macro builders) share a green family. System entries stay neutral so they
 * never compete with a control. Within a group the glyph shape does the
 * differentiating — which is why eight wildly different hues are not needed.
 */
private const val ControlsHue = 0xFF3D8BFF
private const val ControlsHueAlt = 0xFF6C8CFF
private const val CreateHue = 0xFF2ED5A5
private const val SystemHue = 0xFF8B9BB5

private val controlTiles = listOf(
    HomeTile(
        id = "mouse",
        title = "Mouse",
        subtitle = "Trackpad, scroll & air mouse",
        icon = AeroIcons.Mouse,
        route = Routes.MOUSE
    ),
    HomeTile(
        id = "keyboard",
        title = "Keyboard",
        subtitle = "Keys, shortcuts & numpad",
        icon = AeroIcons.Keyboard,
        route = Routes.KEYBOARD
    ),
    HomeTile(
        id = "full_keyboard",
        title = "Full Board",
        subtitle = "Complete QWERTY keyboard",
        icon = AeroIcons.FullKeyboard,
        route = Routes.FULL_KEYBOARD,
        tint = ControlsHueAlt
    ),
    HomeTile(
        id = "gamepad",
        title = "Gamepad",
        subtitle = "Pad & motion control",
        icon = AeroIcons.Gamepad,
        route = Routes.GAMEPAD
    ),
    HomeTile(
        id = "multimedia",
        title = "Multimedia",
        subtitle = "Media, volume & transport",
        icon = AeroIcons.Media,
        route = Routes.MULTIMEDIA
    ),
    HomeTile(
        id = "presenter",
        title = "Presenter",
        subtitle = "Slides, laser & timer",
        icon = AeroIcons.Presenter,
        route = Routes.PRESENTER
    ),
    HomeTile(
        id = "wifi_tv_remote",
        title = "Wi-Fi TV Remote",
        subtitle = "HiSilicon TV boxes on your Wi-Fi",
        icon = AeroIcons.Television,
        route = Routes.WIFI_TV_REMOTE
    ),
    HomeTile(
        id = "android_remote",
        title = "Android",
        subtitle = "Remote for phones & TVs",
        icon = AeroIcons.Screen,
        route = Routes.ANDROID
    ),
    HomeTile(
        id = "text_clips",
        title = "Text & Clips",
        subtitle = "Clipboard, snippets & decks",
        icon = AeroIcons.TextLines,
        route = Routes.TEXT
    )
)

private val createTiles = listOf(
    HomeTile(
        id = "layouts",
        title = "Layouts",
        subtitle = "Custom screens, PC Combo & pads",
        icon = AeroIcons.Grid,
        route = Routes.LAYOUTS,
        tint = CreateHue
    ),
    HomeTile(
        id = "macros",
        title = "Macros",
        subtitle = "Record & replay sequences",
        icon = AeroIcons.Bolt,
        route = Routes.MACROS,
        tint = CreateHue
    )
)

private val systemTiles = listOf(
    HomeTile(
        id = "settings",
        title = "Settings",
        subtitle = "Tune everything",
        icon = AeroIcons.Sliders,
        route = Routes.SETTINGS,
        tint = SystemHue
    ),
    HomeTile(
        id = "help",
        title = "Help",
        subtitle = "Pairing & troubleshooting",
        icon = AeroIcons.Help,
        route = Routes.HELP,
        tint = SystemHue
    )
)

private val allDashboardTiles = controlTiles + createTiles + systemTiles

/** Quick Tools — secondary screens that used to be emoji AssistChips. */
private data class QuickTool(val label: String, val icon: ImageVector, val route: String)

private val quickTools = listOf(
    QuickTool("Air Mouse", AeroIcons.AirMouse, Routes.AIR_MOUSE),
    QuickTool("PC Combo", AeroIcons.Combo, Routes.PC_COMBO),
    QuickTool("Pad Builder", AeroIcons.GamepadPlus, Routes.GAMEPAD_BUILDER),
    QuickTool("My Layout", AeroIcons.Frame, Routes.MY_LAYOUT),
    QuickTool("Numpad", AeroIcons.FullKeyboard, Routes.NUMPAD),
    QuickTool("Decks", AeroIcons.Deck, Routes.SHORTCUTS),
    QuickTool("Health", AeroIcons.Pulse, Routes.CONNECTION_HEALTH),
    QuickTool("Devices", AeroIcons.Laptop, Routes.DEVICES)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val wifiTvTarget by WifiTvSession.targetIp.collectAsStateWithLifecycle()
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
    var showCustomize by remember { mutableStateOf(false) }
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

    // If permissions are already granted, keep the engine warm so the status
    // pill is truthful before the user ever visits the Connection screen.
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
                // Round 11 — 2 columns in portrait, more on tablets/foldables.
                columns = GridCells.Fixed(
                    com.aeropad.remote.ui.components.adaptiveColumns(
                        portrait = 2, expanded = 3, wide = 4
                    )
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    // Round 14 — contentInset() covers the dock pill but not the
                    // system navigation bar beneath it, so the final row peeked
                    // through the strip under the pill. Use the real inset
                    // instead of guessing another magic number.
                    bottom = com.aeropad.remote.ui.components.DockMetrics.contentInset() +
                        androidx.compose.foundation.layout.WindowInsets.navigationBars
                            .asPaddingValues().calculateBottomPadding()
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                // ---------- Header ----------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AeroIcons.Mark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AeroPad",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Your controls, in your hand",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                                Icon(
                                    imageVector = AeroIcons.Sliders,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { showCustomize = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Choose dashboard tiles",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StatusPill(
                            state = effectiveState,
                            permissionsGranted = permissionsGranted,
                            onClick = {
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP
                                )
                                if (!permissionsGranted) onNavigate(Routes.PERMISSIONS)
                                else onNavigate(Routes.CONNECTION)
                            }
                        )
                        wifiTvTarget?.let { ip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onNavigate(Routes.WIFI_TV_REMOTE) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AeroIcons.Television,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Wi-Fi TV target", style = MaterialTheme.typography.labelMedium)
                                    Text(ip, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { WifiTvSession.disconnect() }) { Text("Disconnect") }
                            }
                        }
                    }
                }

                // ---------- Controls ----------
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Controls") }
                if (hidden.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${hidden.size} ${if (hidden.size == 1) "tile" else "tiles"} hidden",
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
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Create") }
                items(visible(createTiles), key = { it.id }) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) {
                        onNavigate(tile.route)
                    }
                }

                // ---------- System ----------
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("System") }
                items(visible(systemTiles), key = { it.id }) { tile ->
                    HomeTileCard(tile = tile, onLongPress = { hideTile(tile) }) {
                        onNavigate(tile.route)
                    }
                }

                // ---------- Quick Tools ----------
                // Was a horizontally scrolling row of emoji AssistChips: eight
                // different system emoji, half of them off-screen, on a screen
                // that otherwise has its own visual language. Now a quiet 4×2
                // strip that fits without scrolling on any phone.
                if (quickTools.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Quick tools") }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickTools.chunked(4).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { tool ->
                                        QuickToolChip(
                                            tool = tool,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onNavigate(tool.route) }
                                        )
                                    }
                                    repeat(4 - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
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
                        text = "Toggle which tiles appear on Home (or long-press any tile to hide it):",
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

/**
 * The single connection surface on Home.
 *
 * Replaces the old `ConnectionStatusCard` (a bordered card carrying a headline,
 * a sub-line, a 12 dp dot, an optional warning glyph and — when permissions
 * were missing — a numbered three-step paragraph) plus the 52 dp Connect
 * button underneath it. One row: state dot, state, the one thing to do next,
 * and a chevron. The colour is reserved for state: neutral while idle, green
 * only while genuinely connected, red only on a real failure.
 */
@Composable
private fun StatusPill(
    state: HidConnectionState,
    permissionsGranted: Boolean,
    onClick: () -> Unit
) {
    val visual = statusVisual(state)
    val connected = state is HidConnectionState.Connected
    val trouble = state is HidConnectionState.Error ||
        state is HidConnectionState.HidUnsupported ||
        state is HidConnectionState.BluetoothDisabled ||
        state is HidConnectionState.PermissionMissing

    val actionLabel = when {
        state is HidConnectionState.Connected -> "Manage"
        state is HidConnectionState.Connecting -> "View"
        state is HidConnectionState.PermissionMissing -> "Allow"
        state is HidConnectionState.BluetoothDisabled -> "Turn on"
        state is HidConnectionState.Error || state is HidConnectionState.HidUnsupported -> "Fix"
        else -> "Connect"
    }

    val subtitle = when {
        state is HidConnectionState.PermissionMissing && !permissionsGranted ->
            "Tap to allow Nearby Devices — needed once"
        state is HidConnectionState.PermissionMissing -> "Tap to allow Nearby Devices"
        else -> visual.subtitle
    }

    val container = when {
        connected -> visual.color.copy(alpha = 0.14f)
        trouble -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        connected -> visual.color.copy(alpha = 0.42f)
        trouble -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (trouble) MaterialTheme.colorScheme.error else visual.color)
        )
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = visual.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Round 19 — a section label, not a section banner.
 *
 * The old one was `titleSmall` in the accent colour, competing with the tile
 * titles underneath it. Uppercase micro-type in the muted colour marks the
 * group without shouting.
 */
@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 16.dp, start = 4.dp)
    )
}

/** Compact icon chip for the Quick Tools strip. */
@Composable
private fun QuickToolChip(
    tool: QuickTool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClickLabel = "Open ${tool.label}", onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tile card with tactile press feedback and a cut-corner icon plate that echoes
 * the icon set's geometry. Hairline border so the card reads as an object in
 * light mode, where surface-on-background contrast alone is nearly invisible.
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
        targetValue = if (pressed && !reduceMotion) 0.965f else 1f,
        animationSpec = tween(110),
        label = "tileScale"
    )
    val tint = tile.tint.toComposeColor()
    val plate = RoundedCornerShape(
        topStart = 11.dp,
        topEnd = 11.dp,
        bottomEnd = 11.dp,
        bottomStart = 3.dp
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClickLabel = "Open ${tile.title}",
                onLongClickLabel = "Hide ${tile.title} tile",
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(plate)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // contentDescription = null — the title right below names it.
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.height(11.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            /**
             * Subtitles were 1–3 lines depending on the wording, so tiles in the
             * same row ended at different heights. Pinned to two lines so every
             * card in a row matches.
             */
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
