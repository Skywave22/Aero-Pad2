package com.bluepilot.remote.ui.screens.connection

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.service.HidService
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.ui.components.ConnectionStatusCard
import com.bluepilot.remote.viewmodel.ConnectionViewModel
import timber.log.Timber

/**
 * Connection hub: live status + guided PC pairing steps + discoverability
 * trigger + shortcut to the devices list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsState()
    val context = LocalContext.current

    // Boot engine + foreground service when entering this flow.
    LaunchedEffect(Unit) {
        viewModel.initialize()
        HidService.start(context)
    }

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Timber.i("discoverable result=%d", result.resultCode)
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("PC connection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ConnectionStatusCard(state)
            Spacer(Modifier.height(16.dp))

            // Primary actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        // Make the phone discoverable so the PC can find it.
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        }
                        runCatching { discoverableLauncher.launch(intent) }
                            .onFailure { Timber.e(it, "discoverable intent failed") }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Prepare PC pairing") }

                OutlinedButton(
                    onClick = onOpenDevices,
                    modifier = Modifier.weight(1f)
                ) { Text("Paired devices") }
            }

            if (state is HidConnectionState.Connected) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Disconnect") }
            }

            Spacer(Modifier.height(24.dp))
            PairingGuide()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Step-by-step Windows pairing instructions (carried over from v2, refined). */
@Composable
private fun PairingGuide() {
    val steps = listOf(
        "Tap “Prepare PC pairing” above and accept discoverability.",
        "On Windows: Settings → Bluetooth & devices → Add device → Bluetooth.",
        "Select this phone from the list.",
        "Accept the pairing prompt on BOTH the PC and the phone.",
        "Wait for the status above to show “Connected”.",
        "Test with the Keyboard screen first (press Space), then Mouse.",
        "If pairing is stuck: remove the old pairing from Windows AND from the phone's Bluetooth settings, then retry."
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "How to pair with a PC",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
