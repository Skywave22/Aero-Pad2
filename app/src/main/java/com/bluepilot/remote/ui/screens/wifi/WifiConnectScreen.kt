package com.bluepilot.remote.ui.screens.wifi

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.viewmodel.WifiConnectViewModel
import com.bluepilot.remote.wifi.WifiEngine
import com.bluepilot.remote.wifi.WifiProtocol

/**
 * AEROPAD v1.0 — WiFi LAN connection screen.
 *
 * Discovery via mDNS/NSD, manual IP fallback, QR-payload paste, PIN
 * confirmation dialog, live status + real measured RTT. Requires the
 * AeroPad companion receiver on the host PC (clearly stated in-UI).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiConnectScreen(
    onBack: () -> Unit,
    viewModel: WifiConnectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val discovered by viewModel.discovered.collectAsState()
    val latency by viewModel.latencyMs.collectAsState()
    val mode by viewModel.mode.collectAsState()

    var manualIp by remember { mutableStateOf("") }
    var qrPayload by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("WiFi Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Transport mode switch ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Input transport", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == com.bluepilot.remote.wifi.TransportManager.Mode.BLUETOOTH,
                            onClick = { viewModel.setMode(com.bluepilot.remote.wifi.TransportManager.Mode.BLUETOOTH) },
                            label = { Text("Bluetooth HID") }
                        )
                        FilterChip(
                            selected = mode == com.bluepilot.remote.wifi.TransportManager.Mode.WIFI,
                            onClick = { viewModel.setMode(com.bluepilot.remote.wifi.TransportManager.Mode.WIFI) },
                            label = { Text("WiFi LAN") }
                        )
                    }
                    Text(
                        "All controls (keyboard, mouse, gamepad, combos) work identically on both transports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- V2 MATRIX 4 — saved hosts (one-tap quick-switch) ----------
            val hostProfiles by viewModel.hostProfiles.collectAsState()
            if (hostProfiles.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Saved hosts", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        // Edit mode toggles ✕ chips (no fragile long-press).
                        var editHosts by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (editHosts) "Tap ✕ to forget a host." else "Tap to switch instantly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { editHosts = !editHosts }) {
                                Text(if (editHosts) "Done" else "Edit")
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hostProfiles.forEach { p ->
                                val icon = if (p.transport ==
                                    com.bluepilot.remote.data.hosts.HostProfile.TRANSPORT_WIFI
                                ) "📶" else "🅱"
                                AssistChip(
                                    onClick = {
                                        if (editHosts) viewModel.removeProfile(p.id)
                                        else viewModel.quickConnect(p)
                                    },
                                    label = {
                                        Text(
                                            (if (editHosts) "✕ " else "$icon ") + p.label.take(16),
                                            color = if (editHosts) MaterialTheme.colorScheme.error
                                            else Color.Unspecified
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Status ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (dotColor, label) = when (val s = state) {
                        is WifiEngine.WifiState.Connected ->
                            Color(0xFF2ECC71) to "Connected — ${s.name} (${s.host})" +
                                (latency?.let { "  •  ${it}ms RTT" } ?: "")
                        is WifiEngine.WifiState.Connecting -> Color(0xFFF1C40F) to "Connecting to ${s.host}…"
                        is WifiEngine.WifiState.AwaitingPin -> Color(0xFFF1C40F) to "Enter the PIN shown on the receiver"
                        is WifiEngine.WifiState.Discovering -> MaterialTheme.colorScheme.primary to "Scanning your network…"
                        is WifiEngine.WifiState.Error -> Color(0xFFE74C3C) to s.message
                        else -> MaterialTheme.colorScheme.outline to "Not connected"
                    }
                    Box(Modifier.size(12.dp).background(dotColor, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (state is WifiEngine.WifiState.Connected) {
                        TextButton(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
                    }
                }
            }

            // ---------- Receiver requirement (honest) ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Requires the AeroPad receiver on your PC",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Run the free companion script (Windows/Mac/Linux) from the AeroPad GitHub repo: " +
                            "python aeropad_receiver.py — it shows a PIN and appears in the list below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Discovered hosts ----------
            Text("Devices on your network", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            if (discovered.isEmpty()) {
                Text(
                    "No receivers found yet — make sure the PC receiver is running on the same WiFi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            discovered.forEach { host ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(host.name, style = MaterialTheme.typography.titleMedium)
                            Text("${host.host}:${host.port}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { viewModel.requestConnect(host.host, host.port) }) {
                            Text("Connect")
                        }
                    }
                }
            }
            OutlinedButton(onClick = { viewModel.startDiscovery() }, modifier = Modifier.fillMaxWidth()) {
                Text("Rescan network")
            }

            // ---------- Manual IP fallback ----------
            Text("Manual connection", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    singleLine = true,
                    placeholder = { Text("IP address e.g. 192.168.1.20") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { viewModel.requestConnect(manualIp.trim()) },
                    enabled = manualIp.isNotBlank()
                ) { Text("Go") }
            }

            // ---------- QR quick pair (paste payload) ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = qrPayload,
                    onValueChange = { qrPayload = it },
                    singleLine = true,
                    placeholder = { Text("Paste QR payload: aeropad://ip:port/pin") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        WifiProtocol.parseQrPayload(qrPayload)?.let { (h, p, pin) ->
                            viewModel.requestConnect(h, p)
                            viewModel.connectWithPin(pin)
                        }
                    },
                    enabled = WifiProtocol.parseQrPayload(qrPayload) != null
                ) { Text("Pair") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- PIN confirmation dialog (security) ----------
    if (state is WifiEngine.WifiState.AwaitingPin) {
        // V2 MATRIX 4 — opt-in PIN save for one-tap reconnect.
        var savePin by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.disconnect() },
            title = { Text("Enter receiver PIN") },
            text = {
                Column {
                    Text(
                        "Type the 6-digit PIN shown by the AeroPad receiver on your PC. " +
                            "This confirms you own both devices.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        singleLine = true,
                        placeholder = { Text("123456") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = savePin,
                            onCheckedChange = { savePin = it }
                        )
                        Text(
                            "Remember this host + PIN (stored on this phone only)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.connectWithPin(pinInput, savePin); pinInput = "" },
                    enabled = pinInput.length == 6
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.disconnect() }) { Text("Cancel") }
            }
        )
    }
}
