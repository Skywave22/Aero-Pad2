package com.aeropad.remote.ui.screens.androidremote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import kotlin.math.roundToInt

/**
 * Touch Screen control — turns this device into an absolute digitizer for
 * the host. Your finger position on the pad maps 1:1 to the host screen
 * over a real HID Digitizer (Usage Page 0x0D) report: touch down = finger
 * touch, drag = swipe, release = lift off.
 *
 * Works on hosts that accept HID touch input (Android phones/tablets/TV
 * hosts with digitizer support, Windows). Hosts without touch support
 * simply ignore the report — nothing can break.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchControlScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)

    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var finger by remember { mutableStateOf<Offset?>(null) }

    /** Normalizes a local touch point and streams it as an absolute digitizer contact. */
    fun sendContact(point: Offset, contact: Boolean) {
        if (areaSize.width <= 0 || areaSize.height <= 0) return
        val nx = (point.x / areaSize.width).coerceIn(0f, 1f)
        val ny = (point.y / areaSize.height).coerceIn(0f, 1f)
        viewModel.touch(nx, ny, contact)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Touch Screen") },
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .onSizeChanged { areaSize = it }
                    .pointerInput(areaSize) {
                        if (areaSize.width <= 0 || areaSize.height <= 0) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                finger = offset
                                haptic()
                                sendContact(offset, contact = true)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                finger = change.position
                                sendContact(change.position, contact = true)
                            },
                            onDragEnd = {
                                finger?.let { sendContact(it, contact = false) }
                                finger = null
                            },
                            onDragCancel = {
                                finger?.let { sendContact(it, contact = false) }
                                finger = null
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Crosshair guides — mirrors the host's X/Y axes.
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f)
                    drawLine(tint, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2f)
                    drawLine(tint, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2f)
                }

                if (finger == null) {
                    Text(
                        text = "Touch & drag anywhere",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Live finger indicator, 1:1 with the streamed position.
                    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
                    val tint = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                val f = finger ?: Offset.Zero
                                IntOffset(
                                    (f.x - radiusPx).roundToInt(),
                                    (f.y - radiusPx).roundToInt()
                                )
                            }
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(tint)
                    )
                }
            }

            Text(
                text = "Your finger position maps directly onto the host screen — " +
                    "tap to touch, drag to swipe, release to lift. Works on hosts that " +
                    "accept HID touch input (Android hosts, Windows); others ignore it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
