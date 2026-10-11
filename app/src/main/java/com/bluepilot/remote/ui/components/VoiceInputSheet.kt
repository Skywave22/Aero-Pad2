package com.bluepilot.remote.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.domain.VoiceCommandParser
import com.bluepilot.remote.viewmodel.VoiceInputViewModel
import com.bluepilot.remote.voice.VoiceInputManager

/**
 * ADV SECTION 4 — Voice input bottom sheet.
 *
 * Mic permission flow: rationale dialog BEFORE the system prompt (rule 8),
 * graceful denied state, waveform driven by the recognizer's REAL RMS dB
 * stream, text preview with confirm-before-send, language picker, modes,
 * commands help and typed error messages with retry.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputSheet(
    onDismiss: () -> Unit,
    viewModel: VoiceInputViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.voice.state.collectAsState()
    val partial by viewModel.voice.partial.collectAsState()
    val rms by viewModel.voice.rmsDb.collectAsState()
    val error by viewModel.voice.error.collectAsState()
    val pending by viewModel.pendingText.collectAsState()
    val lastSent by viewModel.lastSent.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val language by viewModel.language.collectAsState()
    val continuous by viewModel.continuous.collectAsState()
    val preferOffline by viewModel.preferOffline.collectAsState()
    val confirm by viewModel.confirmBeforeSend.collectAsState()
    val commands by viewModel.commandsEnabled.collectAsState()
    val punctuation by viewModel.manualPunctuation.collectAsState()

    var showRationale by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.startListening()
    }

    fun micTapped() {
        when {
            state != VoiceInputManager.State.IDLE -> viewModel.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startListening()
            else -> showRationale = true   // rationale BEFORE system prompt
        }
    }

    // Rationale dialog (rule 8 — clear explanation before asking).
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Microphone access") },
            text = {
                Text(
                    "BluePilot uses the microphone ONLY while you hold a dictation session, " +
                        "to convert your speech to text and type it on the connected device. " +
                        "Nothing is stored or sent anywhere by this app — recognition is done " +
                        "by your device's speech service."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = { viewModel.stopListening(); onDismiss() }) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Voice to text",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (!isConnected) {
                Text(
                    "Not connected — recognized text can't be typed until you connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))

            // ---------- Waveform (real RMS dB) + mic button ----------
            val bars = remember { FloatArray(24) }
            // Shift in the latest REAL rms value (recognizer callback).
            remember(rms) {
                for (i in 0 until bars.size - 1) bars[i] = bars[i + 1]
                bars[bars.size - 1] = ((rms + 2f) / 12f).coerceIn(0.05f, 1f)
                0
            }
            val active = state == VoiceInputManager.State.LISTENING
            val barColor = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val bw = size.width / bars.size
                bars.forEachIndexed { i, v ->
                    val h = (if (active) v else 0.08f) * size.height
                    drawRect(
                        color = barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(i * bw + bw * 0.2f, (size.height - h) / 2f),
                        size = androidx.compose.ui.geometry.Size(bw * 0.6f, h)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { micTapped() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (state) {
                        VoiceInputManager.State.IDLE -> "🎤  Tap to speak"
                        VoiceInputManager.State.STARTING -> "Starting…"
                        VoiceInputManager.State.LISTENING -> "Listening — tap to stop"
                        VoiceInputManager.State.PROCESSING -> "Processing…"
                    }
                )
            }

            // Live partial text.
            if (partial.isNotBlank()) {
                Text(
                    "“$partial”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ---------- Errors (typed, with retry) ----------
            error?.let { e ->
                Spacer(Modifier.height(8.dp))
                Text(e.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                if (e.canRetry) {
                    OutlinedButton(
                        onClick = { viewModel.voice.consumeError(); micTapped() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) { Text("Retry") }
                }
            }
            if (permissionDenied) {
                Text(
                    "Microphone permission denied — enable it in system Settings → Apps → BluePilot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ---------- Confirm-before-send preview ----------
            pending?.let { p ->
                Spacer(Modifier.height(10.dp))
                Text("Preview — edit then send:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = p,
                    onValueChange = { viewModel.editPending(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(
                        onClick = { viewModel.confirmPending() },
                        enabled = isConnected,
                        modifier = Modifier.weight(1f)
                    ) { Text("Type on host") }
                    OutlinedButton(
                        onClick = { viewModel.discardPending() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Discard") }
                }
            }
            if (lastSent.isNotBlank()) {
                Text(
                    "Sent: $lastSent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Options ----------
            Text("Language", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VoiceCommandParser.LANGUAGES.forEach { (tag, name) ->
                    FilterChip(
                        selected = language == tag,
                        onClick = { viewModel.setLanguage(tag) },
                        label = { Text(name) }
                    )
                }
            }
            OptionRow("Confirm before sending", confirm, viewModel::setConfirmBeforeSend)
            OptionRow("Continuous dictation", continuous, viewModel::setContinuous)
            OptionRow(
                "Prefer offline recognition" +
                    if (!viewModel.voice.supportsOnDevice) " (not supported here)" else "",
                preferOffline, viewModel::setPreferOffline
            )
            OptionRow("Voice commands (\"new line\", \"delete that\")", commands, viewModel::setCommandsEnabled)
            OptionRow("Spoken punctuation (\"comma\" → , )", punctuation, viewModel::setManualPunctuation)

            Text(
                "Commands: new line • delete that • delete N characters • tab • space",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun OptionRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
