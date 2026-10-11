package com.bluepilot.remote.ui.screens.fullkeyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.bluepilot.remote.ui.components.bestContentColor
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import com.bluepilot.remote.model.keyboard.KeySpec
import com.bluepilot.remote.ui.components.HintBar
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.FullKeyboardViewModel

/**
 * SECTION: Full Keyboard — complete QWERTY board with per-key customization.
 * Toggle Full/Compact; enable Edit mode then long-press any key to
 * resize/recolor/relabel/remap/add secondary function/move it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullKeyboardScreen(
    onBack: () -> Unit,
    viewModel: FullKeyboardViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsState()
    val fullMode by viewModel.fullMode.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val editingKey by viewModel.editingKey.collectAsState()
    val haptic = rememberHaptic(true)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Full Keyboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // V2 MATRIX 7 — FN layer (latched). Only shown when the
                    // layout actually defines an overlay.
                    if (layout.fnOverlay.isNotEmpty()) {
                        val fnOn by viewModel.fnActive.collectAsState()
                        FilterChip(
                            selected = fnOn,
                            onClick = { viewModel.toggleFn() },
                            label = { Text("FN") }
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    FilterChip(
                        selected = !fullMode,
                        onClick = { viewModel.setFullMode(false) },
                        label = { Text("Compact") }
                    )
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = fullMode,
                        onClick = { viewModel.setFullMode(true) },
                        label = { Text("Full") }
                    )
                    // V2 M7 b2 — one-handed: OFF → RIGHT → LEFT → OFF.
                    val oneHand by viewModel.oneHanded.collectAsState()
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = oneHand != com.bluepilot.remote.data.keyboard.OneHandedMode.OFF,
                        onClick = { viewModel.cycleOneHanded() },
                        label = {
                            Text(
                                when (oneHand) {
                                    com.bluepilot.remote.data.keyboard.OneHandedMode.OFF -> "1-hand"
                                    com.bluepilot.remote.data.keyboard.OneHandedMode.RIGHT -> "1-hand →"
                                    com.bluepilot.remote.data.keyboard.OneHandedMode.LEFT -> "← 1-hand"
                                }
                            )
                        }
                    )
                    IconButton(onClick = { viewModel.toggleEditMode() }) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit keys",
                            tint = if (editMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            NotConnectedBanner(!isConnected)
            HintBar("Long-press a key for its secondary symbol • edit mode: tap keys to recolor")
            if (editMode) {
                Text(
                    "EDIT MODE — long-press any key to customize it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // ---------- Favorites row ----------
            if (layout.favorites.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star, contentDescription = "Favorites",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    layout.favorites.forEach { fav ->
                        AssistChip(
                            onClick = {
                                if (editMode) viewModel.removeFavorite(fav.id)
                                else { haptic(); viewModel.tap(fav) }
                            },
                            label = { Text(if (editMode) "✕ ${fav.label}" else fav.label) }
                        )
                    }
                }
            }

            // ---------- Shortcut packs (edit mode only) ----------
            if (editMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Packs:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.bluepilot.remote.model.keyboard.ShortcutPacks.ALL.forEach { pack ->
                        AssistChip(
                            onClick = { viewModel.applyPack(pack) },
                            label = { Text("${pack.name} ⇄") }
                        )
                    }
                    Text(
                        "replaces favorites",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- The board ----------
            // V2 MATRIX 7 — FN active: keys with an overlay entry render
            // their FN binding's label (real what-you-see-is-what-you-send).
            val fnOnBoard by viewModel.fnActive.collectAsState()
            // V2 M7 b2 — one-handed: board occupies 78% width, hugging the
            // chosen thumb side. Pure layout change — bindings untouched.
            val oneHandMode by viewModel.oneHanded.collectAsState()
            val boardWidthFraction =
                if (oneHandMode == com.bluepilot.remote.data.keyboard.OneHandedMode.OFF) 1f else 0.78f
            val boardAlign = when (oneHandMode) {
                com.bluepilot.remote.data.keyboard.OneHandedMode.LEFT -> Alignment.Start
                com.bluepilot.remote.data.keyboard.OneHandedMode.RIGHT -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
            layout.rows.forEachIndexed { rowIndex, row ->
                if (!fullMode && rowIndex in layout.compactHiddenRows) return@forEachIndexed
                Row(
                    modifier = Modifier
                        .fillMaxWidth(boardWidthFraction)
                        .align(boardAlign)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { key ->
                        val shown = layout.effectiveKey(key, fnOnBoard)
                        KeyCap(
                            // Label/color from the effective key; width stays
                            // the BASE key's (board geometry never shifts).
                            key = shown.copy(widthWeight = key.widthWeight),
                            editMode = editMode,
                            highlight = fnOnBoard && shown !== key,
                            modifier = Modifier.weight(key.widthWeight),
                            onTap = { haptic(); viewModel.tap(key) },
                            onLongPress = { haptic(); viewModel.longPress(key) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // STITCH REDESIGN — live status strip (REAL measured values).
            run {
                val healthVm: com.bluepilot.remote.viewmodel.ConnectionHealthViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val snap by healthVm.snapshot.collectAsState()
                val battery by healthVm.battery.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.bluepilot.remote.ui.components.GlassCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("LATENCY", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (snap.totalSent > 0) "${snap.recentMeanSendUs}µs" else "—",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    com.bluepilot.remote.ui.components.GlassCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("REPORTS", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${snap.reportsPerSecond}/s",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    com.bluepilot.remote.ui.components.GlassCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("BATTERY", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${battery.first}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editingKey != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.closeEditor() }) {
            KeyEditorPanel(viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyCap(
    key: KeySpec,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    /** V2 M7 — FN overlay is remapping this key: tinted so it's obvious. */
    highlight: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val custom = key.colorArgb?.let { it.toComposeColor() }
    val bg = when {
        highlight -> MaterialTheme.colorScheme.primaryContainer
        else -> custom ?: MaterialTheme.colorScheme.surfaceVariant
    }
    // SECTION 4 - 3D keycap: pressed state sinks + shadow shrinks
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .graphicsLayer {
                if (pressed) {
                    scaleX = 0.95f; scaleY = 0.95f
                    translationY = 2f * density
                }
            }
            .shadow(if (pressed) 0.dp else 3.dp, MaterialTheme.shapes.small, clip = false)
            .background(Brush.verticalGradient(listOf(bg.copy(alpha=1f), bg)), MaterialTheme.shapes.small)
            .then(
                if (editMode) Modifier.border(
                    1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    MaterialTheme.shapes.small
                ) else Modifier
            )
                        .pointerInput(key.id + "-press") {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        pressed = e.changes.any { it.pressed }
                    }
                }
            }
.combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (custom != null) custom.bestContentColor() else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
            )
            if (key.secondaryKeyCode != null && key.secondaryLabel.isNotBlank()) {
                Text(
                    text = key.secondaryLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Key editor
// ----------------------------------------------------------------------

/** Remappable HID targets shown in the editor (name → code+mods). */
private val remapTargets: List<Triple<String, Byte, Byte>> = listOf(
    Triple("Enter", HidKeys.ENTER, 0.toByte()),
    Triple("Esc", HidKeys.ESCAPE, 0.toByte()),
    Triple("Tab", HidKeys.TAB, 0.toByte()),
    Triple("Space", HidKeys.SPACE, 0.toByte()),
    Triple("Bksp", HidKeys.BACKSPACE, 0.toByte()),
    Triple("Del", HidKeys.DELETE, 0.toByte()),
    Triple("Copy", HidKeys.C, HidModifiers.LEFT_CTRL),
    Triple("Paste", HidKeys.V, HidModifiers.LEFT_CTRL),
    Triple("Cut", HidKeys.X, HidModifiers.LEFT_CTRL),
    Triple("Undo", HidKeys.Z, HidModifiers.LEFT_CTRL),
    Triple("Save", HidKeys.S, HidModifiers.LEFT_CTRL),
    Triple("SelAll", HidKeys.A, HidModifiers.LEFT_CTRL),
    Triple("Alt+Tab", HidKeys.TAB, HidModifiers.LEFT_ALT),
    Triple("Win+D", HidKeys.D, HidModifiers.LEFT_GUI),
    Triple("F5", HidKeys.F5, 0.toByte()),
    Triple("PgUp", HidKeys.PAGE_UP, 0.toByte()),
    Triple("PgDn", HidKeys.PAGE_DOWN, 0.toByte())
)

private val keyPalette = listOf<Long>(
    0xFF2F6BFF, 0xFF29C5FF, 0xFF2ECC71, 0xFFF1C40F,
    0xFFE67E22, 0xFFE74C3C, 0xFFFF2D95, 0xFF9B59B6, 0xFF34495E
)

@Composable
private fun KeyEditorPanel(viewModel: FullKeyboardViewModel) {
    val editing by viewModel.editingKey.collectAsState()
    val key = editing ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Customize key: ${key.label}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))

        // Label
        OutlinedTextField(
            value = key.label,
            onValueChange = { new -> viewModel.updateEditingKey { it.copy(label = new) } },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Size (width weight)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Width", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                String.format("%.1f×", key.widthWeight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = key.widthWeight,
            onValueChange = { v -> viewModel.updateEditingKey { it.copy(widthWeight = v) } },
            valueRange = KeySpec.WEIGHT_MIN..KeySpec.WEIGHT_MAX
        )

        // Position
        Text("Position in row", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            AssistChip(onClick = { viewModel.moveEditingKey(-1) }, label = { Text("← Move left") })
            AssistChip(onClick = { viewModel.moveEditingKey(1) }, label = { Text("Move right →") })
        }

        // Color
        Text("Key color", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Default" swatch clears the override
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .border(
                        width = if (key.colorArgb == null) 3.dp else 1.dp,
                        color = if (key.colorArgb == null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { viewModel.updateEditingKey { it.copy(colorArgb = null) } }
            )
            keyPalette.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(argb.toComposeColor(), CircleShape)
                        .border(
                            width = if (key.colorArgb == argb) 3.dp else 1.dp,
                            color = if (key.colorArgb == argb) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { viewModel.updateEditingKey { it.copy(colorArgb = argb) } }
                )
            }
        }

        // Remap primary
        Text("Sends (remap)", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            remapTargets.forEach { (name, code, mods) ->
                FilterChip(
                    selected = key.keyCode == code && key.modifiers == mods,
                    onClick = { viewModel.updateEditingKey { it.copy(keyCode = code, modifiers = mods) } },
                    label = { Text(name) }
                )
            }
        }

        // Secondary function
        Text("Long-press function", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = key.secondaryKeyCode == null,
                onClick = {
                    viewModel.updateEditingKey {
                        it.copy(secondaryKeyCode = null, secondaryModifiers = 0, secondaryLabel = "")
                    }
                },
                label = { Text("None") }
            )
            remapTargets.forEach { (name, code, mods) ->
                FilterChip(
                    selected = key.secondaryKeyCode == code && key.secondaryModifiers == mods,
                    onClick = {
                        viewModel.updateEditingKey {
                            it.copy(secondaryKeyCode = code, secondaryModifiers = mods, secondaryLabel = name)
                        }
                    },
                    label = { Text(name) }
                )
            }
        }

        // Favorites
        Spacer(Modifier.height(6.dp))
        AssistChip(
            onClick = { viewModel.addToFavorites(key) },
            label = { Text("★ Add to Favorites") }
        )
    }
}
