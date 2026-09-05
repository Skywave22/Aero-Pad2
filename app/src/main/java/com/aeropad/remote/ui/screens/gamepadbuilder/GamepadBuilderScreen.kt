package com.bluepilot.remote.ui.screens.gamepadbuilder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.gamepad.ControlShape
import com.bluepilot.remote.model.gamepad.ButtonNaming
import com.bluepilot.remote.model.gamepad.GamepadButtonNames
import com.bluepilot.remote.model.gamepad.HapticPattern
import com.bluepilot.remote.model.gamepad.ResponseCurve
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.StickSide
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.gamepad.GamepadCanvas
import com.bluepilot.remote.ui.gamepad.GamepadEvents
import com.bluepilot.remote.ui.gamepad.RenderGamepadControl
import com.bluepilot.remote.viewmodel.GamepadBuilderViewModel
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.components.cornerBrackets
import com.bluepilot.remote.ui.theme.LocalAppTheme
import timber.log.Timber

/**
 * SECTION 2 — Gamepad Builder: profile list → PLAY / EDIT modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadBuilderScreen(
    onBack: () -> Unit,
    /** GTA presets — non-null jumps straight into PLAY mode for that preset. */
    presetKey: String? = null,
    viewModel: GamepadBuilderViewModel = hiltViewModel()
) {
    val playing by viewModel.playing.collectAsState()
    val draft by viewModel.draft.collectAsState()

    // Auto-play the requested preset once (recomposition-safe).
    LaunchedEffect(presetKey) { presetKey?.let { viewModel.playPreset(it) } }

    when {
        playing != null -> GamepadPlayer(viewModel)
        draft != null -> GamepadEditor(viewModel)
        else -> ProfileList(onBack, viewModel)
    }
}

