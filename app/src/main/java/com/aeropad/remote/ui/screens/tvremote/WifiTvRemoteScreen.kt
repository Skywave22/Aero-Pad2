package com.aeropad.remote.ui.screens.tvremote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aeropad.remote.data.tv.PhoneRemoteProtocol
import com.aeropad.remote.data.tv.WifiTv
import com.aeropad.remote.data.tv.WifiTvRemoteClient
import com.aeropad.remote.ui.components.KeyCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Independent Wi-Fi TV remote. Existing Bluetooth HID functions are untouched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTvRemoteScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { WifiTvRemoteClient() }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var selectedIp by rememberSaveable { mutableStateOf("") }
    var devices by remember { mutableStateOf(emptyList<WifiTv>()) }
    var scanning by remember { mutableStateOf(false) }
    var mouseMode by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    fun launchCommand(command: String) {
        if (selectedIp.isBlank()) {
            notice = "Select a TV first."
            return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.sendRemoteCommand(command) } }
                .onFailure { notice = it.message ?: "Could not send the command." }
        }
    }

    fun connect(ip: String) {
        val normalized = ip.trim()
        if (!PhoneRemoteProtocol.isIpv4(normalized)) {
            notice = "Enter a valid IPv4 address, for example 192.168.1.40."
            return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.connect(normalized) } }
                .onSuccess {
                    selectedIp = normalized
                    manualIp = normalized
                    notice = "Remote commands are being sent to $normalized."
                }
                .onFailure { notice = it.message ?: "Could not reach the TV over Wi-Fi." }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi TV Remote") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !scanning,
                        onClick = {
                            scanning = true
                            notice = null
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { client.discover() } }
                                    .onSuccess {
                                        devices = it
                                        notice = if (it.isEmpty()) {
                                            "No compatible TV replied. Confirm same Wi-Fi, then try its IP below."
                                        } else "Found ${it.size} compatible device${if (it.size == 1) "" else "s"}."
                                    }
                                    .onFailure { notice = it.message ?: "TV discovery failed." }
                                scanning = false
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Scan Wi-Fi for TVs")
                    }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Works with PhoneRemote-compatible HiSilicon TV boxes on your local Wi-Fi. This adds a TV remote alongside AeroPad’s existing Bluetooth controls; it does not replace them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (selectedIp.isBlank()) "No TV selected" else "Selected TV · $selectedIp", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it.take(15) },
                            modifier = Modifier.weight(1f),
                            label = { Text("TV IP address") },
                            singleLine = true,
                            placeholder = { Text("192.168.1.40") }
                        )
                        Button(onClick = { connect(manualIp) }) { Text("Connect") }
                    }
                    if (scanning) Text("Scanning the local network…", color = MaterialTheme.colorScheme.primary)
                    devices.forEach { tv ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tv.label + " · " + tv.address, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { connect(tv.address) }) { Text("Use TV") }
                        }
                    }
                }
            }

            notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Text("Navigation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteKey("▲", enabled = selectedIp.isNotBlank()) { launchCommand("19") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteKey("◀", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("21") }
                    RemoteKey("OK", Modifier.weight(1f), selectedIp.isNotBlank(), emphasized = true) { launchCommand("23") }
                    RemoteKey("▶", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("22") }
                }
                RemoteKey("▼", enabled = selectedIp.isNotBlank()) { launchCommand("20") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKey("Back", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("4") }
                RemoteKey("Home", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("3") }
                RemoteKey("Menu", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("2005") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKey("Vol −", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("25") }
                RemoteKey("Mute", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("164") }
                RemoteKey("Vol +", Modifier.weight(1f), selectedIp.isNotBlank()) { launchCommand("24") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Air mouse", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(
                    enabled = selectedIp.isNotBlank(),
                    onClick = {
                        mouseMode = !mouseMode
                        launchCommand("mousemode=$mouseMode")
                    }
                ) { Text(if (mouseMode) "Turn off" else "Turn on") }
            }
            if (mouseMode && selectedIp.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().pointerInput(selectedIp) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val dx = drag.x.toInt()
                                val dy = drag.y.toInt()
                                if (dx != 0 || dy != 0) launchCommand("key=mouseaction;$dx=$dy")
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Drag to move; use Left click below", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                RemoteKey("Left click", modifier = Modifier.fillMaxWidth(), enabled = selectedIp.isNotBlank()) {
                    launchCommand("key=clickmouse")
                }
            }

            Text("TV search", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search text") },
                    singleLine = true
                )
                Button(enabled = selectedIp.isNotBlank() && searchText.isNotBlank(), onClick = {
                    launchCommand("key=search=$searchText")
                }) { Text("Send") }
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "Both devices must be on the same non-isolated Wi-Fi. Discovery and control use the TV’s legacy UDP protocol on port 7899; it has no authentication or encryption, so use only on a trusted home network. Generic Android/Google TVs are not guaranteed to support it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RemoteKey(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    KeyCard(label = label, modifier = modifier, height = 54.dp, enabled = enabled, emphasized = emphasized, onClick = onClick)
}
