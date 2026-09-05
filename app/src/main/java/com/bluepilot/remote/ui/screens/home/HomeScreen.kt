package com.bluepilot.remote.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.ui.components.ConnectionStatusCard
import com.bluepilot.remote.ui.components.GelIcon
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.navigation.Routes
import com.bluepilot.remote.ui.components.toComposeColor
import com.bluepilot.remote.ui.theme.LocalAppTheme
import com.bluepilot.remote.viewmodel.ConnectionViewModel

/**
 * UI/UX v2.1 — HOME REDESIGN: 3-tier visual hierarchy.
 *
 * The old Home was a flat 12-tile grid: "Mouse" carried the same visual
 * weight as "Help". Redesigned around FREQUENCY OF USE:
 *
 *  Tier 1  HERO      — live status + the 3 daily drivers (Mouse /
 *                      Keyboard / Gamepad) as tall feature cards.
 *  Tier 2  CONTROLS  — the remaining control surfaces, 2-col grid.
 *  Tier 3  SETUP     — Connect / Health / Themes / Settings /
 *                      Help as compact list rows (half tile height).
 *
 * Result: primary actions reachable in one glance + one tap, secondary
 * content visually quieter, setup chores tucked at the bottom.
 */

private data class Entry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val gel: Long,
    val needsPermissions: Boolean = false
)

// Tier 1 — the three daily drivers.
private val heroEntries = listOf(
    Entry("Mouse", "Trackpad & buttons", Icons.Rounded.Mouse, Routes.MOUSE, 0xFF3D8BFF),
    Entry("Keyboard", "Text, board, combo", Icons.Rounded.Keyboard, Routes.KEYBOARD, 0xFF9B59F6),
    Entry("Gamepad", "Play & build pads", Icons.Rounded.Gamepad, Routes.GAMEPAD, 0xFFFF5C8A)
)

// Tier 2 — remaining control surfaces.
private val controlEntries = listOf(
    Entry("Air Mouse", "Point to move", Icons.Rounded.Mouse, Routes.AIR_MOUSE, 0xFF00C2A8),
    Entry("Multimedia", "Media & volume", Icons.Rounded.MusicNote, Routes.MULTIMEDIA, 0xFF17C3CE),
    Entry("Numpad", "Numeric keypad", Icons.Rounded.Pin, Routes.NUMPAD, 0xFFFF8C42),
    Entry("Presenter", "Slide control", Icons.Rounded.Slideshow, Routes.PRESENTER, 0xFFF5C542),
    Entry("Layouts", "Custom decks", Icons.Rounded.Dashboard, Routes.LAYOUTS, 0xFF2ED5A5),
    Entry("Macros", "Record & play", Icons.Rounded.Bolt, Routes.MACROS, 0xFF57D163)
)

