package com.aeropad.remote.ui.screens.mylayout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeropad.remote.model.HidModifiers
import com.aeropad.remote.model.keys.KeyCatalog
import com.aeropad.remote.model.keys.KeyDef
import com.aeropad.remote.model.keys.KeyGroup
import com.aeropad.remote.model.keys.NamedCombo
import com.aeropad.remote.ui.components.ScrollableChipRow
import com.aeropad.remote.viewmodel.KeyPreset

/**
 * The keyboard pad: every key on a real keyboard, plus the user's own named
 * shortcuts.
 *
 * Two modes, one pad:
 *  - **Send** — tapping a key presses it on the PC right now.
 *  - **Add**  — tapping a key drops that key onto the layout as a button, so a
 *    user who wants only W/A/S/D (or only Esc and Enter) can build exactly that.
 *
 * Modifier chips latch for the next key, which is how "Ctrl+Shift+Esc" gets
 * built and saved under the user's own name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyPadSheet(
    combos: List<NamedCombo>,
    isConnected: Boolean,
    hasLayout: Boolean,
    onDismiss: () -> Unit,
    onSend: (Byte, Int) -> Unit,
    onAdd: (String, Byte, Int, Boolean) -> Unit,
    onSaveCombo: (String, Byte, Int) -> Unit,
    onDeleteCombo: (String) -> Unit,
    onAddCombo: (NamedCombo) -> Unit,
    onAddPreset: (KeyPreset) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    var addMode by remember { mutableStateOf(false) }
    var mods by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf(KeyGroup.LETTERS) }
    var name by remember { mutableStateOf("") }
    // The key the next "Save" will store. Starts on Esc so an accidental Save
    // can never store "no key" (which would send nothing).
    var lastKey by remember { mutableStateOf(KeyCatalog.navigation.first()) }
    // F10 — when adding, these keys repeat while held (ideal for WASD/arrows).
    var turbo by remember { mutableStateOf(false) }

    val chordPreview = NamedCombo.chord(mods, "key")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- header ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keyboard & shortcuts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (addMode) "Tap a key to add it to this layout"
                        else if (isConnected) "Tap a key to send it to the PC"
                        else "Not connected — keys will queue as unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // ---------- mode switch ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeTab("Send", selected = !addMode, modifier = Modifier.weight(1f)) {
                    addMode = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                ModeTab("Add to layout", selected = addMode, modifier = Modifier.weight(1f)) {
                    addMode = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }

            if (addMode && !hasLayout) {
                Text(
                    "Pick a layout above first. Adding to a built-in template saves " +
                        "a personal copy, so the original stays clean.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ---------- modifier latches ----------
            ScrollableChipRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModChip("Ctrl", HidModifiers.LEFT_CTRL, mods) { mods = it }
                ModChip("Shift", HidModifiers.LEFT_SHIFT, mods) { mods = it }
                ModChip("Alt", HidModifiers.LEFT_ALT, mods) { mods = it }
                ModChip("Win", HidModifiers.LEFT_GUI, mods) { mods = it }
                if (mods != 0) {
                    TextButton(onClick = { mods = 0 }) { Text("Clear") }
                }
            }

            // F10 — hold-to-repeat switch, only meaningful when adding.
            AnimatedVisibility(visible = addMode && hasLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Turbo keys", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (turbo) "Hold a key to repeat it ~16x/second"
                            else "One press per tap — turn on for WASD / arrows",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = turbo,
                        onCheckedChange = { turbo = it }
                    )
                }
            }

            // Ready-made groups — the fastest way to get a usable pad onto an
            // empty layout without tapping 12 keys one at a time.
            AnimatedVisibility(visible = addMode && hasLayout) {
                ScrollableChipRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick add",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KeyPreset.entries.forEach { preset ->
                        GroupChip(preset.title, selected = false) { onAddPreset(preset) }
                    }
                }
            }

            AnimatedVisibility(visible = mods != 0) {
                Text(
                    chordPreview + " …",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---------- key groups ----------
            ScrollableChipRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyGroup.entries.forEach { g ->
                    GroupChip(g.title, selected = group == g) { group = g }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp, max = 232.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(KeyCatalog.group(group), key = { it.label }) { key ->
                    KeyTile(key) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lastKey = key
                        if (addMode) {
                            onAdd(key.label, key.code, mods, turbo)
                        } else {
                            onSend(key.code, mods)
                        }
                        // Modifier latches are one-shot when sending.
                        if (mods != 0 && !addMode) mods = 0
                    }
                }
            }

            // ---------- save a named combo ----------
            Text(
                "Your shortcuts  ·  next save: " + NamedCombo.chord(mods, lastKey.name),
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NamedCombo.NAME_MAX) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Name (e.g. Close window)") },
                    shape = RoundedCornerShape(14.dp)
                )
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSaveCombo(name, lastKey.code, mods)
                        name = ""
                        mods = 0
                    }
                ) { Text("Save") }
            }

            if (combos.isEmpty()) {
                Text(
                    "Nothing saved yet. Latch a modifier or two above, tap the key, " +
                        "then name it — it becomes a button you can drop on any layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                CombosGrid(
                    combos = combos,
                    onAdd = onAddCombo,
                    onDelete = onDeleteCombo
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModChip(label: String, bit: Byte, mods: Int, onChange: (Int) -> Unit) {
    val selected = (mods and bit.toInt()) != 0
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(if (selected) mods and bit.toInt().inv() else mods or bit.toInt())
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyTile(key: KeyDef, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    // Press feedback derived from the real interaction state — it can never
    // get stuck "pressed" the way a manual boolean can.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "keyScale")
    val bg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        label = "keyBg"
    )

    Box(
        modifier = Modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            key.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CombosGrid(
    combos: List<NamedCombo>,
    onAdd: (NamedCombo) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 168.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        combos.forEach { combo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable { onAdd(combo) }
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        combo.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        combo.chordString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Add →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { onDelete(combo.id) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Delete ${combo.displayName()}",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
