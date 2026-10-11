package com.bluepilot.remote.ui.screens.mouse

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.HintBar
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.ui.theme.LocalAppTheme
import com.bluepilot.remote.viewmodel.RemoteControlViewModel

/**
 * Mouse screen: trackpad (drag = move, tap = left click, double-tap =
 * double click, long-press = right click), scroll strip, and L/M/R buttons.
 * All motion runs through PointerMath with the user's settings applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MouseScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)
    val spec = LocalAppTheme.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (spec.monoFont) "MOUSE" else "Mouse") },
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
        ) {
            NotConnectedBanner(!isConnected)
            HintBar("Long-press the trackpad for right-click • two-finger drag scrolls")

            // AEROPAD v1.0 — #19 Precision Mode + #22 Drag Lock chips.
            run {
                val precision by viewModel.precisionMode.collectAsState()
                val dragLock by viewModel.dragLock.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = precision,
                        onClick = { viewModel.setPrecisionMode(!precision) },
                        label = { Text(if (precision) "Precision ON (slow)" else "Precision mode") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = dragLock,
                        onClick = { viewModel.toggleDragLock() },
                        label = { Text(if (dragLock) "Drag locked — tap to release" else "Drag lock") }
                    )
                }
            }

            Row(modifier = Modifier.weight(1f)) {
                // ---------- Trackpad ----------
                GlassCard(
                    modifier = Modifier
                        .weight(1f).shadow3DPad()
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { haptic(); viewModel.onTrackpadTap() },
                                onDoubleTap = { haptic(); viewModel.onTrackpadDoubleTap() },
                                onLongPress = { haptic(); viewModel.onTrackpadLongPress() }
                            )
                        }
                        .pointerInput(viewModel) {
                            // keyed on viewModel (stable): gesture coroutine
                            // survives recompositions -> no dropped deltas.
                            detectDragGestures(
                                onDragStart = { viewModel.onTrackpadGestureStart() }
                            ) { change, dragAmount ->
                                change.consume()
                                viewModel.onTrackpadDelta(dragAmount.x, dragAmount.y)
                            }
                        }
                ) {
                    // Specular streak overlay running diagonally
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.0f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.0f)
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(300f, 850f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // STITCH REDESIGN — ghosted watermark caption.
                        Text(
                            text = "TRACKPAD AREA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = androidx.compose.ui.unit.TextUnit(
                                    3f, androidx.compose.ui.unit.TextUnitType.Sp)
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // ---------- Scroll strip ----------
                GlassCard(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .pointerInput(viewModel) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.onScrollDelta(dragAmount.y)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S\nC\nR\nO\nL\nL",
                            style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Mouse buttons ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // STITCH REDESIGN — LEFT CLICK is the accent (primary) button.
                KeyCard("LEFT CLICK", modifier = Modifier.weight(2f), height = 56.dp, emphasized = true) {
                    haptic(); viewModel.clickButton(MouseButton.LEFT)
                }
                KeyCard("MID", modifier = Modifier.weight(1f), height = 56.dp) {
                    haptic(); viewModel.clickButton(MouseButton.MIDDLE)
                }
                KeyCard("RIGHT CLICK", modifier = Modifier.weight(2f), height = 56.dp) {
                    haptic(); viewModel.clickButton(MouseButton.RIGHT)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** SECTION 5 - 3D pad lift: frosted surface floats above background. */
private fun Modifier.shadow3DPad(): Modifier =
    this.graphicsLayer { shadowElevation = 8f * density }
