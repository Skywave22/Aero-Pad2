package com.aeropad.remote.ui.screens.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.DpadDirection
import com.aeropad.remote.model.GamepadButton
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.ui.components.KeyCard
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.RemoteControlViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Gamepad: virtual left stick (drag), D-pad, ABXY, shoulder + menu buttons.
 * Behavior depends on the mapping mode in Settings (HID / keyboard / hybrid).
 * Gamepad state resets to neutral when leaving the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadScreen(
    onBack: () -> Unit,
    onOpenBuilder: (() -> Unit)? = null,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val gamepadSettings by viewModel.gamepadSettings.collectAsStateWithLifecycle()
    val vibration by viewModel.vibrationsEnabled.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(vibration)
    val scope = rememberCoroutineScope()
    // Round 7 — Pro gamepad.
    val settingsVm: SettingsViewModel = hiltViewModel()
    val profiles by viewModel.gamepadProfiles.profiles.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(false) }

    // Gyro aim / tilt steering: sensors live only while enabled.
    val motionOn = gamepadSettings.gyroAim || gamepadSettings.tiltSteer
    LaunchedEffect(motionOn) {
        if (!motionOn) {
            viewModel.motionSource.stop()
            return@LaunchedEffect
        }
        viewModel.motionSource.start()
        viewModel.motionSource.motion.collect { m ->
            viewModel.onMotion(m.steer, m.aimX, m.aimY)
        }
    }

    // Neutralize everything when this screen goes away.
    DisposableEffect(Unit) {
        onDispose { viewModel.resetGamepad() }
    }

    // Round 14 — landscape a 64 dp app bar + a 50 dp banner left the pad ~156 dp
    // of usable height, which clipped the A button and both triggers. Chrome
    // goes before the controller: landscape drops the app bar entirely and
    // floats a small back chip in its place.
    val fullBleedPad = com.aeropad.remote.ui.components.isLandscape()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            if (!fullBleedPad) TopAppBar(
                title = { Text("Gamepad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onOpenBuilder != null) {
                        AssistChip(
                            onClick = onOpenBuilder,
                            label = { Text("🕹️ Builder") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(if (fullBleedPad) 8.dp else 16.dp)
                // Round 14 - must NOT scroll here. A vertically scrollable
                // Column is measured with unbounded height, so the pad's
                // weight(1f) had nothing to resolve against and every cluster
                // inside was clipped into slivers (A became a green sliver, the
                // d-pad lost three arrows) regardless of scale factor. The
                // block is scaled to fit the window instead.
        ) {
            if (fullBleedPad) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Gamepad",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (onOpenBuilder != null) {
                        AssistChip(
                            onClick = onOpenBuilder,
                            label = { Text("Builder") }
                        )
                    }
                }
            }

            NotConnectedBanner(!isConnected)
            // Round 14 — in landscape this line competed with the pad for a
            // ~270 dp tall window; the mode is also shown in Settings, so it is
            // portrait-only now.
            val landscapePad = com.aeropad.remote.ui.components.isLandscape()
            if (!landscapePad) {
                Text(
                    text = "Mode: " + when (gamepadSettings.mappingMode) {
                        GamepadMappingMode.HID_GAMEPAD -> "HID gamepad"
                        GamepadMappingMode.KEYBOARD_FALLBACK -> "Keyboard fallback"
                        GamepadMappingMode.MOUSE_KEYBOARD -> "Mouse + keyboard"
                    } + " (change in Settings)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            // Round 12 — on a real phone in landscape this weighted row was
            // squeezed to ~40 dp: the sticks, d-pad and ABXY collapsed into
            // slivers that looked like broken sliders. Landscape therefore gets
            // a fixed, thumb-sized control deck inside a scrolling column;
            // portrait keeps sharing the leftover height as before.
            // Round 14 — this pad is ~380 dp of content in a ~270 dp landscape
            // window, so the bottom of the ABXY cluster, both analog triggers
            // and Select/Home/Start sat below the screen edge and could not be
            // reached (the Column does not scroll). The rows are now one block
            // that is scaled uniformly to the height that actually exists, so
            // nothing is ever clipped and the controller keeps its proportions.
            // Sizes are derived from the height that actually exists rather
            // than shrinking a fixed block: a transformed block is still
            // laid out at its natural size and gets clipped by whatever
            // contains it, which is what produced a green sliver for A and a
            // d-pad missing its bottom arrow.
            @Composable
            fun androidx.compose.foundation.layout.ColumnScope.padContent(m: PadMetrics) {
                val landscapeDeck = com.aeropad.remote.ui.components.isLandscape()
                Row(
                    // Round 14 — 210 dp was shorter than the right-hand column
                    // it has to contain (L1/R1 + Y + X/B + A at 64 dp), so the
                    // A button was clipped inside the row. 290 dp fits the
                    // whole cluster; the wrapper above then scales the whole
                    // block down to the window.
                    modifier = if (landscapeDeck) Modifier.fillMaxWidth().height(m.deck)
                    else Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ---------- Left: stick + dpad ----------
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        VirtualStick(
                            onMove = { x, y -> viewModel.onStick(left = true, rawX = x, rawY = y) },
                            onRelease = { viewModel.onStick(left = true, rawX = 0f, rawY = 0f) },
                            baseSize = m.stick
                        )
                        DpadCluster(
                            onPress = { dir -> haptic(); viewModel.onDpad(dir) },
                            onRelease = { viewModel.onDpad(DpadDirection.NONE) },
                            keySize = m.dpad
                        )
                    }

                    // ---------- Right: ABXY + shoulders ----------
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FaceButton("L1", size = m.face) { pressed -> viewModel.onGamepadButton(GamepadButton.L1, pressed); if (pressed) haptic() }
                            FaceButton("R1", size = m.face) { pressed -> viewModel.onGamepadButton(GamepadButton.R1, pressed); if (pressed) haptic() }
                        }
                        // ABXY diamond — classic controller colors (Y amber,
                        // X blue, B red, A green) with 3D gel shading.
                        FaceButton("Y", size = m.face, tint = Color(0xFFF5A623)) { pressed -> viewModel.onGamepadButton(GamepadButton.Y, pressed); if (pressed) haptic() }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            FaceButton("X", size = m.face, tint = Color(0xFF2F6BFF)) { pressed -> viewModel.onGamepadButton(GamepadButton.X, pressed); if (pressed) haptic() }
                            FaceButton("B", size = m.face, tint = Color(0xFFE74C3C)) { pressed -> viewModel.onGamepadButton(GamepadButton.B, pressed); if (pressed) haptic() }
                        }
                        FaceButton("A", size = m.face, tint = Color(0xFF2ECC71)) { pressed -> viewModel.onGamepadButton(GamepadButton.A, pressed); if (pressed) haptic() }
                    }
                }

                // ---------- Round 7 — Pro: analog triggers + pro panel ----------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TriggerPad("L2") { v -> viewModel.onTrigger(right = false, v) }
                    AssistChip(onClick = { showPro = true }, label = { Text("⚙ Pro") })
                    TriggerPad("R2") { v -> viewModel.onTrigger(right = true, v) }
                }

                // ---------- Bottom: menu buttons ----------
                Row(
                    // UI fix: was a fixed .height(48.dp) Row, which CLIPPED the
                    // Select/Home/Start labels once the user raised the system
                    // font size. heightIn(min=) keeps the touch target while
                    // letting the row grow with the text.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KeyCard("Select", Modifier.weight(1f)) { haptic(); scope.launch { pressAndRelease(viewModel, GamepadButton.SELECT) } }
                    KeyCard("Home", Modifier.weight(1f)) { haptic(); scope.launch { pressAndRelease(viewModel, GamepadButton.HOME) } }
                    KeyCard("Start", Modifier.weight(1f)) { haptic(); scope.launch { pressAndRelease(viewModel, GamepadButton.START) } }
                }
            }
            if (landscapePad) {
                // Landscape is wide and short. Stacking the stick above the
                // d-pad needs ~270 dp of height that a 270 dp window does not
                // have once the banner is drawn, so landscape lays the
                // controller out ACROSS the screen instead: L2 + d-pad, the
                // stick, ABXY with L1/R1, then R2. Everything is on screen and
                // thumb-sized; nothing is clipped or scaled.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    // Every cluster is sized from the row height, and the row
                    // height leaves room for the menu row plus a little slack,
                    // so nothing can spill past the bottom edge. The right
                    // column has 5 rows (L1/R1 + Y + X/B + A + spacing) and the
                    // left column is a trigger above a 3-row d-pad.
                    val bottomRow = 36.dp
                    val pad = (maxHeight - bottomRow - 12.dp).coerceIn(90.dp, 220.dp)
                    val face = ((pad - 18.dp) / 5).coerceIn(22.dp, 56.dp)
                    val trigger = (pad * 0.36f).coerceIn(32.dp, 92.dp)
                    val dpad = ((pad - trigger - 20.dp) / 3).coerceIn(18.dp, 44.dp)
                    val stick = (pad * 0.9f).coerceIn(64.dp, 140.dp)

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(pad),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                TriggerPad("L2", height = trigger) { v -> viewModel.onTrigger(right = false, v) }
                                Spacer(Modifier.height(6.dp))
                                DpadCluster(
                                    onPress = { dir -> haptic(); viewModel.onDpad(dir) },
                                    onRelease = { viewModel.onDpad(DpadDirection.NONE) },
                                    keySize = dpad
                                )
                            }
                            Spacer(Modifier.weight(0.5f))
                            VirtualStick(
                                onMove = { x, y -> viewModel.onStick(left = true, rawX = x, rawY = y) },
                                onRelease = { viewModel.onStick(left = true, rawX = 0f, rawY = 0f) },
                                baseSize = stick
                            )
                            Spacer(Modifier.weight(0.5f))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FaceButton("L1", size = face) { pressed -> viewModel.onGamepadButton(GamepadButton.L1, pressed); if (pressed) haptic() }
                                    FaceButton("R1", size = face) { pressed -> viewModel.onGamepadButton(GamepadButton.R1, pressed); if (pressed) haptic() }
                                }
                                FaceButton("Y", size = face, tint = Color(0xFFF5A623)) { pressed -> viewModel.onGamepadButton(GamepadButton.Y, pressed); if (pressed) haptic() }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FaceButton("X", size = face, tint = Color(0xFF2F6BFF)) { pressed -> viewModel.onGamepadButton(GamepadButton.X, pressed); if (pressed) haptic() }
                                    FaceButton("B", size = face, tint = Color(0xFFE74C3C)) { pressed -> viewModel.onGamepadButton(GamepadButton.B, pressed); if (pressed) haptic() }
                                }
                                FaceButton("A", size = face, tint = Color(0xFF2ECC71)) { pressed -> viewModel.onGamepadButton(GamepadButton.A, pressed); if (pressed) haptic() }
                            }
                            Spacer(Modifier.weight(0.5f))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                TriggerPad("R2", height = trigger) { v -> viewModel.onTrigger(right = true, v) }
                                Spacer(Modifier.height(6.dp))
                                AssistChip(onClick = { showPro = true }, label = { Text("Pro") })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(bottomRow),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KeyCard("Select", Modifier.weight(1f)) { haptic(); scope.launch { pressAndRelease(viewModel, GamepadButton.SELECT) } }
                            KeyCard("Home", Modifier.weight(1f)) { haptic(); scope.launch { pressAndRelease(viewModel, GamepadButton.HOME) } }
                            KeyCard("Start", Modifier.weight(1f)) { haptic(); scope.launch { pressAndRelease(viewModel, GamepadButton.START) } }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    padContent(PadMetrics())
                }
            }
        }
    }

    if (showPro) {
        ProDialog(
            settings = gamepadSettings,
            profiles = profiles,
            onDismiss = { showPro = false },
            settingsVm = settingsVm,
            onSaveProfile = { name -> viewModel.gamepadProfiles.save(name, gamepadSettings) },
            onApplyProfile = { p -> settingsVm.applyGamepad(p.toSettings()) },
            onRemoveProfile = { p -> viewModel.gamepadProfiles.remove(p.id) },
            onExportAllJson = { viewModel.gamepadProfiles.exportAllJson() },
            onImportJson = { raw -> viewModel.gamepadProfiles.importJson(raw) }
        )
    }
}

