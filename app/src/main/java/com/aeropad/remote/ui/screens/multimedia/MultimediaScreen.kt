package com.aeropad.remote.ui.screens.multimedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.HidConsumer
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.ui.theme.LocalAppTheme
import com.aeropad.remote.viewmodel.RemoteControlViewModel

/**
 * STITCH v3 REBUILD — Multimedia (built 1:1 from the generated design
 * HTML, A_Obsidian3D/11_Multimedia.html):
 *  - Hero: 256dp circular play/pause disc (`w-64 h-64 rounded-full`) with
 *    a concentric inner ring (`border-primary/20`, surface-lowest fill)
 *    and an 80sp glyph — the whole disc is the button.
 *  - Transport row: 64dp rounded-xl side buttons, STOP is 80dp in the
 *    error tint (exact widths from the HTML: w-16 / w-20).
 *  - Live status pill with a glowing dot (shadow-[0_0_8px]).
 *  - Link rows to Presenter/Numpad with 40dp icon coins
 *    (`w-10 h-10 rounded-lg bg-surface-container`).
 * All colors come from the theme spec so Obsidian 3D and Glass Light
 * both render exactly like their PNGs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultimediaScreen(
    onBack: () -> Unit,
    onOpenPresenter: () -> Unit = {},
    onOpenNumpad: () -> Unit = {},
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
                title = { Text("Multimedia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Status pill with glow dot (from the HTML header).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(
                                spec.surfaceVariant.copy(alpha = 0.8f),
                                CircleShape
                            )
                            .border(1.dp, spec.connected.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(spec.connected, spec.connected.copy(alpha = 0.4f))
                                    ),
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (isConnected) "LIVE" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) spec.connected
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        // LANDSCAPE FIX — a 256dp disc + rows can't stack in a ~250dp-tall
        // landscape viewport. Landscape: disc (smaller) on the left,
        // transport/volume/links in a column on the right. Portrait as-is.
        val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left: compact disc hero (works at 168dp).
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(spec.primary.copy(alpha = 0.10f), Color.Transparent)
                            ),
                            CircleShape
                        )
                        .clickable { haptic(); viewModel.mediaTap(HidConsumer.PLAY_PAUSE) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .background(spec.surface.copy(alpha = spec.surfaceAlpha), CircleShape)
                            .border(1.5.dp, spec.primary.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏯", fontSize = 56.sp, color = spec.primary)
                    }
                }
                // Right: everything else, scrollable if tight.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NotConnectedBanner(!isConnected)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TransportKey("⏮", 52.dp, spec) { haptic(); viewModel.mediaTap(HidConsumer.PREV_TRACK) }
                        TransportKey("⏹", 62.dp, spec, error = true) { haptic(); viewModel.mediaTap(HidConsumer.STOP) }
                        TransportKey("⏭", 52.dp, spec) { haptic(); viewModel.mediaTap(HidConsumer.NEXT_TRACK) }
                        TransportKey("🔇", 52.dp, spec) { haptic(); viewModel.mediaTap(HidConsumer.MUTE) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TransportKey("Vol −", 48.dp, spec, wide = true, modifier = Modifier.weight(1f)) {
                            haptic(); viewModel.mediaTap(HidConsumer.VOLUME_DOWN)
                        }
                        TransportKey("Vol +", 48.dp, spec, wide = true, modifier = Modifier.weight(1f)) {
                            haptic(); viewModel.mediaTap(HidConsumer.VOLUME_UP)
                        }
                    }
                    LinkRow("📽", "Presenter mode", spec, onOpenPresenter)
                    LinkRow("🔢", "Numpad", spec, onOpenNumpad)
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NotConnectedBanner(!isConnected)
            Spacer(Modifier.height(10.dp))

            // ---------- HERO: 256dp play/pause disc ----------
            Box(
                modifier = Modifier
                    .size(256.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                spec.primary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
                    .clickable { haptic(); viewModel.mediaTap(HidConsumer.PLAY_PAUSE) },
                contentAlignment = Alignment.Center
            ) {
                // Concentric inner ring (border-primary/20 + lowest fill).
                Box(
                    modifier = Modifier
                        .size(216.dp)
                        .background(spec.background.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, spec.primary.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .background(
                                spec.surface.copy(alpha = spec.surfaceAlpha),
                                CircleShape
                            )
                            .border(1.5.dp, spec.primary.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⏯",
                            fontSize = 80.sp,
                            color = spec.primary
                        )
                    }
                }
            }
            Text(
                "tap = play/pause",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            // ---------- Transport: w-16 / w-20(stop, error) / w-16 / w-16 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TransportKey("⏮", 64.dp, spec) { haptic(); viewModel.mediaTap(HidConsumer.PREV_TRACK) }
                TransportKey("⏹", 80.dp, spec, error = true) { haptic(); viewModel.mediaTap(HidConsumer.STOP) }
                TransportKey("⏭", 64.dp, spec) { haptic(); viewModel.mediaTap(HidConsumer.NEXT_TRACK) }
                TransportKey("🔇", 64.dp, spec) { haptic(); viewModel.mediaTap(HidConsumer.MUTE) }
            }
            Spacer(Modifier.height(14.dp))

            // ---------- Volume row ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransportKey("Vol −", 64.dp, spec, wide = true, modifier = Modifier.weight(1f)) {
                    haptic(); viewModel.mediaTap(HidConsumer.VOLUME_DOWN)
                }
                TransportKey("Vol +", 64.dp, spec, wide = true, modifier = Modifier.weight(1f)) {
                    haptic(); viewModel.mediaTap(HidConsumer.VOLUME_UP)
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---------- Link rows (w-10 h-10 rounded-lg icon coins) ----------
            LinkRow("📽", "Presenter mode", spec, onOpenPresenter)
            Spacer(Modifier.height(8.dp))
            LinkRow("🔢", "Numpad", spec, onOpenNumpad)
            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun TransportKey(
    label: String,
    keySize: androidx.compose.ui.unit.Dp,
    spec: com.aeropad.remote.ui.theme.AppThemeSpec,
    error: Boolean = false,
    wide: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (error) spec.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .then(if (wide) Modifier.height(keySize) else Modifier.size(keySize))
            .background(
                spec.surface.copy(alpha = spec.surfaceAlpha),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (error) spec.error.copy(alpha = 0.4f) else spec.outline,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = if (label.length <= 2) 26.sp else 14.sp, color = tint)
    }
}

@Composable
private fun LinkRow(
    icon: String,
    label: String,
    spec: com.aeropad.remote.ui.theme.AppThemeSpec,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(spec.surface.copy(alpha = spec.surfaceAlpha), RoundedCornerShape(12.dp))
            .border(1.dp, spec.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(spec.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.size(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
    }
}
