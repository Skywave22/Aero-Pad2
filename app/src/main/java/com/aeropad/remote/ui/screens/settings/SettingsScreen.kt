package com.aeropad.remote.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HapticIntensity
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.ui.components.GlassCard
import com.aeropad.remote.viewmodel.SettingsViewModel

/**
 * Settings — rebuilt clean and flat.
 *
 * Appearance is exactly one choice: Light / Dark / Follow system.
 * Every control writes through the sanitizing SettingsStore immediately
 * (no save button needed) and UI state comes straight back from DataStore,
 * so what you see is always what is persisted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen3DPreview: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val app by viewModel.app.collectAsState()
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    /** Search: a row matches when its label contains the query. */
    fun matches(vararg labels: String): Boolean =
        query.isBlank() || labels.any { it.contains(query, ignoreCase = true) }
    val mouse by viewModel.mouse.collectAsState()
    val keyboard by viewModel.keyboard.collectAsState()
    val gamepad by viewModel.gamepad.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search settings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            // ---------- Appearance ----------
            if (matches("appearance", "theme", "light", "dark", "mode")) SettingsGroup("Appearance") {
                Text(
                    "White or black — pick how AeroPad looks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeCard(
                        label = "Light",
                        icon = Icons.Rounded.LightMode,
                        selected = app.theme == ThemeMode.LIGHT,
                        onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeCard(
                        label = "Dark",
                        icon = Icons.Rounded.DarkMode,
                        selected = app.theme == ThemeMode.DARK,
                        onClick = { viewModel.setTheme(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeCard(
                        label = "Auto",
                        icon = Icons.Rounded.SettingsBrightness,
                        selected = app.theme == ThemeMode.SYSTEM,
                        onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---------- Behavior ----------
            if (matches("fullscreen", "screen", "keep", "secure", "fps", "automation", "motion")) SettingsGroup("Behavior") {
                ToggleRow("Keep screen on", app.keepScreenOn, viewModel::setKeepScreenOn)
                ToggleRow("Fullscreen mode", app.fullscreenMode, viewModel::setFullscreen)
                ToggleRow(
                    "Secure screen",
                    app.secureScreen,
                    viewModel::setSecureScreen,
                    subtitle = "Blocks screenshots and app-switcher preview"
                )
                ToggleRow(
                    "Reduce motion",
                    app.reduceMotion,
                    viewModel::setReduceMotion,
                    subtitle = "Turns off 3D tilts, parallax and flip transitions"
                )
                ToggleRow(
                    "FPS overlay",
                    app.fpsOverlay,
                    viewModel::setFpsOverlay,
                    subtitle = "Real-time frame rate in the corner (measured, Choreographer)"
                )
                ToggleRow(
                    "Automation API (Tasker)",
                    app.automationApi,
                    viewModel::setAutomationApi,
                    subtitle = "Let automation apps play macros, media keys and type text via broadcast. Off = all external commands dropped."
                )
            }

            // ---------- Haptics ----------
            if (matches("haptic", "vibration", "feedback")) SettingsGroup("Haptics") {
                ToggleRow("Touch vibrations", app.touchVibrations, viewModel::setTouchVibrations)
                if (app.touchVibrations) {
                    Text(
                        "Vibration strength",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        HapticIntensity.entries.forEach { level ->
                            ChoiceChip(
                                label = level.name.lowercase().replaceFirstChar { it.uppercase() },
                                selected = app.hapticIntensity == level,
                                onClick = { viewModel.setHapticIntensity(level) }
                            )
                        }
                    }
                }
            }

            // ---------- Visual style ----------
            if (matches("3d", "quality", "icon", "pack", "preview")) SettingsGroup("Visual style") {
                Text("3D quality", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf("FULL", "REDUCED", "FLAT").forEach { q ->
                        ChoiceChip(
                            label = q.lowercase().replaceFirstChar { it.uppercase() },
                            selected = app.quality3D == q,
                            onClick = { viewModel.setQuality3D(q) }
                        )
                    }
                }
                Text("Icon pack", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf("FILLED", "OUTLINED", "ROUNDED", "SHARP").forEach { pack ->
                        ChoiceChip(
                            label = pack.lowercase().replaceFirstChar { it.uppercase() },
                            selected = app.iconPack == pack,
                            onClick = { viewModel.setIconPack(pack) }
                        )
                    }
                }
                TextButton(onClick = onOpen3DPreview) { Text("Open 3D effects preview") }
            }

            // ---------- Accessibility ----------
            if (matches("access", "talkback", "speak", "spoken", "reader")) SettingsGroup("Accessibility") {
                ToggleRow(
                    "Spoken connection alerts",
                    app.spokenAlerts,
                    viewModel::setSpokenAlerts,
                    subtitle = "Announce connect/disconnect through TalkBack (silent when no screen reader is active)"
                )
                Text(
                    "More: Reduce motion lives under Behavior • gamepad controls " +
                        "announce their bindings to TalkBack • the layout editor flags " +
                        "controls under the 48dp touch target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- Mouse ----------
            if (matches("mouse", "trackpad", "sensitivity", "scroll", "smoothing", "pen", "tap")) SettingsGroup("Mouse & trackpad") {
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)
                SliderRow("Scroll speed", mouse.scrollSpeed, viewModel::setScrollSpeed)
                SliderRow("Movement smoothing", mouse.movementSmoothing, viewModel::setMovementSmoothing)
                ToggleRow("Invert scroll", mouse.invertScroll, viewModel::setInvertScroll)
                ToggleRow("Tap to click", mouse.tapToClick, viewModel::setTapToClick)
                ToggleRow("Pen mode", mouse.penMode, viewModel::setPenMode, subtitle = "Slower, precise pointer")
            }

            // ---------- Keyboard ----------
            if (matches("keyboard", "text", "input")) SettingsGroup("Keyboard") {
                ToggleRow("Show text input bar", keyboard.showTextInputBar, viewModel::setShowTextInputBar)
            }

            // ---------- Gamepad ----------
            if (matches("gamepad", "joystick", "dead", "haptic", "mode")) SettingsGroup("Gamepad") {
                Text(
                    "Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    GamepadMappingMode.entries.forEach { mode ->
                        val label = gamepadModeLabel(mode)
                        val selected = gamepad.mappingMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setGamepadMode(mode) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                SliderRow("Joystick sensitivity", gamepad.joystickSensitivity, viewModel::setJoystickSensitivity)
                SliderRow(
                    "Dead zone", gamepad.deadZone, viewModel::setDeadZone,
                    max = com.aeropad.remote.model.GamepadSettings.DEAD_ZONE_MAX
                )
                ToggleRow("Haptic feedback", gamepad.hapticFeedback, viewModel::setHapticFeedback)
            }

            Spacer(Modifier.height(110.dp)) // room for the floating dock
        }
    }
}

private fun gamepadModeLabel(mode: GamepadMappingMode): String = when (mode) {
    GamepadMappingMode.HID_GAMEPAD -> "HID gamepad (real controller)"
    GamepadMappingMode.KEYBOARD_FALLBACK -> "Keyboard fallback (WASD/keys)"
    GamepadMappingMode.MOUSE_KEYBOARD -> "Mouse + keyboard hybrid"
}

// ----------------------------------------------------------------------
// Reusable rows
// ----------------------------------------------------------------------

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** Appearance mode card: icon + label, highlighted when active. */
@Composable
private fun ModeCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(50)
            )
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    max: Int = 100
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )
    }
}
