package com.bluepilot.remote.ui.screens.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Help & troubleshooting: real answers for the common failure cases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val faqs = listOf(
        "PC can't find my phone" to
            "Tap Prepare PC pairing on the Connect screen first - this makes the phone visible for 2 minutes. Keep BluePilot open while pairing.",
        "Paired but not connected" to
            "Remove the pairing on BOTH sides (Windows Bluetooth settings AND phone Bluetooth settings), then pair again from Windows while BluePilot is open.",
        "Keyboard or mouse doesn't respond" to
            "Check the status card says Connected. Test with Space in Keyboard first. Some TVs accept only keyboard input, not mouse.",
        "Status says HID not supported" to
            "Some phones/ROMs block Android's Bluetooth HID Device mode. There is no app-level workaround - try another phone or a custom ROM.",
        "Connection drops when app is in background" to
            "BluePilot runs a foreground service to keep the link alive. Disable battery optimization for BluePilot in Android settings if drops continue.",
        "Mouse pointer too fast or too slow" to
            "Adjust Sensitivity and Movement smoothing in Settings > Mouse. Pen mode gives extra-precise slow movement.",
        "Text typing misses characters" to
            "Very long text is typed key-by-key. Keep the phone near the PC to avoid Bluetooth packet loss, and avoid typing while moving the mouse.",
        "Which devices can I control?" to
            "Windows, macOS, Linux, Android TV, smart TVs and anything that accepts a Bluetooth keyboard/mouse. iOS supports Bluetooth keyboards; mouse support depends on iOS version and settings."
    )

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Help") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            faqs.forEach { (question, answer) ->
                Card(
                    modifier = Modifier.padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = question,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