private suspend fun pressAndRelease(viewModel: RemoteControlViewModel, button: GamepadButton) {
    viewModel.onGamepadButton(button, pressed = true)
    delay(40)
    viewModel.onGamepadButton(button, pressed = false)
}

/**
 * Draggable virtual joystick. Knob follows the finger inside the base circle;
 * releasing snaps back to center and emits (0,0).
 */
@Composable
private fun VirtualStick(
    onMove: (Float, Float) -> Unit,
    onRelease: () -> Unit,
    baseSize: androidx.compose.ui.unit.Dp = 140.dp
) {
    val knobSize = baseSize * 0.37f
    val density = LocalDensity.current
    val radiusPx = with(density) { (baseSize - knobSize).toPx() / 2f }
    var knob by remember { mutableStateOf(Offset.Zero) }

    val well = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(baseSize)
            // Recessed "well" look: dark rim at top (inner shadow illusion).
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.18f).compositeOver(well),
                        well,
                        Color.White.copy(alpha = 0.08f).compositeOver(well)
                    )
                ),
                CircleShape
            )
            .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { knob = Offset.Zero; onRelease() },
                    onDragCancel = { knob = Offset.Zero; onRelease() }
                ) { change, dragAmount ->
                    change.consume()
                    val next = knob + dragAmount
                    val clamped = if (next.getDistance() > radiusPx) {
                        next * (radiusPx / next.getDistance())
                    } else next
                    knob = clamped
                    onMove(
                        (clamped.x / radiusPx).coerceIn(-1f, 1f),
                        (clamped.y / radiusPx).coerceIn(-1f, 1f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val knobColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(knobSize)
                .graphicsLayer {
                    shadowElevation = 6.dp.toPx()
                    shape = CircleShape
                    clip = false
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f).compositeOver(knobColor),
                            knobColor,
                            Color.Black.copy(alpha = 0.22f).compositeOver(knobColor)
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
        )
    }
}

