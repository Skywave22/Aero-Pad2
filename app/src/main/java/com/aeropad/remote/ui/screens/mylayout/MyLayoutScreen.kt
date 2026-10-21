package com.aeropad.remote.ui.screens.mylayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.model.keys.NamedCombo
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.ScrollableChipRow
import com.aeropad.remote.ui.widgets.LayoutCanvas
import com.aeropad.remote.ui.widgets.WidgetEvents
import com.aeropad.remote.viewmodel.MyLayoutViewModel

/**
 * My Layout — the personalized control surface. Shows the layout bound to
 * the connected host (laptop / desktop / TV / tablet) and lets you switch
 * instantly; picking one while connected remembers it for that device.
 *
 * Round 8: the screen also owns the **key pad** — the full keyboard, single
 * keys you pick yourself, and your own named combos — reachable from the
 * floating button without leaving the layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLayoutScreen(
    onBack: () -> Unit,
    viewModel: MyLayoutViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val connected by viewModel.connectedAddress.collectAsStateWithLifecycle()
    val combos by viewModel.combos.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showKeyPad by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val events = remember {
        object : WidgetEvents {
            override fun onPrimary(widget: WidgetSpec) { viewModel.interactor.primary(widget) }
            override fun onSecondary(widget: WidgetSpec) { viewModel.interactor.secondary(widget) }
            override fun onTrackpadStart() { viewModel.interactor.trackpadStart() }
            override fun onTrackpadDelta(dx: Float, dy: Float) { viewModel.interactor.trackpadDelta(dx, dy) }
            override fun onScrollDelta(dy: Float) { viewModel.interactor.scrollDelta(dy) }
            override fun onJoystick(x: Float, y: Float) { viewModel.interactor.joystick(x, y) }
            override fun onDpad(dirX: Int, dirY: Int) { viewModel.interactor.dpad(dirX, dirY) }
            override fun onSwipe(widget: WidgetSpec, direction: com.aeropad.remote.domain.SwipeDirection) {
                viewModel.interactor.swipe(widget, direction)
            }
            override fun onTwoFingerTap(widget: WidgetSpec) { viewModel.interactor.twoFingerTap(widget) }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("My Layout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showKeyPad = true },
                icon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                text = { Text("Keys") },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NotConnectedBanner(connected == null)

            // ---------- layout switcher ----------
            if (profiles.isNotEmpty()) {
                ScrollableChipRow(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    profiles.forEach { p ->
                        val current = layout?.name == p.spec.name
                        AssistChip(
                            onClick = { viewModel.show(p.id) },
                            label = { Text((if (current) "● " else "") + p.spec.name) }
                        )
                    }
                }
            }

            // ---------- your shortcuts, one tap from the layout ----------
            if (combos.isNotEmpty()) {
                ComboStrip(
                    combos = combos,
                    onSend = { viewModel.playCombo(it) },
                    onManage = { showKeyPad = true }
                )
            }

            val spec = layout
            if (spec != null) {
                LayoutCanvas(
                    layout = spec,
                    events = events,
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep the last row of keys clear of the floating button.
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp)
                )
            } else {
                EmptyLayoutHint(onOpenPad = { showKeyPad = true })
            }
        }
    }

    if (showKeyPad) {
        KeyPadSheet(
            combos = combos,
            isConnected = connected != null,
            hasLayout = layout != null,
            onDismiss = { showKeyPad = false },
            onSend = { key, mods -> viewModel.playKey(key, mods) },
            onAdd = { label, key, mods, turbo -> viewModel.addKeyToLayout(label, key, mods, turbo) },
            onSaveCombo = { name, key, mods -> viewModel.saveCombo(name, key, mods) },
            onDeleteCombo = { viewModel.deleteCombo(it) },
            onAddCombo = { viewModel.addComboToLayout(it) },
            onAddPreset = { viewModel.addPreset(it) }
        )
    }
}

/** Horizontal list of the user's saved shortcuts, tap = send now. */
@Composable
private fun ComboStrip(
    combos: List<NamedCombo>,
    onSend: (NamedCombo) -> Unit,
    onManage: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your shortcuts",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "manage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManage)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        ScrollableChipRow(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            combos.forEach { combo ->
                AssistChip(
                    onClick = { onSend(combo) },
                    label = { Text(combo.displayName()) }
                )
            }
        }
    }
}

@Composable
private fun EmptyLayoutHint(onOpenPad: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            "No layout selected yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Create one in Layouts, then pick it here (or on the Devices screen) — " +
                "AeroPad remembers which layout belongs to which paired host.\n\n" +
                "You can still use the key pad right now: tap Keys to send any key, " +
                "or to save your own shortcuts like Ctrl+Shift+Esc under a name " +
                "you choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(16.dp))
        AssistChip(onClick = onOpenPad, label = { Text("Open key pad") })
    }
}
