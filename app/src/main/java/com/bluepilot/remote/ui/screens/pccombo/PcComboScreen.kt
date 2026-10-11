package com.bluepilot.remote.ui.screens.pccombo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.PcComboViewModel
import com.bluepilot.remote.viewmodel.ScrollBarSide

/**
 * SECTION: "Full PC Control" — trackpad top / keyboard bottom with a
 * draggable divider, dedicated scroll bar (left/right/hidden), multi-finger
 * gestures (2-finger scroll & middle-click, 3-finger right-click), and
 * one-tap export as an editable Layout profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcComboScreen(
    onBack: () -> Unit,
    viewModel: PcComboViewModel = hiltViewModel()
) {
    val board by viewModel.board.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val ratio by viewModel.ratio.collectAsState()
    val scrollSide by viewModel.scrollBarSide.collectAsState()
    val message by viewModel.message.collectAsState()
    val haptic = rememberHaptic(true)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    var showVoiceSheet by remember { mutableStateOf(false) }   // ADV S4
    Scaffold(
        floatingActionButton = {
            androidx.compose.material3.SmallFloatingActionButton(onClick = { showVoiceSheet = true }) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
            }
        },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Full PC Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Scroll bar side picker
                    FilterChip(
                        selected = scrollSide == ScrollBarSide.LEFT,
                        onClick = { viewModel.setScrollSide(ScrollBarSide.LEFT) },
                        label = { Text("L") }
                    )
                    FilterChip(
                        selected = scrollSide == ScrollBarSide.RIGHT,
                        onClick = { viewModel.setScrollSide(ScrollBarSide.RIGHT) },
                        label = { Text("R") }
                    )
                    FilterChip(
                        selected = scrollSide == ScrollBarSide.HIDDEN,
                        onClick = { viewModel.setScrollSide(ScrollBarSide.HIDDEN) },
                        label = { Text("Off") }
                    )
                    IconButton(onClick = { viewModel.saveAsProfile() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save as layout profile")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp)
        ) {
            val totalH = maxHeight
            val totalHpx = constraints.maxHeight.toFloat()
            Column(modifier = Modifier.fillMaxSize()) {
                NotConnectedBanner(!isConnected)

                // ============ TOP: trackpad + optional scroll rail ============
                Row(modifier = Modifier.height(totalH * ratio)) {
                    if (scrollSide == ScrollBarSide.LEFT) {
                        ScrollRail(viewModel, Modifier.fillMaxHeight())
                    }
                    MultiTouchTrackpad(
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp)
                    )
                    if (scrollSide == ScrollBarSide.RIGHT) {
                        ScrollRail(viewModel, Modifier.fillMaxHeight())
                    }
                }

                // ============ DIVIDER (drag to resize) ============
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                // BUGFIX: read live value (not composition-captured)
                                // so fast drags never compound stale ratios.
                                viewModel.setRatio(viewModel.ratio.value + drag.y / totalHpx)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(5.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.shapes.small
                            )
                    )
                }

                // ============ BOTTOM: compact keyboard ============
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    board.rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex in board.compactHiddenRows) return@forEachIndexed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(key.widthWeight)
                                        .heightIn(min = 34.dp)
                                        .background(
                                            key.colorArgb?.let { it.toComposeColor() }
                                                ?: MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.shapes.small
                                        )
                                        .pointerInput(key.id) {
                                            detectTapGestures(
                                                onTap = { haptic(); viewModel.key(key) }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        key.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ADV S4 — voice input quick-action sheet.
    if (showVoiceSheet) {
        com.bluepilot.remote.ui.components.VoiceInputSheet(onDismiss = { showVoiceSheet = false })
    }
}

/** Dedicated vertical scroll bar (separate from trackpad gestures). */
@Composable
private fun ScrollRail(viewModel: PcComboViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(40.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                MaterialTheme.shapes.medium
            )
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    viewModel.scroll(drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text("⇅", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Trackpad with finger-count awareness:
 * 1-finger drag = move · 2-finger drag = scroll ·
 * 1/2/3-finger tap = left/middle/right click.
 * Implemented on raw pointer events so finger count is exact.
 */
@Composable
private fun MultiTouchTrackpad(viewModel: PcComboViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                MaterialTheme.shapes.medium
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val pressedNow = down.changes.filter { it.pressed }
                        if (pressedNow.isEmpty()) continue

                        viewModel.gestureStart()
                        var maxFingers = pressedNow.size
                        var moved = false
                        var lastCentroid = pressedNow
                            .map { it.position }
                            .reduce { a, b -> a + b } / pressedNow.size.toFloat()

                        // Track until all fingers lift.
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > maxFingers) maxFingers = pressed.size

                            val centroid = pressed
                                .map { it.position }
                                .reduce { a, b -> a + b } / pressed.size.toFloat()
                            val dx = centroid.x - lastCentroid.x
                            val dy = centroid.y - lastCentroid.y
                            lastCentroid = centroid

                            if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                                moved = true
                                if (pressed.size >= 2) viewModel.scroll(dy)
                                else viewModel.move(dx, dy)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // Tap (no significant movement) → click by finger count.
                        if (!moved) viewModel.tap(maxFingers)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Trackpad\n1-tap click · 2-drag scroll · 3-tap right-click",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
