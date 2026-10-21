package com.aeropad.remote.ui.screens.shortcuts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.ShortcutCatalog
import com.aeropad.remote.model.ShortcutDeck
import com.aeropad.remote.ui.components.ScrollableChipRow
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel

/**
 * Smart Shortcut Deck — ready-made control panels for VS Code, Photoshop,
 * Blender, browsers and Windows. Every key is a real HID combo
 * (e.g. Ctrl+Shift+P), no companion software needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutDeckScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val vibration by viewModel.vibrationsEnabled.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(vibration)
    var deck by remember { mutableStateOf<ShortcutDeck>(ShortcutCatalog.DECKS.first()) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Shortcut Decks") },
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
        ) {
            NotConnectedBanner(!isConnected)

ScrollableChipRow(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ShortcutCatalog.DECKS.forEach { d ->
                    AssistChip(
                        onClick = { deck = d },
                        label = { Text("${d.icon} ${d.name}") },
                        enabled = true
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(deck.keys, key = { it.label }) { k ->
                    KeyCard(k.label) {
                        haptic()
                        viewModel.keyTap(k.key, k.mods)
                    }
                }
            }
        }
    }
}
