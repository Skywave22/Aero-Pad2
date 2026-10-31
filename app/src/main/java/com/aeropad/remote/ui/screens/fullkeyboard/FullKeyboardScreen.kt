package com.aeropad.remote.ui.screens.fullkeyboard

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.aeropad.remote.ui.components.bestContentColor
import com.aeropad.remote.ui.components.toComposeColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.keyboard.KeySpec
import com.aeropad.remote.ui.components.HintBar
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.components.ScrollableChipRow
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.viewmodel.FullKeyboardViewModel

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
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val fullMode by viewModel.fullMode.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val editingKey by viewModel.editingKey.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(true)

    // Round 15 — landscape is the orientation people actually type in with the
    // phone propped on a stand, and the board did not fit: five rows of ~56 dp
    // under an app bar, a mode row, a banner, a favourites row, a modifier row
    // and a status line left room for two and a half rows, so the rest of the
    // keyboard was below the fold. Landscape now draws one slim header, ONE
    // chip row, and then shares every remaining dp between the key rows.
    val landscape = com.aeropad.remote.ui.components.isLandscape()
    val mods by viewModel.modifiers.collectAsStateWithLifecycle()
    var symbolLayer by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Full Keyboard",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isConnected) {
                        Text(
                            "• Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleEditMode() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit keys",
                            tint = if (editMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else TopAppBar(
                title = { Text("Full Keyboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Round 14 — the app bar used to hold FN, Compact, Full,
                    // 1-hand AND the edit button. On a 360 dp screen the title
                    // "Full Keyboard" was squeezed to "FN" and the back arrow
                    // looked like part of a chip. Mode switches now live in a
                    // row inside the keyboard (below), which also puts them
                    // nearer the keys; the app bar keeps only the title.
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
        val compactBoard = landscape
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp)
                .then(if (landscape) Modifier else Modifier.verticalScroll(rememberScrollState()))
        ) {
            // Round 14 — mode switches moved here from the crowded app bar,
            // which had squeezed the title down to "FN".
            run {
                val fnOn by viewModel.fnActive.collectAsStateWithLifecycle()
                val oneHand by viewModel.oneHanded.collectAsStateWithLifecycle()
                com.aeropad.remote.ui.components.ScrollableChipRow(
                    modifier = Modifier.padding(vertical = if (landscape) 2.dp else 4.dp)
                ) {
                    if (layout.fnOverlay.isNotEmpty()) {
                        FilterChip(
                            selected = fnOn,
                            onClick = { viewModel.toggleFn() },
                            label = { Text("FN") }
                        )
                    }
                    FilterChip(
                        selected = !fullMode,
                        onClick = { viewModel.setFullMode(false) },
                        label = { Text("Compact") }
                    )
                    FilterChip(
                        selected = fullMode,
                        onClick = { viewModel.setFullMode(true) },
                        label = { Text("Full") }
                    )
                    FilterChip(
                        selected = oneHand != com.aeropad.remote.data.keyboard.OneHandedMode.OFF,
                        onClick = { viewModel.cycleOneHanded() },
                        label = {
                            Text(
                                when (oneHand) {
                                    com.aeropad.remote.data.keyboard.OneHandedMode.OFF -> "1-hand"
                                    com.aeropad.remote.data.keyboard.OneHandedMode.RIGHT -> "1-hand right"
                                    com.aeropad.remote.data.keyboard.OneHandedMode.LEFT -> "1-hand left"
                                }
                            )
                        }
                    )
                    // Landscape: the modifiers travel in this same row, because
                    // a second chip row costs a whole key row of height.
                    if (landscape) {
                        ModifierChips(
                            mods = mods,
                            symbolLayer = symbolLayer,
                            onToggleModifier = { haptic(); viewModel.toggleModifier(it) },
                            onCaps = { haptic(); viewModel.toggleCaps() },
                            onSymbols = { haptic(); symbolLayer = !symbolLayer },
                            onClear = { haptic(); viewModel.clearModifiers() }
                        )
                    }
                }
            }

            NotConnectedBanner(!isConnected)

            // Round 11 — in landscape a phone window is only ~190 dp tall and
            // the board needs every one of those dp for keys: the hint bar cost
            // a whole row of them, which is why only two key rows were visible
            // sideways. (The same tip lives in Help.)
            if (!compactBoard) {
                HintBar("Long-press a key for its secondary symbol • edit mode: tap keys to recolor")
            }
            if (editMode) {
                Text(
                    "EDIT MODE — long-press any key to customize it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // ---------- Favorites row ----------
            // Landscape needs the dp for keys; Copy/Paste/Undo are one tap away
            // in portrait and in the Text & Clips screen.
            if (layout.favorites.isNotEmpty() && !landscape) {
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
                    com.aeropad.remote.model.keyboard.ShortcutPacks.ALL.forEach { pack ->
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

            // ---------- Round 15: modifiers, caps and symbols ----------
            // The board is the only screen with letters, and it had no way to
            // produce Ctrl+/Shift+/Alt+ or any symbol. Every modifier is a
            // latch chip here, the Caps state is drawn, and ?123 swaps in the
            // symbol layer.
            // The status line sits directly above the modifier row: it is the
            // answer to "I pressed Caps and nothing changed", so it has to be
            // next to the control that changes it.
            if (!landscape) Text(
                text = when {
                    mods.capsLock && mods.mask != 0 -> "CAPS LOCK on  •  modifier latched"
                    mods.capsLock -> "CAPS LOCK on — letters will be capitals"
                    mods.mask != 0 -> "Modifier latched: applies to the next key"
                    else -> "Letters are lower case — tap Caps or Shift for capitals"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mods.anyActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!landscape) ScrollableChipRow(modifier = Modifier.padding(bottom = 4.dp)) {
                ModifierChips(
                    mods = mods,
                    symbolLayer = symbolLayer,
                    onToggleModifier = { haptic(); viewModel.toggleModifier(it) },
                    onCaps = { haptic(); viewModel.toggleCaps() },
                    onSymbols = { haptic(); symbolLayer = !symbolLayer },
                    onClear = { haptic(); viewModel.clearModifiers() }
                )
            }

            // ---------- The board ----------
            // V2 MATRIX 7 — FN active: keys with an overlay entry render
            // their FN binding's label (real what-you-see-is-what-you-send).
            val fnOnBoard by viewModel.fnActive.collectAsStateWithLifecycle()
            // V2 M7 b2 — one-handed: board occupies 78% width, hugging the
            // chosen thumb side. Pure layout change — bindings untouched.
            val oneHandMode by viewModel.oneHanded.collectAsStateWithLifecycle()
            val boardWidthFraction =
                if (oneHandMode == com.aeropad.remote.data.keyboard.OneHandedMode.OFF) 1f else 0.78f
            val boardAlign = when (oneHandMode) {
                com.aeropad.remote.data.keyboard.OneHandedMode.LEFT -> Alignment.Start
                com.aeropad.remote.data.keyboard.OneHandedMode.RIGHT -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
            val boardRows = if (symbolLayer) layout.symbolRows else layout.rows
            boardRows.forEachIndexed { rowIndex, row ->
                if (!symbolLayer && !fullMode && rowIndex in layout.compactHiddenRows) return@forEachIndexed
                Row(
                    modifier = Modifier
                        .fillMaxWidth(boardWidthFraction)
                        .align(boardAlign)
                        .then(if (landscape) Modifier.weight(1f) else Modifier)
                        .padding(vertical = if (compactBoard) 1.dp else 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { key ->
                        val shown = layout.effectiveKey(key, fnOnBoard)
                        // Length-1 alphabetic labels follow the real case that
                        // will be typed, so Caps and Shift are visible on the
                        // board itself rather than only in a settings toggle.
                        val cased = if (!symbolLayer && key.label.length == 1 &&
                            key.label[0].isLetter()
                        ) {
                            key.copy(
                                label = if (mods.effectivelyShifted) key.label.uppercase()
                                else key.label.lowercase()
                            )
                        } else key
                        KeyCap(
                            // Label/color from the effective key; width stays
                            // the BASE key's (board geometry never shifts).
                            key = shown.copy(
                                widthWeight = key.widthWeight,
                                label = if (shown === key) cased.label else shown.label
                            ),
                            editMode = editMode,
                            highlight = fnOnBoard && shown !== key,
                            modifier = Modifier
                                .weight(key.widthWeight)
                                .then(if (landscape) Modifier.fillMaxHeight() else Modifier),
                            compact = compactBoard,
                            stretch = landscape,
                            onTap = { haptic(); viewModel.tap(if (symbolLayer) key else cased) },
                            onLongPress = { haptic(); viewModel.longPress(key) }
                        )
                    }
                }
            }
            // STITCH REDESIGN — live status strip (REAL measured values).
            // Portrait only: the same numbers live in Connection Health.
            if (!landscape) run {
                Spacer(Modifier.height(10.dp))
                val healthVm: com.aeropad.remote.viewmodel.ConnectionHealthViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val snap by healthVm.snapshot.collectAsStateWithLifecycle()
                val battery by healthVm.battery.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.aeropad.remote.ui.components.GlassCard(modifier = Modifier.weight(1f)) {
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
                    com.aeropad.remote.ui.components.GlassCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("REPORTS", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${snap.reportsPerSecond}/s",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    com.aeropad.remote.ui.components.GlassCard(modifier = Modifier.weight(1f)) {
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

/**
 * Round 15 — the modifier / caps / symbol controls, in one place so the
 * portrait and landscape layouts cannot drift apart again.
 */
@Composable
private fun ModifierChips(
    mods: com.aeropad.remote.domain.KeyboardModifierState.Ui,
    symbolLayer: Boolean,
    onToggleModifier: (Byte) -> Unit,
    onCaps: () -> Unit,
    onSymbols: () -> Unit,
    onClear: () -> Unit
) {
    com.aeropad.remote.domain.KeyboardModifierState.CHIPS.forEach { (label, bit) ->
        FilterChip(
            selected = mods.isOn(bit),
            onClick = { onToggleModifier(bit) },
            label = { Text(if (mods.isLocked(bit)) "$label locked" else label) }
        )
    }
    // Caps lives here as well as on the keycap: the keycap sits at the far
    // left of the number row, and hunting for it was the reported problem.
    FilterChip(
        selected = mods.capsLock,
        onClick = onCaps,
        label = { Text(if (mods.capsLock) "CAPS on" else "Caps") }
    )
    FilterChip(
        selected = symbolLayer,
        onClick = onSymbols,
        label = { Text(if (symbolLayer) "abc" else "?123") }
    )
    if (mods.anyActive) {
        AssistChip(onClick = onClear, label = { Text("Clear") })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyCap(
    key: KeySpec,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    /** V2 M7 — FN overlay is remapping this key: tinted so it's obvious. */
    highlight: Boolean = false,
    /**
     * Round 11 — landscape squeezes the board into ~130 dp of window, so the
     * keycaps shrink rather than pushing whole rows off-screen.
     */
    compact: Boolean = false,
    /** Landscape rows share the leftover height, so the cap must not have a floor. */
    stretch: Boolean = false,
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
            .then(if (stretch) Modifier else Modifier.heightIn(min = if (compact) 30.dp else 40.dp))
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
                modifier = Modifier.padding(
                    horizontal = 2.dp,
                    vertical = if (stretch) 1.dp else 6.dp
                )
            )
            // On a shared-height row there is not room for two lines of text.
            if (!stretch && key.secondaryKeyCode != null && key.secondaryLabel.isNotBlank()) {
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
    val editing by viewModel.editingKey.collectAsStateWithLifecycle()
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
