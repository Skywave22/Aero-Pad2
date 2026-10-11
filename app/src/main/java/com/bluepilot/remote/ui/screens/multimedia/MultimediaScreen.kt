package com.bluepilot.remote.ui.screens.multimedia

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
import com.bluepilot.remote.model.HidConsumer
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.RemoteControlViewModel

/** Multimedia: play/pause, tracks, volume, mute, brightness (consumer HID). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultimediaScreen(
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
                title = { Text("Multimedia") },
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

            Section("Playback")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("⏮ Prev", Modifier.weight(1f), 64.dp) { haptic(); viewModel.mediaTap(HidConsumer.PREV_TRACK) }
                KeyCard("⏯ Play/Pause", Modifier.weight(1.4f), 64.dp, emphasized = true) { haptic(); viewModel.mediaTap(HidConsumer.PLAY_PAUSE) }
                KeyCard("⏭ Next", Modifier.weight(1f), 64.dp) { haptic(); viewModel.mediaTap(HidConsumer.NEXT_TRACK) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("⏹ Stop", Modifier.weight(1f), 56.dp) { haptic(); viewModel.mediaTap(HidConsumer.STOP) }
            }

            Section("Volume")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("Vol −", Modifier.weight(1f), 64.dp) { haptic(); viewModel.mediaTap(HidConsumer.VOLUME_DOWN) }
                KeyCard("Mute", Modifier.weight(1f), 64.dp) { haptic(); viewModel.mediaTap(HidConsumer.MUTE) }
                KeyCard("Vol +", Modifier.weight(1f), 64.dp) { haptic(); viewModel.mediaTap(HidConsumer.VOLUME_UP) }
            }

            Section("Screen")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("☀ −", Modifier.weight(1f), 56.dp) { haptic(); viewModel.mediaTap(HidConsumer.BRIGHTNESS_DOWN) }
                KeyCard("☀ +", Modifier.weight(1f), 56.dp) { haptic(); viewModel.mediaTap(HidConsumer.BRIGHTNESS_UP) }
            }
            Text(
                text = "Brightness keys work on hosts that support consumer brightness controls (most laptops).",
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
