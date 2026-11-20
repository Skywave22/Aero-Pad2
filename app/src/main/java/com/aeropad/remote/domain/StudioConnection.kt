package com.aeropad.remote.domain

import com.aeropad.remote.model.HidConnectionState

/** Copy for the dashboard, derived only from the actual HID and permission state. */
internal data class StudioConnection(
    val badge: String,
    val title: String,
    val action: String,
    val detail: String? = null
)

internal fun dashboardConnection(state: HidConnectionState, hasPermissions: Boolean): StudioConnection {
    if (!hasPermissions || state == HidConnectionState.PermissionMissing) {
        return StudioConnection("Offline", "Ready to connect.", "Pair device")
    }
    return when (state) {
        is HidConnectionState.Connected -> StudioConnection("Connected", "Connected.", "Manage device", state.device.name)
        is HidConnectionState.Connecting -> StudioConnection("Connecting", "Connecting…", "View connection", state.device.name)
        HidConnectionState.Initializing -> StudioConnection("Starting", "Starting Bluetooth…", "View connection")
        HidConnectionState.BluetoothDisabled -> StudioConnection("Bluetooth off", "Bluetooth is off.", "Turn on Bluetooth")
        is HidConnectionState.Error -> StudioConnection("Offline", "Connection interrupted.", "View details")
        is HidConnectionState.HidUnsupported -> StudioConnection("Unavailable", "HID unavailable.", "View details")
        HidConnectionState.Idle, HidConnectionState.PermissionMissing -> StudioConnection("Offline", "Ready to connect.", "Pair device")
    }
}
