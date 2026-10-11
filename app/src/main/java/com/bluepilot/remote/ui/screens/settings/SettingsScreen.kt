package com.bluepilot.remote.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.model.HapticIntensity
import com.bluepilot.remote.model.ThemeMode
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.theme.LocalAppTheme
import com.bluepilot.remote.viewmodel.SettingsViewModel

/**
 * Full settings screen: General / Mouse / Keyboard / Gamepad groups.
 * Every control writes through the sanitizing SettingsStore immediately
 * (no save button needed) and UI state comes straight back from DataStore,
 * so what you see is always what is persisted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenThemes: () -> Unit = {},
    onOpen3DPreview: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val app by viewModel.app.collectAsState()
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    /** UI/UX redesign: search — a row matches when its label contains the query. */
    fun matches(vararg labels: String): Boolean =
        query.isBlank() || labels.any { it.contains(query, ignoreCase = true) }
    val mouse by viewModel.mouse.collectAsState()
    val keyboard by viewModel.keyboard.collectAsState()
    val gamepad by viewModel.gamepad.collectAsState()
    val spec = LocalAppTheme.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (spec.monoFont) "SETTINGS" else "Settings") },
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
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search settings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            // ---------- General ----------
            if (matches("theme", "fullscreen", "screen", "vibration", "secure", "gallery", "motion", "3d", "icon", "pack")) SettingsGroup("General") {
                OutlinedButton(
                    onClick = onOpenThemes,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = spec.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = spec.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (spec.monoFont) "OPEN THEME GALLERY" else "Open theme gallery",
                        style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (spec.monoFont) "THEME" else "Theme",
                    style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                        FilterChip(
                            selected = app.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = {
                                Text(
                                    text = if (spec.monoFont) label.uppercase() else label,
                                    style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                                    else MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                ToggleRow("Fullscreen mode", app.fullscreenMode, viewModel::setFullscreen)
                ToggleRow("Keep screen on", app.keepScreenOn, viewModel::setKeepScreenOn)
                ToggleRow("Touch vibrations", app.touchVibrations, viewModel::setTouchVibrations)
                if (app.touchVibrations) {
                    Text(
                        text = if (spec.monoFont) "VIBRATION STRENGTH" else "Vibration strength",
                        style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        HapticIntensity.entries.forEach { level ->
                            val label = level.name.lowercase().replaceFirstChar { it.uppercase() }
                            FilterChip(
                                selected = app.hapticIntensity == level,
                                onClick = { viewModel.setHapticIntensity(level) },
                                label = {
                                    Text(
                                        text = if (spec.monoFont) label.uppercase() else label,
                                        style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                                        else MaterialTheme.typography.labelLarge
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
                Text("3D quality", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf("FULL", "REDUCED", "FLAT").forEach { q ->
                        FilterChip(
                            selected = app.quality3D == q,
                            onClick = { viewModel.setQuality3D(q) },
                            label = { Text(q.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                // AUDIT FIX: the 3D showcase screen existed but was
                // unreachable — this button was never rendered.
                TextButton(onClick = onOpen3DPreview) { Text("Open 3D effects preview") }
                Text("Icon pack", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf("FILLED", "OUTLINED", "ROUNDED", "SHARP").forEach { pack ->
                        FilterChip(
                            selected = app.iconPack == pack,
                            onClick = { viewModel.setIconPack(pack) },
                            label = { Text(pack.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
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
                ToggleRow(
                    "Reduce motion",
                    app.reduceMotion,
                    viewModel::setReduceMotion,
                    subtitle = "Turns off 3D tilts, parallax and flip transitions"
                )
                ToggleRow(
                    "Secure screen",
                    app.secureScreen,
                    viewModel::setSecureScreen,
                    subtitle = "Blocks screenshots and app-switcher preview"
                )
            }

            // ---------- V2 MATRIX 5 b2: Accessibility ----------
            if (matches("access", "talkback", "speak", "spoken", "reader", "motion")) SettingsGroup("Accessibility") {
                ToggleRow(
                    "Spoken connection alerts",
                    app.spokenAlerts,
                    viewModel::setSpokenAlerts,
                    subtitle = "Announce connect/disconnect through TalkBack (silent when no screen reader is active)"
                )
                Text(
                    "More: Reduce motion lives under General • gamepad controls " +
                        "announce their bindings to TalkBack • the layout editor flags " +
                        "controls under the 48dp touch target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- SECTION 1: Auto theme scheduling ----------
            if (matches("theme", "auto", "schedule", "night", "day")) SettingsGroup("Auto theme schedule") {
                ToggleRow(
                    "Switch theme by time of day",
                    app.autoThemeEnabled,
                    viewModel::setAutoTheme,
                    subtitle = "Day theme by day, night theme at night (checked every minute)"
                )
                ToggleRow(
                    "Switch theme by room light",
                    app.lightAutoTheme,
                    viewModel::setLightAutoTheme,
                    subtitle = "Real light sensor: dark room → night theme (overrides the clock; needs a light sensor)"
                )
                if (app.autoThemeEnabled) {
                    Text("Day theme", style = MaterialTheme.typography.bodyMedium)
                    ThemePickerRow(
                        selectedId = app.autoDayTheme,
                        dark = false,
                        onPick = viewModel::setAutoDayTheme
                    )
                    Text("Night theme", style = MaterialTheme.typography.bodyMedium)
                    ThemePickerRow(
                        selectedId = app.autoNightTheme,
                        dark = true,
                        onPick = viewModel::setAutoNightTheme
                    )
                    SliderRow(
                        "Night starts (hour)", app.autoNightStart,
                        viewModel::setAutoNightStart, max = 23
                    )
                    SliderRow(
                        "Night ends (hour)", app.autoNightEnd,
                        viewModel::setAutoNightEnd, max = 23
                    )
                }
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
                    text = if (spec.monoFont) "MODE" else "Mode",
                    style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    GamepadMappingMode.entries.forEach { mode ->
                        val label = gamepadModeLabel(mode)
                        FilterChip(
                            selected = gamepad.mappingMode == mode,
                            onClick = { viewModel.setGamepadMode(mode) },
                            label = {
                                Text(
                                    text = if (spec.monoFont) label.uppercase() else label,
                                    style = if (spec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                                    else MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                SliderRow("Joystick sensitivity", gamepad.joystickSensitivity, viewModel::setJoystickSensitivity)
                SliderRow(
                    "Dead zone", gamepad.deadZone, viewModel::setDeadZone,
                    max = com.bluepilot.remote.model.GamepadSettings.DEAD_ZONE_MAX
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
    val spec = LocalAppTheme.current
    Text(
        text = if (spec.monoFont) title.uppercase() else title,
        style = if (spec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
        else MaterialTheme.typography.titleMedium,
        color = spec.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    val spec = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (spec.monoFont) title.uppercase() else title,
                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = if (spec.monoFont) subtitle.uppercase() else subtitle,
                    style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = spec.primary,
                checkedTrackColor = spec.primary.copy(alpha = 0.35f),
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
    val spec = LocalAppTheme.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (spec.monoFont) title.uppercase() else title,
                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value",
                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = spec.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = spec.primary,
                activeTrackColor = spec.primary,
                inactiveTrackColor = spec.outline.copy(alpha = 0.3f)
            )
        )
    }
}

/** SECTION 1 — compact theme chips for the auto-schedule pickers. */
@Composable
private fun ThemePickerRow(
    selectedId: String,
    dark: Boolean,
    onPick: (String) -> Unit
) {
    val options = com.bluepilot.remote.ui.theme.BuiltInThemes.ALL.filter { it.isDark == dark }
    LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        items(options, key = { it.id }) { theme ->
            FilterChip(
                selected = theme.id == selectedId,
                onClick = { onPick(theme.id) },
                label = { Text(theme.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(theme.primary, CircleShape)
                    )
                }
            )
        }
    }
}