/**
 * Round press-and-release button reporting both press states.
 * 3D gel look: colored radial gradient dome + top highlight + press-sink.
 */
@Composable
private fun FaceButton(
    label: String,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    onPress: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "faceBtnSink"
    )
    val base = tint ?: MaterialTheme.colorScheme.surfaceVariant
    // UI fix — the A/B/X/Y buttons are tinted with a FIXED brand colour, so the
    // label must be chosen from that colour's luminance rather than assuming a
    // dark theme. Hard-coding Color.White made the labels unreadable whenever
    // the tint was light in Light mode.
    val labelColor = if (tint != null) {
        if (tint.luminance() > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // Dome shading: light hits top-left.
    fun lighten(c: Color, f: Float) = Color(
        (c.red + (1 - c.red) * f), (c.green + (1 - c.green) * f), (c.blue + (1 - c.blue) * f), c.alpha
    )
    fun darken(c: Color, f: Float) = Color(c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha)

    Box(
        modifier = Modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label button"
                role = Role.Button
                onClick(label = "Press $label") {
                    onPress(true)
                    onPress(false)
                    true
                }
            }
            .graphicsLayer {
                val s = 1f - 0.06f * sink
                scaleX = s; scaleY = s
                translationY = 2.dp.toPx() * sink
                shadowElevation = (6.dp.toPx()) * (1f - sink)
                shape = CircleShape
                clip = false
                if (tint != null) { spotShadowColor = tint; ambientShadowColor = tint }
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(lighten(base, 0.30f), base, darken(base, 0.25f))
                ),
                CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.any { it.pressed }) {
                            pressed = true
                            onPress(true)
                            // Wait for release
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            pressed = false
                            onPress(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = labelColor
        )
    }
}

