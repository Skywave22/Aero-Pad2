package com.aeropad.remote.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aeropad.remote.BuildConfig
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.ui.theme.LocalAppTheme

data class StatusVisual(val color: Color, val title: String, val subtitle: String)

@Composable
fun statusVisual(state: HidConnectionState): StatusVisual {
    val spec = LocalAppTheme.current
    return when (state) {
        is HidConnectionState.Connected ->
            StatusVisual(spec.connected, "Connected", state.device.name)
        is HidConnectionState.Connecting ->
            StatusVisual(spec.connecting, "Connecting…", state.device.name)
        is HidConnectionState.Initializing ->
            StatusVisual(spec.connecting, "Starting HID engine…", "Registering with Android Bluetooth")
        is HidConnectionState.Idle ->
            StatusVisual(MaterialTheme.colorScheme.outline, "Ready", "Waiting for a host connection")
        is HidConnectionState.BluetoothDisabled ->
            StatusVisual(spec.error, "Bluetooth is off", "Turn on Bluetooth to continue")
        is HidConnectionState.PermissionMissing ->
            StatusVisual(spec.error, "Permissions needed", "Grant Bluetooth permissions to continue")
        is HidConnectionState.HidUnsupported ->
            StatusVisual(spec.error, "HID not supported", state.reason)
        is HidConnectionState.Error ->
            StatusVisual(spec.error, "Error", state.message)
    }
}

/**
 * P9.8 — Contextual three-line troubleshooting tips shown inline when the
 * connection state requires user action.
 */
fun contextualTroubleshooting(state: HidConnectionState): List<String> = when (state) {
    is HidConnectionState.PermissionMissing -> listOf(
        "1. Tap this card or 'Connect to a device' to grant Nearby Devices permission.",
        "2. If previously denied, open Android Settings → Apps → AeroPad → Permissions.",
        "3. AeroPad only uses Bluetooth for HID input — never location or network."
    )
    is HidConnectionState.BluetoothDisabled -> listOf(
        "1. Turn on Bluetooth in your phone's Quick Settings panel.",
        "2. Keep your PC, Mac, or TV awake and within 5 meters.",
        "3. AeroPad will automatically register its HID profile once Bluetooth turns on."
    )
    is HidConnectionState.HidUnsupported -> listOf(
        "1. Verify Bluetooth is turned on and Airplane mode is off.",
        "2. Some custom OEM ROMs disable the Android BluetoothHidDevice profile.",
        "3. Tap 'Copy diagnostics' below to inspect your device/ROM HID status."
    )
    is HidConnectionState.Error -> listOf(
        "1. Make sure the host PC/TV is awake and Bluetooth is enabled on both sides.",
        "2. If pairing is stuck, remove/forget the device on BOTH the PC and phone, then re-pair.",
        "3. In Settings → Advanced, keep 'Extended HID reports' OFF for maximum compatibility."
    )
    else -> emptyList()
}

/**
 * A6 — Builds a shareable device + HID diagnostic summary with zero network access.
 */
fun buildQuickDiagnosticsReport(state: HidConnectionState): String = buildString {
    val stateName = when (state) {
        is HidConnectionState.Connected -> "Connected"
        is HidConnectionState.Connecting -> "Connecting"
        is HidConnectionState.Initializing -> "Initializing"
        is HidConnectionState.Idle -> "Idle"
        is HidConnectionState.BluetoothDisabled -> "BluetoothDisabled"
        is HidConnectionState.PermissionMissing -> "PermissionMissing"
        is HidConnectionState.HidUnsupported -> "HidUnsupported"
        is HidConnectionState.Error -> "Error"
    }
    appendLine("AeroPad Diagnostics Report")
    appendLine("App: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
    appendLine("ROM/Build: ${Build.DISPLAY}")
    appendLine("HID State: $stateName")
    when (state) {
        is HidConnectionState.HidUnsupported -> appendLine("Reason: ${state.reason}")
        is HidConnectionState.Error -> appendLine("Error: ${state.message}")
        is HidConnectionState.Connected -> appendLine("Host: ${state.device.name} (${state.device.address})")
        else -> {}
    }
}

/**
 * Live Bluetooth connection status — one clean rounded card:
 * status dot • title/subtitle • Bluetooth glyph + contextual troubleshooting (P9.8)
 * and one-tap diagnostics copy (A6).
 */
@Composable
fun ConnectionStatusCard(
    state: HidConnectionState,
    modifier: Modifier = Modifier,
    onTroubleshoot: (() -> Unit)? = null
) {
    val visual = statusVisual(state)
    val tips = contextualTroubleshooting(state)
    val context = LocalContext.current
    var copied by remember(state) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(visual.color, CircleShape)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = visual.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = visual.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // UI fix — this always drew a red Bluetooth logo, even for
                // permission and ROM problems where Bluetooth is not the
                // issue. Show a warning glyph for actionable/error states so
                // the icon matches the message.
                Icon(
                    imageVector = when (state) {
                        is HidConnectionState.PermissionMissing,
                        is HidConnectionState.HidUnsupported,
                        is HidConnectionState.Error -> Icons.Rounded.WarningAmber
                        else -> Icons.Rounded.Bluetooth
                    },
                    contentDescription = null,
                    tint = visual.color,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (tips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                tips.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onTroubleshoot != null) {
                        AssistChip(
                            onClick = onTroubleshoot,
                            label = { Text("Troubleshoot") }
                        )
                    }
                    if (state is HidConnectionState.HidUnsupported || state is HidConnectionState.Error) {
                        AssistChip(
                            onClick = {
                                val report = buildQuickDiagnosticsReport(state)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("AeroPad Diagnostics", report))
                                copied = true
                            },
                            label = { Text(if (copied) "Diagnostics copied ✓" else "Copy diagnostics") }
                        )
                    }
                }
            }
        }
    }
}
