package com.aeropad.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 * Live Bluetooth connection status — one clean rounded card:
 * status dot • title/subtitle • Bluetooth glyph.
 */
@Composable
fun ConnectionStatusCard(state: HidConnectionState, modifier: Modifier = Modifier) {
    val visual = statusVisual(state)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
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
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = "Bluetooth status",
                tint = visual.color,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
