import re

with open('app/src/main/java/com/aeropad/remote/ui/screens/settings/SettingsScreen.kt', 'r') as f:
    text = f.read()

old_mouse_group = """            // ---------- Mouse ----------
            if (matches("mouse", "trackpad", "sensitivity", "scroll", "smoothing", "pen", "tap", "acceleration", "profile", "drag", "edge")) SettingsGroup("Mouse & trackpad") {
                // Profile dropdown missing standard combo box, so just list buttons or radio. Let's use SegmentedRow or list.
                // Simple representation:
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)"""

new_mouse_group = """            // ---------- Mouse ----------
            if (matches("mouse", "trackpad", "sensitivity", "scroll", "smoothing", "pen", "tap", "acceleration", "profile", "drag", "edge")) SettingsGroup("Mouse & trackpad") {
                Text("Trackpad profile", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                    com.aeropad.remote.model.TrackpadProfile.entries.forEach { p ->
                        val label = p.name.lowercase().replaceFirstChar { it.uppercase() }
                        FilterChip(
                            selected = mouse.profile == p,
                            onClick = { viewModel.setTrackpadProfile(p) },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)"""

text = text.replace(old_mouse_group, new_mouse_group)

with open('app/src/main/java/com/aeropad/remote/ui/screens/settings/SettingsScreen.kt', 'w') as f:
    f.write(text)
