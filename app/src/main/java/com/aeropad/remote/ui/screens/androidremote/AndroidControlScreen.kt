package com.aeropad.remote.ui.screens.androidremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.HidConsumer
import com.aeropad.remote.model.HidKeyCodes
import com.aeropad.remote.model.HidSystem
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel

/**
 * Android Remote — dedicated controls for Android hosts (phones, tablets,
 * Android TV) over Bluetooth HID.
 *
 * Mapping (all real HID, no companion app needed on the host):
 *  - Home     → consumer AC Home (0x223)
 *  - Back     → consumer AC Back (0x224)
 *  - Recents  → Alt+Tab (Android switches apps with a hardware keyboard)
 *  - Power    → system page power-down bit
 *  - Volume/media → standard consumer usages
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidControlScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Android Remote") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NotConnectedBanner(!isConnected)

            Section("Navigation")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("← Back", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.mediaTap(HidConsumer.AC_BACK)
                }
                KeyCard("⌂ Home", Modifier.weight(1f), 64.dp, emphasized = true) {
                    haptic(); viewModel.mediaTap(HidConsumer.AC_HOME)
                }
                KeyCard("▭ Recents", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.keyTap(HidKeyCodes.TAB, HidKeyCodes.LEFT_ALT)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("⏻ Power", Modifier.weight(1f), 56.dp) {
                    haptic(); viewModel.systemTap(HidSystem.POWER)
                }
            }

            Section("Volume")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("Vol −", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.mediaTap(HidConsumer.VOLUME_DOWN)
                }
                KeyCard("Mute", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.mediaTap(HidConsumer.MUTE)
                }
                KeyCard("Vol +", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.mediaTap(HidConsumer.VOLUME_UP)
                }
            }

            Section("Media")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("⏮ Prev", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.mediaTap(HidConsumer.PREV_TRACK)
                }
                KeyCard("⏯ Play/Pause", Modifier.weight(1.4f), 64.dp, emphasized = true) {
                    haptic(); viewModel.mediaTap(HidConsumer.PLAY_PAUSE)
                }
                KeyCard("⏭ Next", Modifier.weight(1f), 64.dp) {
                    haptic(); viewModel.mediaTap(HidConsumer.NEXT_TRACK)
                }
            }

            Text(
                text = "Built for Android phones, tablets and Android TV: " +
                    "Home/Back/Recents/Power work over standard Bluetooth HID " +
                    "keyboard & consumer keys — nothing to install on the host.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
