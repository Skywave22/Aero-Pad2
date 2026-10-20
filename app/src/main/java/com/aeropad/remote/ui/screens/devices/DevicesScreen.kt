package com.aeropad.remote.ui.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.RemoteDevice
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.ui.components.ConnectionStatusCard
import com.aeropad.remote.viewmodel.ConnectionViewModel

/**
 * Paired devices + nearby scan results + Per-Host Settings Binding (A4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val bonded by viewModel.bondedDevices.collectAsStateWithLifecycle()
    val hostLayouts: com.aeropad.remote.viewmodel.HostLayoutsViewModel = hiltViewModel()
    val layouts by hostLayouts.layouts.collectAsStateWithLifecycle()
    val hostProfiles by hostLayouts.hostProfiles.collectAsStateWithLifecycle()
    var bindTarget by remember { mutableStateOf<RemoteDevice?>(null) }
    val discovered by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshBondedDevices() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item { ConnectionStatusCard(state) }

            item {
                Text(
                    text = "Paired devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (bonded.isEmpty()) {
                item {
                    Text(
                        text = "No paired devices yet. Use “Prepare PC pairing” on the Connection screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(bonded, key = { "b-" + it.address }) { device ->
                val savedHosts by viewModel.savedHostAddresses.collectAsStateWithLifecycle()
                val boundProfile = hostProfiles.firstOrNull { h -> h.address == device.address }
                val boundLayoutName = layouts.firstOrNull { it.id == boundProfile?.layoutId }?.spec?.name
                DeviceRow(
                    device,
                    connected = (state as? HidConnectionState.Connected)?.device?.address == device.address,
                    saved = device.address in savedHosts,
                    onToggleSave = { viewModel.toggleSavedHost(device) },
                    boundLayoutName = boundLayoutName ?: boundProfile?.let {
                        buildList {
                            if (it.mouseSensitivity >= 0) add("${it.mouseSensitivity}% sens")
                            if (it.keyTapDelayMs >= 0) add("${it.keyTapDelayMs}ms")
                        }.firstOrNull()
                    },
                    onBindLayout = { bindTarget = device }
                ) {
                    viewModel.connect(device.address)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby devices",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(onClick = { viewModel.startScan() }) { Text("Scan") }
                    }
                }
            }
            items(discovered, key = { "d-" + it.address }) { device ->
                DeviceRow(device, connected = false) {
                    viewModel.stopScan()
                    viewModel.connect(device.address)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // A4 — Per-host layout & tuning binding dialog.
    val target = bindTarget
    if (target != null) {
        val current = hostProfiles.firstOrNull { it.address == target.address }
        var selectedLayoutId by remember(current) { mutableStateOf(current?.layoutId ?: 0L) }
        var overrideMouse by remember(current) { mutableStateOf((current?.mouseSensitivity ?: -1) >= 0) }
        var mouseSens by remember(current) {
            mutableIntStateOf((current?.mouseSensitivity?.takeIf { it >= 0 }) ?: 75)
        }
        var overrideKeyDelay by remember(current) { mutableStateOf((current?.keyTapDelayMs ?: -1) >= 0) }
        var keyDelay by remember(current) {
            mutableIntStateOf((current?.keyTapDelayMs?.takeIf { it >= 0 }) ?: 12)
        }
        var selectedGamepadMode by remember(current) { mutableStateOf(current?.gamepadMode.orEmpty()) }
        var selectedThemeMode by remember(current) { mutableStateOf(current?.themeMode.orEmpty()) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bindTarget = null },
            title = { Text("Host profile for ${target.name}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Remembered and auto-applied whenever ${target.name} connects:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Layout", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedLayoutId == 0L, onClick = { selectedLayoutId = 0L })
                        Text("Default")
                    }
                    layouts.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedLayoutId == p.id, onClick = { selectedLayoutId = p.id })
                            Text(p.spec.name)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Override mouse sensitivity", modifier = Modifier.weight(1f))
                        Switch(checked = overrideMouse, onCheckedChange = { overrideMouse = it })
                    }
                    if (overrideMouse) {
                        Text("Mouse sensitivity: $mouseSens%")
                        Slider(
                            value = mouseSens.toFloat(),
                            onValueChange = { mouseSens = it.toInt() },
                            valueRange = 0f..100f
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Override key tap delay", modifier = Modifier.weight(1f))
                        Switch(checked = overrideKeyDelay, onCheckedChange = { overrideKeyDelay = it })
                    }
                    if (overrideKeyDelay) {
                        Text("Key delay: $keyDelay ms (raise for Smart TVs)")
                        Slider(
                            value = keyDelay.toFloat(),
                            onValueChange = { keyDelay = it.toInt() },
                            valueRange = 5f..100f
                        )
                    }

                    Text("Gamepad mode override", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedGamepadMode.isBlank(),
                            onClick = { selectedGamepadMode = "" },
                            label = { Text("Default") }
                        )
                        GamepadMappingMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedGamepadMode == mode.name,
                                onClick = { selectedGamepadMode = mode.name },
                                label = {
                                    Text(
                                        when (mode) {
                                            GamepadMappingMode.HID_GAMEPAD -> "HID Pad"
                                            GamepadMappingMode.KEYBOARD_FALLBACK -> "WASD"
                                            GamepadMappingMode.MOUSE_KEYBOARD -> "Hybrid"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Text("Theme override", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedThemeMode.isBlank(),
                            onClick = { selectedThemeMode = "" },
                            label = { Text("Default") }
                        )
                        ThemeMode.entries.forEach { tm ->
                            FilterChip(
                                selected = selectedThemeMode == tm.name,
                                onClick = { selectedThemeMode = tm.name },
                                label = { Text(tm.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        hostLayouts.bindTuning(
                            address = target.address,
                            layoutId = selectedLayoutId,
                            mouseSensitivity = if (overrideMouse) mouseSens else -1,
                            keyTapDelayMs = if (overrideKeyDelay) keyDelay else -1,
                            gamepadMode = selectedGamepadMode,
                            themeMode = selectedThemeMode
                        )
                        bindTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { bindTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceRow(
    device: RemoteDevice,
    connected: Boolean,
    saved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    boundLayoutName: String? = null,
    onBindLayout: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = device.address + if (connected) "  •  connected" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Round 7 — per-host layout binding (bonded rows only).
            if (onBindLayout != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onBindLayout) {
                        Icon(
                            Icons.Rounded.Dashboard,
                            contentDescription = "Bind layout",
                            tint = if (boundLayoutName != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (boundLayoutName != null) {
                        Text(
                            boundLayoutName.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
            // V2 M4 — quick-switch bookmark (bonded rows only).
            if (onToggleSave != null) {
                IconButton(onClick = onToggleSave) {
                    Text(
                        if (saved) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (saved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
