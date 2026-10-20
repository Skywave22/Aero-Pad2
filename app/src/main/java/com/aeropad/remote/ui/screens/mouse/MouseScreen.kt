package com.aeropad.remote.ui.screens.mouse

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.ui.components.GlassCard
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.HintBar
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.ui.theme.LocalAppTheme
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel

/**
 * Mouse screen: trackpad (drag = move, tap = left click, double-tap =
 * double click, long-press = right click), scroll strip, L/M/R buttons,
 * Scroll mode & right-edge scroll zone (A2), Air Mouse toggle (R7),
 * and in-screen Mouse Tuning sheet (P9.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MouseScreen(
    onBack: () -> Unit,
    onOpenAirMouse: (() -> Unit)? = null,
    viewModel: RemoteControlViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val vibration by viewModel.vibrationsEnabled.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()
    val mouseSettings by settingsVm.mouse.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(vibration)
    val spec = LocalAppTheme.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showTuningSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiNotice) {
        val msg = uiNotice ?: return@LaunchedEffect
        haptic()
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeNotice()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (spec.monoFont) "MOUSE" else "Mouse") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // P9.1 — In-screen Mouse tuning gear.
                    IconButton(onClick = { showTuningSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Mouse tuning")
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
            HintBar("Long-press trackpad for right-click • right edge or Scroll mode scrolls")

            // Precision Mode, Drag Lock, Scroll Mode (A2), and Air Mouse (R7) chips.
            run {
                val precision by viewModel.precisionMode.collectAsStateWithLifecycle()
                val dragLock by viewModel.dragLock.collectAsStateWithLifecycle()
                val scrollMode by viewModel.scrollMode.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = precision,
                        onClick = { viewModel.setPrecisionMode(!precision) },
                        label = { Text(if (precision) "Precision ON" else "Precision mode") }
                    )
                    FilterChip(
                        selected = dragLock,
                        onClick = { viewModel.toggleDragLock() },
                        label = { Text(if (dragLock) "Drag locked" else "Drag lock") }
                    )
                    FilterChip(
                        selected = scrollMode,
                        onClick = { viewModel.setScrollMode(!scrollMode) },
                        label = { Text(if (scrollMode) "Scroll mode ON" else "Scroll mode") }
                    )
                    if (onOpenAirMouse != null) {
                        AssistChip(
                            onClick = onOpenAirMouse,
                            label = { Text("🎯 Air Mouse") }
                        )
                    }
                }
            }

            Row(modifier = Modifier.weight(1f)) {
                var padWidthPx by remember { mutableFloatStateOf(0f) }
                var edgeScrolling by remember { mutableStateOf(false) }
                // ---------- Trackpad ----------
                GlassCard(
                    modifier = Modifier
                        .weight(1f).shadow3DPad()
                        .fillMaxHeight()
                        .onSizeChanged { padWidthPx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { haptic(); viewModel.onTrackpadTap() },
                                onDoubleTap = { haptic(); viewModel.onTrackpadDoubleTap() },
                                onLongPress = { haptic(); viewModel.onTrackpadLongPress() }
                            )
                        }
                        .pointerInput(viewModel) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    edgeScrolling = padWidthPx > 0f && startOffset.x >= padWidthPx * 0.88f
                                    viewModel.onTrackpadGestureStart()
                                },
                                onDragEnd = { edgeScrolling = false },
                                onDragCancel = { edgeScrolling = false }
                            ) { change, dragAmount ->
                                change.consume()
                                if (edgeScrolling) {
                                    viewModel.onScrollDelta(dragAmount.y)
                                } else {
                                    viewModel.onTrackpadDelta(dragAmount.x, dragAmount.y)
                                }
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

    // P9.1 — In-screen Mouse Tuning bottom sheet.
    if (showTuningSheet) {
        ModalBottomSheet(onDismissRequest = { showTuningSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Mouse & Trackpad Tuning",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Sensitivity: ${mouseSettings.sensitivity}%")
                Slider(
                    value = mouseSettings.sensitivity.toFloat(),
                    onValueChange = { settingsVm.setMouseSensitivity(it.toInt()) },
                    valueRange = 0f..100f
                )
                Text("Scroll speed: ${mouseSettings.scrollSpeed}%")
                Slider(
                    value = mouseSettings.scrollSpeed.toFloat(),
                    onValueChange = { settingsVm.setScrollSpeed(it.toInt()) },
                    valueRange = 0f..100f
                )
                Text("Smoothing: ${mouseSettings.movementSmoothing}%")
                Slider(
                    value = mouseSettings.movementSmoothing.toFloat(),
                    onValueChange = { settingsVm.setMovementSmoothing(it.toInt()) },
                    valueRange = 0f..100f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap to click", modifier = Modifier.weight(1f))
                    Switch(checked = mouseSettings.tapToClick, onCheckedChange = settingsVm::setTapToClick)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Invert scroll", modifier = Modifier.weight(1f))
                    Switch(checked = mouseSettings.invertScroll, onCheckedChange = settingsVm::setInvertScroll)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pen mode (precision)", modifier = Modifier.weight(1f))
                    Switch(checked = mouseSettings.penMode, onCheckedChange = settingsVm::setPenMode)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** SECTION 5 - 3D pad lift: frosted surface floats above background. */
private fun Modifier.shadow3DPad(): Modifier =
    this.graphicsLayer { shadowElevation = 8f * density }