/** D-pad: 4 direction keys in a plus layout. */
@Composable
private fun DpadCluster(
    onPress: (DpadDirection) -> Unit,
    onRelease: () -> Unit,
    keySize: androidx.compose.ui.unit.Dp = 44.dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DpadKey("▲", keySize) { pressed -> if (pressed) onPress(DpadDirection.UP) else onRelease() }
        Row(horizontalArrangement = Arrangement.spacedBy(keySize + 2.dp)) {
            DpadKey("◀", keySize) { pressed -> if (pressed) onPress(DpadDirection.LEFT) else onRelease() }
            DpadKey("▶", keySize) { pressed -> if (pressed) onPress(DpadDirection.RIGHT) else onRelease() }
        }
        DpadKey("▼", keySize) { pressed -> if (pressed) onPress(DpadDirection.DOWN) else onRelease() }
    }
}

@Composable
private fun DpadKey(
    label: String,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    onPressChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "dpadSink"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val dirDesc = when (label) {
        "▲" -> "D-pad Up"
        "▼" -> "D-pad Down"
        "◀" -> "D-pad Left"
        "▶" -> "D-pad Right"
        else -> "D-pad $label"
    }
    Box(
        modifier = Modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = dirDesc
                role = Role.Button
                onClick(label = dirDesc) {
                    onPressChange(true)
                    onPressChange(false)
                    true
                }
            }
            .graphicsLayer {
                val s = 1f - 0.07f * sink
                scaleX = s; scaleY = s
                shadowElevation = 4.dp.toPx() * (1f - sink)
                shape = CircleShape
                clip = false
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f).compositeOver(base),
                        base,
                        Color.Black.copy(alpha = 0.14f).compositeOver(base)
                    )
                ),
                CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.any { it.pressed }) {
                            pressed = true
                            onPressChange(true)
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            pressed = false
                            onPressChange(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Round 7 + H3 fix — pressure-style trigger: responds immediately on touch-down
 * (no 8dp drag touch-slop delay!) while tracking vertical position for analog pressure.
 */
@Composable
private fun TriggerPad(
    label: String,
    height: androidx.compose.ui.unit.Dp = 92.dp,
    onValue: (Float) -> Unit
) {
    var value by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label analog trigger"
                role = Role.Button
                onClick(label = "Press $label") {
                    onValue(1f)
                    onValue(0f)
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    // Immediate press response: touching anywhere engages at least 0.35f pressure
                    // (or proportional to vertical position, with bottom = 1.0f).
                    fun pressureAt(y: Float): Float = (y / h).coerceIn(0.25f, 1f)
                    value = pressureAt(down.position.y)
                    onValue(value)
                    do {
                        val event = awaitPointerEvent()
                        val active = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull { it.pressed }
                        if (active != null && active.pressed) {
                            active.consume()
                            value = pressureAt(active.position.y)
                            onValue(value)
                        }
                    } while (event.changes.any { it.pressed })
                    value = 0f
                    onValue(0f)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(value)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        )
        Text(
            label,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Round 7 + A11 — Pro panel: motion options, stick tuning, per-game profiles + JSON share/import. */
@Composable
private fun ProDialog(
    settings: com.aeropad.remote.model.GamepadSettings,
    profiles: List<com.aeropad.remote.data.gamepad.TuningProfile>,
    settingsVm: SettingsViewModel,
    onDismiss: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onApplyProfile: (com.aeropad.remote.data.gamepad.TuningProfile) -> Unit,
    onRemoveProfile: (com.aeropad.remote.data.gamepad.TuningProfile) -> Unit,
    onExportAllJson: () -> String = { "[]" },
    onImportJson: (String) -> Int = { 0 }
) {
    var profileName by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }
    var shareStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pro gamepad") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gyro aiming", modifier = Modifier.weight(1f))
                    Switch(settings.gyroAim, { settingsVm.setGyroAim(it) })
                }
                if (settings.gyroAim) {
                    Text("Gyro sensitivity ${settings.gyroSensitivity}")
                    Slider(
                        settings.gyroSensitivity.toFloat(),
                        { settingsVm.setGyroSensitivity(it.toInt()) },
                        valueRange = 0f..100f
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tilt steering", modifier = Modifier.weight(1f))
                    Switch(settings.tiltSteer, { settingsVm.setTiltSteer(it) })
                }
                if (settings.tiltSteer) {
                    Text("Tilt sensitivity ${settings.tiltSensitivity}")
                    Slider(
                        settings.tiltSensitivity.toFloat(),
                        { settingsVm.setTiltSensitivity(it.toInt()) },
                        valueRange = 0f..100f
                    )
                }
                Text("Stick sensitivity ${settings.joystickSensitivity}")
                Slider(
                    settings.joystickSensitivity.toFloat(),
                    { settingsVm.setJoystickSens(it.toInt()) },
                    valueRange = 0f..100f
                )
                Text("Dead zone ${settings.deadZone}")
                Slider(
                    settings.deadZone.toFloat(),
                    { settingsVm.setDeadZoneValue(it.toInt()) },
                    valueRange = 0f..50f
                )

                Text("Per-game & community profiles", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        profileName, { profileName = it },
                        label = { Text("Profile name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        onSaveProfile(profileName); profileName = ""
                    }) { Text("Save") }
                }
                profiles.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onApplyProfile(p) }, modifier = Modifier.weight(1f)) {
                            Text("• " + p.name)
                        }
                        TextButton(onClick = { onRemoveProfile(p) }) { Text("✕") }
                    }
                }
                // A11 — Gamepad profile JSON import / export (shareable without a server)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            val json = onExportAllJson()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("AeroPad Gamepad Profiles", json))
                            shareStatus = "Copied ${profiles.size} profile(s) as JSON to clipboard ✓"
                        },
                        label = { Text("Copy profiles JSON") }
                    )
                    AssistChip(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                ?: importJsonText
                            val count = onImportJson(clip)
                            shareStatus = if (count > 0) "Imported $count profile(s) ✓"
                            else "Paste valid profile JSON below or copy it to clipboard first."
                        },
                        label = { Text("Import from clipboard") }
                    )
                }
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    label = { Text("Or paste profile JSON here") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (importJsonText.isNotBlank()) {
                    TextButton(onClick = {
                        val count = onImportJson(importJsonText)
                        shareStatus = if (count > 0) "Imported $count profile(s) ✓" else "Invalid profile JSON"
                        if (count > 0) importJsonText = ""
                    }) { Text("Import JSON") }
                }
                shareStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Gyro aim moves the right stick from phone rotation; tilt steer " +
                        "drives the left stick by tilting. Some Windows games need a " +
                        "companion for full controller compatibility.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/**
 * Control sizes for the gamepad. Portrait uses the designed sizes; landscape
 * derives them from the window so the whole controller always fits.
 */
private data class PadMetrics(
    val deck: androidx.compose.ui.unit.Dp = 380.dp,
    val face: androidx.compose.ui.unit.Dp = 56.dp,
    val stick: androidx.compose.ui.unit.Dp = 140.dp,
    val dpad: androidx.compose.ui.unit.Dp = 44.dp
)
