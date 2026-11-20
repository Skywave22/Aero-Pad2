package com.aeropad.remote.ui.screens.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel

/**
 * Presenter: big next/previous slide keys plus start/end/black-screen
 * controls (PowerPoint, Google Slides, Keynote-on-Windows conventions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresenterScreen(
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
                title = { Text("Presenter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val split = maxWidth >= 520.dp && maxHeight < 420.dp
            if (split) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotConnectedBanner(!isConnected)
                        SlideControls(Modifier.weight(1f), viewModel, haptic)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PresentationActions(viewModel, haptic)
                    }
                }
            } else {
                val keyHeight = (maxHeight * 0.3f).coerceIn(64.dp, 180.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotConnectedBanner(!isConnected)
                    SlideControls(Modifier, viewModel, haptic, minHeight = keyHeight)
                    PresentationActions(viewModel, haptic)
                }
            }
        }
    }
}

@Composable
private fun SlideControls(
    modifier: Modifier,
    viewModel: RemoteControlViewModel,
    haptic: () -> Unit,
    minHeight: androidx.compose.ui.unit.Dp? = null
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val keys = if (minHeight == null) Modifier.weight(1f).fillMaxHeight() else Modifier.weight(1f)
        KeyCard("Previous slide", keys, minHeight ?: 48.dp, maxLines = 2) { haptic(); viewModel.keyTap(HidKeys.PAGE_UP) }
        KeyCard("Next slide", keys, minHeight ?: 48.dp, emphasized = true, maxLines = 2) { haptic(); viewModel.keyTap(HidKeys.PAGE_DOWN) }
    }
}

@Composable
private fun PresentationActions(viewModel: RemoteControlViewModel, haptic: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyCard("Start (F5)", Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.F5) }
            KeyCard("From here", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.keyTap(HidKeys.F5, HidModifiers.LEFT_SHIFT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyCard("Black screen", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.keyTap(HidKeys.B) }
            KeyCard("White screen", Modifier.weight(1f), maxLines = 2) { haptic(); viewModel.keyTap(HidKeys.W) }
        }
        KeyCard("End (Esc)", Modifier.fillMaxWidth()) { haptic(); viewModel.keyTap(HidKeys.ESCAPE) }
        Text("Key support depends on the presentation app on your host.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
