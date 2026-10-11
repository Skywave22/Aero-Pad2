package com.bluepilot.remote.ui.screens.numpad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.RemoteControlViewModel

/** Numpad: calculator-style keypad sending real keypad usage codes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumpadScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)

    // Rows: NumLock / ÷ × − | 789+ | 456 | 123 Enter | 0 .
    val rows: List<List<Pair<String, Byte>>> = listOf(
        listOf("Num" to HidKeys.NUM_LOCK, "÷" to HidKeys.KP_DIVIDE, "×" to HidKeys.KP_MULTIPLY, "−" to HidKeys.KP_MINUS),
        listOf("7" to HidKeys.KP_7, "8" to HidKeys.KP_8, "9" to HidKeys.KP_9, "+" to HidKeys.KP_PLUS),
        listOf("4" to HidKeys.KP_4, "5" to HidKeys.KP_5, "6" to HidKeys.KP_6, "Bksp" to HidKeys.BACKSPACE),
        listOf("1" to HidKeys.KP_1, "2" to HidKeys.KP_2, "3" to HidKeys.KP_3, "Enter" to HidKeys.KP_ENTER),
        listOf("0" to HidKeys.KP_0, "." to HidKeys.KP_PERIOD, "Tab" to HidKeys.TAB, "Esc" to HidKeys.ESCAPE)
    )

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Numpad") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NotConnectedBanner(!isConnected)
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (label, key) ->
                        // fillMaxHeight: keys scale with the weighted row, so
                        // the grid never overflows on small screens.
                        KeyCard(
                            label = label,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            emphasized = label == "Enter"
                        ) { haptic(); viewModel.keyTap(key) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
