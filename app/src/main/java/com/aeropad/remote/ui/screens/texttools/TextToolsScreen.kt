package com.aeropad.remote.ui.screens.texttools

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.data.text.TextToolsStore
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import com.aeropad.remote.viewmodel.TextToolsViewModel

/**
 * Quick Text & Clipboard Tools — a send-text field, saved snippets
 * (addresses, commands, code) and local clipboard history, all typed
 * on the host as real HID keystrokes.
 *
 * Bluetooth HID is one-way, so clipboard history mirrors this phone's
 * clipboard; full two-way sync needs a companion channel (the UI says so).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolsScreen(
    onBack: () -> Unit,
    store: TextToolsStore = hiltViewModel<TextToolsViewModel>().store,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)
    val snippets by store.snippets.collectAsState()
    val clips by store.clips.collectAsState()

    var draft by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
        topBar = {
            TopAppBar(
                title = { Text("Text & Clips") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NotConnectedBanner(!isConnected)

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
