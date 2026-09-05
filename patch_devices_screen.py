import re

with open('app/src/main/java/com/aeropad/remote/ui/screens/devices/DevicesScreen.kt', 'r') as f:
    text = f.read()

old_bonded = """            items(bonded, key = { "b-" + it.address }) { device ->
                // V2 M4 deferred-item — ★ saves this host for one-tap quick-switch.
                val savedHosts by viewModel.savedHostAddresses.collectAsState()
                DeviceRow(
                    device,
                    connected = (state as? HidConnectionState.Connected)?.device?.address == device.address,
                    saved = device.address in savedHosts,
                    onToggleSave = { viewModel.toggleSavedHost(device) }
                ) {
                    viewModel.connect(device.address)
                }
            }"""

new_bonded = """            items(bonded, key = { "b-" + it.address }) { device ->
                // V2 M4 deferred-item — ★ saves this host for one-tap quick-switch.
                val savedHosts by viewModel.savedHostAddresses.collectAsState()
                DeviceRow(
                    device,
                    connected = (state as? HidConnectionState.Connected)?.device?.address == device.address,
                    saved = device.address in savedHosts,
                    onToggleSave = { viewModel.toggleSavedHost(device) },
                    onUnpair = { viewModel.unpair(device.address) }
                ) {
                    viewModel.connect(device.address)
                }
            }"""

old_row = """private fun DeviceRow(
    device: RemoteDevice,
    connected: Boolean,
    saved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onClick: () -> Unit
) {"""

new_row = """private fun DeviceRow(
    device: RemoteDevice,
    connected: Boolean,
    saved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onUnpair: (() -> Unit)? = null,
    onClick: () -> Unit
) {"""

old_icon = """            // V2 M4 — quick-switch bookmark (bonded rows only).
            if (onToggleSave != null) {
                IconButton(onClick = onToggleSave) {
                    Text(
                        if (saved) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (saved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }"""

new_icon = """            // V2 M4 — quick-switch bookmark (bonded rows only).
            if (onToggleSave != null) {
                IconButton(onClick = onToggleSave) {
                    Text(
                        if (saved) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (saved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onUnpair != null) {
                IconButton(onClick = onUnpair) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Delete,
                        contentDescription = "Unpair",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }"""

text = text.replace(old_bonded, new_bonded)
text = text.replace(old_row, new_row)
text = text.replace(old_icon, new_icon)

# Add Delete icon import if needed. We can just use the fully qualified name or add to import list.
text = text.replace("import androidx.compose.material.icons.rounded.Computer", "import androidx.compose.material.icons.rounded.Computer\nimport androidx.compose.material.icons.rounded.Delete")

with open('app/src/main/java/com/aeropad/remote/ui/screens/devices/DevicesScreen.kt', 'w') as f:
    f.write(text)
