package com.aeropad.remote.ui.screens.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.ui.components.ScrollableChipRow
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel

/**
 * PC Keyboard screen (R1 + P9.2 + L2 + M6):
 *  - text input bar (types whole strings on the PC, warns if chars are skipped)
 *  - Numpad tab (merged from NumpadScreen — R1) + Full QWERTY shortcut
 *  - In-screen Keyboard Tuning gear sheet (P9.2 + L2 keyTapDelayMs)
 *  - shortcut row, F1..F12, navigation cluster, arrows, modifier combos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    onBack: () -> Unit,
    onOpenFullBoard: (() -> Unit)? = null,
    viewModel: RemoteControlViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val keyboardSettings by viewModel.keyboardSettings.collectAsStateWithLifecycle()
    val vibration by viewModel.vibrationsEnabled.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(vibration)
    val snackbarHostState = remember { SnackbarHostState() }
    var text by remember { mutableStateOf("") }
    var showVoiceSheet by remember { mutableStateOf(false) }   // ADV S4
    var showTuningSheet by remember { mutableStateOf(false) }  // P9.2

    LaunchedEffect(uiNotice) {
        val msg = uiNotice ?: return@LaunchedEffect
        haptic()
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeNotice()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("PC Keyboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTuningSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Keyboard tuning")
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

            // ---------- Text input bar ----------
            if (keyboardSettings.showTextInputBar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type here, send to PC…") },
                        singleLine = true
                    )
                    // ADV S4 — voice-to-text entry point.
                    IconButton(onClick = { showVoiceSheet = true }) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Voice input",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        haptic()
                        viewModel.typeText(text)
                        text = ""
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send text",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // AEROPAD v1.0 #12 — sent-text history (tap to resend).
                run {
                    val history by viewModel.sentHistory.collectAsStateWithLifecycle()
                    if (history.isNotEmpty()) {
ScrollableChipRow {
                            history.forEach { item ->
                                AssistChip(
                                    onClick = { haptic(); viewModel.typeText(item) },
                                    label = {
                                        Text(if (item.length > 18) item.take(18) + "…" else item)
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // R1 — Prominent Numpad tab & Full Board shortcut.
            run {
                var showNumpad by remember { mutableStateOf(false) }
                ScrollableChipRow {
                    FilterChip(
                        selected = !showNumpad,
                        onClick = { showNumpad = false },
                        label = { Text("Shortcuts & Keys") }
                    )
                    FilterChip(
                        selected = showNumpad,
                        onClick = { showNumpad = !showNumpad },
                        label = { Text(if (showNumpad) "Hide numpad" else "Numpad") }
                    )
                    if (onOpenFullBoard != null) {
                        AssistChip(
                            onClick = onOpenFullBoard,
                            label = { Text("⌨ Full QWERTY") }
                        )
                    }
                }
                if (showNumpad) {
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        listOf("7", "8", "9", "/"),
                        listOf("4", "5", "6", "*"),
                        listOf("1", "2", "3", "-"),
                        listOf("0", ".", "⌫", "+"),
                        listOf("=", "Tab", "⏎")
                    ).forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowKeys.forEach { k ->
                                KeyCard(
                                    label = k,
                                    modifier = Modifier.weight(if (k == "⏎") 2f else 1f),
                                    height = 48.dp,
                                    emphasized = k == "⏎"
                                ) {
                                    haptic()
                                    when (k) {
                                        "⏎" -> viewModel.keyTap(HidKeys.ENTER, 0)
                                        "⌫" -> viewModel.keyTap(HidKeys.BACKSPACE, 0)
                                        "Tab" -> viewModel.keyTap(HidKeys.TAB, 0)
                                        else -> viewModel.typeText(k)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ---------- F3: sticky modifiers ----------
            // Tapping latches for one key, double-tapping locks it down, so
            // Ctrl+Shift+Esc and Alt+Tab are possible with one thumb.
            SectionLabel("Modifiers (tap = next key, double-tap = lock)")
            run {
                val stickyState by viewModel.stickyState.collectAsStateWithLifecycle()
                ScrollableChipRow {
                    val mods = listOf(
                        "Ctrl" to HidModifiers.LEFT_CTRL,
                        "Shift" to HidModifiers.LEFT_SHIFT,
                        "Alt" to HidModifiers.LEFT_ALT,
                        "Win" to HidModifiers.LEFT_GUI
                    )
                    mods.forEach { (label, mod) ->
                        FilterChip(
                            selected = stickyState.isOn(mod),
                            onClick = { haptic(); viewModel.toggleStickyModifier(mod) },
                            label = {
                                Text(
                                    if (stickyState.isLockedFor(mod)) "$label 🔒" else label
                                )
                            }
                        )
                    }
                    AssistChip(
                        onClick = { haptic(); viewModel.clearStickyModifiers() },
                        label = { Text("Clear") },
                        enabled = stickyState.mask != 0
                    )
                }
            }

            // ---------- Shortcuts ----------
            SectionLabel("Shortcuts")
            val ctrl = HidModifiers.LEFT_CTRL
            KeyGrid(
                keys = listOf(
                    "Copy" to { viewModel.keyTap(HidKeys.C, ctrl) },
                    "Paste" to { viewModel.keyTap(HidKeys.V, ctrl) },
                    "Cut" to { viewModel.keyTap(HidKeys.X, ctrl) },
                    "All" to { viewModel.keyTap(HidKeys.A, ctrl) },
                    "Save" to { viewModel.keyTap(HidKeys.S, ctrl) },
                    "Undo" to { viewModel.keyTap(HidKeys.Z, ctrl) },
                    "Redo" to { viewModel.keyTap(HidKeys.Y, ctrl) },
                    "Del" to { viewModel.keyTap(HidKeys.DELETE) }
                ),
                columns = 4,
                haptic = haptic
            )

            // ---------- Main control keys ----------
            SectionLabel("Keys")
            KeyGrid(
                keys = listOf(
                    "Esc" to { viewModel.keyTap(HidKeys.ESCAPE) },
                    "Tab" to { viewModel.keyTap(HidKeys.TAB) },
                    "Space" to { viewModel.keyTap(HidKeys.SPACE) },
                    "Enter" to { viewModel.keyTap(HidKeys.ENTER) },
                    "Backspc" to { viewModel.keyTap(HidKeys.BACKSPACE) },
                    "Win" to { viewModel.keyTap(HidKeys.NONE, HidModifiers.LEFT_GUI) },
                    "Menu" to { viewModel.keyTap(HidKeys.APPLICATION) },
                    "PrtScr" to { viewModel.keyTap(HidKeys.PRINT_SCREEN) }
                ),
                columns = 4,
                haptic = haptic
            )

            // ---------- Arrows + navigation ----------
            SectionLabel("Navigation")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.weight(1f))
                        KeyCard("▲", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.ARROW_UP) }
                        Spacer(Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyCard("◀", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.ARROW_LEFT) }
                        KeyCard("▼", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.ARROW_DOWN) }
                        KeyCard("▶", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.ARROW_RIGHT) }
                    }
                }
                Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyCard("Home", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.HOME) }
                        KeyCard("End", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.END) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyCard("PgUp", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.PAGE_UP) }
                        KeyCard("PgDn", modifier = Modifier.weight(1f)) { haptic(); viewModel.keyTap(HidKeys.PAGE_DOWN) }
                    }
                }
            }

            // ---------- Function keys ----------
            SectionLabel("Function keys")
            val fKeys = listOf(
                HidKeys.F1, HidKeys.F2, HidKeys.F3, HidKeys.F4, HidKeys.F5, HidKeys.F6,
                HidKeys.F7, HidKeys.F8, HidKeys.F9, HidKeys.F10, HidKeys.F11, HidKeys.F12
            )
            KeyGrid(
                keys = fKeys.mapIndexed { i, key -> "F${i + 1}" to { viewModel.keyTap(key) } },
                columns = 6,
                haptic = haptic
            )

            // ---------- Combos ----------
            SectionLabel("Combos")
            KeyGrid(
                keys = listOf(
                    "Alt+Tab" to { viewModel.keyTap(HidKeys.TAB, HidModifiers.LEFT_ALT) },
                    "Alt+F4" to { viewModel.keyTap(HidKeys.F4, HidModifiers.LEFT_ALT) },
                    "Win+D" to { viewModel.keyTap(HidKeys.D, HidModifiers.LEFT_GUI) },
                    "Win+L" to { viewModel.keyTap(HidKeys.L, HidModifiers.LEFT_GUI) },
                    "Ctrl+Esc" to { viewModel.keyTap(HidKeys.ESCAPE, HidModifiers.LEFT_CTRL) },
                    "Ctl+Sh+Esc" to {
                        viewModel.keyTap(
                            HidKeys.ESCAPE,
                            (HidModifiers.LEFT_CTRL.toInt() or HidModifiers.LEFT_SHIFT.toInt()).toByte()
                        )
                    }
                ),
                columns = 3,
                haptic = haptic
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ADV S4 — voice input sheet.
    if (showVoiceSheet) {
        com.aeropad.remote.ui.components.VoiceInputSheet(onDismiss = { showVoiceSheet = false })
    }

    // P9.2 + L2 — In-screen Keyboard Tuning bottom sheet.
    if (showTuningSheet) {
        ModalBottomSheet(onDismissRequest = { showTuningSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Keyboard Tuning",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show text input bar", modifier = Modifier.weight(1f))
                    Switch(
                        checked = keyboardSettings.showTextInputBar,
                        onCheckedChange = settingsVm::setShowTextInputBar
                    )
                }
                Text("Key tap delay: ${keyboardSettings.keyTapDelayMs} ms")
                Slider(
                    value = keyboardSettings.keyTapDelayMs.toFloat(),
                    onValueChange = { settingsVm.setKeyTapDelayMs(it.toInt()) },
                    valueRange = KeyboardSettings.DELAY_MIN.toFloat()..KeyboardSettings.DELAY_MAX.toFloat()
                )
                Text(
                    "Default is 12 ms. Raise to 20–30 ms if a Smart TV, Android TV, or BIOS screen drops characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/** Simple fixed-column grid of KeyCards. */
@Composable
private fun KeyGrid(
    keys: List<Pair<String, () -> Unit>>,
    columns: Int,
    haptic: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, action) ->
                    KeyCard(label, modifier = Modifier.weight(1f)) { haptic(); action() }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
