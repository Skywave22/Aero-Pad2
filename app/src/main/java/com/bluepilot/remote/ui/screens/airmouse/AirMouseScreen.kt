package com.bluepilot.remote.ui.screens.airmouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.domain.AxisLock
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.AirMouseViewModel

/**
 * SECTION: Air Mouse — point the phone like a laser pointer / Wii remote.
 * Gyro streaming stops automatically on leave (DisposableEffect).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirMouseScreen(
    onBack: () -> Unit,
    viewModel: AirMouseViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val active by viewModel.active.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val smoothing by viewModel.smoothing.collectAsState()
    val axisLock by viewModel.axisLock.collectAsState()
    val haptic = rememberHaptic(true)

    DisposableEffect(Unit) {
        onDispose { viewModel.setActive(false) } // never stream in background
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Air Mouse") },
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
            NotConnectedBanner(!isConnected)

            if (!viewModel.hasGyro) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "This device has no gyroscope — Air Mouse is unavailable. " +
                            "(Emulators don't have motion sensors; test on a real phone.)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                return@Scaffold
            }

            // ---------- Master toggle ----------
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (active) "AIR MOUSE ACTIVE" else "Air mouse off",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Point the phone at your screen and move it like a laser pointer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = active, onCheckedChange = { viewModel.setActive(it) })
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { haptic(); viewModel.recenter() }, modifier = Modifier.fillMaxWidth()) {
                Text("⊕ Recenter / calibrate")
            }

            // ---------- Tuning ----------
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Sensitivity", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$sensitivity", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            Slider(value = sensitivity.toFloat(), onValueChange = { viewModel.setSensitivity(it.toInt()) }, valueRange = 0f..100f)

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Stabilization (anti-shake)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$smoothing", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            Slider(value = smoothing.toFloat(), onValueChange = { viewModel.setSmoothing(it.toInt()) }, valueRange = 0f..100f)

            Text("Axis lock", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                FilterChip(selected = axisLock == AxisLock.BOTH,
                    onClick = { viewModel.setAxisLock(AxisLock.BOTH) }, label = { Text("Free") })
                FilterChip(selected = axisLock == AxisLock.X_ONLY,
                    onClick = { viewModel.setAxisLock(AxisLock.X_ONLY) }, label = { Text("X only") })
                FilterChip(selected = axisLock == AxisLock.Y_ONLY,
                    onClick = { viewModel.setAxisLock(AxisLock.Y_ONLY) }, label = { Text("Y only") })
            }

            // ---------- Click buttons ----------
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KeyCard("Left click", Modifier.weight(2f), 72.dp, emphasized = true) {
                    haptic(); viewModel.click(MouseButton.LEFT)
                }
                KeyCard("Middle", Modifier.weight(1f), 72.dp) {
                    haptic(); viewModel.click(MouseButton.MIDDLE)
                }
                KeyCard("Right click", Modifier.weight(2f), 72.dp) {
                    haptic(); viewModel.click(MouseButton.RIGHT)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