// ======================================================================
// Profile list
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileList(onBack: () -> Unit, viewModel: GamepadBuilderViewModel) {
    val profiles by viewModel.filteredProfiles.collectAsState()
    val recents by viewModel.recentProfiles.collectAsState()
    val query by viewModel.profileQuery.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val suggested by viewModel.suggestedTags.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val message by viewModel.message.collectAsState()
    val exportPayload by viewModel.exportPayload.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.onFailure { Timber.e(it, "gamepad import read failed") }.getOrNull()
        viewModel.importFromJson(text)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val payload = exportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.second.toByteArray()) }
            }.onFailure { Timber.e(it, "gamepad export write failed") }
        }
        viewModel.consumeExport()
    }
    LaunchedEffect(exportPayload) { exportPayload?.let { exportLauncher.launch(it.first) } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Gamepad Builder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Import gamepad")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.newProfile() }) {
                Icon(Icons.Rounded.Add, contentDescription = "New gamepad")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tap ▶ to play a gamepad • pencil to edit • + to build a new one. Rotate to landscape for the best play experience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            // ADV S3 — search + tag filter.
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setProfileQuery(it) },
                    singleLine = true,
                    placeholder = { Text("Search gamepads (name or tag)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = tagFilter == null,
                        onClick = { viewModel.setTagFilter(null) },
                        label = { Text("All") }
                    )
                    com.bluepilot.remote.domain.ProfileSuggester.ALL_TAGS.forEach { tag ->
                        FilterChip(
                            selected = tagFilter == tag,
                            onClick = { viewModel.setTagFilter(if (tagFilter == tag) null else tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }
            // ADV S3 — contextual suggestion from the real connected host.
            if (suggested.isNotEmpty()) {
                item {
                    Text(
                        "Connected host suggests: ${suggested.joinToString(" • ")} layouts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // ADV S3 — recently played quick-access row.
            if (recents.isNotEmpty() && query.isBlank() && tagFilter == null) {
                item {
                    Column {
                        Text(
                            "Recently played",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recents.forEach { rp ->
                                AssistChip(
                                    onClick = { viewModel.play(rp.id) },
                                    label = { Text("▶ " + rp.spec.name) }
                                )
                            }
                        }
                    }
                }
            }
            items(profiles, key = { it.id }) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.spec.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${profile.spec.controls.size} controls" +
                                    (if (profile.spec.tags.isNotEmpty()) "  •  " + profile.spec.tags.joinToString(", ") else "") +
                                    (if (profile.isBuiltIn) "  •  template" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // ADV S3 — favorite pin (favorites sort to top).
                        val fav = com.bluepilot.remote.ui.theme.ThemeListCodec.contains(
                            appSettings.favoriteGamepads, profile.id.toString())
                        IconButton(onClick = { viewModel.toggleFavorite(profile.id) }) {
                            Icon(
                                if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (fav) "Unfavorite" else "Favorite",
                                tint = if (fav) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.play(profile.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.edit(profile) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.duplicate(profile.id) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate")
                        }
                        IconButton(onClick = { viewModel.requestExport(profile.id) }) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Export")
                        }
                        if (!profile.isBuiltIn) {
                            IconButton(onClick = { viewModel.delete(profile.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================
// Player (live HID)
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamepadPlayer(viewModel: GamepadBuilderViewModel) {
    val playing by viewModel.playing.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val motionEnabled by viewModel.motionEnabled.collectAsState()
    val motionSensitivity by viewModel.motionSensitivity.collectAsState()
    val motionDeadZone by viewModel.motionDeadZone.collectAsState()
    val lastPressed by viewModel.lastPressed.collectAsState()
    val profile = playing ?: return

    // BUG FIX: if the user leaves via system back (route pop), the composable
    // is disposed without stopPlaying() — neutralize HID state so no button
    // stays pressed on the host. neutralizeHid() is idempotent.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.neutralizeHid() }
    }

    val events = remember {
        object : GamepadEvents {
            override fun onButton(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onButton(control, pressed)
            override fun onStick(control: GamepadControlSpec, x: Float, y: Float) =
                viewModel.onStick(control, x, y)
            override fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) =
                viewModel.onDpadTouch(control, dx, dy)
            override fun onDpadRelease(control: GamepadControlSpec) =
                viewModel.onDpadRelease(control)
            override fun onToggle(control: GamepadControlSpec) =
                viewModel.onToggleButton(control)
            override fun onMultiTap(control: GamepadControlSpec) =
                viewModel.onMultiTap(control)
            override fun onRadialPick(control: GamepadControlSpec, index: Int) =
                viewModel.onRadialPick(control, index)
            override fun onArrow(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onArrow(control, pressed)
            override fun onComboZone(control: GamepadControlSpec, zone: Int, pressed: Boolean) =
                viewModel.onComboZone(control, zone, pressed)
            override fun onStickClick(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onStickClick(control, pressed)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        profile.spec.name +
                            if (lastPressed.isNotBlank()) "   [$lastPressed]" else ""
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.stopPlaying() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // UI/UX v2.1 LANDSCAPE FIX — the 6 assist chips overflowed
                    // and got clipped in landscape. All assists now live in
                    // ONE dropdown menu; active count badges on the button.
                    val flickOn by viewModel.flickEnabled.collectAsState()
                    val steerOn by viewModel.steerEnabled.collectAsState()
                    val waveOn by viewModel.proximityEnabled.collectAsState()
                    val strafeOn by viewModel.strafeEnabled.collectAsState()
                    val activeCount = listOf(motionEnabled, flickOn, steerOn, waveOn, strafeOn).count { it }
                    var assistsOpen by remember { mutableStateOf(false) }
                    if (motionEnabled) {
                        AssistChip(
                            onClick = { viewModel.recenterMotion() },
                            label = { Text("⊕") }
                        )
                    }
                    Box {
                        FilterChip(
                            selected = activeCount > 0,
                            onClick = { assistsOpen = true },
                            label = { Text(if (activeCount > 0) "Assists ($activeCount)" else "Assists") }
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = assistsOpen,
                            onDismissRequest = { assistsOpen = false }
                        ) {
                            if (viewModel.hasGyro) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text((if (motionEnabled) "✓ " else "") + "Motion aim (gyro)") },
                                    onClick = { viewModel.setMotionEnabled(!motionEnabled) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text((if (flickOn) "✓ " else "") + "Flick actions") },
                                    onClick = { viewModel.setFlickEnabled(!flickOn) }
                                )
                            }
                            if (viewModel.hasGravity) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text((if (steerOn) "✓ " else "") + "Steering wheel") },
                                    onClick = { viewModel.setSteerEnabled(!steerOn) }
                                )
                            }
                            if (viewModel.hasProximity) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text((if (waveOn) "✓ " else "") + "Wave to tap") },
                                    onClick = { viewModel.setProximityEnabled(!waveOn) }
                                )
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text((if (strafeOn) "✓ " else "") + "Auto-strafe") },
                                onClick = { viewModel.setStrafeEnabled(!strafeOn) }
                            )
                        }
                    }
                    // Quick-switch between saved gamepad profiles.
                    IconButton(onClick = { viewModel.playNext(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous profile")
                    }
                    IconButton(onClick = { viewModel.playNext(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = "Next profile")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            NotConnectedBanner(!isConnected)
            if (motionEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Aim", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = motionSensitivity.toFloat(),
                        onValueChange = { viewModel.setMotionSensitivity(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("DZ", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = motionDeadZone.toFloat(),
                        onValueChange = { viewModel.setMotionDeadZone(it.toInt()) },
                        valueRange = 0f..50f,
                        modifier = Modifier.weight(0.6f)
                    )
                }
            }
            // V2 MATRIX 3 — steering full-lock angle (only while Wheel ON).
            val steerOnRow by viewModel.steerEnabled.collectAsState()
            if (steerOnRow) {
                val steerMax by viewModel.steerMaxAngle.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Lock ${steerMax}°", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = steerMax.toFloat(),
                        onValueChange = { viewModel.setSteerMaxAngle(it.toInt()) },
                        valueRange = 15f..75f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // V2 M3 finale — flick direction mapping (only while Flick ON).
            // Tap a direction chip to cycle its button: × → A → B → X → Y → ×.
            val flickOnRow by viewModel.flickEnabled.collectAsState()
            if (flickOnRow) {
                val fmap by viewModel.flickMap.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Flick", style = MaterialTheme.typography.bodyMedium)
                    listOf(
                        "↑" to com.bluepilot.remote.domain.FlickDetector.FlickDirection.UP,
                        "↓" to com.bluepilot.remote.domain.FlickDetector.FlickDirection.DOWN,
                        "←" to com.bluepilot.remote.domain.FlickDetector.FlickDirection.LEFT,
                        "→" to com.bluepilot.remote.domain.FlickDetector.FlickDirection.RIGHT
                    ).forEach { (arrow, dir) ->
                        val mapped = fmap[dir] ?: -1
                        FilterChip(
                            selected = mapped >= 0,
                            onClick = { viewModel.cycleFlickMapping(dir) },
                            label = {
                                Text(
                                    arrow + " " +
                                        com.bluepilot.remote.domain.FlickMapping.label(mapped)
                                )
                            }
                        )
                    }
                }
            }
            // V2 MATRIX 2 — auto-strafe period (only while Strafe ON).
            val strafeOnRow by viewModel.strafeEnabled.collectAsState()
            if (strafeOnRow) {
                val strafePeriod by viewModel.strafePeriodMs.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Strafe ${strafePeriod}ms", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = strafePeriod.toFloat(),
                        onValueChange = { viewModel.setStrafePeriodMs(it.toInt()) },
                        valueRange = 80f..2000f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // ADV S2 — shift-layer aware canvas. If the layout defines a
            // shift control, its presses toggle the visible layer instead
            // of ALSO sending shifted-layer controls' HID (base action
            // still fires — a shift key can double as a real button).
            val activeLayer by viewModel.activeLayer.collectAsState()
            val shiftId = profile.spec.shiftControlId
            val layerEvents = remember(shiftId) {
                if (shiftId.isBlank()) events else object : GamepadEvents by events {
                    override fun onButton(control: GamepadControlSpec, pressed: Boolean) {
                        if (control.id == shiftId) viewModel.onShift(pressed)
                        events.onButton(control, pressed)
                    }
                }
            }
            // V2 MATRIX 6 — frame-data input history overlay (toggleable).
            val showHistory by viewModel.showInputHistory.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showHistory,
                    onClick = { viewModel.setShowInputHistory(!showHistory) },
                    label = { Text(if (showHistory) "History ✓" else "Input history") }
                )
                if (showHistory) {
                    val historyEvents by viewModel.inputHistory.collectAsState()
                    val now = System.currentTimeMillis()
                    Text(
                        text = historyEvents.joinToString("  ") { e ->
                            "[${e.label} +${(now - e.atMs).coerceAtMost(9999)}ms]"
                        }.ifBlank { "waiting for input…" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
            GamepadCanvas(
                layout = profile.spec,
                events = layerEvents,
                modifier = Modifier.fillMaxSize(),
                activeLayer = activeLayer
            )
        }
    }
}

// ======================================================================
// Editor
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamepadEditor(viewModel: GamepadBuilderViewModel) {
    val draft by viewModel.draft.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val previewMode by viewModel.previewMode.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val (_, spec) = draft ?: return
    var showProperties by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }   // ADV S3

    val playEvents = remember {
        object : GamepadEvents {
            override fun onButton(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onButton(control, pressed)
            override fun onStick(control: GamepadControlSpec, x: Float, y: Float) =
                viewModel.onStick(control, x, y)
            override fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) =
                viewModel.onDpadTouch(control, dx, dy)
            override fun onDpadRelease(control: GamepadControlSpec) =
                viewModel.onDpadRelease(control)
            override fun onToggle(control: GamepadControlSpec) =
                viewModel.onToggleButton(control)
            override fun onMultiTap(control: GamepadControlSpec) =
                viewModel.onMultiTap(control)
            override fun onRadialPick(control: GamepadControlSpec, index: Int) =
                viewModel.onRadialPick(control, index)
            override fun onArrow(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onArrow(control, pressed)
            override fun onComboZone(control: GamepadControlSpec, zone: Int, pressed: Boolean) =
                viewModel.onComboZone(control, zone, pressed)
            override fun onStickClick(control: GamepadControlSpec, pressed: Boolean) =
                viewModel.onStickClick(control, pressed)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(spec.name) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeDraft() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo && !previewMode) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.togglePreview() }) {
                        Icon(
                            if (previewMode) Icons.Rounded.Edit else Icons.Rounded.PlayArrow,
                            contentDescription = if (previewMode) "Edit mode" else "Preview mode"
                        )
                    }
                    IconButton(onClick = { viewModel.saveDraft() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!previewMode) {
                val themeSpec = LocalAppTheme.current
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedId == null) {
                            Text(
                                text = if (themeSpec.monoFont) "ADD:" else "Add:",
                                style = if (themeSpec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AssistChip(onClick = { viewModel.addControl(GamepadControlType.BUTTON) }, label = { Text(if (themeSpec.monoFont) "BUTTON" else "Button") })
                            AssistChip(onClick = { viewModel.addControl(GamepadControlType.TRIGGER) }, label = { Text(if (themeSpec.monoFont) "TRIGGER" else "Trigger") })
                            AssistChip(onClick = { viewModel.addControl(GamepadControlType.STICK) }, label = { Text(if (themeSpec.monoFont) "STICK" else "Stick") })
                            AssistChip(onClick = { viewModel.addControl(GamepadControlType.DPAD) }, label = { Text(if (themeSpec.monoFont) "D-PAD" else "D-pad") })
                            AssistChip(onClick = { viewModel.addControl(GamepadControlType.ARROW) }, label = { Text(if (themeSpec.monoFont) "ARROW" else "Arrow") })
                            AssistChip(onClick = { viewModel.addControl(GamepadControlType.COMBO) }, label = { Text(if (themeSpec.monoFont) "L1+L2" else "L1+L2") })
                            // ADV S2 — layout intelligence tools.
                            AssistChip(onClick = { viewModel.mirrorWholeLayout() }, label = { Text("⇄ Flip hand") })
                            val gridOn = (viewModel.draft.collectAsState().value?.second?.gridSize ?: 0f) > 0f
                            AssistChip(
                                onClick = { viewModel.setGridSize(if (gridOn) 0f else 0.025f) },
                                label = { Text(if (gridOn) "Grid ✓" else "Grid") }
                            )
                            val grip = viewModel.heatmapGrip.collectAsState().value
                            AssistChip(
                                onClick = {
                                    val next = when (grip) {
                                        null -> com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.TWO_THUMB
                                        com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.TWO_THUMB ->
                                            com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.CLAW
                                        com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.CLAW ->
                                            com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.PALM
                                        com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.PALM -> null
                                    }
                                    viewModel.setHeatmapGrip(next)
                                },
                                label = { Text(when (grip) {
                                    null -> "Reach map"
                                    com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.TWO_THUMB -> "Reach: thumbs"
                                    com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.CLAW -> "Reach: claw"
                                    com.bluepilot.remote.domain.LayoutIntelligence.GripStyle.PALM -> "Reach: palm"
                                }) }
                            )
                            val testOn = viewModel.testMode.collectAsState().value
                            AssistChip(
                                onClick = { viewModel.setTestMode(!testOn) },
                                label = { Text(if (testOn) "Test ✓" else "Test mode") }
                            )
                            // ADV S3 — versioning + A/B compare.
                            AssistChip(
                                onClick = { viewModel.loadVersions(); showVersions = true },
                                label = { Text("Versions") }
                            )
                            val hasB = viewModel.hasAbSlot.collectAsState().value
                            AssistChip(
                                onClick = { viewModel.setAbSlot() },
                                label = { Text(if (hasB) "Set B ✓" else "Set as B") }
                            )
                            if (hasB) {
                                AssistChip(
                                    onClick = { viewModel.toggleAb() },
                                    label = { Text("⇆ A/B") }
                                )
                            }
                            // ADV S3 — profile tags.
                            com.bluepilot.remote.domain.ProfileSuggester.ALL_TAGS.forEach { tag ->
                                FilterChip(
                                    selected = tag in spec.tags,
                                    onClick = { viewModel.toggleTag(tag) },
                                    label = { Text(tag) }
                                )
                            }
                        } else {
                            AssistChip(onClick = { showProperties = true }, label = { Text(if (themeSpec.monoFont) "PROPERTIES" else "Properties") })
                            AssistChip(onClick = { viewModel.mirrorSelected() }, label = { Text("Mirror →") })
                            // V2 MATRIX 1 b5 — quick anchors.
                            AssistChip(
                                onClick = { viewModel.anchorSelected(com.bluepilot.remote.domain.AnchorLayout.Anchor.BOTTOM_LEFT) },
                                label = { Text("⌊ BL") }
                            )
                            AssistChip(
                                onClick = { viewModel.anchorSelected(com.bluepilot.remote.domain.AnchorLayout.Anchor.BOTTOM_RIGHT) },
                                label = { Text("BR ⌋") }
                            )
                            AssistChip(
                                onClick = { viewModel.anchorSelected(com.bluepilot.remote.domain.AnchorLayout.Anchor.CENTER) },
                                label = { Text("◎ Center") }
                            )
                            AssistChip(
                                onClick = { viewModel.anchorSelected(com.bluepilot.remote.domain.AnchorLayout.Anchor.TOP_RIGHT) },
                                label = { Text("TR ⌉") }
                            )
                            val selLayer = viewModel.selectedControl()?.layer ?: 0
                            AssistChip(
                                onClick = { viewModel.setSelectedLayer(if (selLayer == 0) 1 else 0) },
                                label = { Text(if (selLayer == 0) "→ Shift layer" else "→ Base layer") }
                            )
                            val isShift = viewModel.draft.collectAsState().value?.second?.shiftControlId == selectedId
                            AssistChip(
                                onClick = { viewModel.setSelectedAsShift() },
                                label = { Text(if (isShift) "Shift key ✓" else "Make shift key") }
                            )
                            AssistChip(onClick = { viewModel.removeSelected() }, label = { Text(if (themeSpec.monoFont) "DELETE" else "Delete") })
                            AssistChip(onClick = { viewModel.select(null) }, label = { Text(if (themeSpec.monoFont) "DESELECT" else "Deselect") })
                        }
                    }
                }
            }

            // ADV S2 — smart spacing warnings (mis-tap risk).
            if (!previewMode) {
                val warns = viewModel.spacingWarnings()
                if (warns.isNotEmpty()) {
                    Text(
                        text = "⚠ ${warns.size} control pair${if (warns.size > 1) "s" else ""} closer than 2% — mis-tap risk. Spread them out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }
            // ADV S2 — Test Mode: real wire bytes + measured dispatch time.
            val testModeOn by viewModel.testMode.collectAsState()
            if (!previewMode && testModeOn) {
                val testEvents by viewModel.testEvents.collectAsState()
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "TEST MODE — tap controls below. Shows real HID report bytes + measured dispatch µs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        testEvents.take(5).forEach { e ->
                            Text(
                                text = "${e.label}: ${e.wire} — ${e.latencyUs}µs",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.85f
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (testEvents.isEmpty()) {
                            Text(
                                "No events yet — tap any button control on the canvas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------- Canvas ----------
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                val w = maxWidth
                val h = maxHeight
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()

                // ADV S2 — magnetic grid dots + thumb-reach heatmap +
                // alignment guides, all one lightweight Canvas draw.
                val grip by viewModel.heatmapGrip.collectAsState()
                val gridSize = spec.gridSize
                val dragGuides = if (selectedId != null && !previewMode)
                    viewModel.alignmentGuides(selectedId!!) else null
                val guideColor = MaterialTheme.colorScheme.primary
                // V2 MATRIX 5 — min-touch-target audit: red dashed outline on
                // controls smaller than 48dp (real Material/WCAG guidance,
                // computed against the real canvas size). Editor-only.
                val a11yErrColor = MaterialTheme.colorScheme.error
                val canvasWDp = with(LocalDensity.current) { wPx.toDp().value }
                val canvasHDp = with(LocalDensity.current) { hPx.toDp().value }
                if (!previewMode) {
                    val tooSmall = spec.controls.filter {
                        com.bluepilot.remote.domain.A11y.isBelowMinTarget(
                            it.frame.sanitized(), canvasWDp, canvasHDp
                        )
                    }
                    if (tooSmall.isNotEmpty()) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                            tooSmall.forEach { c ->
                                val f = c.frame.sanitized()
                                drawRect(
                                    color = a11yErrColor.copy(alpha = 0.9f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        f.x * size.width, f.y * size.height
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        f.w * size.width, f.h * size.height
                                    ),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 3f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect
                                            .dashPathEffect(floatArrayOf(10f, 8f))
                                    )
                                )
                            }
                        }
                        Text(
                            "⚠ ${tooSmall.size} control${if (tooSmall.size > 1) "s" else ""} below the 48dp touch target",
                            style = MaterialTheme.typography.bodySmall,
                            color = a11yErrColor,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp)
                        )
                    }
                }
                if (!previewMode && (gridSize > 0f || grip != null || dragGuides != null)) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                        // Grid dots.
                        if (gridSize > 0f) {
                            var gx = gridSize
                            while (gx < 1f) {
                                var gy = gridSize
                                while (gy < 1f) {
                                    drawCircle(
                                        color = guideColor.copy(alpha = 0.20f),
                                        radius = 1.6f,
                                        center = androidx.compose.ui.geometry.Offset(gx * size.width, gy * size.height)
                                    )
                                    gy += gridSize
                                }
                                gx += gridSize
                            }
                        }
                        // Thumb-reach heatmap: comfortable (green) + stretch (amber) arcs.
                        grip?.let { g ->
                            com.bluepilot.remote.domain.LayoutIntelligence.reachZones(g).forEach { z ->
                                val c = androidx.compose.ui.geometry.Offset(z.cx * size.width, z.cy * size.height)
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFFFFB300).copy(alpha = 0.14f),
                                    radius = z.stretch * size.width, center = c
                                )
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFF2ECC71).copy(alpha = 0.18f),
                                    radius = z.comfortable * size.width, center = c
                                )
                            }
                        }
                        // Live alignment guides while a control is selected.
                        dragGuides?.let { gsn ->
                            if (gsn.verticalCenter) drawLine(
                                guideColor.copy(alpha = 0.8f),
                                androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                                androidx.compose.ui.geometry.Offset(size.width / 2f, size.height), 2f
                            )
                            if (gsn.horizontalCenter) drawLine(
                                guideColor.copy(alpha = 0.8f),
                                androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                                androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), 2f
                            )
                            gsn.matchX.forEach { mx ->
                                drawLine(
                                    guideColor.copy(alpha = 0.55f),
                                    androidx.compose.ui.geometry.Offset(mx * size.width, 0f),
                                    androidx.compose.ui.geometry.Offset(mx * size.width, size.height), 1.5f
                                )
                            }
                            gsn.matchY.forEach { my ->
                                drawLine(
                                    guideColor.copy(alpha = 0.55f),
                                    androidx.compose.ui.geometry.Offset(0f, my * size.height),
                                    androidx.compose.ui.geometry.Offset(size.width, my * size.height), 1.5f
                                )
                            }
                        }
                    }
                }

                spec.controls.forEach { control ->
                    val frame = control.frame.sanitized()
                    val isSelected = control.id == selectedId && !previewMode
                    Box(
                        modifier = Modifier
                            .offset(x = w * frame.x, y = h * frame.y)
                            .size(width = w * frame.w, height = h * frame.h)
                    ) {
                        if (previewMode) {
                            RenderGamepadControl(control, playEvents, Modifier.fillMaxSize())
                        } else {
                            RenderGamepadControl(control, noOpGamepadEvents, Modifier.fillMaxSize())
                            val themeSpec = LocalAppTheme.current
                            val outlineColor = themeSpec.glowColor ?: MaterialTheme.colorScheme.primary
                            val borderGlowModifier = if (isSelected) {
                                if (themeSpec.monoFont) {
                                    Modifier.cornerBrackets(outlineColor)
                                } else {
                                    Modifier.border(
                                        2.dp, outlineColor, RoundedCornerShape(12.dp)
                                    )
                                }
                            } else Modifier

                            if (control.layer == 1) {
                                Text(
                                    "⇧",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(borderGlowModifier)
                                    .pointerInput(control.id + "-tap", testModeOn) {
                                        detectTapGestures(onTap = {
                                            if (testModeOn &&
                                                (control.type == GamepadControlType.BUTTON ||
                                                 control.type == GamepadControlType.TRIGGER ||
                                                 control.type == GamepadControlType.COMBO)) {
                                                // ADV S2 test mode: real press+release through the HID path.
                                                viewModel.testPress(control, true)
                                                viewModel.testPress(control, false)
                                            } else viewModel.select(control.id)
                                        })
                                    }
                                    .pointerInput(control.id + "-drag") {
                                        detectDragGestures(
                                            onDragStart = { viewModel.beginGesture(); viewModel.select(control.id) }
                                        ) { change, drag ->
                                            change.consume()
                                            val current = viewModel.draft.value?.second
                                                ?.controls?.firstOrNull { it.id == control.id }
                                                ?: return@detectDragGestures
                                            viewModel.moveSelected(
                                                current.frame.x + drag.x / wPx,
                                                current.frame.y + drag.y / hPx
                                            )
                                        }
                                    }
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(26.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .pointerInput("resize-" + control.id) {
                                            detectDragGestures(
                                                onDragStart = { viewModel.beginGesture() }
                                            ) { change, drag ->
                                                change.consume()
                                                val current = viewModel.draft.value?.second
                                                    ?.controls?.firstOrNull { it.id == control.id }
                                                    ?: return@detectDragGestures
                                                viewModel.resizeSelected(
                                                    current.frame.w + drag.x / wPx,
                                                    current.frame.h + drag.y / hPx
                                                )
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProperties && selectedId != null) {
        ModalBottomSheet(onDismissRequest = { showProperties = false }) {
            ControlPropertiesPanel(viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }

    // ADV S3 — version history sheet: revert to any archived iteration.
    if (showVersions) {
        val versions by viewModel.versions.collectAsState()
        ModalBottomSheet(onDismissRequest = { showVersions = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Version history",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                if (versions.isEmpty()) {
                    Text(
                        "No archived versions yet — each Save of this profile archives the previous layout here (last ${com.bluepilot.remote.data.gamepad.VersionCodec.MAX_VERSIONS}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                versions.forEachIndexed { i, v ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("v-${versions.size - i}  •  ${v.controls.size} controls", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                v.name + if (v.tags.isNotEmpty()) "  •  " + v.tags.joinToString(", ") else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = { viewModel.revertTo(v); showVersions = false },
                            label = { Text("Revert") }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val noOpGamepadEvents = object : GamepadEvents {
    override fun onButton(control: GamepadControlSpec, pressed: Boolean) {}
    override fun onStick(control: GamepadControlSpec, x: Float, y: Float) {}
    override fun onDpadTouch(control: GamepadControlSpec, dx: Float, dy: Float) {}
    override fun onDpadRelease(control: GamepadControlSpec) {}
}

// ======================================================================
// Properties panel
// ======================================================================

private val controlPalette = listOf(
    0xFF2F6BFF, 0xFF29C5FF, 0xFF2ECC71, 0xFFF1C40F, 0xFFE67E22,
    0xFFE74C3C, 0xFFFF2D95, 0xFF9B59B6, 0xFF1A2238, 0xFF7F8C8D
)

@Composable
private fun ControlPropertiesPanel(viewModel: GamepadBuilderViewModel) {
    val draft by viewModel.draft.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val control = draft?.second?.controls?.firstOrNull { it.id == selectedId } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "Control: ${control.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))

        // Label (buttons/triggers)
        if (control.type == GamepadControlType.BUTTON || control.type == GamepadControlType.TRIGGER) {
            OutlinedTextField(
                value = control.label,
                onValueChange = { new -> viewModel.updateSelected { it.copy(label = new) } },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // HID button binding
            // SECTION 5 — naming convention toggle (Xbox / PlayStation)
            val naming = draft?.second?.naming ?: ButtonNaming.XBOX
            Text("Button naming", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = naming == ButtonNaming.XBOX,
                    onClick = { viewModel.setNaming(ButtonNaming.XBOX) },
                    label = { Text("A/B/X/Y") }
                )
                FilterChip(
                    selected = naming == ButtonNaming.PLAYSTATION,
                    onClick = { viewModel.setNaming(ButtonNaming.PLAYSTATION) },
                    label = { Text("✕/○/□/△") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Sends HID button", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..15).forEach { index ->
                    FilterChip(
                        selected = control.buttonIndex == index,
                        onClick = { viewModel.updateSelected { it.copy(buttonIndex = index) } },
                        label = { Text(GamepadButtonNames.label(index, naming)) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // SECTION 5 — turbo / rapid fire
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Turbo (rapid fire)", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.turbo,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(turbo = v) } }
                )
            }
            if (control.turbo) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Rate", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${control.turboRate}/s", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = control.turboRate.toFloat(),
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(turboRate = v.toInt()) } },
                    valueRange = 2f..20f
                )
            }
            // ADV S1 — button press semantics.
            Spacer(Modifier.height(8.dp))
            Text("Press mode", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.bluepilot.remote.model.gamepad.ButtonMode.entries.forEach { m ->
                    FilterChip(
                        selected = control.buttonMode == m,
                        onClick = { viewModel.updateSelected { it.copy(buttonMode = m) } },
                        label = { Text(m.name.lowercase().replace("_", " ")) }
                    )
                }
            }
            if (control.buttonMode == com.bluepilot.remote.model.gamepad.ButtonMode.MULTI_TAP) {
                Text("Double-tap sends", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..15).forEach { index ->
                        FilterChip(
                            selected = control.secondaryButtonIndex == index,
                            onClick = { viewModel.updateSelected { it.copy(secondaryButtonIndex = index) } },
                            label = { Text(GamepadButtonNames.label(index, draft?.second?.naming ?: ButtonNaming.XBOX)) }
                        )
                    }
                }
            }
            if (control.buttonMode == com.bluepilot.remote.model.gamepad.ButtonMode.RADIAL) {
                Text("Radial wheel options (tap to add/remove)", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..15).forEach { index ->
                        val selectedOpt = index in control.radialOptions
                        FilterChip(
                            selected = selectedOpt,
                            onClick = {
                                viewModel.updateSelected {
                                    it.copy(radialOptions =
                                        if (selectedOpt) it.radialOptions - index
                                        else (it.radialOptions + index).take(com.bluepilot.remote.model.gamepad.GamepadControlSpec.RADIAL_MAX))
                                }
                            },
                            label = { Text(GamepadButtonNames.label(index, draft?.second?.naming ?: ButtonNaming.XBOX)) }
                        )
                    }
                }
            }
            // V2 MATRIX 6 — trigger travel + overlap (TRIGGER type only).
            if (control.type == GamepadControlType.TRIGGER) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Trigger travel", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (control.triggerTravelMs <= 0) "Off" else "${control.triggerTravelMs}ms",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = control.triggerTravelMs.toFloat(),
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(triggerTravelMs = v.toInt()) } },
                    valueRange = 0f..2000f
                )
                if (control.triggerTravelMs > 0) {
                    Text(
                        "Stage 1 at ${(control.overlapL1At * 100).toInt()}% • Stage 2 at ${(control.overlapL2At * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = control.overlapL1At,
                        onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(overlapL1At = v) } },
                        valueRange = 0.05f..0.95f
                    )
                    Slider(
                        value = control.overlapL2At,
                        onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(overlapL2At = v) } },
                        valueRange = 0.1f..1f
                    )
                }
            }
            // V2 MATRIX 1 — animation personality per element.
            Text("Animation style", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.bluepilot.remote.model.gamepad.MotionStyle.entries.forEach { m ->
                    FilterChip(
                        selected = control.motionStyle == m,
                        onClick = { viewModel.updateSelected { it.copy(motionStyle = m) } },
                        label = { Text(m.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    )
                }
            }
            // V2 MATRIX 1 — pressed-state color override.
            Text("Pressed color", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = control.pressedStyle.color == null,
                    onClick = { viewModel.updateSelected { it.copy(pressedStyle = it.pressedStyle.copy(color = null)) } },
                    label = { Text("Auto") }
                )
                listOf(0xFFFFFFFF, 0xFF2ECC71, 0xFFE74C3C, 0xFF29C5FF, 0xFFF1C40F, 0xFFFF2D95).forEach { c ->
                    FilterChip(
                        selected = control.pressedStyle.color == c,
                        onClick = { viewModel.updateSelected { it.copy(pressedStyle = it.pressedStyle.copy(color = c)) } },
                        label = { Text("■") }
                    )
                }
            }
            // V2 MATRIX 1 b4 — per-corner radius (ROUNDED/SQUARE only).
            if (control.shape != ControlShape.CIRCLE) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Per-corner radius", modifier = Modifier.weight(1f))
                    Switch(
                        checked = control.cornerRadii.size == 4,
                        onCheckedChange = { v ->
                            viewModel.updateSelected {
                                it.copy(cornerRadii = if (v) listOf(18, 18, 18, 18) else emptyList())
                            }
                        }
                    )
                }
                if (control.cornerRadii.size == 4) {
                    listOf("TL", "TR", "BR", "BL").forEachIndexed { ci, cn ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(cn, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${control.cornerRadii[ci]}dp", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(
                            value = control.cornerRadii[ci].toFloat(),
                            onValueChange = { v ->
                                viewModel.updateSelected(withUndo = false) {
                                    it.copy(cornerRadii = it.cornerRadii.toMutableList()
                                        .also { l -> if (l.size == 4) l[ci] = v.toInt() })
                                }
                            },
                            valueRange = 0f..40f
                        )
                    }
                }
            }
            // V2 MATRIX 1 b4 — multi-touch rejection.
            Text("Max pointers (0 = unlimited)", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..3).forEach { n ->
                    FilterChip(
                        selected = control.maxPointers == n,
                        onClick = { viewModel.updateSelected { it.copy(maxPointers = n) } },
                        label = { Text(if (n == 0) "Any" else "$n") }
                    )
                }
            }
            // V2 MATRIX 1 finale — touch slop (slide-off cancel distance).
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Slide-off cancel", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (control.touchSlopDp <= 0) "Never" else "${control.touchSlopDp}dp",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = control.touchSlopDp.toFloat(),
                onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(touchSlopDp = v.toInt()) } },
                valueRange = 0f..48f
            )
            // V2 MATRIX 1 b3 — duration-to-activate (accidental-tap guard).
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Hold to activate", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (control.holdToActivateMs <= 0) "Instant" else "${control.holdToActivateMs}ms",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = control.holdToActivateMs.toFloat(),
                onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(holdToActivateMs = v.toInt()) } },
                valueRange = 0f..3000f
            )
            // V2 MATRIX 1 b2 — cooldown ring overlay (visual timing aid).
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Cooldown ring", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (control.cooldownMs <= 0) "Off" else "${control.cooldownMs / 1000f}s",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = control.cooldownMs.toFloat(),
                onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(cooldownMs = v.toInt()) } },
                valueRange = 0f..10000f
            )
            // ADV S1 — press-confirmation glow.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Press glow confirmation", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.pressGlow,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(pressGlow = v) } }
                )
            }
            // V2 MATRIX 1 b6 — idle breathing glow toggle.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Breathing glow (idle)", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.breathingGlow,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(breathingGlow = v) } }
                )
            }
            // SECTION 8 — per-control haptic pattern
            Text("Icon", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = control.icon.isEmpty(),
                    onClick = { viewModel.updateSelected { it.copy(icon = "") } },
                    label = { Text("None") })
                com.bluepilot.remote.ui.components.ControlGlyphs.ALL.forEach { g ->
                    FilterChip(selected = control.icon == g,
                        onClick = { viewModel.updateSelected { it.copy(icon = g) } },
                        label = { Text(g) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Haptic on press", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HapticPattern.entries.forEach { hp ->
                    FilterChip(
                        selected = control.haptic == hp,
                        onClick = { viewModel.updateSelected { it.copy(haptic = hp) } },
                        label = { Text(hp.name.lowercase().replace("_", " ")) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Shape
            Text("Shape", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlShape.entries.forEach { shape ->
                    FilterChip(
                        selected = control.shape == shape,
                        onClick = { viewModel.updateSelected { it.copy(shape = shape) } },
                        label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        // Stick options
        if (control.type == GamepadControlType.STICK) {
            // SECTION 5 — response curve (linear / expo / aggressive)
            Text("Response curve", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ResponseCurve.entries.forEach { c ->
                    FilterChip(
                        selected = control.curve == c,
                        onClick = { viewModel.updateSelected { it.copy(curve = c) } },
                        label = { Text(c.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // Per-profile stick sensitivity preset
            val sens = draft?.second?.stickSensitivity ?: 70
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Profile stick sensitivity", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$sens%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = sens.toFloat(),
                onValueChange = { v -> viewModel.setStickSensitivity(v.toInt()) },
                valueRange = 0f..100f
            )
            Text("Axis pair", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StickSide.entries.forEach { side ->
                    FilterChip(
                        selected = control.stickSide == side,
                        onClick = { viewModel.updateSelected { it.copy(stickSide = side) } },
                        label = { Text(if (side == StickSide.LEFT) "Left stick (X/Y)" else "Right stick (Z/Rz)") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Dead zone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${control.deadZone}%", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = control.deadZone.toFloat(),
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(deadZone = v.toInt()) } },
                    valueRange = 0f..50f
                )
            }
            // ADV S1 — advanced stick options.
            Text("Gate shape", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.bluepilot.remote.model.gamepad.StickGate.entries.forEach { g ->
                    FilterChip(
                        selected = control.stickGate == g,
                        onClick = { viewModel.updateSelected { it.copy(stickGate = g) } },
                        label = { Text(if (g == com.bluepilot.remote.model.gamepad.StickGate.CIRCLE) "Circle" else "Square") }
                    )
                }
            }
            Text("Size preset", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = control.frame.w <= 0.17f,
                    onClick = { viewModel.updateSelected { it.copy(frame = it.frame.copy(w = 0.15f, h = 0.27f)) } },
                    label = { Text("Mini") }
                )
                FilterChip(
                    selected = control.frame.w > 0.17f,
                    onClick = { viewModel.updateSelected { it.copy(frame = it.frame.copy(w = 0.22f, h = 0.40f)) } },
                    label = { Text("Full-size") }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("8-direction guide lines", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.stickGuides,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(stickGuides = v) } }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sticky position (no auto-center)", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.stickSticky,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(stickSticky = v) } }
                )
            }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Outer range", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${(control.outerRange * 100).toInt()}%", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = control.outerRange,
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(outerRange = v) } },
                    valueRange = 0.5f..1f
                )
            }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Anti-deadzone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${control.antiDeadZone}%", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = control.antiDeadZone.toFloat(),
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(antiDeadZone = v.toInt()) } },
                    valueRange = 0f..40f
                )
            }
            // V2 MATRIX 6 — esports: snap angles.
            Text("Snap angles", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Analog", 8 to "8-way", 16 to "16-way").forEach { (n, name) ->
                    FilterChip(
                        selected = control.snapNotches == n,
                        onClick = { viewModel.updateSelected { it.copy(snapNotches = n) } },
                        label = { Text(name) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stick as D-pad (digital)", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.stickAsDpad,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(stickAsDpad = v) } }
                )
            }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Aim zone (sniper center)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (control.aimZoneRadius <= 0f) "Off"
                        else "${(control.aimZoneRadius * 100).toInt()}% • ${control.aimZoneInner}%",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = control.aimZoneRadius,
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(aimZoneRadius = v) } },
                    valueRange = 0f..0.9f
                )
                if (control.aimZoneRadius > 0f) {
                    Slider(
                        value = control.aimZoneInner.toFloat(),
                        onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(aimZoneInner = v.toInt()) } },
                        valueRange = 1f..100f
                    )
                }
            }
            Text("Stick click (press-down = L3/R3)", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = control.stickClickIndex < 0,
                    onClick = { viewModel.updateSelected { it.copy(stickClickIndex = -1) } },
                    label = { Text("Off") }
                )
                listOf(10 to "L3", 11 to "R3").forEach { (idx, name) ->
                    FilterChip(
                        selected = control.stickClickIndex == idx,
                        onClick = { viewModel.updateSelected { it.copy(stickClickIndex = idx) } },
                        label = { Text(name) }
                    )
                }
            }
        }

        // D-pad options
        if (control.type == GamepadControlType.DPAD) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("8-way (diagonals)", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.eightWay,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(eightWay = v) } }
                )
            }
            // ADV S1 — D-pad style + diagonal-only.
            Text("D-pad style", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.bluepilot.remote.model.gamepad.DpadStyle.entries.forEach { s ->
                    FilterChip(
                        selected = control.dpadStyle == s,
                        onClick = { viewModel.updateSelected { it.copy(dpadStyle = s) } },
                        label = { Text(if (s == com.bluepilot.remote.model.gamepad.DpadStyle.CROSS) "Cross" else "Circular") }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diagonals only", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.diagonalOnly,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(diagonalOnly = v) } }
                )
            }
        }

        // ADV S1 — ARROW options.
        if (control.type == GamepadControlType.ARROW) {
            Text("Direction", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.bluepilot.remote.model.gamepad.ArrowDirection.entries.forEach { d ->
                    FilterChip(
                        selected = control.arrowDirection == d,
                        onClick = { viewModel.updateSelected { it.copy(arrowDirection = d) } },
                        label = { Text(d.name.lowercase().replace("_", "-")) }
                    )
                }
            }
            Text("Style", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.bluepilot.remote.model.gamepad.ArrowStyle.entries.forEach { s ->
                    FilterChip(
                        selected = control.arrowStyle == s,
                        onClick = { viewModel.updateSelected { it.copy(arrowStyle = s) } },
                        label = { Text(s.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hold-to-repeat", modifier = Modifier.weight(1f))
                Switch(
                    checked = control.arrowRepeat,
                    onCheckedChange = { v -> viewModel.updateSelected { it.copy(arrowRepeat = v) } }
                )
            }
            if (control.arrowRepeat) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Repeat rate", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${control.arrowRepeatRate}/s", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = control.arrowRepeatRate.toFloat(),
                    onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(arrowRepeatRate = v.toInt()) } },
                    valueRange = 2f..30f
                )
            }
        }

        // ADV S1 — COMBO (bumper+trigger) options.
        if (control.type == GamepadControlType.COMBO) {
            val comboNaming = draft?.second?.naming ?: ButtonNaming.XBOX
            Text("Top zone sends", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..15).forEach { index ->
                    FilterChip(
                        selected = control.buttonIndex == index,
                        onClick = { viewModel.updateSelected { it.copy(buttonIndex = index) } },
                        label = { Text(GamepadButtonNames.label(index, comboNaming)) }
                    )
                }
            }
            Text("Bottom zone sends", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..15).forEach { index ->
                    FilterChip(
                        selected = control.comboSecondIndex == index,
                        onClick = { viewModel.updateSelected { it.copy(comboSecondIndex = index) } },
                        label = { Text(GamepadButtonNames.label(index, comboNaming)) }
                    )
                }
            }
            OutlinedTextField(
                value = control.label,
                onValueChange = { new -> viewModel.updateSelected { it.copy(label = new) } },
                label = { Text("Label prefix (L or R)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(10.dp))

        // Color
        Text("Color", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            controlPalette.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(argb.toComposeColor(), CircleShape)
                        .border(
                            width = if (argb == control.color) 3.dp else 1.dp,
                            color = if (argb == control.color) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { viewModel.updateSelected { it.copy(color = argb) } }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Opacity
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Opacity", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(String.format("%.2f", control.opacity), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = control.opacity,
                onValueChange = { v -> viewModel.updateSelected(withUndo = false) { it.copy(opacity = v) } },
                valueRange = 0.15f..1f
            )
        }
    }
}
