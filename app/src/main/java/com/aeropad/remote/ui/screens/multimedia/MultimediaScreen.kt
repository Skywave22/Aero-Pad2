package com.aeropad.remote.ui.screens.multimedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.HidConsumer
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel

/** Multimedia: play/pause, tracks, volume, mute, brightness (consumer HID). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultimediaScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val vibration by viewModel.vibrationsEnabled.collectAsStateWithLifecycle()
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val split = maxWidth >= 600.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NotConnectedBanner(!isConnected)
                if (split) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlaybackKeys(viewModel, haptic)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VolumeAndScreenKeys(viewModel, haptic)
                        }
                    }
                } else {
                    PlaybackKeys(viewModel, haptic)
                    VolumeAndScreenKeys(viewModel, haptic)
                }
            }
        }
    }
}

@Composable
private fun PlaybackKeys(viewModel: RemoteControlViewModel, haptic: () -> Unit) {
    Section("Playback")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyCard("Previous", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.mediaTap(HidConsumer.PREV_TRACK) }
        KeyCard("Play / Pause", Modifier.weight(1.4f), emphasized = true, maxLines = 2) { haptic(); viewModel.mediaTap(HidConsumer.PLAY_PAUSE) }
        KeyCard("Next", Modifier.weight(1f)) { haptic(); viewModel.mediaTap(HidConsumer.NEXT_TRACK) }
    }
    KeyCard("Stop", Modifier.fillMaxWidth()) { haptic(); viewModel.mediaTap(HidConsumer.STOP) }
}

@Composable
private fun VolumeAndScreenKeys(viewModel: RemoteControlViewModel, haptic: () -> Unit) {
    Section("Volume")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyCard("Volume −", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.mediaTap(HidConsumer.VOLUME_DOWN) }
        KeyCard("Mute", Modifier.weight(1f)) { haptic(); viewModel.mediaTap(HidConsumer.MUTE) }
        KeyCard("Volume +", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.mediaTap(HidConsumer.VOLUME_UP) }
    }
    Section("Screen")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyCard("Brightness −", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.mediaTap(HidConsumer.BRIGHTNESS_DOWN) }
        KeyCard("Brightness +", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.mediaTap(HidConsumer.BRIGHTNESS_UP) }
    }
    Text("Brightness keys only work on hosts that support them.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