// Tier 3 — setup & tools (compact rows).
private val setupEntries = listOf(
    Entry("Connect", "Pair with a PC or device", Icons.Rounded.Bluetooth, Routes.CONNECTION, 0xFF2F6BFF, needsPermissions = true),
    Entry("Health", "Live connection metrics", Icons.Rounded.MonitorHeart, Routes.CONNECTION_HEALTH, 0xFF00C853),
    Entry("Settings", "Tune everything", Icons.Rounded.Settings, Routes.SETTINGS, 0xFF8B9BB5),
    Entry("Help", "Pairing & troubleshooting", Icons.AutoMirrored.Rounded.HelpOutline, Routes.HELP, 0xFF64B6F0)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val spec = LocalAppTheme.current

    LaunchedEffect(Unit) { viewModel.initialize() }

    fun open(entry: Entry) {
        if (entry.needsPermissions && !permissionsGranted) onNavigate(Routes.PERMISSIONS)
        else onNavigate(entry.route)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            // ---------- Header ----------
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
                        text = if (spec.monoFont) "WIRELESS KEYBOARD, MOUSE & GAMEPAD"
                        else "Wireless keyboard, mouse & gamepad",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassCard(shape = CircleShape, modifier = Modifier.padding(start = 8.dp)) {
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
            Spacer(Modifier.height(14.dp))

            // ---------- Tier 1: status + daily drivers ----------
            // STITCH v3 — the signature Command Orb from designs A/B.
            // LANDSCAPE FIX: orb and hero cards sit SIDE BY SIDE in
            // landscape (stacked they ate the whole viewport height).
            val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape && !spec.monoFont) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CommandOrb(state, modifier = Modifier)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            heroEntries.forEach { entry ->
                                HeroCard(
                                    entry = entry,
                                    modifier = Modifier.weight(1f),
                                    onClick = { open(entry) }
                                )
                            }
                        }
                    }
                }
            } else {
                if (spec.monoFont) {
                    ConnectionStatusCard(state)
                } else {
                    CommandOrb(state)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    heroEntries.forEach { entry ->
                        HeroCard(
                            entry = entry,
                            modifier = Modifier.weight(1f),
                            onClick = { open(entry) }
                        )
                    }
                }
            }

            // ---------- Tier 2: controls ----------
            SectionLabel(if (spec.monoFont) "CONTROLS" else "Controls")
            controlEntries.chunked(2).forEach { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPair.forEach { entry ->
                        ControlCard(
                            entry = entry,
                            modifier = Modifier.weight(1f),
                            onClick = { open(entry) }
                        )
                    }
                    if (rowPair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---------- Tier 3: setup & tools ----------
            SectionLabel(if (spec.monoFont) "SETUP & TOOLS" else "Setup & tools")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    setupEntries.forEachIndexed { i, entry ->
                        SetupRow(entry = entry, onClick = { open(entry) })
                        if (i < setupEntries.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }

            // Space to scroll past floating dock + gesture bar.
            Spacer(Modifier.height(120.dp))
        }
    }
}

/**
 * STITCH v3 — Command Orb: the circular connection hero both generated
 * designs share. Connected = accent ring + soft radial glow + device
 * name; anything else = dim ring + state text. Pure draw-phase glow
 * (radial gradient), no per-frame allocation.
 */
@Composable
private fun CommandOrb(
    state: HidConnectionState,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val spec = LocalAppTheme.current
    val connected = state is HidConnectionState.Connected
    val (title, subtitle) = when (state) {
        is HidConnectionState.Connected -> state.device.name to "Connected"
        is HidConnectionState.Connecting -> "Connecting…" to state.device.name
        HidConnectionState.BluetoothDisabled -> "Bluetooth off" to "Tap Connect below"
        HidConnectionState.PermissionMissing -> "Setup needed" to "Grant permissions"
        is HidConnectionState.Error -> "Not connected" to "Tap Connect below"
        else -> "Not connected" to "Tap Connect below"
    }

    androidx.compose.material3.Surface(
        modifier = modifier.padding(vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        color = if (connected) spec.primary.copy(alpha = 0.1f) else spec.surfaceVariant,
        contentColor = if (connected) spec.primary else spec.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }
    }
}




@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 2.dp)
    )
}

/** Tier 1 — tall feature card: big gel icon, bold title. */
@Composable
private fun HeroCard(entry: Entry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val spec = LocalAppTheme.current
    GlassCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GelIcon(
                color = entry.gel.toComposeColor(),
                icon = entry.icon,
                contentDescription = entry.title,
                size = 52.dp,
                iconSize = 30.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (spec.monoFont) entry.title.uppercase() else entry.title,
                style = if (spec.monoFont)
                    MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tier 2 — standard control tile (icon left, text right = compact). */
@Composable
private fun ControlCard(entry: Entry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val spec = LocalAppTheme.current
    GlassCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GelIcon(
                color = entry.gel.toComposeColor(),
                icon = entry.icon,
                contentDescription = entry.title,
                size = 38.dp,
                iconSize = 22.dp
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = if (spec.monoFont) entry.title.uppercase() else entry.title,
                    style = if (spec.monoFont)
                        MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Tier 3 — compact list row inside one shared card. */
@Composable
private fun SetupRow(entry: Entry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.title,
            tint = entry.gel.toComposeColor(),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = entry.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
