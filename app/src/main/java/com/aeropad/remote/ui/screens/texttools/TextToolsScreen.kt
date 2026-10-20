package com.aeropad.remote.ui.screens.texttools

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.data.text.TextToolsStore
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.ShortcutCatalog
import com.aeropad.remote.model.ShortcutDeck
import com.aeropad.remote.ui.components.GlassCard
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import com.aeropad.remote.viewmodel.TextToolsViewModel

/**
 * Quick Text, Clipboard Push/Pull (A3) & Shortcut Decks (R6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolsScreen(
    onBack: () -> Unit,
    store: TextToolsStore = hiltViewModel<TextToolsViewModel>().store,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val vibration by viewModel.vibrationsEnabled.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(vibration)
    val snippets by store.snippets.collectAsStateWithLifecycle()
    val clips by store.clips.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Text & Clips, 1 = Shortcut Decks
    var selectedDeck by remember { mutableStateOf<ShortcutDeck>(ShortcutCatalog.DECKS.first()) }
    var draft by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(uiNotice) {
        val msg = uiNotice ?: return@LaunchedEffect
        haptic()
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeNotice()
    }

    // Mirror this phone's clipboard into history while the screen is open.
    DisposableEffect(Unit) {
        val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            runCatching {
                mgr?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    ?.let(store::pushClip)
            }
        }
        mgr?.addPrimaryClipChangedListener(listener)
        listener.onPrimaryClipChanged() // seed with the current clip
        onDispose { mgr?.removePrimaryClipChangedListener(listener) }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Text, Clips & Decks") },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("📋 Text & Clipboard") }
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("⚡ Shortcut Decks") }
                )
            }

            if (activeTab == 1) {
                // R6 — Merged Shortcut Decks tab
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    NotConnectedBanner(!isConnected)
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShortcutCatalog.DECKS.forEach { d ->
                            FilterChip(
                                selected = selectedDeck.name == d.name,
                                onClick = { selectedDeck = d },
                                label = { Text("${d.icon} ${d.name}") }
                            )
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(selectedDeck.keys, key = { it.label }) { k ->
                            KeyCard(k.label) {
                                haptic()
                                viewModel.keyTap(k.key, k.mods)
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NotConnectedBanner(!isConnected)

                    // A3 — One-tap Clipboard Push / Pull card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Clipboard Push & Pull",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {
                                        haptic()
                                        val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val currentClip = mgr?.primaryClip?.getItemAt(0)
                                            ?.coerceToText(context)?.toString().orEmpty()
                                        val toSend = currentClip.ifBlank { clips.firstOrNull().orEmpty() }
                                        if (toSend.isNotBlank()) {
                                            viewModel.type(toSend)
                                        }
                                    },
                                    label = { Text("⬆ Push phone clipboard to PC") }
                                )
                                AssistChip(
                                    onClick = {
                                        haptic()
                                        viewModel.keyTap(HidKeys.V, HidModifiers.LEFT_CTRL)
                                    },
                                    label = { Text("Paste on PC (Ctrl+V)") }
                                )
                                AssistChip(
                                    onClick = {
                                        haptic()
                                        viewModel.keyTap(HidKeys.C, HidModifiers.LEFT_CTRL)
                                    },
                                    label = { Text("⬇ Copy on PC (Ctrl+C)") }
                                )
                            }
                        }
                    }

                    Section("Send text")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type anything, send it to the host…") },
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        if (draft.isNotBlank()) {
                            haptic(); viewModel.type(draft); draft = ""
                        }
                    },
                    label = { Text("Send to host") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.padding(0.dp))
                    }
                )
                AssistChip(
                    onClick = { if (draft.isNotBlank()) store.addSnippet("", draft) },
                    label = { Text("Save as snippet") }
                )
            }

            Section("Snippets")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { showAdd = true }, label = { Text("＋ Add") })
                snippets.forEach { sn ->
                    AssistChip(
                        onClick = { haptic(); viewModel.type(sn.text) },
                        label = { Text(sn.label.ifEmpty { sn.text.take(18) }) }
                    )
                    AssistChip(onClick = { store.removeSnippet(sn.id) }, label = { Text("✕") })
                }
            }

            Section("Clipboard history (this phone)")
            if (clips.isEmpty()) {
                Text(
                    "Nothing copied yet. Copy something on this phone and it shows up here, ready to send.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            clips.forEach { c ->
                AssistChip(
                    onClick = { haptic(); viewModel.type(c) },
                    label = { Text(c.take(40).replace('\n', ' ')) }
                )
            }
            if (clips.isNotEmpty()) {
                TextButton(onClick = { store.clearClips() }) { Text("Clear history") }
            }

                    Text(
                        "Snippets and clips are typed on the host as real keystrokes. " +
                            "Bluetooth HID is one-way — full clipboard sync would need a " +
                            "companion app or another data channel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAdd) {
        var label by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Label") })
                    OutlinedTextField(text, { text = it }, label = { Text("Text to send") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.addSnippet(label, text); showAdd = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
