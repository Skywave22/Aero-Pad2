package com.bluepilot.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.ui.theme.LocalAppTheme

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

/** Status card: styled either as an avionics strip (HUD) or a frosted status capsule (Glass). */
@Composable
fun ConnectionStatusCard(state: HidConnectionState, modifier: Modifier = Modifier) {
    val spec = LocalAppTheme.current
    val visual = statusVisual(state)

    if (spec.monoFont) {
        // Cockpit HUD / Day Flight: Avionics strip style
        val borderGlowColor = spec.glowColor ?: spec.primary
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(spec.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .border(
                    width = 1.5.dp,
                    color = borderGlowColor.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tactical bracket and status code
                Text(
                    text = "[SYS: ${visual.title.uppercase()}]",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = visual.color
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = visual.subtitle.uppercase(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = spec.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = "Bluetooth Status",
                    tint = visual.color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        // Frosted status capsule style (Liquid Glass, Hawaii, etc.)
        GlassCard(
            modifier = modifier.fillMaxWidth(),
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
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
                    contentDescription = "Bluetooth status glyph",
                    tint = spec.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
