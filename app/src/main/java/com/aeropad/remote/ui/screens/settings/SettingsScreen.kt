package com.aeropad.remote.ui.screens.settings

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.shape.CircleShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.BuildConfig
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.HapticIntensity
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.PowerProfile
import com.aeropad.remote.model.ThemeMode
import com.aeropad.remote.ui.components.GlassCard
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.Material3D
import com.aeropad.remote.ui.components.surface3D
import com.aeropad.remote.viewmodel.SettingsViewModel

/**
 * Settings grouped into 4 clear sections (P9.9):
 *  1. Appearance & Visual Style (including inline 3D preview card — R4)
 *  2. Controls — Mouse & trackpad, Keyboard & Gamepad (including keyTapDelayMs — L2)
 *  3. Connection & Power (including Power profile — A12)
 *  4. Advanced & Diagnostics (Automation API + token — H2/P9.5, Diagnostics export — A6)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHealth: (() -> Unit)? = null,
    onReplayTutorial: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    /** Search: a row matches when its label contains the query. */
    fun matches(vararg labels: String): Boolean =
        query.isBlank() || labels.any { it.contains(query, ignoreCase = true) }
    val mouse by viewModel.mouse.collectAsStateWithLifecycle()
    val keyboard by viewModel.keyboard.collectAsStateWithLifecycle()
    val gamepad by viewModel.gamepad.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val activeToken = app.automationToken
    LaunchedEffect(app.automationToken) {
        if (app.automationToken.isBlank()) {
            viewModel.ensureAutomationToken()
        }
    }

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
                // UI fix — reserve room for the floating dock. This screen is a
                // top-level hub so the dock is always drawn over it, but there
                // was no bottom padding, meaning the last settings rows were
                // permanently unreachable behind it.
                .padding(bottom = com.aeropad.remote.ui.components.DockMetrics.height + 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search settings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            // ---------- GROUP 1: Appearance ----------
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

                Spacer(Modifier.height(14.dp))
                Text(
                    "Accent colour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                com.aeropad.remote.ui.components.ScrollableChipRow {
                    val isDark = app.theme == ThemeMode.DARK ||
                        (app.theme == ThemeMode.SYSTEM && com.aeropad.remote.ui.theme.LocalAppTheme.current.isDark)
                    com.aeropad.remote.ui.theme.AccentPalette.entries.forEach { palette ->
                        val selected = app.accentColor == palette.id
                        val dot = palette.accent(isDark)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(dot)
                                .then(
                                    if (selected) Modifier.border(
                                        width = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    ) else Modifier
                                )
                                .clickable { viewModel.setAccentColor(palette.id) }
                                .semantics {
                                    contentDescription = palette.label + " accent"
                                    this.role = Role.RadioButton
                                    this.selected = selected
                                }
                        )
                    }
                }
            }

            // ---------- GROUP 2: Controls — Mouse & trackpad (kept near top for quick access & UI tests) ----------
            if (matches("mouse", "trackpad", "sensitivity", "scroll", "smoothing", "pen", "tap")) SettingsGroup("Mouse & trackpad") {
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)
                SliderRow("Scroll speed", mouse.scrollSpeed, viewModel::setScrollSpeed)
                SliderRow("Movement smoothing", mouse.movementSmoothing, viewModel::setMovementSmoothing)
                ToggleRow("Invert scroll", mouse.invertScroll, viewModel::setInvertScroll)
                ToggleRow("Tap to click", mouse.tapToClick, viewModel::setTapToClick)
                ToggleRow("Pen mode", mouse.penMode, viewModel::setPenMode, subtitle = "Slower, precise pointer")
                ToggleRow(
                    "Left-handed mode",
                    mouse.leftHanded,
                    viewModel::setLeftHanded,
                    subtitle = "Swaps tap and click sides, mirrors the buttons"
                )
            }

            // ---------- Visual style (with R4 inline 3D preview) ----------
            if (matches("3d", "quality", "icon", "pack", "preview", "style", "glass", "neon")) SettingsGroup("Visual style") {
                Text("UI style", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf("MATERIAL" to "Material", "GLASS" to "Glass", "NEON" to "Neon").forEach { (id, label) ->
                        ChoiceChip(
                            label = label,
                            selected = app.uiStyle == id,
                            onClick = { viewModel.setUiStyle(id) }
                        )
                    }
                }
                Text("3D quality", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf("FULL", "REDUCED", "FLAT").forEach { q ->
                        ChoiceChip(
                            label = q.lowercase().replaceFirstChar { it.uppercase() },
                            selected = app.quality3D == q,
                            onClick = { viewModel.setQuality3D(q) }
                        )
                    }
                }
                Text("Icon pack", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf("FILLED", "OUTLINED", "ROUNDED", "SHARP").forEach { pack ->
                        ChoiceChip(
                            label = pack.lowercase().replaceFirstChar { it.uppercase() },
                            selected = app.iconPack == pack,
                            onClick = { viewModel.setIconPack(pack) }
                        )
                    }
                }
                // R4 — Inline 3D Material & Keycap Preview (replaces standalone Preview3DScreen tile)
                Spacer(Modifier.height(8.dp))
                var previewElevation by remember { mutableFloatStateOf(8f) }
                Text("Live 3D material preview", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Material3D.entries.forEach { m ->
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .weight(1f)
                                .surface3D(
                                    base = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small,
                                    material = m,
                                    elevation = previewElevation.dp
                                )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyCard("Tap 3D", Modifier.weight(1f), 44.dp) {}
                    KeyCard("Accent 3D", Modifier.weight(1f), 44.dp, emphasized = true) {}
                }
                Slider(
                    value = previewElevation,
                    onValueChange = { previewElevation = it },
                    valueRange = 0f..20f
                )
            }

            // ---------- Keyboard (L2 configurable keyTapDelayMs) ----------
            if (matches("keyboard", "text", "input", "delay", "tap")) SettingsGroup("Keyboard") {
                ToggleRow("Show text input bar", keyboard.showTextInputBar, viewModel::setShowTextInputBar)
                SliderRow(
                    title = "Key tap delay (ms) — raise for Smart TVs / BIOS",
                    value = keyboard.keyTapDelayMs,
                    onChange = viewModel::setKeyTapDelayMs,
                    min = KeyboardSettings.DELAY_MIN,
                    max = KeyboardSettings.DELAY_MAX
                )
            }

            // ---------- Gamepad & Haptics ----------
            if (matches("gamepad", "joystick", "dead", "haptic", "mode", "vibration", "feedback")) SettingsGroup("Gamepad & Haptics") {
                ToggleRow("Touch vibrations", app.touchVibrations, viewModel::setTouchVibrations)
                if (app.touchVibrations) {
                    Text(
                        "Vibration strength",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        HapticIntensity.entries.forEach { level ->
                            ChoiceChip(
                                label = level.name.lowercase().replaceFirstChar { it.uppercase() },
                                selected = app.hapticIntensity == level,
                                onClick = { viewModel.setHapticIntensity(level) }
                            )
                        }
                    }
                }
                Text(
                    "Gamepad mode",
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
                ToggleRow("Gamepad haptic feedback", gamepad.hapticFeedback, viewModel::setHapticFeedback)
            }

            // ---------- GROUP 3: Connection & Power (P9.9 + A12) ----------
            if (matches("connection", "power", "battery", "hid", "extended", "compat", "screen", "keep")) SettingsGroup("Connection & Power") {
                ToggleRow("Keep screen on", app.keepScreenOn, viewModel::setKeepScreenOn)
                ToggleRow("Fullscreen mode", app.fullscreenMode, viewModel::setFullscreen)
                Text(
                    "Power profile (gyro rate & keep-alive interval)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    PowerProfile.entries.forEach { profile ->
                        ChoiceChip(
                            label = profile.label,
                            selected = app.powerProfile == profile,
                            onClick = { viewModel.setPowerProfile(profile) }
                        )
                    }
                }
                ToggleRow(
                    "Extended HID reports",
                    app.extendedReports,
                    viewModel::setExtendedReports,
                    subtitle = "Adds the touch-screen digitizer + analog triggers to the Bluetooth descriptor. " +
                        "Off (default) uses the classic, proven-stable descriptor — turn off if your host drops the connection."
                )
            }

            // ---------- GROUP 4: Advanced, Accessibility & Automation API (P9.5, H2, A6) ----------
            if (matches("advanced", "access", "talkback", "speak", "spoken", "reader", "secure", "fps", "automation", "motion", "diag", "token")) SettingsGroup("Advanced & Automation API") {
                ToggleRow(
                    "Spoken connection alerts",
                    app.spokenAlerts,
                    viewModel::setSpokenAlerts,
                    subtitle = "Announce connect/disconnect through TalkBack (silent when no screen reader is active)"
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
                ToggleRow(
                    "FPS overlay",
                    app.fpsOverlay,
                    viewModel::setFpsOverlay,
                    subtitle = "Real-time frame rate in the corner (measured, Choreographer)"
                )
                ToggleRow(
                    "Automation API (Tasker / ADB)",
                    app.automationApi,
                    viewModel::setAutomationApi,
                    subtitle = "Let automation apps play macros, media keys and type text via broadcast. Requires secret token below."
                )
                if (app.automationApi) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Secret Token: $activeToken",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "adb shell am broadcast -a com.aeropad.remote.AUTOMATION --es token $activeToken --es cmd type --es arg \"Hello\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("AeroPad Automation Token", activeToken))
                                Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Copy token") }
                        )
                        AssistChip(
                            onClick = {
                                val next = viewModel.regenerateAutomationToken()
                                Toast.makeText(context, "New token: ${next.take(6)}…", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Regenerate token") }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // A6 — One-tap Diagnostics Export
                Button(
                    onClick = {
                        val report = buildString {
                            appendLine("=== AeroPad Diagnostics Report ===")
                            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                            appendLine("Theme: ${app.theme} | UI Style: ${app.uiStyle} | 3D: ${app.quality3D}")
                            appendLine("Extended HID Reports: ${app.extendedReports} | Power Profile: ${app.powerProfile}")
                            appendLine("Mouse: sens=${mouse.sensitivity}, scroll=${mouse.scrollSpeed}, smooth=${mouse.movementSmoothing}")
                            appendLine("Keyboard: keyTapDelayMs=${keyboard.keyTapDelayMs}, showTextBar=${keyboard.showTextInputBar}")
                            appendLine("Gamepad: mode=${gamepad.mappingMode}, sens=${gamepad.joystickSensitivity}, deadZone=${gamepad.deadZone}")
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AeroPad Diagnostics", report))
                        Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy diagnostics report")
                }
                if (onOpenHealth != null) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenHealth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Connection Health & Diagnostics")
                    }
                }
                if (onReplayTutorial != null) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = onReplayTutorial,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Replay Welcome Tutorial")
                    }
                }
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
            maxLines = 1,
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
            maxLines = 1,
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
    min: Int = 0,
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
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )
    }
}
